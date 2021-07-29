package org.unichain.core.services.http.fullnode.servlet;

import com.alibaba.fastjson.JSONObject;
import com.google.protobuf.ByteString;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unichain.api.GrpcAPI.Return;
import org.unichain.api.GrpcAPI.Return.response_code;
import org.unichain.api.GrpcAPI.TransactionExtention;
import org.unichain.common.utils.ByteArray;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.JsonFormat;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract.TriggerSmartContract;
import org.unichain.protos.Protocol.Transaction;
import org.unichain.protos.Protocol.Transaction.Contract.ContractType;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.stream.Collectors;


@Component
@Slf4j(topic = "API")
public class TriggerConstantContractServlet extends HttpServlet {
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
    if (!jsonObject.containsKey(functionSelector)
        || StringUtil.isNullOrEmpty(jsonObject.getString(functionSelector))) {
      throw new InvalidParameterException("function_selector isn't set.");
    }
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    TriggerSmartContract.Builder build = TriggerSmartContract.newBuilder();
    TransactionExtention.Builder unxExtBuilder = TransactionExtention.newBuilder();
    Return.Builder retBuilder = Return.newBuilder();
    boolean visible = false;
    try {
      String contract = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(contract);
      visible = Util.getVisiblePost(contract);
      validateParameter(contract);
      JsonFormat.merge(contract, build, visible);
      JSONObject jsonObject = JSONObject.parseObject(contract);
      String selector = jsonObject.getString(functionSelector);
      String parameter = jsonObject.getString("parameter");
      String data = Util.parseMethod(selector, parameter);
      build.setData(ByteString.copyFrom(ByteArray.fromHexString(data)));
      long feeLimit = Util.getJsonLongValue(jsonObject, "fee_limit");

      TransactionCapsule unxCap = wallet.createTransactionCapsule(build.build(), ContractType.TriggerSmartContract);

      Transaction.Builder txBuilder = unxCap.getInstance().toBuilder();
      Transaction.raw.Builder rawBuilder = unxCap.getInstance().getRawData().toBuilder();
      rawBuilder.setFeeLimit(feeLimit);
      txBuilder.setRawData(rawBuilder);

      Transaction unx = wallet.triggerConstantContract(build.build(), new TransactionCapsule(txBuilder.build()),
              unxExtBuilder,
              retBuilder);
      unx = Util.setTransactionPermissionId(jsonObject, unx);
      unxExtBuilder.setTransaction(unx);
      retBuilder.setResult(true).setCode(response_code.SUCCESS);
    } catch (ContractValidateException e) {
      retBuilder.setResult(false).setCode(response_code.CONTRACT_VALIDATE_ERROR).setMessage(ByteString.copyFromUtf8(e.getMessage()));
    } catch (Exception e) {
      String errString = null;
      if (e.getMessage() != null) {
        errString = e.getMessage().replaceAll("[\"]", "\'");
      }
      retBuilder.setResult(false).setCode(response_code.OTHER_ERROR).setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + errString));
    }
    unxExtBuilder.setResult(retBuilder);
    response.getWriter().println(Util.printTransactionExtention(unxExtBuilder.build(), visible));
  }
}