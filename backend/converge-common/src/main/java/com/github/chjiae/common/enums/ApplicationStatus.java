package com.github.chjiae.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 申请状态枚举，用于标识租户注册申请的审核进度
 */
@Getter
@AllArgsConstructor
public enum ApplicationStatus {

    /** 待审核，等待超管或平台运营审核 */
    PENDING("待审核"),
    /** 已通过，审核通过 */
    APPROVED("已通过"),
    /** 已拒绝，审核被拒绝 */
    REJECTED("已拒绝");

    /** 状态描述 */
    private final String description;
}
