package org.unichain.core.services.internal.impl;

import com.google.protobuf.ByteString;
import lombok.var;
import org.unichain.common.utils.PosBridgeUtil;
import org.unichain.core.actuator.urc30.Urc30TokenTransferActuatorV4;
import org.unichain.core.capsule.PosBridgeConfigCapsule;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.internal.PredicateService;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;

public class PredicateErc20Service implements PredicateService {
    private final Manager dbManager;
    private final TransactionResultCapsule ret;
    private final PosBridgeConfigCapsule config;

    public PredicateErc20Service(Manager dbManager,
                                  TransactionResultCapsule ret,
                                  PosBridgeConfigCapsule config) {
        this.dbManager = dbManager;
        this.ret = ret;
        this.config = config;
    }

    @Override
    public void lockTokens(ByteString depositor, ByteString rootToken, String depositData) throws ContractExeException, ContractValidateException {
        var symbol = dbManager.getTokenAddrSymbolIndexStore().get(rootToken.toByteArray()).getSymbol();
        var wrapCtx = Contract.TransferTokenContract.newBuilder()
                .setAmount(PosBridgeUtil.abiDecodeToUint256(depositData).getValue().longValue())
                .setOwnerAddress(depositor)
                .setToAddress(config.getPredicateErc20())
                .setTokenName(symbol)
                .setAvailableTime(0L)
                .build();
        buildThenExecContract(wrapCtx);
    }

    @Override
    public void unlockTokens(ByteString withdrawer, ByteString rootToken, String withdrawData) throws ContractExeException, ContractValidateException {
        var symbol = dbManager.getTokenAddrSymbolIndexStore().get(rootToken.toByteArray()).getSymbol();
        var wrapCtx = Contract.TransferTokenContract.newBuilder()
                .setAmount(PosBridgeUtil.abiDecodeToUint256(withdrawData).getValue().longValue())
                .setOwnerAddress(config.getPredicateErc20())
                .setToAddress(withdrawer)
                .setTokenName(symbol)
                .setAvailableTime(0L)
                .build();
        buildThenExecContract(wrapCtx);
    }

    private void buildThenExecContract(Contract.TransferTokenContract wrapCtx) throws ContractExeException, ContractValidateException {
        var contract = new TransactionCapsule(wrapCtx, Protocol.Transaction.Contract.ContractType.TransferTokenContract)
                .getInstance()
                .getRawData()
                .getContract(0)
                .getParameter();
        var wrapAct = new Urc30TokenTransferActuatorV4(contract, dbManager);
        var wrapRet = new TransactionResultCapsule();
        wrapAct.validate();
        wrapAct.execute(wrapRet);
        ret.setFee(wrapRet.getFee());
    }

}
