<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>数据集详情</title>
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
        .actions { display: flex; gap: 8px; flex-wrap: wrap; }
        .btn { height: 34px; padding: 0 14px; border: 0; border-radius: 6px; background: #009688; color: #fff; text-decoration: none; display: inline-flex; align-items: center; }
        .btn.secondary { background: #334155; }
        .grid { display: grid; grid-template-columns: 1.2fr .8fr; gap: 14px; margin-top: 14px; }
        .panel { background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; box-shadow: 0 10px 24px rgba(15,23,42,.04); }
        .panel-h { padding: 14px 16px; border-bottom: 1px solid #edf0f4; font-weight: 700; }
        .panel-b { padding: 14px 16px; }
        .kv { display: grid; grid-template-columns: 110px 1fr; gap: 8px 10px; font-size: 13px; }
        .kv .k { color: #64748b; }
        .metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; }
        .metric { background: #f8fafc; border-radius: 8px; padding: 12px; }
        .metric b { display: block; font-size: 18px; color: #0f766e; }
        .metric span { color: #64748b; font-size: 12px; }
        .preview-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; }
        .preview { background: #0f172a; border-radius: 8px; overflow: hidden; aspect-ratio: 1; display: flex; align-items: center; justify-content: center; }
        .preview img { width: 100%; height: 100%; object-fit: cover; display: block; }
        .preview .cap { position: absolute; } /* unused */
        .bars { display: grid; gap: 8px; }
        .bar-row { display: grid; grid-template-columns: 90px 1fr 36px; gap: 8px; align-items: center; font-size: 12px; }
        .bar-track { height: 8px; background: #e2e8f0; border-radius: 999px; overflow: hidden; }
        .bar-fill { height: 100%; background: linear-gradient(90deg, #14b8a6, #0f766e); }
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
        .tree { max-height: 320px; overflow: auto; font-family: Menlo, Consolas, monospace; font-size: 12px; background: #0f172a; color: #d1fae5; border-radius: 8px; padding: 12px; }
        .tree div { padding: 2px 0; white-space: nowrap; }
        .muted { color: #64748b; }
        @media (max-width: 900px) {
            .grid, .metrics, .preview-grid { grid-template-columns: 1fr 1fr; }
            .hero-top { flex-direction: column; }
        }
    </style>
</head>
<body>
<div class="page">
    <a class="back" href="/datasets">← 返回数据集库</a>
    <div class="hero">
        <div class="hero-top">
            <div>
                <h1 id="name">加载中...</h1>
                <div class="sub" id="desc"></div>
                <div class="tags" id="tags"></div>
            </div>
            <div class="actions">
                <a class="btn" id="downloadBtn" href="#" target="_blank">下载 ZIP</a>
                <a class="btn secondary" href="/federated_learning/setup">用于联邦训练</a>
            </div>
        </div>
    </div>

    <div class="grid">
        <div>
            <div class="panel">
                <div class="panel-h">统计摘要</div>
                <div class="panel-b"><div class="metrics" id="metrics"></div></div>
            </div>
            <div class="panel" style="margin-top:14px;">
                <div class="panel-h">样本预览</div>
                <div class="panel-b"><div class="preview-grid" id="previews"><div class="muted">加载中...</div></div></div>
            </div>
            <div class="panel" style="margin-top:14px;">
                <div class="panel-h">文件结构</div>
                <div class="panel-b"><div class="tree" id="tree">加载中...</div></div>
            </div>
        </div>
        <div>
            <div class="panel">
                <div class="panel-h">类别分布</div>
                <div class="panel-b" id="classes"><div class="muted">加载中...</div></div>
            </div>
            <div class="panel" style="margin-top:14px;">
                <div class="panel-h">基本信息</div>
                <div class="panel-b"><div class="kv" id="basic"></div></div>
            </div>
            <div class="panel" style="margin-top:14px;">
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
function fmtTime(t) { return t ? String(t) : '-'; }

function render(detail) {
    var storage = detail.storage || {};
    $('#name').text(detail.name || '未命名数据集');
    $('#desc').text(detail.description || '暂无描述');
    $('#downloadBtn').attr('href', detail.downloadUrl || '#');

    var tags = '';
    tags += '<span class="tag blue">' + escapeHtml(detail.datasetTypeText || '') + '</span>';
    if (detail.category) tags += '<span class="tag">' + escapeHtml(detail.category) + '</span>';
    if (detail.labelFormat) tags += '<span class="tag gray">' + escapeHtml(detail.labelFormat) + '</span>';
    if (detail.statusText) tags += '<span class="tag gray">' + escapeHtml(detail.statusText) + '</span>';
    $('#tags').html(tags);

    $('#metrics').html(
        '<div class="metric"><b>' + escapeHtml(detail.sampleCount == null ? '-' : detail.sampleCount) + '</b><span>样本数</span></div>'
        + '<div class="metric"><b>' + escapeHtml(storage.imageCount == null ? '-' : storage.imageCount) + '</b><span>图片数</span></div>'
        + '<div class="metric"><b>' + escapeHtml(detail.classCount || storage.detectedClassCount || '-') + '</b><span>类别数</span></div>'
        + '<div class="metric"><b>' + escapeHtml(detail.totalSizeText || storage.scannedSizeText || '-') + '</b><span>大小</span></div>'
    );

    var previews = storage.previews || [];
    if (!previews.length) {
        $('#previews').html('<div class="muted">暂无预览图</div>');
    } else {
        var ph = '';
        previews.forEach(function (p) {
            ph += '<div class="preview" title="' + escapeHtml(p.path) + '"><img src="' + escapeHtml(p.url) + '" alt="' + escapeHtml(p.name) + '"></div>';
        });
        $('#previews').html(ph);
    }

    var dist = storage.classDistribution || {};
    var keys = Object.keys(dist);
    if (!keys.length) {
        $('#classes').html('<div class="muted">未检测到类别目录分布</div>');
    } else {
        var max = 1;
        keys.forEach(function (k) { if (dist[k] > max) max = dist[k]; });
        var ch = '<div class="bars">';
        keys.forEach(function (k) {
            var pct = Math.round(dist[k] * 100 / max);
            ch += '<div class="bar-row"><div>' + escapeHtml(k) + '</div><div class="bar-track"><div class="bar-fill" style="width:' + pct + '%"></div></div><div>' + dist[k] + '</div></div>';
        });
        ch += '</div>';
        $('#classes').html(ch);
    }

    var basic = '';
    function row(k, v) { basic += '<div class="k">' + k + '</div><div>' + escapeHtml(v == null || v === '' ? '-' : v) + '</div>'; }
    row('归属人', detail.ownerName);
    row('联邦名', detail.flKey);
    row('标注格式', detail.labelFormat);
    row('空文件', storage.emptyFileCount);
    row('扩展名统计', JSON.stringify(storage.extensionStats || {}));
    row('创建时间', fmtTime(detail.createdTime));
    $('#basic').html(basic);

    var versions = detail.versions || [];
    if (!versions.length) {
        $('#versions').html('<div class="muted">暂无版本信息</div>');
    } else {
        var vh = '';
        versions.forEach(function (v) {
            vh += '<div class="version' + (v.current ? ' current' : '') + '">'
                + '<div><a href="' + escapeHtml(v.detailUrl) + '">' + escapeHtml(v.name) + '</a>'
                + '<div class="muted" style="font-size:12px;margin-top:4px;">' + escapeHtml(v.versionLabel || '') + ' · ' + fmtTime(v.createdTime) + '</div></div>'
                + '<div>' + escapeHtml(v.sampleCount == null ? '-' : v.sampleCount) + '</div></div>';
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

    var tree = storage.fileTree || [];
    if (!tree.length) {
        $('#tree').text('暂无文件');
    } else {
        var th = '';
        tree.forEach(function (f) {
            th += '<div>' + escapeHtml(f.path) + '  <span style="color:#94a3b8">(' + escapeHtml(f.sizeText) + ')</span></div>';
        });
        if (storage.truncated) th += '<div style="color:#fbbf24">… 已截断，仅显示部分文件</div>';
        $('#tree').html(th);
    }
}

$.getJSON('/api/datasets/' + assetId).done(function (res) {
    if (res.code !== 200 || !res.data) {
        $('#name').text('数据集不存在');
        return;
    }
    render(res.data);
}).fail(function () {
    $('#name').text('加载失败');
});
</script>
</body>
</html>
