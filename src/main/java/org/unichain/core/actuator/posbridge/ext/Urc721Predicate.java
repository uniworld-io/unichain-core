package org.unichain.core.actuator.posbridge.ext;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.unichain.common.utils.PosBridgeUtil;
import org.unichain.core.actuator.urc721.Urc721TransferFromActuator;
import org.unichain.core.capsule.PosBridgeConfigCapsule;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;

@Slf4j
public class Urc721Predicate implements Predicate {
    private final Manager dbManager;
    private final TransactionResultCapsule ret;
    private final PosBridgeConfigCapsule config;

    public Urc721Predicate(Manager dbManager,
                           TransactionResultCapsule ret,
                           PosBridgeConfigCapsule config) {
        this.dbManager = dbManager;
        this.ret = ret;
        this.config = config;
    }

    @Override
    public void lockTokens(ByteString depositor, ByteString rootToken, String depositData) throws ContractExeException, ContractValidateException {

        PosBridgeUtil.ERC721Decode erc721Decode = PosBridgeUtil.abiDecodeToErc721(depositData);
        var wrapCtx = Contract.Urc721TransferFromContract.newBuilder()
                .setOwnerAddress(depositor)
                .setTo(config.getPredicateErc721())
                .setAddress(rootToken)
                .setTokenId(erc721Decode.tokenId)
                .build();
        buildThenExecContract(wrapCtx);
    }


    @Override
    public void unlockTokens(ByteString withdrawer, ByteString rootToken, String withdrawData) throws ContractExeException, ContractValidateException {
        var wrapCtx = Contract.Urc721TransferFromContract.newBuilder()
                .setOwnerAddress(config.getPredicateErc721())
                .setTo(withdrawer)
                .setAddress(rootToken)
                .setTokenId(PosBridgeUtil.abiDecodeToUint256(withdrawData).getValue().longValue())
                .build();
        buildThenExecContract(wrapCtx);
    }

    private void buildThenExecContract(Contract.Urc721TransferFromContract wrapCtx) throws ContractExeException, ContractValidateException {
        var contract = new TransactionCapsule(wrapCtx, Protocol.Transaction.Contract.ContractType.Urc721TransferFromContract)
                .getInstance()
                .getRawData()
                .getContract(0)
                .getParameter();
        var wrapActuator = new Urc721TransferFromActuator(contract, dbManager);
        var wrapRet = new TransactionResultCapsule();
        wrapActuator.validate();
        wrapActuator.execute(wrapRet);
        ret.setFee(wrapRet.getFee());
    }

}
