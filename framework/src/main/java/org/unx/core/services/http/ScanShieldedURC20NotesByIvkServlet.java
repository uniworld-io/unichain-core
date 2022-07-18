package org.unx.core.services.http;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unx.api.GrpcAPI;
import org.unx.api.GrpcAPI.IvkDecryptURC20Parameters;
import org.unx.common.utils.ByteArray;
import org.unx.core.Wallet;

@Component
@Slf4j(topic = "API")
public class ScanShieldedURC20NotesByIvkServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  public static String convertOutput(GrpcAPI.DecryptNotesURC20 notes, boolean visible) {
    String resultString = JsonFormat.printToString(notes, visible);
    if (notes.getNoteTxsCount() == 0) {
      return resultString;
    } else {
      JSONObject jsonNotes = JSONObject.parseObject(resultString);
      JSONArray array = jsonNotes.getJSONArray("noteTxs");
      for (int index = 0; index < array.size(); index++) {
        JSONObject item = array.getJSONObject(index);
        item.put("index", notes.getNoteTxs(index).getIndex()); // Avoid automatically ignoring 0
      }
      return jsonNotes.toJSONString();
    }
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      PostParams params = PostParams.getPostParams(request);
      IvkDecryptURC20Parameters.Builder ivkDecryptURC20Parameters = IvkDecryptURC20Parameters
          .newBuilder();
      JsonFormat.merge(params.getParams(), ivkDecryptURC20Parameters, params.isVisible());

      GrpcAPI.DecryptNotesURC20 notes = wallet
          .scanShieldedURC20NotesByIvk(ivkDecryptURC20Parameters.getStartBlockIndex(),
              ivkDecryptURC20Parameters.getEndBlockIndex(),
              ivkDecryptURC20Parameters.getShieldedURC20ContractAddress().toByteArray(),
              ivkDecryptURC20Parameters.getIvk().toByteArray(),
              ivkDecryptURC20Parameters.getAk().toByteArray(),
              ivkDecryptURC20Parameters.getNk().toByteArray(),
              ivkDecryptURC20Parameters.getEventsList());
      response.getWriter().println(convertOutput(notes, params.isVisible()));
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      boolean visible = Util.getVisible(request);
      long startNum = Long.parseLong(request.getParameter("start_block_index"));
      long endNum = Long.parseLong(request.getParameter("end_block_index"));
      String ivk = request.getParameter("ivk");

      String contractAddress = request.getParameter("shielded_URC20_contract_address");
      if (visible) {
        contractAddress = Util.getHexAddress(contractAddress);
      }

      String ak = request.getParameter("ak");
      String nk = request.getParameter("nk");

      GrpcAPI.DecryptNotesURC20 notes = wallet
          .scanShieldedURC20NotesByIvk(startNum, endNum,
              ByteArray.fromHexString(contractAddress), ByteArray.fromHexString(ivk),
              ByteArray.fromHexString(ak), ByteArray.fromHexString(nk), null);
      response.getWriter().println(convertOutput(notes, visible));
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }
}
