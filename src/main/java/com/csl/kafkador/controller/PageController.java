package com.csl.kafkador.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {


    @GetMapping("/")
    public String dashboard(Model model, HttpServletRequest request) {

        return "views/pages/dashboard.html";
    }

}
