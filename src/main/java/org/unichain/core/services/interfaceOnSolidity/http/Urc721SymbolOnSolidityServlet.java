package org.unichain.core.services.interfaceOnSolidity.http;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unichain.core.services.http.fullnode.servlet.urc721.Urc721SymbolServlet;
import org.unichain.core.services.interfaceOnSolidity.WalletOnSolidity;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

//@todo urc721
@Component
@Slf4j(topic = "API")
public class Urc721SymbolOnSolidityServlet extends Urc721SymbolServlet {

  @Autowired
  private WalletOnSolidity walletOnSolidity;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    walletOnSolidity.futureGet(() -> super.doGet(request, response));
  }
}
