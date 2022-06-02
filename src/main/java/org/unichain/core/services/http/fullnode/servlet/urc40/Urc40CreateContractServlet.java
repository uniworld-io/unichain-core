package org.unichain.core.services.http.fullnode.servlet.urc40;

import com.alibaba.fastjson.JSONObject;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unichain.common.utils.AddressUtil;
import org.unichain.core.actuator.urc40.ext.Urc40;
import org.unichain.core.services.http.utils.JsonFormat;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract.Urc40CreateContract;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Collectors;


@Component
@Slf4j(topic = "API")
public class Urc40CreateContractServlet extends HttpServlet {
  @Autowired
  private Urc40 urc40;

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      var contract = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(contract);
      var visible = Util.getVisiblePost(contract);
      var build = Urc40CreateContract.newBuilder();
      JsonFormat.merge(contract, build, visible);
      /**
       *  generate address
       */
      build.setAddress(ByteString.copyFrom(AddressUtil.generateRandomAddress()));
      var contractCtx = build.build();
      var tx = urc40.createContract(contractCtx);
      var jsonObject = JSONObject.parseObject(contract);
      tx = Util.setTransactionPermissionId(jsonObject, tx);
      response.getWriter().println(Util.printCreateTransaction(tx, visible));
    } catch (Exception e) {
      try {
        logger.error("Urc40CreateContract error: ", e);
        response.getWriter().println(Util.printErrorMsg(e));
      } catch (IOException ioe) {
        logger.error("IOException: {}", ioe.getMessage());
      }
    }
  }
}
