package org.unichain.core.services.http.fullnode.servlet.urc721;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unichain.core.actuator.urc721.ext.Urc721;
import org.unichain.core.services.http.utils.JsonFormat;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Protocol;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

//@todo later
@Component
@Slf4j(topic = "API")
public class Urc721GetApprovedForAllServlet extends HttpServlet {

  @Autowired
  private Urc721 urc721;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      var visible = Util.getVisible(request);
      var contractAddr = request.getParameter("address");
      var tokenId = request.getParameter("id");
      var builder = Protocol.Urc721TokenQuery.newBuilder();
      var jsonObject = new JSONObject();
      jsonObject.put("address", contractAddr);
      jsonObject.put("id", tokenId);
      JsonFormat.merge(jsonObject.toJSONString(), builder, visible);

      var reply = urc721.getApproved(builder.build());

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
