/*
 * Copyright (c) [2016] [ <ether.camp> ]
 * This file is part of the ethereumJ library.
 *
 * The ethereumJ library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ethereumJ library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ethereumJ library. If not, see <http://www.gnu.org/licenses/>.
 */

package org.unx.core;

import static org.unx.common.utils.Commons.getAssetIssueStoreFinal;
import static org.unx.common.utils.Commons.getExchangeStoreFinal;
import static org.unx.common.utils.WalletUtil.isConstant;
import static org.unx.core.config.Parameter.ChainConstant.BLOCK_PRODUCED_INTERVAL;
import static org.unx.core.config.Parameter.ChainConstant.UNW_PRECISION;
import static org.unx.core.config.Parameter.DatabaseConstants.EXCHANGE_COUNT_LIMIT_MAX;
import static org.unx.core.config.Parameter.DatabaseConstants.MARKET_COUNT_LIMIT_MAX;
import static org.unx.core.config.Parameter.DatabaseConstants.PROPOSAL_COUNT_LIMIT_MAX;
import static org.unx.core.services.jsonrpc.JsonRpcImpl.EARLIEST_STR;

import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.ProtocolStringList;
import java.math.BigInteger;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.unx.api.GrpcAPI;
import org.unx.api.GrpcAPI.AccountNetMessage;
import org.unx.api.GrpcAPI.AccountResourceMessage;
import org.unx.api.GrpcAPI.Address;
import org.unx.api.GrpcAPI.AssetIssueList;
import org.unx.api.GrpcAPI.BlockList;
import org.unx.api.GrpcAPI.BytesMessage;
import org.unx.api.GrpcAPI.DecryptNotes;
import org.unx.api.GrpcAPI.DecryptNotes.NoteTx;
import org.unx.api.GrpcAPI.DecryptNotesURC20;
import org.unx.api.GrpcAPI.DelegatedResourceList;
import org.unx.api.GrpcAPI.DiversifierMessage;
import org.unx.api.GrpcAPI.ExchangeList;
import org.unx.api.GrpcAPI.ExpandedSpendingKeyMessage;
import org.unx.api.GrpcAPI.IncomingViewingKeyMessage;
import org.unx.api.GrpcAPI.NfParameters;
import org.unx.api.GrpcAPI.NfURC20Parameters;
import org.unx.api.GrpcAPI.Node;
import org.unx.api.GrpcAPI.NodeList;
import org.unx.api.GrpcAPI.NoteParameters;
import org.unx.api.GrpcAPI.NumberMessage;
import org.unx.api.GrpcAPI.PaymentAddressMessage;
import org.unx.api.GrpcAPI.PrivateParameters;
import org.unx.api.GrpcAPI.PrivateParametersWithoutAsk;
import org.unx.api.GrpcAPI.PrivateShieldedURC20Parameters;
import org.unx.api.GrpcAPI.PrivateShieldedURC20ParametersWithoutAsk;
import org.unx.api.GrpcAPI.ProposalList;
import org.unx.api.GrpcAPI.ReceiveNote;
import org.unx.api.GrpcAPI.Return;
import org.unx.api.GrpcAPI.Return.response_code;
import org.unx.api.GrpcAPI.ShieldedAddressInfo;
import org.unx.api.GrpcAPI.ShieldedURC20Parameters;
import org.unx.api.GrpcAPI.ShieldedURC20TriggerContractParameters;
import org.unx.api.GrpcAPI.SpendAuthSigParameters;
import org.unx.api.GrpcAPI.SpendNote;
import org.unx.api.GrpcAPI.SpendResult;
import org.unx.api.GrpcAPI.TransactionApprovedList;
import org.unx.api.GrpcAPI.TransactionExtention;
import org.unx.api.GrpcAPI.TransactionExtention.Builder;
import org.unx.api.GrpcAPI.TransactionInfoList;
import org.unx.api.GrpcAPI.WitnessList;
import org.unx.common.crypto.Hash;
import org.unx.common.crypto.SignInterface;
import org.unx.common.crypto.SignUtils;
import org.unx.common.overlay.discover.node.NodeHandler;
import org.unx.common.overlay.discover.node.NodeManager;
import org.unx.common.parameter.CommonParameter;
import org.unx.common.runtime.ProgramResult;
import org.unx.common.runtime.vm.LogInfo;
import org.unx.common.utils.ByteArray;
import org.unx.common.utils.ByteUtil;
import org.unx.common.utils.DecodeUtil;
import org.unx.common.utils.Sha256Hash;
import org.unx.common.utils.Utils;
import org.unx.common.utils.WalletUtil;
import org.unx.common.zksnark.IncrementalMerkleTreeContainer;
import org.unx.common.zksnark.IncrementalMerkleVoucherContainer;
import org.unx.common.zksnark.JLibrustzcash;
import org.unx.common.zksnark.LibrustzcashParam.ComputeNfParams;
import org.unx.common.zksnark.LibrustzcashParam.CrhIvkParams;
import org.unx.common.zksnark.LibrustzcashParam.IvkToPkdParams;
import org.unx.common.zksnark.LibrustzcashParam.SpendSigParams;
import org.unx.consensus.ConsensusDelegate;
import org.unx.core.actuator.Actuator;
import org.unx.core.actuator.ActuatorFactory;
import org.unx.core.actuator.VMActuator;
import org.unx.core.capsule.AbiCapsule;
import org.unx.core.capsule.AccountCapsule;
import org.unx.core.capsule.AssetIssueCapsule;
import org.unx.core.capsule.BlockBalanceTraceCapsule;
import org.unx.core.capsule.BlockCapsule;
import org.unx.core.capsule.BlockCapsule.BlockId;
import org.unx.core.capsule.BytesCapsule;
import org.unx.core.capsule.CodeCapsule;
import org.unx.core.capsule.ContractCapsule;
import org.unx.core.capsule.DelegatedResourceAccountIndexCapsule;
import org.unx.core.capsule.DelegatedResourceCapsule;
import org.unx.core.capsule.ExchangeCapsule;
import org.unx.core.capsule.IncrementalMerkleTreeCapsule;
import org.unx.core.capsule.IncrementalMerkleVoucherCapsule;
import org.unx.core.capsule.MarketAccountOrderCapsule;
import org.unx.core.capsule.MarketOrderCapsule;
import org.unx.core.capsule.MarketOrderIdListCapsule;
import org.unx.core.capsule.PedersenHashCapsule;
import org.unx.core.capsule.ProposalCapsule;
import org.unx.core.capsule.TransactionCapsule;
import org.unx.core.capsule.TransactionInfoCapsule;
import org.unx.core.capsule.TransactionResultCapsule;
import org.unx.core.capsule.TransactionRetCapsule;
import org.unx.core.capsule.WitnessCapsule;
import org.unx.core.capsule.utils.MarketUtils;
import org.unx.core.capsule.utils.TransactionUtil;
import org.unx.core.config.args.Args;
import org.unx.core.db.BandwidthProcessor;
import org.unx.core.db.BlockIndexStore;
import org.unx.core.db.EnergyProcessor;
import org.unx.core.db.Manager;
import org.unx.core.db.TransactionContext;
import org.unx.core.db2.core.Chainbase;
import org.unx.core.exception.AccountResourceInsufficientException;
import org.unx.core.exception.BadItemException;
import org.unx.core.exception.ContractExeException;
import org.unx.core.exception.ContractValidateException;
import org.unx.core.exception.DupTransactionException;
import org.unx.core.exception.HeaderNotFound;
import org.unx.core.exception.ItemNotFoundException;
import org.unx.core.exception.JsonRpcInvalidParamsException;
import org.unx.core.exception.NonUniqueObjectException;
import org.unx.core.exception.PermissionException;
import org.unx.core.exception.SignatureFormatException;
import org.unx.core.exception.StoreException;
import org.unx.core.exception.TaposException;
import org.unx.core.exception.TooBigTransactionException;
import org.unx.core.exception.TransactionExpirationException;
import org.unx.core.exception.VMIllegalException;
import org.unx.core.exception.ValidateSignatureException;
import org.unx.core.exception.ZksnarkException;
import org.unx.core.net.UnxNetDelegate;
import org.unx.core.net.UnxNetService;
import org.unx.core.net.message.TransactionMessage;
import org.unx.core.store.AccountIdIndexStore;
import org.unx.core.store.AccountStore;
import org.unx.core.store.AccountTraceStore;
import org.unx.core.store.BalanceTraceStore;
import org.unx.core.store.ContractStore;
import org.unx.core.store.MarketOrderStore;
import org.unx.core.store.MarketPairPriceToOrderStore;
import org.unx.core.store.MarketPairToPriceStore;
import org.unx.core.store.StoreFactory;
import org.unx.core.zen.ShieldedURC20ParametersBuilder;
import org.unx.core.zen.ZenTransactionBuilder;
import org.unx.core.zen.address.DiversifierT;
import org.unx.core.zen.address.ExpandedSpendingKey;
import org.unx.core.zen.address.IncomingViewingKey;
import org.unx.core.zen.address.KeyIo;
import org.unx.core.zen.address.PaymentAddress;
import org.unx.core.zen.address.SpendingKey;
import org.unx.core.zen.note.Note;
import org.unx.core.zen.note.NoteEncryption;
import org.unx.core.zen.note.OutgoingPlaintext;
import org.unx.protos.Protocol;
import org.unx.protos.Protocol.Account;
import org.unx.protos.Protocol.Block;
import org.unx.protos.Protocol.DelegatedResourceAccountIndex;
import org.unx.protos.Protocol.Exchange;
import org.unx.protos.Protocol.MarketOrder;
import org.unx.protos.Protocol.MarketOrderList;
import org.unx.protos.Protocol.MarketOrderPairList;
import org.unx.protos.Protocol.MarketPrice;
import org.unx.protos.Protocol.MarketPriceList;
import org.unx.protos.Protocol.Proposal;
import org.unx.protos.Protocol.Transaction;
import org.unx.protos.Protocol.Transaction.Contract;
import org.unx.protos.Protocol.Transaction.Contract.ContractType;
import org.unx.protos.Protocol.Transaction.Result.code;
import org.unx.protos.Protocol.TransactionInfo;
import org.unx.protos.contract.AssetIssueContractOuterClass.AssetIssueContract;
import org.unx.protos.contract.BalanceContract;
import org.unx.protos.contract.BalanceContract.BlockBalanceTrace;
import org.unx.protos.contract.BalanceContract.TransferContract;
import org.unx.protos.contract.ShieldContract.IncrementalMerkleTree;
import org.unx.protos.contract.ShieldContract.IncrementalMerkleVoucherInfo;
import org.unx.protos.contract.ShieldContract.OutputPoint;
import org.unx.protos.contract.ShieldContract.OutputPointInfo;
import org.unx.protos.contract.ShieldContract.PedersenHash;
import org.unx.protos.contract.ShieldContract.ReceiveDescription;
import org.unx.protos.contract.ShieldContract.ShieldedTransferContract;
import org.unx.protos.contract.SmartContractOuterClass.CreateSmartContract;
import org.unx.protos.contract.SmartContractOuterClass.SmartContract;
import org.unx.protos.contract.SmartContractOuterClass.SmartContractDataWrapper;
import org.unx.protos.contract.SmartContractOuterClass.TriggerSmartContract;
import org.unx.core.services.jsonrpc.JsonRpcApiUtil;

@Slf4j
@Component
public class Wallet {

  private static final String SHIELDED_ID_NOT_ALLOWED = "ShieldedTransactionApi is not allowed";
  private static final String PAYMENT_ADDRESS_FORMAT_WRONG = "paymentAddress format is wrong";
  private static final String SHIELDED_TRANSACTION_SCAN_RANGE =
      "request requires start_block_index >= 0 && end_block_index > "
          + "start_block_index && end_block_index - start_block_index <= 1000";
  private static String addressPreFixString = Constant.ADD_PRE_FIX_STRING_MAINNET;//default testnet
  private static final byte[] SHIELDED_URC20_LOG_TOPICS_MINT = Hash.sha3(ByteArray.fromString(
      "MintNewLeaf(uint256,bytes32,bytes32,bytes32,bytes32[21])"));
  private static final byte[] SHIELDED_URC20_LOG_TOPICS_TRANSFER = Hash.sha3(ByteArray.fromString(
      "TransferNewLeaf(uint256,bytes32,bytes32,bytes32,bytes32[21])"));
  private static final byte[] SHIELDED_URC20_LOG_TOPICS_BURN_LEAF = Hash.sha3(ByteArray.fromString(
      "BurnNewLeaf(uint256,bytes32,bytes32,bytes32,bytes32[21])"));
  private static final byte[] SHIELDED_URC20_LOG_TOPICS_BURN_TOKEN = Hash.sha3(ByteArray
      .fromString("TokenBurn(address,uint256,bytes32[3])"));
  private static final String BROADCAST_TRANS_FAILED = "Broadcast transaction {} failed, {}.";

  @Getter
  private final SignInterface cryptoEngine;
  @Autowired
  private UnxNetService unxNetService;
  @Autowired
  private UnxNetDelegate unxNetDelegate;
  @Autowired
  private Manager dbManager;
  @Autowired
  private ConsensusDelegate consensusDelegate;

  @Autowired
  private ChainBaseManager chainBaseManager;

  @Autowired
  private NodeManager nodeManager;
  private int minEffectiveConnection = Args.getInstance().getMinEffectiveConnection();
  private boolean unxCacheEnable = Args.getInstance().isUnxCacheEnable();
  public static final String CONTRACT_VALIDATE_EXCEPTION = "ContractValidateException: {}";
  public static final String CONTRACT_VALIDATE_ERROR = "Contract validate error : ";

  /**
   * Creates a new Wallet with a random ECKey.
   */
  public Wallet() {
    this.cryptoEngine = SignUtils.getGeneratedRandomSign(Utils.getRandom(),
        Args.getInstance().isECKeyCryptoEngine());
  }

  /**
   * Creates a Wallet with an existing ECKey.
   */
  public Wallet(final SignInterface cryptoEngine) {
    this.cryptoEngine = cryptoEngine;
    logger.info("wallet address: {}", ByteArray.toHexString(this.cryptoEngine.getAddress()));
  }

  public static String getAddressPreFixString() {
    return DecodeUtil.addressPreFixString;
  }

  public static void setAddressPreFixString(String addressPreFixString) {
    DecodeUtil.addressPreFixString = addressPreFixString;
  }

  public static byte getAddressPreFixByte() {
    return DecodeUtil.addressPreFixByte;
  }

  public static void setAddressPreFixByte(byte addressPreFixByte) {
    DecodeUtil.addressPreFixByte = addressPreFixByte;
  }

  //  public ShieldAddress generateShieldAddress() {
  //    ShieldAddress.Builder builder = ShieldAddress.newBuilder();
  //    ShieldAddressGenerator shieldAddressGenerator = new ShieldAddressGenerator();
  //
  //    byte[] privateKey = shieldAddressGenerator.generatePrivateKey();
  //    byte[] publicKey = shieldAddressGenerator.generatePublicKey(privateKey);
  //
  //    byte[] privateKeyEnc = shieldAddressGenerator.generatePrivateKeyEnc(privateKey);
  //    byte[] publicKeyEnc = shieldAddressGenerator.generatePublicKeyEnc(privateKeyEnc);
  //
  //    byte[] addPrivate = ByteUtil.merge(privateKey, privateKeyEnc);
  //    byte[] addPublic = ByteUtil.merge(publicKey, publicKeyEnc);
  //
  //    builder.setPrivateAddress(ByteString.copyFrom(addPrivate));
  //    builder.setPublicAddress(ByteString.copyFrom(addPublic));
  //    return builder.build();
  //  }

  public byte[] getAddress() {
    return cryptoEngine.getAddress();
  }

  public Account getAccount(Account account) {
    AccountStore accountStore = chainBaseManager.getAccountStore();
    AccountCapsule accountCapsule = accountStore.get(account.getAddress().toByteArray());
    if (accountCapsule == null) {
      return null;
    }
    accountCapsule.importAllAsset();
    BandwidthProcessor processor = new BandwidthProcessor(chainBaseManager);
    processor.updateUsage(accountCapsule);

    EnergyProcessor energyProcessor = new EnergyProcessor(
        chainBaseManager.getDynamicPropertiesStore(),
        chainBaseManager.getAccountStore());
    energyProcessor.updateUsage(accountCapsule);

    long genesisTimeStamp = chainBaseManager.getGenesisBlock().getTimeStamp();
    accountCapsule.setLatestConsumeTime(genesisTimeStamp
        + BLOCK_PRODUCED_INTERVAL * accountCapsule.getLatestConsumeTime());
    accountCapsule.setLatestConsumeFreeTime(genesisTimeStamp
        + BLOCK_PRODUCED_INTERVAL * accountCapsule.getLatestConsumeFreeTime());
    accountCapsule.setLatestConsumeTimeForEnergy(genesisTimeStamp
        + BLOCK_PRODUCED_INTERVAL * accountCapsule.getLatestConsumeTimeForEnergy());

    return accountCapsule.getInstance();
  }

  public Account getAccountById(Account account) {
    AccountStore accountStore = chainBaseManager.getAccountStore();
    AccountIdIndexStore accountIdIndexStore = chainBaseManager.getAccountIdIndexStore();
    byte[] address = accountIdIndexStore.get(account.getAccountId());
    if (address == null) {
      return null;
    }
    AccountCapsule accountCapsule = accountStore.get(address);
    if (accountCapsule == null) {
      return null;
    }
    accountCapsule.importAllAsset();
    BandwidthProcessor processor = new BandwidthProcessor(chainBaseManager);
    processor.updateUsage(accountCapsule);

    EnergyProcessor energyProcessor = new EnergyProcessor(
        chainBaseManager.getDynamicPropertiesStore(),
        chainBaseManager.getAccountStore());
    energyProcessor.updateUsage(accountCapsule);

    long genesisTimeStamp = chainBaseManager.getGenesisBlock().getTimeStamp();
    accountCapsule.setLatestConsumeTime(genesisTimeStamp
        + BLOCK_PRODUCED_INTERVAL * accountCapsule.getLatestConsumeTime());
    accountCapsule.setLatestConsumeFreeTime(genesisTimeStamp
        + BLOCK_PRODUCED_INTERVAL * accountCapsule.getLatestConsumeFreeTime());
    accountCapsule.setLatestConsumeTimeForEnergy(genesisTimeStamp
        + BLOCK_PRODUCED_INTERVAL * accountCapsule.getLatestConsumeTimeForEnergy());

    return accountCapsule.getInstance();
  }

  /**
   * Create a transaction by contract.
   */
  @Deprecated
  public Transaction createTransaction(TransferContract contract) {
    AccountStore accountStore = chainBaseManager.getAccountStore();
    return new TransactionCapsule(contract, accountStore).getInstance();
  }

  private void setTransaction(TransactionCapsule unx) {
    try {
      BlockId blockId = chainBaseManager.getHeadBlockId();
      if ("solid".equals(Args.getInstance().getUnxReferenceBlock())) {
        blockId = chainBaseManager.getSolidBlockId();
      }
      unx.setReference(blockId.getNum(), blockId.getBytes());
      long expiration = chainBaseManager.getHeadBlockTimeStamp() + Args.getInstance()
          .getUnxExpirationTimeInMilliseconds();
      unx.setExpiration(expiration);
      unx.setTimestamp();
    } catch (Exception e) {
      logger.error("Create transaction capsule failed.", e);
    }
  }

  private TransactionCapsule createTransactionCapsuleWithoutValidateWithTimeout(
      com.google.protobuf.Message message,
      ContractType contractType,
      long timeout) {
    TransactionCapsule unx = new TransactionCapsule(message, contractType);
    try {
      BlockId blockId = chainBaseManager.getHeadBlockId();
      if ("solid".equals(Args.getInstance().getUnxReferenceBlock())) {
        blockId = chainBaseManager.getSolidBlockId();
      }
      unx.setReference(blockId.getNum(), blockId.getBytes());

      long expiration;
      if (timeout > 0) {
        expiration =
            chainBaseManager.getHeadBlockTimeStamp() + timeout * 1000;
      } else {
        expiration =
            chainBaseManager.getHeadBlockTimeStamp() + Args.getInstance()
                .getUnxExpirationTimeInMilliseconds();
      }
      unx.setExpiration(expiration);
      unx.setTimestamp();
    } catch (Exception e) {
      logger.error("Create transaction capsule failed.", e);
    }
    return unx;
  }

  public TransactionCapsule createTransactionCapsuleWithoutValidate(
      com.google.protobuf.Message message,
      ContractType contractType,
      long timeout) {
    return createTransactionCapsuleWithoutValidateWithTimeout(message, contractType, timeout);
  }

  public TransactionCapsule createTransactionCapsuleWithoutValidate(
      com.google.protobuf.Message message,
      ContractType contractType) {
    return createTransactionCapsuleWithoutValidateWithTimeout(message, contractType, 0);
  }

  public TransactionCapsule createTransactionCapsule(com.google.protobuf.Message message,
      ContractType contractType) throws ContractValidateException {
    TransactionCapsule unx = new TransactionCapsule(message, contractType);
    if (contractType != ContractType.CreateSmartContract
        && contractType != ContractType.TriggerSmartContract) {
      List<Actuator> actList = ActuatorFactory.createActuator(unx, chainBaseManager);
      for (Actuator act : actList) {
        act.validate();
      }
    }

    if (contractType == ContractType.CreateSmartContract) {

      CreateSmartContract contract = ContractCapsule
          .getSmartContractFromTransaction(unx.getInstance());
      long percent = contract.getNewContract().getConsumeUserResourcePercent();
      if (percent < 0 || percent > 100) {
        throw new ContractValidateException("percent must be >= 0 and <= 100");
      }
    }
    setTransaction(unx);
    return unx;
  }

