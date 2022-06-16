package org.unichain.core.services.http.fullnode.servlet.urc721;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unichain.core.services.http.utils.JsonFormat;
import org.unichain.core.services.http.utils.Util;
import org.unichain.core.actuator.urc721.ext.Urc721;
import org.unichain.protos.Protocol;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@Slf4j(topic = "API")
public class Urc721ContractListServlet extends HttpServlet {

  @Autowired
  private Urc721 urc721;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      var visible = Util.getVisible(request);
      var address = request.getParameter("owner_address");
      var pageSize = Long.parseLong(request.getParameter("page_size"));
      var pageIndex = Long.parseLong(request.getParameter("page_index"));
      var ownerType = request.getParameter("owner_type");

      var jsonObject = new JSONObject();
      jsonObject.put("owner_address", address);
      jsonObject.put("page_size", pageSize);
      jsonObject.put("page_index", pageIndex);
      jsonObject.put("owner_type", StringUtils.isEmpty(ownerType) ? "OWNER" : ownerType);

      var builder = Protocol.Urc721ContractQuery.newBuilder();
      JsonFormat.merge(jsonObject.toJSONString(), builder, visible);

      var reply = urc721.listContract(builder.build());

      if (reply != null) {
        response.getWriter().println(JsonFormat.printToString(reply, true));
      } else {
        response.getWriter().println("[]");
      }
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      try {
        response.setStatus(400);
        response.getWriter().println(Util.messageErrorHttp(e));
      } catch (IOException ioe) {
        logger.debug("IOException: {}", ioe.getMessage());
      }
    }
  }
}
