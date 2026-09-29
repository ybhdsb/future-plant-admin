<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>未来植物 · 指令日志</title>
    <script src="/static/js/jquery-3.4.1.min.js"></script>
    <link rel="stylesheet" href="/static/layui/css/layui.css">
    <link rel="stylesheet" href="/static/css/plant.css">
</head>
<body>
<div class="fp-page">
    <div class="fp-hero">
        <div class="fp-hero-main">
            <div class="fp-kicker">Control · Command Log</div>
            <h1 class="fp-title">指令日志</h1>
            <div class="fp-subtitle">查看最近下发的控制指令、执行状态与回执信息，便于联调排查。</div>
        </div>
        <div class="fp-hero-aside">
            <div class="fp-status-row">
                <span class="fp-pill danger" id="onlinePill"><span class="fp-dot"></span>离线</span>
                <span class="fp-pill info" id="modePill">Mock</span>
            </div>
            <div class="fp-toolbar">
                <button class="fp-btn secondary" id="btnRefresh">刷新</button>
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
        <a class="fp-subtab" href="/plant/control/camera">云台与相机</a>
        <a class="fp-subtab active" href="/plant/control/logs">指令日志</a>
    </div>

    <div class="fp-panel">
        <div class="fp-panel-hd">
            <div>
                <h3>最近指令</h3>
                <div class="desc">含 LED / 泵 / 继电器 / 云台 / 抓拍</div>
            </div>
        </div>
        <div class="fp-panel-bd" id="cmdLog" style="min-height:320px;"></div>
    </div>
</div>

<script src="/static/layui/layui.js"></script>
<script src="/static/js/plant-control.js"></script>
<script>
(function () {
    function refresh() {
        PlantControl.fetchDashboard(function (d) {
            PlantControl.renderLog(d.recentCommands, '#cmdLog');
        });
    }
    PlantControl.ready(function () {
        $('#btnRefresh').on('click', refresh);
        refresh();
        setInterval(refresh, 5000);
    });
})();
</script>
</body>
</html>
