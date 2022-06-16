package org.unichain.core.actuator.posbridge.ext;

import com.google.protobuf.ByteString;
import lombok.var;
import org.unichain.common.utils.PosBridgeUtil;
import org.unichain.core.actuator.TransferActuator;
import org.unichain.core.capsule.PosBridgeConfigCapsule;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;

public class UnwPredicate implements Predicate {

    private final Manager dbManager;
    private final TransactionResultCapsule ret;
    private final PosBridgeConfigCapsule config;

    public UnwPredicate(Manager dbManager,
                        TransactionResultCapsule ret,
                        PosBridgeConfigCapsule config) {
        this.dbManager = dbManager;
        this.ret = ret;
        this.config = config;
    }

    @Override
    public void lockTokens(ByteString depositor, ByteString rootToken, String depositData) throws ContractExeException, ContractValidateException {
        var wrapCtx = Contract.TransferContract.newBuilder()
                .setAmount(PosBridgeUtil.abiDecodeToUint256(depositData).getValue().longValue())
                .setOwnerAddress(depositor)
                .setToAddress(config.getPredicateNative())
                .build();
        buildThenExecContract(wrapCtx);
    }

    @Override
    public void unlockTokens(ByteString withdrawer, ByteString rootToken, String withdrawData) throws ContractExeException, ContractValidateException {
        var wrapCtx = Contract.TransferContract.newBuilder()
                .setAmount(PosBridgeUtil.abiDecodeToUint256(withdrawData).getValue().longValue())
                .setOwnerAddress(config.getPredicateNative())
                .setToAddress(withdrawer)
                .build();
        buildThenExecContract(wrapCtx);
    }

    private void buildThenExecContract(Contract.TransferContract wrapCtx) throws ContractExeException, ContractValidateException {
        var contract = new TransactionCapsule(wrapCtx, Protocol.Transaction.Contract.ContractType.TransferContract)
                .getInstance()
                .getRawData()
                .getContract(0)
                .getParameter();
        var wrapActuator = new TransferActuator(contract, dbManager);
        var wrapRet = new TransactionResultCapsule();
        wrapActuator.validate();
        wrapActuator.execute(wrapRet);
        ret.setFee(wrapRet.getFee());
    }

}
