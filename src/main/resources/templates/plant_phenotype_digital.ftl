<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>未来植物 · 数字化植株</title>
    <script src="/static/js/jquery-3.4.1.min.js"></script>
    <script src="/static/js/plant-corn-3d.js"></script>
    <link rel="stylesheet" href="/static/layui/css/layui.css">
    <link rel="stylesheet" href="/static/css/plant.css">
    <style>
        .pheno-layout { display:grid; grid-template-columns: 1fr 320px; gap:14px; }
        .field-canvas {
            position:relative; min-height:420px; border-radius:16px; overflow:hidden;
            background:
                linear-gradient(180deg, rgba(186,230,253,.35) 0%, rgba(167,243,208,.15) 42%, rgba(120,160,90,.25) 42%, rgba(90,130,60,.45) 100%),
                #dbeafe;
            border:1px solid #d7e5df;
        }
        .field-grid {
            display:grid; grid-template-columns: repeat(3, 1fr); gap:12px;
            padding:24px 18px 16px; height:100%; box-sizing:border-box;
            align-items:end;
        }
        .corn-slot {
            position:relative; text-align:center;
            transition: transform .18s ease;
            user-select: none;
        }
        .corn-slot:hover, .corn-slot.active { transform: translateY(-4px); }
        .corn-slot.active .plant-stage {
            box-shadow: 0 0 0 2px rgba(0,150,136,.5), 0 14px 26px rgba(0,150,136,.2);
        }
        .plant-stage {
            position:relative; height:236px; margin:0 auto; max-width:158px;
            border-radius: 16px;
            background:
                radial-gradient(ellipse at 50% 100%, rgba(255,255,255,.4), transparent 55%),
                linear-gradient(180deg, rgba(255,255,255,.08), rgba(255,255,255,0));
            overflow: hidden;
        }
        .plant-stage canvas {
            display:block; width:100%; height:100%;
            cursor: grab; touch-action: none;
        }
        .plant-stage canvas:active { cursor: grabbing; }
        .hint-rotate {
            position:absolute; left:50%; bottom:6px; transform:translateX(-50%);
            font-size:10px; color:rgba(20,53,47,.42); pointer-events:none;
            opacity:0; transition:opacity .2s; white-space:nowrap;
            background: rgba(255,255,255,.55); padding:1px 7px; border-radius:999px;
        }
        .corn-slot:hover .hint-rotate { opacity:1; }
        .slot-tag {
            display:inline-block; margin-top:6px; padding:2px 8px; border-radius:999px;
            background:rgba(255,255,255,.85); border:1px solid #cfe8e1;
            font-size:11px; font-weight:700; color:#0f766e; cursor:pointer;
        }
        .detail-panel .kv { display:grid; grid-template-columns: 88px 1fr; gap:8px 10px; font-size:13px; }
        .detail-panel .kv b { color:#64748b; font-weight:600; }
        .detail-panel .kv span { color:#14352f; font-weight:700; }
        .metric-chips { display:flex; flex-wrap:wrap; gap:8px; margin-top:12px; }
        .metric-chip {
            min-width:88px; padding:8px 10px; border-radius:10px; background:#f3faf7; border:1px solid #d7e5df;
        }
        .metric-chip .n { font-size:11px; color:#64748b; }
        .metric-chip .v { font-size:16px; font-weight:800; color:#0f766e; margin-top:2px; }
        .hover-tip {
            position:absolute; z-index:5; pointer-events:none; display:none;
            min-width:160px; max-width:220px; padding:10px 12px; border-radius:10px;
            background:rgba(15,40,35,.92); color:#fff; font-size:12px; line-height:1.5;
            box-shadow:0 12px 28px rgba(0,0,0,.18);
        }
        .hover-tip strong { display:block; font-size:13px; margin-bottom:4px; }
        @media (max-width: 980px) {
            .pheno-layout { grid-template-columns: 1fr; }
            .field-grid { grid-template-columns: repeat(2, 1fr); }
        }
    </style>
</head>
<body>
<div class="fp-page">
    <div class="fp-hero">
        <div class="fp-hero-main">
            <div class="fp-kicker">Phenotype · Digital Twin</div>
            <h1 class="fp-title">数字化植株</h1>
            <div class="fp-subtitle">按表型参数生成可旋转的 3D 玉米；左右拖动单株旋转，点击查看株高、叶片、生育期等。当前为 Mock 数据，模型接入后自动驱动。</div>
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
        <a class="fp-subtab active" href="/plant/phenotype/digital">数字化植株</a>
        <a class="fp-subtab" href="/plant/phenotype/metrics">表型指标</a>
        <a class="fp-subtab" href="/plant/phenotype/media">多模态影像</a>
        <a class="fp-subtab" href="/plant/phenotype/jobs">分析任务</a>
    </div>

    <div class="pheno-layout">
        <div class="fp-panel" style="margin:0;">
            <div class="fp-panel-hd">
                <div>
                    <h3>植株阵列</h3>
                    <div class="desc">形态由株高 / 叶片数 / 生育期驱动 · 左右拖动单株 3D 旋转</div>
                </div>
            </div>
            <div class="fp-panel-bd" style="padding-top:0;">
                <div class="field-canvas" id="fieldCanvas">
                    <div class="field-grid" id="fieldGrid"></div>
                    <div class="hover-tip" id="hoverTip"></div>
                </div>
            </div>
        </div>
        <div class="fp-panel detail-panel" style="margin:0;">
            <div class="fp-panel-hd">
                <div>
                    <h3 id="detailTitle">选择植株</h3>
                    <div class="desc" id="detailSub">点击或悬停左侧植株</div>
                </div>
            </div>
            <div class="fp-panel-bd">
                <div class="kv" id="detailKv">
                    <b>提示</b><span>先选择一株玉米</span>
                </div>
                <div class="metric-chips" id="detailMetrics"></div>
            </div>
        </div>
    </div>
</div>

<script>
(function () {
    var DEVICE_KEY = 'plant-ctrl-01';
    var specimens = [];
    var selected = null;
    var views = {};
    var angleMap = {};

    function destroyViews() {
        Object.keys(views).forEach(function (k) {
            if (views[k]) {
                angleMap[k] = views[k].angle;
                views[k].destroy();
            }
        });
        views = {};
    }

    function renderField() {
        destroyViews();
        var $grid = $('#fieldGrid').empty();
        if (!window.CornPlant3D) {
            $grid.html('<div style="padding:24px;color:#64748b;">3D 植株脚本未加载</div>');
            return;
        }
        specimens.forEach(function (p) {
            var $slot = $('<div class="corn-slot"></div>')
                .attr('data-code', p.plantCode)
                .toggleClass('active', selected === p.plantCode);
            var $stage = $('<div class="plant-stage"></div>');
            var canvas = document.createElement('canvas');
            $stage.append(canvas);
            $stage.append('<div class="hint-rotate">← 拖动旋转 →</div>');
            $slot.append($stage);
            $slot.append('<div class="slot-tag">' + p.plantCode + ' · ' + (p.slotCode || '') + '</div>');
            $grid.append($slot);

            var view = window.CornPlant3D.create(canvas, p);
            if (angleMap[p.plantCode] != null) view.angle = angleMap[p.plantCode];
            view.onSelect = function (sp) { showDetail(sp, true); };
            views[p.plantCode] = view;
        });
        // 布局完成后再校准 canvas 尺寸
        requestAnimationFrame(function () {
            Object.keys(views).forEach(function (k) {
                if (views[k]) views[k]._resize();
            });
        });
    }

    function showDetail(p, skipRerender) {
        if (!p) return;
        selected = p.plantCode;
        $('#detailTitle').text(p.plantCode + ' · ' + (p.slotCode || ''));
        $('#detailSub').text((p.cropType || '玉米') + ' / ' + (p.variety || '') + ' / ' + (p.growthStage || ''));
        var latest = p.latest || {};
        $('#detailKv').html(
            '<b>穴位</b><span>' + (p.slotCode || '--') + '</span>' +
            '<b>生育期</b><span>' + (p.growthStage || '--') + '</span>' +
            '<b>状态</b><span>' + (p.status || '--') + '</span>' +
            '<b>位置</b><span>行 ' + (p.posRow || '-') + ' / 列 ' + (p.posCol || '-') + '</span>'
        );
        var chips = [
            ['株高', latest.height_cm, 'cm'],
            ['叶片数', latest.leaf_count, '片'],
            ['茎粗', latest.stem_diameter_mm, 'mm'],
            ['叶面积', latest.leaf_area_cm2, 'cm²'],
            ['SPAD', latest.spad, '']
        ];
        var html = '';
        chips.forEach(function (c) {
            html += '<div class="metric-chip"><div class="n">' + c[0] + '</div><div class="v">' +
                (c[1] == null ? '--' : c[1]) + (c[2] ? '<small style="font-size:11px;font-weight:600;"> ' + c[2] + '</small>' : '') +
                '</div></div>';
        });
        $('#detailMetrics').html(html);
        if (skipRerender) {
            $('.corn-slot').removeClass('active');
            $('.corn-slot[data-code="' + selected + '"]').addClass('active');
        } else {
            renderField();
        }
    }

    function tipHtml(p) {
        var l = p.latest || {};
        return '<strong>' + p.plantCode + ' · ' + (p.slotCode || '') + '</strong>' +
            '生育期 ' + (p.growthStage || '--') + '<br>' +
            '株高 ' + (l.height_cm == null ? '--' : l.height_cm + ' cm') + '<br>' +
            '叶片 ' + (l.leaf_count == null ? '--' : l.leaf_count + ' 片');
    }

    function load() {
        $.getJSON('/plant/api/phenotype/specimens', {deviceKey: DEVICE_KEY}, function (resp) {
            if (resp.code !== 0) return;
            specimens = resp.data || [];
            renderField();
            if (!selected && specimens.length) showDetail(specimens[0], true);
            else if (selected) {
                var cur = specimens.filter(function (x) { return x.plantCode === selected; })[0];
                if (cur) showDetail(cur, true);
            }
        });
    }

    $(document).on('mouseenter', '.corn-slot', function (e) {
        var code = $(this).data('code');
        var p = specimens.filter(function (x) { return x.plantCode === code; })[0];
        if (!p) return;
        var $tip = $('#hoverTip').html(tipHtml(p)).show();
        var canvas = $('#fieldCanvas')[0].getBoundingClientRect();
        $tip.css({
            left: Math.min(e.clientX - canvas.left + 12, canvas.width - 180),
            top: Math.max(8, e.clientY - canvas.top - 20)
        });
    });
    $(document).on('mousemove', '.corn-slot', function (e) {
        var canvas = $('#fieldCanvas')[0].getBoundingClientRect();
        $('#hoverTip').css({
            left: Math.min(e.clientX - canvas.left + 12, canvas.width - 180),
            top: Math.max(8, e.clientY - canvas.top - 20)
        });
    });
    $(document).on('mouseleave', '.corn-slot', function () {
        $('#hoverTip').hide();
    });
    $(document).on('click', '.slot-tag', function () {
        var code = $(this).closest('.corn-slot').data('code');
        var p = specimens.filter(function (x) { return x.plantCode === code; })[0];
        showDetail(p, true);
    });
    $('#btnRefresh').on('click', load);
    $(window).on('resize', function () {
        Object.keys(views).forEach(function (k) {
            if (views[k]) views[k]._resize();
        });
    });
    load();
})();
</script>
</body>
</html>
