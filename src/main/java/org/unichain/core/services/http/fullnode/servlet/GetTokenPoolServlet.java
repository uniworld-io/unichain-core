package org.unichain.core.services.http.fullnode.servlet;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unichain.core.Wallet;
import org.unichain.core.services.http.utils.JsonFormat;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract.CreateTokenContract;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

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
      String assetId = tokenPoolJson.get("asset_issued_ID").toString();
      tokenPoolJson.put("start_time", LocalDateTime.ofInstant(Instant.ofEpochMilli(start_time), TimeZone.getDefault().toZoneId()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
      tokenPoolJson.put("end_time", LocalDateTime.ofInstant(Instant.ofEpochMilli(end_time), TimeZone.getDefault().toZoneId()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
      tokenPoolJson.put("latest_operation_time", LocalDateTime.ofInstant(Instant.ofEpochMilli(lastOpTime), TimeZone.getDefault().toZoneId()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
      return tokenPoolJson.toJSONString();
  }

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      boolean visible = Util.getVisible(request);
      String tokenName = request.getParameter("name");
      CreateTokenContract.Builder build = CreateTokenContract.newBuilder();
      JSONObject jsonObject = new JSONObject();
      jsonObject.put("name", tokenName);
      JsonFormat.merge(jsonObject.toJSONString(), build, visible);

      CreateTokenContract reply = wallet.getTokenPool(build.build());
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

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
  }
}
