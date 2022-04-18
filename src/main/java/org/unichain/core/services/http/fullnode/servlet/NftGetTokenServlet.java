package org.unichain.core.services.http.fullnode.servlet;

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
public class NftGetTokenServlet extends HttpServlet {
  @Autowired
  private NftService nftService;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      boolean visible = Util.getVisible(request);
      String contract = request.getParameter("contract");
      Integer tokenId = Integer.valueOf(request.getParameter("id"));
      Protocol.NftTokenGet.Builder build = Protocol.NftTokenGet.newBuilder();
      JSONObject jsonObject = new JSONObject();
      jsonObject.put("contract", contract);
      jsonObject.put("id", tokenId);
      JsonFormat.merge(jsonObject.toJSONString(), build, visible);

      Protocol.NftTokenGetResult reply = nftService.getToken(build.build());

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
