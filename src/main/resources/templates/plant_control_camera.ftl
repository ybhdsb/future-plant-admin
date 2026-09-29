<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>未来植物 · 云台相机</title>
    <script src="/static/js/jquery-3.4.1.min.js"></script>
    <link rel="stylesheet" href="/static/layui/css/layui.css">
    <link rel="stylesheet" href="/static/css/plant.css">
    <style>
        .camera-layout { display:grid; grid-template-columns: 280px 1fr; gap:16px; }
        .gimbal { display:grid; grid-template-columns:repeat(3,72px); gap:10px; justify-content:center; margin: 8px auto 0; }
        .gimbal .fp-btn { width:72px; height:48px; padding:0; }
        .ghost-cell { visibility:hidden; }
        .preview-box {
            min-height: 280px; border-radius: 14px; border:1px solid #d7e5df; overflow:hidden;
            background: linear-gradient(135deg, #ecfdf5, #e0f2fe);
            display:flex; align-items:center; justify-content:center;
        }
        .preview-box img { width:100%; height:100%; object-fit:cover; display:block; max-height:360px; }
        @media (max-width: 900px) { .camera-layout { grid-template-columns:1fr; } }
    </style>
</head>
<body>
<div class="fp-page">
    <div class="fp-hero">
        <div class="fp-hero-main">
            <div class="fp-kicker">Control · Gimbal / Camera</div>
            <h1 class="fp-title">云台与相机</h1>
            <div class="fp-subtitle">点动控制云台方向、复位，并触发抓拍。抓拍样例会进入「历史数据」图片区。</div>
        </div>
        <div class="fp-hero-aside">
            <div class="fp-status-row">
                <span class="fp-pill danger" id="onlinePill"><span class="fp-dot"></span>离线</span>
                <span class="fp-pill info" id="modePill">Mock</span>
            </div>
            <div class="fp-toolbar">
                <button class="fp-btn secondary" id="btnRefresh">刷新状态</button>
            </div>
        </div>
    </div>

    <div class="fp-tabs">
        <a class="fp-tab" href="/plant/dashboard">系统总览</a>
        <a class="fp-tab active" href="/plant/control/led">设备控制</a>
        <a class="fp-tab" href="/plant/phenotype/digital">作物表型</a>
        <a class="fp-tab" href="/plant/history">历史数据</a>
        <a class="fp-tab" href="/plant/data">数据管理</a>
    </div>
    <div class="fp-subtabs">
        <a class="fp-subtab" href="/plant/control/led">LED 补光</a>
        <a class="fp-subtab" href="/plant/control/actuators">通风供水与自动化</a>
        <a class="fp-subtab active" href="/plant/control/camera">云台与相机</a>
        <a class="fp-subtab" href="/plant/control/logs">指令日志</a>
    </div>

    <div id="controlRoot" class="fp-panel">
        <div class="fp-panel-hd">
            <div>
                <h3>云台控制 / 抓拍预览</h3>
                <div class="desc">左侧操作，右侧显示最近一张采集图</div>
            </div>
            <a class="fp-btn secondary" href="/plant/history">查看历史图片</a>
        </div>
        <div class="fp-panel-bd">
            <div class="camera-layout">
                <div>
                    <div class="gimbal">
                        <span class="ghost-cell"></span>
                        <button class="fp-btn gimbal-btn" data-action="UP">上</button>
                        <span class="ghost-cell"></span>
                        <button class="fp-btn gimbal-btn" data-action="LEFT">左</button>
                        <button class="fp-btn secondary gimbal-btn" data-action="STOP">停</button>
                        <button class="fp-btn gimbal-btn" data-action="RIGHT">右</button>
                        <span class="ghost-cell"></span>
                        <button class="fp-btn gimbal-btn" data-action="DOWN">下</button>
                        <span class="ghost-cell"></span>
                    </div>
                    <div class="fp-toolbar" style="justify-content:center;margin-top:16px;">
                        <button class="fp-btn secondary" id="btnHome">云台复位</button>
                        <button class="fp-btn" id="btnCapture">触发抓拍</button>
                    </div>
                </div>
                <div>
                    <div class="preview-box" id="previewBox">
                        <img id="previewImg" src="/static/images/plant/corn_plant_sample.png" alt="玉米植株样例">
                    </div>
                    <div class="fp-muted" style="margin-top:8px;font-size:12px;" id="previewMeta">样例预览 · 玉米植株</div>
                </div>
            </div>
        </div>
    </div>
</div>

<script src="/static/layui/layui.js"></script>
<script src="/static/js/plant-control.js"></script>
<script>
(function () {
    function loadLatestMedia() {
        $.getJSON('/plant/api/media', {deviceKey: PlantControl.DEVICE_KEY, limit: 1}, function (resp) {
            var list = resp.data || [];
            if (!list.length) return;
            var m = list[0];
            $('#previewImg').attr('src', m.url || '/static/images/plant/corn_plant_sample.png');
            $('#previewMeta').text((m.label || '采集记录') + ' · ' + PlantControl.fmt(m.capturedAt));
        });
    }

    function refresh() {
        PlantControl.fetchDashboard(function () {});
        loadLatestMedia();
    }

    PlantControl.ready(function () {
        $('.gimbal-btn').on('click', function () {
            PlantControl.sendCommand('GIMBAL_MOVE', {action: $(this).data('action')});
        });
        $('#btnHome').on('click', function () {
            PlantControl.sendCommand('GIMBAL_MOVE', {action: 'HOME'});
        });
        $('#btnCapture').on('click', function () {
            PlantControl.sendCommand('CAMERA_CAPTURE', {cameraId: 'camera.main', reason: 'manual'})
                .done(function () { setTimeout(loadLatestMedia, 300); });
        });
        $('#btnRefresh').on('click', refresh);
        refresh();
        setInterval(function () { PlantControl.fetchDashboard(function () {}); }, 5000);
    });
})();
</script>
</body>
</html>
