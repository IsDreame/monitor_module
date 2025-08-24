package cn.nebulaedata.cccs.acutor_module.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/")
public class MainController {
    @GetMapping
    public String index() {
        System.out.println(1);
        // 返回 src/main/resources/templates/index.html 模板
        return "index";
    }
}