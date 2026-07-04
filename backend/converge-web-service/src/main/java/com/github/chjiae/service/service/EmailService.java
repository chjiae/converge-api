package com.github.chjiae.service.service;

import com.github.chjiae.service.config.AppMailProperties;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

/**
 * 邮件发送服务
 *
 * 基于 Spring JavaMailSender + Thymeleaf 模板引擎实现 HTML 邮件发送。
 * 邮件发送采用异步方式，避免阻塞主业务流程。
 * 当 JavaMailSender 未配置时（如开发环境未设置 SMTP），自动降级跳过发送。
 */
@Slf4j
@Service
public class EmailService {

    /** 邮件发送器（未配置时为 null，自动降级跳过发送） */
    private final JavaMailSender mailSender;

    /** Thymeleaf 模板引擎 */
    private final TemplateEngine templateEngine;

    /** 应用邮件配置 */
    private final AppMailProperties mailProperties;

    /** 发件人地址（从 spring.mail.username 读取） */
    @Value("${spring.mail.username:}")
    private String fromAddress;

    /**
     * 构造方法
     *
     * JavaMailSender 使用 required=false 注入，当 SMTP 未配置时 bean 不存在也不会导致启动失败。
     *
     * @param mailSender    邮件发送器（可选，未配置时为 null）
     * @param templateEngine Thymeleaf 模板引擎
     * @param mailProperties 应用邮件配置
     */
    public EmailService(@Autowired(required = false) JavaMailSender mailSender,
                        TemplateEngine templateEngine,
                        AppMailProperties mailProperties) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.mailProperties = mailProperties;

        if (mailSender == null) {
            log.warn("JavaMailSender 未配置，邮件发送功能已禁用。请设置 MAIL_USERNAME 和 MAIL_PASSWORD 环境变量启用。");
        }
    }

    /**
     * 异步发送申请审核通过的邮件通知
     *
     * @param contactEmail  收件人邮箱（申请人联系邮箱）
     * @param contactName   收件人姓名
     * @param companyName   公司名称
     * @param adminUsername 管理员用户名
     * @param tenantCode    租户编码
     */
    @Async
    public void sendApplicationApprovedEmail(String contactEmail, String contactName,
                                              String companyName, String adminUsername,
                                              String tenantCode) {
        if (!isMailConfigured()) {
            return;
        }

        log.info("准备发送审核通过邮件，收件人: {}", contactEmail);

        Context context = new Context();
        context.setVariables(Map.of(
                "contactName", contactName,
                "companyName", companyName,
                "adminUsername", adminUsername,
                "tenantCode", tenantCode
        ));

        String htmlContent = templateEngine.process("email/application-approved", context);

        sendHtmlMail(contactEmail, "【Converge API】入驻申请已通过", htmlContent);
    }

    /**
     * 异步发送申请审核拒绝的邮件通知
     *
     * @param contactEmail 收件人邮箱（申请人联系邮箱）
     * @param contactName  收件人姓名
     * @param companyName  公司名称
     * @param rejectReason 拒绝原因
     */
    @Async
    public void sendApplicationRejectedEmail(String contactEmail, String contactName,
                                              String companyName, String rejectReason) {
        if (!isMailConfigured()) {
            return;
        }

        log.info("准备发送审核拒绝邮件，收件人: {}", contactEmail);

        Context context = new Context();
        context.setVariables(Map.of(
                "contactName", contactName,
                "companyName", companyName,
                "rejectReason", rejectReason != null ? rejectReason : "未说明"
        ));

        String htmlContent = templateEngine.process("email/application-rejected", context);

        sendHtmlMail(contactEmail, "【Converge API】入驻申请审核结果", htmlContent);
    }

    /**
     * 异步发送注册邮箱验证码。
     *
     * @param email      收件人邮箱
     * @param code       邮箱验证码
     * @param expireMins 验证码有效分钟数
     */
    @Async
    public void sendRegisterVerificationCodeEmail(String email, String code, long expireMins) {
        if (!isMailConfigured()) {
            log.warn("邮件服务未配置，注册验证码不会真实发送。邮箱: {}，验证码: {}", email, code);
            return;
        }

        log.info("准备发送注册验证码邮件，收件人: {}", email);
        String htmlContent = """
                <div style="font-family:Arial,'Microsoft YaHei',sans-serif;line-height:1.7;color:#111827">
                  <h2>Converge API 注册验证码</h2>
                  <p>你的注册验证码是：</p>
                  <p style="font-size:28px;font-weight:700;letter-spacing:6px">%s</p>
                  <p>验证码 %d 分钟内有效。如非本人操作，请忽略本邮件。</p>
                </div>
                """.formatted(code, expireMins);

        sendHtmlMail(email, "【Converge API】注册验证码", htmlContent);
    }

    /**
     * 检查邮件服务是否已配置
     *
     * @return true 表示已配置可发送邮件，false 表示未配置
     */
    private boolean isMailConfigured() {
        if (mailSender == null) {
            log.warn("JavaMailSender 未注入，跳过邮件发送");
            return false;
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            log.warn("邮件服务未配置（spring.mail.username 为空），跳过邮件发送");
            return false;
        }
        return true;
    }

    /**
     * 发送 HTML 格式邮件
     *
     * @param to      收件人邮箱
     * @param subject 邮件主题
     * @param html    HTML 内容
     */
    private void sendHtmlMail(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, mailProperties.getFromName());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(message);
            log.info("邮件发送成功，收件人: {}，主题: {}", to, subject);
        } catch (Exception e) {
            // 邮件发送失败不应影响主业务流程，仅记录错误日志
            log.error("邮件发送失败，收件人: {}，主题: {}，错误: {}", to, subject, e.getMessage(), e);
        }
    }
}
