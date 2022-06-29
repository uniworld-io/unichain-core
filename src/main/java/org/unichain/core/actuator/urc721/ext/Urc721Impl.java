package org.unichain.core.actuator.urc721.ext;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.unichain.api.GrpcAPI;
import org.unichain.common.utils.AddressUtil;
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

    private static final Descriptors.FieldDescriptor URC721_APPROVE_FOR_ALL_FIELD_OWNER= Protocol.Urc721ApprovedForAllQuery.getDescriptor().findFieldByNumber(Protocol.Urc721ApprovedForAllQuery.OWNER_ADDRESS_FIELD_NUMBER);
    private static final Descriptors.FieldDescriptor URC721_APPROVE_FOR_ALL_FIELD_CONTRACT= Protocol.Urc721ApprovedForAllQuery.getDescriptor().findFieldByNumber(Protocol.Urc721ApprovedForAllQuery.ADDRESS_FIELD_NUMBER);

    private static final Descriptors.FieldDescriptor URC721_IS_APPROVE_FOR_ALL_FIELD_OWNER= Protocol.Urc721IsApprovedForAllQuery.getDescriptor().findFieldByNumber(Protocol.Urc721IsApprovedForAllQuery.OWNER_ADDRESS_FIELD_NUMBER);
    private static final Descriptors.FieldDescriptor URC721_IS_APPROVE_FOR_ALL_FIELD_OPERATOR = Protocol.Urc721IsApprovedForAllQuery.getDescriptor().findFieldByNumber(Protocol.Urc721IsApprovedForAllQuery.OPERATOR_FIELD_NUMBER);
    private static final Descriptors.FieldDescriptor URC721_IS_APPROVE_FOR_ALL_FIELD_CONTRACT = Protocol.Urc721IsApprovedForAllQuery.getDescriptor().findFieldByNumber(Protocol.Urc721IsApprovedForAllQuery.ADDRESS_FIELD_NUMBER);

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
    public static Descriptors.FieldDescriptor URC721_CONTRACT_QUERY_FIELD_TYPE = Protocol.Urc721ContractQuery.getDescriptor().findFieldByNumber(Protocol.Urc721ContractQuery.OWNER_TYPE_FIELD_NUMBER);


    public static Descriptors.FieldDescriptor URC721_TOKEN_QUERY_FIELD_ADDR = Protocol.Urc721TokenQuery.getDescriptor().findFieldByNumber(Protocol.Urc721TokenQuery.ADDRESS_FIELD_NUMBER);
    public static Descriptors.FieldDescriptor URC721_TOKEN_QUERY_FIELD_ID = Protocol.Urc721TokenQuery.getDescriptor().findFieldByNumber(Protocol.Urc721TokenQuery.ID_FIELD_NUMBER);


    @Autowired
    private Manager dbManager;

    @Autowired
    private Wallet wallet;

    @Override
    public Protocol.Transaction createContract(Contract.Urc721CreateContract contract) throws ContractValidateException {
        contract = contract.toBuilder()
                .setAddress(ByteString.copyFrom(AddressUtil.generateRandomAddress()))
                .build();
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
        Assert.isTrue(query.hasField(URC721_TOKEN_QUERY_FIELD_ADDR)
                && query.hasField(URC721_TOKEN_QUERY_FIELD_ID)
                && query.getId() >= 0,
                "Missing or bad contract address | token id");

        var tokenKey = Urc721TokenCapsule.genTokenKey(query.getAddress().toByteArray(), query.getId());

        if(!dbManager.getUrc721TokenStore().has(tokenKey))
        {
            return Protocol.Urc721Token.newBuilder().build();
        }
        else {
            var token = dbManager.getUrc721TokenStore().get(tokenKey).getInstance();
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
    public Protocol.BoolMessage isApprovedForAll(Protocol.Urc721IsApprovedForAllQuery query) {
        Assert.isTrue(query.hasField(URC721_IS_APPROVE_FOR_ALL_FIELD_OWNER)
                && query.hasField(URC721_IS_APPROVE_FOR_ALL_FIELD_OPERATOR)
                && query.hasField(URC721_IS_APPROVE_FOR_ALL_FIELD_CONTRACT),
                "Owner|operator|contract address null");

        var ownerAddr = query.getOwnerAddress().toByteArray();
        var operatorAddr = query.getOperator().toByteArray();
        var contractAddr = query.getAddress().toByteArray();

        Assert.isTrue(Wallet.addressValid(ownerAddr)
                && Wallet.addressValid(operatorAddr)
                && Wallet.addressValid(contractAddr),
                "Invalid owner|contract|operator address");

        var summaryStore = dbManager.getUrc721AccountTokenRelationStore();

        var isApproved4All = !summaryStore.has(ownerAddr) ? false :
                summaryStore.get(ownerAddr).isApprovedForAll(contractAddr, operatorAddr);

        return Protocol.BoolMessage.newBuilder()
                .setValue(isApproved4All)
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
    public Protocol.AddressMessage getApprovedForAll(Protocol.Urc721ApprovedForAllQuery query) {
        try {
            Assert.isTrue(query.hasField(URC721_APPROVE_FOR_ALL_FIELD_OWNER) && query.hasField(URC721_APPROVE_FOR_ALL_FIELD_CONTRACT),
                    "Missing owner|contract address");

            var ownerAddr = query.getOwnerAddress().toByteArray();
            var contractAddr = query.getAddress().toByteArray();

            Assert.isTrue(Wallet.addressValid(ownerAddr) && Wallet.addressValid(contractAddr),
                    "Invalid owner|contract address");

            var summaryStore = dbManager.getUrc721AccountTokenRelationStore();
            var builder =  Protocol.AddressMessage
                    .newBuilder()
                    .clearAddress();

            if(summaryStore.has(ownerAddr)){
                var operator = summaryStore.get(ownerAddr).getApprovedForAll(contractAddr);
                operator.ifPresent(operatorAddr -> builder.setAddress(ByteString.copyFrom(operatorAddr)));
            }

            return builder.build();
        }
        catch (Exception e){
            logger.error("getApprovedForAll got error -->", e);
            return Protocol.AddressMessage.newBuilder().build();
        }
    }

    @Override
    public Protocol.Transaction burn(Contract.Urc721BurnContract contract) throws ContractValidateException {
        return wallet.createTransactionCapsule(contract, ContractType.Urc721BurnContract).getInstance();
    }

    @Override
    public Protocol.Urc721ContractPage listContract(Protocol.Urc721ContractQuery query) {
        Assert.isTrue(query.hasField(URC721_CONTRACT_QUERY_FIELD_OWNER_ADDR), "Owner address null");
        Assert.isTrue(query.hasField(URC721_CONTRACT_QUERY_FIELD_TYPE)
                && ("owner".equalsIgnoreCase(query.getOwnerType()) || "minter".equalsIgnoreCase(query.getOwnerType())),
                "Bad type: missing or invalid value, required minter|owner");

        int pageSize = query.hasField(URC721_CONTRACT_QUERY_FIELD_PAGE_SIZE) ? query.getPageSize() : DEFAULT_PAGE_SIZE;
        int pageIndex = query.hasField(URC721_CONTRACT_QUERY_FIELD_PAGE_INDEX) ? query.getPageIndex() : DEFAULT_PAGE_INDEX;

        Assert.isTrue((pageSize > 0) && (pageIndex >= 0) && (pageSize <= MAX_PAGE_SIZE), "Invalid paging info");

        var ownerAddr = query.getOwnerAddress().toByteArray();
        Assert.isTrue(Wallet.addressValid(ownerAddr), "Invalid owner address");

        List<Protocol.Urc721Contract> unsorted = new ArrayList<>();
        var summaryStore = dbManager.getUrc721AccountContractRelationStore();
        var minterSummaryStore = dbManager.getUrc721MinterContractRelationStore();
        var contractStore = dbManager.getUrc721ContractStore();

        if ("owner".equalsIgnoreCase(query.getOwnerType()) && summaryStore.has(ownerAddr) && summaryStore.get(ownerAddr).getTotal() > 0) {
            var summary = summaryStore.get(ownerAddr);
            var start = contractStore.get(summary.getHead().toByteArray());
            while (true) {
                unsorted.add(start.getInstance());
                if (start.hasNext()) {
                    start = contractStore.get(start.getNext());
                    continue;
                } else {
                    break;
                }
            }
        }


        if("minter".equalsIgnoreCase(query.getOwnerType()) && minterSummaryStore.has(ownerAddr) && minterSummaryStore.get(ownerAddr).getTotal() > 0){
            var minterSummary = minterSummaryStore.get(ownerAddr);
            var start = contractStore.get(minterSummary.getHead().toByteArray());
            while (true){
                unsorted.add(start.getInstance());
                if(start.hasNextOfMinter()){
                    start = contractStore.get(start.getNextOfMinter());
                }else {
                    break;
                }
            }
        }

        //because new builder: clear action dont modify root info!
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
        try {
            Assert.isTrue(query.hasField(URC721_TOKEN_LIST_QUERY_FIELD_OWNER), "Owner address null");
            Assert.isTrue(query.hasField(URC721_TOKEN_LIST_QUERY_FIELD_TYPE) && TOKEN_LIST_OWNER_TYPES.contains(query.getOwnerType().toLowerCase()), "Owner type null or invalid type");

            int pageSize = query.hasField(URC721_TOKEN_LIST_QUERY_FIELD_PAGE_SIZE) ? query.getPageSize() : DEFAULT_PAGE_SIZE;
            int pageIndex = query.hasField(URC721_TOKEN_LIST_QUERY_FIELD_PAGE_INDEX) ? query.getPageIndex() : DEFAULT_PAGE_INDEX;
            Assert.isTrue(pageSize > 0 && pageIndex >= 0 && pageSize <= MAX_PAGE_SIZE, "Invalid paging info");

            var ownerAddr = query.getOwnerAddress().toByteArray();
            var ownerType = query.getOwnerType().toLowerCase();

            var contractAddr = query.getAddress();
            var hasContractAddr = query.hasField(URC721_TOKEN_LIST_QUERY_FIELD_ADDR);

            List<Protocol.Urc721Token> unsorted = new ArrayList<>();
            Predicate<Urc721TokenCapsule> filter = cap -> !hasContractAddr || Arrays.equals(cap.getAddr(), contractAddr.toByteArray());

            switch (ownerType) {
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

            return Protocol.Urc721TokenPage.newBuilder()
                    .setPageSize(pageSize)
                    .setPageIndex(pageIndex)
                    .setTotal(unsorted.size())
                    .addAllTokens(Utils.paging(unsorted, pageIndex, pageSize))
                    .build();
        }
        catch (Exception e){
            logger.error("listtoken error -->", e);
            throw e;
        }
    }

    private List<Protocol.Urc721Token> listTokenByOwner(byte[] ownerAddr, Predicate<Urc721TokenCapsule> filter){
        var unsorted = new LinkedList<Protocol.Urc721Token>();
        var summaryStore = dbManager.getUrc721AccountTokenRelationStore();
        var tokenStore = dbManager.getUrc721TokenStore();

        if(summaryStore.has(ownerAddr) && summaryStore.get(ownerAddr).getTotal() > 0){
            var start = tokenStore.get(summaryStore.get(ownerAddr).getHead().toByteArray());
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

    private List<Protocol.Urc721Token> listTokenByApproved(byte[] operatorAddr, Predicate<Urc721TokenCapsule> filter){
        var tokenStore = dbManager.getUrc721TokenStore();
        var approvedStore = dbManager.getUrc721TokenApproveRelationStore();
        var summaryStore = dbManager.getUrc721AccountTokenRelationStore();
        var result = new LinkedList<Protocol.Urc721Token>();

        if(summaryStore.has(operatorAddr) && summaryStore.get(operatorAddr).getTotalApprove() > 0){
            var summary = summaryStore.get(operatorAddr);
            var approveIndex = approvedStore.get(summary.getHeadApprove());
            while (true){
                if(tokenStore.has(approveIndex.getKey()))
                {
                    var tmpToken = tokenStore.get(approveIndex.getKey());
                    if(filter.test(tmpToken))
                        result.add(tmpToken.getInstance());
                }

                if(approveIndex.hasNext()) {
                    approveIndex = approvedStore.get(approveIndex.getNext());
                } else {
                    break;
                }
            }
        }
        return result;
    }

    private List<Protocol.Urc721Token> listTokenByApprovedForAll(byte[] operatorAddr, Predicate<Urc721TokenCapsule> filter){
        var result = new ArrayList<Protocol.Urc721Token>();
        var summaryStore = dbManager.getUrc721AccountTokenRelationStore();
        if (summaryStore.has(operatorAddr) && summaryStore.get(operatorAddr).getApproveAllMap().size() > 0) {
            summaryStore.get(operatorAddr).getApproveAllMap().forEach((ownerBase58, contracts) -> {
                val ownerAddr = Wallet.decodeFromBase58Check(ownerBase58);
                contracts.getContractsMap().forEach((contractBase58, approved) -> {
                    if(approved){
                        if (summaryStore.has(ownerAddr)) {
                            var ownerSummary = summaryStore.get(ownerAddr);
                            Predicate<Urc721TokenCapsule> filter0 = cap -> (Arrays.equals(cap.getAddr(), Wallet.decodeFromBase58Check(contractBase58))) && filter.test(cap);
                            result.addAll(listTokenByOwner(ownerSummary.getInstance().getOwnerAddress().toByteArray(), filter0));
                        }
                    }
                });
            });
        }

        return result;
    }

    @Override
    public Protocol.Transaction transferFrom(Contract.Urc721TransferFromContract contract) throws ContractValidateException {
        return wallet.createTransactionCapsule(contract, ContractType.Urc721TransferFromContract).getInstance();
    }

    @Override
    public GrpcAPI.NumberMessage balanceOf(Protocol.Urc721BalanceOfQuery query) {
        try {
            Assert.isTrue(query.hasField(URC721_BALANCE_OF_QUERY_FIELD_OWNER)
                            && query.hasField(URC721_BALANCE_OF_QUERY_FIELD_CONTRACT),
                    "Owner address | urc721 address is null");

            var owner = query.getOwnerAddress().toByteArray();
            var contract = query.getAddress().toByteArray();
            Assert.isTrue(Wallet.addressValid(owner) && Wallet.addressValid(contract), "Bad owner|contract address");

            Assert.isTrue(dbManager.getAccountStore().has(owner), "Unrecognized owner address");
            Assert.isTrue(dbManager.getUrc721ContractStore().has(contract), "Unrecognized contract address");

            var relationStore = dbManager.getUrc721AccountTokenRelationStore();
            var balance = relationStore.has(owner) ? relationStore.get(owner).getTotal(Wallet.encode58Check(contract)) : 0;

            return GrpcAPI.NumberMessage.newBuilder()
                    .setNum(balance)
                    .build();
        }
        catch (Exception e){
            logger.error("balanceOf error -->", e);
            throw e;
        }
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