  /**
   * Broadcast a transaction.
   */
  public GrpcAPI.Return broadcastTransaction(Transaction signedTransaction) {
    GrpcAPI.Return.Builder builder = GrpcAPI.Return.newBuilder();
    TransactionCapsule unx = new TransactionCapsule(signedTransaction);
    unx.setTime(System.currentTimeMillis());
    Sha256Hash txID = unx.getTransactionId();
    try {
      TransactionMessage message = new TransactionMessage(signedTransaction.toByteArray());
      if (minEffectiveConnection != 0) {
        if (unxNetDelegate.getActivePeer().isEmpty()) {
          logger.warn("Broadcast transaction {} has failed, no connection.", txID);
          return builder.setResult(false).setCode(response_code.NO_CONNECTION)
              .setMessage(ByteString.copyFromUtf8("No connection."))
              .build();
        }

        int count = (int) unxNetDelegate.getActivePeer().stream()
            .filter(p -> !p.isNeedSyncFromUs() && !p.isNeedSyncFromPeer())
            .count();

        if (count < minEffectiveConnection) {
          String info = "Effective connection:" + count + " lt minEffectiveConnection:"
              + minEffectiveConnection;
          logger.warn("Broadcast transaction {} has failed. {}.", txID, info);
          return builder.setResult(false).setCode(response_code.NOT_ENOUGH_EFFECTIVE_CONNECTION)
              .setMessage(ByteString.copyFromUtf8(info))
              .build();
        }
      }

      if (dbManager.isTooManyPending()) {
        logger.warn("Broadcast transaction {} has failed, too many pending.", txID);
        return builder.setResult(false).setCode(response_code.SERVER_BUSY)
            .setMessage(ByteString.copyFromUtf8("Server busy.")).build();
      }

      if (unxCacheEnable) {
        if (dbManager.getTransactionIdCache().getIfPresent(txID) != null) {
          logger.warn("Broadcast transaction {} has failed, it already exists.", txID);
          return builder.setResult(false).setCode(response_code.DUP_TRANSACTION_ERROR)
              .setMessage(ByteString.copyFromUtf8("Transaction already exists.")).build();
        } else {
          dbManager.getTransactionIdCache().put(txID, true);
        }
      }

      if (chainBaseManager.getDynamicPropertiesStore().supportVM()) {
        unx.resetResult();
      }
      dbManager.pushTransaction(unx);
      int num = unxNetService.fastBroadcastTransaction(message);
      if (num == 0 && minEffectiveConnection != 0) {
        return builder.setResult(false).setCode(response_code.NOT_ENOUGH_EFFECTIVE_CONNECTION)
            .setMessage(ByteString.copyFromUtf8("P2P broadcast failed.")).build();
      } else {
        logger.info("Broadcast transaction {} to {} peers successfully.", txID, num);
        return builder.setResult(true).setCode(response_code.SUCCESS).build();
      }
    } catch (ValidateSignatureException e) {
      logger.error(BROADCAST_TRANS_FAILED, txID, e.getMessage());
      return builder.setResult(false).setCode(response_code.SIGERROR)
          .setMessage(ByteString.copyFromUtf8("Validate signature error: " + e.getMessage()))
          .build();
    } catch (ContractValidateException e) {
      logger.error(BROADCAST_TRANS_FAILED, txID, e.getMessage());
      return builder.setResult(false).setCode(response_code.CONTRACT_VALIDATE_ERROR)
          .setMessage(ByteString.copyFromUtf8(CONTRACT_VALIDATE_ERROR + e.getMessage()))
          .build();
    } catch (ContractExeException e) {
      logger.error(BROADCAST_TRANS_FAILED, txID, e.getMessage());
      return builder.setResult(false).setCode(response_code.CONTRACT_EXE_ERROR)
          .setMessage(ByteString.copyFromUtf8("Contract execute error : " + e.getMessage()))
          .build();
    } catch (AccountResourceInsufficientException e) {
      logger.error(BROADCAST_TRANS_FAILED, txID, e.getMessage());
      return builder.setResult(false).setCode(response_code.BANDWITH_ERROR)
          .setMessage(ByteString.copyFromUtf8("Account resource insufficient error."))
          .build();
    } catch (DupTransactionException e) {
      logger.error(BROADCAST_TRANS_FAILED, txID, e.getMessage());
      return builder.setResult(false).setCode(response_code.DUP_TRANSACTION_ERROR)
          .setMessage(ByteString.copyFromUtf8("Dup transaction."))
          .build();
    } catch (TaposException e) {
      logger.error(BROADCAST_TRANS_FAILED, txID, e.getMessage());
      return builder.setResult(false).setCode(response_code.TAPOS_ERROR)
          .setMessage(ByteString.copyFromUtf8("Tapos check error."))
          .build();
    } catch (TooBigTransactionException e) {
      logger.error(BROADCAST_TRANS_FAILED, txID, e.getMessage());
      return builder.setResult(false).setCode(response_code.TOO_BIG_TRANSACTION_ERROR)
          .setMessage(ByteString.copyFromUtf8("Transaction size is too big."))
          .build();
    } catch (TransactionExpirationException e) {
      logger.error(BROADCAST_TRANS_FAILED, txID, e.getMessage());
      return builder.setResult(false).setCode(response_code.TRANSACTION_EXPIRATION_ERROR)
          .setMessage(ByteString.copyFromUtf8("Transaction expired"))
          .build();
    } catch (Exception e) {
      logger.error(BROADCAST_TRANS_FAILED, txID, e.getMessage());
      return builder.setResult(false).setCode(response_code.OTHER_ERROR)
          .setMessage(ByteString.copyFromUtf8("Error: " + e.getMessage()))
          .build();
    }
  }

  public TransactionApprovedList getTransactionApprovedList(Transaction unx) {
    TransactionApprovedList.Builder tswBuilder = TransactionApprovedList.newBuilder();
    TransactionExtention.Builder exBuilder = TransactionExtention.newBuilder();
    exBuilder.setTransaction(unx);
    exBuilder.setTxid(ByteString.copyFrom(Sha256Hash.hash(CommonParameter
        .getInstance().isECKeyCryptoEngine(), unx.getRawData().toByteArray())));
    Return.Builder retBuilder = Return.newBuilder();
    retBuilder.setResult(true).setCode(response_code.SUCCESS);
    exBuilder.setResult(retBuilder);
    tswBuilder.setTransaction(exBuilder);
    TransactionApprovedList.Result.Builder resultBuilder = TransactionApprovedList.Result
        .newBuilder();

    if (unx.getRawData().getContractCount() == 0) {
      resultBuilder.setCode(TransactionApprovedList.Result.response_code.OTHER_ERROR);
      resultBuilder.setMessage("Invalid transaction: no valid contract");
    } else {
      try {
        Contract contract = unx.getRawData().getContract(0);
        byte[] owner = TransactionCapsule.getOwner(contract);
        AccountCapsule account = chainBaseManager.getAccountStore().get(owner);
        if (account == null) {
          throw new PermissionException("Account does not exist!");
        }

        if (unx.getSignatureCount() > 0) {
          List<ByteString> approveList = new ArrayList<ByteString>();
          byte[] hash = Sha256Hash.hash(CommonParameter
              .getInstance().isECKeyCryptoEngine(), unx.getRawData().toByteArray());
          for (ByteString sig : unx.getSignatureList()) {
            if (sig.size() < 65) {
              throw new SignatureFormatException(
                  "Signature size is " + sig.size());
            }
            String base64 = TransactionCapsule.getBase64FromByteString(sig);
            byte[] address = SignUtils.signatureToAddress(hash, base64, Args.getInstance()
                .isECKeyCryptoEngine());
            approveList.add(ByteString.copyFrom(address)); //out put approve list.
          }
          tswBuilder.addAllApprovedList(approveList);
        }
        resultBuilder.setCode(TransactionApprovedList.Result.response_code.SUCCESS);
      } catch (SignatureFormatException signEx) {
        resultBuilder.setCode(TransactionApprovedList.Result.response_code.SIGNATURE_FORMAT_ERROR);
        resultBuilder.setMessage(signEx.getMessage());
      } catch (SignatureException signEx) {
        resultBuilder.setCode(TransactionApprovedList.Result.response_code.COMPUTE_ADDRESS_ERROR);
        resultBuilder.setMessage(signEx.getMessage());
      } catch (Exception ex) {
        resultBuilder.setCode(TransactionApprovedList.Result.response_code.OTHER_ERROR);
        resultBuilder.setMessage(ex.getClass() + " : " + ex.getMessage());
      }
    }

    tswBuilder.setResult(resultBuilder);
    return tswBuilder.build();
  }

  public byte[] pass2Key(byte[] passPhrase) {
    return Sha256Hash.hash(CommonParameter
        .getInstance().isECKeyCryptoEngine(), passPhrase);
  }

  public byte[] createAddress(byte[] passPhrase) {
    byte[] privateKey = pass2Key(passPhrase);
    SignInterface ecKey = SignUtils.fromPrivate(privateKey,
        Args.getInstance().isECKeyCryptoEngine());
    return ecKey.getAddress();
  }

  public Block getNowBlock() {
    List<BlockCapsule> blockList = chainBaseManager.getBlockStore().getBlockByLatestNum(1);
    if (CollectionUtils.isEmpty(blockList)) {
      return null;
    } else {
      return blockList.get(0).getInstance();
    }
  }

  public Block getBlockByNum(long blockNum) {
    try {
      return chainBaseManager.getBlockByNum(blockNum).getInstance();
    } catch (StoreException e) {
      logger.info(e.getMessage());
      return null;
    }
  }

  public BlockCapsule getBlockCapsuleByNum(long blockNum) {
    try {
      return chainBaseManager.getBlockByNum(blockNum);
    } catch (StoreException e) {
      logger.info(e.getMessage());
      return null;
    }
  }

  public long getTransactionCountByBlockNum(long blockNum) {
    long count = 0;

    try {
      Block block = chainBaseManager.getBlockByNum(blockNum).getInstance();
      count = block.getTransactionsCount();
    } catch (StoreException e) {
      logger.error(e.getMessage());
    }

    return count;
  }

  public Block getByJsonBlockId(String id) throws JsonRpcInvalidParamsException {
    if (EARLIEST_STR.equalsIgnoreCase(id)) {
      return getBlockByNum(0);
    } else if ("latest".equalsIgnoreCase(id)) {
      return getNowBlock();
    } else if ("pending".equalsIgnoreCase(id)) {
      throw new JsonRpcInvalidParamsException("TAG pending not supported");
    } else {
      long blockNumber;
      try {
        blockNumber = ByteArray.hexToBigInteger(id).longValue();
      } catch (Exception e) {
        throw new JsonRpcInvalidParamsException("invalid block number");
      }

      return getBlockByNum(blockNumber);
    }
  }

  public List<Transaction> getTransactionsByJsonBlockId(String id)
      throws JsonRpcInvalidParamsException {
    if ("pending".equalsIgnoreCase(id)) {
      throw new JsonRpcInvalidParamsException("TAG pending not supported");
    } else {
      Block block = getByJsonBlockId(id);
      return block != null ? block.getTransactionsList() : null;
    }
  }

  public WitnessList getWitnessList() {
    WitnessList.Builder builder = WitnessList.newBuilder();
    List<WitnessCapsule> witnessCapsuleList = chainBaseManager.getWitnessStore().getAllWitnesses();
    witnessCapsuleList
        .forEach(witnessCapsule -> builder.addWitnesses(witnessCapsule.getInstance()));
    return builder.build();
  }

  public ProposalList getProposalList() {
    ProposalList.Builder builder = ProposalList.newBuilder();
    List<ProposalCapsule> proposalCapsuleList =
        chainBaseManager.getProposalStore().getAllProposals();
    proposalCapsuleList
        .forEach(proposalCapsule -> builder.addProposals(proposalCapsule.getInstance()));
    return builder.build();
  }

  public DelegatedResourceList getDelegatedResource(ByteString fromAddress, ByteString toAddress) {
    DelegatedResourceList.Builder builder = DelegatedResourceList.newBuilder();
    byte[] dbKey = DelegatedResourceCapsule
        .createDbKey(fromAddress.toByteArray(), toAddress.toByteArray());
    DelegatedResourceCapsule delegatedResourceCapsule = chainBaseManager.getDelegatedResourceStore()
        .get(dbKey);
    if (delegatedResourceCapsule != null) {
      builder.addDelegatedResource(delegatedResourceCapsule.getInstance());
    }
    return builder.build();
  }

  public DelegatedResourceAccountIndex getDelegatedResourceAccountIndex(ByteString address) {
    DelegatedResourceAccountIndexCapsule accountIndexCapsule =
        chainBaseManager.getDelegatedResourceAccountIndexStore().get(address.toByteArray());
    if (accountIndexCapsule != null) {
      return accountIndexCapsule.getInstance();
    } else {
      return null;
    }
  }

  public ExchangeList getExchangeList() {
    ExchangeList.Builder builder = ExchangeList.newBuilder();
    List<ExchangeCapsule> exchangeCapsuleList =
        getExchangeStoreFinal(chainBaseManager.getDynamicPropertiesStore(),
            chainBaseManager.getExchangeStore(),
            chainBaseManager.getExchangeV2Store()).getAllExchanges();

    exchangeCapsuleList
        .forEach(exchangeCapsule -> builder.addExchanges(exchangeCapsule.getInstance()));
    return builder.build();
  }

  public Protocol.ChainParameters getChainParameters() {
    Protocol.ChainParameters.Builder builder = Protocol.ChainParameters.newBuilder();

    // MAINTENANCE_TIME_INTERVAL, //ms  ,0
    builder.addChainParameter(
        Protocol.ChainParameters.ChainParameter.newBuilder()
            .setKey("getMaintenanceTimeInterval")
            .setValue(chainBaseManager.getDynamicPropertiesStore().getMaintenanceTimeInterval())
            .build());
    //    ACCOUNT_UPGRADE_COST, //GINZA ,1
    builder.addChainParameter(
        Protocol.ChainParameters.ChainParameter.newBuilder()
            .setKey("getAccountUpgradeCost")
            .setValue(chainBaseManager.getDynamicPropertiesStore().getAccountUpgradeCost())
            .build());
    //    CREATE_ACCOUNT_FEE, //GINZA ,2
    builder.addChainParameter(
        Protocol.ChainParameters.ChainParameter.newBuilder()
            .setKey("getCreateAccountFee")
            .setValue(chainBaseManager.getDynamicPropertiesStore().getCreateAccountFee())
            .build());
    //    TRANSACTION_FEE, //GINZA ,3
    builder.addChainParameter(
        Protocol.ChainParameters.ChainParameter.newBuilder()
            .setKey("getTransactionFee")
            .setValue(chainBaseManager.getDynamicPropertiesStore().getTransactionFee())
            .build());
    //    ASSET_ISSUE_FEE, //GINZA ,4
    builder.addChainParameter(
        Protocol.ChainParameters.ChainParameter.newBuilder()
            .setKey("getAssetIssueFee")
            .setValue(chainBaseManager.getDynamicPropertiesStore().getAssetIssueFee())
            .build());
    //    WITNESS_PAY_PER_BLOCK, //GINZA ,5
    builder.addChainParameter(
        Protocol.ChainParameters.ChainParameter.newBuilder()
            .setKey("getWitnessPayPerBlock")
            .setValue(chainBaseManager.getDynamicPropertiesStore().getWitnessPayPerBlock())
            .build());
    //    WITNESS_STANDBY_ALLOWANCE, //GINZA ,6
    builder.addChainParameter(
        Protocol.ChainParameters.ChainParameter.newBuilder()
            .setKey("getWitnessStandbyAllowance")
            .setValue(chainBaseManager.getDynamicPropertiesStore().getWitnessStandbyAllowance())
            .build());
    //    CREATE_NEW_ACCOUNT_FEE_IN_SYSTEM_CONTRACT, //GINZA ,7
    builder.addChainParameter(
        Protocol.ChainParameters.ChainParameter.newBuilder()
            .setKey("getCreateNewAccountFeeInSystemContract")
            .setValue(chainBaseManager.getDynamicPropertiesStore()
                .getCreateNewAccountFeeInSystemContract())
            .build());
    //    CREATE_NEW_ACCOUNT_BANDWIDTH_RATE, // 1 ~ ,8
    builder.addChainParameter(
        Protocol.ChainParameters.ChainParameter.newBuilder()
            .setKey("getCreateNewAccountBandwidthRate")
            .setValue(chainBaseManager.getDynamicPropertiesStore()
                .getCreateNewAccountBandwidthRate()).build());
    //    ALLOW_CREATION_OF_CONTRACTS, // 0 / >0 ,9
    builder.addChainParameter(
        Protocol.ChainParameters.ChainParameter.newBuilder()
            .setKey("getAllowCreationOfContracts")
            .setValue(chainBaseManager.getDynamicPropertiesStore().getAllowCreationOfContracts())
            .build());
    //    REMOVE_THE_POWER_OF_THE_GR,  // 1 ,10
    builder.addChainParameter(
        Protocol.ChainParameters.ChainParameter.newBuilder()
            .setKey("getRemoveThePowerOfTheGr")
            .setValue(chainBaseManager.getDynamicPropertiesStore().getRemoveThePowerOfTheGr())
            .build());
    //    ENERGY_FEE, // GINZA, 11
    builder.addChainParameter(
        Protocol.ChainParameters.ChainParameter.newBuilder()
            .setKey("getEnergyFee")
            .setValue(chainBaseManager.getDynamicPropertiesStore().getEnergyFee())
            .build());
    //    EXCHANGE_CREATE_FEE, // GINZA, 12
    builder.addChainParameter(
        Protocol.ChainParameters.ChainParameter.newBuilder()
            .setKey("getExchangeCreateFee")
            .setValue(chainBaseManager.getDynamicPropertiesStore().getExchangeCreateFee())
            .build());
    //    MAX_CPU_TIME_OF_ONE_TX, // ms, 13
    builder.addChainParameter(
        Protocol.ChainParameters.ChainParameter.newBuilder()
            .setKey("getMaxCpuTimeOfOneTx")
            .setValue(chainBaseManager.getDynamicPropertiesStore().getMaxCpuTimeOfOneTx())
            .build());
    //    ALLOW_UPDATE_ACCOUNT_NAME, // 1, 14
    builder.addChainParameter(
        Protocol.ChainParameters.ChainParameter.newBuilder()
            .setKey("getAllowUpdateAccountName")
            .setValue(chainBaseManager.getDynamicPropertiesStore().getAllowUpdateAccountName())
            .build());
    //    ALLOW_SAME_TOKEN_NAME, // 1, 15
    builder.addChainParameter(
        Protocol.ChainParameters.ChainParameter.newBuilder()
            .setKey("getAllowSameTokenName")
            .setValue(chainBaseManager.getDynamicPropertiesStore().getAllowSameTokenName())
            .build());
    //    ALLOW_DELEGATE_RESOURCE, // 0, 16
    builder.addChainParameter(
        Protocol.ChainParameters.ChainParameter.newBuilder()
            .setKey("getAllowDelegateResource")
            .setValue(chainBaseManager.getDynamicPropertiesStore().getAllowDelegateResource())
            .build());
    //    TOTAL_ENERGY_LIMIT, // 50,000,000,000, 17
    builder.addChainParameter(
        Protocol.ChainParameters.ChainParameter.newBuilder()
            .setKey("getTotalEnergyLimit")
            .setValue(chainBaseManager.getDynamicPropertiesStore().getTotalEnergyLimit())
            .build());
    //    ALLOW_UVM_TRANSFER_URC10, // 1, 18
    builder.addChainParameter(
        Protocol.ChainParameters.ChainParameter.newBuilder()
            .setKey("getAllowUvmTransferUrc10")
            .setValue(chainBaseManager.getDynamicPropertiesStore().getAllowUvmTransferUrc10())
            .build());
    //    TOTAL_CURRENT_ENERGY_LIMIT, // 50,000,000,000, 19
    builder.addChainParameter(
        Protocol.ChainParameters.ChainParameter.newBuilder()
            .setKey("getTotalEnergyCurrentLimit")
            .setValue(chainBaseManager.getDynamicPropertiesStore().getTotalEnergyCurrentLimit())
            .build());
    //    ALLOW_MULTI_SIGN, // 1, 20
    builder.addChainParameter(
        Protocol.ChainParameters.ChainParameter.newBuilder()
            .setKey("getAllowMultiSign")
            .setValue(chainBaseManager.getDynamicPropertiesStore().getAllowMultiSign())
            .build());
    //    ALLOW_ADAPTIVE_ENERGY, // 1, 21
    builder.addChainParameter(
        Protocol.ChainParameters.ChainParameter.newBuilder()
            .setKey("getAllowAdaptiveEnergy")
            .setValue(chainBaseManager.getDynamicPropertiesStore().getAllowAdaptiveEnergy())
            .build());
    //other chainParameters
    builder.addChainParameter(Protocol.ChainParameters.ChainParameter.newBuilder()
        .setKey("getTotalEnergyTargetLimit")
        .setValue(chainBaseManager.getDynamicPropertiesStore().getTotalEnergyTargetLimit())
        .build());

    builder.addChainParameter(Protocol.ChainParameters.ChainParameter.newBuilder()
        .setKey("getTotalEnergyAverageUsage")
        .setValue(chainBaseManager.getDynamicPropertiesStore().getTotalEnergyAverageUsage())
        .build());

    builder.addChainParameter(Protocol.ChainParameters.ChainParameter.newBuilder()
        .setKey("getUpdateAccountPermissionFee")
        .setValue(chainBaseManager.getDynamicPropertiesStore().getUpdateAccountPermissionFee())
        .build());

    builder.addChainParameter(Protocol.ChainParameters.ChainParameter.newBuilder()
        .setKey("getMultiSignFee")
        .setValue(chainBaseManager.getDynamicPropertiesStore().getMultiSignFee())
        .build());

    builder.addChainParameter(Protocol.ChainParameters.ChainParameter.newBuilder()
        .setKey("getAllowAccountStateRoot")
        .setValue(chainBaseManager.getDynamicPropertiesStore().getAllowAccountStateRoot())
        .build());

    builder.addChainParameter(Protocol.ChainParameters.ChainParameter.newBuilder()
        .setKey("getAllowProtoFilterNum")
        .setValue(chainBaseManager.getDynamicPropertiesStore().getAllowProtoFilterNum())
        .build());

    // ALLOW_UVM_CONSTANTINOPLE
    builder.addChainParameter(Protocol.ChainParameters.ChainParameter.newBuilder()
        .setKey("getAllowUvmConstantinople")
        .setValue(chainBaseManager.getDynamicPropertiesStore().getAllowUvmConstantinople())
        .build());

    builder.addChainParameter(Protocol.ChainParameters.ChainParameter.newBuilder()
        .setKey("getAllowUvmSolidity059")
        .setValue(chainBaseManager.getDynamicPropertiesStore().getAllowUvmSolidity059())
        .build());

    // ALLOW_UVM_ISTANBUL
    builder.addChainParameter(
        Protocol.ChainParameters.ChainParameter.newBuilder().setKey("getAllowUvmIstanbul")
            .setValue(dbManager.getDynamicPropertiesStore().getAllowUvmIstanbul()).build());

    // ALLOW_ZKSNARK_TRANSACTION
    //    builder.addChainParameter(
    //        Protocol.ChainParameters.ChainParameter.newBuilder()
    //            .setKey("getAllowShieldedTransaction")
    //            .setValue(dbManager.getDynamicPropertiesStore().getAllowShieldedTransaction())
    //            .build());
    //
    //    // SHIELDED_TRANSACTION_FEE
    //    builder.addChainParameter(
    //        Protocol.ChainParameters.ChainParameter.newBuilder()
    //            .setKey("getShieldedTransactionFee")
    //            .setValue(dbManager.getDynamicPropertiesStore().getShieldedTransactionFee())
    //            .build());
    //
    //    // ShieldedTransactionCreateAccountFee
    //    builder.addChainParameter(
    //        Protocol.ChainParameters.ChainParameter.newBuilder()
    //            .setKey("getShieldedTransactionCreateAccountFee")
    //            .setValue(
    //                dbManager.getDynamicPropertiesStore()
    //                .getShieldedTransactionCreateAccountFee())
    //            .build());

    // ALLOW_SHIELDED_URC20_TRANSACTION
    builder.addChainParameter(
        Protocol.ChainParameters.ChainParameter.newBuilder()
            .setKey("getAllowShieldedURC20Transaction")
            .setValue(
                dbManager.getDynamicPropertiesStore().getAllowShieldedURC20Transaction())
            .build());

    builder.addChainParameter(Protocol.ChainParameters.ChainParameter.newBuilder()
        .setKey("getForbidTransferToContract")
        .setValue(dbManager.getDynamicPropertiesStore().getForbidTransferToContract())
        .build());

    builder.addChainParameter(Protocol.ChainParameters.ChainParameter.newBuilder()
        .setKey("getAdaptiveResourceLimitTargetRatio")
        .setValue(
            chainBaseManager.getDynamicPropertiesStore()
                .getAdaptiveResourceLimitTargetRatio() / (24 * 60)).build());

    builder.addChainParameter(Protocol.ChainParameters.ChainParameter.newBuilder()
        .setKey("getAdaptiveResourceLimitMultiplier")
        .setValue(chainBaseManager.getDynamicPropertiesStore().getAdaptiveResourceLimitMultiplier())
        .build());

    builder.addChainParameter(Protocol.ChainParameters.ChainParameter.newBuilder()
        .setKey("getChangeDelegation")
        .setValue(chainBaseManager.getDynamicPropertiesStore().getChangeDelegation())
        .build());

    builder.addChainParameter(Protocol.ChainParameters.ChainParameter.newBuilder()
        .setKey("getWitness127PayPerBlock")
        .setValue(chainBaseManager.getDynamicPropertiesStore().getWitness127PayPerBlock())
        .build());

    builder.addChainParameter(
        Protocol.ChainParameters.ChainParameter.newBuilder()
            .setKey("getAllowMarketTransaction")
            .setValue(dbManager.getDynamicPropertiesStore().getAllowMarketTransaction())
            .build());

    builder.addChainParameter(Protocol.ChainParameters.ChainParameter.newBuilder()
        .setKey("getMarketSellFee")
        .setValue(dbManager.getDynamicPropertiesStore().getMarketSellFee())
        .build());

    builder.addChainParameter(Protocol.ChainParameters.ChainParameter.newBuilder()
        .setKey("getMarketCancelFee")
        .setValue(dbManager.getDynamicPropertiesStore().getMarketCancelFee())
        .build());

    builder.addChainParameter(Protocol.ChainParameters.ChainParameter.newBuilder()
        .setKey("getAllowPBFT")
        .setValue(dbManager.getDynamicPropertiesStore().getAllowPBFT())
        .build());

    builder.addChainParameter(Protocol.ChainParameters.ChainParameter.newBuilder()
        .setKey("getAllowTransactionFeePool")
        .setValue(dbManager.getDynamicPropertiesStore().getAllowTransactionFeePool())
        .build());

    builder.addChainParameter(Protocol.ChainParameters.ChainParameter.newBuilder()
        .setKey("getMaxFeeLimit")
        .setValue(dbManager.getDynamicPropertiesStore().getMaxFeeLimit())
        .build());

    builder.addChainParameter(Protocol.ChainParameters.ChainParameter.newBuilder()
        .setKey("getAllowOptimizeBlackHole")
        .setValue(dbManager.getDynamicPropertiesStore().getAllowBlackHoleOptimization())
        .build());

    builder.addChainParameter(Protocol.ChainParameters.ChainParameter.newBuilder()
        .setKey("getAllowNewResourceModel")
        .setValue(dbManager.getDynamicPropertiesStore().getAllowNewResourceModel())
        .build());

    builder.addChainParameter(Protocol.ChainParameters.ChainParameter.newBuilder()
        .setKey("getAllowUvmFreeze")
        .setValue(dbManager.getDynamicPropertiesStore().getAllowUvmFreeze())
        .build());

    builder.addChainParameter(Protocol.ChainParameters.ChainParameter.newBuilder()
        .setKey("getAllowUvmVote")
        .setValue(dbManager.getDynamicPropertiesStore().getAllowUvmVote())
        .build());

    builder.addChainParameter(Protocol.ChainParameters.ChainParameter.newBuilder()
        .setKey("getAllowUvmLondon")
        .setValue(dbManager.getDynamicPropertiesStore().getAllowUvmLondon())
        .build());

    builder.addChainParameter(Protocol.ChainParameters.ChainParameter.newBuilder()
        .setKey("getAllowUvmCompatibleEvm")
        .setValue(dbManager.getDynamicPropertiesStore().getAllowUvmCompatibleEvm())
        .build());

    builder.addChainParameter(Protocol.ChainParameters.ChainParameter.newBuilder()
        .setKey("getAllowAccountAssetOptimization")
        .setValue(dbManager.getDynamicPropertiesStore().getAllowAccountAssetOptimization())
        .build());

    builder.addChainParameter(Protocol.ChainParameters.ChainParameter.newBuilder()
        .setKey("getFreeNetLimit")
        .setValue(dbManager.getDynamicPropertiesStore().getFreeNetLimit())
        .build());

    builder.addChainParameter(Protocol.ChainParameters.ChainParameter.newBuilder()
        .setKey("getTotalNetLimit")
        .setValue(dbManager.getDynamicPropertiesStore().getTotalNetLimit())
        .build());

    builder.addChainParameter(Protocol.ChainParameters.ChainParameter.newBuilder()
        .setKey("getAllowHigherLimitForMaxCpuTimeOfOneTx")
        .setValue(dbManager.getDynamicPropertiesStore().getAllowHigherLimitForMaxCpuTimeOfOneTx())
        .build());
    builder.addChainParameter(Protocol.ChainParameters.ChainParameter.newBuilder()
        .setKey("getAllowAssetOptimization")
        .setValue(dbManager.getDynamicPropertiesStore().getAllowAssetOptimization())
        .build());

    return builder.build();
  }

