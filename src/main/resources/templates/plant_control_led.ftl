<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>未来植物 · LED补光</title>
    <script src="/static/js/jquery-3.4.1.min.js"></script>
    <link rel="stylesheet" href="/static/layui/css/layui.css">
    <link rel="stylesheet" href="/static/css/plant.css">
    <style>
        .led-board { display:grid; grid-template-columns: repeat(4, minmax(0,1fr)); gap:12px; }
        .led-card {
            border:1px solid #d7e5df; border-radius:12px; padding:14px; background:#f7fbf9;
            border-left: 4px solid var(--led-color, #94a3b8);
            transition: box-shadow .2s ease, transform .2s ease, background .2s ease;
        }
        .led-card.active {
            background: color-mix(in srgb, var(--led-color, #009688) 8%, #ffffff);
            box-shadow: 0 10px 22px color-mix(in srgb, var(--led-color, #009688) 22%, transparent);
            transform: translateY(-1px);
        }
        .led-card label {
            display:flex; justify-content:space-between; align-items:center;
            font-size:12px; color:#5f746e; margin-bottom:8px; font-weight:700; gap:8px;
        }
        .led-name {
            display:inline-flex; align-items:center; gap:8px; min-width:0;
        }
        .led-swatch {
            width:12px; height:12px; border-radius:50%;
            background: var(--led-color, #94a3b8);
            box-shadow: 0 0 0 2px #fff, 0 0 0 3px color-mix(in srgb, var(--led-color, #94a3b8) 35%, transparent);
            flex: 0 0 auto;
        }
        .led-card.active .led-swatch {
            box-shadow: 0 0 0 2px #fff, 0 0 10px color-mix(in srgb, var(--led-color, #94a3b8) 55%, transparent);
        }
        .led-meta { color:#334155; }
        .led-wave { color:#94a3b8; font-weight:600; font-size:11px; margin-left:4px; }
        .led-range { accent-color: var(--led-color, #009688); width:100%; }
        .bulk-bar {
            display:flex; flex-wrap:wrap; gap:10px; align-items:center;
            margin-bottom:14px; padding:12px 14px; border-radius:12px; background:#f3faf7; border:1px solid #d7e5df;
        }
        .form-grid { display:grid; grid-template-columns: 110px 1fr; gap:10px 12px; align-items:center; max-width:720px; }
        .form-grid .span2 { grid-column: 1 / -1; }
        .sched-type-fields { display:none; }
        .sched-type-fields.show { display:contents; }
        @media (max-width: 900px) { .led-board { grid-template-columns:1fr 1fr; } }
    </style>
</head>
<body>
<div class="fp-page">
    <div class="fp-hero">
        <div class="fp-hero-main">
            <div class="fp-kicker">Control · LED</div>
            <h1 class="fp-title">LED 补光控制</h1>
            <div class="fp-subtitle">调节 8 通道亮度，并支持每天定时开/关、间隔循环开灯。手动调节后需点「应用到设备」才会真正生效。</div>
        </div>
        <div class="fp-hero-aside">
            <div class="fp-status-row">
                <span class="fp-pill danger" id="onlinePill"><span class="fp-dot"></span>离线</span>
                <span class="fp-pill info" id="modePill">Mock</span>
            </div>
            <div class="fp-toolbar">
                <button class="fp-btn secondary" id="btnRefresh">刷新状态</button>
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
        <a class="fp-subtab active" href="/plant/control/led">LED 补光</a>
        <a class="fp-subtab" href="/plant/control/actuators">通风供水与自动化</a>
        <a class="fp-subtab" href="/plant/control/camera">云台与相机</a>
        <a class="fp-subtab" href="/plant/control/logs">指令日志</a>
    </div>

    <div id="controlRoot" class="fp-panel" style="margin-bottom:12px;">
        <div class="fp-panel-hd">
            <div>
                <h3>手动调节亮度</h3>
                <div class="desc">先改滑条或批量亮度，再点「应用到设备」发送控制指令</div>
            </div>
            <div class="fp-toolbar">
                <button class="fp-btn secondary" id="btnLedOff">全部关闭</button>
                <button class="fp-btn" id="btnLedApply">应用到设备</button>
            </div>
        </div>
        <div class="fp-panel-bd">
            <div class="bulk-bar">
                <span class="fp-strong">批量设置全部通道</span>
                <input type="number" class="fp-field" id="bulkBrightness" min="0" max="100" value="80" style="width:90px;">
                <span class="fp-muted">%</span>
                <button class="fp-btn ghost" id="btnFillBulk">填入滑条</button>
                <button class="fp-btn ghost" id="btnApplyBulk">填入并应用到设备</button>
            </div>
            <div class="led-board" id="ledBoard"></div>
        </div>
    </div>

    <div class="fp-panel">
        <div class="fp-panel-hd">
            <div>
                <h3>定时补光策略</h3>
                <div class="desc">支持「每天固定时段」与「开/关间隔循环」，每分钟自动检查并执行</div>
            </div>
            <button class="fp-btn" id="btnSaveSchedule">保存策略</button>
        </div>
        <div class="fp-panel-bd">
            <div class="form-grid" id="schedForm">
                <label>策略名称</label>
                <input class="fp-field" id="schedName" placeholder="例如：白天补光" value="白天补光">

                <label>策略类型</label>
                <select class="fp-field" id="schedType">
                    <option value="DAILY_WINDOW">每天固定时段开灯</option>
                    <option value="INTERVAL">间隔循环开/关灯</option>
                </select>

                <label>开灯亮度</label>
                <div>
                    <input type="number" class="fp-field" id="schedBrightness" min="0" max="100" value="80" style="width:100px;">
                    <span class="fp-muted">%</span>
                </div>

                <div class="sched-type-fields show" id="fieldsDaily">
                    <label>每天开灯</label>
                    <input class="fp-field" id="schedOnTime" placeholder="HH:mm" value="08:00" style="width:120px;">
                    <label>每天关灯</label>
                    <input class="fp-field" id="schedOffTime" placeholder="HH:mm" value="20:00" style="width:120px;">
                </div>

                <div class="sched-type-fields" id="fieldsInterval">
                    <label>开灯时长</label>
                    <div>
                        <input type="number" class="fp-field" id="schedOnMinutes" min="1" value="30" style="width:100px;">
                        <span class="fp-muted">分钟</span>
                    </div>
                    <label>关灯时长</label>
                    <div>
                        <input type="number" class="fp-field" id="schedOffMinutes" min="1" value="30" style="width:100px;">
                        <span class="fp-muted">分钟（按当天 0 点起算循环）</span>
                    </div>
                </div>

                <label>备注</label>
                <input class="fp-field" id="schedRemark" placeholder="可选">
            </div>

            <div style="margin-top:16px;" class="fp-table-wrap">
                <table class="fp-table">
                    <thead>
                    <tr>
                        <th>名称</th>
                        <th>类型</th>
                        <th>参数</th>
                        <th>亮度</th>
                        <th>状态</th>
                        <th>操作</th>
                    </tr>
                    </thead>
                    <tbody id="schedBody"></tbody>
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
    var layer;
    var ledDirty = false;
    // V1 暂定通道光谱映射（电气最终冻结后可改这里）
    var LED_CHANNELS = {
        1: { name: '深红', wave: '660nm', color: '#e11d48' },
        2: { name: '蓝光', wave: '450nm', color: '#2563eb' },
        3: { name: '白光', wave: '全光谱', color: '#94a3b8' },
        4: { name: '远红', wave: '730nm', color: '#9f1239' },
        5: { name: '红光', wave: '630nm', color: '#dc2626' },
        6: { name: '蓝光', wave: '460nm', color: '#3b82f6' },
        7: { name: '暖白', wave: '3000K', color: '#f59e0b' },
        8: { name: '紫外', wave: 'UVA', color: '#7c3aed' }
    };

    function syncLamp($card, v) {
        var n = Number(v) || 0;
        $card.find('.led-val').text(n + '%');
        $card.toggleClass('active', n > 0);
    }

    function buildLed(actuators) {
        var map = PlantControl.actuatorMap(actuators);
        var html = '';
        for (var i = 1; i <= 8; i++) {
            var id = 'led.ch' + i;
            var meta = LED_CHANNELS[i];
            var brightness = map[id] && map[id].brightness != null ? Number(map[id].brightness) : 0;
            html += '<div class="led-card ' + (brightness > 0 ? 'active' : '') + '" style="--led-color:' + meta.color + ';">'
                + '<label>'
                + '<span class="led-name"><i class="led-swatch" aria-hidden="true"></i>'
                + '<span class="led-meta">CH' + i + ' · ' + meta.name + '<span class="led-wave">' + meta.wave + '</span></span></span>'
                + '<span class="led-val">' + brightness + '%</span></label>'
                + '<input type="range" min="0" max="100" value="' + brightness + '" class="led-range" data-ch="' + i + '">'
                + '</div>';
        }
        $('#ledBoard').html(html);
        ledDirty = false;
    }

    function collectChannels() {
        var channels = [];
        $('.led-range').each(function () {
            channels.push({
                actuatorId: 'led.ch' + $(this).data('ch'),
                brightness: parseInt($(this).val(), 10)
            });
        });
        return channels;
    }

    function applyChannels(channels) {
        return PlantControl.sendCommand('LED_SET', {channels: channels}).done(function () {
            ledDirty = false;
            refreshLedFromServer();
        });
    }

    function fillAll(v) {
        $('.led-range').each(function () {
            $(this).val(v);
            syncLamp($(this).closest('.led-card'), v);
        });
        ledDirty = true;
    }

    function typeText(t) {
        return t === 'INTERVAL' ? '间隔循环' : '每天时段';
    }

    function paramText(s) {
        if (s.scheduleType === 'INTERVAL') {
            return '开 ' + (s.onMinutes || 0) + ' 分钟 / 关 ' + (s.offMinutes || 0) + ' 分钟';
        }
        return (s.onTime || '--') + ' ~ ' + (s.offTime || '--');
    }

    function loadSchedules() {
        $.getJSON('/plant/api/led/schedules', {deviceKey: DEVICE_KEY}, function (resp) {
            var list = resp.data || [];
            var html = '';
            list.forEach(function (s) {
                html += '<tr>'
                    + '<td>' + (s.name || '-') + '</td>'
                    + '<td>' + typeText(s.scheduleType) + '</td>'
                    + '<td>' + paramText(s) + '</td>'
                    + '<td>' + (s.brightness == null ? 0 : s.brightness) + '%</td>'
                    + '<td>' + (s.enabled ? '<span class="fp-pill ok">启用</span>' : '<span class="fp-pill neutral">停用</span>') + '</td>'
                    + '<td>'
                    + '<button class="fp-btn ghost btn-toggle" data-id="' + s.id + '" data-on="' + (s.enabled ? 1 : 0) + '" style="height:28px;">' + (s.enabled ? '停用' : '启用') + '</button> '
                    + '<button class="fp-btn secondary btn-del" data-id="' + s.id + '" style="height:28px;">删除</button>'
                    + '</td></tr>';
            });
            $('#schedBody').html(html || '<tr><td colspan="6" class="fp-empty">暂无定时策略，可在上方创建后保存</td></tr>');
        });
    }

    function toggleTypeFields() {
        var t = $('#schedType').val();
        $('#fieldsDaily').toggleClass('show', t === 'DAILY_WINDOW');
        $('#fieldsInterval').toggleClass('show', t === 'INTERVAL');
    }

    function refreshStatusOnly() {
        PlantControl.fetchDashboard(function () {});
    }

    function refreshLedFromServer() {
        PlantControl.fetchDashboard(function (d) { buildLed(d.actuators); });
    }

    PlantControl.ready(function () {
        layui.use(['layer', 'laydate'], function () {
            layer = layui.layer;
            var laydate = layui.laydate;
            laydate.render({elem: '#schedOnTime', type: 'time', format: 'HH:mm'});
            laydate.render({elem: '#schedOffTime', type: 'time', format: 'HH:mm'});
        });

        $(document).on('input', '.led-range', function () {
            ledDirty = true;
            syncLamp($(this).closest('.led-card'), $(this).val());
        });

        $('#btnFillBulk').on('click', function () {
            var v = parseInt($('#bulkBrightness').val(), 10);
            if (isNaN(v) || v < 0 || v > 100) {
                PlantControl.toast('请输入 0-100 的亮度', false);
                return;
            }
            fillAll(v);
        });

        $('#btnApplyBulk').on('click', function () {
            var v = parseInt($('#bulkBrightness').val(), 10);
            if (isNaN(v) || v < 0 || v > 100) {
                PlantControl.toast('请输入 0-100 的亮度', false);
                return;
            }
            fillAll(v);
            applyChannels(collectChannels());
        });

        $('#btnLedApply').on('click', function () {
            applyChannels(collectChannels());
        });

        $('#btnLedOff').on('click', function () {
            fillAll(0);
            applyChannels(collectChannels());
        });

        $('#schedType').on('change', toggleTypeFields);
        toggleTypeFields();

        $('#btnSaveSchedule').on('click', function () {
            var body = {
                deviceKey: DEVICE_KEY,
                name: $('#schedName').val(),
                scheduleType: $('#schedType').val(),
                brightness: parseInt($('#schedBrightness').val(), 10),
                onTime: $('#schedOnTime').val(),
                offTime: $('#schedOffTime').val(),
                onMinutes: parseInt($('#schedOnMinutes').val(), 10),
                offMinutes: parseInt($('#schedOffMinutes').val(), 10),
                remark: $('#schedRemark').val(),
                enabled: true
            };
            $.ajax({
                url: '/plant/api/led/schedules',
                method: 'POST',
                contentType: 'application/json',
                data: JSON.stringify(body)
            }).done(function (resp) {
                if (resp.code === 0) {
                    PlantControl.toast('策略已保存', true);
                    loadSchedules();
                } else {
                    PlantControl.toast(resp.message || '保存失败', false);
                }
            }).fail(function () {
                PlantControl.toast('保存失败', false);
            });
        });

        $(document).on('click', '.btn-toggle', function () {
            var id = $(this).data('id');
            var on = $(this).data('on') == 1;
            $.ajax({
                url: '/plant/api/led/schedules/' + id + '/enabled',
                method: 'POST',
                contentType: 'application/json',
                data: JSON.stringify({enabled: !on})
            }).done(loadSchedules);
        });

        $(document).on('click', '.btn-del', function () {
            var id = $(this).data('id');
            if (!confirm('确认删除该策略？')) return;
            $.ajax({ url: '/plant/api/led/schedules/' + id, method: 'DELETE' }).done(loadSchedules);
        });

        $('#btnRefresh').on('click', function () {
            if (ledDirty && !confirm('你有未应用到设备的亮度修改，刷新将恢复为设备当前值，是否继续？')) {
                return;
            }
            refreshLedFromServer();
            loadSchedules();
        });
        refreshLedFromServer();
        loadSchedules();
        // 定时只刷新在线状态，绝不重绘滑条，避免把本地调节冲回旧值
        setInterval(refreshStatusOnly, 5000);
    });
})();
</script>
</body>
</html>
