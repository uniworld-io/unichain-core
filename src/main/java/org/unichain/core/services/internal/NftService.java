package org.unichain.core.services.internal;

import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;

public interface NftService {

    Protocol.Transaction createContract(Contract.Urc721CreateContract contract) throws ContractValidateException;
    Protocol.NftTemplate getContract(Protocol.NftTemplate query);
    Protocol.NftTemplateQueryResult listContract(Protocol.NftTemplateQuery query);

    Protocol.Transaction createToken(Contract.Urc721MintContract contract) throws ContractValidateException;
    Protocol.NftTokenGetResult getToken(Protocol.NftTokenGet query);
    Protocol.Transaction burnToken(Contract.Urc721BurnContract burnNftTokenContract) throws ContractValidateException;
    Protocol.NftTokenQueryResult listToken(Protocol.NftTokenQuery query);

    Protocol.Transaction addMinter(Contract.Urc721AddMinterContract addNftMinterContract) throws ContractValidateException;
    Protocol.Transaction removeMinter(Contract.Urc721RemoveMinterContract contract) throws ContractValidateException;
    Protocol.Transaction renounceMinter(Contract.Urc721RenounceMinterContract contract) throws ContractValidateException;

    Protocol.Transaction approve(Contract.Urc721ApproveContract contract) throws ContractValidateException;
    Protocol.NftTokenApproveResult getListApproval(Protocol.NftTokenApproveQuery query);

    Protocol.Transaction setApprovalForAll(Contract.Urc721SetApprovalForAllContract approvalAll) throws ContractValidateException;
    Protocol.NftTokenApproveAllResult getApprovalForAll(Protocol.NftTokenApproveAllQuery query);
    Protocol.IsApprovedForAll isApprovalForAll(Protocol.IsApprovedForAll query);


    Protocol.Transaction transfer(Contract.Urc721TransferFromContract contract) throws ContractValidateException;
    Protocol.NftBalanceOf balanceOf(Protocol.NftBalanceOf query);
}
