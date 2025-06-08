package com.baykin.cloud_storage.skydrive.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping({"/", "/{path:[^\\.]+}", "/**/{path:[^\\.]+}"})
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
