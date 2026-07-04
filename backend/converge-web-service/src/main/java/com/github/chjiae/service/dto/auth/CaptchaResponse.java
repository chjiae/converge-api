package com.github.chjiae.service.dto.auth;

import lombok.Builder;
import lombok.Data;

/**
 * 人机验证码响应参数。
 */
@Data
@Builder
public class CaptchaResponse {

    /** 验证码 ID，用于后续提交答案时定位服务端缓存 */
    private String captchaId;

    /** Base64 图片数据，可直接作为 img 标签 src 使用 */
    private String imageBase64;

    /** 测试环境使用的答案，生产环境不返回 */
    private String answer;
}
