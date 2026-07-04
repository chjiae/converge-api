package com.github.chjiae.service.service;

import com.github.chjiae.common.exception.BusinessException;
import com.github.chjiae.service.dto.auth.CaptchaResponse;
import com.github.chjiae.service.dto.auth.SendRegisterEmailCodeRequest;
import com.github.chjiae.service.dto.auth.VerifyRegisterEmailCodeRequest;
import com.github.chjiae.service.dto.auth.VerifyRegisterEmailCodeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 注册验证码服务，负责人机校验、邮箱验证码发送频率控制和注册凭据签发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterVerificationService {

    /** 人机验证码 Redis Key 前缀 */
    private static final String CAPTCHA_KEY_PREFIX = "auth:register:captcha:";

    /** 邮箱验证码 Redis Key 前缀 */
    private static final String EMAIL_CODE_KEY_PREFIX = "auth:register:email-code:";

    /** 邮箱发送冷却 Redis Key 前缀 */
    private static final String EMAIL_COOLDOWN_KEY_PREFIX = "auth:register:email-code:cooldown:";

    /** 注册凭据 Redis Key 前缀 */
    private static final String VERIFICATION_TOKEN_KEY_PREFIX = "auth:register:verified:";

    /** 人机验证码有效期 */
    private static final Duration CAPTCHA_TTL = Duration.ofMinutes(5);

    /** 邮箱验证码有效期 */
    private static final Duration EMAIL_CODE_TTL = Duration.ofMinutes(10);

    /** 邮箱验证码发送冷却时间 */
    private static final Duration EMAIL_SEND_COOLDOWN = Duration.ofSeconds(60);

    /** 注册凭据有效期 */
    private static final Duration VERIFICATION_TOKEN_TTL = Duration.ofMinutes(15);

    /** 图形验证码字符集，排除容易混淆的 0/O/1/I */
    private static final String CAPTCHA_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /** 图形验证码长度 */
    private static final int CAPTCHA_LENGTH = 4;

    /** 图形验证码宽度 */
    private static final int CAPTCHA_WIDTH = 140;

    /** 图形验证码高度 */
    private static final int CAPTCHA_HEIGHT = 44;

    /** 随机数生成器 */
    private final SecureRandom secureRandom = new SecureRandom();

    /** Redis 字符串模板 */
    private final StringRedisTemplate redisTemplate;

    /** 邮件发送服务 */
    private final EmailService emailService;

    /** 是否在响应中暴露人机验证码答案，仅允许测试环境开启 */
    @Value("${app.register-verification.expose-captcha-answer:false}")
    private boolean exposeCaptchaAnswer;

    /**
     * 创建人机验证码。
     *
     * @return 人机验证码响应
     */
    public CaptchaResponse createCaptcha() {
        String answer = generateCaptchaText();
        String captchaId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(CAPTCHA_KEY_PREFIX + captchaId, answer.toLowerCase(Locale.ROOT), CAPTCHA_TTL);

        log.info("注册人机验证码已创建，验证码 ID: {}", captchaId);
        return CaptchaResponse.builder()
                .captchaId(captchaId)
                .imageBase64(generateCaptchaImage(answer))
                .answer(exposeCaptchaAnswer ? answer : null)
                .build();
    }

    /**
     * 发送注册邮箱验证码。
     *
     * @param request 发送验证码请求参数
     */
    public void sendEmailCode(SendRegisterEmailCodeRequest request) {
        String email = normalizeEmail(request.getEmail());
        validateCaptcha(request.getCaptchaId(), request.getCaptchaAnswer());
        ensureEmailCooldown(email);

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        redisTemplate.opsForValue().set(EMAIL_CODE_KEY_PREFIX + email, code, EMAIL_CODE_TTL);
        emailService.sendRegisterVerificationCodeEmail(email, code, EMAIL_CODE_TTL.toMinutes());

        log.info("注册邮箱验证码已发送，邮箱: {}", email);
    }

    /**
     * 校验注册邮箱验证码并签发注册凭据。
     *
     * @param request 校验验证码请求参数
     * @return 注册凭据响应
     */
    public VerifyRegisterEmailCodeResponse verifyEmailCode(VerifyRegisterEmailCodeRequest request) {
        String email = normalizeEmail(request.getEmail());
        String key = EMAIL_CODE_KEY_PREFIX + email;
        String expectedCode = redisTemplate.opsForValue().get(key);
        if (expectedCode == null || !expectedCode.equals(request.getCode())) {
            log.warn("注册邮箱验证码校验失败，邮箱: {}", email);
            throw new BusinessException(400, "邮箱验证码错误或已过期");
        }

        redisTemplate.delete(key);
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(VERIFICATION_TOKEN_KEY_PREFIX + token, email, VERIFICATION_TOKEN_TTL);
        log.info("注册邮箱验证码校验成功，邮箱: {}", email);

        return VerifyRegisterEmailCodeResponse.builder()
                .verificationToken(token)
                .build();
    }

    /**
     * 校验并消费注册凭据。
     *
     * @param email             注册邮箱
     * @param verificationToken 注册凭据
     */
    public void consumeRegistrationToken(String email, String verificationToken) {
        if (verificationToken == null || verificationToken.isBlank()) {
            log.warn("注册失败，缺少邮箱验证凭据，邮箱: {}", email);
            throw new BusinessException(400, "请先完成邮箱验证");
        }

        String key = VERIFICATION_TOKEN_KEY_PREFIX + verificationToken;
        String verifiedEmail = redisTemplate.opsForValue().get(key);
        if (verifiedEmail == null || !verifiedEmail.equals(normalizeEmail(email))) {
            log.warn("注册失败，邮箱验证凭据无效，邮箱: {}", email);
            throw new BusinessException(400, "邮箱验证已失效，请重新验证");
        }

        redisTemplate.delete(key);
    }

    /**
     * 校验人机验证码。
     *
     * 校验成功后立即删除，防止同一个人机验证码被重复利用。
     *
     * @param captchaId     验证码 ID
     * @param captchaAnswer 用户答案
     */
    private void validateCaptcha(String captchaId, String captchaAnswer) {
        String key = CAPTCHA_KEY_PREFIX + captchaId;
        String expectedAnswer = redisTemplate.opsForValue().get(key);
        String actualAnswer = captchaAnswer.trim().toLowerCase(Locale.ROOT);
        if (expectedAnswer == null || !expectedAnswer.equals(actualAnswer)) {
            log.warn("注册人机验证码校验失败，验证码 ID: {}", captchaId);
            throw new BusinessException(400, "人机验证码错误或已过期");
        }
        redisTemplate.delete(key);
    }

    /**
     * 确保邮箱不在 60 秒发送冷却期内。
     *
     * @param email 邮箱地址
     */
    private void ensureEmailCooldown(String email) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(EMAIL_COOLDOWN_KEY_PREFIX + email, "1",
                        EMAIL_SEND_COOLDOWN.toSeconds(), TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(acquired)) {
            log.warn("注册邮箱验证码发送过于频繁，邮箱: {}", email);
            throw new BusinessException(429, "验证码发送过于频繁，请 60 秒后再试");
        }
    }

    /**
     * 规范化邮箱地址。
     *
     * @param email 原始邮箱
     * @return 去除首尾空格并转小写后的邮箱
     */
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 生成图形验证码文本。
     *
     * @return 图形验证码文本
     */
    private String generateCaptchaText() {
        StringBuilder builder = new StringBuilder(CAPTCHA_LENGTH);
        for (int i = 0; i < CAPTCHA_LENGTH; i++) {
            builder.append(CAPTCHA_CHARS.charAt(secureRandom.nextInt(CAPTCHA_CHARS.length())));
        }
        return builder.toString();
    }

    /**
     * 生成带干扰线和噪点的验证码图片。
     *
     * @param text 验证码文本
     * @return data URL 格式的 PNG 图片
     */
    private String generateCaptchaImage(String text) {
        BufferedImage image = new BufferedImage(CAPTCHA_WIDTH, CAPTCHA_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(245, 247, 250));
            graphics.fillRect(0, 0, CAPTCHA_WIDTH, CAPTCHA_HEIGHT);

            drawNoiseLines(graphics);
            drawNoiseDots(graphics);
            drawCaptchaText(graphics, text);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "png", outputStream);
            return "data:image/png;base64,"
                    + Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            log.error("注册图形验证码生成失败", e);
            throw new BusinessException(500, "图形验证码生成失败");
        } finally {
            graphics.dispose();
        }
    }

    /**
     * 绘制图片干扰线。
     *
     * @param graphics 画布对象
     */
    private void drawNoiseLines(Graphics2D graphics) {
        graphics.setStroke(new BasicStroke(1.2F));
        for (int i = 0; i < 8; i++) {
            graphics.setColor(randomColor(90, 180));
            int x1 = secureRandom.nextInt(CAPTCHA_WIDTH);
            int y1 = secureRandom.nextInt(CAPTCHA_HEIGHT);
            int x2 = secureRandom.nextInt(CAPTCHA_WIDTH);
            int y2 = secureRandom.nextInt(CAPTCHA_HEIGHT);
            graphics.drawLine(x1, y1, x2, y2);
        }
    }

    /**
     * 绘制图片噪点。
     *
     * @param graphics 画布对象
     */
    private void drawNoiseDots(Graphics2D graphics) {
        for (int i = 0; i < 70; i++) {
            graphics.setColor(randomColor(120, 220));
            graphics.fillRect(secureRandom.nextInt(CAPTCHA_WIDTH), secureRandom.nextInt(CAPTCHA_HEIGHT), 1, 1);
        }
    }

    /**
     * 绘制带旋转扰动的验证码文字。
     *
     * @param graphics 画布对象
     * @param text     验证码文本
     */
    private void drawCaptchaText(Graphics2D graphics, String text) {
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        for (int i = 0; i < text.length(); i++) {
            AffineTransform originalTransform = graphics.getTransform();
            double angle = Math.toRadians(secureRandom.nextInt(31) - 15);
            int x = 22 + i * 27;
            int y = 31 + secureRandom.nextInt(5) - 2;
            graphics.rotate(angle, x, y);
            graphics.setColor(randomColor(20, 110));
            graphics.drawString(String.valueOf(text.charAt(i)), x, y);
            graphics.setTransform(originalTransform);
        }
    }

    /**
     * 生成指定亮度区间内的随机颜色。
     *
     * @param min 最小颜色值
     * @param max 最大颜色值
     * @return 随机颜色
     */
    private Color randomColor(int min, int max) {
        int bound = max - min + 1;
        return new Color(
                min + secureRandom.nextInt(bound),
                min + secureRandom.nextInt(bound),
                min + secureRandom.nextInt(bound)
        );
    }
}
