package org.unichain.core.services.internal.impl;

import com.google.protobuf.ByteString;
import lombok.var;
import org.unichain.common.utils.PosBridgeUtil;
import org.unichain.core.actuator.urc721.NftTransferTokenActuator;
import org.unichain.core.capsule.PosBridgeConfigCapsule;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.internal.PredicateService;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;

public class PredicateErc721Service implements PredicateService {
    private final Manager dbManager;
    private final TransactionResultCapsule ret;
    private final PosBridgeConfigCapsule config;

    public PredicateErc721Service(Manager dbManager,
                                 TransactionResultCapsule ret,
                                 PosBridgeConfigCapsule config) {
        this.dbManager = dbManager;
        this.ret = ret;
        this.config = config;
    }

    @Override
    public void lockTokens(ByteString depositor, ByteString rootToken,String depositData) throws ContractExeException, ContractValidateException {
        var wrapCtx = Contract.TransferNftTokenContract.newBuilder()
                .setOwnerAddress(depositor)
                .setToAddress(config.getPredicateErc721())
                .setAddress(rootToken)
                .setTokenId(PosBridgeUtil.abiDecodeToUint256(depositData).getValue().longValue())
                .build();
        buildThenExecContract(wrapCtx);
    }

    private void buildThenExecContract(Contract.TransferNftTokenContract wrapCtx) throws ContractExeException, ContractValidateException {
        var contract = new TransactionCapsule(wrapCtx, Protocol.Transaction.Contract.ContractType.TransferNftTokenContract)
                .getInstance()
                .getRawData()
                .getContract(0)
                .getParameter();
        var wrapActuator = new NftTransferTokenActuator(contract, dbManager);
        var wrapRet = new TransactionResultCapsule();
        wrapActuator.validate();
        wrapActuator.execute(wrapRet);
        ret.setFee(wrapRet.getFee());
    }

    @Override
    public void unlockTokens(ByteString withdrawer, ByteString rootToken, String withdrawData) throws ContractExeException, ContractValidateException {
        var wrapCtx = Contract.TransferNftTokenContract.newBuilder()
                .setOwnerAddress(config.getPredicateErc721())
                .setToAddress(withdrawer)
                .setAddress(rootToken)
                .setTokenId(PosBridgeUtil.abiDecodeToUint256(withdrawData).getValue().longValue())
                .build();
        buildThenExecContract(wrapCtx);
    }

}
