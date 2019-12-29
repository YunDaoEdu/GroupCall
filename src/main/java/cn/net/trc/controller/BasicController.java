package cn.net.trc.controller;

import cn.net.trc.pojo.ReplyMessage;
import cn.net.trc.pojo.UserInfo;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.regex.Pattern;

@Controller
public class BasicController {

    @GetMapping(value = {"/", "/home", "/index"})
    public String indexPage() {
        return "/page/Index.html";
    }

    @GetMapping("/chat")
    public String chatPage() {
        return "/page/Chat.html";
    }

    @PostMapping("/register")
    @ResponseBody
    public ReplyMessage userRegister(String uName, String uRoom, HttpServletRequest request) {
        ReplyMessage replyMessage = new ReplyMessage();

        HttpSession session = request.getSession();
        Object userObj = session.getAttribute("UserInfo");
        if (userObj != null) {
            replyMessage.setStatus(200);
            return replyMessage;
        }

        if (uName == null) {
            replyMessage.setStatus(400);
            replyMessage.setInfo("用户名不能为空");
            return replyMessage;
        }

        if (uRoom == null) {
            replyMessage.setStatus(400);
            replyMessage.setInfo("房间号不能为空");
            return replyMessage;
        }

        var reg = "^[a-zA-Z0-9]{1,7}$";

        var matchName = Pattern.matches(reg, uName);
        if (!matchName) {
            replyMessage.setStatus(400);
            replyMessage.setInfo("用户名必须是1～7个字母或数字");
            return replyMessage;
        }

        var matchRoom = Pattern.matches(reg, uRoom);
        if (!matchRoom) {
            replyMessage.setStatus(400);
            replyMessage.setInfo("密码必须是1～7个字母或数字");
            return replyMessage;
        }

        UserInfo userInfo = new UserInfo();
        userInfo.setUserName(uName);
        userInfo.setUserRoom(uRoom);
        session.setAttribute("UserInfo", userInfo);

        replyMessage.setStatus(200);
        return replyMessage;
    }


    @PostMapping("/exit")
    @ResponseBody
    public ReplyMessage userExit(boolean isExit, HttpServletRequest request) {
        ReplyMessage replyMessage = new ReplyMessage();

        if (isExit) {
            HttpSession session = request.getSession();
            session.removeAttribute("UserInfo");

            replyMessage.setStatus(200);
        } else {
            replyMessage.setStatus(400);
        }

        return replyMessage;
    }
}
