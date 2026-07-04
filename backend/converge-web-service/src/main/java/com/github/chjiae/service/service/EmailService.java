package com.github.chjiae.service.service;

import com.github.chjiae.service.config.AppMailProperties;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    /** 邮件发送器 */
    private final JavaMailSender mailSender;

    /** Thymeleaf 模板引擎 */
    private final TemplateEngine templateEngine;

    /** 应用邮件配置 */
    private final AppMailProperties mailProperties;

    /** 发件人地址（从 spring.mail.username 读取） */
    @Value("${spring.mail.username:}")
    private String fromAddress;

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
        if (fromAddress == null || fromAddress.isBlank()) {
            log.warn("邮件服务未配置（spring.mail.username 为空），跳过发送审核通过通知邮件");
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
        if (fromAddress == null || fromAddress.isBlank()) {
            log.warn("邮件服务未配置（spring.mail.username 为空），跳过发送审核拒绝通知邮件");
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
