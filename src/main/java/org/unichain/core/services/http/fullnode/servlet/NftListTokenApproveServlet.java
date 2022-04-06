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
public class NftListTokenApproveServlet extends HttpServlet {
  @Autowired
  private NftService nftService;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      boolean visible = Util.getVisible(request);
      String address = request.getParameter("owner_address");
      long pageSize = Long.parseLong(request.getParameter("page_size"));
      long pageIndex = Long.parseLong(request.getParameter("page_index"));
      Protocol.NftTokenApproveQuery.Builder build = Protocol.NftTokenApproveQuery.newBuilder();
      JSONObject jsonObject = new JSONObject();
      jsonObject.put("owner_address", address);
      jsonObject.put("page_size", pageSize);
      jsonObject.put("page_index", pageIndex);
      JsonFormat.merge(jsonObject.toJSONString(), build, visible);

      Protocol.NftTokenApproveResult reply = nftService.getApproval(build.build());

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
