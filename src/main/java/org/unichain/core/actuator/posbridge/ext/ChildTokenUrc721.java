package org.unichain.core.actuator.posbridge.ext;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.unichain.common.utils.PosBridgeUtil;
import org.unichain.core.actuator.urc721.Urc721BurnActuator;
import org.unichain.core.actuator.urc721.Urc721MintActuator;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;
@Slf4j
public class ChildTokenUrc721 implements ChildToken {

    private final Manager dbManager;
    private final TransactionResultCapsule ret;

    public ChildTokenUrc721(Manager dbManager, TransactionResultCapsule ret) {
        this.dbManager = dbManager;
        this.ret = ret;
    }

    @Override
    public void deposit(ByteString user, ByteString childToken, String depositData) throws ContractExeException, ContractValidateException {
        var contractStore = dbManager.getUrc721ContractStore();
        var contractKey =   childToken.toByteArray();
        var contractCap = contractStore.get(contractKey);

        PosBridgeUtil.ERC721Decode erc721Decode = PosBridgeUtil.abiDecodeToErc721(depositData);
        logger.info("========>Deposit 721 decode data  {}", erc721Decode);

        var wrapCtx = Contract.Urc721MintContract.newBuilder()
                .setOwnerAddress(ByteString.copyFrom(contractCap.getOwner()))
                .setAddress(childToken)
                .setToAddress(user)
                .setTokenId(erc721Decode.tokenId)
                .setUri(erc721Decode.uri)
                .build();
        var txCap = new TransactionCapsule(wrapCtx, Protocol.Transaction.Contract.ContractType.Urc721MintContract)
                .getInstance()
                .getRawData()
                .getContract(0)
                .getParameter();
        var wrapActuator = new Urc721MintActuator(txCap, dbManager);
        var wrapRet = new TransactionResultCapsule();
        wrapActuator.validate();
        wrapActuator.execute(wrapRet);
        ret.setFee(wrapRet.getFee());
    }

    @Override
    public void withdraw(ByteString user, ByteString childToken, String withdrawData) throws ContractExeException, ContractValidateException {
        var wrapCtx = Contract.Urc721BurnContract.newBuilder()
                .setOwnerAddress(user)
                .setAddress(childToken)
                .setTokenId(PosBridgeUtil.abiDecodeToUint256(withdrawData).getValue().longValue())
                .build();
        var wrapCap = new TransactionCapsule(wrapCtx, Protocol.Transaction.Contract.ContractType.Urc721BurnContract)
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
