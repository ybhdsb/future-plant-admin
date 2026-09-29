<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>未来植物 · 系统总览</title>
    <script src="/static/js/jquery-3.4.1.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js"></script>
    <link rel="stylesheet" href="/static/layui/css/layui.css">
    <link rel="stylesheet" href="/static/css/plant.css">
</head>
<body>
<div class="fp-page">
    <div class="fp-hero">
        <div class="fp-hero-main">
            <div class="fp-kicker">Future Plant · V1</div>
            <h1 class="fp-title">系统总览</h1>
            <div class="fp-subtitle">实时查看控制器在线状态、环境/营养液测点、执行器摘要与运行事件。前期支持 Mock 数据，联调后可一键切到实机。</div>
        </div>
        <div class="fp-hero-aside">
            <div class="fp-status-row">
                <span class="fp-pill danger" id="onlinePill"><span class="fp-dot"></span>离线</span>
                <span class="fp-pill info" id="modePill">Mock 模式</span>
                <span class="fp-pill neutral" id="freshPill">数据新鲜度 --</span>
            </div>
            <div class="fp-toolbar">
                <label class="fp-switch"><input type="checkbox" id="mockToggle" checked> 使用 Mock</label>
                <button class="fp-btn secondary" id="btnRefresh">刷新</button>
                <a class="fp-btn" href="/plant/control/led">去控制</a>
            </div>
        </div>
    </div>

    <div class="fp-tabs">
        <a class="fp-tab active" href="/plant/dashboard">系统总览</a>
        <a class="fp-tab" href="/plant/control/led">设备控制</a>
        <a class="fp-tab" href="/plant/phenotype/digital">作物表型</a>
        <a class="fp-tab" href="/plant/history">历史数据</a>
        <a class="fp-tab" href="/plant/data">数据管理</a>
    </div>

    <div class="fp-grid-4" style="margin-bottom:12px;" id="kpiRow"></div>
    <div class="fp-grid-4" style="margin-bottom:12px;" id="metricGrid"></div>

    <div class="fp-grid-2">
        <div class="fp-panel">
            <div class="fp-panel-hd">
                <div>
                    <h3>近 6 小时趋势</h3>
                    <div class="desc">温度 / 湿度 / pH / EC</div>
                </div>
            </div>
            <div class="fp-panel-bd">
                <div id="trendChart" style="height:320px;"></div>
            </div>
        </div>
        <div class="fp-panel">
            <div class="fp-panel-hd">
                <div>
                    <h3>执行器与事件</h3>
                    <div class="desc">当前状态 · 最近指令/告警</div>
                </div>
            </div>
            <div class="fp-panel-bd">
                <div id="actuatorChips" style="display:flex;flex-wrap:wrap;gap:8px;margin-bottom:14px;"></div>
                <div id="eventList"></div>
            </div>
        </div>
    </div>
</div>

