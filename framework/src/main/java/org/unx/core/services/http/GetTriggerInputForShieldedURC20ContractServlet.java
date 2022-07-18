package org.unx.core.services.http;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unx.api.GrpcAPI.BytesMessage;
import org.unx.api.GrpcAPI.ShieldedURC20TriggerContractParameters;
import org.unx.core.Wallet;

@Component
@Slf4j(topic = "API")
public class GetTriggerInputForShieldedURC20ContractServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      PostParams params = PostParams.getPostParams(request);
      ShieldedURC20TriggerContractParameters.Builder builder =
          ShieldedURC20TriggerContractParameters
              .newBuilder();
      JsonFormat.merge(params.getParams(), builder, params.isVisible());
      BytesMessage result = wallet.getTriggerInputForShieldedURC20Contract(builder.build());
      response.getWriter().println(JsonFormat.printToString(result, params.isVisible()));
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }
}
