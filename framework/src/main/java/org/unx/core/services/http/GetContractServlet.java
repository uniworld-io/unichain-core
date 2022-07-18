package org.unx.core.services.http;

import com.alibaba.fastjson.JSONObject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unx.api.GrpcAPI.BytesMessage;
import org.unx.core.Wallet;
import org.unx.protos.contract.SmartContractOuterClass.SmartContract;


@Component
@Slf4j(topic = "API")
public class GetContractServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      boolean visible = Util.getVisible(request);
      String input = request.getParameter(PostParams.S_VALUE);
      if (visible) {
        input = Util.getHexAddress(input);
      }

      JSONObject jsonObject = new JSONObject();
      jsonObject.put(PostParams.S_VALUE, input);
      BytesMessage.Builder build = BytesMessage.newBuilder();
      JsonFormat.merge(jsonObject.toJSONString(), build, visible);
      SmartContract smartContract = wallet.getContract(build.build());

      if (smartContract == null) {
        response.getWriter().println("{}");
      } else {
        JSONObject jsonSmartContract = JSONObject
            .parseObject(JsonFormat.printToString(smartContract, visible));
        response.getWriter().println(jsonSmartContract.toJSONString());
      }
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      PostParams params = PostParams.getPostParams(request);
      boolean visible = params.isVisible();
      String input = params.getParams();
      if (visible) {
        JSONObject jsonObject = JSONObject.parseObject(input);
        String value = jsonObject.getString(PostParams.S_VALUE);
        jsonObject.put(PostParams.S_VALUE, Util.getHexAddress(value));
        input = jsonObject.toJSONString();
      }

      BytesMessage.Builder build = BytesMessage.newBuilder();
      JsonFormat.merge(input, build, visible);
      SmartContract smartContract = wallet.getContract(build.build());

      if (smartContract == null) {
        response.getWriter().println("{}");
      } else {
        JSONObject jsonSmartContract = JSONObject
            .parseObject(JsonFormat.printToString(smartContract, visible));
        response.getWriter().println(jsonSmartContract.toJSONString());
      }
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }
}
