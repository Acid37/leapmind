package com.treepeople.leapmindtts.controller.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 管理后台视图控制器
 * 提供管理后台页面的访问入口
 */
@Controller
public class AdminViewController {

    /**
     * 管理后台首页
     * 重定向到管理后台的现代化界面
     */
    @GetMapping("/admin")
    public String adminIndex() {
        return "redirect:/admin/index-modern.html";
    }

    /**
     * 管理后台登录页面
     * 直接访问管理后台的现代化界面
     */
    @GetMapping("/admin/login")
    public String adminLogin() {
        return "redirect:/admin/index-modern.html";
    }
}
