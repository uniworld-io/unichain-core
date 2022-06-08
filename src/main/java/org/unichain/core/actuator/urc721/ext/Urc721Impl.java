package org.unichain.core.actuator.urc721.ext;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.unichain.api.GrpcAPI;
import org.unichain.common.utils.ByteArray;
import org.unichain.common.utils.Utils;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.urc721.Urc721TokenCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Transaction.Contract.ContractType;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.unichain.core.services.http.utils.Util.*;

@Slf4j
@Service
public class Urc721Impl implements Urc721 {
    private static final Set<String> TOKEN_LIST_OWNER_TYPES = new HashSet<>(Arrays.asList("owner","approved", "approved_all"));

    private static final Descriptors.FieldDescriptor ADDR_MSG_ADDR_FIELD = Protocol.AddressMessage.getDescriptor().findFieldByNumber(Protocol.AddressMessage.ADDRESS_FIELD_NUMBER);

    private static final Descriptors.FieldDescriptor URC721_IS_APPROVE_FOR_ALL_FIELD_OWNER= Protocol.Urc721IsApprovedForAllQuery.getDescriptor().findFieldByNumber(Protocol.Urc721IsApprovedForAllQuery.OWNER_ADDRESS_FIELD_NUMBER);
    private static final Descriptors.FieldDescriptor URC721_IS_APPROVE_FOR_ALL_FIELD_OPERATOR = Protocol.Urc721IsApprovedForAllQuery.getDescriptor().findFieldByNumber(Protocol.Urc721IsApprovedForAllQuery.OPERATOR_FIELD_NUMBER);

    private static final Descriptors.FieldDescriptor URC721_TOKEN_LIST_QUERY_FIELD_OWNER = Protocol.Urc721TokenListQuery.getDescriptor().findFieldByNumber(Protocol.Urc721TokenListQuery.OWNER_ADDRESS_FIELD_NUMBER);
    private static final Descriptors.FieldDescriptor URC721_TOKEN_LIST_QUERY_FIELD_TYPE = Protocol.Urc721TokenListQuery.getDescriptor().findFieldByNumber(Protocol.Urc721TokenListQuery.OWNER_TYPE_FIELD_NUMBER);
    private static final Descriptors.FieldDescriptor URC721_TOKEN_LIST_QUERY_FIELD_ADDR = Protocol.Urc721TokenListQuery.getDescriptor().findFieldByNumber(Protocol.Urc721TokenListQuery.ADDRESS_FIELD_NUMBER);
    private static final Descriptors.FieldDescriptor URC721_TOKEN_LIST_QUERY_FIELD_PAGE_SIZE = Protocol.Urc721TokenListQuery.getDescriptor().findFieldByNumber(Protocol.Urc721TokenListQuery.PAGE_SIZE_FIELD_NUMBER);
    private static final Descriptors.FieldDescriptor URC721_TOKEN_LIST_QUERY_FIELD_PAGE_INDEX = Protocol.Urc721TokenListQuery.getDescriptor().findFieldByNumber(Protocol.Urc721TokenListQuery.PAGE_INDEX_FIELD_NUMBER);

    private static final Descriptors.FieldDescriptor URC721_BALANCE_OF_QUERY_FIELD_OWNER = Protocol.Urc721BalanceOfQuery.getDescriptor().findFieldByNumber(Protocol.Urc721BalanceOfQuery.OWNER_ADDRESS_FIELD_NUMBER);
    private static final Descriptors.FieldDescriptor URC721_BALANCE_OF_QUERY_FIELD_CONTRACT = Protocol.Urc721BalanceOfQuery.getDescriptor().findFieldByNumber(Protocol.Urc721BalanceOfQuery.ADDRESS_FIELD_NUMBER);

