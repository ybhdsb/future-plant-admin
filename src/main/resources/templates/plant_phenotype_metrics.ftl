<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>未来植物 · 表型指标</title>
    <script src="/static/js/jquery-3.4.1.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js"></script>
    <link rel="stylesheet" href="/static/layui/css/layui.css">
    <link rel="stylesheet" href="/static/css/plant.css">
    <style>
        .filter-row { display:flex; flex-wrap:wrap; gap:10px; align-items:center; margin-bottom:12px; }
        .filter-row .fp-field { width:140px; }
        #heightChart { height:280px; }
        .data-table { width:100%; border-collapse:collapse; font-size:13px; }
        .data-table th, .data-table td { padding:8px 10px; border-bottom:1px solid #e5eee9; text-align:left; }
        .data-table th { color:#64748b; font-weight:700; background:#f8fbfa; }
    </style>
</head>
<body>
<div class="fp-page">
    <div class="fp-hero">
        <div class="fp-hero-main">
            <div class="fp-kicker">Phenotype · Metrics</div>
            <h1 class="fp-title">表型指标</h1>
            <div class="fp-subtitle">查看各植株株高、叶片数、茎粗、叶面积、SPAD 等指标；支持按植株与指标筛选。数据来源含 Mock / 模型写回。</div>
        </div>
        <div class="fp-hero-aside">
            <div class="fp-toolbar">
                <button class="fp-btn" id="btnQuery">查询</button>
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
        <a class="fp-subtab active" href="/plant/phenotype/metrics">表型指标</a>
        <a class="fp-subtab" href="/plant/phenotype/media">多模态影像</a>
        <a class="fp-subtab" href="/plant/phenotype/jobs">分析任务</a>
    </div>

    <div class="fp-panel">
        <div class="fp-panel-hd">
            <div>
                <h3>筛选</h3>
                <div class="desc">默认展示全部最新记录</div>
            </div>
        </div>
        <div class="fp-panel-bd">
            <div class="filter-row">
                <select class="fp-field" id="plantCode"><option value="">全部植株</option></select>
                <select class="fp-field" id="metric">
                    <option value="">全部指标</option>
                    <option value="height_cm">株高 height_cm</option>
                    <option value="leaf_count">叶片数 leaf_count</option>
                    <option value="stem_diameter_mm">茎粗 stem_diameter_mm</option>
                    <option value="leaf_area_cm2">叶面积 leaf_area_cm2</option>
                    <option value="spad">SPAD</option>
                </select>
            </div>
            <div id="heightChart"></div>
            <table class="data-table" id="metricTable">
                <thead>
                <tr><th>时间</th><th>植株</th><th>指标</th><th>数值</th><th>单位</th><th>来源</th></tr>
                </thead>
                <tbody></tbody>
            </table>
        </div>
    </div>
</div>
<script>
(function () {
    var DEVICE_KEY = 'plant-ctrl-01';
    var chart = echarts.init(document.getElementById('heightChart'));

    function fmt(v) {
        if (!v) return '--';
        var d = new Date(v);
        if (isNaN(d.getTime())) return String(v);
        function p(n) { return n < 10 ? '0' + n : n; }
        return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) + ' '
            + p(d.getHours()) + ':' + p(d.getMinutes());
    }

    function loadPlants(cb) {
        $.getJSON('/plant/api/phenotype/specimens', {deviceKey: DEVICE_KEY}, function (resp) {
            if (resp.code !== 0) return;
            var $sel = $('#plantCode');
            (resp.data || []).forEach(function (p) {
                $sel.append('<option value="' + p.plantCode + '">' + p.plantCode + ' · ' + (p.slotCode || '') + '</option>');
            });
            cb && cb();
        });
    }

    function query() {
        $.getJSON('/plant/api/phenotype/metrics', {
            deviceKey: DEVICE_KEY,
            plantCode: $('#plantCode').val(),
            metric: $('#metric').val(),
            limit: 200
        }, function (resp) {
            if (resp.code !== 0) return;
            var rows = resp.data || [];
            var html = '';
            rows.forEach(function (r) {
                html += '<tr><td>' + fmt(r.sampledAt) + '</td><td>' + r.plantCode + '</td><td>' + r.metric +
                    '</td><td>' + r.value + '</td><td>' + (r.unit || '') + '</td><td>' + (r.source || '') + '</td></tr>';
            });
            $('#metricTable tbody').html(html || '<tr><td colspan="6">暂无数据</td></tr>');

            var heightRows = rows.filter(function (r) { return r.metric === 'height_cm'; }).slice().reverse();
            var byPlant = {};
            heightRows.forEach(function (r) {
                if (!byPlant[r.plantCode]) byPlant[r.plantCode] = [];
                byPlant[r.plantCode].push(r);
            });
            var series = Object.keys(byPlant).slice(0, 6).map(function (code) {
                return {
                    name: code,
                    type: 'line',
                    smooth: true,
                    data: byPlant[code].map(function (r) { return [r.sampledAt, r.value]; })
                };
            });
            chart.setOption({
                title: { text: '株高趋势', left: 0, textStyle: { fontSize: 14 } },
                tooltip: { trigger: 'axis' },
                legend: { top: 0, right: 0 },
                grid: { left: 40, right: 20, top: 40, bottom: 30 },
                xAxis: { type: 'time' },
                yAxis: { type: 'value', name: 'cm' },
                series: series.length ? series : [{ type: 'line', data: [] }]
            }, true);
        });
    }

    $('#btnQuery').on('click', query);
    loadPlants(query);
    $(window).on('resize', function () { chart.resize(); });
})();
</script>
</body>
</html>
