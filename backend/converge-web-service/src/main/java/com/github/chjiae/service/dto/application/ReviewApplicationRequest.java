package com.github.chjiae.service.dto.application;

import lombok.Data;

/**
 * 审核申请请求参数。
 * 审核拒绝时需要提供拒绝原因。
 */
@Data
public class ReviewApplicationRequest {

    /** 拒绝原因（审核拒绝时必填） */
    private String rejectReason;
}
