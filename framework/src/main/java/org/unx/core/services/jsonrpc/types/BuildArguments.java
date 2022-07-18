package org.unx.core.services.jsonrpc.types;

import com.google.protobuf.ByteString;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.unx.api.GrpcAPI.BytesMessage;
import org.unx.core.Wallet;
import org.unx.core.exception.JsonRpcInvalidParamsException;
import org.unx.core.exception.JsonRpcInvalidRequestException;
import org.unx.protos.Protocol.Transaction.Contract.ContractType;
import org.unx.protos.contract.SmartContractOuterClass.SmartContract;
import org.unx.core.services.jsonrpc.JsonRpcApiUtil;

@NoArgsConstructor
@AllArgsConstructor
@ToString
public class BuildArguments {

  @Getter
  @Setter
  private String from;
  @Getter
  @Setter
  private String to;
  @Getter
  @Setter
  private String gas = "0x0";
  @Getter
  @Setter
  private String gasPrice = ""; //not used
  @Getter
  @Setter
  private String value;
  @Getter
  @Setter
  private String data;
  @Getter
  @Setter
  private String nonce = ""; //not used

  @Getter
  @Setter
  private Long tokenId = 0L;
  @Getter
  @Setter
  private Long tokenValue = 0L;
  @Getter
  @Setter
  private String abi = "";
  @Getter
  @Setter
  private Long consumeUserResourcePercent = 0L;
  @Getter
  @Setter
  private Long originEnergyLimit = 0L;
  @Getter
  @Setter
  private String name = "";

  @Getter
  @Setter
  private Integer permissionId = 0;
  @Getter
  @Setter
  private String extraData = "";

  @Getter
  @Setter
  private boolean visible = false;

  public BuildArguments(CallArguments args) {
    from = args.getFrom();
    to = args.getTo();
    gas = args.getGas();
    gasPrice = args.getGasPrice();
    value = args.getValue();
    data = args.getData();
  }

  public ContractType getContractType(Wallet wallet) throws JsonRpcInvalidRequestException,
      JsonRpcInvalidParamsException {
    ContractType contractType;

    // to is null
    if (JsonRpcApiUtil.paramStringIsNull(to)) {
      // data is null
      if (JsonRpcApiUtil.paramStringIsNull(data)) {
        throw new JsonRpcInvalidRequestException("invalid json request");
      }

      contractType = ContractType.CreateSmartContract;
    } else {
      // to is not null
      byte[] contractAddressData = JsonRpcApiUtil.addressCompatibleToByteArray(to);
      BytesMessage.Builder build = BytesMessage.newBuilder();
      BytesMessage bytesMessage = build.setValue(ByteString.copyFrom(contractAddressData)).build();
      SmartContract smartContract = wallet.getContract(bytesMessage);

      // check if to is smart contract
      if (smartContract != null) {
        contractType = ContractType.TriggerSmartContract;
      } else {
        // tokenId and tokenValue: urc10, value: UNW
        if (availableTransferAsset()) {
          contractType = ContractType.TransferAssetContract;
        } else {
          if (StringUtils.isNotEmpty(value)) {
            contractType = ContractType.TransferContract;
          } else {
            throw new JsonRpcInvalidRequestException("invalid json request");
          }
        }
      }
    }

    return contractType;
  }

  public long parseValue() throws JsonRpcInvalidParamsException {
    return JsonRpcApiUtil.parseQuantityValue(value);
  }

  public long parseGas() throws JsonRpcInvalidParamsException {
    return JsonRpcApiUtil.parseQuantityValue(gas);
  }

  private boolean availableTransferAsset() {
    return tokenId > 0 && tokenValue > 0 && JsonRpcApiUtil.paramQuantityIsNull(value);
  }

}