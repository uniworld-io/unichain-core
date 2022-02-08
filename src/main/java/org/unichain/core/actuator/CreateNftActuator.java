package org.unichain.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.CreateNftContract;
import org.unichain.protos.Protocol;

@Slf4j(topic = "actuator")
public class CreateNftActuator extends AbstractActuator {

    CreateNftActuator(Any contract, Manager dbManager) {
        super(contract, dbManager);
    }

    //@todo: coding method execute
    @Override
    public boolean execute(TransactionResultCapsule result) throws ContractExeException {
        var fee = calcFee();
        try {
            var ctx = contract.unpack(CreateNftContract.class);

            return true;
        } catch (InvalidProtocolBufferException e) {
            logger.error("Actuator error: {} --> ", e.getMessage(), e);
            result.setStatus(fee, Protocol.Transaction.Result.code.FAILED);
            throw new ContractExeException(e.getMessage());
        }
    }

    //@todo: coding method validate
    @Override
    public boolean validate() throws ContractValidateException {
        return false;
    }

    @Override
    public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
        return contract.unpack(CreateNftContract.class).getOwnerAddress();
    }

    @Override
    public long calcFee() {
        return dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract();
    }
}
