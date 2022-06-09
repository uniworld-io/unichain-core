package org.unichain.core.services.http.fullnode.servlet.urc721;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unichain.core.services.http.utils.JsonFormat;
import org.unichain.core.services.http.utils.Util;
import org.unichain.core.actuator.urc721.ext.Urc721;
import org.unichain.protos.Contract;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Collectors;

@Component
@Slf4j(topic = "API")
public class Urc721TransferFromServlet extends HttpServlet {

  @Autowired
  private Urc721 urc721;

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      String contract = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(contract);
      var visible = Util.getVisiblePost(contract);
      var builder = Contract.Urc721TransferFromContract.newBuilder();
      JsonFormat.merge(contract, builder, visible);
      var tx = urc721.transferFrom(builder.build());
      var jsonObject = JSONObject.parseObject(contract);
      tx = Util.setTransactionPermissionId(jsonObject, tx);
      response.getWriter().println(Util.printCreateTransaction(tx, visible));
    } catch (Exception e) {
      try {
        logger.error(e.getMessage(), e);
        response.setStatus(400);
        response.getWriter().println(Util.messageErrorHttp(e));
      } catch (IOException ioe) {
        logger.error("IOException: {}", ioe.getMessage());
      }
    }
  }
}
