package org.unichain.core.services.internal.impl;

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
import org.unichain.core.services.internal.Urc721Service;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Transaction.Contract.ContractType;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.unichain.core.services.http.utils.Util.*;


@Slf4j
@Service
public class Urc721ServiceImpl implements Urc721Service {
    @Autowired
    private Manager dbManager;

    @Autowired
    private Wallet wallet;

    @Override
    public Protocol.Transaction createContract(Contract.Urc721CreateContract contract) throws ContractValidateException {
        return wallet.createTransactionCapsule(contract, ContractType.Urc721CreateContract).getInstance();
    }

    private static Descriptors.FieldDescriptor CONTRACT_ADDR = Protocol.Urc721Contract.getDescriptor().findFieldByNumber(Protocol.Urc721Contract.ADDRESS_FIELD_NUMBER);

    @Override
    public Protocol.Urc721Contract getContract(Protocol.Urc721Contract query) {
        Assert.isTrue(query.hasField(CONTRACT_ADDR), "Contract address missing");
        return dbManager.getUrc721ContractStore().get(query.getAddress().toByteArray()).getInstance();
    }

    @Override
    public Protocol.Transaction createToken(Contract.Urc721MintContract contract) throws ContractValidateException {
        return wallet.createTransactionCapsule(contract, ContractType.Urc721MintContract).getInstance();
    }

    @Override
    public Protocol.Urc721Token getToken(Protocol.Urc721Token query) {
        Assert.notNull(query.getAddress(), "Token address empty");
        var id = Urc721TokenCapsule.genTokenKey(query.getAddress().toByteArray(), query.getId());

        if(!dbManager.getUrc721TokenStore().has(id))
        {
            return Protocol.Urc721Token.newBuilder()
                    .build();
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
    public Protocol.Urc721IsApprovedForAll isApprovalForAll(Protocol.Urc721IsApprovedForAll query) {
        //@todo urc721 review
        Assert.notNull(query.getOwnerAddress(), "Owner address null");
        Assert.notNull(query.getOperator(), "Operator null");
        var relationStore = dbManager.getUrc721AccountTokenRelationStore();
        var isApproved = false;
        if(relationStore.has(query.getOwnerAddress().toByteArray())){
            var relation = relationStore.get(query.getOwnerAddress().toByteArray());
            if(relation.hasApprovalForAll() && Arrays.equals(relation.getApprovedForAll(), query.getOperator().toByteArray()))
                isApproved = true;
        }

        return Protocol.Urc721IsApprovedForAll.newBuilder()
                .setOwnerAddress(query.getOwnerAddress())
                .setOperator(query.getOperator())
                .setIsApproved(isApproved)
                .build();
    }

    @Override
    public Protocol.AddressMessage getApproved(Protocol.Urc721Token query) {
        try {
            var tokenStore = dbManager.getUrc721TokenStore();
            var contractAddr = query.getAddress().toByteArray();
            var tokenKey = ArrayUtils.addAll(contractAddr, ByteArray.fromLong(query.getId()));
            Assert.isTrue(tokenStore.has(tokenKey), "token not found!");

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
        Assert.notNull(query.getOwnerAddress(), "Owner address null");

        int pageSize = query.hasField(NFT_TEMPLATE_QUERY_FIELD_PAGE_SIZE) ? query.getPageSize() : DEFAULT_PAGE_SIZE;
        int pageIndex = query.hasField(NFT_TEMPLATE_QUERY_FIELD_PAGE_INDEX) ? query.getPageIndex() : DEFAULT_PAGE_INDEX;
        Assert.isTrue(pageSize > 0 && pageIndex >= 0 && pageSize <= MAX_PAGE_SIZE, "Invalid paging info");

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

    private static final Set<String> TOKEN_LIST_OWNER_TYPES = new HashSet<>(Arrays.asList("owner","approved", "approved_all"));

    @Override
    public Protocol.Urc721TokenPage listToken(Protocol.Urc721TokenQuery query) {
        Assert.notNull(query.getOwnerAddress(), "Owner address null");
        Assert.isTrue(Objects.nonNull(query.getOwnerType()) && TOKEN_LIST_OWNER_TYPES.contains(query.getOwnerType().toLowerCase()), "Owner type null or invalid type");

        int pageSize = query.hasField(NFT_TOKEN_QUERY_FIELD_PAGE_SIZE) ? query.getPageSize() : DEFAULT_PAGE_SIZE;
        int pageIndex = query.hasField(NFT_TOKEN_QUERY_FIELD_PAGE_INDEX) ? query.getPageIndex() : DEFAULT_PAGE_INDEX;
        Assert.isTrue(pageSize > 0 && pageIndex >= 0 && pageSize <= MAX_PAGE_SIZE, "Invalid paging info");

        var ownerAddr = query.getOwnerAddress().toByteArray();
        var ownerType = query.getOwnerType();

        var contractAddr = query.getAddress();
        var hasContractAddr = query.hasField(NFT_TOKEN_QUERY_FIELD_ADDR);

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
    public Protocol.Urc721BalanceOf balanceOf(Protocol.Urc721BalanceOf query) {
        //@todo urc721: count token of provided urc721 address only
        Assert.notNull(query.getOwnerAddress(), "Owner address null");
        var nftAccountTokenStore = dbManager.getUrc721AccountTokenRelationStore();
        var owner = query.getOwnerAddress().toByteArray();
        var result = Protocol.Urc721BalanceOf.newBuilder()
                .setCount(0)
                .setOwnerAddress(query.getOwnerAddress());

        if(nftAccountTokenStore.has(owner))
            return result.build();

        var firstAccTokenRelation = nftAccountTokenStore.get(owner);
        return result.setCount(firstAccTokenRelation.getTotal()).build();
    }

    @Override
    public GrpcAPI.StringMessage getName(Protocol.AddressMessage msg) {
        var contract = Protocol.Urc721Contract.newBuilder()
                .setAddress(msg.getAddress())
                .build();
        return GrpcAPI.StringMessage.newBuilder()
                .setValue(getContract(contract).getName())
                .build();
    }

    @Override
    public GrpcAPI.StringMessage getSymbol(Protocol.AddressMessage msg) {
        //@todo urc721 review
        var contract = Protocol.Urc721Contract.newBuilder()
                .setAddress(msg.getAddress())
                .build();
        return GrpcAPI.StringMessage.newBuilder()
                .setValue(getContract(contract).getSymbol())
                .build();
    }

    @Override
    public GrpcAPI.NumberMessage getTotalSupply(Protocol.AddressMessage msg) {
        //@todo urc721 review
        var contract = Protocol.Urc721Contract.newBuilder()
                .setAddress(msg.getAddress())
                .build();
        return GrpcAPI.NumberMessage.newBuilder()
                .setNum(getContract(contract).getTotalSupply())
                .build();
    }

    @Override
    public GrpcAPI.StringMessage getTokenUri(Protocol.Urc721Token msg) {
        var tokenQuery = Protocol.Urc721Token.newBuilder()
                .setAddress(msg.getAddress())
                .build();
        return GrpcAPI.StringMessage.newBuilder()
                .setValue(getToken(tokenQuery).getUri())
                .build();
    }

    @Override
    public Protocol.AddressMessage getOwnerOf(Protocol.Urc721Token msg) {
        //@todo urc721 review
        var tokenQuery = Protocol.Urc721Token.newBuilder()
                .setAddress(msg.getAddress())
                .build();
        return Protocol.AddressMessage.newBuilder()
                .setAddress(getToken(tokenQuery).getOwnerAddress())
                .build();
    }
}
