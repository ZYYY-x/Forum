package com.example.api.dto;

/**
 * 内部用户资料 DTO。
 *
 * <p>由 user-service 按隐私设置过滤后返回；content 侧不再直接访问用户表。</p>
 */
public record ProfileDTO(
        Integer id,
        String username,
        String avatar,
        String desc,
        Integer gender,
        String qq,
        String wx,
        String phone,
        String email
) {
}
