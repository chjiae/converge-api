package com.github.chjiae.service.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.chjiae.common.enums.ApplicationStatus;
import com.github.chjiae.common.enums.ApplicationType;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 租户注册申请实体，对应 tenant_application 表。
 * 记录租户的注册申请信息，包含申请类型、审核状态等。
 * 该表没有 tenant_id 字段，因此不继承 BaseEntity。
 */
@Data
@TableName("tenant_application")
public class TenantApplication implements Serializable {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 公司/组织名称 */
    private String companyName;

    /** 联系人姓名 */
    private String contactName;

    /** 联系人邮箱 */
    private String contactEmail;

    /** 联系人电话 */
    private String contactPhone;

    /** 用途描述 */
    private String description;

    /** 申请类型：REGISTER（注册充值）/ TRIAL（申请试用） */
    private ApplicationType applicationType;

    /** 申请状态：PENDING / APPROVED / REJECTED */
    private ApplicationStatus status;

    /** 拒绝原因 */
    private String rejectReason;

    /** 审核人 ID（超管 ID） */
    private Long reviewedBy;

    /** 审核时间 */
    private LocalDateTime reviewedAt;

    /** 申请时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