  public AssetIssueList getAssetIssueList() {
    BandwidthProcessor processor = new BandwidthProcessor(chainBaseManager);
    AssetIssueList.Builder builder = AssetIssueList.newBuilder();

    getAssetIssueStoreFinal(chainBaseManager.getDynamicPropertiesStore(),
        chainBaseManager.getAssetIssueStore(),
        chainBaseManager.getAssetIssueV2Store()).getAllAssetIssues()
        .forEach(
            issueCapsule -> {
              processor.updateUsage(issueCapsule);
              builder.addAssetIssue(issueCapsule.getInstance());
            });

    return builder.build();
  }

  public AssetIssueList getAssetIssueList(long offset, long limit) {
    AssetIssueList.Builder builder = AssetIssueList.newBuilder();

    List<AssetIssueCapsule> assetIssueList =
        getAssetIssueStoreFinal(chainBaseManager.getDynamicPropertiesStore(),
            chainBaseManager.getAssetIssueStore(),
            chainBaseManager.getAssetIssueV2Store()).getAssetIssuesPaginated(offset, limit);

    if (CollectionUtils.isEmpty(assetIssueList)) {
      return null;
    }

    BandwidthProcessor processor = new BandwidthProcessor(chainBaseManager);
    assetIssueList.forEach(
        issueCapsule -> {
          processor.updateUsage(issueCapsule);
          builder.addAssetIssue(issueCapsule.getInstance());
        }
    );
    return builder.build();
  }

  public AssetIssueList getAssetIssueByAccount(ByteString accountAddress) {
    if (accountAddress == null || accountAddress.isEmpty()) {
      return null;
    }

    List<AssetIssueCapsule> assetIssueCapsuleList =
        getAssetIssueStoreFinal(chainBaseManager.getDynamicPropertiesStore(),
            chainBaseManager.getAssetIssueStore(),
            chainBaseManager.getAssetIssueV2Store()).getAllAssetIssues();

    BandwidthProcessor processor = new BandwidthProcessor(chainBaseManager);
    AssetIssueList.Builder builder = AssetIssueList.newBuilder();
    assetIssueCapsuleList.stream()
        .filter(assetIssueCapsule -> assetIssueCapsule.getOwnerAddress().equals(accountAddress))
        .forEach(
            issueCapsule -> {
              processor.updateUsage(issueCapsule);
              builder.addAssetIssue(issueCapsule.getInstance());
            });

    return builder.build();
  }

  private Map<String, Long> setAssetNetLimit(Map<String, Long> assetNetLimitMap,
      AccountCapsule accountCapsule) {
    Map<String, Long> allFreeAssetNetUsage;
    if (chainBaseManager.getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
      allFreeAssetNetUsage = accountCapsule.getAllFreeAssetNetUsage();
      allFreeAssetNetUsage.keySet().forEach(asset -> {
        byte[] key = ByteArray.fromString(asset);
        assetNetLimitMap
            .put(asset, chainBaseManager.getAssetIssueStore().get(key).getFreeAssetNetLimit());
      });
    } else {
      allFreeAssetNetUsage = accountCapsule.getAllFreeAssetNetUsageV2();
      allFreeAssetNetUsage.keySet().forEach(asset -> {
        byte[] key = ByteArray.fromString(asset);
        assetNetLimitMap
            .put(asset, chainBaseManager.getAssetIssueV2Store().get(key).getFreeAssetNetLimit());
      });
    }
    return allFreeAssetNetUsage;
  }

  public AccountNetMessage getAccountNet(ByteString accountAddress) {
    if (accountAddress == null || accountAddress.isEmpty()) {
      return null;
    }
    AccountNetMessage.Builder builder = AccountNetMessage.newBuilder();
    AccountCapsule accountCapsule =
        chainBaseManager.getAccountStore().get(accountAddress.toByteArray());
    if (accountCapsule == null) {
      return null;
    }

    BandwidthProcessor processor = new BandwidthProcessor(chainBaseManager);
    processor.updateUsage(accountCapsule);

    long netLimit = processor
        .calculateGlobalNetLimit(accountCapsule);
    long freeNetLimit = chainBaseManager.getDynamicPropertiesStore().getFreeNetLimit();
    long totalNetLimit = chainBaseManager.getDynamicPropertiesStore().getTotalNetLimit();
    long totalNetWeight = chainBaseManager.getDynamicPropertiesStore().getTotalNetWeight();

    Map<String, Long> assetNetLimitMap = new HashMap<>();
    Map<String, Long> allFreeAssetNetUsage = setAssetNetLimit(assetNetLimitMap, accountCapsule);

    builder.setFreeNetUsed(accountCapsule.getFreeNetUsage())
        .setFreeNetLimit(freeNetLimit)
        .setNetUsed(accountCapsule.getNetUsage())
        .setNetLimit(netLimit)
        .setTotalNetLimit(totalNetLimit)
        .setTotalNetWeight(totalNetWeight)
        .putAllAssetNetUsed(allFreeAssetNetUsage)
        .putAllAssetNetLimit(assetNetLimitMap);
    return builder.build();
  }

  public AccountResourceMessage getAccountResource(ByteString accountAddress) {
    if (accountAddress == null || accountAddress.isEmpty()) {
      return null;
    }
    AccountResourceMessage.Builder builder = AccountResourceMessage.newBuilder();
    AccountCapsule accountCapsule =
        chainBaseManager.getAccountStore().get(accountAddress.toByteArray());
    if (accountCapsule == null) {
      return null;
    }

    BandwidthProcessor processor = new BandwidthProcessor(chainBaseManager);
    processor.updateUsage(accountCapsule);

    EnergyProcessor energyProcessor = new EnergyProcessor(
        chainBaseManager.getDynamicPropertiesStore(),
        chainBaseManager.getAccountStore());
    energyProcessor.updateUsage(accountCapsule);

    long netLimit = processor
        .calculateGlobalNetLimit(accountCapsule);
    long freeNetLimit = chainBaseManager.getDynamicPropertiesStore().getFreeNetLimit();
    long totalNetLimit = chainBaseManager.getDynamicPropertiesStore().getTotalNetLimit();
    long totalNetWeight = chainBaseManager.getDynamicPropertiesStore().getTotalNetWeight();
    long totalUnxPowerWeight = chainBaseManager.getDynamicPropertiesStore()
        .getTotalUnxPowerWeight();
    long energyLimit = energyProcessor
        .calculateGlobalEnergyLimit(accountCapsule);
    long totalEnergyLimit =
        chainBaseManager.getDynamicPropertiesStore().getTotalEnergyCurrentLimit();
    long totalEnergyWeight =
        chainBaseManager.getDynamicPropertiesStore().getTotalEnergyWeight();

    long storageLimit = accountCapsule.getAccountResource().getStorageLimit();
    long storageUsage = accountCapsule.getAccountResource().getStorageUsage();
    long allUnxPowerUsage = accountCapsule.getUnwPowerUsage();
    long allUnxPower = accountCapsule.getAllUnwPower() / UNW_PRECISION;

    Map<String, Long> assetNetLimitMap = new HashMap<>();
    Map<String, Long> allFreeAssetNetUsage = setAssetNetLimit(assetNetLimitMap, accountCapsule);

    builder.setFreeNetUsed(accountCapsule.getFreeNetUsage())
        .setFreeNetLimit(freeNetLimit)
        .setNetUsed(accountCapsule.getNetUsage())
        .setNetLimit(netLimit)
        .setTotalNetLimit(totalNetLimit)
        .setTotalNetWeight(totalNetWeight)
        .setTotalUnwPowerWeight(totalUnxPowerWeight)
        .setEnergyLimit(energyLimit)
        .setEnergyUsed(accountCapsule.getAccountResource().getEnergyUsage())
        .setUnwPowerUsed(allUnxPowerUsage)
        .setUnwPowerLimit(allUnxPower)
        .setTotalEnergyLimit(totalEnergyLimit)
        .setTotalEnergyWeight(totalEnergyWeight)
        .setStorageLimit(storageLimit)
        .setStorageUsed(storageUsage)
        .putAllAssetNetUsed(allFreeAssetNetUsage)
        .putAllAssetNetLimit(assetNetLimitMap);
    return builder.build();
  }

  public AssetIssueContract getAssetIssueByName(ByteString assetName)
      throws NonUniqueObjectException {
    if (assetName == null || assetName.isEmpty()) {
      return null;
    }

    BandwidthProcessor processor = new BandwidthProcessor(chainBaseManager);
    if (chainBaseManager.getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
      // fetch from old DB, same as old logic ops
      AssetIssueCapsule assetIssueCapsule =
          chainBaseManager.getAssetIssueStore().get(assetName.toByteArray());
      if (assetIssueCapsule != null) {
        processor.updateUsage(assetIssueCapsule);
        return assetIssueCapsule.getInstance();
      } else {
        return null;
      }
    } else {
      // get asset issue by name from new DB
      List<AssetIssueCapsule> assetIssueCapsuleList =
          chainBaseManager.getAssetIssueV2Store().getAllAssetIssues();
      AssetIssueList.Builder builder = AssetIssueList.newBuilder();
      assetIssueCapsuleList
          .stream()
          .filter(assetIssueCapsule -> assetIssueCapsule.getName().equals(assetName))
          .forEach(
              issueCapsule -> {
                processor.updateUsage(issueCapsule);
                builder.addAssetIssue(issueCapsule.getInstance());
              });

      // check count
      if (builder.getAssetIssueCount() > 1) {
        throw new NonUniqueObjectException(
            "To get more than one asset, please use getAssetIssueById syntax");
      } else {
        // fetch from DB by assetName as id
        AssetIssueCapsule assetIssueCapsule =
            chainBaseManager.getAssetIssueV2Store().get(assetName.toByteArray());

        if (assetIssueCapsule != null) {
          processor.updateUsage(assetIssueCapsule);

          // check already fetch
          if (builder.getAssetIssueCount() > 0
              && builder.getAssetIssue(0).getId()
              .equals(assetIssueCapsule.getInstance().getId())) {
            return assetIssueCapsule.getInstance();
          }

          builder.addAssetIssue(assetIssueCapsule.getInstance());
          // check count
          if (builder.getAssetIssueCount() > 1) {
            throw new NonUniqueObjectException(
                "To get more than one asset, please use getAssetIssueById syntax");
          }
        }
      }

      if (builder.getAssetIssueCount() > 0) {
        return builder.getAssetIssue(0);
      } else {
        return null;
      }
    }
  }

  public AssetIssueList getAssetIssueListByName(ByteString assetName) {
    if (assetName == null || assetName.isEmpty()) {
      return null;
    }

    List<AssetIssueCapsule> assetIssueCapsuleList =
        getAssetIssueStoreFinal(chainBaseManager.getDynamicPropertiesStore(),
            chainBaseManager.getAssetIssueStore(),
            chainBaseManager.getAssetIssueV2Store()).getAllAssetIssues();

    BandwidthProcessor processor = new BandwidthProcessor(chainBaseManager);
    AssetIssueList.Builder builder = AssetIssueList.newBuilder();
    assetIssueCapsuleList.stream()
        .filter(assetIssueCapsule -> assetIssueCapsule.getName().equals(assetName))
        .forEach(
            issueCapsule -> {
              processor.updateUsage(issueCapsule);
              builder.addAssetIssue(issueCapsule.getInstance());
            });

    return builder.build();
  }

  public AssetIssueContract getAssetIssueById(String assetId) {
    if (assetId == null || assetId.isEmpty()) {
      return null;
    }
    AssetIssueCapsule assetIssueCapsule = chainBaseManager.getAssetIssueV2Store()
        .get(ByteArray.fromString(assetId));
    if (assetIssueCapsule != null) {
      BandwidthProcessor processor = new BandwidthProcessor(chainBaseManager);
      processor.updateUsage(assetIssueCapsule);

      return assetIssueCapsule.getInstance();
    } else {
      return null;
    }
  }

  public NumberMessage totalTransaction() {
    NumberMessage.Builder builder = NumberMessage.newBuilder()
        .setNum(chainBaseManager.getTransactionStore().getTotalTransactions());
    return builder.build();
  }

  public NumberMessage getNextMaintenanceTime() {
    NumberMessage.Builder builder = NumberMessage.newBuilder()
        .setNum(chainBaseManager.getDynamicPropertiesStore().getNextMaintenanceTime());
    return builder.build();
  }

  public Block getBlockById(ByteString blockId) {
    if (Objects.isNull(blockId)) {
      return null;
    }
    Block block = null;
    try {
      block = chainBaseManager.getBlockStore().get(blockId.toByteArray()).getInstance();
    } catch (StoreException e) {
      logger.error(e.getMessage());
    }
    return block;
  }

  public BlockList getBlocksByLimitNext(long number, long limit) {
    if (limit <= 0) {
      return null;
    }
    BlockList.Builder blockListBuilder = BlockList.newBuilder();
    chainBaseManager.getBlockStore().getLimitNumber(number, limit).forEach(
        blockCapsule -> blockListBuilder.addBlock(blockCapsule.getInstance()));
    return blockListBuilder.build();
  }

  public BlockList getBlockByLatestNum(long getNum) {
    BlockList.Builder blockListBuilder = BlockList.newBuilder();
    chainBaseManager.getBlockStore().getBlockByLatestNum(getNum).forEach(
        blockCapsule -> blockListBuilder.addBlock(blockCapsule.getInstance()));
    return blockListBuilder.build();
  }