    public static Descriptors.FieldDescriptor URC721_CONTRACT_QUERY_FIELD_PAGE_SIZE = Protocol.Urc721ContractQuery.getDescriptor().findFieldByNumber(Protocol.Urc721ContractQuery.PAGE_SIZE_FIELD_NUMBER);
    public static Descriptors.FieldDescriptor URC721_CONTRACT_QUERY_FIELD_PAGE_INDEX = Protocol.Urc721ContractQuery.getDescriptor().findFieldByNumber(Protocol.Urc721ContractQuery.PAGE_INDEX_FIELD_NUMBER);
    public static Descriptors.FieldDescriptor URC721_CONTRACT_QUERY_FIELD_OWNER_ADDR = Protocol.Urc721ContractQuery.getDescriptor().findFieldByNumber(Protocol.Urc721ContractQuery.OWNER_ADDRESS_FIELD_NUMBER);

    public static Descriptors.FieldDescriptor URC721_TOKEN_QUERY_FIELD_ADDR = Protocol.Urc721TokenQuery.getDescriptor().findFieldByNumber(Protocol.Urc721TokenQuery.ADDRESS_FIELD_NUMBER);


    @Autowired
    private Manager dbManager;

    @Autowired
    private Wallet wallet;

    @Override
    public Protocol.Transaction createContract(Contract.Urc721CreateContract contract) throws ContractValidateException {
        return wallet.createTransactionCapsule(contract, ContractType.Urc721CreateContract).getInstance();
    }

    @Override
    public Protocol.Urc721Contract getContract(Protocol.AddressMessage query) {
        Assert.isTrue(query.hasField(ADDR_MSG_ADDR_FIELD), "Contract address missing");
        return dbManager.getUrc721ContractStore().get(query.getAddress().toByteArray()).getInstance();
    }

    @Override
    public Protocol.Transaction mint(Contract.Urc721MintContract contract) throws ContractValidateException {
        return wallet.createTransactionCapsule(contract, ContractType.Urc721MintContract).getInstance();
    }

    @Override
    public Protocol.Urc721Token getToken(Protocol.Urc721TokenQuery query) {
        Assert.isTrue(query.hasField(URC721_TOKEN_QUERY_FIELD_ADDR), "Contract address missing");
        var id = Urc721TokenCapsule.genTokenKey(query.getAddress().toByteArray(), query.getId());

        if(!dbManager.getUrc721TokenStore().has(id))
        {
            return Protocol.Urc721Token.newBuilder().build();
        }
        else {
            var token = dbManager.getUrc721TokenStore().get(id).getInstance();
            return  Protocol.Urc721Token.newBuilder()
                    .setId(token.getId())
                    .setAddress(token.getAddress())
                    .setSymbol(token.getSymbol())
                    .setUri(token.getUri())
                    .setApproval(token.getApproval())
                    .setLastOperation(token.getLastOperation())
                    .setOwnerAddress(token.getOwnerAddress())
                    .build();
        }
    }

    @Override
    public Protocol.Transaction addMinter(Contract.Urc721AddMinterContract contract) throws ContractValidateException {
        return wallet.createTransactionCapsule(contract, ContractType.Urc721AddMinterContract).getInstance();
    }

    @Override
    public Protocol.Transaction removeMinter(Contract.Urc721RemoveMinterContract contract) throws ContractValidateException {
        return wallet.createTransactionCapsule(contract, ContractType.Urc721RemoveMinterContract).getInstance();
    }

    @Override
    public Protocol.Transaction renounceMinter(Contract.Urc721RenounceMinterContract contract) throws ContractValidateException {
        return wallet.createTransactionCapsule(contract, ContractType.Urc721RenounceMinterContract).getInstance();
    }

    @Override
    public Protocol.Transaction approve(Contract.Urc721ApproveContract contract) throws ContractValidateException {
        return wallet.createTransactionCapsule(contract, ContractType.Urc721ApproveContract).getInstance();
    }

    @Override
    public Protocol.Transaction setApprovalForAll(Contract.Urc721SetApprovalForAllContract approvalAll) throws ContractValidateException {
        return wallet.createTransactionCapsule(approvalAll, ContractType.Urc721SetApprovalForAllContract).getInstance();
    }

