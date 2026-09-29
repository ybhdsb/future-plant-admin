package cn.geek51.service;

import cn.geek51.dao.DatasetLibraryRepository;
import cn.geek51.dao.ModelLibraryRepository;
import cn.geek51.domain.DatasetLibrary;
import cn.geek51.domain.ModelLibrary;
import cn.geek51.domain.UserAuth;
import cn.geek51.service.impl.UserAuthServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

/**
 * 首次启动写入公共/自建样例资产（磁盘文件 + MySQL 元数据），并补齐版本血缘。
 */
@Component
public class LibrarySeedRunner implements ApplicationRunner {

    @Autowired
    private ModelLibraryRepository modelRepository;

    @Autowired
    private DatasetLibraryRepository datasetRepository;

    @Autowired
    private UserAuthServiceImpl userAuthService;

    @Value("${library.storage-root:uploads/library}")
    private String storageRoot;

    @Override
    public void run(ApplicationArguments args) {
        try {
            String owner = pickOwner();
            DatasetLibrary mnist = seedDataset("MNIST", "mnist", "mnist", owner, "图像", "public",
                    70000, 10, "folder", "ready",
                    "{\"version\":\"v1\",\"quality\":0.98}",
                    "公共手写数字数据集。联邦训练参数名为 mnist，与现有 server.py --dataset 对应。",
                    null);
            DatasetLibrary cifar = seedDataset("CIFAR-10", "cifar10", "cifar10", owner, "图像", "public",
                    60000, 10, "folder", "ready",
                    "{\"version\":\"v1\",\"quality\":0.97}",
                    "公共彩色物体分类数据集。联邦训练参数名为 cifar10，与现有 server.py --dataset 对应。",
                    null);
            DatasetLibrary pig = seedDataset("生猪行为识别", "pig-behavior", "pig_behavior", owner, "图像", "custom",
                    240, 4, "folder", "ready",
                    "{\"version\":\"v1.0\",\"labeled\":1,\"nightRatio\":0.25}",
                    "自建生猪行为数据集，含站立/躺卧/采食/打斗四类。用于演示详情预览、类别分布与模型血缘。",
                    null);

            enrichDatasetFiles(mnist, false);
            enrichDatasetFiles(cifar, false);
            enrichDatasetFiles(pig, true);

            ModelLibrary lenet = seedModel("LeNet-5", "lenet5", "lenet5", owner, "PyTorch", "v1.0",
                    "图像分类", "public", "deployable",
                    mnist == null ? null : mnist.getId(), null, "FedBuff",
                    "1x28x28", 10, "{\"accuracy\":0.986,\"f1\":0.984}",
                    "公共卷积神经网络，适配 MNIST。联邦训练参数名为 lenet5。");
            ModelLibrary resnet = seedModel("ResNet-18", "resnet18", "resnet18", owner, "PyTorch", "v1.0",
                    "图像分类", "public", "deployable",
                    cifar == null ? null : cifar.getId(), null, "FedFix",
                    "3x32x32", 10, "{\"accuracy\":0.912,\"f1\":0.908}",
                    "公共残差网络，适配 CIFAR-10。联邦训练参数名为 resnet18。");

            ModelLibrary pigV1 = seedModel("PigBehavior-CNN", "pig-behavior-cnn", "pig_cnn", owner, "PyTorch", "v1.0",
                    "行为识别", "custom", "experimental",
                    pig == null ? null : pig.getId(), null, "FedBuff",
                    "3x128x128", 4, "{\"accuracy\":0.864,\"f1\":0.851,\"mAP\":0.79}",
                    "自编轻量 CNN，面向生猪行为四分类。v1.0 为联邦初训版本。");
            ModelLibrary pigV2 = seedModel("PigBehavior-CNN", "pig-behavior-cnn", "pig_cnn_v11", owner, "PyTorch", "v1.1",
                    "行为识别", "custom", "deployable",
                    pig == null ? null : pig.getId(),
                    pigV1 == null ? null : pigV1.getId(),
                    "FedASRA",
                    "3x128x128", 4, "{\"accuracy\":0.913,\"f1\":0.905,\"mAP\":0.86}",
                    "在 v1.0 基础上继续联邦训练得到的改进版，指标提升后标记为可部署。");

            enrichModelFiles(lenet, "LeNet-5", "lenet5");
            enrichModelFiles(resnet, "ResNet-18", "resnet18");
            enrichModelFiles(pigV1, "PigBehavior-CNN", "pig_cnn");
            enrichModelFiles(pigV2, "PigBehavior-CNN", "pig_cnn_v11");

            backfillLineage(mnist, cifar, pig, lenet, resnet, pigV1, pigV2);
        } catch (Exception e) {
            System.err.println("公共模型/数据集初始化失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void backfillLineage(DatasetLibrary mnist, DatasetLibrary cifar, DatasetLibrary pig,
                                 ModelLibrary lenet, ModelLibrary resnet,
                                 ModelLibrary pigV1, ModelLibrary pigV2) {
        if (lenet != null && lenet.getSourceDatasetId() == null && mnist != null) {
            lenet.setSourceDatasetId(mnist.getId());
            lenet.setFamilyKey("lenet5");
            lenet.setTrainingMethod("FedBuff");
            lenet.setStatus("deployable");
            lenet.setInputShape("1x28x28");
            lenet.setOutputClasses(10);
            lenet.setMetricsJson("{\"accuracy\":0.986,\"f1\":0.984}");
            modelRepository.save(lenet);
        }
        if (resnet != null && resnet.getSourceDatasetId() == null && cifar != null) {
            resnet.setSourceDatasetId(cifar.getId());
            resnet.setFamilyKey("resnet18");
            resnet.setTrainingMethod("FedFix");
            resnet.setStatus("deployable");
            resnet.setInputShape("3x32x32");
            resnet.setOutputClasses(10);
            resnet.setMetricsJson("{\"accuracy\":0.912,\"f1\":0.908}");
            modelRepository.save(resnet);
        }
        if (pigV1 != null && pigV1.getSourceDatasetId() == null && pig != null) {
            pigV1.setSourceDatasetId(pig.getId());
            modelRepository.save(pigV1);
        }
        if (pigV2 != null) {
            boolean dirty = false;
            if (pigV2.getSourceDatasetId() == null && pig != null) {
                pigV2.setSourceDatasetId(pig.getId());
                dirty = true;
            }
            if (pigV2.getParentModelId() == null && pigV1 != null) {
                pigV2.setParentModelId(pigV1.getId());
                dirty = true;
            }
            if (dirty) {
                modelRepository.save(pigV2);
            }
        }
        if (mnist != null && (mnist.getFamilyKey() == null || mnist.getFamilyKey().trim().isEmpty())) {
            mnist.setFamilyKey("mnist");
            mnist.setStatus("ready");
            mnist.setClassCount(10);
            mnist.setLabelFormat("folder");
            datasetRepository.save(mnist);
        }
        if (cifar != null && (cifar.getFamilyKey() == null || cifar.getFamilyKey().trim().isEmpty())) {
            cifar.setFamilyKey("cifar10");
            cifar.setStatus("ready");
            cifar.setClassCount(10);
            cifar.setLabelFormat("folder");
            datasetRepository.save(cifar);
        }
    }

    private String pickOwner() {
        try {
            List<UserAuth> users = userAuthService.listAll();
            if (users != null) {
                for (UserAuth user : users) {
                    if (user != null && user.getUsername() != null && user.getUsername().trim().length() > 0) {
                        return user.getUsername().trim();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "admin";
    }

    private DatasetLibrary seedDataset(String name, String familyKey, String flKey, String owner,
                                       String category, String datasetType, int sampleCount, Integer classCount,
                                       String labelFormat, String status, String metricsJson,
                                       String description, Long parentId) throws Exception {
        DatasetLibrary existing = datasetRepository.findFirstByFlKey(flKey);
        if (existing == null) {
            existing = datasetRepository.findFirstByName(name);
        }
        if (existing != null) {
            boolean dirty = false;
            if (isBlank(existing.getFamilyKey())) {
                existing.setFamilyKey(familyKey);
                dirty = true;
            }
            if (isBlank(existing.getStatus())) {
                existing.setStatus(status);
                dirty = true;
            }
            if (existing.getClassCount() == null && classCount != null) {
                existing.setClassCount(classCount);
                dirty = true;
            }
            if (isBlank(existing.getLabelFormat())) {
                existing.setLabelFormat(labelFormat);
                dirty = true;
            }
            if (isBlank(existing.getMetricsJson()) && metricsJson != null) {
                existing.setMetricsJson(metricsJson);
                dirty = true;
            }
            if (dirty) {
                existing.setUpdatedTime(new Date());
                existing = datasetRepository.save(existing);
            }
            return existing;
        }

        LibraryStorageHelper.SaveResult folder = LibraryStorageHelper.createEmptyFolder(storageRoot, "datasets");
        Path dir = LibraryStorageHelper.resolveStorageDir(storageRoot, folder.storagePath);
        if ("pig_behavior".equals(flKey)) {
            writePigDatasetFiles(dir, owner);
        } else {
            writeDatasetFiles(dir, name, flKey, owner);
        }
        LibraryStorageHelper.SaveResult summary = LibraryStorageHelper.summarize(folder.storagePath, name, dir);

        DatasetLibrary dataset = new DatasetLibrary();
        dataset.setName(name);
        dataset.setOwnerName(owner);
        dataset.setDatasetType(datasetType);
        dataset.setCategory(category);
        dataset.setFlKey(flKey);
        dataset.setFamilyKey(familyKey);
        dataset.setParentDatasetId(parentId);
        dataset.setDescription(description);
        dataset.setSampleCount(sampleCount);
        dataset.setClassCount(classCount);
        dataset.setLabelFormat(labelFormat);
        dataset.setStatus(status);
        dataset.setMetricsJson(metricsJson);
        dataset.setFolderName(name);
        dataset.setStoragePath(summary.storagePath);
        dataset.setFileCount(summary.fileCount);
        dataset.setTotalSizeBytes(summary.totalSizeBytes);
        Date now = new Date();
        dataset.setCreatedTime(now);
        dataset.setUpdatedTime(now);
        DatasetLibrary saved = datasetRepository.save(dataset);
        System.out.println("已写入数据集: " + name + " -> " + dir);
        return saved;
    }

    private ModelLibrary seedModel(String name, String familyKey, String flKey, String owner,
                                   String framework, String version, String category, String libraryType,
                                   String status, Long sourceDatasetId, Long parentModelId,
                                   String trainingMethod, String inputShape, Integer outputClasses,
                                   String metricsJson, String description) throws Exception {
        ModelLibrary existing = modelRepository.findFirstByNameAndVersion(name, version);
        if (existing == null && parentModelId == null) {
            existing = modelRepository.findFirstByFlKey(flKey);
            if (existing != null && existing.getVersion() != null
                    && !version.equalsIgnoreCase(existing.getVersion())) {
                existing = null;
            }
        }
        if (existing != null) {
            boolean dirty = false;
            if (isBlank(existing.getFamilyKey())) {
                existing.setFamilyKey(familyKey);
                dirty = true;
            }
            if (existing.getSourceDatasetId() == null && sourceDatasetId != null) {
                existing.setSourceDatasetId(sourceDatasetId);
                dirty = true;
            }
            if (existing.getParentModelId() == null && parentModelId != null) {
                existing.setParentModelId(parentModelId);
                dirty = true;
            }
            if (isBlank(existing.getTrainingMethod()) && trainingMethod != null) {
                existing.setTrainingMethod(trainingMethod);
                dirty = true;
            }
            if (isBlank(existing.getStatus())) {
                existing.setStatus(status);
                dirty = true;
            }
            if (isBlank(existing.getInputShape()) && inputShape != null) {
                existing.setInputShape(inputShape);
                dirty = true;
            }
            if (existing.getOutputClasses() == null && outputClasses != null) {
                existing.setOutputClasses(outputClasses);
                dirty = true;
            }
            if (isBlank(existing.getMetricsJson()) && metricsJson != null) {
                existing.setMetricsJson(metricsJson);
                dirty = true;
            }
            if (dirty) {
                existing.setUpdatedTime(new Date());
                existing = modelRepository.save(existing);
            }
            return existing;
        }

        LibraryStorageHelper.SaveResult folder = LibraryStorageHelper.createEmptyFolder(storageRoot, "models");
        Path dir = LibraryStorageHelper.resolveStorageDir(storageRoot, folder.storagePath);
        if (familyKey.startsWith("pig-behavior")) {
            writePigModelFiles(dir, name, flKey, owner, framework, version, trainingMethod);
        } else {
            writeModelFiles(dir, name, flKey, owner, framework, version);
        }
        LibraryStorageHelper.SaveResult summary = LibraryStorageHelper.summarize(folder.storagePath, name, dir);

        ModelLibrary model = new ModelLibrary();
        model.setName(name);
        model.setOwnerName(owner);
        model.setFramework(framework);
        model.setVersion(version);
        model.setCategory(category);
        model.setLibraryType(libraryType);
        model.setFlKey(flKey);
        model.setFamilyKey(familyKey);
        model.setSourceDatasetId(sourceDatasetId);
        model.setParentModelId(parentModelId);
        model.setTrainingMethod(trainingMethod);
        model.setInputShape(inputShape);
        model.setOutputClasses(outputClasses);
        model.setStatus(status);
        model.setMetricsJson(metricsJson);
        model.setDescription(description);
        model.setFolderName(name + "-" + version);
        model.setStoragePath(summary.storagePath);
        model.setFileCount(summary.fileCount);
        model.setTotalSizeBytes(summary.totalSizeBytes);
        Date now = new Date();
        model.setCreatedTime(now);
        model.setUpdatedTime(now);
        ModelLibrary saved = modelRepository.save(model);
        System.out.println("已写入模型: " + name + " " + version + " -> " + dir);
        return saved;
    }

    private void enrichDatasetFiles(DatasetLibrary dataset, boolean pig) throws Exception {
        if (dataset == null || isBlank(dataset.getStoragePath())) {
            return;
        }
        Path dir = LibraryStorageHelper.resolveStorageDir(storageRoot, dataset.getStoragePath());
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        // 若样本过少，则补写演示文件（兼容旧种子）
        long images = 0L;
        try (java.util.stream.Stream<Path> stream = Files.walk(dir)) {
            images = stream.filter(p -> Files.isRegularFile(p)
                    && p.getFileName().toString().toLowerCase().endsWith(".png")).count();
        }
        if (pig && images < 8) {
            writePigDatasetFiles(dir, dataset.getOwnerName());
            LibraryStorageHelper.SaveResult summary = LibraryStorageHelper.summarize(
                    dataset.getStoragePath(), dataset.getFolderName(), dir);
            dataset.setFileCount(summary.fileCount);
            dataset.setTotalSizeBytes(summary.totalSizeBytes);
            dataset.setUpdatedTime(new Date());
            datasetRepository.save(dataset);
        } else if (!pig && images < 1) {
            writeDatasetFiles(dir, dataset.getName(), dataset.getFlKey(), dataset.getOwnerName());
            LibraryStorageHelper.SaveResult summary = LibraryStorageHelper.summarize(
                    dataset.getStoragePath(), dataset.getFolderName(), dir);
            dataset.setFileCount(summary.fileCount);
            dataset.setTotalSizeBytes(summary.totalSizeBytes);
            dataset.setUpdatedTime(new Date());
            datasetRepository.save(dataset);
        }
    }

    private void enrichModelFiles(ModelLibrary model, String name, String flKey) throws Exception {
        if (model == null || isBlank(model.getStoragePath())) {
            return;
        }
        Path dir = LibraryStorageHelper.resolveStorageDir(storageRoot, model.getStoragePath());
        if (!Files.exists(dir.resolve("README.md"))) {
            if (model.getFamilyKey() != null && model.getFamilyKey().startsWith("pig-behavior")) {
                writePigModelFiles(dir, name, flKey, model.getOwnerName(), model.getFramework(),
                        model.getVersion(), model.getTrainingMethod());
            } else {
                writeModelFiles(dir, name, flKey, model.getOwnerName(), model.getFramework(), model.getVersion());
            }
            LibraryStorageHelper.SaveResult summary = LibraryStorageHelper.summarize(
                    model.getStoragePath(), model.getFolderName(), dir);
            model.setFileCount(summary.fileCount);
            model.setTotalSizeBytes(summary.totalSizeBytes);
            model.setUpdatedTime(new Date());
            modelRepository.save(model);
        }
    }

    private void writeDatasetFiles(Path dir, String name, String flKey, String owner) throws Exception {
        LibraryStorageHelper.writeUtf8(dir.resolve("README.md"),
                "# " + name + "\n\n"
                        + "- 类型：公共数据集\n"
                        + "- 归属人：" + owner + "\n"
                        + "- 联邦训练名（--dataset）：" + flKey + "\n"
                        + "- 存储：Web 服务器 uploads/library 目录，联邦启动时由服务器角色下载后再分发给客户端。\n");
        LibraryStorageHelper.writeUtf8(dir.resolve("fl.json"),
                "{\n  \"name\": \"" + name + "\",\n  \"fl_key\": \"" + flKey + "\",\n  \"kind\": \"dataset\",\n  \"owner\": \"" + owner + "\"\n}\n");
        LibraryStorageHelper.writeUtf8(dir.resolve("manifest.csv"),
                "split,label,file\ntrain,0,train/0/sample_0.png\ntrain,1,train/1/sample_1.png\ntest,0,test/0/sample_0.png\n");
        writePng(dir.resolve("train/0/sample_0.png"), new Color(30, 30, 30), "0", 28);
        writePng(dir.resolve("train/1/sample_1.png"), new Color(80, 80, 80), "1", 28);
        writePng(dir.resolve("test/0/sample_0.png"), new Color(40, 40, 40), "0", 28);
    }

    private void writePigDatasetFiles(Path dir, String owner) throws Exception {
        String[] classes = {"standing", "lying", "eating", "fighting"};
        String[] labels = {"站立", "躺卧", "采食", "打斗"};
        LibraryStorageHelper.writeUtf8(dir.resolve("README.md"),
                "# 生猪行为识别数据集\n\n"
                        + "- 类型：自建数据集\n"
                        + "- 归属人：" + owner + "\n"
                        + "- 类别：standing / lying / eating / fighting\n"
                        + "- 标注格式：folder（按类别目录组织）\n"
                        + "- 说明：平台自动生成的演示样本，用于详情页预览、类别分布与联邦血缘演示。\n");
        LibraryStorageHelper.writeUtf8(dir.resolve("classes.json"),
                "{\n  \"classes\": [\"standing\", \"lying\", \"eating\", \"fighting\"],\n"
                        + "  \"labels_zh\": [\"站立\", \"躺卧\", \"采食\", \"打斗\"],\n"
                        + "  \"label_format\": \"folder\"\n}\n");
        StringBuilder manifest = new StringBuilder("split,label,file\n");
        Color[] colors = {
                new Color(46, 125, 50),
                new Color(25, 118, 210),
                new Color(245, 124, 0),
                new Color(198, 40, 40)
        };
        for (int c = 0; c < classes.length; c++) {
            for (int i = 0; i < 3; i++) {
                String trainRel = "train/" + classes[c] + "/sample_" + i + ".png";
                writeLabeledPng(dir.resolve(trainRel), colors[c], labels[c] + "-" + i, 96);
                manifest.append("train,").append(classes[c]).append(",").append(trainRel).append("\n");
            }
            String testRel = "test/" + classes[c] + "/sample_0.png";
            writeLabeledPng(dir.resolve(testRel), colors[c].darker(), labels[c], 96);
            manifest.append("test,").append(classes[c]).append(",").append(testRel).append("\n");
        }
        LibraryStorageHelper.writeUtf8(dir.resolve("manifest.csv"), manifest.toString());
        LibraryStorageHelper.writeUtf8(dir.resolve("fl.json"),
                "{\n  \"name\": \"生猪行为识别\",\n  \"fl_key\": \"pig_behavior\",\n  \"kind\": \"dataset\",\n  \"owner\": \"" + owner + "\"\n}\n");
    }

    private void writeModelFiles(Path dir, String name, String flKey, String owner, String framework, String version) throws Exception {
        LibraryStorageHelper.writeUtf8(dir.resolve("README.md"),
                "# " + name + "\n\n"
                        + "- 类型：公共模型\n"
                        + "- 归属人：" + owner + "\n"
                        + "- 框架：" + framework + " " + version + "\n"
                        + "- 联邦训练名（--local_model）：" + flKey + "\n");
        LibraryStorageHelper.writeUtf8(dir.resolve("architecture.json"),
                "{\n  \"name\": \"" + name + "\",\n  \"fl_key\": \"" + flKey + "\",\n  \"framework\": \"" + framework + "\",\n  \"version\": \"" + version + "\",\n  \"owner\": \"" + owner + "\"\n}\n");
        Path weights = dir.resolve("weights/placeholder.bin");
        Files.createDirectories(weights.getParent());
        byte[] bytes = new byte[256];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i * 17);
        }
        Files.write(weights, bytes);
    }

    private void writePigModelFiles(Path dir, String name, String flKey, String owner,
                                    String framework, String version, String method) throws Exception {
        LibraryStorageHelper.writeUtf8(dir.resolve("README.md"),
                "# " + name + " " + version + "\n\n"
                        + "- 场景：生猪行为识别（站立/躺卧/采食/打斗）\n"
                        + "- 框架：" + framework + "\n"
                        + "- 训练算法：" + (method == null ? "FedBuff" : method) + "\n"
                        + "- 联邦训练名：" + flKey + "\n"
                        + "- 说明：本仓库提供可运行的轻量模型定义（model.py）与配置，weights 为演示占位权重。\n");
        LibraryStorageHelper.writeUtf8(dir.resolve("config.yaml"),
                "name: " + name + "\n"
                        + "version: " + version + "\n"
                        + "framework: " + framework + "\n"
                        + "fl_key: " + flKey + "\n"
                        + "num_classes: 4\n"
                        + "input_size: [3, 128, 128]\n"
                        + "classes: [standing, lying, eating, fighting]\n"
                        + "training_method: " + (method == null ? "FedBuff" : method) + "\n");
        LibraryStorageHelper.writeUtf8(dir.resolve("architecture.json"),
                "{\n"
                        + "  \"name\": \"" + name + "\",\n"
                        + "  \"version\": \"" + version + "\",\n"
                        + "  \"fl_key\": \"" + flKey + "\",\n"
                        + "  \"backbone\": \"TinyCNN\",\n"
                        + "  \"layers\": [\"Conv3x3-16\", \"ReLU\", \"MaxPool\", \"Conv3x3-32\", \"ReLU\", \"MaxPool\", \"FC-4\"],\n"
                        + "  \"num_classes\": 4,\n"
                        + "  \"owner\": \"" + owner + "\"\n"
                        + "}\n");
        LibraryStorageHelper.writeUtf8(dir.resolve("model.py"),
                "import torch\n"
                        + "import torch.nn as nn\n\n"
                        + "class PigBehaviorCNN(nn.Module):\n"
                        + "    \"\"\"Lightweight CNN for pig behavior classification.\"\"\"\n"
                        + "    def __init__(self, num_classes=4):\n"
                        + "        super(PigBehaviorCNN, self).__init__()\n"
                        + "        self.features = nn.Sequential(\n"
                        + "            nn.Conv2d(3, 16, 3, padding=1), nn.ReLU(inplace=True), nn.MaxPool2d(2),\n"
                        + "            nn.Conv2d(16, 32, 3, padding=1), nn.ReLU(inplace=True), nn.MaxPool2d(2),\n"
                        + "            nn.Conv2d(32, 64, 3, padding=1), nn.ReLU(inplace=True), nn.AdaptiveAvgPool2d(1)\n"
                        + "        )\n"
                        + "        self.classifier = nn.Linear(64, num_classes)\n\n"
                        + "    def forward(self, x):\n"
                        + "        x = self.features(x)\n"
                        + "        x = x.view(x.size(0), -1)\n"
                        + "        return self.classifier(x)\n\n"
                        + "def build_model(num_classes=4):\n"
                        + "    return PigBehaviorCNN(num_classes=num_classes)\n\n"
                        + "if __name__ == '__main__':\n"
                        + "    net = build_model()\n"
                        + "    y = net(torch.randn(2, 3, 128, 128))\n"
                        + "    print('PigBehaviorCNN output:', tuple(y.shape))\n");
        Path weights = dir.resolve("weights/demo_weights.bin");
        Files.createDirectories(weights.getParent());
        byte[] bytes = new byte[512];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) ((i * 31 + version.hashCode()) & 0xff);
        }
        Files.write(weights, bytes);
    }

    private void writePng(Path file, Color background, String label, int size) throws Exception {
        Files.createDirectories(file.getParent());
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = image.createGraphics();
        g.setColor(background);
        g.fillRect(0, 0, size, size);
        g.setColor(Color.WHITE);
        g.drawString(label, Math.max(4, size / 3), size / 2 + 4);
        g.dispose();
        ImageIO.write(image, "png", file.toFile());
    }

    private void writeLabeledPng(Path file, Color background, String label, int size) throws Exception {
        Files.createDirectories(file.getParent());
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(background);
        g.fillRect(0, 0, size, size);
        g.setColor(new Color(255, 255, 255, 40));
        g.fillOval(size / 6, size / 5, size * 2 / 3, size * 2 / 3);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.drawString(label, 8, size - 12);
        g.dispose();
        ImageIO.write(image, "png", file.toFile());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
