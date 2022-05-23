package org.unichain.core.services.http.fullnode.servlet.urc721;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unichain.core.services.http.utils.JsonFormat;
import org.unichain.core.services.http.utils.Util;
import org.unichain.core.services.internal.Urc721Service;
import org.unichain.protos.Protocol;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@Slf4j(topic = "API")
public class Urc721ListContractServlet extends HttpServlet {

  @Autowired
  private Urc721Service urc721Service;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      boolean visible = Util.getVisible(request);
      String address = request.getParameter("owner_address");
      long pageSize = Long.parseLong(request.getParameter("page_size"));
      long pageIndex = Long.parseLong(request.getParameter("page_index"));
      String ownerType = request.getParameter("owner_type");
      Protocol.Urc721ContractQuery.Builder build = Protocol.Urc721ContractQuery.newBuilder();
      JSONObject jsonObject = new JSONObject();
      jsonObject.put("owner_address", address);
      jsonObject.put("page_size", pageSize);
      jsonObject.put("page_index", pageIndex);
      jsonObject.put("owner_type", StringUtils.isEmpty(ownerType) ? "OWNER" : ownerType);
      JsonFormat.merge(jsonObject.toJSONString(), build, visible);

      Protocol.Urc721ContractPage reply = urc721Service.listContract(build.build());

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