    @Override
    public Protocol.BoolMessage isApprovalForAll(Protocol.Urc721IsApprovedForAllQuery query) {
        Assert.isTrue(query.hasField(URC721_IS_APPROVE_FOR_ALL_FIELD_OWNER), "Owner address null");
        Assert.isTrue(query.hasField(URC721_IS_APPROVE_FOR_ALL_FIELD_OPERATOR), "Operator null");
        var relationStore = dbManager.getUrc721AccountTokenRelationStore();
        var isApproved = false;
        if(relationStore.has(query.getOwnerAddress().toByteArray())){
            var relation = relationStore.get(query.getOwnerAddress().toByteArray());
            if(relation.hasApprovalForAll() && Arrays.equals(relation.getApprovedForAll(), query.getOperator().toByteArray()))
                isApproved = true;
        }

        return Protocol.BoolMessage.newBuilder()
                .setValue(isApproved)
                .build();
    }

    @Override
    public Protocol.AddressMessage getApproved(Protocol.Urc721TokenQuery query) {
        try {
            var tokenStore = dbManager.getUrc721TokenStore();
            var contractAddr = query.getAddress().toByteArray();
            var tokenKey = ArrayUtils.addAll(contractAddr, ByteArray.fromLong(query.getId()));
            Assert.isTrue(tokenStore.has(tokenKey), "Token not found!");

            var token = tokenStore.get(tokenKey);
            var addrMsg = Protocol.AddressMessage.newBuilder();
            if (token.hasApproval())
                addrMsg.setAddress(ByteString.copyFrom(token.getApproval()));
            else
                addrMsg.clearAddress();
            return addrMsg.build();
        }
        catch (Exception e){
            logger.error("getApproved got error -->", e);
            return Protocol.AddressMessage.newBuilder().build();
        }
    }

    @Override
    public Protocol.Transaction burnToken(Contract.Urc721BurnContract contract) throws ContractValidateException {
        return wallet.createTransactionCapsule(contract, ContractType.Urc721BurnContract).getInstance();
    }

    @Override
    public Protocol.Urc721ContractPage listContract(Protocol.Urc721ContractQuery query) {
        Assert.isTrue(query.hasField(URC721_CONTRACT_QUERY_FIELD_OWNER_ADDR), "Owner address null");

        int pageSize = query.hasField(URC721_CONTRACT_QUERY_FIELD_PAGE_SIZE) ? query.getPageSize() : DEFAULT_PAGE_SIZE;
        int pageIndex = query.hasField(URC721_CONTRACT_QUERY_FIELD_PAGE_INDEX) ? query.getPageIndex() : DEFAULT_PAGE_INDEX;
        Assert.isTrue((pageSize > 0) && (pageIndex >= 0) && (pageSize <= MAX_PAGE_SIZE), "Invalid paging info");

        var ownerAddr = query.getOwnerAddress().toByteArray();
        List<Protocol.Urc721Contract> unsorted = new ArrayList<>();
        var relationStore = dbManager.getUrc721AccountContractRelationStore();
        var templateStore = dbManager.getUrc721ContractStore();

        if ("owner".equalsIgnoreCase(query.getOwnerType()) && relationStore.has(ownerAddr) && relationStore.get(ownerAddr).getTotal() > 0) {
            var start = templateStore.get(relationStore.get(ownerAddr).getHead().toByteArray());
            while (true) {
                unsorted.add(start.getInstance());
                if (start.hasNext()) {
                    start = templateStore.get(start.getNext());
                } else {
                    break;
                }
            }
        }
        var minterRelationStore = dbManager.getUrc721MinterContractRelationStore();
        if("minter".equalsIgnoreCase(query.getOwnerType()) && minterRelationStore.has(ownerAddr) && minterRelationStore.get(ownerAddr).getTotal() > 0){
            var relation = minterRelationStore.get(ownerAddr);
            var start = templateStore.get(relation.getHead().toByteArray());
            while (true){
                unsorted.add(start.getInstance());
                if(start.hasNextOfMinter()){
                    start = templateStore.get(start.getNextOfMinter());
                }else {
                    break;
                }
            }
        }

        unsorted = unsorted.stream()
                .map(item -> item.toBuilder()
                        .clearNext()
                        .clearPrev()
                        .build())
                .collect(Collectors.toList());

        return  Protocol.Urc721ContractPage.newBuilder()
                .setPageIndex(pageIndex)
                .setPageSize(pageSize)
                .setTotal(unsorted.size())
                .addAllContracts(Utils.paging(unsorted, pageIndex, pageSize))
                .build();
    }

