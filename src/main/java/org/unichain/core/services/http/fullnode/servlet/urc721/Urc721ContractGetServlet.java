package org.unichain.core.services.http.fullnode.servlet.urc721;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
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
public class Urc721ContractGetServlet extends HttpServlet {
  @Autowired
  private Urc721Service urc721Service;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      var visible = Util.getVisible(request);
      var address = request.getParameter("address");
      var builder = Protocol.Urc721Contract.newBuilder();
      JSONObject jsonObject = new JSONObject();
      jsonObject.put("address", address);
      JsonFormat.merge(jsonObject.toJSONString(), builder, visible);
      var reply = urc721Service.getContract(builder.build());
      if (reply != null) {
        response.getWriter().println(JsonFormat.printToString(reply, visible));
      } else {
        response.getWriter().println("{}");
      }
    } catch (Exception e) {
      logger.error("Exception: {}", e.getMessage(), e);
      try {
        response.getWriter().println(Util.printErrorMsg(e));
      } catch (IOException ioe) {
        logger.error("IOException: {}", ioe.getMessage(), ioe);
      }
    }
  }
}
