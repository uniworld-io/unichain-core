package org.unichain.core.actuator.urc721.ext;

import org.unichain.api.GrpcAPI;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;

public interface Urc721 {
    //contracts
    Protocol.Transaction createContract(Contract.Urc721CreateContract contract) throws ContractValidateException;
    Protocol.Transaction mint(Contract.Urc721MintContract contract) throws ContractValidateException;
    Protocol.Transaction burn(Contract.Urc721BurnContract contract) throws ContractValidateException;
    Protocol.Transaction addMinter(Contract.Urc721AddMinterContract contract) throws ContractValidateException;
    Protocol.Transaction removeMinter(Contract.Urc721RemoveMinterContract contract) throws ContractValidateException;
    Protocol.Transaction renounceMinter(Contract.Urc721RenounceMinterContract contract) throws ContractValidateException;
    Protocol.Transaction approve(Contract.Urc721ApproveContract contract) throws ContractValidateException;
    Protocol.Transaction setApprovalForAll(Contract.Urc721SetApprovalForAllContract approvalAll) throws ContractValidateException;
    Protocol.Transaction transferFrom(Contract.Urc721TransferFromContract contract) throws ContractValidateException;

    //listing
    GrpcAPI.NumberMessage balanceOf(Protocol.Urc721BalanceOfQuery query);
    GrpcAPI.StringMessage name(Protocol.AddressMessage msg);
    GrpcAPI.StringMessage symbol(Protocol.AddressMessage msg);
    GrpcAPI.NumberMessage totalSupply(Protocol.AddressMessage msg);
    GrpcAPI.StringMessage tokenUri(Protocol.Urc721TokenQuery msg);
    Protocol.AddressMessage ownerOf(Protocol.Urc721TokenQuery msg);
    Protocol.AddressMessage getApproved(Protocol.Urc721TokenQuery query);
    Protocol.AddressMessage getApprovedForAll(Protocol.Urc721ApprovedForAllQuery query);
    Protocol.BoolMessage isApprovedForAll(Protocol.Urc721IsApprovedForAllQuery query);
    Protocol.Urc721ContractPage listContract(Protocol.Urc721ContractQuery query);
    Protocol.Urc721Contract getContract(Protocol.AddressMessage query);
    Protocol.Urc721TokenPage listToken(Protocol.Urc721TokenListQuery query);
    Protocol.Urc721Token getToken(Protocol.Urc721TokenQuery query);
}
