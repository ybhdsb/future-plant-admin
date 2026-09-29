package cn.geek51.service;

import cn.geek51.domain.DatasetLibrary;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface DatasetLibraryService {
    List<DatasetLibrary> listAll(String keyword, String datasetType);

    DatasetLibrary getById(Long id);

    DatasetLibrary create(String name, String ownerName, String datasetType, String category,
                          String description, Integer sampleCount, MultipartFile[] files);

    DatasetLibrary updateMeta(Long id, String name, String ownerName, String datasetType,
                              String category, String description, Integer sampleCount);

    void delete(Long id);

    Map<String, Object> toView(DatasetLibrary dataset);

    /** 详情：元数据 + 文件树 + 预览 + 版本链 + 血缘 */
    Map<String, Object> getDetail(Long id);
}
