package org.unichain.core.services.internal.impl;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.unichain.common.utils.ByteArray;
import org.unichain.common.utils.Utils;
import org.unichain.core.Wallet;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.core.services.internal.NftService;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Transaction.Contract.ContractType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.unichain.core.services.http.utils.Util.*;


@Slf4j
@Service
public class NftServiceImpl implements NftService {
    @Autowired
    private Manager dbManager;

    @Autowired
    private Wallet wallet;

    @Override
    public Protocol.Transaction createContract(Contract.CreateNftTemplateContract contract) throws ContractValidateException {
        return wallet.createTransactionCapsule(contract, ContractType.CreateNftTemplateContract).getInstance();
    }

    @Override
    public Protocol.NftTemplate getContract(Protocol.NftTemplate query) {
        Assert.notNull(query.getContract(), "Template contract empty");
        return dbManager.getNftTemplateStore().get(Util.stringAsBytesUppercase(query.getContract())).getInstance();
    }

    @Override
    public Protocol.Transaction createToken(Contract.MintNftTokenContract contract) throws ContractValidateException {
        return wallet.createTransactionCapsule(contract, ContractType.MintNftTokenContract).getInstance();
    }

    @Override
    public Protocol.NftTokenGetResult getToken(Protocol.NftTokenGet query) {
        Assert.notNull(query.getContract(), "Token contract empty");
        var id = ArrayUtils.addAll(Util.stringAsBytesUppercase(query.getContract()), ByteArray.fromLong(query.getId()));
        if(!dbManager.getNftTokenStore().has(id))
            return Protocol.NftTokenGetResult.newBuilder().build();
        var token = dbManager.getNftTokenStore().get(id).getInstance();
        return  Protocol.NftTokenGetResult.newBuilder()
                .setId(token.getId())
                .setContract(query.getContract())
                .setUri(token.getUri())
                .setApproval(token.getApproval())
                .setLastOperation(token.getLastOperation())
                .setOwnerAddress(token.getOwnerAddress())
                .build();
    }

    @Override
    public Protocol.Transaction addMinter(Contract.AddNftMinterContract addNftMinterContract) {
        return null;
    }

    @Override
    public Protocol.Transaction removeMinter(Contract.RemoveNftMinterContract contract) throws ContractValidateException {
        return wallet.createTransactionCapsule(contract, ContractType.RemoveNftMinterContract).getInstance();
    }

    @Override
    public Protocol.Transaction renounceMinter(Contract.RenounceNftMinterContract contract) throws ContractValidateException {
        return wallet.createTransactionCapsule(contract, ContractType.RenounceNftMinterContract).getInstance();
    }

    @Override
    public Protocol.Transaction approve(Contract.ApproveNftTokenContract contract) throws ContractValidateException {
        return wallet.createTransactionCapsule(contract, ContractType.ApproveNftTokenContract).getInstance();
    }

    @Override
    public Protocol.NftTokenApproveResult approval(Protocol.NftTokenApproveQuery query) {
        Assert.notNull(query.getOwnerAddress(), "Owner address null");

        int pageSize = query.hasField(NFT_TOKEN_APPROVE_QUERY_FIELD_PAGE_SIZE) ? query.getPageSize() : DEFAULT_PAGE_SIZE;
        int pageIndex = query.hasField(NFT_TOKEN_APPROVE_QUERY_FIELD_PAGE_INDEX) ? query.getPageIndex() : DEFAULT_PAGE_INDEX;
        Assert.isTrue(pageSize > 0 && pageIndex >= 0 && pageSize <= MAX_PAGE_SIZE, "Invalid paging info");

        var ownerAddr = query.getOwnerAddress().toByteArray();
        var nftTokenStore = dbManager.getNftTokenStore();
        var approveStore = dbManager.getNftTokenApproveRelationStore();
        var relationStore = dbManager.getNftAccountTokenStore();

        List<Protocol.NftToken> unsorted = new ArrayList<>();

        if(relationStore.has(ownerAddr) && relationStore.get(ownerAddr).getTotal() > 0){
            var relationCapsule = approveStore.get(relationStore.get(ownerAddr).getHead().toByteArray());
            while (true){
                unsorted.add(nftTokenStore.get(relationCapsule.getKey()).getInstance());
                if(relationCapsule.hasNext()) {
                    relationCapsule = approveStore.get(relationCapsule.getNext());
                } else {
                    break;
                }
            }
        } else {
            unsorted.add(Protocol.NftToken.newBuilder().build());
        }

        return Protocol.NftTokenApproveResult.newBuilder()
                .setPageIndex(pageIndex)
                .setPageSize(pageSize)
                .setTotal(unsorted.size())
                .addAllTokens(Utils.paging(unsorted, pageIndex, pageSize))
                .build();
    }

