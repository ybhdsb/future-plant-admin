package cn.geek51.controller;

import cn.geek51.domain.plant.PlantMediaAsset;
import cn.geek51.service.plant.PlantActuatorCatalog;
import cn.geek51.service.plant.PlantAutomationService;
import cn.geek51.service.plant.PlantCameraHardwareService;
import cn.geek51.service.plant.PlantLedHardwareService;
import cn.geek51.service.plant.PlantLedScheduleService;
import cn.geek51.service.plant.PlantPhenotypeService;
import cn.geek51.service.plant.PlantService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/plant")
public class PlantController {

    private final PlantService plantService;
    private final PlantLedScheduleService ledScheduleService;
    private final PlantAutomationService automationService;
    private final PlantPhenotypeService phenotypeService;
    private final PlantLedHardwareService ledHardwareService;
    private final PlantCameraHardwareService cameraHardwareService;

    public PlantController(PlantService plantService,
                           PlantLedScheduleService ledScheduleService,
                           PlantAutomationService automationService,
                           PlantPhenotypeService phenotypeService,
                           PlantLedHardwareService ledHardwareService,
                           PlantCameraHardwareService cameraHardwareService) {
        this.plantService = plantService;
        this.ledScheduleService = ledScheduleService;
        this.automationService = automationService;
        this.phenotypeService = phenotypeService;
        this.ledHardwareService = ledHardwareService;
        this.cameraHardwareService = cameraHardwareService;
    }

    // ---------- pages ----------

    @GetMapping({"", "/", "/dashboard"})
    public String dashboard() {
        return "plant_dashboard";
    }

    @GetMapping("/control")
    public String control() {
        return "redirect:/plant/control/led";
    }

    @GetMapping("/control/led")
    public String controlLed() {
        return "plant_control_led";
    }

    @GetMapping("/control/pump")
    public String controlPump() {
        return "plant_control_actuators";
    }

    @GetMapping("/control/actuators")
    public String controlActuators() {
        return "plant_control_actuators";
    }

    @GetMapping("/control/camera")
    public String controlCamera() {
        return "plant_control_camera";
    }

    @GetMapping("/control/logs")
    public String controlLogs() {
        return "plant_control_logs";
    }

    @GetMapping("/history")
    public String history() {
        return "plant_history";
    }

    @GetMapping("/history/env")
    public String historyEnv() {
        return "plant_history";
    }

    @GetMapping("/history/phenotype")
    public String historyPhenotype() {
        return "redirect:/plant/phenotype/metrics";
    }

    @GetMapping("/data")
    public String data() {
        return "plant_data";
    }

    @GetMapping({"/phenotype", "/phenotype/"})
    public String phenotype() {
        return "redirect:/plant/phenotype/digital";
    }

    @GetMapping("/phenotype/digital")
    public String phenotypeDigital() {
        return "plant_phenotype_digital";
    }

    @GetMapping("/phenotype/metrics")
    public String phenotypeMetrics() {
        return "plant_phenotype_metrics";
    }

    @GetMapping("/phenotype/media")
    public String phenotypeMedia() {
        return "plant_phenotype_media";
    }

    @GetMapping("/phenotype/jobs")
    public String phenotypeJobs() {
        return "plant_phenotype_jobs";
    }

    // ---------- APIs ----------

    @GetMapping("/api/dashboard")
    @ResponseBody
    public Map<String, Object> apiDashboard(@RequestParam(value = "deviceKey", required = false) String deviceKey,
                                            @RequestParam(value = "mock", required = false) Boolean mock) {
        return ok(plantService.getDashboard(deviceKey, mock));
    }

    @GetMapping("/api/device/{deviceKey}/state")
    @ResponseBody
    public Map<String, Object> apiState(@PathVariable String deviceKey) {
        return ok(plantService.getState(deviceKey));
    }

    @PostMapping("/api/mock")
    @ResponseBody
    public Map<String, Object> apiMock(@RequestBody Map<String, Object> body) {
        String deviceKey = body.get("deviceKey") == null ? null : String.valueOf(body.get("deviceKey"));
        boolean enabled = body.get("enabled") == null || Boolean.parseBoolean(String.valueOf(body.get("enabled")));
        return ok(plantService.setMockEnabled(deviceKey, enabled));
    }

