package org.unichain.core.actuator.posbridge.ext;

import com.google.protobuf.ByteString;
import lombok.var;
import org.unichain.common.utils.PosBridgeUtil;
import org.unichain.core.actuator.urc20.Urc20TransferActuator;
import org.unichain.core.capsule.PosBridgeConfigCapsule;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;

public class Urc20Predicate implements Predicate {
    private final Manager dbManager;
    private final TransactionResultCapsule ret;
    private final PosBridgeConfigCapsule config;

    public Urc20Predicate(Manager dbManager,
                          TransactionResultCapsule ret,
                          PosBridgeConfigCapsule config) {
        this.dbManager = dbManager;
        this.ret = ret;
        this.config = config;
    }

    //@todo review should not use Urc20TransferFromContract
    @Override
    public void lockTokens(ByteString depositor, ByteString rootToken, String depositData) throws ContractExeException, ContractValidateException {
        var wrapCtx = Contract.Urc20TransferContract.newBuilder()
                .setOwnerAddress(depositor)
                .setTo(config.getPredicateErc20())
                .setAddress(rootToken)
                .setAmount(PosBridgeUtil.abiDecodeToUint256(depositData).getValue().toString())
                .setAvailableTime(0L)
                .build();


        var contract = new TransactionCapsule(wrapCtx, Protocol.Transaction.Contract.ContractType.Urc20TransferContract)
                .getInstance()
                .getRawData()
                .getContract(0)
                .getParameter();

        var actuator = new Urc20TransferActuator(contract, dbManager);
        actuator.validate();
        actuator.execute(ret);
    }

    @Override
    public void unlockTokens(ByteString withdrawer, ByteString rootToken, String withdrawData) throws ContractExeException, ContractValidateException {

        var wrapCtx = Contract.Urc20TransferContract.newBuilder()
                .setOwnerAddress(config.getPredicateErc20())
                .setTo(withdrawer)
                .setAddress(rootToken)
                .setAmount(PosBridgeUtil.abiDecodeToUint256(withdrawData).getValue().toString())
                .setAvailableTime(0L)
                .build();

        var contract = new TransactionCapsule(wrapCtx, Protocol.Transaction.Contract.ContractType.Urc20TransferContract)
                .getInstance()
                .getRawData()
                .getContract(0)
                .getParameter();

        var actuator = new Urc20TransferActuator(contract, dbManager);
        actuator.validate();
        actuator.execute(ret);
    }

}
