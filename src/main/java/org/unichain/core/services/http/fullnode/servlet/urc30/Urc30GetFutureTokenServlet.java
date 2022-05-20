package org.unichain.core.services.http.fullnode.servlet.urc30;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unichain.core.Wallet;
import org.unichain.core.services.http.utils.JsonFormat;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.FutureTokenQuery;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Collectors;

@Component
@Slf4j(topic = "API")
public class Urc30GetFutureTokenServlet extends HttpServlet {
  @Autowired
  private Wallet wallet;

  private String convertOutput(Protocol.FutureTokenPack tokenPool) {
      JSONObject tokenPoolJson = JSONObject.parseObject(JsonFormat.printToString(tokenPool, false));
      return tokenPoolJson.toJSONString();
  }

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
      doPost(request, response);
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      String filter = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(filter);
      boolean visible = Util.getVisiblePost(filter);
      FutureTokenQuery.Builder build = FutureTokenQuery.newBuilder();
      JsonFormat.merge(filter, build, visible);
      var query = build.build();
      Protocol.FutureTokenPack reply = wallet.getFutureToken(query);
      if (reply != null) {
        if (visible) {
          response.getWriter().println(JsonFormat.printToString(reply, true));
        } else {
          response.getWriter().println(convertOutput(reply));
        }
      } else {
        response.getWriter().println("{}");
      }
    } catch (Exception e) {
      logger.debug("Exception: {}", e.getMessage());
      try {
        response.getWriter().println(Util.printErrorMsg(e));
      } catch (IOException ioe) {
        logger.debug("IOException: {}", ioe.getMessage());
      }
    }
  }
}
