package org.unichain.core.services.internal.impl;

import com.google.protobuf.ByteString;
import lombok.var;
import org.unichain.common.utils.PosBridgeUtil;
import org.unichain.core.actuator.TokenTransferActuatorV4;
import org.unichain.core.actuator.TransferActuator;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.core.services.internal.ChildTokenService;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;

public class ChildTokenErc20Service implements ChildTokenService {

    private final Manager dbManager;
    private final TransactionResultCapsule ret;

    public ChildTokenErc20Service(Manager dbManager, TransactionResultCapsule ret) {
        this.dbManager = dbManager;
        this.ret = ret;
    }

    @Override
    public void deposit(ByteString user,ByteString childToken, String depositData) throws ContractExeException, ContractValidateException {
        //load token and transfer from token owner to ...
        var symbol = dbManager.getTokenAddrSymbolIndexStore().get(childToken.toByteArray()).getSymbol();
        var tokenOwner = dbManager.getTokenPoolStore().get(Util.stringAsBytesUppercase(symbol));

        var wrapCtx = Contract.TransferTokenContract.newBuilder()
                .setOwnerAddress(tokenOwner.getOwnerAddress())
                .setToAddress(user)
                .setTokenName(symbol)
                .setAmount(PosBridgeUtil.abiDecodeToUint256(depositData).getValue().longValue())
                .build();
        buildThenExecContract(wrapCtx);
    }

    @Override
    public void withdraw(ByteString user, ByteString childToken, String withdrawData) throws ContractExeException, ContractValidateException {
        var symbol = dbManager.getTokenAddrSymbolIndexStore().get(childToken.toByteArray()).getSymbol();
        var tokenInfo = dbManager.getTokenPoolStore().get(symbol.toUpperCase().getBytes());
        var wrapCtx = Contract.TransferTokenContract.newBuilder()
                .setAmount(PosBridgeUtil.abiDecodeToUint256(withdrawData).getValue().longValue())
                .setOwnerAddress(user)
                .setToAddress(tokenInfo.getOwnerAddress())
                .setTokenName(symbol)
                .setAvailableTime(0L)
                .build();
        buildThenExecContract(wrapCtx);
    }


    private void buildThenExecContract(Contract.TransferTokenContract wrapCtx) throws ContractExeException, ContractValidateException {
        var wrapCap = new TransactionCapsule(wrapCtx, Protocol.Transaction.Contract.ContractType.TransferTokenContract)
                .getInstance()
                .getRawData()
                .getContract(0)
                .getParameter();
        var wrapActuator = new TokenTransferActuatorV4(wrapCap, dbManager);
        var wrapRet = new TransactionResultCapsule();
        wrapActuator.validate();
        wrapActuator.execute(wrapRet);
        ret.setFee(wrapRet.getFee());
    }
}
