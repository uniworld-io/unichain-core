package org.unichain.core.actuator.posbridge.ext;

import com.google.protobuf.ByteString;
import lombok.var;
import org.unichain.common.utils.PosBridgeUtil;
import org.unichain.core.actuator.urc20.Urc20BurnActuator;
import org.unichain.core.actuator.urc20.Urc20MintActuator;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;

public class ChildTokenUrc20 implements ChildToken {

    private final Manager dbManager;
    private final TransactionResultCapsule ret;

    public ChildTokenUrc20(Manager dbManager, TransactionResultCapsule ret) {
        this.dbManager = dbManager;
        this.ret = ret;
    }

    @Override
    public void deposit(ByteString user, ByteString childToken, String depositData) throws ContractExeException, ContractValidateException {
        //load token and transfer from token owner to ...
        var symbol = dbManager.getTokenAddrSymbolIndexStore().get(childToken.toByteArray()).getSymbol();
        var tokenInfo = dbManager.getTokenPoolStore().get(Util.stringAsBytesUppercase(symbol));

        var wrapCtx = Contract.Urc20MintContract.newBuilder()
                .setOwnerAddress(user)
                .setAddress(tokenInfo.getAddress())
                .setAmount(PosBridgeUtil.abiDecodeToUint256(depositData).getValue().longValue())
                .build();
        var wrapCap = new TransactionCapsule(wrapCtx, Protocol.Transaction.Contract.ContractType.Urc20MintContract)
                .getInstance()
                .getRawData()
                .getContract(0)
                .getParameter();
        var urc20Mint = new Urc20MintActuator(wrapCap, dbManager);
        urc20Mint.validate();
        urc20Mint.execute(ret);
    }

    @Override
    public void withdraw(ByteString user, ByteString childToken, String withdrawData) throws ContractExeException, ContractValidateException {
        var symbol = dbManager.getTokenAddrSymbolIndexStore().get(childToken.toByteArray()).getSymbol();
        var tokenInfo = dbManager.getTokenPoolStore().get(Util.stringAsBytesUppercase(symbol));

        var wrapCtx = Contract.Urc20BurnContract.newBuilder()
                .setAmount(PosBridgeUtil.abiDecodeToUint256(withdrawData).getValue().longValue())
                .setOwnerAddress(user)
                .setAddress(tokenInfo.getAddress())
                .build();
        var wrapCap = new TransactionCapsule(wrapCtx, Protocol.Transaction.Contract.ContractType.Urc20BurnContract)
                .getInstance()
                .getRawData()
                .getContract(0)
                .getParameter();

        var urc20Burner = new Urc20BurnActuator(wrapCap, dbManager);
        urc20Burner.validate();
        urc20Burner.execute(ret);
    }
}
