<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>未来植物 · 分析任务</title>
    <script src="/static/js/jquery-3.4.1.min.js"></script>
    <link rel="stylesheet" href="/static/layui/css/layui.css">
    <link rel="stylesheet" href="/static/css/plant.css">
    <style>
        .filter-row { display:flex; flex-wrap:wrap; gap:10px; align-items:center; margin-bottom:14px; }
        .job-table { width:100%; border-collapse:collapse; font-size:13px; }
        .job-table th, .job-table td { padding:9px 10px; border-bottom:1px solid #e5eee9; text-align:left; vertical-align:top; }
        .job-table th { color:#64748b; font-weight:700; background:#f8fbfa; }
        .st { display:inline-block; padding:2px 8px; border-radius:999px; font-size:11px; font-weight:700; }
        .st-SUCCESS { background:#dcfce7; color:#166534; }
        .st-QUEUED { background:#e0f2fe; color:#075985; }
        .st-RUNNING { background:#fef3c7; color:#92400e; }
        .st-FAILED { background:#fee2e2; color:#991b1b; }
        .empty-hint {
            margin-top:8px; padding:12px; border-radius:10px; background:#f8fafc; border:1px dashed #cbd5e1;
            color:#64748b; font-size:12px; line-height:1.6;
        }
    </style>
</head>
<body>
<div class="fp-page">
    <div class="fp-hero">
        <div class="fp-hero-main">
            <div class="fp-kicker">Phenotype · Analysis</div>
            <h1 class="fp-title">分析任务</h1>
            <div class="fp-subtitle">影像 → 表型提取任务队列。真实模型接入前，可用「Mock 完成」模拟推理并写回指标，驱动数字化植株更新。</div>
        </div>
        <div class="fp-hero-aside">
            <div class="fp-toolbar">
                <button class="fp-btn secondary" id="btnRefresh">刷新</button>
            </div>
        </div>
    </div>

    <div class="fp-tabs">
        <a class="fp-tab" href="/plant/dashboard">系统总览</a>
        <a class="fp-tab" href="/plant/control/led">设备控制</a>
        <a class="fp-tab active" href="/plant/phenotype/digital">作物表型</a>
        <a class="fp-tab" href="/plant/history">历史数据</a>
        <a class="fp-tab" href="/plant/data">数据管理</a>
    </div>
    <div class="fp-subtabs">
        <a class="fp-subtab" href="/plant/phenotype/digital">数字化植株</a>
        <a class="fp-subtab" href="/plant/phenotype/metrics">表型指标</a>
        <a class="fp-subtab" href="/plant/phenotype/media">多模态影像</a>
        <a class="fp-subtab active" href="/plant/phenotype/jobs">分析任务</a>
    </div>

    <div class="fp-panel">
        <div class="fp-panel-hd">
            <div>
                <h3>创建任务</h3>
                <div class="desc">选择植株后入队；结果将写入表型指标</div>
            </div>
        </div>
        <div class="fp-panel-bd">
            <div class="filter-row">
                <select class="fp-field" id="plantCode" style="width:180px;"></select>
                <button class="fp-btn" id="btnCreate">创建表型提取任务</button>
            </div>
            <div class="empty-hint">模型侧约定：输入 RGB/深度/红外媒体 ID → 输出 height_cm、leaf_count、stem_diameter_mm、leaf_area_cm2、spad。当前无模型服务时请用 Mock 完成。</div>
        </div>
    </div>

    <div class="fp-panel">
        <div class="fp-panel-hd">
            <div>
                <h3>任务列表</h3>
                <div class="desc">按创建时间倒序</div>
            </div>
        </div>
        <div class="fp-panel-bd">
            <table class="job-table">
                <thead>
                <tr><th>ID</th><th>植株</th><th>类型</th><th>状态</th><th>说明</th><th>时间</th><th>操作</th></tr>
                </thead>
                <tbody id="jobBody"></tbody>
            </table>
        </div>
    </div>
</div>
<script src="/static/layui/layui.js"></script>
<script>
(function () {
    var DEVICE_KEY = 'plant-ctrl-01';
    var layer;

    function fmt(v) {
        if (!v) return '--';
        var d = new Date(v);
        if (isNaN(d.getTime())) return String(v);
        function p(n) { return n < 10 ? '0' + n : n; }
        return p(d.getMonth() + 1) + '-' + p(d.getDate()) + ' ' + p(d.getHours()) + ':' + p(d.getMinutes());
    }

    function loadPlants() {
        $.getJSON('/plant/api/phenotype/specimens', {deviceKey: DEVICE_KEY}, function (resp) {
            if (resp.code !== 0) return;
            var html = '';
            (resp.data || []).forEach(function (p) {
                html += '<option value="' + p.plantCode + '">' + p.plantCode + ' · ' + (p.slotCode || '') + '</option>';
            });
            $('#plantCode').html(html);
        });
    }

    function loadJobs() {
        $.getJSON('/plant/api/phenotype/jobs', {deviceKey: DEVICE_KEY, limit: 40}, function (resp) {
            if (resp.code !== 0) return;
            var html = '';
            (resp.data || []).forEach(function (j) {
                var canMock = j.status === 'QUEUED' || j.status === 'RUNNING' || j.status === 'FAILED';
                html += '<tr>' +
                    '<td>' + j.id + '</td>' +
                    '<td>' + (j.plantCode || '--') + '</td>' +
                    '<td>' + (j.jobType || '') + '</td>' +
                    '<td><span class="st st-' + j.status + '">' + j.status + '</span></td>' +
                    '<td>' + (j.message || '') + '</td>' +
                    '<td>' + fmt(j.createdAt) + (j.finishedAt ? '<br>完成 ' + fmt(j.finishedAt) : '') + '</td>' +
                    '<td>' + (canMock
                        ? '<button class="fp-btn ghost btn-mock" data-id="' + j.id + '">Mock 完成</button>'
                        : '<a class="fp-btn ghost" href="/plant/phenotype/digital">查看植株</a>') +
                    '</td></tr>';
            });
            $('#jobBody').html(html || '<tr><td colspan="7">暂无任务</td></tr>');
        });
    }

    $('#btnCreate').on('click', function () {
        $.ajax({
            url: '/plant/api/phenotype/jobs',
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({
                deviceKey: DEVICE_KEY,
                plantCode: $('#plantCode').val(),
                jobType: 'PHENOTYPE_EXTRACT',
                mediaIds: []
            })
        }).done(function (resp) {
            if (resp.code === 0) {
                layer && layer.msg('已入队', {icon: 1});
                loadJobs();
            } else {
                layer && layer.msg(resp.message || '失败', {icon: 2});
            }
        });
    });

    $(document).on('click', '.btn-mock', function () {
        var id = $(this).data('id');
        $.ajax({
            url: '/plant/api/phenotype/jobs/' + id + '/mock-complete',
            method: 'POST',
            contentType: 'application/json',
            data: '{}'
        }).done(function (resp) {
            if (resp.code === 0) {
                layer && layer.msg('已 Mock 写回表型', {icon: 1});
                loadJobs();
            } else {
                layer && layer.msg(resp.message || '失败', {icon: 2});
            }
        });
    });

    $('#btnRefresh').on('click', loadJobs);
    layui.use(['layer'], function () {
        layer = layui.layer;
        loadPlants();
        loadJobs();
    });
})();
</script>
</body>
</html>
