package org.unichain.core.services.http.fullnode.servlet;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unichain.api.GrpcAPI;
import org.unichain.api.GrpcAPI.EasyTransferMessage;
import org.unichain.api.GrpcAPI.EasyTransferResponse;
import org.unichain.api.GrpcAPI.Return.response_code;
import org.unichain.common.crypto.ECKey;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.JsonFormat;
import org.unichain.core.services.http.utils.JsonFormat.ParseException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract.TransferContract;
import org.unichain.protos.Protocol.Transaction.Contract.ContractType;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Collectors;


@Component
@Slf4j(topic = "API")
public class EasyTransferServlet extends HttpServlet {

  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {

  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    GrpcAPI.Return.Builder returnBuilder = GrpcAPI.Return.newBuilder();
    EasyTransferResponse.Builder responseBuild = EasyTransferResponse.newBuilder();
    boolean visible = false;
    try {
      String input = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(input);
      visible = Util.getVisiblePost(input);
      EasyTransferMessage.Builder build = EasyTransferMessage.newBuilder();
      JsonFormat.merge(input, build, visible);
      byte[] privateKey = wallet.pass2Key(build.getPassPhrase().toByteArray());
      ECKey ecKey = ECKey.fromPrivate(privateKey);
      byte[] owner = ecKey.getAddress();
      TransferContract.Builder builder = TransferContract.newBuilder();
      builder.setOwnerAddress(ByteString.copyFrom(owner));
      builder.setToAddress(build.getToAddress());
      builder.setAmount(build.getAmount());

      TransactionCapsule transactionCapsule;
      transactionCapsule = wallet.createTransactionCapsule(builder.build(), ContractType.TransferContract);
      transactionCapsule.sign(privateKey);
      GrpcAPI.Return retur = wallet.broadcastTransaction(transactionCapsule.getInstance());
      responseBuild.setTransaction(transactionCapsule.getInstance());
      responseBuild.setResult(retur);
      response.getWriter().println(Util.printEasyTransferResponse(responseBuild.build(), visible));
    } catch (ParseException e) {
      logger.error("Api error: {} --> ", e.getMessage(), e);
    } catch (IOException e) {
      logger.error("Api error: {} --> ", e.getMessage(), e);
    } catch (ContractValidateException e) {
      returnBuilder.setResult(false)
              .setCode(response_code.CONTRACT_VALIDATE_ERROR)
              .setMessage(ByteString.copyFromUtf8(e.getMessage()));
      responseBuild.setResult(returnBuilder.build());
      try {
        response.getWriter().println(JsonFormat.printToString(responseBuild.build(), visible));
      } catch (IOException ioe) {
        logger.error("Api error: {} --> ", e.getMessage(), e);
      }
      return;
    } catch (Exception e) {
      logger.debug("Exception: {}", e.getMessage());
    }
  }
}
