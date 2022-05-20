package org.unichain.core.services.http.fullnode.servlet.urc30;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unichain.common.utils.Utils;
import org.unichain.core.Wallet;
import org.unichain.core.services.http.utils.JsonFormat;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Collectors;

@Component
@Slf4j(topic = "API")
public class Urc30GetTokenPoolServlet extends HttpServlet {
  @Autowired
  private Wallet wallet;

  private String convertOutput(Contract.TokenPage page) {
    JSONObject pageJson = new JSONObject();
    pageJson.put("page_size", page.getPageSize());
    pageJson.put("page_index", page.getPageIndex());
    pageJson.put("total", page.getTotal());
    JSONArray tokens = new JSONArray();
    for(var item : page.getTokensList()){
      JSONObject itemJson = JSONObject.parseObject(JsonFormat.printToString(item, false));
      var start_time = item.getStartTime();
      var end_time = item.getEndTime();
      var lastOpTime = item.getLatestOperationTime();
      itemJson.put("start_time", Utils.formatDateTimeLong(start_time));
      itemJson.put("end_time", Utils.formatDateTimeLong(end_time));
      itemJson.put("latest_operation_time", Utils.formatDateTimeLong(lastOpTime));
      tokens.add(itemJson);
    }
    pageJson.put("tokens", tokens);
    return pageJson.toJSONString();
  }

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
      doPost(request, response);
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      String tokenFilter = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(tokenFilter);
      boolean visible = Util.getVisiblePost(tokenFilter);
      Protocol.TokenPoolQuery.Builder build = Protocol.TokenPoolQuery.newBuilder();
      JsonFormat.merge(tokenFilter, build, visible);
      var query = build.build();
      logger.info("getTokenPool --> {}" , query);
      Contract.TokenPage reply = wallet.getTokenPool(query);
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
      logger.error(e.getMessage(), e);
      try {
        response.getWriter().println(Util.printErrorMsg(e));
      } catch (IOException ioe) {
        logger.debug("IOException: {}", ioe.getMessage());
      }
    }
  }
}
