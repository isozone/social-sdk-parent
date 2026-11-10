package cn.net.rjnetwork.xianyu.manager.notify.adapter;

import cn.net.rjnetwork.xianyu.manager.notify.model.NotifyChannel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 邮件通道适配器（SMTP）。config_json 结构：
 * { smtpHost, smtpPort, useSsl, username, password, from, defaultTo }
 * 借助 spring-boot-starter-mail 的 JavaMailSenderImpl 按通道配置动态构建发送器。
 */
@Component
public class EmailChannelAdapter implements ChannelAdapter {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String type() { return "EMAIL"; }

    @Override
    public void send(NotifyChannel channel, String title, String body, List<String> recipients, Map<String, Object> vars) throws Exception {
        JsonNode cfg = mapper.readTree(channel.getConfigJson());
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(text(cfg, "smtpHost"));
        mailSender.setPort(intOf(cfg, "smtpPort", 25));
        if (!isBlank(text(cfg, "username"))) mailSender.setUsername(text(cfg, "username"));
        if (!isBlank(text(cfg, "password"))) mailSender.setPassword(text(cfg, "password"));

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", !isBlank(text(cfg, "username")) ? "true" : "false");
        boolean ssl = boolOf(cfg, "useSsl", false);
        if (ssl) {
            props.put("mail.smtp.socketFactory.port", String.valueOf(mailSender.getPort()));
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.starttls.enable", "false");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "false");
        }
        props.put("mail.debug", "false");

        String from = text(cfg, "from");
        String defaultTo = text(cfg, "defaultTo");

        // 接收人：订阅里指定的 + 通道默认
        List<String> toList = recipients != null && !recipients.isEmpty() ? recipients : List.of();
        if (toList.isEmpty() && defaultTo != null && !defaultTo.isBlank()) {
            toList = List.of(defaultTo.split("[,;\\s]+"));
        }
        if (toList.isEmpty()) {
            throw new IllegalStateException("邮件通道 " + channel.getName() + " 无接收人");
        }

        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
        helper.setFrom(from);
        helper.setTo(toList.toArray(new String[0]));
        helper.setSubject(title);
        // 纯文本发送：通知模板是带换行的纯文本，按 HTML 渲染会丢失全部换行
        helper.setText(body, false);
        mailSender.send(msg);
    }

    private String text(JsonNode n, String k) {
        JsonNode v = n.get(k);
        return v != null && !v.isNull() ? v.asText() : null;
    }

    private int intOf(JsonNode n, String k, int dflt) {
        JsonNode v = n.get(k);
        // 兼容字符串形式的数字端口（如 "465"）：只认 number 会静默回退 25 端口致 SSL 发送必败
        if (v == null || v.isNull()) return dflt;
        return v.isNumber() ? v.asInt() : dflt;
    }

    private boolean boolOf(JsonNode n, String k, boolean dflt) {
        JsonNode v = n.get(k);
        return v != null ? v.asBoolean(dflt) : dflt;
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }
}