    @Override
    public Protocol.Transaction setApprovalForAll(Contract.ApproveForAllNftTokenContract approvalAll) throws ContractValidateException {
        return wallet.createTransactionCapsule(approvalAll, ContractType.ApproveForAllNftTokenContract).getInstance();
    }

    @Override
    public Protocol.NftTokenApproveAllResult approvalForAll(Protocol.NftTokenApproveAllQuery query) {
        Assert.notNull(query.getOwnerAddress(), "Owner address null");

        var relationStore = dbManager.getNftAccountTokenStore();
        var relation = relationStore.get(query.getOwnerAddress().toByteArray());

        if (relation.getApproveAllMap() == null)
            return Protocol.NftTokenApproveAllResult.newBuilder().setOwnerAddress(query.getOwnerAddress()).build();

        List<Protocol.NftAccountTokenRelation> approveList = new ArrayList<>();

        relation.getApproveAllMap().forEach((owner, isApproveAll) -> {
            logger.info("---------------> Found owner approve all: {} - {}", owner, isApproveAll);
            if (isApproveAll) {
                var ownerRelationCap = relationStore.get(ByteString.copyFrom(ByteArray.fromHexString(owner)).toByteArray());
                if (ownerRelationCap != null) {
                    var nftOwnerRelation = Protocol.NftAccountTokenRelation.newBuilder()
                            .setOwnerAddress(ownerRelationCap.getInstance().getOwnerAddress())
                            .setTotal(ownerRelationCap.getInstance().getTotal())
                            .build();
                    approveList.add(nftOwnerRelation);
                }
            }
        });

        return Protocol.NftTokenApproveAllResult.newBuilder()
                .setOwnerAddress(query.getOwnerAddress())
                .addAllApproveList(approveList)
                .setApprovalForAll(ByteString.copyFrom(relation.getApprovedForAll()))
                .build();
    }

    @Override
    public Protocol.IsApprovedForAll isApprovalForAll(Protocol.IsApprovedForAll query) {
        Assert.notNull(query.getOwnerAddress(), "Owner address null");
        Assert.notNull(query.getOperator(), "Operator null");

        var relationStore = dbManager.getNftAccountTokenStore();
        var isApproved = false;
        if(relationStore.has(query.getOwnerAddress().toByteArray())){
            var relation = relationStore.get(query.getOwnerAddress().toByteArray());
            if(relation.hasApprovalForAll() && Arrays.equals(relation.getApprovedForAll(), query.getOperator().toByteArray()))
                isApproved = true;
        }

        return Protocol.IsApprovedForAll.newBuilder()
                .setOwnerAddress(query.getOwnerAddress())
                .setOperator(query.getOperator())
                .setIsApproved(isApproved)
                .build();
    }

    @Override
    public Protocol.Transaction burnToken(Contract.BurnNftTokenContract contract) throws ContractValidateException {
        return wallet.createTransactionCapsule(contract, ContractType.ApproveNftTokenContract).getInstance();
    }

