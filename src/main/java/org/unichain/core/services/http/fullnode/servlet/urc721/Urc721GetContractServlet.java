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
public class Urc721GetContractServlet extends HttpServlet {
  @Autowired
  private NftService nftService;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      boolean visible = Util.getVisible(request);
      String contract = request.getParameter("contract");
      String address = request.getParameter("address");
      Protocol.NftTemplate.Builder build = Protocol.NftTemplate.newBuilder();
      JSONObject jsonObject = new JSONObject();
      jsonObject.put("contract", contract);
      jsonObject.put("address", address);
      JsonFormat.merge(jsonObject.toJSONString(), build, visible);

      Protocol.NftTemplate reply = nftService.getContract(build.build());

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
