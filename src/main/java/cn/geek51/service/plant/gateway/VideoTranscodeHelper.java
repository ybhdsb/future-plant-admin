package cn.geek51.service.plant.gateway;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 网关录像多为 HEVC，Chrome 等浏览器无法播放（时长显示 0:00）。
 * 拉取后转成 H.264 + yuv420p + faststart，供页面 &lt;video&gt; 直接播。
 */
public final class VideoTranscodeHelper {

    private VideoTranscodeHelper() {
    }

    /**
     * @return 浏览器可播路径（已是 H.264 的 _web.mp4，或转码失败时退回原文件）
     */
    public static Path ensureBrowserPlayable(Path source, String ffmpegPath) {
        if (source == null || !Files.exists(source)) {
            return source;
        }
        String name = source.getFileName().toString().toLowerCase();
        if (!name.endsWith(".mp4") && !name.endsWith(".mkv") && !name.endsWith(".mov")) {
            return source;
        }
        if (name.endsWith("_web.mp4")) {
            return source;
        }
        Path web = webPathFor(source);
        try {
            if (Files.exists(web) && Files.size(web) > 1024) {
                return web;
            }
            String bin = (ffmpegPath == null || ffmpegPath.trim().isEmpty()) ? "ffmpeg" : ffmpegPath.trim();
            List<String> cmd = new ArrayList<>();
            cmd.add(bin);
            cmd.add("-y");
            cmd.add("-i");
            cmd.add(source.toAbsolutePath().toString());
            cmd.add("-c:v");
            cmd.add("libx264");
            cmd.add("-preset");
            cmd.add("veryfast");
            cmd.add("-crf");
            cmd.add("23");
            cmd.add("-pix_fmt");
            cmd.add("yuv420p");
            // 过宽分辨率压到 1280，加快转码、减小体积
            cmd.add("-vf");
            cmd.add("scale='min(1280,iw)':-2");
            cmd.add("-movflags");
            cmd.add("+faststart");
            cmd.add("-an");
            cmd.add(web.toAbsolutePath().toString());

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder log = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (log.length() < 2000) {
                        log.append(line).append('\n');
                    }
                }
            }
            boolean ok = p.waitFor(180, TimeUnit.SECONDS) && p.exitValue() == 0
                    && Files.exists(web) && Files.size(web) > 1024;
            if (!ok) {
                System.err.println("ffmpeg 转码失败 exit=" + (p.isAlive() ? -1 : p.exitValue())
                        + " src=" + source + "\n" + log);
                if (p.isAlive()) {
                    p.destroyForcibly();
                }
                Files.deleteIfExists(web);
                return source;
            }
            return web;
        } catch (Exception e) {
            System.err.println("ffmpeg 转码异常: " + e.getMessage());
            try {
                Files.deleteIfExists(web);
            } catch (Exception ignore) {
            }
            return source;
        }
    }

    public static Path webPathFor(Path source) {
        String n = source.getFileName().toString();
        int dot = n.lastIndexOf('.');
        String base = dot > 0 ? n.substring(0, dot) : n;
        if (base.endsWith("_web")) {
            return source;
        }
        return source.getParent().resolve(base + "_web.mp4");
    }
}
