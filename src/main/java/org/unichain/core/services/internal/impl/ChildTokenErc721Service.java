package org.unichain.core.services.internal.impl;

import com.google.protobuf.ByteString;
import lombok.var;
import org.unichain.common.utils.PosBridgeUtil;
import org.unichain.core.actuator.urc721.Urc721BurnActuator;
import org.unichain.core.actuator.urc721.Urc721MintActuator;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
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
        var nftContractStore = dbManager.getNftTemplateStore();
        var contractKey =   childToken.toByteArray();
        var nft = nftContractStore.get(contractKey);

        PosBridgeUtil.ERC721Decode erc721Decode = PosBridgeUtil.abiDecodeToErc721(depositData);

        var wrapCtx = Contract.Urc721MintContract.newBuilder()
                .setOwnerAddress(ByteString.copyFrom(nft.getOwner()))
                .setAddress(childToken)
                .setToAddress(user)
                .setTokenId(erc721Decode.tokenId)
                .setUri(erc721Decode.uri)
                .build();
        var contract = new TransactionCapsule(wrapCtx, Protocol.Transaction.Contract.ContractType.Urc721MintContract)
                .getInstance()
                .getRawData()
                .getContract(0)
                .getParameter();
        var wrapActuator = new Urc721MintActuator(contract, dbManager);
        var wrapRet = new TransactionResultCapsule();
        wrapActuator.validate();
        wrapActuator.execute(wrapRet);
        ret.setFee(wrapRet.getFee());
    }

    @Override
    public void withdraw(ByteString user, ByteString childToken, String withdrawData) throws ContractExeException, ContractValidateException {
        var wrapCtx = Contract.BurnNftTokenContract.newBuilder()
                .setOwnerAddress(user)
                .setAddress(childToken)
                .setTokenId(PosBridgeUtil.abiDecodeToUint256(withdrawData).getValue().longValue())
                .build();
        var wrapCap = new TransactionCapsule(wrapCtx, Protocol.Transaction.Contract.ContractType.BurnNftTokenContract)
                .getInstance()
                .getRawData()
                .getContract(0)
                .getParameter();
        var wrapActuator = new Urc721BurnActuator(wrapCap, dbManager);
        var wrapRet = new TransactionResultCapsule();

        wrapActuator.validate();
        wrapActuator.execute(wrapRet);
        ret.setFee(wrapRet.getFee());
    }

}
