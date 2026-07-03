package com.github.chjiae.service.dto.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建租户申请请求参数。
 * 用于公开提交租户注册/试用申请。
 */
@Data
public class CreateApplicationRequest {

    /** 公司/组织名称（必填） */
    @NotBlank(message = "公司名称不能为空")
    private String companyName;

    /** 联系人姓名（必填） */
    @NotBlank(message = "联系人姓名不能为空")
    private String contactName;

    /** 联系人邮箱（必填，需符合邮箱格式） */
    @NotBlank(message = "联系人邮箱不能为空")
    @Email(message = "联系人邮箱格式不正确")
    private String contactEmail;

    /** 联系人电话（可选） */
    private String contactPhone;

    /** 用途描述（可选） */
    private String description;

    /** 申请类型（必填）：REGISTER（注册充值）/ TRIAL（申请试用） */
    @NotNull(message = "申请类型不能为空")
    private String applicationType;

    /** 管理员用户名（必填） */
    @NotBlank(message = "管理员用户名不能为空")
    private String adminUsername;

    /** 管理员邮箱（必填，需符合邮箱格式） */
    @NotBlank(message = "管理员邮箱不能为空")
    @Email(message = "管理员邮箱格式不正确")
    private String adminEmail;

    /** 管理员密码（必填） */
    @NotBlank(message = "管理员密码不能为空")
    private String adminPassword;
}
