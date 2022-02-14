package org.unichain.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.NftCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.CreateNftContract;
import org.unichain.protos.Protocol;

@Slf4j(topic = "actuator")
public class CreateNftActuator extends AbstractActuator {

    CreateNftActuator(Any contract, Manager dbManager) {
        super(contract, dbManager);
    }

    @Override
    public boolean execute(TransactionResultCapsule result) throws ContractExeException {
        var fee = calcFee();
        try {
            var ctx = contract.unpack(CreateNftContract.class);
            var capsule = new NftCapsule(ctx);

            capsule.setSymbol(ctx.getSymbol());
            capsule.setName(ctx.getName());
            capsule.setTotalSupply(ctx.getTotalSupply());
            capsule.setTokenIndex(ctx.getTokenIndex());
            capsule.setMinter(ctx.getMinter());
            capsule.setLastOperationTime(ctx.getLastOperation());
            capsule.setOwnerAddress(ctx.getOwner());

            dbManager.getNftStore().put(capsule);
            chargeFee(ctx.getOwner().toByteArray(), fee);
            result.setStatus(fee, Protocol.Transaction.Result.code.SUCESS);
            return true;
        } catch (InvalidProtocolBufferException | BalanceInsufficientException e) {
            logger.error("Actuator error: {} --> ", e.getMessage(), e);
            result.setStatus(fee, Protocol.Transaction.Result.code.FAILED);
            throw new ContractExeException(e.getMessage());
        }
    }

    @Override
    public boolean validate() throws ContractValidateException {
        try {
            Assert.notNull(contract, "No contract!");
            Assert.notNull(dbManager, "No dbManager!");
            var ctx = contract.unpack(CreateNftContract.class);
            var ownerAddress = ctx.getOwner().toByteArray();
            Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid ownerAddress");
            Assert.isTrue(!ctx.getSymbol().isEmpty(), "Invalid symbol");
            var symbol  = ctx.getSymbol().getBytes();
            var nft = dbManager.getNftStore().get(symbol);
            Assert.notNull(nft, "nft has existed");
            return true;
        } catch (Exception e) {
            logger.error("Actuator error: {} --> ", e.getMessage(), e);
            throw new ContractValidateException(e.getMessage());
        }
    }

    @Override
    public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
        return contract.unpack(CreateNftContract.class).getOwner();
    }

    @Override
    public long calcFee() {
        return dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract();
    }
}