<script>
(function () {
    var DEVICE_KEY = 'plant-ctrl-01';
    var chart = echarts.init(document.getElementById('trendChart'));
    var LABELS = {
        'air.temperature': {name:'空气温度', unit:'°C'},
        'air.humidity': {name:'空气湿度', unit:'%RH'},
        'air.co2': {name:'CO₂', unit:'ppm'},
        'nutrient.ph': {name:'营养液 pH', unit:''},
        'nutrient.ec': {name:'营养液 EC', unit:'mS/cm'},
        'nutrient.do': {name:'溶氧', unit:'mg/L'},
        'nutrient.level': {name:'液位', unit:''}
    };

    function fmt(v) {
        if (!v) return '--';
        var d = new Date(v);
        if (isNaN(d.getTime())) return String(v);
        function p(n){ return n < 10 ? '0'+n : n; }
        return p(d.getMonth()+1)+'-'+p(d.getDate())+' '+p(d.getHours())+':'+p(d.getMinutes())+':'+p(d.getSeconds());
    }

    function ageText(v) {
        if (!v) return '暂无采样';
        var d = new Date(v).getTime();
        if (isNaN(d)) return '暂无采样';
        var sec = Math.max(0, Math.floor((Date.now() - d) / 1000));
        if (sec < 60) return sec + ' 秒前更新';
        if (sec < 3600) return Math.floor(sec/60) + ' 分钟前更新';
        return Math.floor(sec/3600) + ' 小时前更新';
    }

    function renderKpi(summary, data) {
        var items = [
            {label:'测点覆盖', value:(summary.metricCount||0)+' 项', tip:'当前有数值的关键测点'},
            {label:'LED 开启', value:(summary.ledOnCount||0)+' / 8', tip:'八通道亮灯数量'},
            {label:'泵开启', value:(summary.pumpOnCount||0)+' 台', tip:'水泵 / 氧泵'},
            {label:'今日指令', value:(summary.commandCount||0)+' 条', tip:'最近指令日志条数'}
        ];
        var html = '';
        items.forEach(function (it) {
            html += '<div class="fp-metric"><div class="label">'+it.label+'</div><div class="value" style="font-size:24px;">'+it.value+'</div><div class="meta">'+it.tip+'</div></div>';
        });
        $('#kpiRow').html(html);
    }

    function renderMetrics(metrics) {
        var keys = ['air.temperature','air.humidity','nutrient.ph','nutrient.ec','air.co2','nutrient.do','nutrient.level'];
        var html = '';
        keys.forEach(function (k) {
            var conf = LABELS[k];
            var m = metrics && metrics[k];
            var val = m && m.value != null ? m.value : '--';
            var unit = (m && m.unit) || (conf && conf.unit) || '';
            html += '<div class="fp-metric"><div class="label">'+(conf?conf.name:k)+'</div>'
                + '<div class="value">'+val+'<span class="unit">'+unit+'</span></div>'
                + '<div class="meta">'+(m ? ageText(m.sampledAt)+' · '+(m.quality||'GOOD') : '未接入 / 等待数据')+'</div></div>';
        });
        $('#metricGrid').html(html);
    }

    function renderActuators(list) {
        if (!list || !list.length) {
            $('#actuatorChips').html('<span class="fp-muted">暂无执行器状态</span>');
            return;
        }
        var html = '';
        list.forEach(function (a) {
            var st = a.state || {};
            var on = !!st.on;
            var extra = st.brightness != null ? (' ' + st.brightness + '%') : '';
            html += '<span class="fp-chip '+(on?'':'off')+'">'+a.actuatorId+(on?' · 开':' · 关')+extra+'</span>';
        });
        $('#actuatorChips').html(html);
    }

    function renderEvents(cmds, events) {
        var html = '';
        (cmds || []).slice(0, 6).forEach(function (c) {
            html += '<div class="fp-list-row"><div><div class="fp-strong">指令 '+c.commandType+'</div><div class="fp-muted">'+(c.commandId||'')+'</div></div>'
                + '<div style="text-align:right;"><div class="fp-strong">'+c.status+'</div><div class="fp-muted">'+fmt(c.createdAt)+'</div></div></div>';
        });
        (events || []).slice(0, 6).forEach(function (e) {
            html += '<div class="fp-list-row"><div><div class="fp-strong">['+e.level+'] '+(e.message||e.code||'')+'</div></div>'
                + '<div class="fp-muted">'+fmt(e.createdAt)+'</div></div>';
        });
        $('#eventList').html(html || '<div class="fp-empty">暂无事件，系统运行正常时这里会滚动显示</div>');
    }

    function renderTrend(series) {
        var metrics = ['air.temperature','air.humidity','nutrient.ph','nutrient.ec'];
        var colors = ['#0f766e','#0284c7','#d97706','#7c3aed'];
        var seriesOpt = [];
        var legend = [];
        metrics.forEach(function (m, idx) {
            var arr = (series && series[m]) || [];
            legend.push(LABELS[m].name);
            seriesOpt.push({
                name: LABELS[m].name,
                type: 'line',
                smooth: true,
                showSymbol: false,
                data: arr.map(function (p) { return [p.sampledAt, p.value]; }),
                lineStyle: { width: 2, color: colors[idx] },
                itemStyle: { color: colors[idx] },
                areaStyle: idx < 2 ? { opacity: 0.06 } : undefined
            });
        });
        chart.setOption({
            color: colors,
            legend: { data: legend, top: 0 },
            tooltip: { trigger: 'axis' },
            grid: { left: 42, right: 18, top: 36, bottom: 28 },
            xAxis: { type: 'time', axisLabel: { color: '#94a3b8' }, axisLine: { lineStyle: { color: '#dbe7e2' } } },
            yAxis: { type: 'value', scale: true, splitLine: { lineStyle: { color: '#edf3f0' } } },
            series: seriesOpt
        }, true);
    }

    function load() {
        $.getJSON('/plant/api/dashboard', { deviceKey: DEVICE_KEY }, function (resp) {
            if (resp.code !== 0) return;
            var d = resp.data || {};
            var online = !!d.online;
            $('#onlinePill').attr('class', 'fp-pill ' + (online ? 'ok' : 'danger'))
                .html('<span class="fp-dot"></span>' + (online ? '控制器在线' : '控制器离线'));
            $('#modePill').text(d.mockEnabled ? 'Mock 模式' : '实机模式');
            $('#mockToggle').prop('checked', !!d.mockEnabled);
            var last = d.summary && d.summary.lastSampleAt;
            $('#freshPill').text(ageText(last));
            renderKpi(d.summary || {}, d);
            renderMetrics(d.metrics || {});
            renderActuators(d.actuators || []);
            renderEvents(d.recentCommands || [], d.events || []);
            renderTrend(d.series || {});
        });
    }

    $('#btnRefresh').on('click', load);
    $('#mockToggle').on('change', function () {
        $.ajax({
            url: '/plant/api/mock',
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({ deviceKey: DEVICE_KEY, enabled: $(this).is(':checked') }),
            success: load
        });
    });
    load();
    setInterval(load, 5000);
    window.addEventListener('resize', function () { chart.resize(); });
})();
</script>
</body>
</html>
