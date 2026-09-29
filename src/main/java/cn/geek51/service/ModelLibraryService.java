package cn.geek51.service;

import cn.geek51.domain.ModelLibrary;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface ModelLibraryService {
    List<ModelLibrary> listAll(String keyword);

    ModelLibrary getById(Long id);

    ModelLibrary create(String name, String ownerName, String framework, String version,
                        String category, String description, MultipartFile[] files);

    ModelLibrary updateMeta(Long id, String name, String ownerName, String framework,
                            String version, String category, String description);

    void delete(Long id);

    Map<String, Object> toView(ModelLibrary model);

    /** 详情：元数据 + 文件树 + 版本链 + 血缘 */
    Map<String, Object> getDetail(Long id);
}
