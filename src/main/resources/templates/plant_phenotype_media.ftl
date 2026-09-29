<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>未来植物 · 多模态影像</title>
    <script src="/static/js/jquery-3.4.1.min.js"></script>
    <link rel="stylesheet" href="/static/layui/css/layui.css">
    <link rel="stylesheet" href="/static/css/plant.css">
    <style>
        .filter-row { display:flex; flex-wrap:wrap; gap:10px; align-items:center; margin-bottom:12px; }
        .media-grid { display:grid; grid-template-columns: repeat(4, minmax(0,1fr)); gap:12px; }
        .media-card {
            border:1px solid #d7e5df; border-radius:12px; overflow:hidden; background:#fff;
        }
        .media-thumb {
            height:150px; background:#ecfdf5; position:relative;
            display:flex; align-items:center; justify-content:center;
        }
        .media-thumb img { width:100%; height:100%; object-fit:cover; display:block; }
        .media-thumb.depth img { filter: grayscale(1) contrast(1.2) hue-rotate(160deg); }
        .media-thumb.ir img { filter: sepia(1) saturate(2) hue-rotate(-20deg) brightness(.95); }
        .badge {
            position:absolute; left:8px; top:8px; padding:2px 8px; border-radius:999px;
            background:rgba(15,40,35,.78); color:#fff; font-size:11px; font-weight:700;
        }
        .media-cap { padding:10px 12px; font-size:12px; color:#64748b; line-height:1.5; }
        .media-cap strong { display:block; color:#14352f; font-size:13px; margin-bottom:2px; }
        @media (max-width: 900px) { .media-grid { grid-template-columns:1fr 1fr; } }
    </style>
</head>
<body>
<div class="fp-page">
    <div class="fp-hero">
        <div class="fp-hero-main">
            <div class="fp-kicker">Phenotype · Multimodal</div>
            <h1 class="fp-title">多模态影像</h1>
            <div class="fp-subtitle">归档 RGB / 深度 / 红外等采集结果，按植株筛选。真机深度与红外接入前使用样例图占位。</div>
        </div>
        <div class="fp-hero-aside">
            <div class="fp-toolbar">
                <a class="fp-btn secondary" href="/plant/control/camera">去云台抓拍</a>
                <button class="fp-btn" id="btnRefresh">刷新</button>
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
        <a class="fp-subtab active" href="/plant/phenotype/media">多模态影像</a>
        <a class="fp-subtab" href="/plant/phenotype/jobs">分析任务</a>
    </div>

    <div class="fp-panel">
        <div class="fp-panel-bd">
            <div class="filter-row">
                <select class="fp-field" id="plantCode" style="width:160px;"><option value="">全部植株</option></select>
                <select class="fp-field" id="modality" style="width:140px;">
                    <option value="">全部模态</option>
                    <option value="RGB">RGB</option>
                    <option value="DEPTH">深度</option>
                    <option value="IR">红外</option>
                    <option value="IMAGE">通用图像</option>
                </select>
            </div>
            <div class="media-grid" id="mediaGrid"></div>
        </div>
    </div>
</div>
<script>
(function () {
    var DEVICE_KEY = 'plant-ctrl-01';

    function fmt(v) {
        if (!v) return '--';
        var d = new Date(v);
        if (isNaN(d.getTime())) return String(v);
        function p(n) { return n < 10 ? '0' + n : n; }
        return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) + ' '
            + p(d.getHours()) + ':' + p(d.getMinutes());
    }

    function loadPlants() {
        $.getJSON('/plant/api/phenotype/specimens', {deviceKey: DEVICE_KEY}, function (resp) {
            if (resp.code !== 0) return;
            (resp.data || []).forEach(function (p) {
                $('#plantCode').append('<option value="' + p.plantCode + '">' + p.plantCode + '</option>');
            });
        });
    }

    function load() {
        $.getJSON('/plant/api/phenotype/media', {
            deviceKey: DEVICE_KEY,
            plantCode: $('#plantCode').val(),
            limit: 60
        }, function (resp) {
            if (resp.code !== 0) return;
            var modality = ($('#modality').val() || '').toUpperCase();
            var list = (resp.data || []).filter(function (m) {
                if (!modality) return true;
                var t = String(m.modality || m.mediaType || '').toUpperCase();
                return t === modality || (modality === 'RGB' && (t === 'IMAGE' || t === 'RGB'));
            });
            var html = '';
            list.forEach(function (m) {
                var mod = String(m.modality || m.mediaType || 'RGB').toUpperCase();
                var cls = mod === 'DEPTH' ? 'depth' : (mod === 'IR' || mod === 'INFRARED' ? 'ir' : '');
                html += '<div class="media-card"><div class="media-thumb ' + cls + '">' +
                    '<span class="badge">' + mod + '</span>' +
                    '<img src="' + m.url + '" alt="">' +
                    '</div><div class="media-cap"><strong>' + (m.label || m.plantCode || '影像') + '</strong>' +
                    fmt(m.capturedAt) + (m.plantCode ? '<br>植株 ' + m.plantCode : '') +
                    '</div></div>';
            });
            $('#mediaGrid').html(html || '<div class="fp-muted">暂无影像</div>');
        });
    }

    $('#btnRefresh').on('click', load);
    $('#plantCode, #modality').on('change', load);
    loadPlants();
    load();
})();
</script>
</body>
</html>
