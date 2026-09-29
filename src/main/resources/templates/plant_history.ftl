<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>未来植物 · 历史数据</title>
    <script src="/static/js/jquery-3.4.1.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js"></script>
    <link rel="stylesheet" href="/static/layui/css/layui.css">
    <link rel="stylesheet" href="/static/css/plant.css">
    <style>
        .metric-checks { display:flex; flex-wrap:wrap; gap:10px 14px; }
        .metric-checks label { font-size:13px; color:#4b635d; display:inline-flex; align-items:center; gap:6px; }
        .media-grid { display:grid; grid-template-columns: repeat(4, minmax(0,1fr)); gap:12px; }
        .media-card {
            border:1px solid #d7e5df; border-radius:12px; overflow:hidden; background:#fff;
        }
        .media-thumb {
            height:140px; background:linear-gradient(135deg,#ecfdf5,#e0f2fe);
            display:flex; align-items:center; justify-content:center; color:#0f766e; font-weight:700;
        }
        .media-thumb img { width:100%; height:100%; object-fit:cover; display:block; }
        .media-cap { padding:10px 12px; font-size:12px; color:#64748b; }
        @media (max-width: 900px) { .media-grid { grid-template-columns:1fr 1fr; } }
    </style>
</head>
<body>
<div class="fp-page">
    <div class="fp-hero">
        <div class="fp-hero-main">
            <div class="fp-kicker">History & Media</div>
            <h1 class="fp-title">历史数据</h1>
            <div class="fp-subtitle">对应任务 S08：按测点与时间查询趋势曲线，查看数据明细表与图片/采集记录。</div>
        </div>
        <div class="fp-hero-aside">
            <div class="fp-toolbar">
                <button class="fp-btn secondary" id="btnLast6h">近 6 小时</button>
                <button class="fp-btn secondary" id="btnLast24h">近 24 小时</button>
                <button class="fp-btn" id="btnQuery">查询</button>
            </div>
        </div>
    </div>

    <div class="fp-tabs">
        <a class="fp-tab" href="/plant/dashboard">系统总览</a>
        <a class="fp-tab" href="/plant/control/led">设备控制</a>
        <a class="fp-tab" href="/plant/phenotype/digital">作物表型</a>
        <a class="fp-tab active" href="/plant/history">历史数据</a>
        <a class="fp-tab" href="/plant/data">数据管理</a>
    </div>
    <div class="fp-subtabs">
        <a class="fp-subtab active" href="/plant/history">环境历史</a>
        <a class="fp-subtab" href="/plant/phenotype/metrics">表型历史</a>
    </div>

    <div class="fp-panel" style="margin-bottom:12px;">
        <div class="fp-panel-bd">
            <div class="fp-toolbar" style="margin-bottom:12px;">
                <input type="text" class="fp-field" id="fromTime" style="width:180px;" placeholder="开始时间">
                <span class="fp-muted">至</span>
                <input type="text" class="fp-field" id="toTime" style="width:180px;" placeholder="结束时间">
            </div>
            <div class="metric-checks" id="metricChecks">
                <label><input type="checkbox" value="air.temperature" checked> 空气温度</label>
                <label><input type="checkbox" value="air.humidity" checked> 空气湿度</label>
                <label><input type="checkbox" value="nutrient.ph" checked> 营养液 pH</label>
                <label><input type="checkbox" value="nutrient.ec" checked> 营养液 EC</label>
                <label><input type="checkbox" value="air.co2"> CO₂</label>
            </div>
        </div>
    </div>

    <div class="fp-panel" style="margin-bottom:12px;">
        <div class="fp-panel-hd">
            <div>
                <h3>趋势曲线</h3>
                <div class="desc">支持多测点叠加对比</div>
            </div>
            <span class="fp-pill neutral" id="pointCount">0 个点</span>
        </div>
        <div class="fp-panel-bd">
            <div id="chart" style="height:380px;"></div>
            <div id="emptyHint" class="fp-empty" style="display:none;">暂无曲线数据。请确认 Mock 已开启，或等待边缘上报后再查询。</div>
        </div>
    </div>

    <div class="fp-grid-2">
        <div class="fp-panel">
            <div class="fp-panel-hd">
                <div>
                    <h3>数据明细</h3>
                    <div class="desc">与上方筛选条件一致</div>
                </div>
            </div>
            <div class="fp-panel-bd fp-table-wrap" style="max-height:360px;">
                <table class="fp-table">
                    <thead>
                    <tr><th>时间</th><th>测点</th><th>数值</th><th>单位</th><th>质量</th></tr>
                    </thead>
                    <tbody id="tableBody"></tbody>
                </table>
            </div>
        </div>
        <div class="fp-panel">
            <div class="fp-panel-hd">
                <div>
                    <h3>图片 / 采集记录</h3>
                    <div class="desc">控制页触发抓拍后在此查看</div>
                </div>
                <button class="fp-btn secondary" id="btnReloadMedia">刷新</button>
            </div>
            <div class="fp-panel-bd">
                <div class="media-grid" id="mediaGrid"></div>
            </div>
        </div>
    </div>
</div>

<script src="/static/layui/layui.js"></script>
<script>
(function () {
    var DEVICE_KEY = 'plant-ctrl-01';
    var chart = echarts.init(document.getElementById('chart'));
    var NAMES = {
        'air.temperature':'空气温度',
        'air.humidity':'空气湿度',
        'air.co2':'CO₂',
        'nutrient.ph':'营养液 pH',
        'nutrient.ec':'营养液 EC'
    };
    var COLORS = ['#0f766e','#0284c7','#d97706','#7c3aed','#dc2626'];

    function pad(n){ return n < 10 ? '0'+n : ''+n; }
    function fmt(d){
        return d.getFullYear()+'-'+pad(d.getMonth()+1)+'-'+pad(d.getDate())+' '+pad(d.getHours())+':'+pad(d.getMinutes())+':'+pad(d.getSeconds());
    }
    function setRange(hours){
        var to = new Date();
        var from = new Date(to.getTime() - hours * 3600 * 1000);
        $('#fromTime').val(fmt(from));
        $('#toTime').val(fmt(to));
    }
    function selectedMetrics(){
        var arr = [];
        $('#metricChecks input:checked').each(function(){ arr.push($(this).val()); });
        return arr;
    }

    function loadChartAndTable(){
        var metrics = selectedMetrics();
        if (!metrics.length) {
            layer && layer.msg('请至少选择一个测点');
            return;
        }
        $.getJSON('/plant/api/metrics/multi', {
            deviceKey: DEVICE_KEY,
            metrics: metrics.join(','),
            from: $('#fromTime').val(),
            to: $('#toTime').val(),
            limit: 800
        }, function(resp){
            var data = resp.data || {};
            var series = [];
            var total = 0;
            var rows = [];
            metrics.forEach(function(m, idx){
                var arr = data[m] || [];
                total += arr.length;
                series.push({
                    name: NAMES[m] || m,
                    type: 'line',
                    smooth: true,
                    showSymbol: false,
                    data: arr.map(function(p){ return [p.sampledAt, p.value]; }),
                    lineStyle: { width: 2, color: COLORS[idx % COLORS.length] },
                    itemStyle: { color: COLORS[idx % COLORS.length] }
                });
                arr.slice(-40).reverse().forEach(function(p){
                    rows.push({t:p.sampledAt, m:m, v:p.value, u:p.unit||'', q:p.quality||''});
                });
            });
            $('#pointCount').text(total + ' 个点');
            if (!total) {
                $('#emptyHint').show();
                chart.clear();
            } else {
                $('#emptyHint').hide();
                chart.setOption({
                    legend: { top: 0 },
                    tooltip: { trigger: 'axis' },
                    grid: { left: 42, right: 18, top: 40, bottom: 28 },
                    xAxis: { type: 'time' },
                    yAxis: { type: 'value', scale: true, splitLine: { lineStyle: { color: '#edf3f0' } } },
                    series: series
                }, true);
            }
            rows.sort(function(a,b){ return new Date(b.t) - new Date(a.t); });
            var html = '';
            rows.slice(0, 80).forEach(function(r){
                html += '<tr><td>'+fmt(new Date(r.t))+'</td><td>'+(NAMES[r.m]||r.m)+'</td><td>'+r.v+'</td><td>'+r.u+'</td><td>'+r.q+'</td></tr>';
            });
            $('#tableBody').html(html || '<tr><td colspan="5" class="fp-empty">暂无明细</td></tr>');
        });
    }

    function loadMedia(){
        $.getJSON('/plant/api/media', {deviceKey: DEVICE_KEY, limit: 12}, function(resp){
            var list = resp.data || [];
            if (!list.length) {
                $('#mediaGrid').html('<div class="fp-empty" style="grid-column:1/-1;">暂无图片。可到「设备控制」触发抓拍（Mock 会生成占位记录）。</div>');
                return;
            }
            var html = '';
            list.forEach(function(m){
                var label = m.label || ('记录 #' + m.id);
                var thumb = '<div class="media-thumb"><img src="'+(m.url||'/static/images/plant/corn_plant_sample.png')+'" alt="'+label+'"></div>';
                html += '<div class="media-card">'+thumb+'<div class="media-cap">'+label+'<br>'+fmt(new Date(m.capturedAt))+'</div></div>';
            });
            $('#mediaGrid').html(html);
        });
    }

    var layer;
    layui.use(['laydate','layer'], function(){
        layer = layui.layer;
        var laydate = layui.laydate;
        laydate.render({elem:'#fromTime', type:'datetime', format:'yyyy-MM-dd HH:mm:ss'});
        laydate.render({elem:'#toTime', type:'datetime', format:'yyyy-MM-dd HH:mm:ss'});
    });

    setRange(6);
    $('#btnLast6h').on('click', function(){ setRange(6); loadChartAndTable(); });
    $('#btnLast24h').on('click', function(){ setRange(24); loadChartAndTable(); });
    $('#btnQuery').on('click', loadChartAndTable);
    $('#btnReloadMedia').on('click', loadMedia);
    loadChartAndTable();
    loadMedia();
    window.addEventListener('resize', function(){ chart.resize(); });
})();
</script>
</body>
</html>
