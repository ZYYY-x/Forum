package com.example.controller;

import com.example.api.dto.AvatarBindDTO;
import com.example.api.dto.AvatarBindRequest;
import com.example.api.dto.ProfileDTO;
import com.example.common.observability.BusinessMetrics;
import com.example.entity.dto.Account;
import com.example.entity.dto.AccountDetails;
import com.example.entity.dto.AccountPrivacy;
import com.example.service.AccountDetailsService;
import com.example.service.AccountPrivacyService;
import com.example.service.AccountService;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

/**
 * 供 content 等内部服务调用的用户资料接口。
 *
 * <p>资料在 user-service 内完成隐私过滤，调用方拿不到被隐藏的字段。</p>
 */
@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    @Resource
    AccountService accountService;
    @Resource
    AccountDetailsService detailsService;
    @Resource
    AccountPrivacyService privacyService;
    @Resource
    BusinessMetrics metrics;

    @GetMapping("/{uid}/profile")
    public ProfileDTO profile(@PathVariable("uid") int uid) {
        ProfileDTO profile = buildProfile(uid);
        if (profile == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        return profile;
    }

    @PostMapping("/profiles")
    public List<ProfileDTO> profiles(@RequestBody List<Integer> uids) {
        if (uids == null || uids.isEmpty()) {
            return List.of();
        }
        return uids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(this::buildProfile)
                .filter(Objects::nonNull)
                .toList();
    }

    @PutMapping("/{uid}/avatar")
    public AvatarBindDTO bindAvatar(@PathVariable("uid") int uid,
                                    @RequestBody AvatarBindRequest request) {
        Account account = accountService.findAccountById(uid);
        if (account == null) {
            metrics.increment("forum.user.avatar_update", "result", "failure");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        String oldAvatar = account.getAvatar();
        accountService.update()
                .eq("id", uid)
                .set("avatar", request.avatar())
                .update();
        metrics.increment("forum.user.avatar_update", "result", "success");
        return new AvatarBindDTO(oldAvatar);
    }

    private ProfileDTO buildProfile(int uid) {
        Account account = accountService.findAccountById(uid);
        if (account == null) {
            return null;
        }
        AccountDetails details = detailsService.findAccountDetailsById(uid);
        AccountPrivacy privacy = privacyService.accountPrivacy(uid);
        return new ProfileDTO(
                account.getId(),
                account.getUsername(),
                account.getAvatar(),
                details == null ? null : details.getDesc(),
                details == null || !privacy.isGender() ? null : details.getGender(),
                details == null || !privacy.isQq() ? null : details.getQq(),
                details == null || !privacy.isWx() ? null : details.getWx(),
                details == null || !privacy.isPhone() ? null : details.getPhone(),
                privacy.isEmail() ? account.getEmail() : null
        );
    }
}
