package org.unx.core.services.http;

import com.alibaba.fastjson.JSONObject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unx.api.GrpcAPI;
import org.unx.common.utils.ByteArray;
import org.unx.core.Wallet;
import org.unx.core.capsule.TransactionCapsule;
import org.unx.protos.Protocol.Transaction;


@Component
@Slf4j(topic = "API")
public class BroadcastServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      PostParams params = PostParams.getPostParams(request);
      Transaction transaction = Util.packTransaction(params.getParams(), params.isVisible());
      TransactionCapsule transactionCapsule = new TransactionCapsule(transaction);
      String transactionID = ByteArray
          .toHexString(transactionCapsule.getTransactionId().getBytes());
      GrpcAPI.Return result = wallet.broadcastTransaction(transaction);
      JSONObject res = JSONObject.parseObject(JsonFormat.printToString(result, params.isVisible()));
      res.put("txid", transactionID);
      response.getWriter().println(res.toJSONString());
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }
}
