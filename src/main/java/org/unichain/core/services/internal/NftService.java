package org.unichain.core.services.internal;

import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;

public interface NftService {

    Protocol.Transaction createContract(Contract.CreateNftTemplateContract contract) throws ContractValidateException;
    Protocol.NftTemplate getContract(Protocol.NftTemplate query);
    Protocol.NftTemplateQueryResult listContract(Protocol.NftTemplateQuery query);

    Protocol.Transaction createToken(Contract.MintNftTokenContract contract) throws ContractValidateException;
    Protocol.NftTokenGetResult getToken(Protocol.NftTokenGet query);

    Protocol.Transaction addMinter(Contract.AddNftMinterContract addNftMinterContract) throws ContractValidateException;
    Protocol.Transaction removeMinter(Contract.RemoveNftMinterContract contract) throws ContractValidateException;
    Protocol.Transaction renounceMinter(Contract.RenounceNftMinterContract contract) throws ContractValidateException;

    Protocol.Transaction approve(Contract.ApproveNftTokenContract contract) throws ContractValidateException;
    Protocol.NftTokenApproveResult getApproval(Protocol.NftTokenApproveQuery query);

    Protocol.Transaction setApprovalForAll(Contract.ApproveForAllNftTokenContract approvalAll) throws ContractValidateException;
    Protocol.NftTokenApproveAllResult getApprovalForAll(Protocol.NftTokenApproveAllQuery query);
    Protocol.IsApprovedForAll isApprovalForAll(Protocol.IsApprovedForAll query);

    Protocol.Transaction burnToken(Contract.BurnNftTokenContract burnNftTokenContract) throws ContractValidateException;
    Protocol.NftTokenQueryResult listToken(Protocol.NftTokenQuery query);

    Protocol.Transaction transfer(Contract.TransferNftTokenContract contract) throws ContractValidateException;
    Protocol.NftBalanceOf balanceOf(Protocol.NftBalanceOf query);

}
