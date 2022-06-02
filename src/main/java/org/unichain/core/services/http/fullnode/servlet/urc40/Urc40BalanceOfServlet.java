package org.unichain.core.services.http.fullnode.servlet.urc40;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unichain.api.GrpcAPI;
import org.unichain.core.actuator.urc40.ext.Urc40;
import org.unichain.core.services.http.utils.JsonFormat;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Protocol;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Collectors;

@Component
@Slf4j(topic = "API")
public class Urc40BalanceOfServlet extends HttpServlet {
  @Autowired
  private Urc40 urc40;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    doPost(request, response);
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      var filter = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(filter);
      var visible = Util.getVisiblePost(filter);
      var builder = Protocol.Urc40BalanceOfQuery.newBuilder();
      JsonFormat.merge(filter, builder, visible);
      var reply = urc40.balanceOf(builder.build());
      if (reply != null) {
        response.getWriter().println(visible ? JsonFormat.printToString(reply, true) : convertOutput(reply));
      } else {
        response.getWriter().println("{}");
      }
    } catch (Exception e) {
      logger.error("Urc40BalanceOf error: {}", e.getMessage(), e);
      try {
        response.getWriter().println(Util.printErrorMsg(e));
      } catch (IOException ioe) {
        logger.debug("IOException: {}", ioe.getMessage());
      }
    }
  }

  private String convertOutput(GrpcAPI.NumberMessage msg) {
    JSONObject tokenPoolJson = JSONObject.parseObject(JsonFormat.printToString(msg, false));
    return tokenPoolJson.toJSONString();
  }
}
