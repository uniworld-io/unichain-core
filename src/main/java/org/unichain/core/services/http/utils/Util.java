package org.unichain.core.services.http.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.eclipse.jetty.util.StringUtil;
import org.apache.commons.lang3.StringUtils;
import org.spongycastle.util.encoders.Hex;
import org.unichain.api.GrpcAPI.*;
import org.unichain.common.crypto.Hash;
import org.unichain.common.utils.ByteArray;
import org.unichain.common.utils.Sha256Hash;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.BlockCapsule;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.core.config.args.Args;
import org.unichain.core.services.http.utils.JsonFormat.ParseException;
import org.unichain.protos.Contract;
import org.unichain.protos.Contract.*;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Block;
import org.unichain.protos.Protocol.Transaction;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;

@Slf4j(topic = "API")
public class Util {
  public static final String PERMISSION_ID = "Permission_id";
  public static final String VISIBLE = "visible";
  public static final String TRANSACTION = "transaction";
  public static final String VALUE = "value";

  public static String messageErrorHttp(Exception e){
    JSONObject jsonObject = new JSONObject();
    jsonObject.put("status", 400);
    jsonObject.put("message", e.getMessage());
    return jsonObject.toJSONString();
  }

  public static String printErrorMsg(Exception e) {
    JSONObject jsonObject = new JSONObject();
    jsonObject.put("Error", e.getClass() + " : " + e.getMessage());
    return jsonObject.toJSONString();
  }

  public static String printBlockList(BlockList list, boolean selfType) {
    List<Block> blocks = list.getBlockList();
    JSONObject jsonObject = JSONObject.parseObject(JsonFormat.printToString(list, selfType));
    JSONArray jsonArray = new JSONArray();
    blocks.forEach(block -> jsonArray.add(printBlockToJSON(block, selfType)));
    jsonObject.put("block", jsonArray);

    return jsonObject.toJSONString();
  }

  public static String printBlock(Block block, boolean selfType) {
    return printBlockToJSON(block, selfType).toJSONString();
  }

  public static JSONObject printBlockToJSON(Block block, boolean selfType) {
    BlockCapsule blockCapsule = new BlockCapsule(block);
    String blockID = ByteArray.toHexString(blockCapsule.getBlockId().getBytes());
    JSONObject jsonObject = JSONObject.parseObject(JsonFormat.printToString(block, selfType));
    jsonObject.put("blockID", blockID);
    if (!blockCapsule.getTransactions().isEmpty()) {
      jsonObject.put("transactions", printTransactionListToJSON(blockCapsule.getTransactions(), selfType));
    }
    return jsonObject;
  }

  public static String printTransactionList(TransactionList list, boolean selfType) {
    List<Transaction> transactions = list.getTransactionList();
    JSONObject jsonObject = JSONObject.parseObject(JsonFormat.printToString(list, selfType));
    JSONArray jsonArray = new JSONArray();
    transactions.forEach(transaction -> jsonArray.add(printTransactionToJSON(transaction, selfType)));
    jsonObject.put(TRANSACTION, jsonArray);

    return jsonObject.toJSONString();
  }

  public static JSONArray printTransactionListToJSON(List<TransactionCapsule> list, boolean selfType) {
    JSONArray transactions = new JSONArray();
    list.forEach(transactionCapsule -> transactions.add(printTransactionToJSON(transactionCapsule.getInstance(), selfType)));
    return transactions;
  }

  public static String printEasyTransferResponse(EasyTransferResponse response, boolean selfType) {
    JSONObject jsonResponse = JSONObject.parseObject(JsonFormat.printToString(response, selfType));
    jsonResponse.put(TRANSACTION, printTransactionToJSON(response.getTransaction(), selfType));
    return jsonResponse.toJSONString();
  }

  public static String printTransaction(Transaction transaction, boolean selfType) {
    return printTransactionToJSON(transaction, selfType).toJSONString();
  }

  public static String printCreateTransaction(Transaction transaction, boolean selfType) {
    JSONObject jsonObject = printTransactionToJSON(transaction, selfType);
    jsonObject.put(VISIBLE, selfType);
    return jsonObject.toJSONString();
  }

