package cn.geek51.service;

import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扫码加入一次性邀请码（内存存储，重启后失效）。
 */
@Service
public class DeviceJoinInviteService {

    public static class Invite {
        private final String token;
        private final long expireAt;
        private volatile boolean used;

        public Invite(String token, long expireAt) {
            this.token = token;
            this.expireAt = expireAt;
        }

        public String getToken() {
            return token;
        }

        public long getExpireAt() {
            return expireAt;
        }

        public boolean isUsed() {
            return used;
        }

        public void markUsed() {
            this.used = true;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }

    private final Map<String, Invite> invites = new ConcurrentHashMap<>();

    public Invite create(int ttlMinutes) {
        cleanup();
        int ttl = ttlMinutes <= 0 ? 30 : ttlMinutes;
        String token = UUID.randomUUID().toString().replace("-", "");
        Invite invite = new Invite(token, System.currentTimeMillis() + ttl * 60L * 1000L);
        invites.put(token, invite);
        return invite;
    }

    public Invite get(String token) {
        if (token == null || "".equals(token.trim())) {
            return null;
        }
        Invite invite = invites.get(token.trim());
        if (invite == null) {
            return null;
        }
        if (invite.isExpired() || invite.isUsed()) {
            invites.remove(token.trim());
            return null;
        }
        return invite;
    }

    public boolean consume(String token) {
        Invite invite = get(token);
        if (invite == null) {
            return false;
        }
        invite.markUsed();
        invites.remove(token.trim());
        return true;
    }

    private void cleanup() {
        Iterator<Map.Entry<String, Invite>> iterator = invites.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Invite> entry = iterator.next();
            Invite invite = entry.getValue();
            if (invite.isExpired() || invite.isUsed()) {
                iterator.remove();
            }
        }
    }
}
