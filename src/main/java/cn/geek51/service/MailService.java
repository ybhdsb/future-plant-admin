package cn.geek51.service;

/**
 * 保留空接口，避免旧基类编译失败；本项目不发送邮件。
 */
public interface MailService {
    default void send(String to, String subject, String content) {
        // no-op
    }
}
