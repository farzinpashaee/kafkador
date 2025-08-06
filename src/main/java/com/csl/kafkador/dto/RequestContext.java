package com.csl.kafkador.dto;

import jakarta.servlet.http.HttpSession;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class RequestContext<T> {

    HttpSession httpSession;
    T body;

    public RequestContext(HttpSession httpSession){
        this.httpSession = httpSession;
    }

    public T getBody() {
        return body;
    }
}
