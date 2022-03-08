package com.nowcoder.community.service;

import com.nowcoder.community.dao.UserMapper;
import com.nowcoder.community.entity.User;
import com.nowcoder.community.util.CommunityUtil;
import com.nowcoder.community.util.MailClient;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static com.nowcoder.community.util.CommunityConstant.*;

@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;

    @Value("${community.path.domain}")
    private String domain;

    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Autowired
    private TemplateEngine templateEngine;

    @Autowired
    private MailClient mailClient;

    public User findUserById(int id){
        return userMapper.selectById(id);
    }

    public Map<String,Object> register(User user) throws Exception{
        Map<String,Object> map = new HashMap<>();

        //空值处理
        if(user==null){
            throw new IllegalArgumentException("参数不能为空!");
        }
        //用户名为空
        if(StringUtils.isBlank(user.getUsername())){
            map.put("usernameMsg","用户名不能为空");
            return map;
        }
        //密码为空
        if(StringUtils.isBlank(user.getPassword())){
            map.put("passwordMsg","密码不能为空");
            return map;
        }
        //邮箱为空
        if(StringUtils.isBlank(user.getEmail())){
            map.put("emailMsg","邮箱不能为空");
            return map;
        }

        //合法性判定
        if(userMapper.selectByName(user.getUsername())!=null){
            map.put("usernameMsg","该用户名已被注册");
            return map;
        }
        if(userMapper.selectByEmail(user.getEmail())!=null){
            map.put("emailMsg","该邮箱已被注册");
            return map;
        }
        //密码.邮箱.用户名都合法
        //对用户进行注册
        user.setSalt(CommunityUtil.generateUUID().substring(0, 5));
        user.setPassword(CommunityUtil.md5(user.getPassword() + user.getSalt()));
        user.setType(0);
        user.setStatus(0);
        user.setActivationCode(CommunityUtil.generateUUID());
        user.setHeaderUrl(String.format("http://images.nowcoder.com/head/%dt.png", new Random().nextInt(1000)));
        user.setCreateTime(new Date());
        //<insert keyProperties="id">可以在执行完insert语句后,将主键的值赋值到参数的成员属性中
        userMapper.insertUser(user);
        //激活邮件
        String url = domain+contextPath+"/activation/" + user.getId() + "/" + user.getActivationCode();
        Context context = new Context();
        context.setVariable("email",user.getEmail());
        context.setVariable("url",url);
        String process = templateEngine.process("/mail/activation", context);
        mailClient.sendMail(user.getEmail(),"激活账号",process);
        return map;
    }

    public int activation(int userId,String code){
        User user = userMapper.selectById(userId);
        if(user.getStatus()==1){
            return ACTIVATION_REPEAT; //重复注册
        }
        if(user.getActivationCode().equals(code)){
            userMapper.updateStatus(user.getId(),1);
            return ACTIVATION_SUCCESS;
        }
        else{
            return ACTIVATION_FAILURE; //激活码错误
        }
    }
}
