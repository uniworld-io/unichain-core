package org.unichain.core.services.http.fullnode.servlet.urc20;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unichain.core.actuator.urc20.ext.Urc20;
import org.unichain.core.services.http.utils.JsonFormat;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract.Urc20WithdrawFutureContract;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Collectors;

@Component
@Slf4j(topic = "API")
public class Urc20WithdrawFutureServlet extends HttpServlet {
  @Autowired
  private Urc20 urc20;

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      var contract = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(contract);
      var visible = Util.getVisiblePost(contract);
      var builder = Urc20WithdrawFutureContract.newBuilder();
      JsonFormat.merge(contract, builder, visible);
      var tx = urc20.withdrawFuture(builder.build());
      var jsonObject = JSONObject.parseObject(contract);
      tx = Util.setTransactionPermissionId(jsonObject, tx);
      response.getWriter().println(Util.printCreateTransaction(tx, visible));
    } catch (Exception e) {
      try {
        logger.error(e.getMessage(), e);
        response.setStatus(400);
        response.getWriter().println(Util.messageErrorHttp(e));
      } catch (IOException ioe) {
        logger.error("IOException: {}", ioe.getMessage());
      }
    }
  }
}