    @PostMapping("/api/telemetry")
    @ResponseBody
    public Map<String, Object> apiIngestTelemetry(@RequestBody String body) {
        try {
            plantService.ingestTelemetryJson(body);
            return ok("accepted");
        } catch (Exception e) {
            return fail(40001, e.getMessage());
        }
    }

    @PostMapping("/api/commands")
    @ResponseBody
    public Map<String, Object> apiCommand(@RequestBody Map<String, Object> body) {
        try {
            return ok(plantService.issueCommand(body));
        } catch (IllegalArgumentException e) {
            return fail(40001, e.getMessage());
        } catch (IllegalStateException e) {
            // 网关/设备侧预期失败：只回消息，不刷整段堆栈
            return fail(50002, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return fail(50002, e.getMessage());
        }
    }

    /** 边缘轮询待执行指令（须写在 {commandId} 之前，避免被当成 id） */
    @GetMapping("/api/commands/pending")
    @ResponseBody
    public Map<String, Object> apiPendingCommands(@RequestParam(value = "deviceKey", required = false) String deviceKey,
                                                  @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return ok(plantService.pollPendingCommands(deviceKey, limit));
    }

    @GetMapping("/api/commands/{commandId}")
    @ResponseBody
    public Map<String, Object> apiGetCommand(@PathVariable String commandId) {
        try {
            Long id = commandId.startsWith("c-")
                    ? Long.parseLong(commandId.substring(2))
                    : Long.parseLong(commandId);
            return ok(plantService.getCommand(id));
        } catch (Exception e) {
            return fail(40001, e.getMessage());
        }
    }

    @GetMapping("/api/commands")
    @ResponseBody
    public Map<String, Object> apiListCommands(@RequestParam(value = "deviceKey", required = false) String deviceKey,
                                               @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return ok(plantService.listCommands(deviceKey, limit));
    }

    @GetMapping("/api/strategy")
    @ResponseBody
    public Map<String, Object> apiGetStrategy(@RequestParam(value = "deviceKey", required = false) String deviceKey,
                                              @RequestParam(value = "strategyType", required = false) String strategyType) {
        return ok(plantService.getSampleStrategy(deviceKey));
    }

    @PostMapping("/api/strategy")
    @ResponseBody
    public Map<String, Object> apiSaveStrategy(@RequestBody Map<String, Object> body) {
        try {
            return ok(plantService.saveSampleStrategy(body));
        } catch (Exception e) {
            return fail(40001, e.getMessage());
        }
    }

    // ---------- LED 硬件（对接文档） ----------

    @GetMapping("/api/led/health")
    @ResponseBody
    public Map<String, Object> apiLedHealth() {
        return ok(ledHardwareService.health());
    }

    @GetMapping("/api/led/state")
    @ResponseBody
    public Map<String, Object> apiLedState(@RequestParam(value = "rackKey", required = false) String rackKey) {
        return ok(ledHardwareService.getState(rackKey));
    }

    @PostMapping("/api/led/set")
    @ResponseBody
    public Map<String, Object> apiLedSet(@RequestBody Map<String, Object> body) {
        try {
            return ok(ledHardwareService.setSingle(body));
        } catch (IllegalArgumentException e) {
            return fail(40001, e.getMessage());
        } catch (IllegalStateException e) {
            return fail(50002, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return fail(50002, e.getMessage());
        }
    }

    @GetMapping("/api/led/read")
    @ResponseBody
    public Map<String, Object> apiLedRead(@RequestParam(value = "rackKey", required = false) String rackKey,
                                          @RequestParam("busAddress") String busAddress) {
        try {
            return ok(ledHardwareService.readSingle(rackKey, busAddress));
        } catch (IllegalStateException e) {
            return fail(50002, e.getMessage());
        } catch (Exception e) {
            return fail(50002, e.getMessage());
        }
    }

    @PostMapping("/api/led/broadcast/set")
    @ResponseBody
    public Map<String, Object> apiLedBroadcast(@RequestBody Map<String, Object> body) {
        try {
            return ok(ledHardwareService.broadcastSet(body));
        } catch (IllegalStateException e) {
            return fail(50002, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return fail(50002, e.getMessage());
        }
    }

    @GetMapping("/api/led/read-all")
    @ResponseBody
    public Map<String, Object> apiLedReadAll(@RequestParam(value = "rackKey", required = false) String rackKey) {
        try {
            return ok(ledHardwareService.readAll(rackKey));
        } catch (IllegalStateException e) {
            return fail(50002, e.getMessage());
        } catch (Exception e) {
            return fail(50002, e.getMessage());
        }
    }

    @PostMapping("/api/led/apply")
    @ResponseBody
    public Map<String, Object> apiLedApply(@RequestBody Map<String, Object> body) {
        try {
            return ok(ledHardwareService.applyUiChannels(body));
        } catch (IllegalStateException e) {
            return fail(50002, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return fail(50002, e.getMessage());
        }
    }

    // ---------- 摄像头硬件（对接文档） ----------

    @GetMapping("/api/camera/status")
    @ResponseBody
    public Map<String, Object> apiCameraStatus(@RequestParam(value = "deviceKey", required = false) String deviceKey) {
        return ok(cameraHardwareService.status(deviceKey));
    }

    @PostMapping("/api/camera/reconnect")
    @ResponseBody
    public Map<String, Object> apiCameraReconnect(@RequestBody(required = false) Map<String, Object> body) {
        try {
            String deviceKey = body == null ? null : String.valueOf(body.get("deviceKey"));
            return ok(cameraHardwareService.reconnect(deviceKey));
        } catch (IllegalStateException e) {
            return fail(50002, e.getMessage());
        } catch (Exception e) {
            return fail(50002, e.getMessage());
        }
    }

    @PostMapping("/api/camera/ptz/move")
    @ResponseBody
    public Map<String, Object> apiCameraPtzMove(@RequestBody Map<String, Object> body) {
        try {
            return ok(cameraHardwareService.ptzMove(body));
        } catch (Exception e) {
            e.printStackTrace();
            return fail(50002, e.getMessage());
        }
    }

    @PostMapping("/api/camera/ptz/stop")
    @ResponseBody
    public Map<String, Object> apiCameraPtzStop(@RequestBody(required = false) Map<String, Object> body) {
        try {
            String deviceKey = body == null ? null : String.valueOf(body.get("deviceKey"));
            return ok(cameraHardwareService.ptzStop(deviceKey));
        } catch (Exception e) {
            return fail(50002, e.getMessage());
        }
    }

    @PostMapping("/api/camera/recording/start")
    @ResponseBody
    public Map<String, Object> apiCameraRecStart(@RequestBody(required = false) Map<String, Object> body) {
        try {
            String deviceKey = body == null ? null : String.valueOf(body.get("deviceKey"));
            return ok(cameraHardwareService.recordingStart(deviceKey));
        } catch (Exception e) {
            return fail(50002, e.getMessage());
        }
    }

    @PostMapping("/api/camera/recording/stop")
    @ResponseBody
    public Map<String, Object> apiCameraRecStop(@RequestBody(required = false) Map<String, Object> body) {
        try {
            String deviceKey = body == null ? null : String.valueOf(body.get("deviceKey"));
            return ok(cameraHardwareService.recordingStop(deviceKey));
        } catch (Exception e) {
            return fail(50002, e.getMessage());
        }
    }

    @GetMapping("/api/camera/recording/status")
    @ResponseBody
    public Map<String, Object> apiCameraRecStatus(@RequestParam(value = "deviceKey", required = false) String deviceKey) {
        try {
            return ok(cameraHardwareService.recordingStatus(deviceKey));
        } catch (Exception e) {
            return fail(50002, e.getMessage());
        }
    }

    @PostMapping("/api/camera/photos/capture")
    @ResponseBody
    public Map<String, Object> apiCameraCapture(@RequestBody(required = false) Map<String, Object> body) {
        try {
            String deviceKey = body == null ? null : String.valueOf(body.get("deviceKey"));
            return ok(cameraHardwareService.capturePhoto(deviceKey));
        } catch (Exception e) {
            return fail(50002, e.getMessage());
        }
    }

    @PostMapping("/api/camera/photos/schedule/start")
    @ResponseBody
    public Map<String, Object> apiCameraScheduleStart(@RequestBody Map<String, Object> body) {
        try {
            return ok(cameraHardwareService.scheduleStart(body));
        } catch (Exception e) {
            return fail(50002, e.getMessage());
        }
    }

    @PostMapping("/api/camera/photos/schedule/stop")
    @ResponseBody
    public Map<String, Object> apiCameraScheduleStop(@RequestBody(required = false) Map<String, Object> body) {
        try {
            String deviceKey = body == null ? null : String.valueOf(body.get("deviceKey"));
            return ok(cameraHardwareService.scheduleStop(deviceKey));
        } catch (Exception e) {
            return fail(50002, e.getMessage());
        }
    }

    @GetMapping("/api/camera/photos/status")
    @ResponseBody
    public Map<String, Object> apiCameraPhotosStatus(@RequestParam(value = "deviceKey", required = false) String deviceKey) {
        try {
            return ok(cameraHardwareService.photosStatus(deviceKey));
        } catch (Exception e) {
            return fail(50002, e.getMessage());
        }
    }

    @GetMapping("/api/camera/preview/frame")
    public ResponseEntity<byte[]> apiCameraPreviewFrame() {
        try {
            byte[] bytes = cameraHardwareService.previewFrame();
            if (bytes == null || bytes.length == 0) {
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    @GetMapping("/api/led/schedules")
    @ResponseBody
    public Map<String, Object> apiListLedSchedules(@RequestParam(value = "deviceKey", required = false) String deviceKey) {
        return ok(ledScheduleService.list(deviceKey));
    }

    @PostMapping("/api/led/schedules")
    @ResponseBody
    public Map<String, Object> apiSaveLedSchedule(@RequestBody Map<String, Object> body) {
        try {
            return ok(ledScheduleService.save(body));
        } catch (IllegalArgumentException e) {
            return fail(40001, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return fail(50002, e.getMessage());
        }
    }

    @PostMapping("/api/led/schedules/{id}/enabled")
    @ResponseBody
    public Map<String, Object> apiEnableLedSchedule(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            boolean enabled = body.get("enabled") == null || Boolean.parseBoolean(String.valueOf(body.get("enabled")));
            return ok(ledScheduleService.setEnabled(id, enabled));
        } catch (Exception e) {
            return fail(40001, e.getMessage());
        }
    }

    @DeleteMapping("/api/led/schedules/{id}")
    @ResponseBody
    public Map<String, Object> apiDeleteLedSchedule(@PathVariable Long id) {
        try {
            ledScheduleService.delete(id);
            return ok("deleted");
        } catch (Exception e) {
            return fail(40001, e.getMessage());
        }
    }

    @GetMapping("/api/actuators/catalog")
    @ResponseBody
    public Map<String, Object> apiActuatorCatalog(@RequestParam(value = "includeV2", defaultValue = "true") boolean includeV2) {
        return ok(PlantActuatorCatalog.toMaps(includeV2));
    }

    @GetMapping("/api/automation/rules")
    @ResponseBody
    public Map<String, Object> apiListAutomationRules(@RequestParam(value = "deviceKey", required = false) String deviceKey,
                                                      @RequestParam(value = "actuatorId", required = false) String actuatorId) {
        return ok(automationService.list(deviceKey, actuatorId));
    }

    @PostMapping("/api/automation/rules")
    @ResponseBody
    public Map<String, Object> apiSaveAutomationRule(@RequestBody Map<String, Object> body) {
        try {
            return ok(automationService.save(body));
        } catch (IllegalArgumentException e) {
            return fail(40001, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return fail(50002, e.getMessage());
        }
    }

    @PostMapping("/api/automation/rules/{id}/enabled")
    @ResponseBody
    public Map<String, Object> apiEnableAutomationRule(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            boolean enabled = body.get("enabled") == null || Boolean.parseBoolean(String.valueOf(body.get("enabled")));
            return ok(automationService.setEnabled(id, enabled));
        } catch (Exception e) {
            return fail(40001, e.getMessage());
        }
    }

    @DeleteMapping("/api/automation/rules/{id}")
    @ResponseBody
    public Map<String, Object> apiDeleteAutomationRule(@PathVariable Long id) {
        try {
            automationService.delete(id);
            return ok("deleted");
        } catch (Exception e) {
            return fail(40001, e.getMessage());
        }
    }

    @GetMapping("/api/metrics")
    @ResponseBody
    public Map<String, Object> apiMetrics(@RequestParam(value = "deviceKey", required = false) String deviceKey,
                                          @RequestParam(value = "metric", required = false) String metric,
                                          @RequestParam(value = "from", required = false)
                                          @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date from,
                                          @RequestParam(value = "to", required = false)
                                          @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date to,
                                          @RequestParam(value = "limit", defaultValue = "500") int limit) {
        return ok(plantService.queryMetrics(deviceKey, metric, from, to, limit));
    }

    @GetMapping("/api/metrics/multi")
    @ResponseBody
    public Map<String, Object> apiMultiMetrics(@RequestParam(value = "deviceKey", required = false) String deviceKey,
                                               @RequestParam(value = "metrics", required = false) String metrics,
                                               @RequestParam(value = "from", required = false)
                                               @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date from,
                                               @RequestParam(value = "to", required = false)
                                               @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date to,
                                               @RequestParam(value = "limit", defaultValue = "500") int limit) {
        return ok(plantService.queryMultiMetrics(deviceKey, metrics, from, to, limit));
    }

    @GetMapping("/api/readings")
    @ResponseBody
    public Map<String, Object> apiReadings(@RequestParam(value = "deviceKey", required = false) String deviceKey,
                                           @RequestParam(value = "metric", required = false) String metric,
                                           @RequestParam(value = "from", required = false)
                                           @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date from,
                                           @RequestParam(value = "to", required = false)
                                           @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date to,
                                           @RequestParam(value = "page", defaultValue = "0") int page,
                                           @RequestParam(value = "size", defaultValue = "50") int size) {
        return ok(plantService.listReadings(deviceKey, metric, from, to, page, size));
    }

    @GetMapping("/api/media")
    @ResponseBody
    public Map<String, Object> apiMedia(@RequestParam(value = "deviceKey", required = false) String deviceKey,
                                        @RequestParam(value = "limit", defaultValue = "30") int limit) {
        return ok(plantService.listMedia(deviceKey, limit));
    }

    @PostMapping("/api/media/upload")
    @ResponseBody
    public Map<String, Object> apiUpload(@RequestParam("deviceKey") String deviceKey,
                                         @RequestParam("file") MultipartFile file,
                                         @RequestParam(value = "capturedAt", required = false)
                                         @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date capturedAt,
                                         @RequestParam(value = "metaJson", required = false) String metaJson) {
        try {
            return ok(plantService.uploadMedia(deviceKey, file, capturedAt, metaJson));
        } catch (Exception e) {
            return fail(50002, e.getMessage());
        }
    }

    @GetMapping("/api/media/{id}/file")
    @ResponseBody
    public ResponseEntity<Resource> apiMediaFile(@PathVariable Long id) {
        try {
            PlantMediaAsset asset = plantService.getMedia(id);
            if (asset.getStoragePath() == null || asset.getStoragePath().trim().isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            File file = new File(asset.getStoragePath());
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }
            MediaType mediaType = guessMediaType(file.getName());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getName() + "\"")
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentLength(file.length())
                    .contentType(mediaType)
                    .body(new FileSystemResource(file));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    private static MediaType guessMediaType(String filename) {
        String n = filename == null ? "" : filename.toLowerCase();
        if (n.endsWith(".mp4")) {
            return MediaType.parseMediaType("video/mp4");
        }
        if (n.endsWith(".webm")) {
            return MediaType.parseMediaType("video/webm");
        }
        if (n.endsWith(".mkv")) {
            return MediaType.parseMediaType("video/x-matroska");
        }
        if (n.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (n.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        if (n.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }

    @GetMapping("/api/events")
    @ResponseBody
    public Map<String, Object> apiEvents(@RequestParam(value = "deviceKey", required = false) String deviceKey,
                                         @RequestParam(value = "limit", defaultValue = "30") int limit) {
        return ok(plantService.listEvents(deviceKey, limit));
    }

    @GetMapping("/api/export/telemetry")
    public void apiExport(@RequestParam(value = "deviceKey", required = false) String deviceKey,
                          @RequestParam(value = "metric", required = false) String metric,
                          @RequestParam(value = "format", defaultValue = "csv") String format,
                          @RequestParam(value = "from", required = false)
                          @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date from,
                          @RequestParam(value = "to", required = false)
                          @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date to,
                          HttpServletResponse response) throws Exception {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        if ("excel".equalsIgnoreCase(format) || "xls".equalsIgnoreCase(format)) {
            response.setContentType("application/vnd.ms-excel");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=plant_telemetry_" + stamp + ".xls");
            plantService.exportTelemetryExcel(deviceKey, metric, from, to, response.getOutputStream());
        } else {
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=plant_telemetry_" + stamp + ".csv");
            plantService.exportTelemetryCsv(deviceKey, metric, from, to, response.getOutputStream());
        }
    }

    // ---------- phenotype APIs ----------

    @GetMapping("/api/phenotype/overview")
    @ResponseBody
    public Map<String, Object> apiPhenotypeOverview(@RequestParam(value = "deviceKey", required = false) String deviceKey) {
        return ok(phenotypeService.overview(deviceKey));
    }

    @GetMapping("/api/phenotype/specimens")
    @ResponseBody
    public Map<String, Object> apiPhenotypeSpecimens(@RequestParam(value = "deviceKey", required = false) String deviceKey) {
        return ok(phenotypeService.listSpecimens(deviceKey));
    }

    @GetMapping("/api/phenotype/specimens/{plantCode}")
    @ResponseBody
    public Map<String, Object> apiPhenotypeSpecimen(@PathVariable String plantCode,
                                                    @RequestParam(value = "deviceKey", required = false) String deviceKey) {
        try {
            return ok(phenotypeService.getSpecimenDetail(deviceKey, plantCode));
        } catch (IllegalArgumentException e) {
            return fail(40001, e.getMessage());
        }
    }

    @GetMapping("/api/phenotype/metrics")
    @ResponseBody
    public Map<String, Object> apiPhenotypeMetrics(@RequestParam(value = "deviceKey", required = false) String deviceKey,
                                                   @RequestParam(value = "plantCode", required = false) String plantCode,
                                                   @RequestParam(value = "metric", required = false) String metric,
                                                   @RequestParam(value = "from", required = false)
                                                   @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date from,
                                                   @RequestParam(value = "to", required = false)
                                                   @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date to,
                                                   @RequestParam(value = "limit", defaultValue = "200") int limit) {
        return ok(phenotypeService.queryMetrics(deviceKey, plantCode, metric, from, to, limit));
    }

    @GetMapping("/api/phenotype/media")
    @ResponseBody
    public Map<String, Object> apiPhenotypeMedia(@RequestParam(value = "deviceKey", required = false) String deviceKey,
                                                 @RequestParam(value = "plantCode", required = false) String plantCode,
                                                 @RequestParam(value = "limit", defaultValue = "40") int limit) {
        return ok(phenotypeService.listPhenotypeMedia(deviceKey, plantCode, limit));
    }

    @GetMapping("/api/phenotype/jobs")
    @ResponseBody
    public Map<String, Object> apiPhenotypeJobs(@RequestParam(value = "deviceKey", required = false) String deviceKey,
                                                @RequestParam(value = "limit", defaultValue = "30") int limit) {
        return ok(phenotypeService.listJobs(deviceKey, limit));
    }

    @PostMapping("/api/phenotype/jobs")
    @ResponseBody
    public Map<String, Object> apiCreatePhenotypeJob(@RequestBody Map<String, Object> body) {
        try {
            return ok(phenotypeService.createJob(body));
        } catch (IllegalArgumentException e) {
            return fail(40001, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return fail(50002, e.getMessage());
        }
    }

    @PostMapping("/api/phenotype/jobs/{id}/mock-complete")
    @ResponseBody
    public Map<String, Object> apiMockCompleteJob(@PathVariable Long id) {
        try {
            return ok(phenotypeService.mockCompleteJob(id));
        } catch (IllegalArgumentException e) {
            return fail(40001, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return fail(50002, e.getMessage());
        }
    }

    private Map<String, Object> ok(Object data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("message", "ok");
        result.put("data", data);
        return result;
    }

    private Map<String, Object> fail(int code, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", code);
        result.put("message", message == null ? "error" : message);
        result.put("data", null);
        return result;
    }
}
