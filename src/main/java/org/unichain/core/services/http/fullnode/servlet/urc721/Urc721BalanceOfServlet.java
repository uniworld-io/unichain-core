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

@Component
@Slf4j(topic = "API")
public class Urc721BalanceOfServlet extends HttpServlet {

  @Autowired
  private Urc721Service urc721Service;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      boolean visible = Util.getVisible(request);
      String address = request.getParameter("owner_address");
      Protocol.Urc721BalanceOf.Builder build = Protocol.Urc721BalanceOf.newBuilder();
      JSONObject jsonObject = new JSONObject();
      jsonObject.put("owner_address", address);
      JsonFormat.merge(jsonObject.toJSONString(), build, visible);

      Protocol.Urc721BalanceOf reply = urc721Service.balanceOf(build.build());

      if (reply != null) {
        response.getWriter().println(JsonFormat.printToString(reply, visible));
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
}
