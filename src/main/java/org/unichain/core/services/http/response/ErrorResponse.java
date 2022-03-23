package org.unichain.core.services.http.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ErrorResponse {
    //@TODO
    private int status;
    private String message;
}
