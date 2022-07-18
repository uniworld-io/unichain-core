package org.unx.core.services.jsonrpc;

import com.googlecode.jsonrpc4j.HttpStatusCodeProvider;
import com.googlecode.jsonrpc4j.JsonRpcServer;
import com.googlecode.jsonrpc4j.ProxyUtil;
import java.io.IOException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unx.core.Wallet;
import org.unx.core.db.Manager;
import org.unx.core.services.NodeInfoService;
import org.unx.core.services.http.RateLimiterServlet;

@Component
@Slf4j(topic = "API")
public class JsonRpcServlet extends RateLimiterServlet {

  private JsonRpcServer rpcServer = null;

  @Autowired
  private NodeInfoService nodeInfoService;
  @Autowired
  private Wallet wallet;
  @Autowired
  private Manager manager;

  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);

    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    JsonRpcImpl jsonRpcImpl = new JsonRpcImpl(nodeInfoService, wallet, manager);
    Object compositeService = ProxyUtil.createCompositeServiceProxy(
        cl,
        new Object[] {jsonRpcImpl},
        new Class[] {JsonRpc.class},
        true);

    rpcServer = new JsonRpcServer(compositeService);

    HttpStatusCodeProvider httpStatusCodeProvider = new HttpStatusCodeProvider() {
      @Override
      public int getHttpStatusCode(int resultCode) {
        return 200;
      }

      @Override
      public Integer getJsonRpcCode(int httpStatusCode) {
        return null;
      }
    };
    rpcServer.setHttpStatusCodeProvider(httpStatusCodeProvider);

    rpcServer.setShouldLogInvocationErrors(false);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    rpcServer.handle(req, resp);
  }
}