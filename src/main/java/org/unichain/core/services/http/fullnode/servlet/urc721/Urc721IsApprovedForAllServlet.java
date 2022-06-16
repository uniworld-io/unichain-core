package org.unichain.core.services.http.fullnode.servlet.urc721;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
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
public class Urc721IsApprovedForAllServlet extends HttpServlet {

  @Autowired
  private Urc721 urc721;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      var visible = Util.getVisible(request);
      var ownerAddr = request.getParameter("owner_address");
      var operator = request.getParameter("operator");
      var contract = request.getParameter("address");
      var jsonObject = new JSONObject();
      jsonObject.put("owner_address", ownerAddr);
      jsonObject.put("operator", operator);
      jsonObject.put("address", contract);

      var builder = Protocol.Urc721IsApprovedForAllQuery.newBuilder();
      JsonFormat.merge(jsonObject.toJSONString(), builder, visible);
      var reply = urc721.isApprovedForAll(builder.build());
      if (reply != null) {
        response.getWriter().println(JsonFormat.printToString(reply, visible));
      } else {
        response.getWriter().println("{}");
      }
    } catch (Exception e) {
      try {
        response.setStatus(400);
        response.getWriter().println(Util.messageErrorHttp(e));
      } catch (IOException ioe) {
        logger.debug("IOException: {}", ioe.getMessage());
      }
    }
  }
}
