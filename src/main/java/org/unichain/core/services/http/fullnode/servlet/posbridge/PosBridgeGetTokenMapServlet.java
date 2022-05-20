package org.unichain.core.services.http.fullnode.servlet.posbridge;

import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unichain.core.Wallet;
import org.unichain.core.services.http.utils.JsonFormat;
import org.unichain.core.services.http.utils.Util;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;


@Component
@Slf4j(topic = "API")
public class PosBridgeGetTokenMapServlet extends HttpServlet {

  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      boolean visible = Util.getVisible(request);
      var reply = wallet.getPosBridgeTokenMap();
      if (reply != null) {
        response.getWriter().println(JsonFormat.printToString(reply, visible));
      } else {
        response.getWriter().println("{}");
      }
    } catch (Exception e) {
      logger.error("Api error: {} --> ", e.getMessage(), e);
      try {
        response.getWriter().println(Util.printErrorMsg(e));
      } catch (IOException ioe) {
        logger.debug("IOException: {}", ioe.getMessage());
      }
    }
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
//    try {
//      String input = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
//      Util.checkBodySize(input);
//      boolean visible = Util.getVisiblePost(input);
//      var reply = wallet.getPosBridgeTokenMap();
//      if (reply != null) {
//        response.getWriter().println(JsonFormat.printToString(reply, visible));
//      } else {
//        response.getWriter().println("{}");
//      }
//    } catch (Exception e) {
//      logger.debug("Exception: {}", e.getMessage());
//      try {
//        response.getWriter().println(Util.printErrorMsg(e));
//      } catch (IOException ioe) {
//        logger.debug("IOException: {}", ioe.getMessage());
//      }
//    }
  }
}