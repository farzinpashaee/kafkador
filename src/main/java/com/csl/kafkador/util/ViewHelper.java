package com.csl.kafkador.util;

import com.csl.kafkador.dto.PageView;
import org.springframework.ui.Model;

import java.util.HashMap;
import java.util.Map;

public class ViewHelper {

    public static final String siteTitle = "Kafkador";
    public static final Integer PAGE_SIZE = 16;

    public static String getDocumentationLink( String configEntryName ){
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("https://docs.confluent.io/platform/current/installation/configuration/broker-configs.html#");
        if(configEntryName != null) stringBuilder.append( configEntryName.replace(".","-"));
        return stringBuilder.toString();
    }


}
