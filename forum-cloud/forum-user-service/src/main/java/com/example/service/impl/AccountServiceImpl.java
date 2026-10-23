package com.example.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.common.observability.BusinessMetrics;
import com.example.entity.dto.Account;
import com.example.entity.dto.AccountDetails;
import com.example.entity.dto.AccountPrivacy;
import com.example.entity.vo.request.*;
import com.example.mapper.AccountDetailsMapper;
import com.example.mapper.AccountMapper;
import com.example.mapper.AccountPrivacyMapper;
import com.example.service.AccountService;
import com.example.utils.Const;
import com.example.utils.FlowUtils;
import jakarta.annotation.Resource;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
public class AccountServiceImpl extends ServiceImpl<AccountMapper, Account> implements AccountService {

    @Resource
    AmqpTemplate amqpTemplate;
    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Resource
    AccountPrivacyMapper privacyMapper;
    @Resource
    AccountDetailsMapper detailsMapper;
    @Resource
    FlowUtils utils;
    @Resource
    PasswordEncoder encoder;
    @Resource
    BusinessMetrics metrics;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account account = this.findUserByNameOeEmail(username);
        if (account == null)
            throw new UsernameNotFoundException("用户名或密码错误");
        return User
                .withUsername(username)
                .password(account.getPassword())
                .roles(account.getRole())
                .build();
    }
    @Override
    public Account findUserByNameOeEmail(String text){
        return this.query()
                .eq("username",text).or()
                .eq("email",text)
                .one();
    }

    @Override
    public String registerEmailVerifyCode(String type, String email, String ip) {
        synchronized (ip.intern()){
            if (!this.verifyLimit(ip)) {
                metrics.increment("forum.auth.verify_code", "type", type, "result", "failure");
                return "请求频繁!!!";
            }
            Random random=new Random();
            int code= random.nextInt(899999) + 100000;
            Map<String,Object> data = Map.of("type",type,"email",email,"code",code);
            amqpTemplate.convertAndSend("mail",data);
            stringRedisTemplate.opsForValue()
                    .set(Const.VERIFY_EMAIL_DATA+email,String.valueOf(code),3, TimeUnit.MINUTES);
            metrics.increment("forum.auth.verify_code", "type", type, "result", "success");
            return null;
        }
    }

    @Override
    public String registerEmailAccount(EmailRegisterVO emailRegisterVO) {
        String email = emailRegisterVO.getEmail();
        String username = emailRegisterVO.getUsername();
        String key = Const.VERIFY_EMAIL_DATA+email;
        String code = stringRedisTemplate.opsForValue().get(key);
        if (code == null) return failRegister("请先获取验证码");
        if (!code.equals(emailRegisterVO.getCode()))return failRegister("验证码错误，请重新输入");
        if (existsAccountByEmail(email)) return failRegister("此电子邮件已被注册");
        if (existsAccountByUsername(username)) return failRegister("用户名已被注册，请更换后再次尝试");
        String password = encoder.encode(emailRegisterVO.getPassword()) ;
        Account account = new Account(null, username,password,email,"user",null,new Date());
        if (this.save(account)) {
            stringRedisTemplate.delete(key);
            privacyMapper.insert(new AccountPrivacy(account.getId()));
            AccountDetails details = new AccountDetails();
            details.setId(account.getId());
            detailsMapper.insert(details);
            metrics.increment("forum.auth.register", "result", "success");
            return null;
        }else {
            return failRegister("内部错误！");
        }
    }

    private String failRegister(String message) {
        metrics.increment("forum.auth.register", "result", "failure");
        return message;
    }

    @Override
    public String resetConfirm(ConfirmResetVO vo) {
        String email = vo.getEmail();
        String code = stringRedisTemplate.opsForValue().get(Const.VERIFY_EMAIL_DATA+vo.getEmail());
        if (code == null)return "请先获取验证码";
        if (!code.equals(vo.getCode()))return "验证码错误，请重新输入";
        return null;
    }

    @Override
    public String resetAccountPassword(EmailResetVO vo) {
        String email = vo.getEmail();
        String verify = this.resetConfirm(new ConfirmResetVO(email,vo.getCode()));
        if (verify != null ) {
            metrics.increment("forum.auth.reset_password", "result", "failure");
            return verify;
        }
        String password =encoder.encode(vo.getPassword());
        boolean update = this.update().eq("email",email).set("password",password).update();
        if (update){
            stringRedisTemplate.delete(Const.VERIFY_EMAIL_DATA+email);
            metrics.increment("forum.auth.reset_password", "result", "success");
        } else {
            metrics.increment("forum.auth.reset_password", "result", "failure");
        }
        return null;
    }

    @Override
    public Account findAccountById(int id) {
        return this.query().eq("id",id).one();
    }

    @Override
    public String modifyEmail(int id, ModifyEmailVO vo) {
        String email = vo.getEmail();
        String code = getEmailVerifyCode(email);
        if (code == null) return failProfile("请先获取验证码！");
        if (!code.equals(vo.getCode())) return failProfile("验证码错误！");
        this.deleteEmailVerifyCode(email);
        Account account = this.findUserByNameOeEmail(email);
        if (account!=null&& account.getId()!=id)
            return failProfile("该电子邮件已被其他账号绑定，无法进行此操作！");
        this.update()
                .set("email",email)
                .eq("id",id)
                .update();
        metrics.increment("forum.user.profile_update", "result", "success");
        return null;
    }

    private String failProfile(String message) {
        metrics.increment("forum.user.profile_update", "result", "failure");
        return message;
    }

    @Override
    public String changePassword(int id, ChangePasswordVO vo) {
        String password = this.query().eq("id",id).one().getPassword();
        if (!encoder.matches(vo.getPassword(),password)) {
            metrics.increment("forum.user.password_change", "result", "failure");
            return "原密码错误！请重新尝试";
        }
        boolean success = this.update()
                .eq("id",id)
                .set("password",encoder.encode(vo.getNew_password()))
                .update();
        metrics.increment("forum.user.password_change",
                "result", success ? "success" : "failure");
        return success?null:"未知错误！请联系管理员";
    }


    private boolean existsAccountByEmail(String email){
        return this.baseMapper.exists(Wrappers.<Account>query().eq("email",email));
    }
    private boolean existsAccountByUsername(String username){
        return this.baseMapper.exists(Wrappers.<Account>query().eq("username",username));
    }
    private boolean verifyLimit(String ip){
        String key= Const.VERIFY_EMAIL_LIMIT+ip;
        return utils.limitOnceCheck(key,60);
    }



    /**
     * 移除Redis中存储的邮件验证码
     * @param email 电邮
     */
    private void deleteEmailVerifyCode(String email){
        String key = Const.VERIFY_EMAIL_DATA + email;
        stringRedisTemplate.delete(key);
    }

    /**
     * 获取Redis中存储的邮件验证码
     * @param email 电邮
     * @return 验证码
     */
    private String getEmailVerifyCode(String email){
        String key = Const.VERIFY_EMAIL_DATA + email;
        return stringRedisTemplate.opsForValue().get(key);
    }
}
