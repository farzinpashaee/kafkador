package com.csl.kafkador.dto;

import jakarta.servlet.http.HttpSession;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Request<T> {

    HttpSession httpSession;
    T body;

    public Request(){
    }

    public Request(HttpSession httpSession){
        this.httpSession = httpSession;
    }

    public T getBody() {
        return body;
    }
}
