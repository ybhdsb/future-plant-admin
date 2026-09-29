<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>未来植物 · 通风供水与自动化</title>
    <script src="/static/js/jquery-3.4.1.min.js"></script>
    <link rel="stylesheet" href="/static/layui/css/layui.css">
    <link rel="stylesheet" href="/static/css/plant.css">
    <style>
        .group-title { margin: 0 0 10px; font-size: 14px; font-weight: 800; color: #0f766e; }
        .toggle-grid { display:grid; grid-template-columns:1fr 1fr; gap:12px; margin-bottom:16px; }
        .toggle-card {
            display:flex; justify-content:space-between; align-items:center; gap:10px;
            padding:14px; border-radius:12px; border:1px solid #d7e5df; background:#fff;
        }
        .toggle-card.v2 { opacity:.7; border-style:dashed; }
        .toggle-card .name { font-weight:800; font-size:15px; }
        .toggle-card .tip { margin-top:4px; font-size:12px; color:#94a3b8; }
        .switch {
            position:relative; width:52px; height:30px; border-radius:999px; background:#cbd5e1; cursor:pointer; flex-shrink:0;
        }
        .switch.on { background:#009688; }
        .switch i {
            position:absolute; top:3px; left:3px; width:24px; height:24px; border-radius:50%; background:#fff;
            transition: left .18s ease; box-shadow: 0 2px 6px rgba(0,0,0,.15);
        }
        .switch.on i { left:25px; }
        .form-grid { display:grid; grid-template-columns: 120px 1fr; gap:10px 12px; align-items:center; }
        .rule-fields { display:none; }
        .rule-fields.show { display:contents; }
        .example {
            margin: 10px 0 14px; padding:10px 12px; border-radius:10px; background:#ecfdf5; border:1px solid #a7f3d0;
            color:#065f46; font-size:12px; line-height:1.6;
        }
        @media (max-width: 900px) { .toggle-grid { grid-template-columns:1fr; } }
    </style>
</head>
<body>
<div class="fp-page">
    <div class="fp-hero">
        <div class="fp-hero-main">
            <div class="fp-kicker">Control · Actuators & Automation</div>
            <h1 class="fp-title">通风 / 供水 / 曝气与自动化</h1>
            <div class="fp-subtitle">对照建设方案补齐循环风机、水泵、氧泵与继电器；并为每个执行器配置「每天定时 / 隔天定时 / 间隔循环 / 传感器阈值触发」。</div>
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
        <a class="fp-subtab active" href="/plant/control/actuators">通风供水与自动化</a>
        <a class="fp-subtab" href="/plant/control/camera">云台与相机</a>
        <a class="fp-subtab" href="/plant/control/logs">指令日志</a>
    </div>

    <div id="controlRoot" class="fp-panel" style="margin-bottom:12px;">
        <div class="fp-panel-hd">
            <div>
                <h3>执行器手动控制</h3>
                <div class="desc">建设方案 V1：循环风机×2、水泵；任务表补充氧泵；遮光电机为 V2 预留</div>
            </div>
        </div>
        <div class="fp-panel-bd" id="actuatorPanels"></div>
    </div>

    <div class="fp-panel">
        <div class="fp-panel-hd">
            <div>
                <h3>自动化规则</h3>
                <div class="desc">以氧泵为例：每天固定时段开、每隔几天开一次、溶氧偏低立即开</div>
            </div>
            <button class="fp-btn" id="btnSaveRule">保存规则</button>
        </div>
        <div class="fp-panel-bd">
            <div class="example">
                <strong>示例建议：</strong><br>
                1）氧泵 · 每隔 1 天 · 09:00 开启 15 分钟<br>
                2）氧泵 · 传感器触发 · <code>nutrient.do &lt; 5.0</code> 立即开启（冷却 10 分钟防抖）<br>
                3）循环风机 · 每天 08:00–20:00 开启
            </div>
            <div class="form-grid" id="ruleForm">
                <label>规则名称</label>
                <input class="fp-field" id="ruleName" value="氧泵自动策略">

                <label>目标执行器</label>
                <select class="fp-field" id="ruleActuator"></select>

                <label>规则类型</label>
                <select class="fp-field" id="ruleType">
                    <option value="DAILY_WINDOW">每天固定时段开启</option>
                    <option value="EVERY_N_DAYS">每隔 N 天固定时刻开启</option>
                    <option value="INTERVAL">开/关间隔循环</option>
                    <option value="SENSOR_THRESHOLD">传感器阈值触发</option>
                </select>

                <label>动作</label>
                <select class="fp-field" id="ruleAction">
                    <option value="ON">开启</option>
                    <option value="OFF">关闭</option>
                </select>

                <div class="rule-fields show" data-for="DAILY_WINDOW">
                    <label>每天开</label>
                    <input class="fp-field" id="onTime" value="08:00" style="width:120px;">
                    <label>每天关</label>
                    <input class="fp-field" id="offTime" value="20:00" style="width:120px;">
                </div>

                <div class="rule-fields" data-for="EVERY_N_DAYS">
                    <label>每隔天数</label>
                    <div><input type="number" class="fp-field" id="everyNDays" min="1" value="1" style="width:100px;"> <span class="fp-muted">天（1=每天）</span></div>
                    <label>开启时刻</label>
                    <input class="fp-field" id="everyOnTime" value="09:00" style="width:120px;">
                    <label>持续分钟</label>
                    <div><input type="number" class="fp-field" id="durationMinutes" min="1" value="15" style="width:100px;"> <span class="fp-muted">分钟</span></div>
                </div>

                <div class="rule-fields" data-for="INTERVAL">
                    <label>开灯/开泵时长</label>
                    <div><input type="number" class="fp-field" id="onMinutes" min="1" value="30" style="width:100px;"> 分钟</div>
                    <label>关闭时长</label>
                    <div><input type="number" class="fp-field" id="offMinutes" min="1" value="30" style="width:100px;"> 分钟</div>
                </div>

                <div class="rule-fields" data-for="SENSOR_THRESHOLD">
                    <label>测点 metric</label>
                    <select class="fp-field" id="metric">
                        <option value="nutrient.do">nutrient.do（溶氧）</option>
                        <option value="air.temperature">air.temperature（气温）</option>
                        <option value="air.humidity">air.humidity（湿度）</option>
                        <option value="air.co2">air.co2（CO₂）</option>
                        <option value="nutrient.ph">nutrient.ph（pH）</option>
                        <option value="nutrient.ec">nutrient.ec（EC）</option>
                        <option value="nutrient.level">nutrient.level（液位）</option>
                    </select>
                    <label>条件</label>
                    <div class="fp-toolbar">
                        <select class="fp-field" id="operator" style="width:100px;">
                            <option value="LT">&lt;</option>
                            <option value="LTE">&lt;=</option>
                            <option value="GT">&gt;</option>
                            <option value="GTE">&gt;=</option>
                            <option value="EQ">=</option>
                        </select>
                        <input type="number" step="0.01" class="fp-field" id="thresholdValue" value="5" style="width:120px;">
                    </div>
                    <label>冷却分钟</label>
                    <div><input type="number" class="fp-field" id="cooldownMinutes" min="0" value="10" style="width:100px;"> <span class="fp-muted">防止阈值抖动反复开关</span></div>
                </div>

                <label>备注</label>
                <input class="fp-field" id="remark" placeholder="可选">
            </div>

            <div style="margin-top:16px;" class="fp-table-wrap">
                <table class="fp-table">
                    <thead>
                    <tr>
                        <th>名称</th>
                        <th>执行器</th>
                        <th>类型</th>
                        <th>参数</th>
                        <th>状态</th>
                        <th>最近执行</th>
                        <th>操作</th>
                    </tr>
                    </thead>
                    <tbody id="ruleBody"></tbody>
                </table>
            </div>
        </div>
    </div>
</div>

<script src="/static/layui/layui.js"></script>
<script src="/static/js/plant-control.js"></script>
<script>
(function () {
    var DEVICE_KEY = 'plant-ctrl-01';
    var catalog = [];
    var stateMap = {};

    function fmt(v) {
        if (!v) return '--';
        return PlantControl.fmt(v);
    }

    function renderActuators() {
        var groups = {};
        catalog.forEach(function (a) {
            groups[a.group] = groups[a.group] || [];
            groups[a.group].push(a);
        });
        var html = '';
        Object.keys(groups).forEach(function (g) {
            html += '<div class="group-title">' + g + '</div><div class="toggle-grid">';
            groups[g].forEach(function (a) {
                var st = stateMap[a.actuatorId] || {};
                var on = !!st.on;
                html += '<div class="toggle-card ' + (a.v1 ? '' : 'v2') + '">'
                    + '<div><div class="name">' + a.name + (a.v1 ? '' : ' · V2预留') + '</div>'
                    + '<div class="tip">' + a.tip + ' · ' + a.actuatorId + '</div></div>'
                    + '<div class="switch ' + (on ? 'on' : '') + '" data-id="' + a.actuatorId + '" data-type="' + a.commandType + '" data-on="' + (on ? 1 : 0) + '"><i></i></div>'
                    + '</div>';
            });
            html += '</div>';
        });
        $('#actuatorPanels').html(html);

        var opts = '';
        catalog.filter(function (a) { return a.v1; }).forEach(function (a) {
            opts += '<option value="' + a.actuatorId + '">' + a.name + ' (' + a.actuatorId + ')</option>';
        });
        var $sel = $('#ruleActuator');
        var cur = $sel.val();
        $sel.html(opts);
        if (cur) $sel.val(cur);
        if (!$sel.val()) $sel.val('pump.oxygen');
    }

    function toggleRuleFields() {
        var t = $('#ruleType').val();
        $('.rule-fields').removeClass('show');
        $('.rule-fields[data-for="' + t + '"]').addClass('show');
    }

    function typeText(t) {
        return ({
            DAILY_WINDOW: '每天时段',
            EVERY_N_DAYS: '隔天定时',
            INTERVAL: '间隔循环',
            SENSOR_THRESHOLD: '传感触发'
        })[t] || t;
    }

    function paramText(r) {
        if (r.ruleType === 'DAILY_WINDOW') return (r.onTime || '') + ' ~ ' + (r.offTime || '');
        if (r.ruleType === 'EVERY_N_DAYS') return '每' + (r.everyNDays || 1) + '天 ' + (r.onTime || '') + ' 开' + (r.durationMinutes || 0) + '分钟';
        if (r.ruleType === 'INTERVAL') return '开' + (r.onMinutes || 0) + '分/关' + (r.offMinutes || 0) + '分';
        if (r.ruleType === 'SENSOR_THRESHOLD') return (r.metric || '') + ' ' + (r.operatorName || r.operator || '') + ' ' + (r.thresholdValue == null ? '' : r.thresholdValue);
        return '-';
    }

    function loadRules() {
        $.getJSON('/plant/api/automation/rules', {deviceKey: DEVICE_KEY}, function (resp) {
            var list = resp.data || [];
            var html = '';
            list.forEach(function (r) {
                html += '<tr>'
                    + '<td>' + (r.name || '-') + '</td>'
                    + '<td>' + r.actuatorId + '</td>'
                    + '<td>' + typeText(r.ruleType) + '</td>'
                    + '<td>' + paramText(r) + '</td>'
                    + '<td>' + (r.enabled ? '<span class="fp-pill ok">启用</span>' : '<span class="fp-pill neutral">停用</span>') + '</td>'
                    + '<td>' + fmt(r.lastAppliedAt || r.lastTriggeredAt) + '</td>'
                    + '<td>'
                    + '<button class="fp-btn ghost btn-toggle" data-id="' + r.id + '" data-on="' + (r.enabled ? 1 : 0) + '" style="height:28px;">' + (r.enabled ? '停用' : '启用') + '</button> '
                    + '<button class="fp-btn secondary btn-del" data-id="' + r.id + '" style="height:28px;">删除</button>'
                    + '</td></tr>';
            });
            $('#ruleBody').html(html || '<tr><td colspan="7" class="fp-empty">暂无规则。可按上方示例为氧泵/风机创建。</td></tr>');
        });
    }

    function refresh() {
        PlantControl.fetchDashboard(function (d) {
            stateMap = {};
            (d.actuators || []).forEach(function (a) { stateMap[a.actuatorId] = a.state || {}; });
            renderActuators();
        });
    }

    function loadCatalog(cb) {
        $.getJSON('/plant/api/actuators/catalog', {includeV2: true}, function (resp) {
            catalog = resp.data || [];
            cb && cb();
        });
    }

    PlantControl.ready(function () {
        layui.use(['laydate'], function () {
            var laydate = layui.laydate;
            laydate.render({elem: '#onTime', type: 'time', format: 'HH:mm'});
            laydate.render({elem: '#offTime', type: 'time', format: 'HH:mm'});
            laydate.render({elem: '#everyOnTime', type: 'time', format: 'HH:mm'});
        });

        $('#ruleType').on('change', toggleRuleFields);
        toggleRuleFields();

        $(document).on('click', '.switch', function () {
            var id = $(this).data('id');
            var type = $(this).data('type');
            var on = $(this).data('on') == 1;
            PlantControl.sendCommand(type, {actuatorId: id, on: !on}).done(refresh);
        });

        $('#btnSaveRule').on('click', function () {
            var t = $('#ruleType').val();
            var body = {
                deviceKey: DEVICE_KEY,
                name: $('#ruleName').val(),
                actuatorId: $('#ruleActuator').val(),
                ruleType: t,
                action: $('#ruleAction').val(),
                onTime: t === 'EVERY_N_DAYS' ? $('#everyOnTime').val() : $('#onTime').val(),
                offTime: $('#offTime').val(),
                everyNDays: parseInt($('#everyNDays').val(), 10),
                durationMinutes: parseInt($('#durationMinutes').val(), 10),
                onMinutes: parseInt($('#onMinutes').val(), 10),
                offMinutes: parseInt($('#offMinutes').val(), 10),
                metric: $('#metric').val(),
                operator: $('#operator').val(),
                thresholdValue: parseFloat($('#thresholdValue').val()),
                cooldownMinutes: parseInt($('#cooldownMinutes').val(), 10),
                remark: $('#remark').val(),
                enabled: true,
                priority: 100
            };
            $.ajax({
                url: '/plant/api/automation/rules',
                method: 'POST',
                contentType: 'application/json',
                data: JSON.stringify(body)
            }).done(function (resp) {
                if (resp.code === 0) {
                    PlantControl.toast('规则已保存', true);
                    loadRules();
                } else {
                    PlantControl.toast(resp.message || '保存失败', false);
                }
            }).fail(function () { PlantControl.toast('保存失败', false); });
        });

        $(document).on('click', '.btn-toggle', function () {
            var id = $(this).data('id');
            var on = $(this).data('on') == 1;
            $.ajax({
                url: '/plant/api/automation/rules/' + id + '/enabled',
                method: 'POST',
                contentType: 'application/json',
                data: JSON.stringify({enabled: !on})
            }).done(loadRules);
        });
        $(document).on('click', '.btn-del', function () {
            if (!confirm('确认删除该规则？')) return;
            $.ajax({ url: '/plant/api/automation/rules/' + $(this).data('id'), method: 'DELETE' }).done(loadRules);
        });

        $('#btnRefresh').on('click', function () { refresh(); loadRules(); });
        loadCatalog(function () { refresh(); loadRules(); });
        setInterval(refresh, 5000);
    });
})();
</script>
</body>
</html>
