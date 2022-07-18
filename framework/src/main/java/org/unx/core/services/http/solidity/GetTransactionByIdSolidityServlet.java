package org.unx.core.services.http.solidity;

import com.google.protobuf.ByteString;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unx.api.GrpcAPI.BytesMessage;
import org.unx.common.utils.ByteArray;
import org.unx.core.Wallet;
import org.unx.core.services.http.JsonFormat;
import org.unx.core.services.http.PostParams;
import org.unx.core.services.http.RateLimiterServlet;
import org.unx.core.services.http.Util;
import org.unx.protos.Protocol.Transaction;


@Component
@Slf4j(topic = "API")
public class GetTransactionByIdSolidityServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      boolean visible = Util.getVisible(request);
      String input = request.getParameter("value");
      fillResponse(ByteString.copyFrom(ByteArray.fromHexString(input)), visible, response);
    } catch (Exception e) {
      logger.debug("Exception: {}", e.getMessage());
      try {
        response.getWriter().println(e.getMessage());
      } catch (IOException ioe) {
        logger.debug("IOException: {}", ioe.getMessage());
      }
    }
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      PostParams params = PostParams.getPostParams(request);
      BytesMessage.Builder build = BytesMessage.newBuilder();
      JsonFormat.merge(params.getParams(), build, params.isVisible());
      fillResponse(build.build().getValue(), params.isVisible(), response);
    } catch (Exception e) {
      logger.debug("Exception: {}", e.getMessage());
      try {
        response.getWriter().println(e.getMessage());
      } catch (IOException ioe) {
        logger.debug("IOException: {}", ioe.getMessage());
      }
    }
  }

  private void fillResponse(ByteString txId, boolean visible, HttpServletResponse response)
      throws IOException {
    Transaction reply = wallet.getTransactionById(txId);
    if (reply != null) {
      response.getWriter().println(Util.printTransaction(reply, visible));
    } else {
      response.getWriter().println("{}");
    }
  }

}