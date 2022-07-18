package org.unx.core.services.http;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unx.api.GrpcAPI;
import org.unx.api.GrpcAPI.NfURC20Parameters;
import org.unx.core.Wallet;

@Component
@Slf4j(topic = "API")
public class IsShieldedURC20ContractNoteSpentServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      PostParams params = PostParams.getPostParams(request);
      NfURC20Parameters.Builder build = NfURC20Parameters.newBuilder();
      JsonFormat.merge(params.getParams(), build, params.isVisible());
      GrpcAPI.NullifierResult result = wallet.isShieldedURC20ContractNoteSpent(build.build());
      response.getWriter().println(JsonFormat.printToString(result, params.isVisible()));
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }
}
