package org.unichain.core.services.http.fullnode.servlet.urc721;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
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

//@todo urc721 review: confuse naming ?
@Component
@Slf4j(topic = "API")
public class Urc721GetApprovedForAllServlet extends HttpServlet {

  @Autowired
  private Urc721Service urc721Service;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      boolean visible = Util.getVisible(request);
      String address = request.getParameter("owner_address");

      Integer pageIndex = request.getParameter("page_index") == null ? 0 : Integer.parseInt(request.getParameter("page_index"));
      Integer pageSize = request.getParameter("page_size") == null ? 10 : Integer.parseInt(request.getParameter("page_size"));

      Protocol.Urc721TokenApproveAllQuery.Builder build = Protocol.Urc721TokenApproveAllQuery.newBuilder();
      JSONObject jsonObject = new JSONObject();
      jsonObject.put("owner_address", address);
      jsonObject.put("page_index", pageIndex);
      jsonObject.put("page_size", pageSize);
      JsonFormat.merge(jsonObject.toJSONString(), build, visible);

      Protocol.Urc721TokenApproveAllResult reply = urc721Service.getApprovalForAll(build.build());

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
