package com.github.chjiae.service.dto.auth;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 用户信息响应，用于令牌响应中携带当前用户基本信息
 */
@Data
@Builder
public class UserInfoResponse {

    /** 用户 ID */
    private Long id;

    /** 用户名 */
    private String username;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 用户类型 */
    private String userType;

    /** 租户 ID */
    private Long tenantId;

    /** 租户名称 */
    private String tenantName;

    /** 角色编码列表 */
    private List<String> roles;
}
