package org.unichain.core.services.http.fullnode.servlet.urc721;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
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
public class Urc721TokenListServlet extends HttpServlet {

  @Autowired
  private Urc721 urc721;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      boolean visible = Util.getVisible(request);
      String address = request.getParameter("owner_address");
      String contract = request.getParameter("address");
      String ownerType = request.getParameter("owner_type");
      long pageSize = Long.parseLong(request.getParameter("page_size"));
      long pageIndex = Long.parseLong(request.getParameter("page_index"));
      Protocol.Urc721TokenListQuery.Builder build = Protocol.Urc721TokenListQuery.newBuilder();
      JSONObject jsonObject = new JSONObject();
      jsonObject.put("owner_address", address);
      jsonObject.put("address", contract);
      jsonObject.put("owner_type", ownerType);
      jsonObject.put("page_size", pageSize);
      jsonObject.put("page_index", pageIndex);

      JsonFormat.merge(jsonObject.toJSONString(), build, visible);
      Protocol.Urc721TokenPage reply = urc721.listToken(build.build());
      if (reply != null) {
        response.getWriter().println(JsonFormat.printToString(reply, true));
      } else {
        response.getWriter().println("[]");
      }
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      try {
        response.getWriter().println(Util.messageErrorHttp(e));
      } catch (IOException ioe) {
        logger.debug("IOException: {}", ioe.getMessage());
      }
    }
  }
}
