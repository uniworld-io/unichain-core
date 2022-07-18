package org.unx.core.services.http;

import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unx.api.GrpcAPI.PrivateShieldedURC20Parameters;
import org.unx.api.GrpcAPI.ShieldedURC20Parameters;
import org.unx.core.Wallet;

@Component
@Slf4j(topic = "API")
public class CreateShieldedContractParametersServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      String contract = request.getReader().lines()
          .collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(contract);

      boolean visible = Util.getVisiblePost(contract);
      PrivateShieldedURC20Parameters.Builder build = PrivateShieldedURC20Parameters.newBuilder();
      JsonFormat.merge(contract, build, visible);

      ShieldedURC20Parameters shieldedURC20Parameters = wallet
          .createShieldedContractParameters(build.build());
      response.getWriter().println(JsonFormat.printToString(shieldedURC20Parameters, visible));
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }
}