  public static String printTransactionExtention(TransactionExtention transactionExtention,
      boolean selfType) {
    String string = JsonFormat.printToString(transactionExtention, selfType);
    JSONObject jsonObject = JSONObject.parseObject(string);
    if (transactionExtention.getResult().getResult()) {
      JSONObject transactionOjbect = printTransactionToJSON(transactionExtention.getTransaction(), selfType);
      transactionOjbect.put(VISIBLE, selfType);
      jsonObject.put(TRANSACTION, transactionOjbect);
    }
    return jsonObject.toJSONString();
  }

  public static String printTransactionSignWeight(TransactionSignWeight transactionSignWeight, boolean selfType) {
    String string = JsonFormat.printToString(transactionSignWeight, selfType);
    JSONObject jsonObject = JSONObject.parseObject(string);
    JSONObject jsonObjectExt = jsonObject.getJSONObject(TRANSACTION);
    jsonObjectExt.put(TRANSACTION, printTransactionToJSON(transactionSignWeight.getTransaction().getTransaction(), selfType));
    jsonObject.put(TRANSACTION, jsonObjectExt);
    return jsonObject.toJSONString();
  }

  public static String printTransactionApprovedList(TransactionApprovedList transactionApprovedList, boolean selfType) {
    String string = JsonFormat.printToString(transactionApprovedList, selfType);
    JSONObject jsonObject = JSONObject.parseObject(string);
    JSONObject jsonObjectExt = jsonObject.getJSONObject(TRANSACTION);
    jsonObjectExt.put(TRANSACTION, printTransactionToJSON(transactionApprovedList.getTransaction().getTransaction(), selfType));
    jsonObject.put(TRANSACTION, jsonObjectExt);
    return jsonObject.toJSONString();
  }

  public static byte[] generateContractAddress(Transaction unx, byte[] ownerAddress) {
    // get tx hash
    byte[] txRawDataHash = Sha256Hash.of(unx.getRawData().toByteArray()).getBytes();

    // combine
    byte[] combined = new byte[txRawDataHash.length + ownerAddress.length];
    System.arraycopy(txRawDataHash, 0, combined, 0, txRawDataHash.length);
    System.arraycopy(ownerAddress, 0, combined, txRawDataHash.length, ownerAddress.length);
    return Hash.sha3omit12(combined);
  }

