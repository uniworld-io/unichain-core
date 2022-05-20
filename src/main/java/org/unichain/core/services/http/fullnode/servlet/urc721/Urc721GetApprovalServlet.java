package org.unichain.core.services.http.fullnode.servlet.urc721;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unichain.core.services.http.utils.JsonFormat;
import org.unichain.core.services.http.utils.Util;
import org.unichain.core.services.internal.NftService;
import org.unichain.protos.Protocol;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@Slf4j(topic = "API")
public class Urc721GetApprovalServlet extends HttpServlet {

  @Autowired
  private NftService nftService;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      boolean visible = Util.getVisible(request);
      String address = request.getParameter("owner_address");
      String operator = request.getParameter("operator");
      boolean isApproved = Boolean.parseBoolean(request.getParameter("is_approved"));
      Protocol.IsApprovedForAll.Builder build = Protocol.IsApprovedForAll.newBuilder();
      JSONObject jsonObject = new JSONObject();
      jsonObject.put("owner_address", address);
      jsonObject.put("operator", operator);
      jsonObject.put("is_approved", isApproved);
      JsonFormat.merge(jsonObject.toJSONString(), build, visible);

      Protocol.IsApprovedForAll reply = nftService.isApprovalForAll(build.build());

      if (reply != null) {
        response.getWriter().println(JsonFormat.printToString(reply, visible));
      } else {
        response.getWriter().println("{}");
      }
    } catch (Exception e) {
      logger.debug("Exception: {}", e.getMessage());
      try {
        response.setStatus(400);
        response.getWriter().println(Util.messageErrorHttp(e));
      } catch (IOException ioe) {
        logger.debug("IOException: {}", ioe.getMessage());
      }
    }
  }
}
