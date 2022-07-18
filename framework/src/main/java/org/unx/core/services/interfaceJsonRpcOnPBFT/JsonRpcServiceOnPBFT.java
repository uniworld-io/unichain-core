package org.unx.core.services.interfaceJsonRpcOnPBFT;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.ConnectionLimit;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unx.common.application.Service;
import org.unx.common.parameter.CommonParameter;

@Component
@Slf4j(topic = "API")
public class JsonRpcServiceOnPBFT implements Service {

  private int port = CommonParameter.getInstance().getJsonRpcHttpPBFTPort();

  private Server server;

  @Autowired
  private JsonRpcOnPBFTServlet jsonRpcOnPBFTServlet;

  @Override
  public void init() {
  }

  @Override
  public void init(CommonParameter args) {
  }

  @Override
  public void start() {
    try {
      server = new Server(port);
      ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
      context.setContextPath("/");
      server.setHandler(context);

      context.addServlet(new ServletHolder(jsonRpcOnPBFTServlet), "/jsonrpc");

      int maxHttpConnectNumber = CommonParameter.getInstance().getMaxHttpConnectNumber();
      if (maxHttpConnectNumber > 0) {
        server.addBean(new ConnectionLimit(maxHttpConnectNumber, server));
      }

      server.start();

    } catch (Exception e) {
      logger.debug("IOException: {}", e.getMessage());
    }
  }

  @Override
  public void stop() {
    try {
      server.stop();
    } catch (Exception e) {
      logger.debug("IOException: {}", e.getMessage());
    }
  }
}