    @Override
    public Protocol.Urc721TokenPage listToken(Protocol.Urc721TokenListQuery query) {
        Assert.isTrue(query.hasField(URC721_TOKEN_LIST_QUERY_FIELD_OWNER), "Owner address null");
        Assert.isTrue(query.hasField(URC721_TOKEN_LIST_QUERY_FIELD_TYPE) && TOKEN_LIST_OWNER_TYPES.contains(query.getOwnerType().toLowerCase()), "Owner type null or invalid type");

        int pageSize = query.hasField(URC721_TOKEN_LIST_QUERY_FIELD_PAGE_SIZE) ? query.getPageSize() : DEFAULT_PAGE_SIZE;
        int pageIndex = query.hasField(URC721_TOKEN_LIST_QUERY_FIELD_PAGE_INDEX) ? query.getPageIndex() : DEFAULT_PAGE_INDEX;
        Assert.isTrue(pageSize > 0 && pageIndex >= 0 && pageSize <= MAX_PAGE_SIZE, "Invalid paging info");

        var ownerAddr = query.getOwnerAddress().toByteArray();
        var ownerType = query.getOwnerType();

        var contractAddr = query.getAddress();
        var hasContractAddr = query.hasField(URC721_TOKEN_LIST_QUERY_FIELD_ADDR);

        List<Protocol.Urc721Token> unsorted = new ArrayList<>();
        Predicate<Urc721TokenCapsule> filter = cap -> !hasContractAddr || Arrays.equals(cap.getAddr(), contractAddr.toByteArray());

        switch (ownerType){
            case "owner":
                unsorted = listTokenByOwner(ownerAddr, filter);
                break;
            case "approved":
                unsorted = listTokenByApproved(ownerAddr, filter);
                break;
            case "approved_all":
                unsorted = listTokenByApprovedForAll(ownerAddr, filter);
                break;
            default:
                break;
        }

        return  Protocol.Urc721TokenPage.newBuilder()
                .setPageSize(pageSize)
                .setPageIndex(pageIndex)
                .setTotal(unsorted.size())
                .addAllTokens(Utils.paging(unsorted, pageIndex, pageSize))
                .build();
    }

    private List<Protocol.Urc721Token> listTokenByOwner(byte[] ownerAddr, Predicate<Urc721TokenCapsule> filter){
        var unsorted = new ArrayList<Protocol.Urc721Token>();
        var relationStore = dbManager.getUrc721AccountTokenRelationStore();
        var tokenStore = dbManager.getUrc721TokenStore();

        if(relationStore.has(ownerAddr) && relationStore.get(ownerAddr).getTotal() > 0){
            var start = tokenStore.get(relationStore.get(ownerAddr).getHead().toByteArray());
            while (true){
                if(filter.test(start))
                    unsorted.add(start.getInstance());
                if(start.hasNext()) {
                    start = tokenStore.get(start.getNext());
                } else {
                    break;
                }
            }
        }
        return unsorted;
    }

