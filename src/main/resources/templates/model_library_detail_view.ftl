<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>模型详情</title>
    <script src="/static/js/jquery-3.4.1.min.js"></script>
    <link rel="stylesheet" href="/static/layui/css/layui.css">
    <style>
        body { margin: 0; padding: 16px; background: #f5f7fb; color: #1f2937; font-family: Arial, "Microsoft YaHei", sans-serif; }
        .page { max-width: 1200px; margin: 0 auto; }
        .back { display: inline-block; margin-bottom: 12px; color: #0f766e; text-decoration: none; }
        .hero { background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; padding: 18px; box-shadow: 0 10px 24px rgba(15,23,42,.05); }
        .hero-top { display: flex; justify-content: space-between; gap: 12px; align-items: flex-start; }
        h1 { margin: 0; font-size: 24px; }
        .sub { margin-top: 8px; color: #64748b; line-height: 1.6; }
        .tags { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }
        .tag { font-size: 12px; padding: 4px 10px; border-radius: 999px; background: #ecfeff; color: #0f766e; }
        .tag.gray { background: #f1f5f9; color: #475569; }
        .tag.blue { background: #eff6ff; color: #1d4ed8; }
        .tag.orange { background: #fff7ed; color: #c2410c; }
        .actions { display: flex; gap: 8px; flex-wrap: wrap; }
        .btn { height: 34px; padding: 0 14px; border: 0; border-radius: 6px; background: #009688; color: #fff; text-decoration: none; display: inline-flex; align-items: center; }
        .btn.secondary { background: #334155; }
        .grid { display: grid; grid-template-columns: 1.2fr .8fr; gap: 14px; margin-top: 14px; }
        .panel { background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; box-shadow: 0 10px 24px rgba(15,23,42,.04); }
        .panel-h { padding: 14px 16px; border-bottom: 1px solid #edf0f4; font-weight: 700; }
        .panel-b { padding: 14px 16px; }
        .kv { display: grid; grid-template-columns: 110px 1fr; gap: 8px 10px; font-size: 13px; }
        .kv .k { color: #64748b; }
        .metrics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
        .metric { background: #f8fafc; border-radius: 8px; padding: 12px; }
        .metric b { display: block; font-size: 20px; color: #0f766e; }
        .metric span { color: #64748b; font-size: 12px; }
        .timeline { list-style: none; margin: 0; padding: 0; }
        .timeline li { position: relative; padding: 0 0 16px 18px; border-left: 2px solid #d1fae5; }
        .timeline li:last-child { padding-bottom: 0; }
        .timeline li:before { content: ''; position: absolute; left: -6px; top: 4px; width: 10px; height: 10px; border-radius: 50%; background: #14b8a6; }
        .timeline li.current:before { background: #0f766e; box-shadow: 0 0 0 4px rgba(15,118,110,.15); }
        .timeline a { color: #0f766e; text-decoration: none; font-weight: 600; }
        .timeline .s { color: #64748b; font-size: 12px; margin-top: 4px; }
        .version { display: flex; justify-content: space-between; gap: 8px; padding: 10px 0; border-bottom: 1px solid #f1f5f9; }
        .version:last-child { border-bottom: 0; }
        .version.current { background: #f0fdfa; margin: 0 -8px; padding: 10px 8px; border-radius: 8px; }
        .tree { max-height: 360px; overflow: auto; font-family: Menlo, Consolas, monospace; font-size: 12px; background: #0f172a; color: #d1fae5; border-radius: 8px; padding: 12px; }
        .tree div { padding: 2px 0; white-space: nowrap; }
        .muted { color: #64748b; }
        @media (max-width: 900px) { .grid, .metrics { grid-template-columns: 1fr; } .hero-top { flex-direction: column; } }
    </style>
</head>
<body>
<div class="page">
    <a class="back" href="/model_library">← 返回模型库</a>
    <div class="hero">
        <div class="hero-top">
            <div>
                <h1 id="name">加载中...</h1>
                <div class="sub" id="desc"></div>
                <div class="tags" id="tags"></div>
            </div>
            <div class="actions">
                <a class="btn" id="downloadBtn" href="#" target="_blank">下载 ZIP</a>
                <a class="btn secondary" href="/federated_learning/setup">去联邦训练</a>
            </div>
        </div>
    </div>

    <div class="grid">
        <div>
            <div class="panel">
                <div class="panel-h">核心指标</div>
                <div class="panel-b"><div class="metrics" id="metrics"></div></div>
            </div>
            <div class="panel" style="margin-top:14px;">
                <div class="panel-h">基本信息</div>
                <div class="panel-b"><div class="kv" id="basic"></div></div>
            </div>
            <div class="panel" style="margin-top:14px;">
                <div class="panel-h">文件结构</div>
                <div class="panel-b"><div class="tree" id="tree">加载中...</div></div>
            </div>
        </div>
        <div>
            <div class="panel">
                <div class="panel-h">版本链</div>
                <div class="panel-b" id="versions"><div class="muted">加载中...</div></div>
            </div>
            <div class="panel" style="margin-top:14px;">
                <div class="panel-h">血缘关系</div>
                <div class="panel-b"><ul class="timeline" id="lineage"></ul></div>
            </div>
        </div>
    </div>
</div>
<script>
var assetId = ${assetId!0};

function escapeHtml(s) {
    return String(s == null ? '' : s)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}
function fmtMetric(v) {
    if (v == null) return '-';
    if (typeof v === 'number') return v <= 1 ? (v * 100).toFixed(1) + '%' : String(v);
    return String(v);
}
function fmtTime(t) {
    if (!t) return '-';
    if (typeof t === 'string') return t;
    return t;
}

function render(detail) {
    $('#name').text(detail.name || '未命名模型');
    $('#desc').text(detail.description || '暂无描述');
    $('#downloadBtn').attr('href', detail.downloadUrl || '#');
    var tags = '';
    tags += '<span class="tag blue">' + escapeHtml(detail.libraryTypeText || '') + '</span>';
    tags += '<span class="tag">' + escapeHtml(detail.version || 'v1') + '</span>';
    tags += '<span class="tag gray">' + escapeHtml(detail.framework || '-') + '</span>';
    tags += '<span class="tag orange">' + escapeHtml(detail.statusText || '-') + '</span>';
    if (detail.category) tags += '<span class="tag gray">' + escapeHtml(detail.category) + '</span>';
    if (detail.trainingMethod) tags += '<span class="tag">' + escapeHtml(detail.trainingMethod) + '</span>';
    $('#tags').html(tags);

    var m = detail.metrics || {};
    $('#metrics').html(
        '<div class="metric"><b>' + fmtMetric(m.accuracy) + '</b><span>Accuracy</span></div>'
        + '<div class="metric"><b>' + fmtMetric(m.f1) + '</b><span>F1</span></div>'
        + '<div class="metric"><b>' + fmtMetric(m.mAP) + '</b><span>mAP</span></div>'
    );

    var basic = '';
    function row(k, v) { basic += '<div class="k">' + k + '</div><div>' + escapeHtml(v == null || v === '' ? '-' : v) + '</div>'; }
    row('归属人', detail.ownerName);
    row('输入形状', detail.inputShape);
    row('输出类别', detail.outputClasses);
    row('联邦名', detail.flKey);
    row('来源数据集', detail.sourceDatasetName);
    row('文件数', detail.fileCount);
    row('大小', detail.totalSizeText);
    row('创建时间', fmtTime(detail.createdTime));
    $('#basic').html(basic);

    var versions = detail.versions || [];
    if (!versions.length) {
        $('#versions').html('<div class="muted">暂无版本信息</div>');
    } else {
        var vh = '';
        versions.forEach(function (v) {
            vh += '<div class="version' + (v.current ? ' current' : '') + '">'
                + '<div><a href="' + escapeHtml(v.detailUrl) + '">' + escapeHtml(v.name) + ' ' + escapeHtml(v.version || '') + '</a>'
                + '<div class="s muted">' + fmtTime(v.createdTime) + '</div></div>'
                + '<div>' + fmtMetric((v.metrics || {}).accuracy) + '</div></div>';
        });
        $('#versions').html(vh);
    }

    var lineage = detail.lineage || [];
    var lh = '';
    lineage.forEach(function (n) {
        lh += '<li class="' + (n.current ? 'current' : '') + '">'
            + '<a href="' + escapeHtml(n.href) + '">' + escapeHtml(n.title) + '</a>'
            + '<div class="s">' + escapeHtml(n.subtitle || '') + ' · ' + fmtTime(n.time) + '</div></li>';
    });
    $('#lineage').html(lh || '<li><div class="s">暂无血缘信息</div></li>');

    var tree = ((detail.storage || {}).fileTree) || [];
    if (!tree.length) {
        $('#tree').text('暂无文件');
    } else {
        var th = '';
        tree.forEach(function (f) {
            th += '<div>' + escapeHtml(f.path) + '  <span style="color:#94a3b8">(' + escapeHtml(f.sizeText) + ')</span></div>';
        });
        if (detail.storage && detail.storage.truncated) th += '<div style="color:#fbbf24">… 已截断，仅显示部分文件</div>';
        $('#tree').html(th);
    }
}

$.getJSON('/api/models/' + assetId).done(function (res) {
    if (res.code !== 200 || !res.data) {
        $('#name').text('模型不存在');
        return;
    }
    render(res.data);
}).fail(function () {
    $('#name').text('加载失败');
});
</script>
</body>
</html>
