package com.example.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.entity.dto.Account;
import com.example.entity.vo.request.*;
import org.springframework.security.core.userdetails.UserDetailsService;

public interface AccountService extends IService<Account>, UserDetailsService {
    Account findUserByNameOeEmail(String text);
    String registerEmailVerifyCode(String type,String email,String ip);
    String registerEmailAccount(EmailRegisterVO emailRegisterVO);
    String resetConfirm(ConfirmResetVO vo);
    String resetAccountPassword(EmailResetVO vo);
    Account findAccountById(int id);
    String modifyEmail(int id , ModifyEmailVO vo);
    String changePassword(int id, ChangePasswordVO vo);
}
