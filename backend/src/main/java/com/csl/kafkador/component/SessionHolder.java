package com.csl.kafkador.component;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class SessionHolder {

    @Autowired
    private HttpSession session;

    public Object getAttribute( String key ) {
        return session.getAttribute(key);
    }

    public HttpSession getSession() {
        return session;
    }


}