    private List<Protocol.Urc721Token> listTokenByApproved(byte[] ownerAddr, Predicate<Urc721TokenCapsule> filter){
        var tokenStore = dbManager.getUrc721TokenStore();
        var approvedStore = dbManager.getUrc721TokenApproveRelationStore();
        var accTokenRelationStore = dbManager.getUrc721AccountTokenRelationStore();
        var result = new ArrayList<Protocol.Urc721Token>();

        if(accTokenRelationStore.has(ownerAddr) && accTokenRelationStore.get(ownerAddr).getTotalApprove() > 0){
            var accTokenRelation = accTokenRelationStore.get(ownerAddr);
            var approveRelation = approvedStore.get(accTokenRelation.getHeadApprove());
            while (true){
                if(tokenStore.has(approveRelation.getKey()))
                {
                    var tmpToken = tokenStore.get(approveRelation.getKey());
                    if(filter.test(tmpToken))
                        result.add(tmpToken.getInstance());
                }

                if(approveRelation.hasNext()) {
                    approveRelation = approvedStore.get(approveRelation.getNext());
                } else {
                    break;
                }
            }
        }
        return result;
    }

    private List<Protocol.Urc721Token> listTokenByApprovedForAll(byte[] ownerAddr, Predicate<Urc721TokenCapsule> filter){
        var result = new ArrayList<Protocol.Urc721Token>();
        var relationStore = dbManager.getUrc721AccountTokenRelationStore();
        relationStore.get(ownerAddr).getApproveAllMap().forEach((owner, isApprovedAll) -> {
            var tokenOwner = ByteString.copyFrom(ByteArray.fromHexString(owner)).toByteArray();
            if (isApprovedAll && relationStore.has(tokenOwner)) {
                var ownerRelationCap = relationStore.get(tokenOwner);
                var tokenRelation = Protocol.Urc721AccountTokenRelation.newBuilder()
                        .setOwnerAddress(ownerRelationCap.getInstance().getOwnerAddress())
                        .setTotal(ownerRelationCap.getInstance().getTotal())
                        .build();
                result.addAll(listTokenByOwner(tokenRelation.getOwnerAddress().toByteArray(), filter));
            }
        });

        return result;
    }

    @Override
    public Protocol.Transaction transfer(Contract.Urc721TransferFromContract contract) throws ContractValidateException {
        return wallet.createTransactionCapsule(contract, ContractType.Urc721TransferFromContract).getInstance();
    }

    @Override
    public GrpcAPI.NumberMessage balanceOf(Protocol.Urc721BalanceOfQuery query) {
        Assert.isTrue(query.hasField(URC721_BALANCE_OF_QUERY_FIELD_OWNER) && query.hasField(URC721_BALANCE_OF_QUERY_FIELD_CONTRACT), "Owner address | urc721 address is null");
        Predicate<Urc721TokenCapsule> filter = cap -> Arrays.equals(cap.getAddr(), query.getAddress().toByteArray());
        var tokens = listTokenByOwner(query.getOwnerAddress().toByteArray(), filter);
        return GrpcAPI.NumberMessage.newBuilder()
                .setNum(tokens.size())
                .build();
    }

    @Override
    public GrpcAPI.StringMessage name(Protocol.AddressMessage msg) {
        return GrpcAPI.StringMessage.newBuilder()
                .setValue(getContract(msg).getName())
                .build();
    }

    @Override
    public GrpcAPI.StringMessage symbol(Protocol.AddressMessage msg) {
        return GrpcAPI.StringMessage.newBuilder()
                .setValue(getContract(msg).getSymbol())
                .build();
    }

    @Override
    public GrpcAPI.NumberMessage totalSupply(Protocol.AddressMessage msg) {
        return GrpcAPI.NumberMessage.newBuilder()
                .setNum(getContract(msg).getTotalSupply())
                .build();
    }

    @Override
    public GrpcAPI.StringMessage tokenUri(Protocol.Urc721TokenQuery msg) {
        return GrpcAPI.StringMessage.newBuilder()
                .setValue(getToken(msg).getUri())
                .build();
    }

    @Override
    public Protocol.AddressMessage ownerOf(Protocol.Urc721TokenQuery msg) {
        return Protocol.AddressMessage.newBuilder()
                .setAddress(getToken(msg).getOwnerAddress())
                .build();
    }
}
