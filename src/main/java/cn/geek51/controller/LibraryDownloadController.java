package cn.geek51.controller;

import cn.geek51.domain.DatasetLibrary;
import cn.geek51.domain.ModelLibrary;
import cn.geek51.service.DatasetLibraryService;
import cn.geek51.service.LibraryStorageHelper;
import cn.geek51.service.ModelLibraryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletResponse;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 模型/数据集文件下载与预览。联邦服务器与客户端用此地址拉取 zip。
 */
@Controller
@RequestMapping("/library/files")
public class LibraryDownloadController {

    @Autowired
    private ModelLibraryService modelLibraryService;

    @Autowired
    private DatasetLibraryService datasetLibraryService;

    @Value("${library.storage-root:uploads/library}")
    private String storageRoot;

    @GetMapping("/models/{id}.zip")
    public void downloadModel(@PathVariable("id") Long id, HttpServletResponse response) throws Exception {
        ModelLibrary model = modelLibraryService.getById(id);
        if (model == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "模型不存在");
            return;
        }
        writeZip(response, model.getStoragePath(), safeName(model.getName()) + ".zip");
    }

    @GetMapping("/datasets/{id}.zip")
    public void downloadDataset(@PathVariable("id") Long id, HttpServletResponse response) throws Exception {
        DatasetLibrary dataset = datasetLibraryService.getById(id);
        if (dataset == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "数据集不存在");
            return;
        }
        writeZip(response, dataset.getStoragePath(), safeName(dataset.getName()) + ".zip");
    }

    @GetMapping("/models/{id}/preview")
    public void previewModelFile(@PathVariable("id") Long id,
                                 @RequestParam("path") String path,
                                 HttpServletResponse response) throws Exception {
        ModelLibrary model = modelLibraryService.getById(id);
        if (model == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "模型不存在");
            return;
        }
        writePreview(response, model.getStoragePath(), path);
    }

    @GetMapping("/datasets/{id}/preview")
    public void previewDatasetFile(@PathVariable("id") Long id,
                                   @RequestParam("path") String path,
                                   HttpServletResponse response) throws Exception {
        DatasetLibrary dataset = datasetLibraryService.getById(id);
        if (dataset == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "数据集不存在");
            return;
        }
        writePreview(response, dataset.getStoragePath(), path);
    }

    private void writePreview(HttpServletResponse response, String storagePath, String relativePath) throws Exception {
        Path file = LibraryStorageHelper.resolveRelativeFile(storageRoot, storagePath, relativePath);
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "文件不存在");
            return;
        }
        String name = file.getFileName().toString().toLowerCase();
        String contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        if (name.endsWith(".png")) {
            contentType = MediaType.IMAGE_PNG_VALUE;
        } else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            contentType = MediaType.IMAGE_JPEG_VALUE;
        } else if (name.endsWith(".gif")) {
            contentType = MediaType.IMAGE_GIF_VALUE;
        } else if (name.endsWith(".webp")) {
            contentType = "image/webp";
        } else if (name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".csv")
                || name.endsWith(".json") || name.endsWith(".py") || name.endsWith(".yaml")
                || name.endsWith(".yml")) {
            contentType = "text/plain;charset=UTF-8";
        }
        response.setContentType(contentType);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, max-age=120");
        Files.copy(file, response.getOutputStream());
        response.flushBuffer();
    }

    private void writeZip(HttpServletResponse response, String storagePath, String filename) throws Exception {
        Path dir = LibraryStorageHelper.resolveStorageDir(storageRoot, storagePath);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "文件目录不存在");
            return;
        }
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        LibraryStorageHelper.zipDirectory(dir, response.getOutputStream());
        response.flushBuffer();
    }

    private String safeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "library-item";
        }
        return name.trim().replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]", "_");
    }
}
