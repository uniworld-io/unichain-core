package org.unx.core.services.http;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unx.api.GrpcAPI;
import org.unx.api.GrpcAPI.OvkDecryptURC20Parameters;
import org.unx.common.utils.ByteArray;
import org.unx.core.Wallet;

@Component
@Slf4j(topic = "API")
public class ScanShieldedURC20NotesByOvkServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      PostParams params = PostParams.getPostParams(request);
      OvkDecryptURC20Parameters.Builder ovkDecryptURC20Parameters = OvkDecryptURC20Parameters
          .newBuilder();
      JsonFormat.merge(params.getParams(), ovkDecryptURC20Parameters, params.isVisible());

      GrpcAPI.DecryptNotesURC20 notes = wallet
          .scanShieldedURC20NotesByOvk(ovkDecryptURC20Parameters.getStartBlockIndex(),
              ovkDecryptURC20Parameters.getEndBlockIndex(),
              ovkDecryptURC20Parameters.getOvk().toByteArray(),
              ovkDecryptURC20Parameters.getShieldedURC20ContractAddress().toByteArray(),
              ovkDecryptURC20Parameters.getEventsList()
          );
      response.getWriter()
          .println(ScanShieldedURC20NotesByIvkServlet.convertOutput(notes, params.isVisible()));
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      boolean visible = Util.getVisible(request);
      long startBlockIndex = Long.parseLong(request.getParameter("start_block_index"));
      long endBlockIndex = Long.parseLong(request.getParameter("end_block_index"));
      String ovk = request.getParameter("ovk");
      String contractAddress = request.getParameter("shielded_URC20_contract_address");
      if (visible) {
        contractAddress = Util.getHexAddress(contractAddress);
      }
      GrpcAPI.DecryptNotesURC20 notes = wallet
          .scanShieldedURC20NotesByOvk(startBlockIndex, endBlockIndex,
              ByteArray.fromHexString(ovk), ByteArray.fromHexString(contractAddress), null);

      response.getWriter()
          .println(ScanShieldedURC20NotesByIvkServlet.convertOutput(notes, visible));
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }
}
