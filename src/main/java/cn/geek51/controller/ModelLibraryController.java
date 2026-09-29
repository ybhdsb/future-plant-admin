package cn.geek51.controller;

import cn.geek51.domain.ModelLibrary;
import cn.geek51.service.ModelLibraryService;
import cn.geek51.util.ResponseUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/models")
public class ModelLibraryController {

    @Autowired
    private ModelLibraryService modelLibraryService;

    @GetMapping
    public Object list(@RequestParam(value = "keyword", required = false) String keyword) {
        List<ModelLibrary> list = modelLibraryService.listAll(keyword);
        List<Map<String, Object>> data = new ArrayList<>();
        for (ModelLibrary item : list) {
            data.add(modelLibraryService.toView(item));
        }
        Map<Object, Object> extra = new HashMap<>();
        extra.put("size", data.size());
        return ResponseUtil.general_response(data, extra);
    }

    @GetMapping("/{id}")
    public Object detail(@PathVariable("id") Long id) {
        Map<String, Object> detail = modelLibraryService.getDetail(id);
        if (detail == null) {
            return ResponseUtil.general_response(402, "模型不存在");
        }
        return ResponseUtil.general_response(detail);
    }

    @PostMapping
    public Object create(@RequestParam("name") String name,
                         @RequestParam("ownerName") String ownerName,
                         @RequestParam(value = "framework", required = false) String framework,
                         @RequestParam(value = "version", required = false) String version,
                         @RequestParam(value = "category", required = false) String category,
                         @RequestParam(value = "description", required = false) String description,
                         @RequestParam("files") MultipartFile[] files) {
        try {
            ModelLibrary saved = modelLibraryService.create(name, ownerName, framework, version, category, description, files);
            return ResponseUtil.general_response("添加成功", modelLibraryService.toView(saved));
        } catch (IllegalArgumentException e) {
            return ResponseUtil.general_response(402, e.getMessage());
        } catch (Exception e) {
            return ResponseUtil.general_response(405, "添加失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Object update(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        try {
            ModelLibrary updated = modelLibraryService.updateMeta(
                    id,
                    asString(body.get("name")),
                    asString(body.get("ownerName")),
                    asString(body.get("framework")),
                    asString(body.get("version")),
                    asString(body.get("category")),
                    asString(body.get("description"))
            );
            return ResponseUtil.general_response("更新成功", modelLibraryService.toView(updated));
        } catch (IllegalArgumentException e) {
            return ResponseUtil.general_response(402, e.getMessage());
        } catch (Exception e) {
            return ResponseUtil.general_response(405, "更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Object delete(@PathVariable("id") Long id) {
        try {
            modelLibraryService.delete(id);
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