    @Override
    public Protocol.NftTemplateQueryResult listContract(Protocol.NftTemplateQuery query) {
        Assert.notNull(query.getOwnerAddress(), "Owner address null");

        int pageSize = query.hasField(NFT_TEMPLATE_QUERY_FIELD_PAGE_SIZE) ? query.getPageSize() : DEFAULT_PAGE_SIZE;
        int pageIndex = query.hasField(NFT_TEMPLATE_QUERY_FIELD_PAGE_INDEX) ? query.getPageIndex() : DEFAULT_PAGE_INDEX;
        Assert.isTrue(pageSize > 0 && pageIndex >= 0 && pageSize <= MAX_PAGE_SIZE, "Invalid paging info");

        var ownerAddr = query.getOwnerAddress().toByteArray();
        List<Protocol.NftTemplate> unsorted = new ArrayList<>();
        var relationStore = dbManager.getNftAccountTemplateStore();
        var templateStore = dbManager.getNftTemplateStore();

        if ("OWNER".equalsIgnoreCase(query.getOwnerType()) && relationStore.has(ownerAddr) && relationStore.get(ownerAddr).getTotal() > 0) {
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
        var minterRelationStore = dbManager.getNftMinterContractStore();
        if("MINTER".equalsIgnoreCase(query.getOwnerType()) && minterRelationStore.has(ownerAddr) && minterRelationStore.get(ownerAddr).getTotal() > 0){
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

        return  Protocol.NftTemplateQueryResult.newBuilder()
                .setPageIndex(pageIndex)
                .setPageSize(pageSize)
                .setTotal(unsorted.size())
                .addAllTemplates(Utils.paging(unsorted, pageIndex, pageSize))
                .build();
    }

    @Override
    public Protocol.NftTokenQueryResult listToken(Protocol.NftTokenQuery query) {
        Assert.notNull(query.getOwnerAddress(), "Owner address null");

        int pageSize = query.hasField(NFT_TOKEN_QUERY_FIELD_PAGE_SIZE) ? query.getPageSize() : DEFAULT_PAGE_SIZE;
        int pageIndex = query.hasField(NFT_TOKEN_QUERY_FIELD_PAGE_INDEX) ? query.getPageIndex() : DEFAULT_PAGE_INDEX;
        Assert.isTrue(pageSize > 0 && pageIndex >= 0 && pageSize <= MAX_PAGE_SIZE, "Invalid paging info");

        var ownerAddr = query.getOwnerAddress().toByteArray();
        var contract = query.getContract();
        var hasFieldContract = query.hasField(NFT_TOKEN_QUERY_FIELD_CONTRACT);
        List<Protocol.NftToken> unsorted = new ArrayList<>();
        var relationStore = dbManager.getNftAccountTokenStore();
        var tokenStore = dbManager.getNftTokenStore();

        if(relationStore.has(ownerAddr) && relationStore.get(ownerAddr).getTotal() > 0){
            var start = tokenStore.get(relationStore.get(ownerAddr).getHead().toByteArray());
            while (true){
                if(!hasFieldContract || start.getContract().equalsIgnoreCase(contract))
                    unsorted.add(start.getInstance());

                if(start.hasNext())
                {
                    start = tokenStore.get(start.getNext());
                }
                else {
                    break;
                }
            }
        }

        return  Protocol.NftTokenQueryResult.newBuilder()
                .setPageIndex(pageIndex)
                .setPageSize(pageSize)
                .setTotal(unsorted.size())
                .addAllTokens(Utils.paging(unsorted, pageIndex, pageSize))
                .build();
    }

    @Override
    public Protocol.Transaction transfer(Contract.TransferNftTokenContract contract) throws ContractValidateException {
        return wallet.createTransactionCapsule(contract, ContractType.TransferNftTokenContract).getInstance();
    }

    @Override
    public Protocol.NftBalanceOf balanceOf(Protocol.NftBalanceOf query) {
        Assert.notNull(query.getOwnerAddress(), "Owner address null");

        var nftAccountTokenStore = dbManager.getNftAccountTokenStore();
        var owner = query.getOwnerAddress().toByteArray();
        var result = Protocol.NftBalanceOf.newBuilder()
                .setCount(0)
                .setOwnerAddress(query.getOwnerAddress());

        if(nftAccountTokenStore.has(owner))
            return result.build();

        var firstAccTokenRelation = nftAccountTokenStore.get(owner);
        return result.setCount(firstAccTokenRelation.getTotal()).build();
    }
}
