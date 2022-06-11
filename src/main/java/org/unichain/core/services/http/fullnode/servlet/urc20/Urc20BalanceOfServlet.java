package org.unichain.core.services.http.fullnode.servlet.urc20;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unichain.api.GrpcAPI;
import org.unichain.core.actuator.urc20.ext.Urc20;
import org.unichain.core.services.http.utils.JsonFormat;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Protocol;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Collectors;

@Component
@Slf4j(topic = "API")
public class Urc20BalanceOfServlet extends HttpServlet {
    @Autowired
    private Urc20 urc20;

    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        try {
            var visible = Util.getVisible(request);

            var address = request.getParameter("address");
            var owner = request.getParameter("owner_address");

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("address", address);
            jsonObject.put("owner_address", owner);

            var builder = Protocol.Urc20BalanceOfQuery.newBuilder();
            JsonFormat.merge(jsonObject.toJSONString(), builder, visible);

            var reply = urc20.balanceOf(builder.build());
            if (reply != null) {
                response.getWriter().println(JsonFormat.printToString(reply, visible));
            } else {
                response.getWriter().println("{}");
            }
        } catch (Exception e) {
            logger.error("Urc20BalanceOf error: {}", e.getMessage(), e);
            try {
                response.getWriter().println(Util.printErrorMsg(e));
            } catch (IOException ioe) {
                logger.debug("IOException: {}", ioe.getMessage());
            }
        }
    }
}
