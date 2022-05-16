package org.unichain.core.services.internal.impl;

import com.google.protobuf.ByteString;
import lombok.var;
import org.unichain.common.utils.PosBridgeUtil;
import org.unichain.core.actuator.NftBurnTokenActuator;
import org.unichain.core.actuator.NftMintTokenActuator;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.core.services.internal.ChildTokenService;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;

public class ChildTokenErc721Service implements ChildTokenService {

    private final Manager dbManager;
    private final TransactionResultCapsule ret;

    public ChildTokenErc721Service(Manager dbManager, TransactionResultCapsule ret) {
        this.dbManager = dbManager;
        this.ret = ret;
    }

    @Override
    public void deposit(ByteString user, ByteString childToken, String depositData) throws ContractExeException, ContractValidateException {
        var symbol = dbManager.getNftAddrSymbolIndexStore().get(childToken.toByteArray()).getSymbol();
        var nftContractStore = dbManager.getNftTemplateStore();
        var contractKey =  Util.stringAsBytesUppercase(symbol);
        var nft = nftContractStore.get(contractKey);

        var wrapCtx = Contract.MintNftTokenContract.newBuilder()
                .setOwnerAddress(ByteString.copyFrom(nft.getOwner()))
                .setContract(symbol)
                .setToAddress(user)
                .setTokenId(PosBridgeUtil.abiDecodeToUint256(depositData).getValue().longValue())
//                .setUri(PosBridgeUtil.abiDecodeFromToString(decodedMsg.extHex))//@TODO
                .build();
        var contract = new TransactionCapsule(wrapCtx, Protocol.Transaction.Contract.ContractType.MintNftTokenContract)
                .getInstance()
                .getRawData()
                .getContract(0)
                .getParameter();
        var wrapActuator = new NftMintTokenActuator(contract, dbManager);
        var wrapRet = new TransactionResultCapsule();
        wrapActuator.validate();
        wrapActuator.execute(wrapRet);
        ret.setFee(wrapRet.getFee());
    }

    @Override
    public void withdraw(ByteString user, ByteString childToken, String withdrawData) throws ContractExeException, ContractValidateException {
        var symbol = dbManager.getNftAddrSymbolIndexStore().get(childToken.toByteArray()).getSymbol();
        var wrapCtx = Contract.BurnNftTokenContract.newBuilder()
                .setOwnerAddress(user)
                .setContract(symbol)
                .setTokenId(PosBridgeUtil.abiDecodeToUint256(withdrawData).getValue().longValue())
                .build();
        var wrapCap = new TransactionCapsule(wrapCtx, Protocol.Transaction.Contract.ContractType.BurnNftTokenContract)
                .getInstance()
                .getRawData()
                .getContract(0)
                .getParameter();
        var wrapActuator = new NftBurnTokenActuator(wrapCap, dbManager);
        var wrapRet = new TransactionResultCapsule();

        wrapActuator.validate();
        wrapActuator.execute(wrapRet);
        ret.setFee(wrapRet.getFee());
    }
}
