package com.github.chjiae.service.dto.application;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 申请响应 DTO，返回给前端的申请信息。
 */
@Data
@Builder
public class ApplicationResponse {

    /** 申请 ID */
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

    /** 申请类型：REGISTER / TRIAL */
    private String applicationType;

    /** 申请状态：PENDING / APPROVED / REJECTED */
    private String status;

    /** 拒绝原因 */
    private String rejectReason;

    /** 审核人 ID */
    private Long reviewedBy;

    /** 审核时间 */
    private LocalDateTime reviewedAt;

    /** 申请时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
