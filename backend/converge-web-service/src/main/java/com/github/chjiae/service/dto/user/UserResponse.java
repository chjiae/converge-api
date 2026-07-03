package com.github.chjiae.service.dto.user;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户响应 DTO，返回给前端的用户信息。
 */
@Data
@Builder
public class UserResponse {

    /** 用户 ID */
    private Long id;

    /** 用户名 */
    private String username;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 用户状态 */
    private String status;

    /** 用户类型 */
    private String userType;

    /** 租户 ID */
    private Long tenantId;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 角色编码列表 */
    private List<String> roles;
}
