package org.unx.core.services.http;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unx.api.GrpcAPI.PrivateShieldedURC20ParametersWithoutAsk;
import org.unx.api.GrpcAPI.ShieldedURC20Parameters;
import org.unx.core.Wallet;

@Component
@Slf4j(topic = "API")
public class CreateShieldedContractParametersWithoutAskServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      PostParams params = PostParams.getPostParams(request);
      PrivateShieldedURC20ParametersWithoutAsk.Builder build =
          PrivateShieldedURC20ParametersWithoutAsk.newBuilder();
      JsonFormat.merge(params.getParams(), build, params.isVisible());
      ShieldedURC20Parameters shieldedURC20Parameters = wallet
          .createShieldedContractParametersWithoutAsk(build.build());
      response.getWriter().println(JsonFormat
              .printToString(shieldedURC20Parameters, params.isVisible()));
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }
}
