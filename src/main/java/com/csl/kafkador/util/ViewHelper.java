package com.csl.kafkador.util;

import com.csl.kafkador.dto.PageView;
import org.springframework.ui.Model;

import java.util.HashMap;
import java.util.Map;

public class ViewHelper {

    public static final String siteTitle = "Kafkador";
    public static final Integer PAGE_SIZE = 16;

    public static final Map<String, PageView> cache = new HashMap<>();

    public static PageView getBasePageView(){
        return new PageView()
                .setTitle("Page Title");
    }

    public static void setPageTitle( String title, Model model ){
        PageView pageView =  getBasePageView();
        model.addAttribute("page", pageView.setTitle( siteTitle + " | " + title ) );
    }

}
