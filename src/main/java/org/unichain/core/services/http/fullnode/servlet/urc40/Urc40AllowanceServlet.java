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
public class Urc40AllowanceServlet extends HttpServlet {
  @Autowired
  private Urc40 urc40;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
      doPost(request, response);
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      var tokenFilter = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(tokenFilter);
      var visible = Util.getVisiblePost(tokenFilter);
      var builder = Protocol.Urc40AllowanceQuery.newBuilder();
      JsonFormat.merge(tokenFilter, builder, visible);
      var query = builder.build();
      logger.info("Urc40Allowance --> {}" , query);
      var reply = urc40.allowance(query);
      if (reply != null) {
        response.getWriter().println(visible ? JsonFormat.printToString(reply, true) :convertOutput(reply));
      } else {
        response.getWriter().println("{}");
      }
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      try {
        response.getWriter().println(Util.printErrorMsg(e));
      } catch (IOException ioe) {
        logger.debug("IOException: {}", ioe.getMessage());
      }
    }
  }

  private String convertOutput(GrpcAPI.NumberMessage msg) {
    return JSONObject.parseObject(JsonFormat.printToString(msg, false)).toJSONString();
  }
}
