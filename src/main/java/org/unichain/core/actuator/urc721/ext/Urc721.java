package org.unichain.core.actuator.urc721.ext;

import org.unichain.api.GrpcAPI;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;

public interface Urc721 {
    Protocol.Transaction createContract(Contract.Urc721CreateContract contract) throws ContractValidateException;
    Protocol.Transaction mint(Contract.Urc721MintContract contract) throws ContractValidateException;
    Protocol.Urc721Token getToken(Protocol.Urc721TokenQuery query);
    Protocol.Transaction burnToken(Contract.Urc721BurnContract contract) throws ContractValidateException;

    Protocol.Transaction addMinter(Contract.Urc721AddMinterContract contract) throws ContractValidateException;
    Protocol.Transaction removeMinter(Contract.Urc721RemoveMinterContract contract) throws ContractValidateException;
    Protocol.Transaction renounceMinter(Contract.Urc721RenounceMinterContract contract) throws ContractValidateException;
    Protocol.Transaction approve(Contract.Urc721ApproveContract contract) throws ContractValidateException;
    Protocol.Transaction setApprovalForAll(Contract.Urc721SetApprovalForAllContract approvalAll) throws ContractValidateException;
    Protocol.Transaction transfer(Contract.Urc721TransferFromContract contract) throws ContractValidateException;

    GrpcAPI.NumberMessage balanceOf(Protocol.Urc721BalanceOfQuery query);
    GrpcAPI.StringMessage getName(Protocol.AddressMessage msg);
    GrpcAPI.StringMessage getSymbol(Protocol.AddressMessage msg);
    GrpcAPI.NumberMessage getTotalSupply(Protocol.AddressMessage msg);
    GrpcAPI.StringMessage getTokenUri(Protocol.Urc721TokenQuery msg);
    Protocol.AddressMessage getOwnerOf(Protocol.Urc721TokenQuery msg);
    Protocol.AddressMessage getApproved(Protocol.Urc721TokenQuery query);
    Protocol.BoolMessage isApprovalForAll(Protocol.Urc721IsApprovedForAllQuery query);
    Protocol.Urc721Contract getContract(Protocol.AddressMessage query);
    Protocol.Urc721ContractPage listContract(Protocol.Urc721ContractQuery query);
    Protocol.Urc721TokenPage listToken(Protocol.Urc721TokenListQuery query);
}
