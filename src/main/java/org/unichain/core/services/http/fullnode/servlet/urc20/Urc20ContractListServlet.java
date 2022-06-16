package org.unichain.core.services.http.fullnode.servlet.urc20;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unichain.common.utils.Utils;
import org.unichain.core.actuator.urc20.ext.Urc20;
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
public class Urc20ContractListServlet extends HttpServlet {
  @Autowired
  private Urc20 urc20;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    doPost(request, response);
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      var tokenFilter = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(tokenFilter);
      var visible = Util.getVisiblePost(tokenFilter);
      var builder = Protocol.Urc20ContractQuery.newBuilder();
      JsonFormat.merge(tokenFilter, builder, visible);
      var query = builder.build();
      var reply = urc20.contractList(query);
      if (reply != null) {
        response.getWriter().println(visible ? JsonFormat.printToString(reply, true) : convertOutput(reply));
      } else {
        response.getWriter().println("{}");
      }
    } catch (Exception e) {
      try {
        logger.error(e.getMessage(), e);
        response.getWriter().println(Util.printErrorMsg(e));
      } catch (IOException ioe) {
        logger.debug("IOException: {}", ioe.getMessage());
      }
    }
  }

  private String convertOutput(Contract.Urc20ContractPage page) {
    var pageJson = new JSONObject();
    pageJson.put("page_size", page.getPageSize());
    pageJson.put("page_index", page.getPageIndex());
    pageJson.put("total", page.getTotal());
    var contracts = new JSONArray();
    for(var item : page.getContractsList()){
      var itemJson = JSONObject.parseObject(JsonFormat.printToString(item, false));
      var start_time = item.getStartTime();
      var end_time = item.getEndTime();
      var lastOpTime = item.getLatestOperationTime();
      itemJson.put("start_time", Utils.formatDateTimeLong(start_time));
      itemJson.put("end_time", Utils.formatDateTimeLong(end_time));
      itemJson.put("latest_operation_time", Utils.formatDateTimeLong(lastOpTime));
      contracts.add(itemJson);
    }
    pageJson.put("contracts", contracts);
    return pageJson.toJSONString();
  }
}
