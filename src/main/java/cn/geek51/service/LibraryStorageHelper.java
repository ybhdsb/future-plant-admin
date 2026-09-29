package cn.geek51.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 将浏览器选择的本地文件夹内容保存到服务器 uploads 目录。
 */
public final class LibraryStorageHelper {

    private LibraryStorageHelper() {
    }

    public static class SaveResult {
        public final String storagePath;
        public final String folderName;
        public final int fileCount;
        public final long totalSizeBytes;

        public SaveResult(String storagePath, String folderName, int fileCount, long totalSizeBytes) {
            this.storagePath = storagePath;
            this.folderName = folderName;
            this.fileCount = fileCount;
            this.totalSizeBytes = totalSizeBytes;
        }
    }

    public static Path resolveBaseDir(String configuredRoot) {
        String root = configuredRoot == null || configuredRoot.trim().isEmpty()
                ? "uploads"
                : configuredRoot.trim();
        Path path = Paths.get(root);
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir"), root);
        }
        return path;
    }

    public static SaveResult saveFolder(String baseDir, String subType, MultipartFile[] files) throws IOException {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("请选择本地文件夹");
        }
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Path targetRoot = resolveBaseDir(baseDir).resolve(subType).resolve(uuid);
        Files.createDirectories(targetRoot);

        String folderName = null;
        int count = 0;
        long total = 0L;

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String relative = file.getOriginalFilename();
            if (relative == null || relative.trim().isEmpty()) {
                relative = file.getName();
            }
            // webkitdirectory 通常给出 "FolderName/sub/file.ext"
            relative = relative.replace('\\', '/');
            if (folderName == null && relative.contains("/")) {
                folderName = relative.substring(0, relative.indexOf('/'));
            }
            // 去掉首层文件夹名，保留相对结构；若无斜杠则直接存文件名
            String storeRel = relative;
            if (folderName != null && relative.startsWith(folderName + "/")) {
                storeRel = relative.substring(folderName.length() + 1);
            }
            if (storeRel == null || storeRel.trim().isEmpty()) {
                storeRel = "file_" + count;
            }
            Path dest = targetRoot.resolve(storeRel).normalize();
            if (!dest.startsWith(targetRoot)) {
                throw new IllegalArgumentException("非法文件路径: " + relative);
            }
            Files.createDirectories(dest.getParent());
            file.transferTo(dest.toFile());
            count++;
            total += file.getSize();
        }

        if (count == 0) {
            throw new IllegalArgumentException("所选文件夹中没有可上传的文件");
        }
        if (folderName == null) {
            folderName = "folder-" + uuid;
        }
        String storagePath = subType + "/" + uuid;
        return new SaveResult(storagePath, folderName, count, total);
    }

    public static void deleteQuietly(String baseDir, String storagePath) {
        if (storagePath == null || storagePath.trim().isEmpty()) {
            return;
        }
        try {
            Path dir = resolveBaseDir(baseDir).resolve(storagePath);
            if (!Files.exists(dir)) {
                return;
            }
            Files.walk(dir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (Exception ignored) {
        }
    }

    public static Path resolveStorageDir(String configuredRoot, String storagePath) {
        Path base = resolveBaseDir(configuredRoot).toAbsolutePath().normalize();
        Path dir = base.resolve(storagePath == null ? "" : storagePath).normalize();
        if (!dir.startsWith(base)) {
            throw new IllegalArgumentException("非法存储路径: " + storagePath);
        }
        return dir;
    }

    public static SaveResult createEmptyFolder(String baseDir, String subType) throws IOException {
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Path targetRoot = resolveBaseDir(baseDir).resolve(subType).resolve(uuid);
        Files.createDirectories(targetRoot);
        return new SaveResult(subType + "/" + uuid, subType + "-" + uuid, 0, 0L);
    }

    public static void writeUtf8(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    public static SaveResult summarize(String storagePath, String folderName, Path dir) throws IOException {
        int count = 0;
        long total = 0L;
        if (Files.exists(dir)) {
            java.util.List<Path> files = new java.util.ArrayList<Path>();
            Files.walk(dir).filter(p -> Files.isRegularFile(p)).forEach(files::add);
            for (Path file : files) {
                count++;
                total += Files.size(file);
            }
        }
        return new SaveResult(storagePath, folderName, count, total);
    }

    public static void zipDirectory(Path sourceDir, OutputStream out) throws IOException {
        Path root = sourceDir.toAbsolutePath().normalize();
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            throw new IllegalArgumentException("文件目录不存在: " + sourceDir);
        }
        ZipOutputStream zos = new ZipOutputStream(out);
        try {
            java.util.List<Path> files = new java.util.ArrayList<Path>();
            Files.walk(root).filter(p -> Files.isRegularFile(p)).forEach(files::add);
            for (Path file : files) {
                String entryName = root.relativize(file).toString().replace('\\', '/');
                zos.putNextEntry(new ZipEntry(entryName));
                Files.copy(file, zos);
                zos.closeEntry();
            }
            zos.finish();
        } finally {
            zos.close();
        }
    }

    public static String formatSize(Long bytes) {
        if (bytes == null || bytes <= 0) {
            return "0 B";
        }
        double size = bytes.doubleValue();
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int idx = 0;
        while (size >= 1024 && idx < units.length - 1) {
            size /= 1024;
            idx++;
        }
        return String.format(idx == 0 ? "%.0f %s" : "%.2f %s", size, units[idx]);
    }

    public static Path resolveRelativeFile(String configuredRoot, String storagePath, String relativePath) {
        Path root = resolveStorageDir(configuredRoot, storagePath);
        String rel = relativePath == null ? "" : relativePath.replace('\\', '/').replaceAll("^/+", "");
        if (rel.contains("..")) {
            throw new IllegalArgumentException("非法相对路径");
        }
        Path file = root.resolve(rel).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("非法相对路径");
        }
        return file;
    }

    /**
     * 扫描目录，生成文件树、预览图路径、类别分布等统计。
     */
    public static Map<String, Object> inspectStorage(String configuredRoot, String storagePath) {
        Map<String, Object> result = new HashMap<String, Object>();
        List<Map<String, Object>> tree = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> previews = new ArrayList<Map<String, Object>>();
        Map<String, Integer> extCount = new LinkedHashMap<String, Integer>();
        Map<String, Integer> classCount = new LinkedHashMap<String, Integer>();
        int fileCount = 0;
        int imageCount = 0;
        int emptyCount = 0;
        long totalBytes = 0L;
        int maxTree = 80;
        int maxPreview = 12;

        try {
            Path root = resolveStorageDir(configuredRoot, storagePath);
            if (!Files.exists(root) || !Files.isDirectory(root)) {
                result.put("exists", false);
                result.put("fileTree", tree);
                result.put("previews", previews);
                result.put("extensionStats", extCount);
                result.put("classDistribution", classCount);
                result.put("imageCount", 0);
                result.put("emptyFileCount", 0);
                result.put("scannedFileCount", 0);
                result.put("scannedSizeBytes", 0L);
                result.put("scannedSizeText", "0 B");
                return result;
            }

            List<Path> files = new ArrayList<Path>();
            Files.walk(root).filter(p -> Files.isRegularFile(p)).forEach(files::add);
            Collections.sort(files);

            for (Path file : files) {
                fileCount++;
                long size = Files.size(file);
                totalBytes += size;
                if (size == 0) {
                    emptyCount++;
                }
                String rel = root.relativize(file).toString().replace('\\', '/');
                String name = file.getFileName().toString();
                String ext = extensionOf(name);
                if (ext.length() > 0) {
                    Integer c = extCount.get(ext);
                    extCount.put(ext, c == null ? 1 : c + 1);
                }
                if (isImageExt(ext)) {
                    imageCount++;
                    String cls = guessClassFromPath(rel);
                    if (cls != null) {
                        Integer c = classCount.get(cls);
                        classCount.put(cls, c == null ? 1 : c + 1);
                    }
                    if (previews.size() < maxPreview) {
                        Map<String, Object> preview = new HashMap<String, Object>();
                        preview.put("path", rel);
                        preview.put("name", name);
                        preview.put("sizeText", formatSize(size));
                        previews.add(preview);
                    }
                }
                if (tree.size() < maxTree) {
                    Map<String, Object> node = new HashMap<String, Object>();
                    node.put("path", rel);
                    node.put("name", name);
                    node.put("size", size);
                    node.put("sizeText", formatSize(size));
                    node.put("ext", ext);
                    node.put("image", Boolean.valueOf(isImageExt(ext)));
                    tree.add(node);
                }
            }

            result.put("exists", true);
            result.put("truncated", tree.size() >= maxTree);
            result.put("fileTree", tree);
            result.put("previews", previews);
            result.put("extensionStats", extCount);
            result.put("classDistribution", classCount);
            result.put("imageCount", imageCount);
            result.put("emptyFileCount", emptyCount);
            result.put("scannedFileCount", fileCount);
            result.put("scannedSizeBytes", totalBytes);
            result.put("scannedSizeText", formatSize(totalBytes));
            result.put("detectedClassCount", classCount.size());
            return result;
        } catch (Exception e) {
            result.put("exists", false);
            result.put("error", e.getMessage());
            result.put("fileTree", tree);
            result.put("previews", previews);
            result.put("extensionStats", extCount);
            result.put("classDistribution", classCount);
            result.put("imageCount", 0);
            result.put("emptyFileCount", 0);
            result.put("scannedFileCount", 0);
            result.put("scannedSizeBytes", 0L);
            result.put("scannedSizeText", "0 B");
            return result;
        }
    }

    private static String extensionOf(String name) {
        if (name == null) {
            return "";
        }
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) {
            return "";
        }
        return name.substring(idx + 1).toLowerCase();
    }

    private static boolean isImageExt(String ext) {
        return "png".equals(ext) || "jpg".equals(ext) || "jpeg".equals(ext)
                || "gif".equals(ext) || "bmp".equals(ext) || "webp".equals(ext);
    }

    private static String guessClassFromPath(String relativePath) {
        if (relativePath == null) {
            return null;
        }
        String[] parts = relativePath.split("/");
        // 常见结构: train/standing/xxx.png 或 standing/xxx.png
        if (parts.length >= 3) {
            String maybeSplit = parts[0].toLowerCase();
            if ("train".equals(maybeSplit) || "test".equals(maybeSplit)
                    || "val".equals(maybeSplit) || "valid".equals(maybeSplit)) {
                return parts[1];
            }
        }
        if (parts.length >= 2) {
            return parts[0];
        }
        return null;
    }
}
