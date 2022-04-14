package org.unichain.core.services.http.fullnode.servlet;

import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unichain.core.Wallet;
import org.unichain.core.services.http.utils.Util;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;


@Component
@Slf4j(topic = "API")
public class ShowFutureDealServlet extends HttpServlet {

  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    doPost(request, response);
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      String ret = "futureDeals: \n";
      for(var item : wallet.listAllFutureDeals())
      {
        ret += item.toString() + "\n";
      }
      ret = "TokenFutureDeals: \n";
      for(var item : wallet.listAllFutureTokenStore()){
        ret += item.toString() + "\n";
      }
      response.getWriter().println(ret);
    } catch (Exception e) {
      try {
        logger.error("Api error: {} --> ", e.getMessage(), e);
        response.getWriter().println(Util.printErrorMsg(e));
      } catch (IOException ioe) {
        logger.debug("IOException: {}", ioe.getMessage());
      }
    }
  }
}