  public Transaction getTransactionById(ByteString transactionId) {
    if (Objects.isNull(transactionId)) {
      return null;
    }
    TransactionCapsule transactionCapsule = null;
    try {
      transactionCapsule = chainBaseManager.getTransactionStore()
          .get(transactionId.toByteArray());
    } catch (StoreException e) {
      return null;
    }
    if (transactionCapsule != null) {
      return transactionCapsule.getInstance();
    }
    return null;
  }

  public TransactionCapsule getTransactionCapsuleById(ByteString transactionId) {
    if (Objects.isNull(transactionId)) {
      return null;
    }
    TransactionCapsule transactionCapsule;
    try {
      transactionCapsule = chainBaseManager.getTransactionStore()
          .get(transactionId.toByteArray());
    } catch (StoreException e) {
      return null;
    }

    return transactionCapsule;
  }

  public TransactionInfo getTransactionInfoById(ByteString transactionId) {
    if (Objects.isNull(transactionId)) {
      return null;
    }
    TransactionInfoCapsule transactionInfoCapsule;
    try {
      transactionInfoCapsule = chainBaseManager.getTransactionRetStore()
          .getTransactionInfo(transactionId.toByteArray());
    } catch (StoreException e) {
      return null;
    }
    if (transactionInfoCapsule != null) {
      return transactionInfoCapsule.getInstance();
    }
    try {
      transactionInfoCapsule = chainBaseManager.getTransactionHistoryStore()
          .get(transactionId.toByteArray());
    } catch (BadItemException e) {
      return null;
    }

    return transactionInfoCapsule == null ? null : transactionInfoCapsule.getInstance();
  }

  public Proposal getProposalById(ByteString proposalId) {
    if (Objects.isNull(proposalId)) {
      return null;
    }
    ProposalCapsule proposalCapsule = null;
    try {
      proposalCapsule = chainBaseManager.getProposalStore()
          .get(proposalId.toByteArray());
    } catch (StoreException e) {
      logger.error(e.getMessage());
    }
    if (proposalCapsule != null) {
      return proposalCapsule.getInstance();
    }
    return null;
  }

  public Exchange getExchangeById(ByteString exchangeId) {
    if (Objects.isNull(exchangeId)) {
      return null;
    }
    ExchangeCapsule exchangeCapsule;
    try {
      exchangeCapsule = getExchangeStoreFinal(chainBaseManager.getDynamicPropertiesStore(),
          chainBaseManager.getExchangeStore(),
          chainBaseManager.getExchangeV2Store()).get(exchangeId.toByteArray());
    } catch (StoreException e) {
      return null;
    }
    if (exchangeCapsule != null) {
      return exchangeCapsule.getInstance();
    }
    return null;
  }

  private boolean getFullNodeAllowShieldedTransaction() {
    return Args.getInstance().isFullNodeAllowShieldedTransactionArgs();
  }

  private void checkFullNodeAllowShieldedTransaction() throws ZksnarkException {
    if (!getFullNodeAllowShieldedTransaction()) {
      throw new ZksnarkException(SHIELDED_ID_NOT_ALLOWED);
    }
  }

  public BytesMessage getNullifier(ByteString id) {
    if (Objects.isNull(id)) {
      return null;
    }
    BytesCapsule nullifier = null;
    nullifier = chainBaseManager.getNullifierStore().get(id.toByteArray());

    if (nullifier != null) {
      return BytesMessage.newBuilder().setValue(ByteString.copyFrom(nullifier.getData())).build();
    }
    return null;
  }

  private long getBlockNumber(OutputPoint outPoint)
      throws BadItemException, ZksnarkException {
    if (!getFullNodeAllowShieldedTransaction()) {
      throw new ZksnarkException(SHIELDED_ID_NOT_ALLOWED);
    }
    ByteString txId = outPoint.getHash();

    long blockNum = chainBaseManager.getTransactionStore().getBlockNumber(txId.toByteArray());
    if (blockNum <= 0) {
      throw new RuntimeException("tx is not found:" + ByteArray.toHexString(txId.toByteArray()));
    }

    return blockNum;
  }

  //in:outPoint, out:blockNumber
  private IncrementalMerkleVoucherContainer createWitness(OutputPoint outPoint, Long blockNumber)
      throws ItemNotFoundException, BadItemException,
      InvalidProtocolBufferException, ZksnarkException {
    if (!getFullNodeAllowShieldedTransaction()) {
      throw new ZksnarkException(SHIELDED_ID_NOT_ALLOWED);
    }
    ByteString txId = outPoint.getHash();

    //Get the tree in blockNum-1 position
    byte[] treeRoot = chainBaseManager.getMerkleTreeIndexStore().get(blockNumber - 1);
    if (treeRoot == null) {
      throw new RuntimeException("treeRoot is null, blockNumber:" + (blockNumber - 1));
    }

    IncrementalMerkleTreeCapsule treeCapsule = chainBaseManager.getMerkleTreeStore()
        .get(treeRoot);
    if (treeCapsule == null) {
      if ("fbc2f4300c01f0b7820d00e3347c8da4ee614674376cbc45359daa54f9b5493e"
          .equals(ByteArray.toHexString(treeRoot))) {
        treeCapsule = new IncrementalMerkleTreeCapsule();
      } else {
        throw new RuntimeException("tree is null, treeRoot:" + ByteArray.toHexString(treeRoot));
      }

    }
    IncrementalMerkleTreeContainer tree = treeCapsule.toMerkleTreeContainer();

    //Get the block of blockNum
    BlockCapsule block = chainBaseManager.getBlockByNum(blockNumber);

    IncrementalMerkleVoucherContainer witness = null;

    //get the witness in three parts
    boolean found = false;
    for (Transaction transaction : block.getInstance().getTransactionsList()) {

      Contract contract = transaction.getRawData().getContract(0);
      if (contract.getType() == ContractType.ShieldedTransferContract) {
        ShieldedTransferContract zkContract = contract.getParameter()
            .unpack(ShieldedTransferContract.class);

        if (new TransactionCapsule(transaction).getTransactionId().getByteString().equals(txId)) {
          found = true;

          if (outPoint.getIndex() >= zkContract.getReceiveDescriptionCount()) {
            throw new RuntimeException("outPoint.getIndex():" + outPoint.getIndex()
                + " >= zkContract.getReceiveDescriptionCount():" + zkContract
                .getReceiveDescriptionCount());
          }

          int index = 0;
          for (ReceiveDescription receiveDescription : zkContract.getReceiveDescriptionList()) {
            PedersenHashCapsule cmCapsule = new PedersenHashCapsule();
            cmCapsule.setContent(receiveDescription.getNoteCommitment());
            PedersenHash cm = cmCapsule.getInstance();

            if (index < outPoint.getIndex()) {
              tree.append(cm);
            } else if (outPoint.getIndex() == index) {
              tree.append(cm);
              witness = tree.getTreeCapsule().deepCopy()
                  .toMerkleTreeContainer().toVoucher();
            } else {
              if (witness != null) {
                witness.append(cm);
              } else {
                throw new ZksnarkException("witness is null!");
              }
            }

            index++;
          }

        } else {
          for (ReceiveDescription receiveDescription :
              zkContract.getReceiveDescriptionList()) {
            PedersenHashCapsule cmCapsule = new PedersenHashCapsule();
            cmCapsule.setContent(receiveDescription.getNoteCommitment());
            PedersenHash cm = cmCapsule.getInstance();
            if (witness != null) {
              witness.append(cm);
            } else {
              tree.append(cm);
            }

          }
        }
      }
    }

    if (!found) {
      throw new RuntimeException("cm not found");
    }

    return witness;

  }

  private void updateWitnesses(List<IncrementalMerkleVoucherContainer> witnessList, long large,
      int synBlockNum) throws ItemNotFoundException, BadItemException,
      InvalidProtocolBufferException, ZksnarkException {
    if (!getFullNodeAllowShieldedTransaction()) {
      throw new ZksnarkException(SHIELDED_ID_NOT_ALLOWED);
    }
    long start = large;
    long end = large + synBlockNum - 1;

    long latestBlockHeaderNumber = chainBaseManager.getDynamicPropertiesStore()
        .getLatestBlockHeaderNumber();

    if (end > latestBlockHeaderNumber) {
      throw new RuntimeException(
          "synBlockNum is too large, cmBlockNum plus synBlockNum must be <= latestBlockNumber");
    }

    for (long n = start; n <= end; n++) {
      BlockCapsule block = chainBaseManager.getBlockByNum(n);
      for (Transaction transaction1 : block.getInstance().getTransactionsList()) {

        Contract contract1 = transaction1.getRawData().getContract(0);
        if (contract1.getType() == ContractType.ShieldedTransferContract) {

          ShieldedTransferContract zkContract = contract1.getParameter()
              .unpack(ShieldedTransferContract.class);

          for (ReceiveDescription receiveDescription :
              zkContract.getReceiveDescriptionList()) {

            PedersenHashCapsule cmCapsule = new PedersenHashCapsule();
            cmCapsule.setContent(receiveDescription.getNoteCommitment());
            PedersenHash cm = cmCapsule.getInstance();
            for (IncrementalMerkleVoucherContainer wit : witnessList) {
              wit.append(cm);
            }
          }

        }
      }
    }
  }

  private void updateLowWitness(IncrementalMerkleVoucherContainer witness, long blockNum1,
      long blockNum2) throws ItemNotFoundException, BadItemException,
      InvalidProtocolBufferException, ZksnarkException {
    long start;
    long end;
    if (blockNum1 < blockNum2) {
      start = blockNum1 + 1;
      end = blockNum2;
    } else {
      return;
    }

    for (long n = start; n <= end; n++) {
      BlockCapsule block = chainBaseManager.getBlockByNum(n);
      for (Transaction transaction1 : block.getInstance().getTransactionsList()) {

        Contract contract1 = transaction1.getRawData().getContract(0);
        if (contract1.getType() == ContractType.ShieldedTransferContract) {

          ShieldedTransferContract zkContract = contract1.getParameter()
              .unpack(ShieldedTransferContract.class);

          for (ReceiveDescription receiveDescription :
              zkContract.getReceiveDescriptionList()) {

            PedersenHashCapsule cmCapsule = new PedersenHashCapsule();
            cmCapsule.setContent(receiveDescription.getNoteCommitment());
            PedersenHash cm = cmCapsule.getInstance();
            witness.append(cm);
          }

        }
      }
    }
  }

  private void validateInput(OutputPointInfo request) throws BadItemException, ZksnarkException {
    if (!getFullNodeAllowShieldedTransaction()) {
      throw new ZksnarkException(SHIELDED_ID_NOT_ALLOWED);
    }
    if (request.getBlockNum() < 0 || request.getBlockNum() > 1000) {
      throw new BadItemException("request.BlockNum must be specified with range in [0, 1000]");
    }

    if (request.getOutPointsCount() < 1 || request.getOutPointsCount() > 10) {
      throw new BadItemException("request.OutPointsCount must be speccified with range in [1, 10]");
    }

    for (OutputPoint outputPoint : request.getOutPointsList()) {

      if (outputPoint.getHash() == null) {
        throw new BadItemException("outPoint.getHash() == null");
      }
      if (outputPoint.getIndex() >= Constant.ZC_OUTPUT_DESC_MAX_SIZE
          || outputPoint.getIndex() < 0) {
        throw new BadItemException(
            "outPoint.getIndex() > " + Constant.ZC_OUTPUT_DESC_MAX_SIZE
                + " || outPoint.getIndex() < 0");
      }
    }
  }

  public IncrementalMerkleVoucherInfo getMerkleTreeVoucherInfo(OutputPointInfo request)
      throws ItemNotFoundException, BadItemException,
      InvalidProtocolBufferException, ZksnarkException {
    checkFullNodeAllowShieldedTransaction();

    validateInput(request);
    IncrementalMerkleVoucherInfo.Builder result = IncrementalMerkleVoucherInfo.newBuilder();

    long largeBlockNum = 0;
    for (OutputPoint outputPoint : request.getOutPointsList()) {
      Long blockNum1 = getBlockNumber(outputPoint);
      if (blockNum1 > largeBlockNum) {
        largeBlockNum = blockNum1;
      }
    }

    logger.debug("largeBlockNum:" + largeBlockNum);
    int opIndex = 0;

    List<IncrementalMerkleVoucherContainer> witnessList = Lists.newArrayList();
    for (OutputPoint outputPoint : request.getOutPointsList()) {
      Long blockNum1 = getBlockNumber(outputPoint);
      logger.debug("blockNum:" + blockNum1 + ", opIndex:" + opIndex++);
      if (blockNum1 + 100 < largeBlockNum) {
        throw new RuntimeException(
            "blockNum:" + blockNum1 + " + 100 < largeBlockNum:" + largeBlockNum);
      }
      IncrementalMerkleVoucherContainer witness = createWitness(outputPoint, blockNum1);
      updateLowWitness(witness, blockNum1, largeBlockNum);
      witnessList.add(witness);
    }

    int synBlockNum = request.getBlockNum();
    if (synBlockNum != 0) {
      // According to the blockNum in the request, obtain the block before [block2+1,
      // blockNum], and update the two witnesses.
      updateWitnesses(witnessList, largeBlockNum + 1, synBlockNum);
    }

    for (IncrementalMerkleVoucherContainer w : witnessList) {
      w.getVoucherCapsule().resetRt();
      result.addVouchers(w.getVoucherCapsule().getInstance());
      result.addPaths(ByteString.copyFrom(w.path().encode()));
    }

    return result.build();
  }

