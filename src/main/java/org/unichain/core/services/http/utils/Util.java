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
import org.pf4j.util.StringUtils;
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

  //@todo move switch-case to mapping to prevent duplicated code
  //@addon declare new tx
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
          case CreateNftTemplateContract:{
            var parsedContract = contractParameter.unpack(CreateNftTemplateContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case MintNftTokenContract:{
            var parsedContract = contractParameter.unpack(MintNftTokenContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case RemoveNftMinterContract:{
            var parsedContract = contractParameter.unpack(RemoveNftMinterContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case AddNftMinterContract:{
            var parsedContract = contractParameter.unpack(AddNftMinterContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case RenounceNftMinterContract:{
            var parsedContract = contractParameter.unpack(RenounceNftMinterContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case BurnNftTokenContract:{
            var parsedContract = contractParameter.unpack(BurnNftTokenContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case ApproveNftTokenContract:{
            var parsedContract = contractParameter.unpack(ApproveNftTokenContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case ApproveForAllNftTokenContract:{
            var parsedContract = contractParameter.unpack(ApproveForAllNftTokenContract.class);
            contractJson = JSONObject.parseObject(JsonFormat.printToString(parsedContract, selfType));
            break;
          }
          case TransferNftTokenContract:{
            var parsedContract = contractParameter.unpack(TransferNftTokenContract.class);
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
          case "AccountCreateContract":
            AccountCreateContract.Builder accountCreateContractBuilder = AccountCreateContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), accountCreateContractBuilder, selfType);
            any = Any.pack(accountCreateContractBuilder.build());
            break;
          case "TransferContract":
            TransferContract.Builder transferContractBuilder = TransferContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), transferContractBuilder, selfType);
            any = Any.pack(transferContractBuilder.build());
            break;
          case "TransferAssetContract":
            TransferAssetContract.Builder transferAssetContractBuilder = TransferAssetContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), transferAssetContractBuilder, selfType);
            any = Any.pack(transferAssetContractBuilder.build());
            break;
          case "VoteAssetContract":
            VoteAssetContract.Builder voteAssetContractBuilder = VoteAssetContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), voteAssetContractBuilder, selfType);
            any = Any.pack(voteAssetContractBuilder.build());
            break;
          case "VoteWitnessContract":
            VoteWitnessContract.Builder voteWitnessContractBuilder = VoteWitnessContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), voteWitnessContractBuilder, selfType);
            any = Any.pack(voteWitnessContractBuilder.build());
            break;
          case "WitnessCreateContract":
            WitnessCreateContract.Builder witnessCreateContractBuilder = WitnessCreateContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), witnessCreateContractBuilder, selfType);
            any = Any.pack(witnessCreateContractBuilder.build());
            break;
          case "AssetIssueContract":
            AssetIssueContract.Builder assetIssueContractBuilder = AssetIssueContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), assetIssueContractBuilder, selfType);
            any = Any.pack(assetIssueContractBuilder.build());
            break;
          case "WitnessUpdateContract":
            WitnessUpdateContract.Builder witnessUpdateContractBuilder = WitnessUpdateContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), witnessUpdateContractBuilder, selfType);
            any = Any.pack(witnessUpdateContractBuilder.build());
            break;
          case "ParticipateAssetIssueContract":
            ParticipateAssetIssueContract.Builder participateAssetIssueContractBuilder = ParticipateAssetIssueContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), participateAssetIssueContractBuilder, selfType);
            any = Any.pack(participateAssetIssueContractBuilder.build());
            break;
          case "AccountUpdateContract":
            AccountUpdateContract.Builder accountUpdateContractBuilder = AccountUpdateContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), accountUpdateContractBuilder, selfType);
            any = Any.pack(accountUpdateContractBuilder.build());
            break;
          case "FreezeBalanceContract":
            FreezeBalanceContract.Builder freezeBalanceContractBuilder = FreezeBalanceContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), freezeBalanceContractBuilder, selfType);
            any = Any.pack(freezeBalanceContractBuilder.build());
            break;
          case "UnfreezeBalanceContract":
            UnfreezeBalanceContract.Builder unfreezeBalanceContractBuilder = UnfreezeBalanceContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), unfreezeBalanceContractBuilder, selfType);
            any = Any.pack(unfreezeBalanceContractBuilder.build());
            break;
          case "WithdrawBalanceContract":
            WithdrawBalanceContract.Builder withdrawBalanceContractBuilder = WithdrawBalanceContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), withdrawBalanceContractBuilder, selfType);
            any = Any.pack(withdrawBalanceContractBuilder.build());
            break;
          case "UnfreezeAssetContract":
            UnfreezeAssetContract.Builder unfreezeAssetContractBuilder = UnfreezeAssetContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), unfreezeAssetContractBuilder, selfType);
            any = Any.pack(unfreezeAssetContractBuilder.build());
            break;
          case "UpdateAssetContract":
            UpdateAssetContract.Builder updateAssetContractBuilder = UpdateAssetContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), updateAssetContractBuilder, selfType);
            any = Any.pack(updateAssetContractBuilder.build());
            break;
          case "ProposalCreateContract":
            ProposalCreateContract.Builder createContractBuilder = ProposalCreateContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), createContractBuilder, selfType);
            any = Any.pack(createContractBuilder.build());
            break;
          case "ProposalApproveContract":
            ProposalApproveContract.Builder approveContractBuilder = ProposalApproveContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), approveContractBuilder, selfType);
            any = Any.pack(approveContractBuilder.build());
            break;
          case "ProposalDeleteContract":
            ProposalDeleteContract.Builder deleteContractBuilder = ProposalDeleteContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), deleteContractBuilder, selfType);
            any = Any.pack(deleteContractBuilder.build());
            break;
          case "SetAccountIdContract":
            Contract.SetAccountIdContract.Builder setAccountid = Contract.SetAccountIdContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), setAccountid, selfType);
            any = Any.pack(setAccountid.build());
            break;
          case "CreateSmartContract":
            CreateSmartContract.Builder createSmartContractBuilder = CreateSmartContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), createSmartContractBuilder, selfType);
            any = Any.pack(createSmartContractBuilder.build());
            break;
          case "TriggerSmartContract":
            TriggerSmartContract.Builder triggerSmartContractBuilder = TriggerSmartContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), triggerSmartContractBuilder, selfType);
            any = Any.pack(triggerSmartContractBuilder.build());
            break;
          case "UpdateSettingContract":
            UpdateSettingContract.Builder updateSettingContractBuilder = UpdateSettingContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), updateSettingContractBuilder, selfType);
            any = Any.pack(updateSettingContractBuilder.build());
            break;
          case "ExchangeCreateContract":
            ExchangeCreateContract.Builder exchangeCreateContractBuilder = ExchangeCreateContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), exchangeCreateContractBuilder, selfType);
            any = Any.pack(exchangeCreateContractBuilder.build());
            break;
          case "ExchangeInjectContract":
            ExchangeInjectContract.Builder exchangeInjectContractBuilder = ExchangeInjectContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), exchangeInjectContractBuilder, selfType);
            any = Any.pack(exchangeInjectContractBuilder.build());
            break;
          case "ExchangeTransactionContract":
            ExchangeTransactionContract.Builder exchangeTransactionContractBuilder = ExchangeTransactionContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), exchangeTransactionContractBuilder, selfType);
            any = Any.pack(exchangeTransactionContractBuilder.build());
            break;
          case "ExchangeWithdrawContract":
            ExchangeWithdrawContract.Builder exchangeWithdrawContractBuilder = ExchangeWithdrawContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), exchangeWithdrawContractBuilder, selfType);
            any = Any.pack(exchangeWithdrawContractBuilder.build());
            break;
          case "UpdateEnergyLimitContract":
            UpdateEnergyLimitContract.Builder updateEnergyLimitContractBuilder = UpdateEnergyLimitContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), updateEnergyLimitContractBuilder, selfType);
            any = Any.pack(updateEnergyLimitContractBuilder.build());
            break;
          case "AccountPermissionUpdateContract":
            AccountPermissionUpdateContract.Builder accountPermissionUpdateContractBuilder = AccountPermissionUpdateContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), accountPermissionUpdateContractBuilder, selfType);
            any = Any.pack(accountPermissionUpdateContractBuilder.build());
            break;
          case "ClearABIContract":
            Contract.ClearABIContract.Builder clearABIContract = Contract.ClearABIContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), clearABIContract, selfType);
            any = Any.pack(clearABIContract.build());
            break;
          case "UpdateBrokerageContract":
            Contract.UpdateBrokerageContract.Builder updateBrokerageContract = Contract.UpdateBrokerageContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), updateBrokerageContract, selfType);
            any = Any.pack(updateBrokerageContract.build());
            break;

          /*
            future transfer
           */
          case "FutureTransferContract":
            Contract.FutureTransferContract.Builder futureTransferContract = Contract.FutureTransferContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), futureTransferContract, selfType);
            any = Any.pack(futureTransferContract.build());
            break;
          case "FutureWithdrawContract":
            Contract.FutureWithdrawContract.Builder futureWithdrawContract = Contract.FutureWithdrawContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), futureWithdrawContract, selfType);
            any = Any.pack(futureWithdrawContract.build());
            break;

          /*
             token economy
           */
          case "CreateTokenContract":
            CreateTokenContract.Builder createTokenContractBuilder = CreateTokenContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), createTokenContractBuilder, selfType);
            any = Any.pack(createTokenContractBuilder.build());
            break;
          case "TransferTokenOwnerContract":
            TransferTokenOwnerContract.Builder transferTokenOwnerContractBuilder = TransferTokenOwnerContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), transferTokenOwnerContractBuilder, selfType);
            any = Any.pack(transferTokenOwnerContractBuilder.build());
            break;
          case "ExchangeTokenContract":
            ExchangeTokenContract.Builder exchangeTokenContractBuilder = ExchangeTokenContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), exchangeTokenContractBuilder, selfType);
            any = Any.pack(exchangeTokenContractBuilder.build());
            break;
          case "ContributeTokenPoolFeeContract":
            ContributeTokenPoolFeeContract.Builder contributeTokenPoolContractBuilder = ContributeTokenPoolFeeContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), contributeTokenPoolContractBuilder, selfType);
            any = Any.pack(contributeTokenPoolContractBuilder.build());
            break;
          case "UpdateTokenParamsContract":
            UpdateTokenParamsContract.Builder updateTokenParamsContractBuilder = UpdateTokenParamsContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), updateTokenParamsContractBuilder, selfType);
            any = Any.pack(updateTokenParamsContractBuilder.build());
            break;
          case "MineTokenContract":
            MineTokenContract.Builder mineContractBuilder = MineTokenContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), mineContractBuilder, selfType);
            any = Any.pack(mineContractBuilder.build());
            break;
          case "BurnTokenContract":
            BurnTokenContract.Builder burnTokenContractBuilder = BurnTokenContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), burnTokenContractBuilder, selfType);
            any = Any.pack(burnTokenContractBuilder.build());
            break;
          case "TransferTokenContract":
            TransferTokenContract.Builder transferTokenContractBuilder = TransferTokenContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), transferTokenContractBuilder, selfType);
            any = Any.pack(transferTokenContractBuilder.build());
            break;
          case "WithdrawFutureTokenContract":
            WithdrawFutureTokenContract.Builder withdrawFutureTokenContractBuilder = WithdrawFutureTokenContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), withdrawFutureTokenContractBuilder, selfType);
            any = Any.pack(withdrawFutureTokenContractBuilder.build());
            break;
          case "CreateNftTemplateContract":
            CreateNftTemplateContract.Builder createNftTemplateContractBuilder = CreateNftTemplateContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), createNftTemplateContractBuilder, selfType);
            any = Any.pack(createNftTemplateContractBuilder.build());
            break;
          case "MintNftTokenContract":
            MintNftTokenContract.Builder  mintNftTokenContractBuilder = MintNftTokenContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), mintNftTokenContractBuilder, selfType);
            any = Any.pack(mintNftTokenContractBuilder.build());
            break;
          case "RemoveNftMinterContract":
            RemoveNftMinterContract.Builder removeNftMinterContractBuilder = RemoveNftMinterContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), removeNftMinterContractBuilder, selfType);
            any = Any.pack(removeNftMinterContractBuilder.build());
            break;

          case "AddNftMinterContract":
            AddNftMinterContract.Builder addNftMinterContractBuilder = AddNftMinterContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), addNftMinterContractBuilder, selfType);
            any = Any.pack(addNftMinterContractBuilder.build());
            break;

          case "RenounceNftMinterContract":
            RenounceNftMinterContract.Builder renounceNftMinterContractBuilder = RenounceNftMinterContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), renounceNftMinterContractBuilder, selfType);
            any = Any.pack(renounceNftMinterContractBuilder.build());
            break;

          case "BurnNftTokenContract":
            BurnNftTokenContract.Builder burnNftTokenContractBuilder = BurnNftTokenContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), burnNftTokenContractBuilder, selfType);
            any = Any.pack(burnNftTokenContractBuilder.build());
            break;

          case "ApproveNftTokenContract":
            ApproveNftTokenContract.Builder approveNftTokenContractBuilder = ApproveNftTokenContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), approveNftTokenContractBuilder, selfType);
            any = Any.pack(approveNftTokenContractBuilder.build());
            break;

          case "ApproveForAllNftTokenContract":
            ApproveForAllNftTokenContract.Builder approveForAllNftTokenContractBuilder = ApproveForAllNftTokenContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), approveForAllNftTokenContractBuilder, selfType);
            any = Any.pack(approveForAllNftTokenContractBuilder.build());
            break;

          case "TransferNftTokenContract":
            TransferNftTokenContract.Builder transferNftTokenContractBuilder = TransferNftTokenContract.newBuilder();
            JsonFormat.merge(parameter.getJSONObject(VALUE).toJSONString(), transferNftTokenContractBuilder, selfType);
            any = Any.pack(transferNftTokenContractBuilder.build());
            break;
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

  public static byte[] makeFutureTransferIndexKey(byte[] ownerAddr, long dayTick){
    return ((new String(ownerAddr)) + "_" + dayTick).getBytes();
  }

  public static Descriptors.FieldDescriptor ACC_FIELD_FUTURE_SUMMARY = Protocol.Account.getDescriptor().findFieldByNumber(Protocol.Account.FUTURE_SUPPLY_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor FUTURE_QR_FIELD_OWNER_ADDR = Protocol.FutureQuery.getDescriptor().findFieldByNumber(Protocol.FutureQuery.OWNER_ADDRESS_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor FUTURE_QR_FIELD_PAGE_SIZE = Protocol.FutureQuery.getDescriptor().findFieldByNumber(Protocol.FutureQuery.PAGE_SIZE_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor FUTURE_QR_FIELD_PAGE_INDEX = Protocol.FutureQuery.getDescriptor().findFieldByNumber(Protocol.FutureQuery.PAGE_INDEX_FIELD_NUMBER);

  public static Descriptors.FieldDescriptor TOKEN_QR_FIELD_NAME = Protocol.FutureTokenQuery.getDescriptor().findFieldByNumber(Protocol.FutureTokenQuery.TOKEN_NAME_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor TOKEN_QR_FIELD_OWNER_ADDR = Protocol.FutureTokenQuery.getDescriptor().findFieldByNumber(Protocol.FutureTokenQuery.OWNER_ADDRESS_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor TOKEN_QR_FIELD_PAGE_SIZE = Protocol.FutureTokenQuery.getDescriptor().findFieldByNumber(Protocol.FutureTokenQuery.PAGE_SIZE_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor TOKEN_QR_FIELD_PAGE_INDEX = Protocol.FutureTokenQuery.getDescriptor().findFieldByNumber(Protocol.FutureTokenQuery.PAGE_INDEX_FIELD_NUMBER);

  public static Descriptors.FieldDescriptor TOKEN_UPDATE_PARAMS_FIELD_OWNER_ADDR = UpdateTokenParamsContract.getDescriptor().findFieldByNumber(UpdateTokenParamsContract.OWNER_ADDRESS_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor TOKEN_UPDATE_PARAMS_FIELD_NAME = UpdateTokenParamsContract.getDescriptor().findFieldByNumber(UpdateTokenParamsContract.TOKEN_NAME_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor TOKEN_UPDATE_PARAMS_FIELD_FEE = UpdateTokenParamsContract.getDescriptor().findFieldByNumber(UpdateTokenParamsContract.AMOUNT_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor TOKEN_UPDATE_PARAMS_FIELD_LOT = UpdateTokenParamsContract.getDescriptor().findFieldByNumber(UpdateTokenParamsContract.LOT_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor TOKEN_UPDATE_PARAMS_FIELD_FEE_RATE = UpdateTokenParamsContract.getDescriptor().findFieldByNumber(UpdateTokenParamsContract.EXTRA_FEE_RATE_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor TOKEN_UPDATE_PARAMS_FIELD_URL = UpdateTokenParamsContract.getDescriptor().findFieldByNumber(UpdateTokenParamsContract.URL_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor TOKEN_UPDATE_PARAMS_FIELD_DESCRIPTION = UpdateTokenParamsContract.getDescriptor().findFieldByNumber(UpdateTokenParamsContract.DESCRIPTION_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor TOKEN_UPDATE_PARAMS_FIELD_TOTAL_SUPPLY = UpdateTokenParamsContract.getDescriptor().findFieldByNumber(UpdateTokenParamsContract.TOTAL_SUPPLY_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor TOKEN_UPDATE_PARAMS_FIELD_FEE_POOL = UpdateTokenParamsContract.getDescriptor().findFieldByNumber(UpdateTokenParamsContract.FEE_POOL_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor TOKEN_UPDATE_PARAMS_FIELD_EXCH_UNW_NUM = UpdateTokenParamsContract.getDescriptor().findFieldByNumber(UpdateTokenParamsContract.EXCH_UNX_NUM_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor TOKEN_UPDATE_PARAMS_FIELD_EXCH_TOKEN_NUM = UpdateTokenParamsContract.getDescriptor().findFieldByNumber(UpdateTokenParamsContract.EXCH_NUM_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor TOKEN_UPDATE_PARAMS_FIELD_CREATE_ACC_FEE = UpdateTokenParamsContract.getDescriptor().findFieldByNumber(UpdateTokenParamsContract.CREATE_ACC_FEE_FIELD_NUMBER);


  public static Descriptors.FieldDescriptor TOKEN_QUERY_FIELD_PAGE_INDEX= Protocol.TokenPoolQuery.getDescriptor().findFieldByNumber(Protocol.TokenPoolQuery.PAGE_INDEX_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor TOKEN_QUERY_FIELD_PAGE_SIZE= Protocol.TokenPoolQuery.getDescriptor().findFieldByNumber(Protocol.TokenPoolQuery.PAGE_SIZE_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor TOKEN_QUERY_FIELD_TOKEN_NAME= Protocol.TokenPoolQuery.getDescriptor().findFieldByNumber(Protocol.TokenPoolQuery.TOKEN_NAME_FIELD_NUMBER);

  public static Descriptors.FieldDescriptor TOKEN_CREATE_FIELD_START_TIME= CreateTokenContract.getDescriptor().findFieldByNumber(CreateTokenContract.START_TIME_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor TOKEN_CREATE_FIELD_END_TIME= CreateTokenContract.getDescriptor().findFieldByNumber(CreateTokenContract.END_TIME_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor TOKEN_CREATE_FIELD_CRITICAL_TIME = CreateTokenContract.getDescriptor().findFieldByNumber(CreateTokenContract.CRITICAL_UPDATE_TIME_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor FUTURE_TRANSFER_FIELD_TO_ADDR = FutureTransferContract.getDescriptor().findFieldByNumber(FutureTransferContract.TO_ADDRESS_FIELD_NUMBER);

  public static Descriptors.FieldDescriptor TOKEN_CREATE_FIELD_CREATE_ACC_FEE = CreateTokenContract.getDescriptor().findFieldByNumber(CreateTokenContract.CREATE_ACC_FEE_FIELD_NUMBER);

  public static Descriptors.FieldDescriptor NFT_CREATE_TEMPLATE_FIELD_MINTER = CreateNftTemplateContract.getDescriptor().findFieldByNumber(CreateNftTemplateContract.MINTER_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor NFT_TEMPLATE_ACCOUNT_FIELD_TAIL = Protocol.NftAccountTemplateRelation.getDescriptor().findFieldByNumber(Protocol.NftAccountTemplateRelation.TAIL_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor NFT_TEMPLATE_FIELD_NEXT = Protocol.NftTemplate.getDescriptor().findFieldByNumber(Protocol.NftTemplate.NEXT_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor NFT_TEMPLATE_FIELD_MINTER = Protocol.NftTemplate.getDescriptor().findFieldByNumber(Protocol.NftTemplate.MINTER_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor NFT_TEMPLATE_FIELD_NEXT_OF_MINTER = Protocol.NftTemplate.getDescriptor().findFieldByNumber(Protocol.NftTemplate.NEXT_OF_MINTER_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor NFT_TEMPLATE_FIELD_PREV_OF_MINTER = Protocol.NftTemplate.getDescriptor().findFieldByNumber(Protocol.NftTemplate.PREV_OF_MINTER_FIELD_NUMBER);

  public static Descriptors.FieldDescriptor NFT_MINT_FIELD_METADATA = MintNftTokenContract.getDescriptor().findFieldByNumber(MintNftTokenContract.METADATA_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor NFT_ACC_TOKEN_RELATION_FIELD_TAIL = Protocol.NftAccountTokenRelation.getDescriptor().findFieldByNumber(Protocol.NftAccountTokenRelation.TAIL_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor NFT_ACC_TOKEN_RELATION_FIELD_APPROVAL_FOR_ALL = Protocol.NftAccountTokenRelation.getDescriptor().findFieldByNumber(Protocol.NftAccountTokenRelation.APPROVED_FOR_ALL_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor NFT_ACC_TOKEN_RELATION_FIELD_TAIL_APPROVE = Protocol.NftAccountTokenRelation.getDescriptor().findFieldByNumber(Protocol.NftAccountTokenRelation.APPROVE_TAIL_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor NFT_ACC_TOKEN_RELATION_FIELD_HEAD_APPROVE = Protocol.NftAccountTokenRelation.getDescriptor().findFieldByNumber(Protocol.NftAccountTokenRelation.APPROVE_HEAD_FIELD_NUMBER);


  public static Descriptors.FieldDescriptor NFT_TOKEN_FIELD_APPROVAL = Protocol.NftToken.getDescriptor().findFieldByNumber(Protocol.NftToken.APPROVAL_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor NFT_TOKEN_FIELD_PREV = Protocol.NftToken.getDescriptor().findFieldByNumber(Protocol.NftToken.PREV_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor NFT_TOKEN_FIELD_NEXT = Protocol.NftToken.getDescriptor().findFieldByNumber(Protocol.NftToken.NEXT_FIELD_NUMBER);

  public static Descriptors.FieldDescriptor NFT_TOKEN_APPROVE_RELATION_FIELD_PREV = Protocol.NftTokenApproveRelation.getDescriptor().findFieldByNumber(Protocol.NftTokenApproveRelation.PREV_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor NFT_TOKEN_APPROVE_RELATION_FIELD_NEXT = Protocol.NftTokenApproveRelation.getDescriptor().findFieldByNumber(Protocol.NftTokenApproveRelation.NEXT_FIELD_NUMBER);


  public static Descriptors.FieldDescriptor NFT_TOKEN_QUERY_FIELD_PAGE_SIZE = Protocol.NftTokenQuery.getDescriptor().findFieldByNumber(Protocol.NftTokenQuery.PAGE_SIZE_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor NFT_TOKEN_QUERY_FIELD_PAGE_INDEX = Protocol.NftTokenQuery.getDescriptor().findFieldByNumber(Protocol.NftTokenQuery.PAGE_INDEX_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor NFT_TOKEN_QUERY_FIELD_CONTRACT = Protocol.NftTokenQuery.getDescriptor().findFieldByNumber(Protocol.NftTokenQuery.CONTRACT_FIELD_NUMBER);

  public static Descriptors.FieldDescriptor NFT_TEMPLATE_QUERY_FIELD_PAGE_SIZE = Protocol.NftTemplateQuery.getDescriptor().findFieldByNumber(Protocol.NftTemplateQuery.PAGE_SIZE_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor NFT_TEMPLATE_QUERY_FIELD_PAGE_INDEX = Protocol.NftTemplateQuery.getDescriptor().findFieldByNumber(Protocol.NftTemplateQuery.PAGE_INDEX_FIELD_NUMBER);

  public static Descriptors.FieldDescriptor NFT_TOKEN_APPROVE_QUERY_FIELD_PAGE_SIZE = Protocol.NftTokenApproveQuery.getDescriptor().findFieldByNumber(Protocol.NftTokenApproveQuery.PAGE_SIZE_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor NFT_TOKEN_APPROVE_QUERY_FIELD_PAGE_INDEX = Protocol.NftTokenApproveQuery.getDescriptor().findFieldByNumber(Protocol.NftTokenApproveQuery.PAGE_INDEX_FIELD_NUMBER);


  public static Descriptors.FieldDescriptor NFT_TOKEN_APPROVE_ALL_QUERY_FIELD_PAGE_SIZE = Protocol.NftTokenApproveAllQuery.getDescriptor().findFieldByNumber(Protocol.NftTokenApproveAllQuery.PAGE_SIZE_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor NFT_TOKEN_APPROVE_ALL_QUERY_FIELD_PAGE_INDEX = Protocol.NftTokenApproveAllQuery.getDescriptor().findFieldByNumber(Protocol.NftTokenApproveAllQuery.PAGE_INDEX_FIELD_NUMBER);

  public static Descriptors.FieldDescriptor POSBRIDGE_NEW_OWNER = PosBridgeSetupContract.getDescriptor().findFieldByNumber(PosBridgeSetupContract.NEW_OWNER_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor POSBRIDGE_MIN_VALIDATOR = PosBridgeSetupContract.getDescriptor().findFieldByNumber(PosBridgeSetupContract.MIN_VALIDATOR_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor POSBRIDGE_VALIDATORS= PosBridgeSetupContract.getDescriptor().findFieldByNumber(PosBridgeSetupContract.VALIDATORS_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor POSBRIDGE_VALIDATOR_F1= PosBridgeSetupContract.getDescriptor().findFieldByNumber(PosBridgeSetupContract.CONSENSUS_F1_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor POSBRIDGE_VALIDATOR_F2= PosBridgeSetupContract.getDescriptor().findFieldByNumber(PosBridgeSetupContract.CONSENSUS_F2_FIELD_NUMBER);


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
    //System.out.println(methodSign + ":" + Hex.toHexString(selector));
    if (StringUtils.isNullOrEmpty(input)) {
      return Hex.toHexString(selector);
    }

    return Hex.toHexString(selector) + input;
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
