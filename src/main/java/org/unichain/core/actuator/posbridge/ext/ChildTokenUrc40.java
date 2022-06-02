package org.unichain.core.actuator.posbridge.ext;

import com.google.protobuf.ByteString;
import lombok.var;
import org.unichain.common.utils.PosBridgeUtil;
import org.unichain.core.actuator.urc40.Urc40BurnActuator;
import org.unichain.core.actuator.urc40.Urc40MintActuator;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;

public class ChildTokenUrc40 implements ChildToken {

    private final Manager dbManager;
    private final TransactionResultCapsule ret;

    public ChildTokenUrc40(Manager dbManager, TransactionResultCapsule ret) {
        this.dbManager = dbManager;
        this.ret = ret;
    }

    @Override
    public void deposit(ByteString user, ByteString childToken, String depositData) throws ContractExeException, ContractValidateException {
        //load token and transfer from token owner to ...
        var symbol = dbManager.getTokenAddrSymbolIndexStore().get(childToken.toByteArray()).getSymbol();
        var tokenInfo = dbManager.getTokenPoolStore().get(Util.stringAsBytesUppercase(symbol));

        var wrapCtx = Contract.Urc40MintContract.newBuilder()
                .setOwnerAddress(user)
                .setAddress(tokenInfo.getAddress())
                .setAmount(PosBridgeUtil.abiDecodeToUint256(depositData).getValue().longValue())
                .build();
        var wrapCap = new TransactionCapsule(wrapCtx, Protocol.Transaction.Contract.ContractType.Urc40MintContract)
                .getInstance()
                .getRawData()
                .getContract(0)
                .getParameter();
        var urc40Mint = new Urc40MintActuator(wrapCap, dbManager);
        urc40Mint.validate();
        urc40Mint.execute(ret);
    }

    @Override
    public void withdraw(ByteString user, ByteString childToken, String withdrawData) throws ContractExeException, ContractValidateException {
        var symbol = dbManager.getTokenAddrSymbolIndexStore().get(childToken.toByteArray()).getSymbol();
        var tokenInfo = dbManager.getTokenPoolStore().get(Util.stringAsBytesUppercase(symbol));

        var wrapCtx = Contract.Urc40BurnContract.newBuilder()
                .setAmount(PosBridgeUtil.abiDecodeToUint256(withdrawData).getValue().longValue())
                .setOwnerAddress(user)
                .setAddress(tokenInfo.getAddress())
                .build();
        var wrapCap = new TransactionCapsule(wrapCtx, Protocol.Transaction.Contract.ContractType.Urc40BurnContract)
                .getInstance()
                .getRawData()
                .getContract(0)
                .getParameter();

        var urc40Burner = new Urc40BurnActuator(wrapCap, dbManager);
        urc40Burner.validate();
        urc40Burner.execute(ret);
    }
}
