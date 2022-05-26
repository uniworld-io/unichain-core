package org.unichain.core.services.http.fullnode.servlet.urc40;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unichain.core.Wallet;
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
public class Urc40FutureGetServlet extends HttpServlet {
  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
      doPost(request, response);
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      var filter = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(filter);
      var visible = Util.getVisiblePost(filter);
      var builder = Protocol.Urc40FutureTokenQuery.newBuilder();
      JsonFormat.merge(filter, builder, visible);
      var query = builder.build();
      var reply = wallet.urc40FutureGet(query);
      if (reply != null) {
        response.getWriter().println(visible ? JsonFormat.printToString(reply, true) : convertOutput(reply));
      } else {
        response.getWriter().println("{}");
      }
    } catch (Exception e) {
      try {
        logger.error("Urc40FutureGet error: {}", e.getMessage(), e);
        response.getWriter().println(Util.printErrorMsg(e));
      } catch (IOException ioe) {
        logger.debug("IOException: {}", ioe.getMessage());
      }
    }
  }

  private String convertOutput(Protocol.Urc40FutureTokenPack msg) {
    return  JSONObject.parseObject(JsonFormat.printToString(msg, false)).toJSONString();
  }
}
