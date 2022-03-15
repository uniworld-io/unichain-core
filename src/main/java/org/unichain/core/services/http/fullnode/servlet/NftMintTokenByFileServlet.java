package org.unichain.core.services.http.fullnode.servlet;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Anh Tran Viet
 * @email tranvietanh190196@gmail.com
 * @created 09:17 3/14/22
 */
@Component
@Slf4j(topic = "API")
public class NftMintTokenByFileServlet extends NftMintTokenServlet {

    //@TODO
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {


        super.doPost(request, response);
    }

    private void validateFile(){

    }
}