  public static JSONObject printTransactionToJSON(Transaction transaction, boolean selfType) {
    JSONObject jsonTransaction = JSONObject.parseObject(JsonFormat.printToString(transaction, selfType));
    JSONArray contracts = new JSONArray();
    transaction.getRawData().getContractList().forEach(contract -> {
      try {
        JSONObject contractJson = null;
        Any contractParameter = contract.getParameter();
        switch (contract.getType()) {
          case AccountCreateContract:{
            var parsedContract = contractParameter.unpack(AccountCreateContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case TransferContract:{
            var parsedContract = contractParameter.unpack(TransferContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case TransferAssetContract:{
            var parsedContract = contractParameter.unpack(TransferAssetContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case VoteAssetContract:{
            var parsedContract = contractParameter.unpack(VoteAssetContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case VoteWitnessContract:{
            var parsedContract = contractParameter.unpack(VoteWitnessContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case WitnessCreateContract:{
            var parsedContract = contractParameter.unpack(WitnessCreateContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case AssetIssueContract:{
            var parsedContract = contractParameter.unpack(AssetIssueContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case WitnessUpdateContract:{
            var parsedContract = contractParameter.unpack(WitnessUpdateContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case ParticipateAssetIssueContract:{
            var parsedContract = contractParameter.unpack(ParticipateAssetIssueContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case AccountUpdateContract:{
            var parsedContract = contractParameter.unpack(AccountUpdateContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case FreezeBalanceContract:{
            var parsedContract = contractParameter.unpack(FreezeBalanceContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case UnfreezeBalanceContract:{
            var parsedContract = contractParameter.unpack(UnfreezeBalanceContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case WithdrawBalanceContract:{
            var parsedContract = contractParameter.unpack(WithdrawBalanceContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case UnfreezeAssetContract:{
            var parsedContract = contractParameter.unpack(UnfreezeAssetContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case UpdateAssetContract:{
            var parsedContract = contractParameter.unpack(UpdateAssetContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case ProposalCreateContract:{
            var parsedContract = contractParameter.unpack(ProposalCreateContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case ProposalApproveContract:{
            var parsedContract = contractParameter.unpack(ProposalApproveContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case ProposalDeleteContract:{
            var parsedContract = contractParameter.unpack(ProposalDeleteContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case SetAccountIdContract:{
            var parsedContract = contractParameter.unpack(SetAccountIdContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case CreateSmartContract:{
            var parsedContract = contractParameter.unpack(CreateSmartContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            byte[] ownerAddress = parsedContract.getOwnerAddress().toByteArray();
            byte[] contractAddress = generateContractAddress(transaction, ownerAddress);
            jsonTransaction.put("contract_address", ByteArray.toHexString(contractAddress));
            break;
          }
          case TriggerSmartContract:{
            var parsedContract = contractParameter.unpack(TriggerSmartContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case UpdateSettingContract:{
            var parsedContract = contractParameter.unpack(UpdateSettingContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case ExchangeCreateContract:{
            var parsedContract = contractParameter.unpack(ExchangeCreateContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case ExchangeInjectContract:{
            var parsedContract = contractParameter.unpack(ExchangeInjectContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case ExchangeWithdrawContract:{
            var parsedContract = contractParameter.unpack(ExchangeWithdrawContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case ExchangeTransactionContract:{
            var parsedContract = contractParameter.unpack(ExchangeTransactionContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case AccountPermissionUpdateContract:{
            var parsedContract = contractParameter.unpack(AccountPermissionUpdateContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case ClearABIContract:{
            var parsedContract = contractParameter.unpack(ClearABIContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case UpdateBrokerageContract:{
            var parsedContract = contractParameter.unpack(UpdateBrokerageContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case FutureTransferContract:{
            var parsedContract = contractParameter.unpack(FutureTransferContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case FutureDealTransferContract:{
            var parsedContract = contractParameter.unpack(FutureDealTransferContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case FutureWithdrawContract:{
            var parsedContract = contractParameter.unpack(FutureWithdrawContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case CreateTokenContract:{
            var parsedContract = contractParameter.unpack(CreateTokenContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case TransferTokenOwnerContract:{
            var parsedContract = contractParameter.unpack(TransferTokenOwnerContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case ExchangeTokenContract:{
            var parsedContract = contractParameter.unpack(ExchangeTokenContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case ContributeTokenPoolFeeContract:{
            var parsedContract = contractParameter.unpack(ContributeTokenPoolFeeContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case UpdateTokenParamsContract:{
            var parsedContract = contractParameter.unpack(UpdateTokenParamsContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case MineTokenContract:{
            var parsedContract = contractParameter.unpack(MineTokenContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case BurnTokenContract:{
            var parsedContract = contractParameter.unpack(BurnTokenContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case TransferTokenContract:{
            var parsedContract = contractParameter.unpack(TransferTokenContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case WithdrawFutureTokenContract:{
            var parsedContract = contractParameter.unpack(WithdrawFutureTokenContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case Urc721CreateContract:{
            var parsedContract = contractParameter.unpack(Urc721CreateContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case Urc721MintContract:{
            var parsedContract = contractParameter.unpack(Urc721MintContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case Urc721RemoveMinterContract:{
            var parsedContract = contractParameter.unpack(Urc721RemoveMinterContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case Urc721AddMinterContract:{
            var parsedContract = contractParameter.unpack(Urc721AddMinterContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case Urc721RenounceMinterContract:{
            var parsedContract = contractParameter.unpack(Urc721RenounceMinterContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case Urc721BurnContract:{
            var parsedContract = contractParameter.unpack(Urc721BurnContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case Urc721ApproveContract:{
            var parsedContract = contractParameter.unpack(Urc721ApproveContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case Urc721SetApprovalForAllContract:{
            var parsedContract = contractParameter.unpack(Urc721SetApprovalForAllContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case Urc721TransferFromContract:{
            var parsedContract = contractParameter.unpack(Urc721TransferFromContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }

          /**
           * POSBridge
           */
          case PosBridgeSetupContract:{
            var parsedContract = contractParameter.unpack(PosBridgeSetupContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case PosBridgeMapTokenContract:{
            var parsedContract = contractParameter.unpack(PosBridgeMapTokenContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case PosBridgeCleanMapTokenContract:{
            var parsedContract = contractParameter.unpack(PosBridgeCleanMapTokenContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case PosBridgeDepositContract:{
            var parsedContract = contractParameter.unpack(PosBridgeDepositContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case PosBridgeDepositExecContract:{
            var parsedContract = contractParameter.unpack(PosBridgeDepositExecContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case PosBridgeWithdrawContract:{
            var parsedContract = contractParameter.unpack(PosBridgeWithdrawContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case PosBridgeWithdrawExecContract:{
            var parsedContract = contractParameter.unpack(PosBridgeWithdrawExecContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }

          //urc20
          case Urc20CreateContract:{
            var parsedContract = contractParameter.unpack(Urc20CreateContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }

          case Urc20ContributePoolFeeContract:{
            var parsedContract = contractParameter.unpack(Urc20ContributePoolFeeContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }

          case Urc20UpdateParamsContract:{
            var parsedContract = contractParameter.unpack(Urc20UpdateParamsContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }

          case Urc20MintContract:{
            var parsedContract = contractParameter.unpack(Urc20MintContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }

          case Urc20BurnContract:{
            var parsedContract = contractParameter.unpack(Urc20BurnContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }

          case Urc20TransferFromContract:{
            var parsedContract = contractParameter.unpack(Urc20TransferFromContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }

          case Urc20TransferContract:{
            var parsedContract = contractParameter.unpack(Urc20TransferContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }

          case Urc20WithdrawFutureContract:{
            var parsedContract = contractParameter.unpack(Urc20WithdrawFutureContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }

          case Urc20TransferOwnerContract:{
            var parsedContract = contractParameter.unpack(Urc20TransferOwnerContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }

          case Urc20ExchangeContract:{
            var parsedContract = contractParameter.unpack(Urc20ExchangeContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }

          case Urc20ApproveContract:{
            var parsedContract = contractParameter.unpack(Urc20ApproveContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          default:
        }
        JSONObject parameter = new JSONObject();
        parameter.put(VALUE, contractJson);
        parameter.put("type_url", contract.getParameterOrBuilder().getTypeUrl());
        JSONObject jsonContract = new JSONObject();
        jsonContract.put("parameter", parameter);
        jsonContract.put("type", contract.getType());
        if (contract.getPermissionId() > 0) {
          jsonContract.put(PERMISSION_ID, contract.getPermissionId());
        }
        contracts.add(jsonContract);
      } catch (InvalidProtocolBufferException e) {
        logger.error(e.getMessage(), e);
      }
    });

    JSONObject rawData = JSONObject.parseObject(jsonTransaction.get("raw_data").toString());
    rawData.put("contract", contracts);
    jsonTransaction.put("raw_data", rawData);
    String rawDataHex = ByteArray.toHexString(transaction.getRawData().toByteArray());
    jsonTransaction.put("raw_data_hex", rawDataHex);
    String txID = ByteArray.toHexString(Sha256Hash.hash(transaction.getRawData().toByteArray()));
    jsonTransaction.put("txID", txID);
    return jsonTransaction;
  }

  //@addon declare new contract
  public static Transaction packTransaction(String strTransaction, boolean selfType) {
    JSONObject jsonTransaction = JSONObject.parseObject(strTransaction);
    JSONObject rawData = jsonTransaction.getJSONObject("raw_data");
    JSONArray contracts = new JSONArray();
    JSONArray rawContractArray = rawData.getJSONArray("contract");

    for (int i = 0; i < rawContractArray.size(); i++) {
      try {
        JSONObject contract = rawContractArray.getJSONObject(i);
        JSONObject parameter = contract.getJSONObject("parameter");
        String contractType = contract.getString("type");
        Any any = null;
        switch (contractType) {
          case "AccountCreateContract":{
            var builder = AccountCreateContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "TransferContract": {
            var builder = TransferContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "TransferAssetContract":{
            var builder = TransferAssetContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "VoteAssetContract": {
            var builder = VoteAssetContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "VoteWitnessContract":
          {
            var builder = VoteWitnessContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "WitnessCreateContract":
          {
            var builder = WitnessCreateContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "AssetIssueContract":
          {
            var builder = AssetIssueContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "WitnessUpdateContract":
          {
            var builder = WitnessUpdateContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "ParticipateAssetIssueContract":
          {
            var builder = ParticipateAssetIssueContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "AccountUpdateContract":
          {
            var builder = AccountUpdateContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "FreezeBalanceContract":
          {
            var builder = FreezeBalanceContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "UnfreezeBalanceContract":
          {
            var builder = UnfreezeBalanceContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "WithdrawBalanceContract":
          {
            var builder = WithdrawBalanceContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "UnfreezeAssetContract":
          {
            var builder = UnfreezeAssetContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "UpdateAssetContract":{
            var builder = UpdateAssetContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "ProposalCreateContract":{
            var builder = ProposalCreateContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "ProposalApproveContract":{
            var builder = ProposalApproveContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "ProposalDeleteContract":{
            var builder = ProposalDeleteContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "SetAccountIdContract": {
            var builder = SetAccountIdContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "CreateSmartContract":{
            var builder = CreateSmartContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "TriggerSmartContract":{
            var builder = TriggerSmartContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "UpdateSettingContract":{
            var builder = UpdateSettingContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "ExchangeCreateContract":{
            var builder = ExchangeCreateContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "ExchangeInjectContract":{
            var builder = ExchangeInjectContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "ExchangeTransactionContract":{
            var builder = ExchangeTransactionContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "ExchangeWithdrawContract":{
            var builder = ExchangeWithdrawContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "UpdateEnergyLimitContract":{
            var builder = UpdateEnergyLimitContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "AccountPermissionUpdateContract":{
            var builder = AccountPermissionUpdateContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "ClearABIContract":{
            var builder = Contract.ClearABIContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "UpdateBrokerageContract":{
            var builder = Contract.UpdateBrokerageContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }

          /*
            future transfer
           */
          case "FutureTransferContract":{
            var builder = Contract.FutureTransferContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "FutureWithdrawContract":{
            var builder = Contract.FutureWithdrawContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "FutureDealTransferContract":{
            var builder = FutureDealTransferContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }

          /*
             urc30
           */
          case "CreateTokenContract":{
            var builder = CreateTokenContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "TransferTokenOwnerContract":{
            var builder = TransferTokenOwnerContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "ExchangeTokenContract":{
            var builder = ExchangeTokenContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "ContributeTokenPoolFeeContract":{
            var builder = ContributeTokenPoolFeeContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "UpdateTokenParamsContract":{
            var builder = UpdateTokenParamsContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "MineTokenContract":{
            var builder = MineTokenContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "BurnTokenContract":{
            var builder = BurnTokenContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "TransferTokenContract":{
            var builder = TransferTokenContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }

          case "WithdrawFutureTokenContract":{
            var builder = WithdrawFutureTokenContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }

          case "Urc721CreateContract":
          {
            var builder = Urc721CreateContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "Urc721MintContract":
          {
            var builder = Urc721MintContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "Urc721RemoveMinterContract":
          {
            var builder = Urc721RemoveMinterContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "Urc721AddMinterContract":
          {
            var builder = Urc721AddMinterContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }

          case "Urc721RenounceMinterContract":
          {
            var builder = Urc721RenounceMinterContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "Urc721BurnContract":
          {
            var builder = Urc721BurnContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }

          case "Urc721ApproveContract":
          {
            var builder = Urc721ApproveContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }

          case "Urc721SetApprovalForAllContract":
          {
            var builder = Urc721SetApprovalForAllContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }

          case "Urc721TransferFromContract":
          {
            var builder = Urc721TransferFromContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          /**
           * POSBridge
            */
          case "PosBridgeSetupContract":{
            var builder = PosBridgeSetupContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "PosBridgeMapTokenContract":{
            var builder = PosBridgeMapTokenContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "PosBridgeCleanMapTokenContract":{
            var builder = PosBridgeCleanMapTokenContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "PosBridgeDepositContract":{
            var builder = PosBridgeDepositContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "PosBridgeDepositExecContract":{
            var builder = PosBridgeDepositExecContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "PosBridgeWithdrawContract":{
            var builder = PosBridgeWithdrawContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "PosBridgeWithdrawExecContract":{
            var builder = PosBridgeWithdrawExecContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          //Urc20
          case "Urc20CreateContract":{
            var builder = Urc20CreateContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "Urc20ContributePoolFeeContract":{
            var builder = Urc20ContributePoolFeeContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "Urc20UpdateParamsContract":{
            var builder = Urc20UpdateParamsContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "Urc20MintContract":{
            var builder = Urc20MintContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "Urc20BurnContract":{
            var builder = Urc20BurnContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "Urc20TransferFromContract":{
            var builder = Urc20TransferFromContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "Urc20TransferContract":{
            var builder = Urc20TransferContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "Urc20WithdrawFutureContract":{
            var builder = Urc20WithdrawFutureContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "Urc20TransferOwnerContract":{
            var builder = Urc20TransferOwnerContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "Urc20ExchangeContract":{
            var builder = Urc20ExchangeContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          case "Urc20ApproveContract":{
            var builder = Urc20ApproveContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), builder, selfType);
            any = Any.pack(builder.build());
            break;
          }
          default:
        }
        if (any != null) {
          String value = ByteArray.toHexString(any.getValue().toByteArray());
          parameter.put(VALUE, value);
          contract.put("parameter", parameter);
          contracts.add(contract);
        }
      } catch (ParseException e) {
        logger.error("ParseException: {}", e.getMessage(), e);
      }
    }
    rawData.put("contract", contracts);
    jsonTransaction.put("raw_data", rawData);
    Transaction.Builder transactionBuilder = Transaction.newBuilder();
    try {
      JsonFormat.merge(jsonTransaction.toJSONString(), transactionBuilder, selfType);
      return transactionBuilder.build();
    } catch (ParseException e) {
      logger.error("ParseException: {}", e.getMessage(), e);
      return null;
    }
  }

  public static final long ONE_DAY = 24 * 60 * 60 * 1000;

  public static long makeDayTick(long timestamp){
    return timestamp - (timestamp % ONE_DAY);
  }

  public static byte[] makeFutureTokenIndexKey(byte[] ownerAddr, byte[] tokenKey, long dayTick){
    return ((new String(ownerAddr)) + "_" + (new String(tokenKey)) + "_" + dayTick).getBytes();
  }

  public static byte[] makeUrc20FutureTokenIndexKey(byte[] ownerAddr, String contractAddrBase58, long dayTick){
    return ((new String(ownerAddr)) + "_" + contractAddrBase58 + "_" + dayTick).getBytes();
  }

  public static byte[] makeFutureTransferIndexKey(byte[] ownerAddr, long dayTick){
    return ((new String(ownerAddr)) + "_" + dayTick).getBytes();
  }

  public static Descriptors.FieldDescriptor FUTURE_QR_FIELD_OWNER_ADDR = Protocol.FutureQuery.getDescriptor().findFieldByNumber(Protocol.FutureQuery.OWNER_ADDRESS_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor FUTURE_QR_FIELD_PAGE_SIZE = Protocol.FutureQuery.getDescriptor().findFieldByNumber(Protocol.FutureQuery.PAGE_SIZE_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor FUTURE_QR_FIELD_PAGE_INDEX = Protocol.FutureQuery.getDescriptor().findFieldByNumber(Protocol.FutureQuery.PAGE_INDEX_FIELD_NUMBER);

  public static Descriptors.FieldDescriptor TOKEN_QR_FIELD_NAME = Protocol.FutureTokenQuery.getDescriptor().findFieldByNumber(Protocol.FutureTokenQuery.TOKEN_NAME_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor TOKEN_QR_FIELD_OWNER_ADDR = Protocol.FutureTokenQuery.getDescriptor().findFieldByNumber(Protocol.FutureTokenQuery.OWNER_ADDRESS_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor TOKEN_QR_FIELD_PAGE_SIZE = Protocol.FutureTokenQuery.getDescriptor().findFieldByNumber(Protocol.FutureTokenQuery.PAGE_SIZE_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor TOKEN_QR_FIELD_PAGE_INDEX = Protocol.FutureTokenQuery.getDescriptor().findFieldByNumber(Protocol.FutureTokenQuery.PAGE_INDEX_FIELD_NUMBER);

  public static Descriptors.FieldDescriptor TOKEN_CREATE_FIELD_START_TIME= CreateTokenContract.getDescriptor().findFieldByNumber(CreateTokenContract.START_TIME_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor TOKEN_CREATE_FIELD_END_TIME= CreateTokenContract.getDescriptor().findFieldByNumber(CreateTokenContract.END_TIME_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor TOKEN_CREATE_FIELD_CRITICAL_TIME = CreateTokenContract.getDescriptor().findFieldByNumber(CreateTokenContract.CRITICAL_UPDATE_TIME_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor TOKEN_CREATE_FIELD_CREATE_ACC_FEE = CreateTokenContract.getDescriptor().findFieldByNumber(CreateTokenContract.CREATE_ACC_FEE_FIELD_NUMBER);

  public static Descriptors.FieldDescriptor FUTURE_TRANSFER_FIELD_TO_ADDR = FutureTransferContract.getDescriptor().findFieldByNumber(FutureTransferContract.TO_ADDRESS_FIELD_NUMBER);


  public static int DEFAULT_PAGE_SIZE = 20;
  public static int DEFAULT_PAGE_INDEX = 0;
  public static int MAX_PAGE_SIZE = 50;

  public static byte[] stringAsBytesUppercase(String str){
    return str.toUpperCase().getBytes();
  }

  public static void checkBodySize(String body) throws Exception {
    Args args = Args.getInstance();
    if (body.getBytes().length > args.getMaxMessageSize()) {
      throw new Exception("body size is too big, limit is " + args.getMaxMessageSize());
    }
  }

  /*
   do paging that start from page 0
   */
  public static <T> List<T> doPaging(List<T> input, int pageSize, int pageIndex){
      if(pageSize <= 0 || pageIndex < 0)
        throw new IllegalArgumentException("invalid paging info");
      if(input.isEmpty())
        return new ArrayList<>();
      int start = pageIndex * pageSize;
      int end = start + pageSize;
      if(start >= input.size())
        return new ArrayList<>();

      if(end >= input.size())
        end = input.size();
      return input.subList(start, end);
  }

  public static boolean getVisible(final HttpServletRequest request) {
    boolean visible = false;
    if (StringUtil.isNotBlank(request.getParameter(VISIBLE))) {
      visible = Boolean.valueOf(request.getParameter(VISIBLE));
    }
    return visible;
  }

  public static boolean getVisiblePost(final String input) {
    boolean visible = false;
    JSONObject jsonObject = JSON.parseObject(input);
    if (jsonObject.containsKey(VISIBLE)) {
      visible = jsonObject.getBoolean(VISIBLE);
    }

    return visible;
  }

  public static String getHexAddress(final String address) {
    if (address != null) {
      byte[] addressByte = Wallet.decodeFromBase58Check(address);
      return ByteArray.toHexString(addressByte);
    } else {
      return null;
    }
  }

  public static String getHexString(final String string) {
    return ByteArray.toHexString(ByteString.copyFromUtf8(string).toByteArray());
  }

  public static Transaction setTransactionPermissionId(JSONObject jsonObject, Transaction transaction) {
    if (jsonObject.containsKey(PERMISSION_ID)) {
      int permissionId = jsonObject.getInteger(PERMISSION_ID);
      if (permissionId > 0) {
        Transaction.raw.Builder raw = transaction.getRawData().toBuilder();
        Transaction.Contract.Builder contract = raw.getContract(0).toBuilder().setPermissionId(permissionId);
        raw.clearContract();
        raw.addContract(contract);
        return transaction.toBuilder().setRawData(raw).build();
      }
    }
    return transaction;
  }

  public static boolean getVisibleOnlyForSign(JSONObject jsonObject) {
    boolean visible = false;
    if (jsonObject.containsKey(VISIBLE)) {
      visible = jsonObject.getBoolean(VISIBLE);
    } else if (jsonObject.getJSONObject(TRANSACTION).containsKey(VISIBLE)) {
      visible = jsonObject.getJSONObject(TRANSACTION).getBoolean(VISIBLE);
    }
    return visible;
  }

  public static String parseMethod(String methodSign, String input) {
    byte[] selector = new byte[4];
    System.arraycopy(Hash.sha3(methodSign.getBytes()), 0, selector, 0, 4);
    if (StringUtils.isEmpty(input)) {
      return Hex.toHexString(selector);
    }
    else
    {
      return Hex.toHexString(selector) + input;
    }
  }

  public static long getJsonLongValue(final JSONObject jsonObject, final String key) {
    return getJsonLongValue(jsonObject, key, false);
  }

  public static long getJsonLongValue(JSONObject jsonObject, String key, boolean required) {
    BigDecimal bigDecimal = jsonObject.getBigDecimal(key);
    if (required && bigDecimal == null) {
      throw new InvalidParameterException("key [" + key + "] not exist");
    }
    return (bigDecimal == null) ? 0L : bigDecimal.longValueExact();
  }
}
