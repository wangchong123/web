package com.wangchong.controller;

import com.wangchong.service.IUserService;
import mvc.annotation.Autowird;
import mvc.annotation.Controller;
import mvc.annotation.RequestMapping;
import mvc.annotation.RequestParam;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Controller
@RequestMapping("/index")
public class UserController {

    @Autowird
    private IUserService userService;

    @RequestMapping("/get")
    public void get(@RequestParam(value = "name") String name,HttpServletResponse response){
        String res=userService.get(name);
        out(response,res);
    }


    private void out(HttpServletResponse response, String str) {
        try {
            response.setContentType("application/json;charset=utf-8");
            response.getWriter().print(str);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
