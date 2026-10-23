package com.example.api.user;

import com.example.api.dto.AvatarBindDTO;
import com.example.api.dto.AvatarBindRequest;
import com.example.api.dto.ProfileDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * user-service 内部接口。
 *
 * <p>url 为空时走 Nacos + LoadBalancer；本地静态模式可设置
 * forum.services.user.url=http://localhost:8082。</p>
 */
@FeignClient(
        name = "forum-user-service",
        url = "${forum.services.user.url:}",
        path = "/internal/users"
)
public interface UserClient {

    @GetMapping("/{uid}/profile")
    ProfileDTO profile(@PathVariable("uid") int uid);

    @PostMapping("/profiles")
    List<ProfileDTO> profiles(@RequestBody List<Integer> uids);

    @PutMapping("/{uid}/avatar")
    AvatarBindDTO bindAvatar(@PathVariable("uid") int uid,
                             @RequestBody AvatarBindRequest request);
}
