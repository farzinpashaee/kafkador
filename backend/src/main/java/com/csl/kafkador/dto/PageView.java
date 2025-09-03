package com.csl.kafkador.dto;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.ui.Model;

@Data
@Accessors(chain = true)
public class PageView {

    private String headTitle;
    private String title;
    private String activeMenu;
    private String icon;

    public PageView() {}

    public PageView(Builder builder) {
        this.headTitle = builder.headTitle;
        this.title = builder.title;
        this.icon = builder.icon;
        this.activeMenu = builder.activeMenu;
    }

    // Builder class
    public static class Builder {
        private String headTitle;
        private String title;
        private String icon;
        private String activeMenu;

        public Builder title(String title) {
            this.title = title;
            this.headTitle = title;
            return this;
        }

        public Builder activeMenu(String activeMenu) {
            this.activeMenu = activeMenu;
            return this;
        }

        public Builder icon(String icon) {
            this.icon = icon;
            return this;
        }

        public PageView build(Model model) {
            PageView pageView = new PageView(this);
            model.addAttribute("page",pageView);
            return pageView;
        }
    }

}
