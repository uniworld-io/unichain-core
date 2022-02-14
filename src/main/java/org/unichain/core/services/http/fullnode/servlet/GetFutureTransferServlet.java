package org.unichain.core.services.http.fullnode.servlet;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unichain.core.Wallet;
import org.unichain.core.services.http.utils.JsonFormat;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Protocol.FuturePack;
import org.unichain.protos.Protocol.FutureQuery;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Collectors;

@Component
@Slf4j(topic = "API")
public class GetFutureTransferServlet extends HttpServlet {
  @Autowired
  private Wallet wallet;

  private String convertOutput(FuturePack futurePack) {
      JSONObject tokenPoolJson = JSONObject.parseObject(JsonFormat.printToString(futurePack, false));
      return tokenPoolJson.toJSONString();
  }

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
      doPost(request, response);
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      var filter = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(filter);
      var visible = Util.getVisiblePost(filter);
      var build = FutureQuery.newBuilder();
      JsonFormat.merge(filter, build, visible);
      FutureQuery query = build.build();
      FuturePack reply = wallet.getFuture(query);

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
      try {
        logger.error("Exception: {}", e.getMessage());
        response.getWriter().println(Util.printErrorMsg(e));
      } catch (IOException ioe) {
        logger.debug("IOException: {}", ioe.getMessage());
      }
    }
  }
}
