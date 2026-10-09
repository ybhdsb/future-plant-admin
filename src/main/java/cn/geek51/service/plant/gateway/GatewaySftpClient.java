package cn.geek51.service.plant.gateway;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 从同事网关主机经 SSH/SFTP 拉取录像等到本机媒体目录。
 */
public final class GatewaySftpClient {

    private GatewaySftpClient() {
    }

    public static Path download(String host, int port, String username, String password,
                                String remotePath, Path localDest, int connectTimeoutMs) throws Exception {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("ssh host empty");
        }
        if (remotePath == null || remotePath.trim().isEmpty()) {
            throw new IllegalArgumentException("remote path empty");
        }
        if (localDest == null) {
            throw new IllegalArgumentException("local dest empty");
        }
        Files.createDirectories(localDest.getParent());

        JSch jsch = new JSch();
        Session session = null;
        ChannelSftp channel = null;
        try {
            session = jsch.getSession(username, host.trim(), port <= 0 ? 22 : port);
            session.setPassword(password == null ? "" : password);
            Properties cfg = new Properties();
            cfg.put("StrictHostKeyChecking", "no");
            session.setConfig(cfg);
            session.connect(Math.max(1000, connectTimeoutMs));

            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(Math.max(1000, connectTimeoutMs));

            String remote = remotePath.trim();
            // 优先 MP4（浏览器可播）；若网关给的是 mkv 则尝试同名 mp4
            String tryFirst = remote;
            String trySecond = null;
            if (remote.toLowerCase().endsWith(".mkv")) {
                tryFirst = remote.substring(0, remote.length() - 4) + ".mp4";
                trySecond = remote;
            }

            Exception last = null;
            for (String candidate : new String[]{tryFirst, trySecond}) {
                if (candidate == null) {
                    continue;
                }
                try (InputStream in = channel.get(candidate)) {
                    Files.copy(in, localDest);
                    return localDest;
                } catch (Exception e) {
                    last = e;
                    if (Files.exists(localDest)) {
                        Files.deleteIfExists(localDest);
                    }
                }
            }
            throw last == null ? new IllegalStateException("sftp download failed") : last;
        } finally {
            if (channel != null) {
                try {
                    channel.disconnect();
                } catch (Exception ignore) {
                }
            }
            if (session != null) {
                try {
                    session.disconnect();
                } catch (Exception ignore) {
                }
            }
        }
    }
}
