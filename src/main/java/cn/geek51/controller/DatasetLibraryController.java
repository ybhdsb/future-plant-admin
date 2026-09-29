package cn.geek51.controller;

import cn.geek51.domain.DatasetLibrary;
import cn.geek51.service.DatasetLibraryService;
import cn.geek51.util.ResponseUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/datasets")
public class DatasetLibraryController {

    @Autowired
    private DatasetLibraryService datasetLibraryService;

    @GetMapping
    public Object list(@RequestParam(value = "keyword", required = false) String keyword,
                       @RequestParam(value = "datasetType", required = false) String datasetType) {
        List<DatasetLibrary> list = datasetLibraryService.listAll(keyword, datasetType);
        List<Map<String, Object>> data = new ArrayList<>();
        for (DatasetLibrary item : list) {
            data.add(datasetLibraryService.toView(item));
        }
        Map<Object, Object> extra = new HashMap<>();
        extra.put("size", data.size());
        return ResponseUtil.general_response(data, extra);
    }

    @GetMapping("/{id}")
    public Object detail(@PathVariable("id") Long id) {
        Map<String, Object> detail = datasetLibraryService.getDetail(id);
        if (detail == null) {
            return ResponseUtil.general_response(402, "数据集不存在");
        }
        return ResponseUtil.general_response(detail);
    }

    @PostMapping
    public Object create(@RequestParam("name") String name,
                         @RequestParam("ownerName") String ownerName,
                         @RequestParam(value = "datasetType", required = false) String datasetType,
                         @RequestParam(value = "category", required = false) String category,
                         @RequestParam(value = "description", required = false) String description,
                         @RequestParam(value = "sampleCount", required = false) Integer sampleCount,
                         @RequestParam("files") MultipartFile[] files) {
        try {
            DatasetLibrary saved = datasetLibraryService.create(
                    name, ownerName, datasetType, category, description, sampleCount, files);
            return ResponseUtil.general_response("添加成功", datasetLibraryService.toView(saved));
        } catch (IllegalArgumentException e) {
            return ResponseUtil.general_response(402, e.getMessage());
        } catch (Exception e) {
            return ResponseUtil.general_response(405, "添加失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Object update(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        try {
            Integer sampleCount = null;
            if (body.get("sampleCount") != null && !"".equals(String.valueOf(body.get("sampleCount")).trim())) {
                sampleCount = Integer.parseInt(String.valueOf(body.get("sampleCount")).trim());
            }
            DatasetLibrary updated = datasetLibraryService.updateMeta(
                    id,
                    asString(body.get("name")),
                    asString(body.get("ownerName")),
                    asString(body.get("datasetType")),
                    asString(body.get("category")),
                    asString(body.get("description")),
                    sampleCount
            );
            return ResponseUtil.general_response("更新成功", datasetLibraryService.toView(updated));
        } catch (IllegalArgumentException e) {
            return ResponseUtil.general_response(402, e.getMessage());
        } catch (Exception e) {
            return ResponseUtil.general_response(405, "更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Object delete(@PathVariable("id") Long id) {
        try {
            datasetLibraryService.delete(id);
            return ResponseUtil.general_response("删除成功");
        } catch (IllegalArgumentException e) {
            return ResponseUtil.general_response(402, e.getMessage());
        } catch (Exception e) {
            return ResponseUtil.general_response(405, "删除失败: " + e.getMessage());
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