  public IncrementalMerkleTree getMerkleTreeOfBlock(long blockNum) throws ZksnarkException {
    if (!getFullNodeAllowShieldedTransaction()) {
      throw new ZksnarkException(SHIELDED_ID_NOT_ALLOWED);
    }
    if (blockNum < 0) {
      return null;
    }

    try {
      if (chainBaseManager.getMerkleTreeIndexStore().has(ByteArray.fromLong(blockNum))) {
        return IncrementalMerkleTree
            .parseFrom(chainBaseManager.getMerkleTreeIndexStore().get(blockNum));
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }

    return null;
  }

  public long getShieldedTransactionFee() {
    return chainBaseManager.getDynamicPropertiesStore().getShieldedTransactionFee();
  }

  private void checkCmValid(List<SpendNote> shieldedSpends, List<ReceiveNote> shieldedReceives)
      throws ContractValidateException {
    checkCmNumber(shieldedSpends, shieldedReceives);
    checkCmValue(shieldedSpends, shieldedReceives);
  }

  private void checkCmNumber(List<SpendNote> shieldedSpends, List<ReceiveNote> shieldedReceives)
      throws ContractValidateException {
    if (!shieldedSpends.isEmpty() && shieldedSpends.size() > 1) {
      throw new ContractValidateException("The number of spend note must <= 1");
    }

    if (!shieldedReceives.isEmpty() && shieldedReceives.size() > 2) {
      throw new ContractValidateException("The number of receive note must <= 2");
    }
  }

  private void checkCmValue(List<SpendNote> shieldedSpends, List<ReceiveNote> shieldedReceives)
      throws ContractValidateException {
    for (SpendNote spendNote : shieldedSpends) {
      if (spendNote.getNote().getValue() < 0) {
        throw new ContractValidateException("The value in SpendNote must >= 0");
      }
    }

    for (ReceiveNote receiveNote : shieldedReceives) {
      if (receiveNote.getNote().getValue() < 0) {
        throw new ContractValidateException("The value in ReceiveNote must >= 0");
      }
    }
  }

  public ReceiveNote createReceiveNoteRandom(long value) throws ZksnarkException, BadItemException {
    SpendingKey spendingKey = SpendingKey.random();
    PaymentAddress paymentAddress = spendingKey.defaultAddress();

    GrpcAPI.Note note = GrpcAPI.Note.newBuilder().setValue(value)
        .setPaymentAddress(KeyIo.encodePaymentAddress(paymentAddress))
        .setRcm(ByteString.copyFrom(Note.generateR()))
        .setMemo(ByteString.copyFrom(new byte[512])).build();

    return ReceiveNote.newBuilder().setNote(note).build();
  }

  public TransactionCapsule createShieldedTransaction(PrivateParameters request)
      throws ContractValidateException, RuntimeException, ZksnarkException, BadItemException {
    checkFullNodeAllowShieldedTransaction();

    ZenTransactionBuilder builder = new ZenTransactionBuilder(this);

    // set timeout
    long timeout = request.getTimeout();
    if (timeout < 0) {
      throw new ContractValidateException("Timeout must >= 0");
    }
    builder.setTimeout(timeout);

    byte[] transparentFromAddress = request.getTransparentFromAddress().toByteArray();
    byte[] ask = request.getAsk().toByteArray();
    byte[] nsk = request.getNsk().toByteArray();
    byte[] ovk = request.getOvk().toByteArray();

    if (ArrayUtils.isEmpty(transparentFromAddress) && (ArrayUtils.isEmpty(ask) || ArrayUtils
        .isEmpty(nsk) || ArrayUtils.isEmpty(ovk))) {
      throw new ContractValidateException("No input address");
    }

    long fromAmount = request.getFromAmount();
    if (!ArrayUtils.isEmpty(transparentFromAddress) && fromAmount <= 0) {
      throw new ContractValidateException("Input amount must > 0");
    }

    List<SpendNote> shieldedSpends = request.getShieldedSpendsList();
    if (!(ArrayUtils.isEmpty(ask) || ArrayUtils.isEmpty(nsk) || ArrayUtils.isEmpty(ovk))
        && shieldedSpends.isEmpty()) {
      throw new ContractValidateException("No input note");
    }

    List<ReceiveNote> shieldedReceives = request.getShieldedReceivesList();
    byte[] transparentToAddress = request.getTransparentToAddress().toByteArray();
    if (shieldedReceives.isEmpty() && ArrayUtils.isEmpty(transparentToAddress)) {
      throw new ContractValidateException("No output address");
    }

    long toAmount = request.getToAmount();
    if (!ArrayUtils.isEmpty(transparentToAddress) && toAmount <= 0) {
      throw new ContractValidateException("Output amount must > 0");
    }

    checkCmValid(shieldedSpends, shieldedReceives);

    // add
    if (!ArrayUtils.isEmpty(transparentFromAddress)) {
      builder.setTransparentInput(transparentFromAddress, fromAmount);
    }

    if (!ArrayUtils.isEmpty(transparentToAddress)) {
      builder.setTransparentOutput(transparentToAddress, toAmount);
    }

    // from shielded to public, without shielded receive, will create a random shielded address
    if (!shieldedSpends.isEmpty()
        && !ArrayUtils.isEmpty(transparentToAddress)
        && shieldedReceives.isEmpty()) {
      shieldedReceives = new ArrayList<>();
      ReceiveNote receiveNote = createReceiveNoteRandom(0);
      shieldedReceives.add(receiveNote);
    }

    // input
    if (!(ArrayUtils.isEmpty(ask) || ArrayUtils.isEmpty(nsk) || ArrayUtils.isEmpty(ovk))) {
      ExpandedSpendingKey expsk = new ExpandedSpendingKey(ask, nsk, ovk);
      for (SpendNote spendNote : shieldedSpends) {
        GrpcAPI.Note note = spendNote.getNote();
        PaymentAddress paymentAddress = KeyIo.decodePaymentAddress(note.getPaymentAddress());
        if (paymentAddress == null) {
          throw new ZksnarkException(PAYMENT_ADDRESS_FORMAT_WRONG);
        }
        Note baseNote = new Note(paymentAddress.getD(),
            paymentAddress.getPkD(), note.getValue(), note.getRcm().toByteArray());

        IncrementalMerkleVoucherContainer voucherContainer = new IncrementalMerkleVoucherCapsule(
            spendNote.getVoucher()).toMerkleVoucherContainer();
        builder.addSpend(expsk,
            baseNote,
            spendNote.getAlpha().toByteArray(),
            spendNote.getVoucher().getRt().toByteArray(),
            voucherContainer);
      }
    }

    // output
    shieldedOutput(shieldedReceives, builder, ovk);

    TransactionCapsule transactionCapsule = null;
    try {
      transactionCapsule = builder.build();
    } catch (ZksnarkException e) {
      logger.error("createShieldedTransaction except, error is " + e.toString());
      throw new ZksnarkException(e.toString());
    }
    return transactionCapsule;

  }

  public TransactionCapsule createShieldedTransactionWithoutSpendAuthSig(
      PrivateParametersWithoutAsk request)
      throws ContractValidateException, ZksnarkException, BadItemException {
    checkFullNodeAllowShieldedTransaction();

    ZenTransactionBuilder builder = new ZenTransactionBuilder(this);

    // set timeout
    long timeout = request.getTimeout();
    if (timeout < 0) {
      throw new ContractValidateException("Timeout must >= 0");
    }
    builder.setTimeout(timeout);

    byte[] transparentFromAddress = request.getTransparentFromAddress().toByteArray();
    byte[] ak = request.getAk().toByteArray();
    byte[] nsk = request.getNsk().toByteArray();
    byte[] ovk = request.getOvk().toByteArray();

    if (ArrayUtils.isEmpty(transparentFromAddress) && (ArrayUtils.isEmpty(ak) || ArrayUtils
        .isEmpty(nsk) || ArrayUtils.isEmpty(ovk))) {
      throw new ContractValidateException("No input address");
    }

    long fromAmount = request.getFromAmount();
    if (!ArrayUtils.isEmpty(transparentFromAddress) && fromAmount <= 0) {
      throw new ContractValidateException("Input amount must > 0");
    }

    List<SpendNote> shieldedSpends = request.getShieldedSpendsList();
    if (!(ArrayUtils.isEmpty(ak) || ArrayUtils.isEmpty(nsk) || ArrayUtils.isEmpty(ovk))
        && shieldedSpends.isEmpty()) {
      throw new ContractValidateException("No input note");
    }

    List<ReceiveNote> shieldedReceives = request.getShieldedReceivesList();
    byte[] transparentToAddress = request.getTransparentToAddress().toByteArray();
    if (shieldedReceives.isEmpty() && ArrayUtils.isEmpty(transparentToAddress)) {
      throw new ContractValidateException("No output address");
    }

    long toAmount = request.getToAmount();
    if (!ArrayUtils.isEmpty(transparentToAddress) && toAmount <= 0) {
      throw new ContractValidateException("Output amount must > 0");
    }

    checkCmValid(shieldedSpends, shieldedReceives);

    // add
    if (!ArrayUtils.isEmpty(transparentFromAddress)) {
      builder.setTransparentInput(transparentFromAddress, fromAmount);
    }

    if (!ArrayUtils.isEmpty(transparentToAddress)) {
      builder.setTransparentOutput(transparentToAddress, toAmount);
    }

    // from shielded to public, without shielded receive, will create a random shielded address
    if (!shieldedSpends.isEmpty()
        && !ArrayUtils.isEmpty(transparentToAddress)
        && shieldedReceives.isEmpty()) {
      shieldedReceives = new ArrayList<>();
      ReceiveNote receiveNote = createReceiveNoteRandom(0);
      shieldedReceives.add(receiveNote);
    }

    // input
    if (!(ArrayUtils.isEmpty(ak) || ArrayUtils.isEmpty(nsk) || ArrayUtils.isEmpty(ovk))) {
      for (SpendNote spendNote : shieldedSpends) {
        GrpcAPI.Note note = spendNote.getNote();
        PaymentAddress paymentAddress = KeyIo.decodePaymentAddress(note.getPaymentAddress());
        if (paymentAddress == null) {
          throw new ZksnarkException(PAYMENT_ADDRESS_FORMAT_WRONG);
        }
        Note baseNote = new Note(paymentAddress.getD(),
            paymentAddress.getPkD(), note.getValue(), note.getRcm().toByteArray());

        IncrementalMerkleVoucherContainer voucherContainer = new IncrementalMerkleVoucherCapsule(
            spendNote.getVoucher()).toMerkleVoucherContainer();
        builder.addSpend(ak,
            nsk,
            ovk,
            baseNote,
            spendNote.getAlpha().toByteArray(),
            spendNote.getVoucher().getRt().toByteArray(),
            voucherContainer);
      }
    }

    // output
    shieldedOutput(shieldedReceives, builder, ovk);

    TransactionCapsule transactionCapsule = null;
    try {
      transactionCapsule = builder.buildWithoutAsk();
    } catch (ZksnarkException e) {
      logger.error("createShieldedTransaction exception, error is " + e.toString());
      throw new ZksnarkException(e.toString());
    }
    return transactionCapsule;

  }

  private void shieldedOutput(List<ReceiveNote> shieldedReceives,
      ZenTransactionBuilder builder,
      byte[] ovk) throws ZksnarkException {
    for (ReceiveNote receiveNote : shieldedReceives) {
      PaymentAddress paymentAddress = KeyIo.decodePaymentAddress(
          receiveNote.getNote().getPaymentAddress());
      if (paymentAddress == null) {
        throw new ZksnarkException(PAYMENT_ADDRESS_FORMAT_WRONG);
      }
      builder.addOutput(ovk, paymentAddress.getD(), paymentAddress.getPkD(),
          receiveNote.getNote().getValue(), receiveNote.getNote().getRcm().toByteArray(),
          receiveNote.getNote().getMemo().toByteArray());
    }
  }


  public ShieldedAddressInfo getNewShieldedAddress() throws BadItemException, ZksnarkException {
    checkFullNodeAllowShieldedTransaction();

    ShieldedAddressInfo.Builder addressInfo = ShieldedAddressInfo.newBuilder();

    BytesMessage sk = getSpendingKey();
    DiversifierMessage d = getDiversifier();
    ExpandedSpendingKeyMessage expandedSpendingKeyMessage = getExpandedSpendingKey(sk.getValue());

    BytesMessage ak = getAkFromAsk(expandedSpendingKeyMessage.getAsk());
    BytesMessage nk = getNkFromNsk(expandedSpendingKeyMessage.getNsk());
    IncomingViewingKeyMessage ivk = getIncomingViewingKey(ak.getValue().toByteArray(),
        nk.getValue().toByteArray());

    PaymentAddressMessage addressMessage =
        getPaymentAddress(new IncomingViewingKey(ivk.getIvk().toByteArray()),
            new DiversifierT(d.getD().toByteArray()));

    addressInfo.setSk(sk.getValue());
    addressInfo.setAsk(expandedSpendingKeyMessage.getAsk());
    addressInfo.setNsk(expandedSpendingKeyMessage.getNsk());
    addressInfo.setOvk(expandedSpendingKeyMessage.getOvk());
    addressInfo.setAk(ak.getValue());
    addressInfo.setNk(nk.getValue());
    addressInfo.setIvk(ivk.getIvk());
    addressInfo.setD(d.getD());
    addressInfo.setPkD(addressMessage.getPkD());
    addressInfo.setPaymentAddress(addressMessage.getPaymentAddress());

    return addressInfo.build();

  }

  public BytesMessage getSpendingKey() throws ZksnarkException {
    checkFullNodeAllowShieldedTransaction();

    byte[] sk = SpendingKey.random().getValue();
    return BytesMessage.newBuilder().setValue(ByteString.copyFrom(sk)).build();
  }

  public ExpandedSpendingKeyMessage getExpandedSpendingKey(ByteString spendingKey)
      throws BadItemException, ZksnarkException {
    checkFullNodeAllowShieldedTransaction();

    if (Objects.isNull(spendingKey)) {
      throw new BadItemException("spendingKey is null");
    }

    if (ByteArray.toHexString(spendingKey.toByteArray()).length() != 64) {
      throw new BadItemException("the length of spendingKey's hexString should be 64");
    }

    ExpandedSpendingKey expandedSpendingKey = null;
    SpendingKey sk = new SpendingKey(spendingKey.toByteArray());
    expandedSpendingKey = sk.expandedSpendingKey();

    ExpandedSpendingKeyMessage.Builder responseBuild = ExpandedSpendingKeyMessage
        .newBuilder();
    responseBuild.setAsk(ByteString.copyFrom(expandedSpendingKey.getAsk()))
        .setNsk(ByteString.copyFrom(expandedSpendingKey.getNsk()))
        .setOvk(ByteString.copyFrom(expandedSpendingKey.getOvk()));

    return responseBuild.build();

  }

  public BytesMessage getAkFromAsk(ByteString ask) throws
      BadItemException, ZksnarkException {
    checkFullNodeAllowShieldedTransaction();

    if (Objects.isNull(ask)) {
      throw new BadItemException("ask is null");
    }

    if (ByteArray.toHexString(ask.toByteArray()).length() != 64) {
      throw new BadItemException("the length of ask's hexString should be 64");
    }

    byte[] ak = ExpandedSpendingKey.getAkFromAsk(ask.toByteArray());
    return BytesMessage.newBuilder().setValue(ByteString.copyFrom(ak)).build();
  }

  public BytesMessage getNkFromNsk(ByteString nsk) throws
      BadItemException, ZksnarkException {
    checkFullNodeAllowShieldedTransaction();

    if (Objects.isNull(nsk)) {
      throw new BadItemException("nsk is null");
    }

    if (ByteArray.toHexString(nsk.toByteArray()).length() != 64) {
      throw new BadItemException("the length of nsk's hexString should be 64");
    }

    byte[] nk = ExpandedSpendingKey.getNkFromNsk(nsk.toByteArray());
    return BytesMessage.newBuilder().setValue(ByteString.copyFrom(nk)).build();
  }

  public IncomingViewingKeyMessage getIncomingViewingKey(byte[] ak, byte[] nk)
      throws ZksnarkException {
    checkFullNodeAllowShieldedTransaction();

    byte[] ivk = new byte[32]; // the incoming viewing key
    JLibrustzcash.librustzcashCrhIvk(new CrhIvkParams(ak, nk, ivk));

    return IncomingViewingKeyMessage.newBuilder()
        .setIvk(ByteString.copyFrom(ivk))
        .build();
  }

  public DiversifierMessage getDiversifier() throws ZksnarkException {
    checkFullNodeAllowShieldedTransaction();

    byte[] d;
    while (true) {
      d = org.unx.keystore.Wallet.generateRandomBytes(Constant.ZC_DIVERSIFIER_SIZE);
      if (JLibrustzcash.librustzcashCheckDiversifier(d)) {
        break;
      }
    }

    return DiversifierMessage.newBuilder()
        .setD(ByteString.copyFrom(d))
        .build();
  }

  public BytesMessage getRcm() throws ZksnarkException {
    checkFullNodeAllowShieldedTransaction();

    byte[] rcm = Note.generateR();
    return BytesMessage.newBuilder().setValue(ByteString.copyFrom(rcm)).build();
  }

  public PaymentAddressMessage getPaymentAddress(IncomingViewingKey ivk,
      DiversifierT d) throws BadItemException, ZksnarkException {
    checkFullNodeAllowShieldedTransaction();

    if (!JLibrustzcash.librustzcashCheckDiversifier(d.getData())) {
      throw new BadItemException("d is not valid");
    }

    PaymentAddressMessage spa = null;
    Optional<PaymentAddress> op = ivk.address(d);
    if (op.isPresent()) {
      DiversifierMessage ds = DiversifierMessage.newBuilder()
          .setD(ByteString.copyFrom(d.getData()))
          .build();
      PaymentAddress paymentAddress = op.get();
      spa = PaymentAddressMessage.newBuilder()
          .setD(ds)
          .setPkD(ByteString.copyFrom(paymentAddress.getPkD()))
          .setPaymentAddress(KeyIo.encodePaymentAddress(paymentAddress))
          .build();
    }
    return spa;
  }

  public SpendResult isSpend(NoteParameters noteParameters) throws
      ZksnarkException, InvalidProtocolBufferException, BadItemException, ItemNotFoundException {
    checkFullNodeAllowShieldedTransaction();

    GrpcAPI.Note note = noteParameters.getNote();
    byte[] ak = noteParameters.getAk().toByteArray();
    byte[] nk = noteParameters.getNk().toByteArray();
    PaymentAddress paymentAddress = KeyIo.decodePaymentAddress(note.getPaymentAddress());
    if (paymentAddress == null) {
      throw new ZksnarkException(PAYMENT_ADDRESS_FORMAT_WRONG);
    }

    //only one OutPoint
    OutputPoint outputPoint = OutputPoint.newBuilder()
        .setHash(noteParameters.getTxid())
        .setIndex(noteParameters.getIndex())
        .build();
    OutputPointInfo outputPointInfo = OutputPointInfo.newBuilder()
        .addOutPoints(outputPoint)
        .setBlockNum(1) //constants
        .build();
    //most one voucher
    IncrementalMerkleVoucherInfo incrementalMerkleVoucherInfo =
        getMerkleTreeVoucherInfo(outputPointInfo);

    SpendResult result;
    if (incrementalMerkleVoucherInfo.getVouchersCount() == 0) {
      result = SpendResult.newBuilder()
          .setResult(false)
          .setMessage("The input note does not exist")
          .build();
      return result;
    }

    IncrementalMerkleVoucherContainer voucherContainer = new IncrementalMerkleVoucherCapsule(
        incrementalMerkleVoucherInfo.getVouchers(0)).toMerkleVoucherContainer();

    Note baseNote = new Note(paymentAddress.getD(), paymentAddress.getPkD(), note.getValue(),
        note.getRcm().toByteArray());
    byte[] nf = baseNote.nullifier(ak, nk, voucherContainer.position());

    if (chainBaseManager.getNullifierStore().has(nf)) {
      result = SpendResult.newBuilder()
          .setResult(true)
          .setMessage("Input note has been spent")
          .build();
    } else {
      result = SpendResult.newBuilder()
          .setResult(false)
          .setMessage("The input note is not spent or does not exist")
          .build();
    }

    return result;
  }

  public BytesMessage createSpendAuthSig(SpendAuthSigParameters spendAuthSigParameters)
      throws ZksnarkException {
    checkFullNodeAllowShieldedTransaction();

    byte[] result = new byte[64];
    SpendSigParams spendSigParams = new SpendSigParams(
        spendAuthSigParameters.getAsk().toByteArray(),
        spendAuthSigParameters.getAlpha().toByteArray(),
        spendAuthSigParameters.getTxHash().toByteArray(),
        result);
    JLibrustzcash.librustzcashSaplingSpendSig(spendSigParams);

    return BytesMessage.newBuilder().setValue(ByteString.copyFrom(result)).build();
  }

  public BytesMessage createShieldNullifier(NfParameters nfParameters) throws ZksnarkException {
    checkFullNodeAllowShieldedTransaction();

    byte[] ak = nfParameters.getAk().toByteArray();
    byte[] nk = nfParameters.getNk().toByteArray();

    byte[] result = new byte[32]; // 256
    GrpcAPI.Note note = nfParameters.getNote();
    IncrementalMerkleVoucherCapsule incrementalMerkleVoucherCapsule
        = new IncrementalMerkleVoucherCapsule(nfParameters.getVoucher());
    IncrementalMerkleVoucherContainer incrementalMerkleVoucherContainer
        = new IncrementalMerkleVoucherContainer(incrementalMerkleVoucherCapsule);
    PaymentAddress paymentAddress = KeyIo.decodePaymentAddress(
        note.getPaymentAddress());
    if (paymentAddress == null) {
      throw new ZksnarkException(PAYMENT_ADDRESS_FORMAT_WRONG);
    }
    ComputeNfParams computeNfParams = new ComputeNfParams(
        paymentAddress.getD().getData(),
        paymentAddress.getPkD(),
        note.getValue(),
        note.getRcm().toByteArray(),
        ak,
        nk,
        incrementalMerkleVoucherContainer.position(),
        result);
    if (!JLibrustzcash.librustzcashComputeNf(computeNfParams)) {
      return null;
    }

    return BytesMessage.newBuilder()
        .setValue(ByteString.copyFrom(result))
        .build();
  }

  public BytesMessage getShieldTransactionHash(Transaction transaction)
      throws ContractValidateException, ZksnarkException {
    checkFullNodeAllowShieldedTransaction();

    List<Contract> contract = transaction.getRawData().getContractList();
    if (contract == null || contract.isEmpty()) {
      throw new ContractValidateException("Contract is null");
    }
    ContractType contractType = contract.get(0).getType();
    if (contractType != ContractType.ShieldedTransferContract) {
      throw new ContractValidateException("Not a shielded transaction");
    }

    TransactionCapsule transactionCapsule = new TransactionCapsule(transaction);
    byte[] transactionHash = TransactionCapsule
        .getShieldTransactionHashIgnoreTypeException(transactionCapsule.getInstance());
    if (transactionHash != null) {
      return BytesMessage.newBuilder().setValue(ByteString.copyFrom(transactionHash)).build();
    } else {
      return BytesMessage.newBuilder().build();
    }
  }

  public TransactionInfoList getTransactionInfoByBlockNum(long blockNum) {
    TransactionInfoList.Builder transactionInfoList = TransactionInfoList.newBuilder();

    try {
      TransactionRetCapsule result = dbManager.getTransactionRetStore()
          .getTransactionInfoByBlockNum(ByteArray.fromLong(blockNum));

      if (!Objects.isNull(result) && !Objects.isNull(result.getInstance())) {
        result.getInstance().getTransactioninfoList().forEach(
            transactionInfo -> transactionInfoList.addTransactionInfo(transactionInfo)
        );
      } else {
        Block block = chainBaseManager.getBlockByNum(blockNum).getInstance();

        if (block != null) {
          List<Transaction> listTransaction = block.getTransactionsList();
          for (Transaction transaction : listTransaction) {
            TransactionInfoCapsule transactionInfoCapsule = dbManager.getTransactionHistoryStore()
                .get(Sha256Hash.hash(CommonParameter.getInstance()
                    .isECKeyCryptoEngine(), transaction.getRawData().toByteArray()));

            if (transactionInfoCapsule != null) {
              transactionInfoList.addTransactionInfo(transactionInfoCapsule.getInstance());
            }
          }
        }
      }
    } catch (BadItemException | ItemNotFoundException e) {
      logger.error(e.getMessage());
    }

    return transactionInfoList.build();
  }

  public NodeList listNodes() {
    List<NodeHandler> handlerList = nodeManager.dumpActiveNodes();

    Map<String, NodeHandler> nodeHandlerMap = new HashMap<>();
    for (NodeHandler handler : handlerList) {
      String key = handler.getNode().getHexId() + handler.getNode().getHost();
      nodeHandlerMap.put(key, handler);
    }

    NodeList.Builder nodeListBuilder = NodeList.newBuilder();

    nodeHandlerMap.entrySet().stream()
        .forEach(v -> {
          org.unx.common.overlay.discover.node.Node node = v.getValue()
              .getNode();
          nodeListBuilder.addNodes(Node.newBuilder().setAddress(
              Address.newBuilder()
                  .setHost(ByteString
                      .copyFrom(ByteArray.fromString(node.getHost())))
                  .setPort(node.getPort())));
        });
    return nodeListBuilder.build();
  }

  public MarketOrder getMarketOrderById(ByteString orderId) {

    if (orderId == null || orderId.isEmpty()) {
      return null;
    }

    MarketOrderStore marketOrderStore = dbManager.getChainBaseManager().getMarketOrderStore();

    try {
      return marketOrderStore.get(orderId.toByteArray()).getInstance();
    } catch (ItemNotFoundException e) {
      logger.error("orderId = " + orderId.toString() + " not found");
      throw new IllegalStateException("order not found in store");
    }

  }

  public MarketOrderList getMarketOrderByAccount(ByteString accountAddress) {

    if (accountAddress == null || accountAddress.isEmpty()) {
      return null;
    }

    MarketAccountOrderCapsule marketAccountOrderCapsule;
    try {
      marketAccountOrderCapsule = dbManager.getChainBaseManager()
          .getMarketAccountStore().get(accountAddress.toByteArray());
    } catch (ItemNotFoundException e) {
      return null;
    }

    MarketOrderStore marketOrderStore = dbManager.getChainBaseManager().getMarketOrderStore();

    MarketOrderList.Builder marketOrderListBuilder = MarketOrderList.newBuilder();
    List<ByteString> orderIdList = marketAccountOrderCapsule.getOrdersList();

    orderIdList.forEach(
        orderId -> {
          try {
            MarketOrderCapsule orderCapsule = marketOrderStore.get(orderId.toByteArray());
            // set prev and next, hide these messages in the print
            orderCapsule.setPrev(new byte[0]);
            orderCapsule.setNext(new byte[0]);

            marketOrderListBuilder
                .addOrders(orderCapsule.getInstance());
          } catch (ItemNotFoundException e) {
            logger.error("orderId = " + orderId.toString() + " not found");
            throw new IllegalStateException("order not found in store");
          }
        }
    );

    return marketOrderListBuilder.build();
  }

  public MarketPriceList getMarketPriceByPair(byte[] sellTokenId, byte[] buyTokenId)
      throws BadItemException {
    MarketUtils.checkPairValid(sellTokenId, buyTokenId);

    MarketPairToPriceStore marketPairToPriceStore = dbManager.getChainBaseManager()
        .getMarketPairToPriceStore();
    MarketPairPriceToOrderStore marketPairPriceToOrderStore = dbManager.getChainBaseManager()
        .getMarketPairPriceToOrderStore();

    MarketPriceList.Builder marketPriceListBuilder = MarketPriceList.newBuilder()
        .setSellTokenId(ByteString.copyFrom(sellTokenId))
        .setBuyTokenId(ByteString.copyFrom(buyTokenId));

    long count = marketPairToPriceStore.getPriceNum(sellTokenId, buyTokenId);
    if (count == 0) {
      return marketPriceListBuilder.build();
    }

    long limit = count < MARKET_COUNT_LIMIT_MAX ? count : MARKET_COUNT_LIMIT_MAX;

    List<byte[]> priceKeysList = marketPairPriceToOrderStore
        .getPriceKeysList(sellTokenId, buyTokenId, limit);

    priceKeysList.forEach(
        priceKey -> {
          MarketPrice marketPrice = MarketUtils.decodeKeyToMarketPrice(priceKey);
          marketPriceListBuilder.addPrices(marketPrice);
        }
    );

    return marketPriceListBuilder.build();
  }

  public MarketOrderPairList getMarketPairList() {
    MarketOrderPairList.Builder builder = MarketOrderPairList.newBuilder();
    MarketPairToPriceStore marketPairToPriceStore = dbManager.getChainBaseManager()
        .getMarketPairToPriceStore();

    Iterator<Entry<byte[], BytesCapsule>> iterator = marketPairToPriceStore
        .iterator();
    long count = 0;
    while (iterator.hasNext()) {
      Entry<byte[], BytesCapsule> next = iterator.next();

      byte[] pairKey = next.getKey();
      builder.addOrderPair(MarketUtils.decodeKeyToMarketPairHuman(pairKey));
      count++;
      if (count > MARKET_COUNT_LIMIT_MAX) {
        break;
      }
    }

    return builder.build();
  }

  public MarketOrderList getMarketOrderListByPair(byte[] sellTokenId, byte[] buyTokenId)
      throws ItemNotFoundException, BadItemException {
    MarketUtils.checkPairValid(sellTokenId, buyTokenId);

    MarketOrderList.Builder builder = MarketOrderList.newBuilder();

    MarketPairToPriceStore marketPairToPriceStore = dbManager.getChainBaseManager()
        .getMarketPairToPriceStore();
    MarketPairPriceToOrderStore marketPairPriceToOrderStore = dbManager.getChainBaseManager()
        .getMarketPairPriceToOrderStore();
    MarketPairPriceToOrderStore pairPriceToOrderStore = dbManager.getChainBaseManager()
        .getMarketPairPriceToOrderStore();
    MarketOrderStore orderStore = dbManager.getChainBaseManager().getMarketOrderStore();

    long countForPrice = marketPairToPriceStore.getPriceNum(sellTokenId, buyTokenId);
    if (countForPrice == 0) {
      return builder.build();
    }
    long limitForPrice =
        countForPrice < MARKET_COUNT_LIMIT_MAX ? countForPrice : MARKET_COUNT_LIMIT_MAX;

    List<byte[]> priceKeysList = marketPairPriceToOrderStore
        .getPriceKeysList(sellTokenId, buyTokenId, limitForPrice);

    long countForOrder = 0;
    for (byte[] pairPriceKey : priceKeysList) {
      MarketOrderIdListCapsule orderIdListCapsule = pairPriceToOrderStore
          .getUnchecked(pairPriceKey);
      if (MARKET_COUNT_LIMIT_MAX - countForOrder <= 0) {
        break;
      }
      if (orderIdListCapsule != null) {
        List<MarketOrderCapsule> orderList = orderIdListCapsule
            .getAllOrder(orderStore, MARKET_COUNT_LIMIT_MAX - countForOrder);

        orderList.forEach(orderCapsule -> {
          // set prev and next, hide these messages in the print
          orderCapsule.setPrev(new byte[0]);
          orderCapsule.setNext(new byte[0]);

          builder.addOrders(orderCapsule.getInstance());
        });
        countForOrder += orderList.size();
      }
    }

    return builder.build();
  }

  public Transaction deployContract(TransactionCapsule unxCap) {

    // do nothing, so can add some useful function later
    // unxCap contract para cacheUnpackValue has value

    return unxCap.getInstance();
  }

  public Transaction triggerContract(TriggerSmartContract
      triggerSmartContract,
      TransactionCapsule unxCap, Builder builder,
      Return.Builder retBuilder)
      throws ContractValidateException, ContractExeException, HeaderNotFound, VMIllegalException {

    byte[] contractAddress = triggerSmartContract.getContractAddress()
        .toByteArray();
    ContractCapsule contractCapsule = chainBaseManager.getContractStore().get(contractAddress);
    if (contractCapsule == null) {
      throw new ContractValidateException(
          "No contract or not a valid smart contract");
    }
    AbiCapsule abiCapsule = chainBaseManager.getAbiStore().get(contractAddress);
    SmartContract.ABI abi;
    if (abiCapsule == null || abiCapsule.getData() == null) {
      abi = SmartContract.ABI.getDefaultInstance();
    } else {
      abi = abiCapsule.getInstance();
    }

    byte[] selector = WalletUtil.getSelector(
        triggerSmartContract.getData().toByteArray());

    if (isConstant(abi, selector)) {
      return callConstantContract(unxCap, builder, retBuilder);
    } else {
      return unxCap.getInstance();
    }
  }

  public Transaction triggerConstantContract(TriggerSmartContract triggerSmartContract,
      TransactionCapsule unxCap, Builder builder, Return.Builder retBuilder)
      throws ContractValidateException, ContractExeException, HeaderNotFound, VMIllegalException {

    if (triggerSmartContract.getContractAddress().isEmpty()) { // deploy contract
      CreateSmartContract.Builder deployBuilder = CreateSmartContract.newBuilder();
      deployBuilder.setOwnerAddress(triggerSmartContract.getOwnerAddress());
      deployBuilder.setNewContract(SmartContract.newBuilder()
          .setOriginAddress(triggerSmartContract.getOwnerAddress())
          .setBytecode(triggerSmartContract.getData())
          .setCallValue(triggerSmartContract.getCallValue())
          .setConsumeUserResourcePercent(100)
          .setOriginEnergyLimit(1)
          .build()
      );
      deployBuilder.setCallTokenValue(triggerSmartContract.getCallTokenValue());
      deployBuilder.setTokenId(triggerSmartContract.getTokenId());
      unxCap = createTransactionCapsule(deployBuilder.build(), ContractType.CreateSmartContract);
    } else { // call contract
      ContractStore contractStore = chainBaseManager.getContractStore();
      byte[] contractAddress = triggerSmartContract.getContractAddress().toByteArray();
      if (contractStore.get(contractAddress) == null) {
        throw new ContractValidateException("Smart contract is not exist.");
      }
    }
    return callConstantContract(unxCap, builder, retBuilder);
  }

  public Transaction callConstantContract(TransactionCapsule unxCap,
      Builder builder, Return.Builder retBuilder)
      throws ContractValidateException, ContractExeException, HeaderNotFound, VMIllegalException {

    if (!Args.getInstance().isSupportConstant()) {
      throw new ContractValidateException("this node does not support constant");
    }

    Block headBlock;
    List<BlockCapsule> blockCapsuleList = chainBaseManager.getBlockStore()
        .getBlockByLatestNum(1);
    if (CollectionUtils.isEmpty(blockCapsuleList)) {
      throw new HeaderNotFound("latest block not found");
    } else {
      headBlock = blockCapsuleList.get(0).getInstance();
    }

    TransactionContext context = new TransactionContext(new BlockCapsule(headBlock), unxCap,
        StoreFactory.getInstance(), true, false);
    VMActuator vmActuator = new VMActuator(true);

    vmActuator.validate(context);
    vmActuator.execute(context);

    ProgramResult result = context.getProgramResult();
    if (result.getException() != null) {
      RuntimeException e = result.getException();
      logger.warn("Constant call has an error {}", e.getMessage());
      throw e;
    }

    TransactionResultCapsule ret = new TransactionResultCapsule();
    builder.setEnergyUsed(result.getEnergyUsed());
    builder.addConstantResult(ByteString.copyFrom(result.getHReturn()));
    result.getLogInfoList().forEach(logInfo ->
        builder.addLogs(LogInfo.buildLog(logInfo)));
    result.getInternalTransactions().forEach(it ->
        builder.addInternalTransactions(TransactionUtil.buildInternalTransaction(it)));
    ret.setStatus(0, code.SUCESS);
    if (StringUtils.isNoneEmpty(result.getRuntimeError())) {
      ret.setStatus(0, code.FAILED);
      retBuilder
          .setMessage(ByteString.copyFromUtf8(result.getRuntimeError()))
          .build();
    }
    if (result.isRevert()) {
      ret.setStatus(0, code.FAILED);
      retBuilder.setMessage(ByteString.copyFromUtf8("REVERT opcode executed"))
          .build();
    }
    unxCap.setResult(ret);
    return unxCap.getInstance();
  }

  public SmartContract getContract(GrpcAPI.BytesMessage bytesMessage) {
    byte[] address = bytesMessage.getValue().toByteArray();
    AccountCapsule accountCapsule = chainBaseManager.getAccountStore().get(address);
    if (accountCapsule == null) {
      logger.error(
          "Get contract failed, the account does not exist or the account "
              + "does not have a code hash!");
      return null;
    }

    ContractCapsule contractCapsule = chainBaseManager.getContractStore().get(address);
    if (Objects.nonNull(contractCapsule)) {
      AbiCapsule abiCapsule = chainBaseManager.getAbiStore().get(address);
      if (abiCapsule != null && abiCapsule.getInstance() != null) {
        contractCapsule = new ContractCapsule(
            contractCapsule.getInstance().toBuilder().setAbi(abiCapsule.getInstance()).build());
      }
      return contractCapsule.getInstance();
    }
    return null;
  }

  /**
   * Add a wrapper for smart contract. Current additional information including runtime code for a
   * smart contract.
   *
   * @param bytesMessage the contract address message
   * @return contract info
   */
  public SmartContractDataWrapper getContractInfo(GrpcAPI.BytesMessage bytesMessage) {
    byte[] address = bytesMessage.getValue().toByteArray();
    AccountCapsule accountCapsule = chainBaseManager.getAccountStore().get(address);
    if (accountCapsule == null) {
      logger.error(
          "Get contract failed, the account does not exist or the account does not have a code "
              + "hash!");
      return null;
    }

    ContractCapsule contractCapsule = chainBaseManager.getContractStore().get(address);
    if (Objects.nonNull(contractCapsule)) {
      AbiCapsule abiCapsule = chainBaseManager.getAbiStore().get(address);
      if (abiCapsule != null && abiCapsule.getInstance() != null) {
        contractCapsule = new ContractCapsule(
            contractCapsule.getInstance().toBuilder().setAbi(abiCapsule.getInstance()).build());
      }
      CodeCapsule codeCapsule = chainBaseManager.getCodeStore().get(address);
      if (Objects.nonNull(codeCapsule) && Objects.nonNull(codeCapsule.getData())) {
        contractCapsule.setRuntimecode(codeCapsule.getData());
      } else {
        contractCapsule.setRuntimecode(new byte[0]);
      }
      return contractCapsule.generateWrapper();
    }
    return null;
  }

  /*
  input
  offset:100,limit:10
  return
  id: 101~110
   */
  public ProposalList getPaginatedProposalList(long offset, long limit) {

    if (limit < 0 || offset < 0) {
      return null;
    }

    long latestProposalNum = chainBaseManager.getDynamicPropertiesStore()
        .getLatestProposalNum();
    if (latestProposalNum <= offset) {
      return null;
    }
    limit =
        limit > PROPOSAL_COUNT_LIMIT_MAX ? PROPOSAL_COUNT_LIMIT_MAX : limit;
    long end = offset + limit;
    end = end > latestProposalNum ? latestProposalNum : end;
    ProposalList.Builder builder = ProposalList.newBuilder();

    ImmutableList<Long> rangeList = ContiguousSet
        .create(Range.openClosed(offset, end), DiscreteDomain.longs())
        .asList();
    rangeList.stream().map(ProposalCapsule::calculateDbKey).map(key -> {
      try {
        return chainBaseManager.getProposalStore().get(key);
      } catch (Exception ex) {
        return null;
      }
    }).filter(Objects::nonNull)
        .forEach(proposalCapsule -> builder
            .addProposals(proposalCapsule.getInstance()));
    return builder.build();
  }

  public ExchangeList getPaginatedExchangeList(long offset, long limit) {
    if (limit < 0 || offset < 0) {
      return null;
    }

    long latestExchangeNum = chainBaseManager.getDynamicPropertiesStore()
        .getLatestExchangeNum();
    if (latestExchangeNum <= offset) {
      return null;
    }
    limit =
        limit > EXCHANGE_COUNT_LIMIT_MAX ? EXCHANGE_COUNT_LIMIT_MAX : limit;
    long end = offset + limit;
    end = end > latestExchangeNum ? latestExchangeNum : end;

    ExchangeList.Builder builder = ExchangeList.newBuilder();
    ImmutableList<Long> rangeList = ContiguousSet
        .create(Range.openClosed(offset, end), DiscreteDomain.longs())
        .asList();
    rangeList.stream().map(ExchangeCapsule::calculateDbKey).map(key -> {
      try {
        return getExchangeStoreFinal(chainBaseManager.getDynamicPropertiesStore(),
            chainBaseManager.getExchangeStore(),
            chainBaseManager.getExchangeV2Store()).get(key);
      } catch (Exception ex) {
        return null;
      }
    }).filter(Objects::nonNull)
        .forEach(exchangeCapsule -> builder
            .addExchanges(exchangeCapsule.getInstance()));
    return builder.build();

  }

  /*
   * strip right 0 from memo
   */
  public byte[] stripRightZero(byte[] memo) {
    int index = memo.length;
    for (; index > 0; --index) {
      if (memo[index - 1] != 0) {
        break;
      }
    }
    byte[] memoStrip = new byte[index];
    System.arraycopy(memo, 0, memoStrip, 0, index);
    return memoStrip;
  }

  /*
   * query note by ivk
   */
  private GrpcAPI.DecryptNotes queryNoteByIvk(long startNum, long endNum, byte[] ivk)
      throws BadItemException, ZksnarkException {
    GrpcAPI.DecryptNotes.Builder builder = GrpcAPI.DecryptNotes.newBuilder();
    if (!(startNum >= 0 && endNum > startNum && endNum - startNum <= 1000)) {
      throw new BadItemException(
          SHIELDED_TRANSACTION_SCAN_RANGE);
    }
    BlockList blockList = this.getBlocksByLimitNext(startNum, endNum - startNum);
    for (Block block : blockList.getBlockList()) {
      for (Transaction transaction : block.getTransactionsList()) {
        TransactionCapsule transactionCapsule = new TransactionCapsule(transaction);
        byte[] txid = transactionCapsule.getTransactionId().getBytes();
        List<Transaction.Contract> contracts = transaction.getRawData().getContractList();
        if (contracts.isEmpty()) {
          continue;
        }
        Transaction.Contract c = contracts.get(0);
        if (c.getType() != Contract.ContractType.ShieldedTransferContract) {
          continue;
        }
        ShieldedTransferContract stContract;
        try {
          stContract = c.getParameter().unpack(ShieldedTransferContract.class);
        } catch (InvalidProtocolBufferException e) {
          throw new ZksnarkException(
              "unpack ShieldedTransferContract failed.");
        }

        for (int index = 0; index < stContract.getReceiveDescriptionList().size(); index++) {
          ReceiveDescription r = stContract.getReceiveDescription(index);
          Optional<Note> notePlaintext = Note.decrypt(r.getCEnc().toByteArray(),//ciphertext
              ivk,
              r.getEpk().toByteArray(),//epk
              r.getNoteCommitment().toByteArray() //cmu
          );

          if (notePlaintext.isPresent()) {
            Note noteText = notePlaintext.get();
            byte[] pkD = new byte[32];
            if (!JLibrustzcash
                .librustzcashIvkToPkd(new IvkToPkdParams(ivk, noteText.getD().getData(),
                    pkD))) {
              continue;
            }

            String paymentAddress = KeyIo
                .encodePaymentAddress(new PaymentAddress(noteText.getD(), pkD));
            GrpcAPI.Note note = GrpcAPI.Note.newBuilder()
                .setPaymentAddress(paymentAddress)
                .setValue(noteText.getValue())
                .setRcm(ByteString.copyFrom(noteText.getRcm()))
                .setMemo(ByteString.copyFrom(stripRightZero(noteText.getMemo())))
                .build();
            DecryptNotes.NoteTx noteTx = DecryptNotes.NoteTx.newBuilder().setNote(note)
                .setTxid(ByteString.copyFrom(txid)).setIndex(index).build();

            builder.addNoteTxs(noteTx);
          }
        } // end of ReceiveDescriptionList
      } // end of transaction
    } //end of block list
    return builder.build();
  }

  /**
   * try to get all note belongs to ivk
   */
  public GrpcAPI.DecryptNotes scanNoteByIvk(long startNum, long endNum,
      byte[] ivk) throws BadItemException, ZksnarkException {
    checkFullNodeAllowShieldedTransaction();

    return queryNoteByIvk(startNum, endNum, ivk);
  }

  /**
   * try to get unspent note belongs to ivk
   */
  public GrpcAPI.DecryptNotesMarked scanAndMarkNoteByIvk(long startNum, long endNum,
      byte[] ivk, byte[] ak, byte[] nk) throws BadItemException, ZksnarkException,
      InvalidProtocolBufferException, ItemNotFoundException {
    checkFullNodeAllowShieldedTransaction();

    GrpcAPI.DecryptNotes srcNotes = queryNoteByIvk(startNum, endNum, ivk);
    GrpcAPI.DecryptNotesMarked.Builder builder = GrpcAPI.DecryptNotesMarked.newBuilder();
    for (NoteTx noteTx : srcNotes.getNoteTxsList()) {
      //query if note is already spent
      NoteParameters noteParameters = NoteParameters.newBuilder()
          .setNote(noteTx.getNote())
          .setAk(ByteString.copyFrom(ak))
          .setNk(ByteString.copyFrom(nk))
          .setTxid(noteTx.getTxid())
          .setIndex(noteTx.getIndex())
          .build();
      SpendResult spendResult = isSpend(noteParameters);

      //construct DecryptNotesMarked
      GrpcAPI.DecryptNotesMarked.NoteTx.Builder markedNoteTx
          = GrpcAPI.DecryptNotesMarked.NoteTx.newBuilder();
      markedNoteTx.setNote(noteTx.getNote());
      markedNoteTx.setTxid(noteTx.getTxid());
      markedNoteTx.setIndex(noteTx.getIndex());
      markedNoteTx.setIsSpend(spendResult.getResult());

      builder.addNoteTxs(markedNoteTx);
    }
    return builder.build();
  }

  /**
   * try to get cm belongs to ovk
   */
  public GrpcAPI.DecryptNotes scanNoteByOvk(long startNum, long endNum,
      byte[] ovk) throws BadItemException, ZksnarkException {
    checkFullNodeAllowShieldedTransaction();

    GrpcAPI.DecryptNotes.Builder builder = GrpcAPI.DecryptNotes.newBuilder();
    if (!(startNum >= 0 && endNum > startNum && endNum - startNum <= 1000)) {
      throw new BadItemException(
          SHIELDED_TRANSACTION_SCAN_RANGE);
    }
    BlockList blockList = this.getBlocksByLimitNext(startNum, endNum - startNum);
    for (Block block : blockList.getBlockList()) {
      for (Transaction transaction : block.getTransactionsList()) {
        TransactionCapsule transactionCapsule = new TransactionCapsule(transaction);
        byte[] txid = transactionCapsule.getTransactionId().getBytes();
        List<Transaction.Contract> contracts = transaction.getRawData().getContractList();
        if (contracts.isEmpty()) {
          continue;
        }
        Transaction.Contract c = contracts.get(0);
        if (c.getType() != Protocol.Transaction.Contract.ContractType.ShieldedTransferContract) {
          continue;
        }
        ShieldedTransferContract stContract;
        try {
          stContract = c.getParameter().unpack(
              ShieldedTransferContract.class);
        } catch (InvalidProtocolBufferException e) {
          throw new RuntimeException(
              "unpack ShieldedTransferContract failed.");
        }
        for (int index = 0; index < stContract.getReceiveDescriptionList().size(); index++) {
          ReceiveDescription r = stContract.getReceiveDescription(index);
          NoteEncryption.Encryption.OutCiphertext cOut = new NoteEncryption.Encryption.OutCiphertext();
          cOut.setData(r.getCOut().toByteArray());
          Optional<OutgoingPlaintext> notePlaintext = OutgoingPlaintext.decrypt(cOut,//ciphertext
              ovk,
              r.getValueCommitment().toByteArray(), //cv
              r.getNoteCommitment().toByteArray(), //cmu
              r.getEpk().toByteArray() //epk
          );

          if (notePlaintext.isPresent()) {
            OutgoingPlaintext decryptedOutCtUnwrapped = notePlaintext.get();
            //decode c_enc with pkd、esk
            NoteEncryption.Encryption.EncCiphertext cipherText = new NoteEncryption.Encryption.EncCiphertext();
            cipherText.setData(r.getCEnc().toByteArray());
            Optional<Note> foo = Note.decrypt(cipherText,
                r.getEpk().toByteArray(),
                decryptedOutCtUnwrapped.getEsk(),
                decryptedOutCtUnwrapped.getPkD(),
                r.getNoteCommitment().toByteArray());

            if (foo.isPresent()) {
              Note bar = foo.get();
              String paymentAddress = KeyIo.encodePaymentAddress(
                  new PaymentAddress(bar.getD(), decryptedOutCtUnwrapped.getPkD()));
              GrpcAPI.Note note = GrpcAPI.Note.newBuilder()
                  .setPaymentAddress(paymentAddress)
                  .setValue(bar.getValue())
                  .setRcm(ByteString.copyFrom(bar.getRcm()))
                  .setMemo(ByteString.copyFrom(stripRightZero(bar.getMemo())))
                  .build();

              DecryptNotes.NoteTx noteTx = DecryptNotes.NoteTx
                  .newBuilder()
                  .setNote(note)
                  .setTxid(ByteString.copyFrom(txid))
                  .setIndex(index)
                  .build();

              builder.addNoteTxs(noteTx);
            }
          }
        } // end of ReceiveDescriptionList
      } // end of transaction
    } //end of block list
    return builder.build();
  }

  private void checkShieldedURC20NoteValue(
      List<GrpcAPI.SpendNoteURC20> spendNoteURC20s, List<ReceiveNote> receiveNotes)
      throws ContractValidateException {
    if (!Objects.isNull(spendNoteURC20s)) {
      for (GrpcAPI.SpendNoteURC20 spendNote : spendNoteURC20s) {
        if (spendNote.getNote().getValue() < 0) {
          throw new ContractValidateException("The value in SpendNoteURC20 must >= 0");
        }
      }
    }

    if (!Objects.isNull(receiveNotes)) {
      for (ReceiveNote receiveNote : receiveNotes) {
        if (receiveNote.getNote().getValue() < 0) {
          throw new ContractValidateException("The value in ReceiveNote must >= 0");
        }
      }
    }
  }

  private void buildShieldedURC20Input(ShieldedURC20ParametersBuilder builder,
                                       GrpcAPI.SpendNoteURC20 spendNote, ExpandedSpendingKey expsk)
      throws ZksnarkException {
    GrpcAPI.Note note = spendNote.getNote();
    PaymentAddress paymentAddress = KeyIo.decodePaymentAddress(note.getPaymentAddress());
    if (Objects.isNull(paymentAddress)) {
      throw new ZksnarkException(PAYMENT_ADDRESS_FORMAT_WRONG);
    }

    Note baseNote = new Note(paymentAddress.getD(),
        paymentAddress.getPkD(),
        note.getValue(),
        note.getRcm().toByteArray());
    builder.addSpend(expsk,
        baseNote,
        spendNote.getAlpha().toByteArray(),
        spendNote.getRoot().toByteArray(),
        spendNote.getPath().toByteArray(),
        spendNote.getPos());
  }

  private void buildShieldedURC20Output(ShieldedURC20ParametersBuilder builder,
      ReceiveNote receiveNote, byte[] ovk) throws ZksnarkException {
    PaymentAddress paymentAddress = KeyIo.decodePaymentAddress(
        receiveNote.getNote().getPaymentAddress());
    if (Objects.isNull(paymentAddress)) {
      throw new ZksnarkException(PAYMENT_ADDRESS_FORMAT_WRONG);
    }

    builder.addOutput(ovk, paymentAddress.getD(), paymentAddress.getPkD(),
        receiveNote.getNote().getValue(), receiveNote.getNote().getRcm().toByteArray(),
        receiveNote.getNote().getMemo().toByteArray());
  }

  public ShieldedURC20Parameters createShieldedContractParameters(
      PrivateShieldedURC20Parameters request)
      throws ContractValidateException, ZksnarkException, ContractExeException {
    checkFullNodeAllowShieldedTransaction();

    ShieldedURC20ParametersBuilder builder = new ShieldedURC20ParametersBuilder();

    byte[] shieldedURC20ContractAddress = request.getShieldedURC20ContractAddress().toByteArray();
    if (ArrayUtils.isEmpty(shieldedURC20ContractAddress)
        || shieldedURC20ContractAddress.length != 21) {
      throw new ContractValidateException("No valid shielded URC-20 contract address");
    }

    byte[] shieldedURC20ContractAddressUvm = new byte[20];
    System.arraycopy(shieldedURC20ContractAddress, 1, shieldedURC20ContractAddressUvm, 0, 20);
    builder.setShieldedURC20Address(shieldedURC20ContractAddressUvm);

    BigInteger fromAmount;
    BigInteger toAmount;
    try {
      fromAmount = getBigIntegerFromString(request.getFromAmount());
      toAmount = getBigIntegerFromString(request.getToAmount());
    } catch (Exception e) {
      throw new ContractValidateException("invalid from_amount or to_amount");
    }

    long[] scaledPublicAmount = checkPublicAmount(shieldedURC20ContractAddress,
        fromAmount, toAmount);
    long scaledFromAmount = scaledPublicAmount[0];
    long scaledToAmount = scaledPublicAmount[1];

    List<GrpcAPI.SpendNoteURC20> shieldedSpends = request.getShieldedSpendsList();
    List<ReceiveNote> shieldedReceives = request.getShieldedReceivesList();
    checkShieldedURC20NoteValue(shieldedSpends, shieldedReceives);

    int spendSize = shieldedSpends.size();
    int receiveSize = shieldedReceives.size();
    long totalToAmount = 0;
    if (scaledToAmount > 0) {
      try {
        totalToAmount = receiveSize == 0 ? scaledToAmount
            : (Math.addExact(scaledToAmount, shieldedReceives.get(0).getNote().getValue()));
      } catch (ArithmeticException e) {
        throw new ZksnarkException("Unbalanced burn!");
      }
    }

    if (scaledFromAmount > 0 && spendSize == 0 && receiveSize == 1
        && scaledFromAmount == shieldedReceives.get(0).getNote().getValue()
        && scaledToAmount == 0) {
      builder.setShieldedURC20ParametersType(ShieldedURC20ParametersBuilder.ShieldedURC20ParametersType.MINT);

      byte[] ovk = request.getOvk().toByteArray();
      if (ArrayUtils.isEmpty(ovk)) {
        ovk = SpendingKey.random().fullViewingKey().getOvk();
      }

      builder.setTransparentFromAmount(fromAmount);
      buildShieldedURC20Output(builder, shieldedReceives.get(0), ovk);
    } else if (scaledFromAmount == 0 && spendSize > 0 && spendSize < 3
        && receiveSize > 0 && receiveSize < 3 && scaledToAmount == 0) {
      builder.setShieldedURC20ParametersType(ShieldedURC20ParametersBuilder.ShieldedURC20ParametersType.TRANSFER);

      byte[] ask = request.getAsk().toByteArray();
      byte[] nsk = request.getNsk().toByteArray();
      byte[] ovk = request.getOvk().toByteArray();
      if ((ArrayUtils.isEmpty(ask) || ArrayUtils.isEmpty(nsk) || ArrayUtils.isEmpty(ovk))) {
        throw new ContractValidateException("No shielded URC-20 ask, nsk or ovk");
      }

      ExpandedSpendingKey expsk = new ExpandedSpendingKey(ask, nsk, ovk);
      for (GrpcAPI.SpendNoteURC20 spendNote : shieldedSpends) {
        buildShieldedURC20Input(builder, spendNote, expsk);
      }

      for (ReceiveNote receiveNote : shieldedReceives) {
        buildShieldedURC20Output(builder, receiveNote, ovk);
      }
    } else if (scaledFromAmount == 0 && spendSize == 1 && receiveSize >= 0 && receiveSize <= 1
        && scaledToAmount > 0 && totalToAmount == shieldedSpends.get(0).getNote().getValue()) {
      builder.setShieldedURC20ParametersType(ShieldedURC20ParametersBuilder.ShieldedURC20ParametersType.BURN);

      byte[] ask = request.getAsk().toByteArray();
      byte[] nsk = request.getNsk().toByteArray();
      byte[] ovk = request.getOvk().toByteArray();
      if ((ArrayUtils.isEmpty(ask) || ArrayUtils.isEmpty(nsk) || ArrayUtils.isEmpty(ovk))) {
        throw new ContractValidateException("No shielded URC-20 ask, nsk or ovk");
      }

      byte[] transparentToAddress = request.getTransparentToAddress().toByteArray();
      if (ArrayUtils.isEmpty(transparentToAddress) || transparentToAddress.length != 21) {
        throw new ContractValidateException("No valid transparent URC-20 output address");
      }

      byte[] transparentToAddressUvm = new byte[20];
      System.arraycopy(transparentToAddress, 1, transparentToAddressUvm, 0, 20);
      builder.setTransparentToAddress(transparentToAddressUvm);
      builder.setTransparentToAmount(toAmount);

      Optional<byte[]> cipher = NoteEncryption.Encryption
          .encryptBurnMessageByOvk(ovk, toAmount, transparentToAddress);
      cipher.ifPresent(builder::setBurnCiphertext);

      ExpandedSpendingKey expsk = new ExpandedSpendingKey(ask, nsk, ovk);
      GrpcAPI.SpendNoteURC20 spendNote = shieldedSpends.get(0);
      buildShieldedURC20Input(builder, spendNote, expsk);
      if (receiveSize == 1) {
        buildShieldedURC20Output(builder, shieldedReceives.get(0), ovk);
      }
    } else {
      throw new ContractValidateException("invalid shielded URC-20 parameters");
    }

    return builder.build(true);
  }

  private void buildShieldedURC20InputWithAK(
      ShieldedURC20ParametersBuilder builder, GrpcAPI.SpendNoteURC20 spendNote,
      byte[] ak, byte[] nsk) throws ZksnarkException {
    GrpcAPI.Note note = spendNote.getNote();
    PaymentAddress paymentAddress = KeyIo.decodePaymentAddress(note.getPaymentAddress());
    if (Objects.isNull(paymentAddress)) {
      throw new ZksnarkException(PAYMENT_ADDRESS_FORMAT_WRONG);
    }

    Note baseNote = new Note(paymentAddress.getD(),
        paymentAddress.getPkD(), note.getValue(), note.getRcm().toByteArray());
    builder.addSpend(ak,
        nsk,
        baseNote,
        spendNote.getAlpha().toByteArray(),
        spendNote.getRoot().toByteArray(),
        spendNote.getPath().toByteArray(),
        spendNote.getPos());
  }

  public ShieldedURC20Parameters createShieldedContractParametersWithoutAsk(
      PrivateShieldedURC20ParametersWithoutAsk request)
      throws ZksnarkException, ContractValidateException, ContractExeException {
    checkFullNodeAllowShieldedTransaction();

    ShieldedURC20ParametersBuilder builder = new ShieldedURC20ParametersBuilder();
    byte[] shieldedURC20ContractAddress = request.getShieldedURC20ContractAddress().toByteArray();
    if (ArrayUtils.isEmpty(shieldedURC20ContractAddress)
        || shieldedURC20ContractAddress.length != 21) {
      throw new ContractValidateException("No valid shielded URC-20 contract address");
    }
    byte[] shieldedURC20ContractAddressUvm = new byte[20];
    System.arraycopy(shieldedURC20ContractAddress, 1, shieldedURC20ContractAddressUvm, 0, 20);
    builder.setShieldedURC20Address(shieldedURC20ContractAddressUvm);

    BigInteger fromAmount;
    BigInteger toAmount;
    try {
      fromAmount = getBigIntegerFromString(request.getFromAmount());
      toAmount = getBigIntegerFromString(request.getToAmount());
    } catch (Exception e) {
      throw new ContractValidateException("invalid_from amount or to_amount");
    }
    long[] scaledPublicAmount = checkPublicAmount(shieldedURC20ContractAddress,
        fromAmount, toAmount);
    long scaledFromAmount = scaledPublicAmount[0];
    long scaledToAmount = scaledPublicAmount[1];

    List<GrpcAPI.SpendNoteURC20> shieldedSpends = request.getShieldedSpendsList();
    int spendSize = shieldedSpends.size();
    List<ReceiveNote> shieldedReceives = request.getShieldedReceivesList();
    int receiveSize = shieldedReceives.size();
    checkShieldedURC20NoteValue(shieldedSpends, shieldedReceives);
    long totalToAmount = 0;
    if (scaledToAmount > 0) {
      try {
        totalToAmount = receiveSize == 0 ? scaledToAmount
            : Math.addExact(scaledToAmount, shieldedReceives.get(0).getNote().getValue());
      } catch (ArithmeticException e) {
        throw new ZksnarkException("Unbalanced burn!");
      }
    }

    if (scaledFromAmount > 0 && spendSize == 0 && receiveSize == 1
        && scaledFromAmount == shieldedReceives.get(0).getNote().getValue()
        && scaledToAmount == 0) {
      byte[] ovk = request.getOvk().toByteArray();
      if (ArrayUtils.isEmpty(ovk)) {
        ovk = SpendingKey.random().fullViewingKey().getOvk();
      }
      builder.setShieldedURC20ParametersType(ShieldedURC20ParametersBuilder.ShieldedURC20ParametersType.MINT);
      builder.setTransparentFromAmount(fromAmount);
      ReceiveNote receiveNote = shieldedReceives.get(0);
      buildShieldedURC20Output(builder, receiveNote, ovk);
    } else if (scaledFromAmount == 0 && spendSize > 0 && spendSize < 3
        && receiveSize > 0 && receiveSize < 3 && scaledToAmount == 0) {
      builder.setShieldedURC20ParametersType(ShieldedURC20ParametersBuilder.ShieldedURC20ParametersType.TRANSFER);
      byte[] ak = request.getAk().toByteArray();
      byte[] nsk = request.getNsk().toByteArray();
      byte[] ovk = request.getOvk().toByteArray();
      if ((ArrayUtils.isEmpty(ak) || ArrayUtils.isEmpty(nsk) || ArrayUtils.isEmpty(ovk))) {
        throw new ContractValidateException("No shielded URC-20 ak, nsk or ovk");
      }
      for (GrpcAPI.SpendNoteURC20 spendNote : shieldedSpends) {
        buildShieldedURC20InputWithAK(builder, spendNote, ak, nsk);
      }
      for (ReceiveNote receiveNote : shieldedReceives) {
        buildShieldedURC20Output(builder, receiveNote, ovk);
      }
    } else if (scaledFromAmount == 0 && spendSize == 1 && receiveSize >= 0 && receiveSize <= 1
        && scaledToAmount > 0 && totalToAmount == shieldedSpends.get(0).getNote().getValue()) {
      builder.setShieldedURC20ParametersType(ShieldedURC20ParametersBuilder.ShieldedURC20ParametersType.BURN);
      byte[] ak = request.getAk().toByteArray();
      byte[] nsk = request.getNsk().toByteArray();
      byte[] ovk = request.getOvk().toByteArray();
      if ((ArrayUtils.isEmpty(ak) || ArrayUtils.isEmpty(nsk) || ArrayUtils.isEmpty(ovk))) {
        throw new ContractValidateException("No shielded URC-20 ak, nsk or ovk");
      }
      byte[] transparentToAddress = request.getTransparentToAddress().toByteArray();
      if (ArrayUtils.isEmpty(transparentToAddress) || transparentToAddress.length != 21) {
        throw new ContractValidateException("No transparent URC-20 output address");
      }
      byte[] transparentToAddressUvm = new byte[20];
      System.arraycopy(transparentToAddress, 1, transparentToAddressUvm, 0, 20);
      builder.setTransparentToAddress(transparentToAddressUvm);
      builder.setTransparentToAmount(toAmount);
      Optional<byte[]> cipher = NoteEncryption.Encryption
          .encryptBurnMessageByOvk(ovk, toAmount, transparentToAddress);
      cipher.ifPresent(builder::setBurnCiphertext);
      GrpcAPI.SpendNoteURC20 spendNote = shieldedSpends.get(0);
      buildShieldedURC20InputWithAK(builder, spendNote, ak, nsk);
      if (receiveSize == 1) {
        buildShieldedURC20Output(builder, shieldedReceives.get(0), ovk);
      }
    } else {
      throw new ContractValidateException("invalid shielded URC-20 parameters");
    }
    return builder.build(false);
  }

  private int getShieldedURC20LogType(TransactionInfo.Log log, byte[] contractAddress,
      ProtocolStringList topicsList) throws ZksnarkException {
    byte[] logAddress = log.getAddress().toByteArray();
    byte[] addressWithoutPrefix = new byte[20];
    if (ArrayUtils.isEmpty(contractAddress) || contractAddress.length != 21) {
      throw new ZksnarkException("invalid contract address");
    }
    System.arraycopy(contractAddress, 1, addressWithoutPrefix, 0, 20);
    if (Arrays.equals(logAddress, addressWithoutPrefix)) {
      List<ByteString> logTopicsList = log.getTopicsList();
      byte[] topicsBytes = new byte[0];
      for (ByteString bs : logTopicsList) {
        topicsBytes = ByteUtil.merge(topicsBytes, bs.toByteArray());
      }
      if (Objects.isNull(topicsList) || topicsList.isEmpty()) {
        if (Arrays.equals(topicsBytes, SHIELDED_URC20_LOG_TOPICS_MINT)) {
          return 1;
        } else if (Arrays.equals(topicsBytes, SHIELDED_URC20_LOG_TOPICS_TRANSFER)) {
          return 2;
        } else if (Arrays.equals(topicsBytes, SHIELDED_URC20_LOG_TOPICS_BURN_LEAF)) {
          return 3;
        } else if (Arrays.equals(topicsBytes, SHIELDED_URC20_LOG_TOPICS_BURN_TOKEN)) {
          return 4;
        }
      } else {
        for (String topic : topicsList) {
          byte[] topicHash = Hash.sha3(ByteArray.fromString(topic));
          if (Arrays.equals(topicsBytes, topicHash)) {
            if (topic.toLowerCase().contains("mint")) {
              return 1;
            } else if (topic.toLowerCase().contains("transfer")) {
              return 2;
            } else if (topic.toLowerCase().contains("burn")) {
              if (topic.toLowerCase().contains("leaf")) {
                return 3;
              } else if (topic.toLowerCase().contains("token")) {
                return 4;
              }
            }
          }
        }
      }
    }
    return 0;
  }

  private Optional<DecryptNotesURC20.NoteTx> getNoteTxFromLogListByIvk(
      DecryptNotesURC20.NoteTx.Builder builder,
      TransactionInfo.Log log, byte[] ivk, byte[] ak, byte[] nk, byte[] contractAddress,
      int logType)
      throws ZksnarkException, ContractExeException {
    byte[] logData = log.getData().toByteArray();
    if (!ArrayUtils.isEmpty(logData) && logType > 0 && logType < 4) {
      // Data = pos(32) + cm(32) + cv(32) + epk(32) + c_enc(580) + c_out(80)
      long pos = ByteArray.toLong(ByteArray.subArray(logData, 0, 32));
      byte[] cm = ByteArray.subArray(logData, 32, 64);
      byte[] epk = ByteArray.subArray(logData, 96, 128);
      byte[] cenc = ByteArray.subArray(logData, 128, 708);
      Optional<Note> notePlaintext = Note.decrypt(cenc, // ciphertext
          ivk, epk, cm);

      if (notePlaintext.isPresent()) {
        Note noteText = notePlaintext.get();
        byte[] pkD = new byte[32];
        if (!JLibrustzcash
            .librustzcashIvkToPkd(new IvkToPkdParams(ivk, noteText.getD().getData(), pkD))) {
          throw new ZksnarkException("get payment address error");
        }

        String paymentAddress = KeyIo
            .encodePaymentAddress(new PaymentAddress(noteText.getD(), pkD));
        GrpcAPI.Note note = GrpcAPI.Note.newBuilder()
            .setPaymentAddress(paymentAddress)
            .setValue(noteText.getValue())
            .setRcm(ByteString.copyFrom(noteText.getRcm()))
            .setMemo(ByteString.copyFrom(stripRightZero(noteText.getMemo())))
            .build();

        if (!(ArrayUtils.isEmpty(ak) || ArrayUtils.isEmpty(nk))) {
          builder.setIsSpent(isShieldedURC20NoteSpent(note, pos, ak, nk, contractAddress));
        }

        return Optional.of(builder.setNote(note).setPosition(pos).build());
      }
    }

    return Optional.empty();
  }

  private DecryptNotesURC20 queryURC20NoteByIvk(long startNum, long endNum,
      byte[] shieldedURC20ContractAddress, byte[] ivk, byte[] ak, byte[] nk,
      ProtocolStringList topicsList)
      throws BadItemException, ZksnarkException, ContractExeException {
    if (!(startNum >= 0 && endNum > startNum && endNum - startNum <= 1000)) {
      throw new BadItemException(
          SHIELDED_TRANSACTION_SCAN_RANGE);
    }

    DecryptNotesURC20.Builder builder = DecryptNotesURC20.newBuilder();
    BlockList blockList = this.getBlocksByLimitNext(startNum, endNum - startNum);
    for (Block block : blockList.getBlockList()) {
      for (Transaction transaction : block.getTransactionsList()) {
        TransactionCapsule transactionCapsule = new TransactionCapsule(transaction);
        byte[] txId = transactionCapsule.getTransactionId().getBytes();
        TransactionInfo info = this.getTransactionInfoById(ByteString.copyFrom(txId));
        DecryptNotesURC20.NoteTx.Builder noteBuilder;
        if (!Objects.isNull(info)) {
          List<TransactionInfo.Log> logList = info.getLogList();
          if (!Objects.isNull(logList)) {
            Optional<DecryptNotesURC20.NoteTx> noteTx;
            int index = 0;
            for (TransactionInfo.Log log : logList) {
              int logType = getShieldedURC20LogType(log, shieldedURC20ContractAddress, topicsList);
              if (logType > 0) {
                noteBuilder = DecryptNotesURC20.NoteTx.newBuilder();
                noteBuilder.setTxid(ByteString.copyFrom(txId));
                noteBuilder.setIndex(index);
                index += 1;
                noteTx = getNoteTxFromLogListByIvk(noteBuilder, log, ivk, ak, nk,
                    shieldedURC20ContractAddress, logType);
                noteTx.ifPresent(builder::addNoteTxs);
              }
            }
          }
        }
      } //end of transaction
    } //end of blocklist
    return builder.build();
  }

  private boolean isShieldedURC20NoteSpent(GrpcAPI.Note note, long pos, byte[] ak,
      byte[] nk, byte[] contractAddress)
      throws ZksnarkException, ContractExeException {
    byte[] nf = getShieldedURC20Nullifier(note, pos, ak, nk);
    if (Objects.isNull(nf)) {
      throw new ZksnarkException("compute nullifier error");
    }

    String methodSign = "nullifiers(bytes32)";
    byte[] selector = new byte[4];
    System.arraycopy(Hash.sha3(methodSign.getBytes()), 0, selector, 0, 4);
    byte[] input = ByteUtil.merge(selector, nf);

    TriggerSmartContract.Builder triggerBuilder = TriggerSmartContract.newBuilder();
    triggerBuilder.setContractAddress(ByteString.copyFrom(contractAddress));
    triggerBuilder.setData(ByteString.copyFrom(input));
    TriggerSmartContract trigger = triggerBuilder.build();

    TransactionExtention.Builder unxExtBuilder = TransactionExtention.newBuilder();
    Return.Builder retBuilder = Return.newBuilder();
    TransactionExtention unxExt;

    try {
      TransactionCapsule unxCap = createTransactionCapsule(trigger,
          ContractType.TriggerSmartContract);
      Transaction unx = triggerConstantContract(trigger, unxCap, unxExtBuilder, retBuilder);

      retBuilder.setResult(true).setCode(response_code.SUCCESS);
      unxExtBuilder.setTransaction(unx);
      unxExtBuilder.setTxid(unxCap.getTransactionId().getByteString());
      unxExtBuilder.setResult(retBuilder);
    } catch (ContractValidateException | VMIllegalException e) {
      retBuilder.setResult(false).setCode(response_code.CONTRACT_VALIDATE_ERROR)
          .setMessage(ByteString.copyFromUtf8(CONTRACT_VALIDATE_ERROR + e.getMessage()));
      unxExtBuilder.setResult(retBuilder);
      logger.warn(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
    } catch (RuntimeException e) {
      retBuilder.setResult(false).setCode(response_code.CONTRACT_EXE_ERROR)
          .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
      unxExtBuilder.setResult(retBuilder);
      logger.warn("When run constant call in VM, have RuntimeException: " + e.getMessage());
    } catch (Exception e) {
      retBuilder.setResult(false).setCode(response_code.OTHER_ERROR)
          .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
      unxExtBuilder.setResult(retBuilder);
      logger.warn("unknown exception caught: " + e.getMessage(), e);
    } finally {
      unxExt = unxExtBuilder.build();
    }

    String code = unxExt.getResult().getCode().toString();
    if ("SUCCESS".equals(code)) {
      List<ByteString> list = unxExt.getConstantResultList();
      byte[] listBytes = new byte[0];
      for (ByteString bs : list) {
        listBytes = ByteUtil.merge(listBytes, bs.toByteArray());
      }
      return Arrays.equals(nf, listBytes);
    } else {
      // trigger contract failed
      throw new ContractExeException("trigger contract to get nullifier error.");
    }
  }

  public DecryptNotesURC20 scanShieldedURC20NotesByIvk(
      long startNum, long endNum, byte[] shieldedURC20ContractAddress,
      byte[] ivk, byte[] ak, byte[] nk, ProtocolStringList topicsList)
      throws BadItemException, ZksnarkException, ContractExeException {
    checkFullNodeAllowShieldedTransaction();

    return queryURC20NoteByIvk(startNum, endNum,
        shieldedURC20ContractAddress, ivk, ak, nk, topicsList);
  }

  private Optional<DecryptNotesURC20.NoteTx> getNoteTxFromLogListByOvk(
      DecryptNotesURC20.NoteTx.Builder builder,
      TransactionInfo.Log log, byte[] ovk, int logType) throws ZksnarkException {
    byte[] logData = log.getData().toByteArray();
    if (!ArrayUtils.isEmpty(logData)) {
      if (logType > 0 && logType < 4) {
        //Data = pos(32) + cm(32) + cv(32) + epk(32) + c_enc(580) + c_out(80)
        byte[] cm = ByteArray.subArray(logData, 32, 64);
        byte[] cv = ByteArray.subArray(logData, 64, 96);
        byte[] epk = ByteArray.subArray(logData, 96, 128);
        byte[] cenc = ByteArray.subArray(logData, 128, 708);
        byte[] coutText = ByteArray.subArray(logData, 708, 788);
        NoteEncryption.Encryption.OutCiphertext cout = new NoteEncryption.Encryption.OutCiphertext();
        cout.setData(coutText);
        Optional<OutgoingPlaintext> notePlaintext = OutgoingPlaintext.decrypt(cout,//ciphertext
            ovk, cv, cm, epk);
        if (notePlaintext.isPresent()) {
          OutgoingPlaintext decryptedOutCtUnwrapped = notePlaintext.get();
          //decode c_enc with pkd、esk
          NoteEncryption.Encryption.EncCiphertext ciphertext = new NoteEncryption.Encryption.EncCiphertext();
          ciphertext.setData(cenc);
          Optional<Note> foo = Note.decrypt(ciphertext,
              epk,
              decryptedOutCtUnwrapped.getEsk(),
              decryptedOutCtUnwrapped.getPkD(),
              cm);
          if (foo.isPresent()) {
            Note bar = foo.get();
            String paymentAddress = KeyIo.encodePaymentAddress(
                new PaymentAddress(bar.getD(), decryptedOutCtUnwrapped.getPkD()));
            GrpcAPI.Note note = GrpcAPI.Note.newBuilder()
                .setPaymentAddress(paymentAddress)
                .setValue(bar.getValue())
                .setRcm(ByteString.copyFrom(bar.getRcm()))
                .setMemo(ByteString.copyFrom(stripRightZero(bar.getMemo())))
                .build();
            builder.setNote(note);
            return Optional.of(builder.build());
          }
        }
      } else if (logType == 4) {
        //Data = toAddress(32) + value(32) + ciphertext(80) + padding(16)
        byte[] logToAddress = ByteArray.subArray(logData, 12, 32);
        byte[] logAmountArray = ByteArray.subArray(logData, 32, 64);
        byte[] cipher = ByteArray.subArray(logData, 64, 144);
        BigInteger logAmount = ByteUtil.bytesToBigInteger(logAmountArray);
        byte[] plaintext;
        byte[] amountArray = new byte[32];
        byte[] decryptedAddress = new byte[20];
        Optional<byte[]> decryptedText = NoteEncryption.Encryption
            .decryptBurnMessageByOvk(ovk, cipher);
        if (decryptedText.isPresent()) {
          plaintext = decryptedText.get();
          System.arraycopy(plaintext, 0, amountArray, 0, 32);
          System.arraycopy(plaintext, 33, decryptedAddress, 0, 20);
          BigInteger decryptedAmount = ByteUtil.bytesToBigInteger(amountArray);
          if (logAmount.equals(decryptedAmount) && Hex.toHexString(logToAddress)
              .equals(Hex.toHexString(decryptedAddress))) {
            byte[] addressWithPrefix = new byte[21];
            System.arraycopy(plaintext, 32, addressWithPrefix, 0, 21);
            builder.setToAmount(logAmount.toString(10))
                .setTransparentToAddress(ByteString.copyFrom(addressWithPrefix));
            return Optional.of(builder.build());
          }
        }
      }
    }
    return Optional.empty();
  }

  public DecryptNotesURC20 scanShieldedURC20NotesByOvk(long startNum, long endNum,
      byte[] ovk, byte[] shieldedURC20ContractAddress, ProtocolStringList topicsList)
      throws ZksnarkException, BadItemException {
    checkFullNodeAllowShieldedTransaction();

    DecryptNotesURC20.Builder builder = DecryptNotesURC20.newBuilder();
    if (!(startNum >= 0 && endNum > startNum && endNum - startNum <= 1000)) {
      throw new BadItemException(
          SHIELDED_TRANSACTION_SCAN_RANGE);
    }
    BlockList blockList = this.getBlocksByLimitNext(startNum, endNum - startNum);
    for (Block block : blockList.getBlockList()) {
      for (Transaction transaction : block.getTransactionsList()) {
        TransactionCapsule transactionCapsule = new TransactionCapsule(transaction);
        byte[] txid = transactionCapsule.getTransactionId().getBytes();
        TransactionInfo info = this.getTransactionInfoById(ByteString.copyFrom(txid));
        DecryptNotesURC20.NoteTx.Builder noteBuilder;
        if (!Objects.isNull(info)) {
          List<TransactionInfo.Log> logList = info.getLogList();
          if (!Objects.isNull(logList)) {
            Optional<DecryptNotesURC20.NoteTx> noteTx;
            int index = 0;
            for (TransactionInfo.Log log : logList) {
              int logType = getShieldedURC20LogType(log, shieldedURC20ContractAddress, topicsList);
              if (logType > 0) {
                noteBuilder = DecryptNotesURC20.NoteTx.newBuilder();
                noteBuilder.setTxid(ByteString.copyFrom(txid));
                noteBuilder.setIndex(index);
                index += 1;
                noteTx = getNoteTxFromLogListByOvk(noteBuilder, log, ovk, logType);
                noteTx.ifPresent(builder::addNoteTxs);
              }
            }
          }
        }
      } // end of transaction
    } // end of blocklist
    return builder.build();
  }

  private byte[] getShieldedURC20Nullifier(GrpcAPI.Note note, long pos, byte[] ak,
      byte[] nk) throws ZksnarkException {
    byte[] result = new byte[32]; // 256
    PaymentAddress paymentAddress = KeyIo.decodePaymentAddress(
        note.getPaymentAddress());
    if (Objects.isNull(paymentAddress)) {
      throw new ZksnarkException(PAYMENT_ADDRESS_FORMAT_WRONG);
    }

    ComputeNfParams computeNfParams = new ComputeNfParams(
        paymentAddress.getD().getData(),
        paymentAddress.getPkD(),
        note.getValue(),
        note.getRcm().toByteArray(),
        ak,
        nk,
        pos,
        result);
    if (!JLibrustzcash.librustzcashComputeNf(computeNfParams)) {
      return null;
    }
    return result;
  }

  public GrpcAPI.NullifierResult isShieldedURC20ContractNoteSpent(NfURC20Parameters request) throws
      ZksnarkException, ContractExeException {
    checkFullNodeAllowShieldedTransaction();

    return GrpcAPI.NullifierResult.newBuilder()
        .setIsSpent(isShieldedURC20NoteSpent(request.getNote(),
            request.getPosition(),
            request.getAk().toByteArray(),
            request.getNk().toByteArray(),
            request.getShieldedURC20ContractAddress().toByteArray()))
        .build();
  }

  private BigInteger getBigIntegerFromString(String in) {
    String trimmedIn = in.trim();
    if (trimmedIn.length() == 0) {
      return BigInteger.ZERO;
    }
    return new BigInteger(trimmedIn, 10);
  }

  /**
   * trigger contract to get the scalingFactor, and check the public amount,
   */
  private long[] checkPublicAmount(byte[] address, BigInteger fromAmount, BigInteger toAmount)
      throws ContractExeException, ContractValidateException {
    checkBigIntegerRange(fromAmount);
    checkBigIntegerRange(toAmount);

    BigInteger scalingFactor;
    try {
      byte[] scalingFactorBytes = getShieldedContractScalingFactor(address);
      scalingFactor = ByteUtil.bytesToBigInteger(scalingFactorBytes);
    } catch (ContractExeException e) {
      throw new ContractExeException("Get shielded contract scalingFactor failed");
    }

    // fromAmount and toAmount must be a multiple of scalingFactor
    if (!(fromAmount.mod(scalingFactor).equals(BigInteger.ZERO)
        && toAmount.mod(scalingFactor).equals(BigInteger.ZERO))) {
      throw new ContractValidateException("fromAmount or toAmount invalid");
    }

    long[] ret = new long[2];
    try {
      ret[0] = fromAmount.divide(scalingFactor).longValueExact();
      ret[1] = toAmount.divide(scalingFactor).longValueExact();
    } catch (ArithmeticException e) {
      throw new ContractValidateException("fromAmount or toAmount invalid");
    }

    return ret;
  }

  private void checkBigIntegerRange(BigInteger in) throws ContractValidateException {
    if (in.compareTo(BigInteger.ZERO) < 0) {
      throw new ContractValidateException("public amount must be non-negative");
    }
    if (in.bitLength() > 256) {
      throw new ContractValidateException("public amount must be no more than 256 bits");
    }
  }

  private byte[] getShieldedContractScalingFactor(byte[] contractAddress)
      throws ContractExeException {
    String methodSign = "scalingFactor()";
    byte[] selector = new byte[4];
    System.arraycopy(Hash.sha3(methodSign.getBytes()), 0, selector, 0, 4);

    TriggerSmartContract.Builder triggerBuilder = TriggerSmartContract.newBuilder();
    triggerBuilder.setContractAddress(ByteString.copyFrom(contractAddress));
    triggerBuilder.setData(ByteString.copyFrom(selector));
    TriggerSmartContract trigger = triggerBuilder.build();

    TransactionExtention.Builder unxExtBuilder = TransactionExtention.newBuilder();
    Return.Builder retBuilder = Return.newBuilder();
    TransactionExtention unxExt;

    try {
      TransactionCapsule unxCap = createTransactionCapsule(trigger,
          ContractType.TriggerSmartContract);
      Transaction unx = triggerConstantContract(trigger, unxCap, unxExtBuilder, retBuilder);

      retBuilder.setResult(true).setCode(response_code.SUCCESS);
      unxExtBuilder.setTransaction(unx);
      unxExtBuilder.setTxid(unxCap.getTransactionId().getByteString());
      unxExtBuilder.setResult(retBuilder);
    } catch (ContractValidateException | VMIllegalException e) {
      retBuilder.setResult(false).setCode(response_code.CONTRACT_VALIDATE_ERROR)
          .setMessage(ByteString.copyFromUtf8(CONTRACT_VALIDATE_ERROR + e.getMessage()));
      unxExtBuilder.setResult(retBuilder);
      logger.warn(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
    } catch (RuntimeException e) {
      retBuilder.setResult(false).setCode(response_code.CONTRACT_EXE_ERROR)
          .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
      unxExtBuilder.setResult(retBuilder);
      logger.warn("When run constant call in VM, have RuntimeException: " + e.getMessage());
    } catch (Exception e) {
      retBuilder.setResult(false).setCode(response_code.OTHER_ERROR)
          .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
      unxExtBuilder.setResult(retBuilder);
      logger.warn("Unknown exception caught: " + e.getMessage(), e);
    } finally {
      unxExt = unxExtBuilder.build();
    }

    String code = unxExt.getResult().getCode().toString();
    if ("SUCCESS".equals(code)) {
      List<ByteString> list = unxExt.getConstantResultList();
      byte[] listBytes = new byte[0];
      for (ByteString bs : list) {
        listBytes = ByteUtil.merge(listBytes, bs.toByteArray());
      }
      return listBytes;
    } else {
      throw new ContractExeException("trigger contract to get scaling factor error.");
    }
  }

  public BytesMessage getTriggerInputForShieldedURC20Contract(
      ShieldedURC20TriggerContractParameters request)
      throws ZksnarkException, ContractValidateException {
    checkFullNodeAllowShieldedTransaction();

    ShieldedURC20Parameters shieldedURC20Parameters = request.getShieldedURC20Parameters();
    List<BytesMessage> spendAuthoritySignature = request.getSpendAuthoritySignatureList();
    BigInteger value = getBigIntegerFromString(request.getAmount());
    checkBigIntegerRange(value);
    byte[] transparentToAddress = request.getTransparentToAddress().toByteArray();
    byte[] transparentToAddressUvm = new byte[20];
    if (!ArrayUtils.isEmpty(transparentToAddress)) {
      if (transparentToAddress.length == 21) {
        System.arraycopy(transparentToAddress, 1, transparentToAddressUvm, 0, 20);
      } else {
        throw new ZksnarkException("invalid transparent to address");
      }
    }
    String parameterType = shieldedURC20Parameters.getParameterType();
    if (shieldedURC20Parameters.getSpendDescriptionList().size() != spendAuthoritySignature
        .size()) {
      throw new ZksnarkException(
          "the number of spendDescription and spendAuthoritySignature is not equal");
    }
    ShieldedURC20ParametersBuilder parametersBuilder = new ShieldedURC20ParametersBuilder(
        parameterType);
    if (parametersBuilder.getShieldedURC20ParametersType() == ShieldedURC20ParametersBuilder.ShieldedURC20ParametersType.BURN) {
      byte[] burnCiper = ByteArray.fromHexString(shieldedURC20Parameters.getTriggerContractInput());
      if (!ArrayUtils.isEmpty(burnCiper) && burnCiper.length == 80) {
        parametersBuilder.setBurnCiphertext(burnCiper);
      } else {
        throw new ZksnarkException(
            "invalid shielded URC-20 contract parameters for burn trigger input");
      }
    }
    String input = parametersBuilder
        .getTriggerContractInput(shieldedURC20Parameters, spendAuthoritySignature, value, false,
            transparentToAddressUvm);
    if (Objects.isNull(input)) {
      throw new ZksnarkException("generate the trigger contract parameters error");
    }
    BytesMessage.Builder bytesBuilder = BytesMessage.newBuilder();
    return bytesBuilder.setValue(ByteString.copyFrom(Hex.decode(input))).build();
  }

  public BalanceContract.AccountBalanceResponse getAccountBalance(
      BalanceContract.AccountBalanceRequest request)
      throws ItemNotFoundException {
    BalanceContract.AccountIdentifier accountIdentifier = request.getAccountIdentifier();
    checkAccountIdentifier(accountIdentifier);
    BlockBalanceTrace.BlockIdentifier blockIdentifier = request.getBlockIdentifier();
    checkBlockIdentifier(blockIdentifier);

    AccountTraceStore accountTraceStore = chainBaseManager.getAccountTraceStore();
    BlockIndexStore blockIndexStore = chainBaseManager.getBlockIndexStore();
    BlockId blockId = blockIndexStore.get(blockIdentifier.getNumber());
    if (!blockId.getByteString().equals(blockIdentifier.getHash())) {
      throw new IllegalArgumentException("number and hash do not match");
    }

    Pair<Long, Long> pair = accountTraceStore.getPrevBalance(
        accountIdentifier.getAddress().toByteArray(), blockIdentifier.getNumber());
    BalanceContract.AccountBalanceResponse.Builder builder =
        BalanceContract.AccountBalanceResponse.newBuilder();
    if (pair.getLeft() == blockIdentifier.getNumber()) {
      builder.setBlockIdentifier(blockIdentifier);
    } else {
      blockId = blockIndexStore.get(pair.getLeft());
      builder.setBlockIdentifier(BlockBalanceTrace.BlockIdentifier.newBuilder()
          .setNumber(pair.getLeft())
          .setHash(blockId.getByteString()));
    }

    builder.setBalance(pair.getRight());
    return builder.build();
  }

  public BalanceContract.BlockBalanceTrace getBlockBalance(
      BlockBalanceTrace.BlockIdentifier request) throws ItemNotFoundException, BadItemException {
    checkBlockIdentifier(request);
    BalanceTraceStore balanceTraceStore = chainBaseManager.getBalanceTraceStore();
    BlockIndexStore blockIndexStore = chainBaseManager.getBlockIndexStore();
    BlockId blockId = blockIndexStore.get(request.getNumber());
    if (!blockId.getByteString().equals(request.getHash())) {
      throw new IllegalArgumentException("number and hash do not match");
    }

    BlockBalanceTraceCapsule blockBalanceTraceCapsule =
        balanceTraceStore.getBlockBalanceTrace(blockId);
    if (blockBalanceTraceCapsule == null) {
      throw new ItemNotFoundException("This block does not exist");
    }

    return blockBalanceTraceCapsule.getInstance();
  }

  public void checkBlockIdentifier(BlockBalanceTrace.BlockIdentifier blockIdentifier) {
    if (blockIdentifier == blockIdentifier.getDefaultInstanceForType()) {
      throw new IllegalArgumentException("block_identifier null");
    }
    if (blockIdentifier.getNumber() < 0) {
      throw new IllegalArgumentException("block_identifier number less than 0");
    }
    if (blockIdentifier.getHash().size() != 32) {
      throw new IllegalArgumentException("block_identifier hash length not equals 32");
    }

  }

  public void checkAccountIdentifier(BalanceContract.AccountIdentifier accountIdentifier) {
    if (accountIdentifier == accountIdentifier.getDefaultInstanceForType()) {
      throw new IllegalArgumentException("account_identifier is null");
    }
    if (accountIdentifier.getAddress().isEmpty()) {
      throw new IllegalArgumentException("account_identifier address is null");
    }
  }

  public long getEnergyFee() {
    return chainBaseManager.getDynamicPropertiesStore().getEnergyFee();
  }

  // this function should be called after EnergyPriceHistoryLoader done
  public long getEnergyFee(long timestamp) {
    try {
      String energyPriceHistory =
          chainBaseManager.getDynamicPropertiesStore().getEnergyPriceHistory();
      long energyFee = JsonRpcApiUtil.parseEnergyFee(timestamp, energyPriceHistory);

      if (energyFee == -1) {
        energyFee = getEnergyFee();
      }

      return energyFee;
    } catch (Exception e) {
      logger.error("getEnergyFee timestamp={} failed, error is {}", timestamp, e.getMessage());
      return getEnergyFee();
    }
  }

  public String getEnergyPrices() {
    try {
      return chainBaseManager.getDynamicPropertiesStore().getEnergyPriceHistory();
    } catch (Exception e) {
      logger.error("getEnergyPrices failed, error is {}", e.getMessage());
    }

    return null;
  }

  public String getCoinbase() {
    if (!CommonParameter.getInstance().isWitness()) {
      return null;
    }

    // get local witnesses
    List<String> localPrivateKeys = Args.getLocalWitnesses().getPrivateKeys();
    List<String> localWitnessAddresses = new ArrayList<>();
    for (String privateKey : localPrivateKeys) {
      localWitnessAddresses.add(Hex.toHexString(SignUtils
          .fromPrivate(ByteArray.fromHexString(privateKey),
              CommonParameter.getInstance().isECKeyCryptoEngine()).getAddress()));
    }

    // get all witnesses
    List<WitnessCapsule> witnesses = consensusDelegate.getAllWitnesses();
    List<String> witnessAddresses = new ArrayList<>();
    for (WitnessCapsule witnessCapsule : witnesses) {
      witnessAddresses.add(Hex.toHexString(witnessCapsule.getAddress().toByteArray()));
    }

    // check if witnesses contains local witness
    for (String localWitnessAddress : localWitnessAddresses) {
      if (witnessAddresses.contains(localWitnessAddress)) {
        return "0x" + localWitnessAddress;
      }
    }

    return null;
  }

  public boolean isMining() {
    if (!CommonParameter.getInstance().isWitness()) {
      return false;
    }

    // get local witnesses
    List<String> localPrivateKeys = Args.getLocalWitnesses().getPrivateKeys();
    List<String> localWitnessAddresses = new ArrayList<>();
    for (String privateKey : localPrivateKeys) {
      localWitnessAddresses.add(Hex.toHexString(SignUtils
          .fromPrivate(ByteArray.fromHexString(privateKey),
              CommonParameter.getInstance().isECKeyCryptoEngine()).getAddress()));
    }

    // get active witnesses
    List<ByteString> activeWitnesses = consensusDelegate.getActiveWitnesses();
    List<String> activeWitnessAddresses = new ArrayList<>();
    for (ByteString activeWitness : activeWitnesses) {
      activeWitnessAddresses.add(Hex.toHexString(activeWitness.toByteArray()));
    }

    // check if active witnesses contains local witness
    for (String localWitnessAddress : localWitnessAddresses) {
      if (activeWitnessAddresses.contains(localWitnessAddress)) {
        return true;
      }
    }

    return false;
  }

  public Chainbase.Cursor getCursor() {
    return chainBaseManager.getBlockStore().getRevokingDB().getCursor();
  }
}

