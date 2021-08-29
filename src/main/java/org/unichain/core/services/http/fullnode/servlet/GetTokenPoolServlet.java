package org.unichain.core.services.http.fullnode.servlet;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unichain.common.utils.Utils;
import org.unichain.core.Wallet;
import org.unichain.core.services.http.utils.JsonFormat;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract.CreateTokenContract;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Collectors;

@Component
@Slf4j(topic = "API")
public class GetTokenPoolServlet extends HttpServlet {
  @Autowired
  private Wallet wallet;

  private String convertOutput(CreateTokenContract tokenPool) {
      JSONObject tokenPoolJson = JSONObject.parseObject(JsonFormat.printToString(tokenPool, false));
      var start_time = tokenPool.getStartTime();
      var end_time = tokenPool.getEndTime();
      var lastOpTime = tokenPool.getLatestOperationTime();
      tokenPoolJson.put("start_time", Utils.formatDateTimeLong(start_time));
      tokenPoolJson.put("end_time", Utils.formatDateTimeLong(end_time));
      tokenPoolJson.put("latest_operation_time", Utils.formatDateTimeLong(lastOpTime));
      return tokenPoolJson.toJSONString();
  }

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {

  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      String tokenFilter = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(tokenFilter);
      boolean visible = Util.getVisiblePost(tokenFilter);
      CreateTokenContract.Builder build = CreateTokenContract.newBuilder();
      JsonFormat.merge(tokenFilter, build, visible);
      var query = build.build();
      logger.info("getTokenPool --> {}" , query);
        CreateTokenContract reply = wallet.getTokenPool(query);
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
