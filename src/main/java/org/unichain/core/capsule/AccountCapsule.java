/*
 * unichain-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * unichain-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.unichain.core.capsule;

import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.unichain.common.utils.ByteArray;
import org.unichain.core.Wallet;
import org.unichain.core.db.Manager;
import org.unichain.protos.Contract.AccountCreateContract;
import org.unichain.protos.Contract.AccountUpdateContract;
import org.unichain.protos.Protocol.*;
import org.unichain.protos.Protocol.Account.AccountResource;
import org.unichain.protos.Protocol.Account.Builder;
import org.unichain.protos.Protocol.Account.Frozen;
import org.unichain.protos.Protocol.Permission.PermissionType;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

@Slf4j(topic = "capsule")
public class AccountCapsule implements ProtoCapsule<Account>, Comparable<AccountCapsule> {

  private Account account;


  @Override
  public int compareTo(AccountCapsule otherObject) {
    return Long.compare(otherObject.getBalance(), this.getBalance());
  }

  /**
   * get account from bytes data.
   */
  public AccountCapsule(byte[] data) {
    try {
      this.account = Account.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage());
    }
  }

  /**
   * initial account capsule.
   */
  public AccountCapsule(ByteString accountName, ByteString address, AccountType accountType, long balance) {
    this.account = Account.newBuilder()
        .setAccountName(accountName)
        .setType(accountType)
        .setAddress(address)
        .setBalance(balance)
        .build();
  }

  /**
   * construct account from AccountCreateContract.
   */
  public AccountCapsule(final AccountCreateContract contract) {
    this.account = Account.newBuilder()
        .setType(contract.getType())
        .setAddress(contract.getAccountAddress())
        .setTypeValue(contract.getTypeValue())
        .build();
  }


  /**
   * construct account from AccountCreateContract and createTime.
   */
  public AccountCapsule(final AccountCreateContract contract, long createTime, boolean withDefaultPermission, Manager manager) {
    if (withDefaultPermission) {
      Permission owner = createDefaultOwnerPermission(contract.getAccountAddress());
      Permission active = createDefaultActivePermission(contract.getAccountAddress(), manager);
      this.account = Account.newBuilder()
          .setType(contract.getType())
          .setAddress(contract.getAccountAddress())
          .setTypeValue(contract.getTypeValue())
          .setCreateTime(createTime)
          .setOwnerPermission(owner)
          .addActivePermission(active)
          .build();
    } else {
      this.account = Account.newBuilder()
          .setType(contract.getType())
          .setAddress(contract.getAccountAddress())
          .setTypeValue(contract.getTypeValue())
          .setCreateTime(createTime)
          .build();
    }
  }

  /**
   * construct account from AccountUpdateContract
   */
  public AccountCapsule(final AccountUpdateContract contract) {

  }

  /**
   * get account from address and account name.
   */
  public AccountCapsule(ByteString address, ByteString accountName, AccountType accountType) {
    this.account = Account.newBuilder()
        .setType(accountType)
        .setAccountName(accountName)
        .setAddress(address)
        .build();
  }

  /**
   * get account from address.
   */
  public AccountCapsule(ByteString address, AccountType accountType) {
    this.account = Account.newBuilder()
        .setType(accountType)
        .setAddress(address)
        .build();
  }

  /**
   * get account from address.
   */
  public AccountCapsule(ByteString address, AccountType accountType, long createTime, boolean withDefaultPermission, Manager manager) {
    if (withDefaultPermission) {
      Permission owner = createDefaultOwnerPermission(address);
      Permission active = createDefaultActivePermission(address, manager);

      this.account = Account.newBuilder()
          .setType(accountType)
          .setAddress(address)
          .setCreateTime(createTime)
          .setOwnerPermission(owner)
          .addActivePermission(active)
          .build();
    } else {
      this.account = Account.newBuilder()
          .setType(accountType)
          .setAddress(address)
          .setCreateTime(createTime)
          .build();
    }
  }

  public AccountCapsule(Account account) {
    this.account = account;
  }

  public byte[] getData() {
    return this.account.toByteArray();
  }

  @Override
  public Account getInstance() {
    return this.account;
  }

  public void clearFutureToken(byte[] tokenKey){
      this.account = this.account.toBuilder().removeTokenFuture(new String(tokenKey)).build();
  }

  public void clearUrc20FutureToken(String base58Addr){
    this.account = this.account.toBuilder().removeUrc20Future(base58Addr).build();
  }

  public void clearFuture(){
    this.account = this.account.toBuilder().clearFutureSupply().build();
  }

  public void setInstance(Account account) {
    this.account = account;
  }

  public ByteString getAddress() {
    return this.account.getAddress();
  }

  public byte[] createDbKey() {
    return getAddress().toByteArray();
  }

  public String createReadableString() {
    return ByteArray.toHexString(getAddress().toByteArray());
  }

  public AccountType getType() {
    return this.account.getType();
  }

  public ByteString getAccountName() {
    return this.account.getAccountName();
  }

  public ByteString getAccountId() {
    return this.account.getAccountId();
  }

  private static ByteString getActiveDefaultOperations(Manager manager) {
    return ByteString.copyFrom(manager.getDynamicPropertiesStore().getActiveDefaultOperations());
  }

  public static Permission createDefaultOwnerPermission(ByteString address) {
    Key.Builder key = Key.newBuilder();
    key.setAddress(address);
    key.setWeight(1);

    Permission.Builder owner = Permission.newBuilder();
    owner.setType(PermissionType.Owner);
    owner.setId(0);
    owner.setPermissionName("owner");
    owner.setThreshold(1);
    owner.setParentId(0);
    owner.addKeys(key);

    return owner.build();
  }

  public static Permission createDefaultActivePermission(ByteString address, Manager manager) {
    Key.Builder key = Key.newBuilder();
    key.setAddress(address);
    key.setWeight(1);

    Permission.Builder active = Permission.newBuilder();
    active.setType(PermissionType.Active);
    active.setId(2);
    active.setPermissionName("active");
    active.setThreshold(1);
    active.setParentId(0);
    active.setOperations(getActiveDefaultOperations(manager));
    active.addKeys(key);

    return active.build();
  }

  public static Permission createDefaultWitnessPermission(ByteString address) {
    Key.Builder key = Key.newBuilder();
    key.setAddress(address);
    key.setWeight(1);

    Permission.Builder active = Permission.newBuilder();
    active.setType(PermissionType.Witness);
    active.setId(1);
    active.setPermissionName("witness");
    active.setThreshold(1);
    active.setParentId(0);
    active.addKeys(key);

    return active.build();
  }

  public void setDefaultWitnessPermission(Manager manager) {
    Account.Builder builder = this.account.toBuilder();
    Permission witness = createDefaultWitnessPermission(this.getAddress());
    if (!this.account.hasOwnerPermission()) {
      Permission owner = createDefaultOwnerPermission(this.getAddress());
      builder.setOwnerPermission(owner);
    }
    if (this.account.getActivePermissionCount() == 0) {
      Permission active = createDefaultActivePermission(this.getAddress(), manager);
      builder.addActivePermission(active);
    }
    this.account = builder.setWitnessPermission(witness).build();
  }

  public byte[] getWitnessPermissionAddress() {
    if (this.account.getWitnessPermission().getKeysCount() == 0) {
      return getAddress().toByteArray();
    } else {
      return this.account.getWitnessPermission().getKeys(0).getAddress().toByteArray();
    }
  }

  public long getBalance() {
    return this.account.getBalance();
  }

  public long getLatestOperationTime() {
    return this.account.getLatestOprationTime();
  }

  public void setLatestOperationTime(long latest_time) {
    this.account = this.account.toBuilder().setLatestOprationTime(latest_time).build();
  }

  public long getLatestConsumeTime() {
    return this.account.getLatestConsumeTime();
  }

  public void setLatestConsumeTime(long latest_time) {
    this.account = this.account.toBuilder().setLatestConsumeTime(latest_time).build();
  }

  public long getLatestConsumeFreeTime() {
    return this.account.getLatestConsumeFreeTime();
  }

  public void setLatestConsumeFreeTime(long latest_time) {
    this.account = this.account.toBuilder().setLatestConsumeFreeTime(latest_time).build();
  }

  public void setBalance(long balance) {
    this.account = this.account.toBuilder().setBalance(balance).build();
  }

  public void setFutureSummary(FutureSummary summary){
    this.account = account.toBuilder()
            .setFutureSupply(summary)
            .build();
  }
  public void addDelegatedFrozenBalanceForBandwidth(long balance) {
    this.account = this.account.toBuilder()
            .setDelegatedFrozenBalanceForBandwidth(Math.addExact(this.account.getDelegatedFrozenBalanceForBandwidth(), balance)).build();
  }

  public long getAcquiredDelegatedFrozenBalanceForBandwidth() {
    return this.account.getAcquiredDelegatedFrozenBalanceForBandwidth();
  }


  public void setAcquiredDelegatedFrozenBalanceForBandwidth(long balance) {
    this.account = this.account.toBuilder()
            .setAcquiredDelegatedFrozenBalanceForBandwidth(balance).build();
  }
  public void addAcquiredDelegatedFrozenBalanceForBandwidth(long balance) {
    this.account = this.account.toBuilder()
            .setAcquiredDelegatedFrozenBalanceForBandwidth(Math.addExact(this.account.getAcquiredDelegatedFrozenBalanceForBandwidth(), balance)).build();
  }

  public long getAcquiredDelegatedFrozenBalanceForEnergy() {
    return getAccountResource().getAcquiredDelegatedFrozenBalanceForEnergy();
  }

  public void setAcquiredDelegatedFrozenBalanceForEnergy(long balance) {
    AccountResource newAccountResource = getAccountResource().toBuilder()
            .setAcquiredDelegatedFrozenBalanceForEnergy(balance).build();

    this.account = this.account.toBuilder()
            .setAccountResource(newAccountResource)
            .build();
  }

  public long getDelegatedFrozenBalanceForEnergy() {
    return getAccountResource().getDelegatedFrozenBalanceForEnergy();
  }

  public long getDelegatedFrozenBalanceForBandwidth() {
    return this.account.getDelegatedFrozenBalanceForBandwidth();
  }

  public void setDelegatedFrozenBalanceForBandwidth(long balance) {
    this.account = this.account.toBuilder()
        .setDelegatedFrozenBalanceForBandwidth(balance)
        .build();
  }

  public void addAcquiredDelegatedFrozenBalanceForEnergy(long balance) {
    AccountResource newAccountResource = getAccountResource().toBuilder()
        .setAcquiredDelegatedFrozenBalanceForEnergy(
            Math.addExact(getAccountResource().getAcquiredDelegatedFrozenBalanceForEnergy(), balance)).build();

    this.account = this.account.toBuilder()
        .setAccountResource(newAccountResource)
        .build();
  }

  public void addDelegatedFrozenBalanceForEnergy(long balance) {
    AccountResource newAccountResource = getAccountResource().toBuilder()
        .setDelegatedFrozenBalanceForEnergy(
            Math.addExact(getAccountResource().getDelegatedFrozenBalanceForEnergy(), balance)).build();

    this.account = this.account.toBuilder()
        .setAccountResource(newAccountResource)
        .build();
  }


  public void setAllowance(long allowance) {
    this.account = this.account.toBuilder().setAllowance(allowance).build();
  }


  @Override
  public String toString() {
    return this.account.toString();
  }


  /**
   * set votes.
   */
  public void addVotes(ByteString voteAddress, long voteAdd) {
    this.account = this.account.toBuilder()
        .addVotes(Vote.newBuilder().setVoteAddress(voteAddress).setVoteCount(voteAdd).build())
        .build();
  }

  public void clearAssetV2() {
    this.account = this.account.toBuilder()
        .clearAssetV2()
        .build();
  }

  public void clearLatestAssetOperationTimeV2() {
    this.account = this.account.toBuilder()
        .clearLatestAssetOperationTimeV2()
        .build();
  }

  public void clearFreeAssetNetUsageV2() {
    this.account = this.account.toBuilder()
        .clearFreeAssetNetUsageV2()
        .build();
  }

  public void clearVotes() {
    this.account = this.account.toBuilder()
        .clearVotes()
        .build();
  }

  /**
   * get votes.
   */
  public List<Vote> getVotesList() {
      return this.account.getVotesList();
  }

  public long getUnichainPower() {
    long tp = 0L;
    for (int i = 0; i < account.getFrozenCount(); ++i) {
      tp = Math.addExact(account.getFrozen(i).getFrozenBalance(), tp);
    }

    tp = Math.addExact(account.getAccountResource().getFrozenBalanceForEnergy().getFrozenBalance(), tp);
    tp = Math.addExact(account.getDelegatedFrozenBalanceForBandwidth(), tp);
    tp = Math.addExact(account.getAccountResource().getDelegatedFrozenBalanceForEnergy(), tp);
    return tp;
  }

  /**
   * asset balance enough
   */
  public boolean assetBalanceEnough(byte[] key, long amount) {
    Map<String, Long> assetMap = this.account.getAssetMap();
    String nameKey = ByteArray.toStr(key);
    Long currentAmount = assetMap.get(nameKey);

    return amount > 0 && null != currentAmount && amount <= currentAmount;
  }

  public boolean assetBalanceEnoughV2(byte[] key, long amount, Manager manager) {
    Map<String, Long> assetMap;
    String nameKey;
    Long currentAmount;
    if (manager.getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
      assetMap = this.account.getAssetMap();
      nameKey = ByteArray.toStr(key);
      currentAmount = assetMap.get(nameKey);
    } else {
      String tokenID = ByteArray.toStr(key);
      assetMap = this.account.getAssetV2Map();
      currentAmount = assetMap.get(tokenID);
    }

    return amount > 0 && null != currentAmount && amount <= currentAmount;
  }


  /**
   * reduce asset amount.
   */
  public boolean reduceAssetAmount(byte[] key, long amount) {
    Map<String, Long> assetMap = this.account.getAssetMap();
    String nameKey = ByteArray.toStr(key);
    Long currentAmount = assetMap.get(nameKey);
    if (amount > 0 && null != currentAmount && amount <= currentAmount) {
      this.account = this.account.toBuilder()
          .putAsset(nameKey, Math.subtractExact(currentAmount, amount)).build();
      return true;
    }

    return false;
  }

  /**
   * reduce asset amount.
   */
  public boolean reduceAssetAmountV2(byte[] key, long amount, Manager manager) {
    //key is token name
    if (manager.getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
      Map<String, Long> assetMap = this.account.getAssetMap();
      AssetIssueCapsule assetIssueCapsule = manager.getAssetIssueStore().get(key);
      String tokenID = assetIssueCapsule.getId();
      String nameKey = ByteArray.toStr(key);
      Long currentAmount = assetMap.get(nameKey);
      if (amount > 0 && null != currentAmount && amount <= currentAmount) {
        this.account = this.account.toBuilder()
            .putAsset(nameKey, Math.subtractExact(currentAmount, amount))
            .putAssetV2(tokenID, Math.subtractExact(currentAmount, amount))
            .build();
        return true;
      }
    }
    //key is token id
    if (manager.getDynamicPropertiesStore().getAllowSameTokenName() == 1) {
      String tokenID = ByteArray.toStr(key);
      Map<String, Long> assetMapV2 = this.account.getAssetV2Map();
      Long currentAmount = assetMapV2.get(tokenID);
      if (amount > 0 && null != currentAmount && amount <= currentAmount) {
        this.account = this.account.toBuilder()
            .putAssetV2(tokenID, Math.subtractExact(currentAmount, amount))
            .build();
        return true;
      }
    }

    return false;
  }

  public boolean addAssetAmount(byte[] key, long amount) {
    Map<String, Long> assetMap = this.account.getAssetMap();
    String nameKey = ByteArray.toStr(key);
    Long currentAmount = assetMap.get(nameKey);
    if (currentAmount == null) {
      currentAmount = 0L;
    }
    this.account = this.account.toBuilder().putAsset(nameKey, Math.addExact(currentAmount, amount))
        .build();
    return true;
  }

  public boolean addAssetAmountV2(byte[] key, long amount, Manager manager) {
    //key is token name
    if (manager.getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
      Map<String, Long> assetMap = this.account.getAssetMap();
      AssetIssueCapsule assetIssueCapsule = manager.getAssetIssueStore().get(key);
      String tokenID = assetIssueCapsule.getId();
      String nameKey = ByteArray.toStr(key);
      Long currentAmount = assetMap.get(nameKey);
      if (currentAmount == null) {
        currentAmount = 0L;
      }
      this.account = this.account.toBuilder()
          .putAsset(nameKey, Math.addExact(currentAmount, amount))
          .putAssetV2(tokenID, Math.addExact(currentAmount, amount))
          .build();
    }
    //key is token id
    if (manager.getDynamicPropertiesStore().getAllowSameTokenName() == 1) {
      String tokenIDStr = ByteArray.toStr(key);
      Map<String, Long> assetMapV2 = this.account.getAssetV2Map();
      Long currentAmount = assetMapV2.get(tokenIDStr);
      if (currentAmount == null) {
        currentAmount = 0L;
      }
      this.account = this.account.toBuilder()
          .putAssetV2(tokenIDStr, Math.addExact(currentAmount, amount))
          .build();
    }
    return true;
  }

  public void setAccountName(byte[] name) {
    this.account = this.account.toBuilder().setAccountName(ByteString.copyFrom(name)).build();
  }

  public void setAccountId(byte[] id) {
    this.account = this.account.toBuilder().setAccountId(ByteString.copyFrom(id)).build();
  }

  public boolean addAsset(byte[] key, long value) {
    Map<String, Long> assetMap = this.account.getAssetMap();
    String nameKey = ByteArray.toStr(key);
    if (!assetMap.isEmpty() && assetMap.containsKey(nameKey)) {
      return false;
    }

    this.account = this.account.toBuilder().putAsset(nameKey, value).build();

    return true;
  }

  public boolean addToken(byte[] key, long value) {
    Map<String, Long> tokenMap = this.account.getTokenMap();
    String nameKey = ByteArray.toStr(key);

    long totalValue = tokenMap.containsKey(nameKey) ? Math.addExact(tokenMap.get(nameKey), value) : value;
    this.account = this.account.toBuilder().putToken(nameKey, totalValue).build();
    return true;
  }

  public boolean addUrc20Token(byte[] addr, BigInteger value) {
    Map<String, String> tokenMap = this.account.getUrc20Map();
    String addrBase58 = Wallet.encode58Check(addr);
    BigInteger totalValue = tokenMap.containsKey(addrBase58) ? (new BigInteger(tokenMap.get(addrBase58))).add(value) : value;
    this.account = this.account.toBuilder().putUrc20(addrBase58, totalValue.toString()).build();
    return true;
  }

  public void addBalance(long value){
    this.account = this.account.toBuilder().setBalance(Math.addExact(account.getBalance(), value)).build();
  }

  public boolean setFutureTokenSummary(FutureTokenSummaryV2 summary){
      this.account = this.account.toBuilder()
              .putTokenFuture(summary.getTokenName(), summary)
              .build();
      return true;
  }

  public boolean setUrc20FutureTokenSummary(String base58Addr, Urc20FutureTokenSummary summary){
    this.account = this.account.toBuilder()
            .putUrc20Future(base58Addr, summary)
            .build();
    return true;
  }


  public FutureTokenSummaryV2 getFutureTokenSummary(String tokenName){
    return account.getTokenFutureMap().get(tokenName);
  }

  public Urc20FutureTokenSummary getUrc20FutureTokenSummary(String addrBase58){
    return account.getUrc20FutureMap().get(addrBase58);
  }

  public FutureSummary getFutureSummary(){
    return account.hasFutureSupply() ? account.getFutureSupply() : null;
  }

  /**
   * burn more token issued by this account
   */
  public boolean burnToken(byte[] key, long amount) {
    Map<String, Long> tokenMap = this.account.getTokenMap();
    String nameKey = ByteArray.toStr(key);
    if (!tokenMap.containsKey(nameKey) || tokenMap.get(nameKey) < amount) {
      return false;
    }

    long remain = Math.subtractExact(tokenMap.get(nameKey), amount);
    if(remain > 0)
    {
      this.account = this.account.toBuilder().putToken(nameKey, remain).build();
    }
    else
    {
      this.account = this.account.toBuilder().removeToken(nameKey).build();
    }
    return true;
  }

  public boolean burnUrc20Token(byte[] addr, BigInteger amount) {
    Map<String, String> tokenMap = this.account.getUrc20Map();
    String addrBase58 = Wallet.encode58Check(addr);
    Assert.isTrue(tokenMap.containsKey(addrBase58)
            && (new BigInteger(tokenMap.get(addrBase58))).compareTo(amount) >= 0, "burn amount violate available token amount!");

    BigInteger remain = new BigInteger(tokenMap.get(addrBase58)).subtract(amount);
    if(remain.compareTo(BigInteger.ZERO) > 0)
    {
      this.account = this.account.toBuilder().putUrc20(addrBase58, remain.toString()).build();
    }
    else
    {
      this.account = this.account.toBuilder().removeUrc20(addrBase58).build();
    }
    return true;
  }

  public long burnAllAvailableToken(byte[] key) {
    Map<String, Long> tokenMap = this.account.getTokenMap();
    String nameKey = ByteArray.toStr(key);
    if (!tokenMap.containsKey(nameKey)) {
      logger.warn("missing token {}", nameKey);
      return 0L;
    }
    else {
      long available = tokenMap.get(nameKey);
      this.account = this.account.toBuilder().removeToken(nameKey).build();
      return available;
    }
  }

  public BigInteger burnUrc20AllAvailableToken(byte[] addr) {
    Map<String, String> tokenMap = this.account.getUrc20Map();
    String addrBase58 = Wallet.encode58Check(addr);
    if (!tokenMap.containsKey(addrBase58)) {
      return BigInteger.ZERO;
    }
    else {
      BigInteger available = new BigInteger(tokenMap.get(addrBase58));
      this.account = this.account.toBuilder().removeUrc20(addrBase58).build();
      return available;
    }
  }

  public Long getTokenAvailable(byte[] key) {
    Map<String, Long> tokenMap = this.account.getTokenMap();
    String nameKey = ByteArray.toStr(key);
    return tokenMap.containsKey(nameKey) ? tokenMap.get(nameKey) : 0L;
  }

  public BigInteger getUrc20TokenAvailable(String addrBase58) {
    Map<String, String> tokenMap = this.account.getUrc20Map();
    return tokenMap.containsKey(addrBase58) ? new BigInteger(tokenMap.get(addrBase58)) : BigInteger.ZERO;
  }

  public boolean addAssetV2(byte[] key, long value) {
    String tokenID = ByteArray.toStr(key);
    Map<String, Long> assetV2Map = this.account.getAssetV2Map();
    if (!assetV2Map.isEmpty() && assetV2Map.containsKey(tokenID)) {
      return false;
    }

    this.account = this.account.toBuilder()
        .putAssetV2(tokenID, value)
        .build();
    return true;
  }

  /**
   * add asset.
   */
  public boolean addAssetMapV2(Map<String, Long> assetMap) {
    this.account = this.account.toBuilder().putAllAssetV2(assetMap).build();
    return true;
  }


  public Map<String, Long> getAssetMap() {
    Map<String, Long> assetMap = this.account.getAssetMap();
    if (assetMap.isEmpty()) {
      assetMap = Maps.newHashMap();
    }

    return assetMap;
  }

  public Map<String, Long> getAssetMapV2() {
    Map<String, Long> assetMap = this.account.getAssetV2Map();
    if (assetMap.isEmpty()) {
      assetMap = Maps.newHashMap();
    }

    return assetMap;
  }

  public boolean addAllLatestAssetOperationTimeV2(Map<String, Long> map) {
    this.account = this.account.toBuilder().putAllLatestAssetOperationTimeV2(map).build();
    return true;
  }

  public Map<String, Long> getLatestAssetOperationTimeMap() {
    return this.account.getLatestAssetOperationTimeMap();
  }

  public Map<String, Long> getLatestAssetOperationTimeMapV2() {
    return this.account.getLatestAssetOperationTimeV2Map();
  }

  public long getLatestAssetOperationTime(String assetName) {
    return this.account.getLatestAssetOperationTimeOrDefault(assetName, 0);
  }

  public long getLatestAssetOperationTimeV2(String assetName) {
    return this.account.getLatestAssetOperationTimeV2OrDefault(assetName, 0);
  }

  public void putLatestAssetOperationTimeMap(String key, Long value) {
    this.account = this.account.toBuilder().putLatestAssetOperationTime(key, value).build();
  }

  public void putLatestAssetOperationTimeMapV2(String key, Long value) {
    this.account = this.account.toBuilder().putLatestAssetOperationTimeV2(key, value).build();
  }

  public int getFrozenCount() {
    return getInstance().getFrozenCount();
  }

  public List<Frozen> getFrozenList() {
    return getInstance().getFrozenList();
  }

  public long getFrozenBalance() {
    List<Frozen> frozenList = getFrozenList();
    final long[] frozenBalance = {0};
    frozenList.forEach(frozen -> frozenBalance[0] = Math.addExact(frozenBalance[0], frozen.getFrozenBalance()));
    return frozenBalance[0];
  }

  public long getAllFrozenBalanceForBandwidth() {
    return Math.addExact(getFrozenBalance(), getAcquiredDelegatedFrozenBalanceForBandwidth());
  }

  public int getFrozenSupplyCount() {
    return getInstance().getFrozenSupplyCount();
  }

  public List<Frozen> getFrozenSupplyList() {
    return getInstance().getFrozenSupplyList();
  }

  public long getFrozenSupplyBalance() {
    List<Frozen> frozenSupplyList = getFrozenSupplyList();
    final long[] frozenSupplyBalance = {0};
    frozenSupplyList.forEach(frozen -> frozenSupplyBalance[0] = Math.addExact(frozenSupplyBalance[0], frozen.getFrozenBalance()));
    return frozenSupplyBalance[0];
  }

  public ByteString getAssetIssuedName() {
    return getInstance().getAssetIssuedName();
  }

  public void setAssetIssuedName(byte[] nameKey) {
    ByteString assetIssuedName = ByteString.copyFrom(nameKey);
    this.account = this.account.toBuilder().setAssetIssuedName(assetIssuedName).build();
  }

  public ByteString getAssetIssuedID() {
    return getInstance().getAssetIssuedID();
  }

  public void setAssetIssuedID(byte[] id) {
    ByteString assetIssuedID = ByteString.copyFrom(id);
    this.account = this.account.toBuilder().setAssetIssuedID(assetIssuedID).build();
  }

  public long getAllowance() {
    return getInstance().getAllowance();
  }

  public long getLatestWithdrawTime() {
    return getInstance().getLatestWithdrawTime();
  }

  public boolean getIsWitness() {
    return getInstance().getIsWitness();
  }

  public void setIsWitness(boolean isWitness) {
    this.account = this.account.toBuilder().setIsWitness(isWitness).build();
  }

  public boolean getIsCommittee() {
    return getInstance().getIsCommittee();
  }

  public void setIsCommittee(boolean isCommittee) {
    this.account = this.account.toBuilder().setIsCommittee(isCommittee).build();
  }

  public void setFrozenForBandwidth(long frozenBalance, long expireTime) {
    Frozen newFrozen = Frozen.newBuilder()
        .setFrozenBalance(frozenBalance)
        .setExpireTime(expireTime)
        .build();

    long frozenCount = getFrozenCount();
    if (frozenCount == 0) {
      setInstance(getInstance().toBuilder()
          .addFrozen(newFrozen)
          .build());
    } else {
      setInstance(getInstance().toBuilder()
          .setFrozen(0, newFrozen)
          .build()
      );
    }
  }

  //set FrozenBalanceForBandwidth
  //for test only
  public void setFrozen(long frozenBalance, long expireTime) {
    Frozen newFrozen = Frozen.newBuilder()
        .setFrozenBalance(frozenBalance)
        .setExpireTime(expireTime)
        .build();

    this.account = this.account.toBuilder()
        .addFrozen(newFrozen)
        .build();
  }

  //for test only
  public void setLatestWithdrawTime(long latestWithdrawTime) {
    this.account = this.account.toBuilder()
        .setLatestWithdrawTime(latestWithdrawTime)
        .build();
  }

  public long getNetUsage() {
    return this.account.getNetUsage();
  }

  public void setNetUsage(long netUsage) {
    this.account = this.account.toBuilder()
        .setNetUsage(netUsage).build();
  }

  public AccountResource getAccountResource() {
    return this.account.getAccountResource();
  }


  public void setFrozenForEnergy(long newFrozenBalanceForEnergy, long time) {
    Frozen newFrozenForEnergy = Frozen.newBuilder()
        .setFrozenBalance(newFrozenBalanceForEnergy)
        .setExpireTime(time)
        .build();

    AccountResource newAccountResource = getAccountResource().toBuilder()
        .setFrozenBalanceForEnergy(newFrozenForEnergy).build();

    this.account = this.account.toBuilder()
        .setAccountResource(newAccountResource)
        .build();
  }


  public long getEnergyFrozenBalance() {
    return this.account.getAccountResource().getFrozenBalanceForEnergy().getFrozenBalance();
  }

  public long getEnergyUsage() {
    return this.account.getAccountResource().getEnergyUsage();
  }

  public long getAllFrozenBalanceForEnergy() {
    return Math.addExact(getEnergyFrozenBalance(), getAcquiredDelegatedFrozenBalanceForEnergy());
  }


  public void setEnergyUsage(long energyUsage) {
    this.account = this.account.toBuilder()
        .setAccountResource(
            this.account.getAccountResource().toBuilder().setEnergyUsage(energyUsage).build())
        .build();
  }

  public void setLatestConsumeTimeForEnergy(long latest_time) {
    this.account = this.account.toBuilder()
        .setAccountResource(
            this.account.getAccountResource().toBuilder().setLatestConsumeTimeForEnergy(latest_time)
                .build()).build();
  }

  public long getLatestConsumeTimeForEnergy() {
    return this.account.getAccountResource().getLatestConsumeTimeForEnergy();
  }

  public long getFreeNetUsage() {
    return this.account.getFreeNetUsage();
  }

  public void setFreeNetUsage(long freeNetUsage) {
    this.account = this.account.toBuilder()
        .setFreeNetUsage(freeNetUsage).build();
  }


  public boolean addAllFreeAssetNetUsageV2(Map<String, Long> map) {
    this.account = this.account.toBuilder().putAllFreeAssetNetUsageV2(map).build();
    return true;
  }

  public long getFreeAssetNetUsage(String assetName) {
    return this.account.getFreeAssetNetUsageOrDefault(assetName, 0);
  }

  public long getFreeAssetNetUsageV2(String assetName) {
    return this.account.getFreeAssetNetUsageV2OrDefault(assetName, 0);
  }

  public Map<String, Long> getAllFreeAssetNetUsage() {
    return this.account.getFreeAssetNetUsageMap();
  }

  public Map<String, Long> getAllFreeAssetNetUsageV2() {
    return this.account.getFreeAssetNetUsageV2Map();
  }

  public void putFreeAssetNetUsage(String s, long freeAssetNetUsage) {
    this.account = this.account.toBuilder()
        .putFreeAssetNetUsage(s, freeAssetNetUsage).build();
  }

  public void putFreeAssetNetUsageV2(String s, long freeAssetNetUsage) {
    this.account = this.account.toBuilder()
        .putFreeAssetNetUsageV2(s, freeAssetNetUsage).build();
  }

  public long getStorageLimit() {
    return this.account.getAccountResource().getStorageLimit();
  }

  public void setStorageLimit(long limit) {
    AccountResource accountResource = this.account.getAccountResource();
    accountResource = accountResource.toBuilder().setStorageLimit(limit).build();

    this.account = this.account.toBuilder()
        .setAccountResource(accountResource)
        .build();
  }

  public long getStorageUsage() {
    return this.account.getAccountResource().getStorageUsage();
  }

  public long getStorageLeft() {
    return Math.subtractExact(getStorageLimit(), getStorageUsage());
  }

  public void setStorageUsage(long usage) {
    AccountResource accountResource = this.account.getAccountResource();
    accountResource = accountResource.toBuilder().setStorageUsage(usage).build();

    this.account = this.account.toBuilder()
        .setAccountResource(accountResource)
        .build();
  }

  public long getLatestExchangeStorageTime() {
    return this.account.getAccountResource().getLatestExchangeStorageTime();
  }

  public void setLatestExchangeStorageTime(long time) {
    AccountResource accountResource = this.account.getAccountResource();
    accountResource = accountResource.toBuilder().setLatestExchangeStorageTime(time).build();

    this.account = this.account.toBuilder()
        .setAccountResource(accountResource)
        .build();
  }

  public void addStorageUsage(long storageUsage) {
    if (storageUsage <= 0) {
      return;
    }
    AccountResource accountResource = this.account.getAccountResource();
    accountResource = accountResource.toBuilder()
        .setStorageUsage(Math.addExact(accountResource.getStorageUsage(), storageUsage)).build();

    this.account = this.account.toBuilder()
        .setAccountResource(accountResource)
        .build();
  }

  public static Permission getDefaultPermission(ByteString owner) {
    return createDefaultOwnerPermission(owner);
  }

  public Permission getPermissionById(int id) {
    if (id == 0) {
      if (this.account.hasOwnerPermission()) {
        return this.account.getOwnerPermission();
      }
      return getDefaultPermission(this.account.getAddress());
    }
    if (id == 1) {
      if (this.account.hasWitnessPermission()) {
        return this.account.getWitnessPermission();
      }
      return null;
    }
    for (Permission permission : this.account.getActivePermissionList()) {
      if (id == permission.getId()) {
        return permission;
      }
    }
    return null;
  }

  public void updatePermissions(Permission owner, Permission witness, List<Permission> actives) {
    Account.Builder builder = this.account.toBuilder();
    owner = owner.toBuilder().setId(0).build();
    builder.setOwnerPermission(owner);
    if (builder.getIsWitness()) {
      witness = witness.toBuilder().setId(1).build();
      builder.setWitnessPermission(witness);
    }
    builder.clearActivePermission();
    for (int i = 0; i < actives.size(); i++) {
      Permission permission = actives.get(i).toBuilder().setId(i + 2).build();
      builder.addActivePermission(permission);
    }
    this.account = builder.build();
  }

  public void updateAccountType(AccountType accountType) {
    this.account = this.account.toBuilder().setType(accountType).build();
  }

  // just for vm create2 instruction
  public void clearDelegatedResource() {
    Builder builder = account.toBuilder();
    AccountResource newAccountResource = getAccountResource().toBuilder()
        .setAcquiredDelegatedFrozenBalanceForEnergy(0L).build();
    builder.setAccountResource(newAccountResource);
    builder.setAcquiredDelegatedFrozenBalanceForBandwidth(0L);
    this.account = builder.build();
  }
}