<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>未来植物 · 数据管理</title>
    <script src="/static/js/jquery-3.4.1.min.js"></script>
    <link rel="stylesheet" href="/static/layui/css/layui.css">
    <link rel="stylesheet" href="/static/css/plant.css">
    <style>
        .form-grid { display:grid; grid-template-columns: 120px 1fr; gap: 12px 10px; align-items:center; max-width:720px; }
        .form-grid label { color:#5f746e; font-size:13px; font-weight:700; }
        .stat-row { display:flex; gap:10px; flex-wrap:wrap; margin: 14px 0; }
    </style>
</head>
<body>
<div class="fp-page">
    <div class="fp-hero">
        <div class="fp-hero-main">
            <div class="fp-kicker">Export & Filter</div>
            <h1 class="fp-title">数据管理</h1>
            <div class="fp-subtitle">对应任务 S09：按设备、测点、时间筛选数据，预览后导出 CSV / Excel，便于实验记录与报表。</div>
        </div>
        <div class="fp-hero-aside">
            <div class="fp-toolbar">
                <button class="fp-btn secondary" id="btnPreview">预览数据</button>
                <button class="fp-btn" id="btnCsv">导出 CSV</button>
                <button class="fp-btn secondary" id="btnExcel">导出 Excel</button>
            </div>
        </div>
    </div>

    <div class="fp-tabs">
        <a class="fp-tab" href="/plant/dashboard">系统总览</a>
        <a class="fp-tab" href="/plant/control/led">设备控制</a>
        <a class="fp-tab" href="/plant/phenotype/digital">作物表型</a>
        <a class="fp-tab" href="/plant/history">历史数据</a>
        <a class="fp-tab active" href="/plant/data">数据管理</a>
    </div>

    <div class="fp-panel" style="margin-bottom:12px;">
        <div class="fp-panel-hd">
            <div>
                <h3>筛选条件</h3>
                <div class="desc">导出字段：device_key / metric / value / unit / quality / sampled_at / received_at</div>
            </div>
        </div>
        <div class="fp-panel-bd">
            <div class="form-grid">
                <label>设备 Key</label>
                <input class="fp-field" id="deviceKey" value="plant-ctrl-01" style="width:280px;">
                <label>测点</label>
                <select class="fp-field" id="metric" style="width:280px;">
                    <option value="">全部测点</option>
                    <option value="air.temperature">空气温度</option>
                    <option value="air.humidity">空气湿度</option>
                    <option value="air.co2">CO₂</option>
                    <option value="nutrient.ph">营养液 pH</option>
                    <option value="nutrient.ec">营养液 EC</option>
                </select>
                <label>开始时间</label>
                <input class="fp-field" id="fromTime" style="width:280px;">
                <label>结束时间</label>
                <input class="fp-field" id="toTime" style="width:280px;">
            </div>
            <div class="stat-row">
                <span class="fp-pill info" id="previewCount">尚未预览</span>
                <span class="fp-pill neutral">单次上限受 plant.export-max-rows 约束</span>
            </div>
        </div>
    </div>

    <div class="fp-panel">
        <div class="fp-panel-hd">
            <div>
                <h3>数据预览</h3>
                <div class="desc">最多展示前 100 条，完整数据请导出</div>
            </div>
        </div>
        <div class="fp-panel-bd fp-table-wrap">
            <table class="fp-table">
                <thead>
                <tr>
                    <th>采样时间</th>
                    <th>设备</th>
                    <th>测点</th>
                    <th>数值</th>
                    <th>单位</th>
                    <th>质量</th>
                    <th>入库时间</th>
                </tr>
                </thead>
                <tbody id="previewBody">
                <tr><td colspan="7" class="fp-empty">点击「预览数据」加载</td></tr>
                </tbody>
            </table>
        </div>
    </div>
</div>

<script src="/static/layui/layui.js"></script>
<script>
(function () {
    function pad(n){ return n < 10 ? '0'+n : ''+n; }
    function fmt(d){
        return d.getFullYear()+'-'+pad(d.getMonth()+1)+'-'+pad(d.getDate())+' '+pad(d.getHours())+':'+pad(d.getMinutes())+':'+pad(d.getSeconds());
    }
    function query(){
        return {
            deviceKey: $('#deviceKey').val(),
            metric: $('#metric').val(),
            from: $('#fromTime').val(),
            to: $('#toTime').val()
        };
    }
    function exportUrl(format){
        var q = query();
        q.format = format;
        return '/plant/api/export/telemetry?' + $.param(q);
    }

    var to = new Date();
    var from = new Date(to.getTime() - 24 * 3600 * 1000);
    $('#fromTime').val(fmt(from));
    $('#toTime').val(fmt(to));

    layui.use(['laydate','layer'], function(){
        var laydate = layui.laydate;
        laydate.render({elem:'#fromTime', type:'datetime', format:'yyyy-MM-dd HH:mm:ss'});
        laydate.render({elem:'#toTime', type:'datetime', format:'yyyy-MM-dd HH:mm:ss'});
    });

    $('#btnPreview').on('click', function(){
        var q = query();
        q.page = 0;
        q.size = 100;
        $.getJSON('/plant/api/readings', q, function(resp){
            var data = resp.data || {};
            var rows = data.rows || [];
            $('#previewCount').text('预览 ' + rows.length + ' 条（本页）');
            var html = '';
            rows.forEach(function(r){
                html += '<tr>'
                    + '<td>'+fmt(new Date(r.sampledAt))+'</td>'
                    + '<td>'+r.deviceKey+'</td>'
                    + '<td>'+r.metric+'</td>'
                    + '<td>'+(r.value==null?'':r.value)+'</td>'
                    + '<td>'+(r.unit||'')+'</td>'
                    + '<td>'+(r.quality||'')+'</td>'
                    + '<td>'+(r.receivedAt?fmt(new Date(r.receivedAt)):'')+'</td>'
                    + '</tr>';
            });
            $('#previewBody').html(html || '<tr><td colspan="7" class="fp-empty">没有匹配数据</td></tr>');
        });
    });

    $('#btnCsv').on('click', function(){ window.location.href = exportUrl('csv'); });
    $('#btnExcel').on('click', function(){ window.location.href = exportUrl('excel'); });

    // 进入页面自动预览一次
    $('#btnPreview').click();
})();
</script>
</body>
</html>
