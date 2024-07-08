package com.zhaoyu.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhaoyu.dto.LoginFormDTO;
import com.zhaoyu.dto.Result;
import com.zhaoyu.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {



    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sendCode(String phone, HttpSession session);
}
