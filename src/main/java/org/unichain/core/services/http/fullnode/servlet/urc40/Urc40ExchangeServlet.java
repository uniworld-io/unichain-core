package org.unichain.core.services.http.fullnode.servlet.urc40;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unichain.core.Wallet;
import org.unichain.core.services.http.utils.JsonFormat;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract.Urc40ExchangeContract;
import org.unichain.protos.Protocol.Transaction.Contract.ContractType;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Collectors;

@Component
@Slf4j(topic = "API")
public class Urc40ExchangeServlet extends HttpServlet {

  @Autowired
  private Wallet wallet;

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      var contract = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(contract);
      var visible = Util.getVisiblePost(contract);
      var build = Urc40ExchangeContract.newBuilder();
      JsonFormat.merge(contract, build, visible);
      var tx = wallet.createTransactionCapsule(build.build(), ContractType.Urc40ExchangeContract).getInstance();
      var jsonObject = JSONObject.parseObject(contract);
      tx = Util.setTransactionPermissionId(jsonObject, tx);
      response.getWriter().println(Util.printCreateTransaction(tx, visible));
    } catch (Exception e) {
      try {
        logger.error("Urc40Exchange error: {} --> ", e.getMessage(), e);
        response.getWriter().println(Util.printErrorMsg(e));
      } catch (IOException ioe) {
        logger.debug("IOException: {}", ioe.getMessage());
      }
    }
  }
}