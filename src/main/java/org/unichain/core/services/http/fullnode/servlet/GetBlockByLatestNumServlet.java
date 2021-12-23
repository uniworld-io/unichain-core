package org.unichain.core.services.http.fullnode.servlet;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unichain.api.GrpcAPI.BlockList;
import org.unichain.api.GrpcAPI.NumberMessage;
import org.unichain.core.Wallet;
import org.unichain.core.services.http.utils.JsonFormat;
import org.unichain.core.services.http.utils.Util;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Collectors;


@Component
@Slf4j(topic = "API")
public class GetBlockByLatestNumServlet extends HttpServlet {

  @Autowired
  private Wallet wallet;
  private static final long BLOCK_LIMIT_NUM = 100;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      boolean visible = Util.getVisible(request);
      long getNum = Long.parseLong(request.getParameter("num"));
      if (getNum > 0 && getNum < BLOCK_LIMIT_NUM) {
        BlockList reply = wallet.getBlockByLatestNum(getNum);
        if (reply != null) {
          response.getWriter().println(Util.printBlockList(reply, visible));
          return;
        }
      }
      response.getWriter().println("{}");
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
    try {
      String input = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(input);
      boolean visible = Util.getVisiblePost(input);
      NumberMessage.Builder build = NumberMessage.newBuilder();
      JsonFormat.merge(input, build, visible);
      long getNum = build.getNum();
      if (getNum > 0 && getNum < BLOCK_LIMIT_NUM) {
        BlockList reply = wallet.getBlockByLatestNum(getNum);
        if (reply != null) {
          response.getWriter().println(Util.printBlockList(reply, visible));
          return;
        }
      }
      response.getWriter().println("{}");
    } catch (Exception e) {
      logger.error("Api error: {} --> ", e.getMessage(), e);
      try {
        response.getWriter().println(Util.printErrorMsg(e));
      } catch (IOException ioe) {
        logger.debug("IOException: {}", ioe.getMessage());
      }
    }
  }
}