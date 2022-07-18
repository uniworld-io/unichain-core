package org.unx.core.services.http;

import com.alibaba.fastjson.JSONObject;
import com.google.protobuf.ByteString;
import io.netty.util.internal.StringUtil;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unx.api.GrpcAPI.Return;
import org.unx.api.GrpcAPI.Return.response_code;
import org.unx.api.GrpcAPI.TransactionExtention;
import org.unx.common.utils.ByteArray;
import org.unx.core.Wallet;
import org.unx.core.capsule.TransactionCapsule;
import org.unx.core.exception.ContractValidateException;
import org.unx.protos.Protocol.Transaction;
import org.unx.protos.Protocol.Transaction.Contract.ContractType;
import org.unx.protos.contract.SmartContractOuterClass.TriggerSmartContract;


@Component
@Slf4j(topic = "API")
public class TriggerSmartContractServlet extends RateLimiterServlet {

  private final String functionSelector = "function_selector";

  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
  }

  protected void validateParameter(String contract) {
    JSONObject jsonObject = JSONObject.parseObject(contract);
    if (!jsonObject.containsKey("owner_address")
        || StringUtil.isNullOrEmpty(jsonObject.getString("owner_address"))) {
      throw new InvalidParameterException("owner_address isn't set.");
    }
    if (!jsonObject.containsKey("contract_address")
        || StringUtil.isNullOrEmpty(jsonObject.getString("contract_address"))) {
      throw new InvalidParameterException("contract_address isn't set.");
    }
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    TriggerSmartContract.Builder build = TriggerSmartContract.newBuilder();
    TransactionExtention.Builder unxExtBuilder = TransactionExtention.newBuilder();
    Return.Builder retBuilder = Return.newBuilder();
    boolean visible = false;
    try {
      String contract = request.getReader().lines()
          .collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(contract);
      visible = Util.getVisiblePost(contract);
      validateParameter(contract);
      JsonFormat.merge(contract, build, visible);
      JSONObject jsonObject = JSONObject.parseObject(contract);

      boolean isFunctionSelectorSet = jsonObject.containsKey(functionSelector)
          && !StringUtil.isNullOrEmpty(jsonObject.getString(functionSelector));
      String data;
      if (isFunctionSelectorSet) {
        String selector = jsonObject.getString(functionSelector);
        String parameter = jsonObject.getString("parameter");
        data = Util.parseMethod(selector, parameter);
        build.setData(ByteString.copyFrom(ByteArray.fromHexString(data)));
      } else {
        build.setData(ByteString.copyFrom(new byte[0]));
      }

      build.setCallTokenValue(Util.getJsonLongValue(jsonObject, "call_token_value"));
      build.setTokenId(Util.getJsonLongValue(jsonObject, "token_id"));
      build.setCallValue(Util.getJsonLongValue(jsonObject, "call_value"));
      long feeLimit = Util.getJsonLongValue(jsonObject, "fee_limit");
      TransactionCapsule unxCap = wallet
          .createTransactionCapsule(build.build(), ContractType.TriggerSmartContract);

      Transaction.Builder txBuilder = unxCap.getInstance().toBuilder();
      Transaction.raw.Builder rawBuilder = unxCap.getInstance().getRawData().toBuilder();
      rawBuilder.setFeeLimit(feeLimit);
      txBuilder.setRawData(rawBuilder);

      Transaction unx = wallet
          .triggerContract(build.build(), new TransactionCapsule(txBuilder.build()), unxExtBuilder,
              retBuilder);
      unx = Util.setTransactionPermissionId(jsonObject, unx);
      unxExtBuilder.setTransaction(unx);
      retBuilder.setResult(true).setCode(response_code.SUCCESS);
    } catch (ContractValidateException e) {
      retBuilder.setResult(false).setCode(response_code.CONTRACT_VALIDATE_ERROR)
          .setMessage(ByteString.copyFromUtf8(e.getMessage()));
    } catch (Exception e) {
      String errString = null;
      if (e.getMessage() != null) {
        errString = e.getMessage().replaceAll("[\"]", "\'");
      }
      retBuilder.setResult(false).setCode(response_code.OTHER_ERROR)
          .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + errString));
    }
    unxExtBuilder.setResult(retBuilder);
    response.getWriter().println(Util.printTransactionExtention(unxExtBuilder.build(), visible));
  }
}
