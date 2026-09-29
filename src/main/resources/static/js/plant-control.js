/* 未来植物控制页公共逻辑 */
window.PlantControl = (function () {
    var DEVICE_KEY = 'plant-ctrl-01';
    var canControl = true;
    var layer = null;

    function ready(cb) {
        if (window.layui) {
            layui.use(['layer'], function () {
                layer = layui.layer;
                cb && cb();
            });
        } else {
            cb && cb();
        }
    }

    function uuid() {
        return 'ui-' + Date.now() + '-' + Math.floor(Math.random() * 100000);
    }

    function toast(msg, ok) {
        if (layer) layer.msg(msg, {icon: ok ? 1 : 2, time: 1600});
        else alert(msg);
    }

    function fmt(v) {
        if (!v) return '--';
        var d = new Date(v);
        if (isNaN(d.getTime())) return String(v);
        function p(n) { return n < 10 ? '0' + n : n; }
        return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
    }

    function setControlEnabled(enabled) {
        canControl = enabled;
        $('#controlRoot').toggleClass('ctrl-disabled', !enabled);
    }

    function updateStatusPills(d) {
        var online = !!(d && d.online);
        var mock = !!(d && d.mockEnabled);
        $('#onlinePill').attr('class', 'fp-pill ' + (online ? 'ok' : 'danger'))
            .html('<span class="fp-dot"></span>' + (online ? '在线' : '离线'));
        $('#modePill').text(mock ? 'Mock 可本地联调' : '实机模式');
        setControlEnabled(mock || online);
    }

    function fetchDashboard(cb) {
        $.getJSON('/plant/api/dashboard', {deviceKey: DEVICE_KEY}, function (resp) {
            if (resp.code !== 0) return;
            var d = resp.data || {};
            updateStatusPills(d);
            cb && cb(d);
        });
    }

    function sendCommand(commandType, payload) {
        if (!canControl) {
            toast('控制器离线，无法下发', false);
            return $.Deferred().reject();
        }
        return $.ajax({
            url: '/plant/api/commands',
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({
                deviceKey: DEVICE_KEY,
                clientRequestId: uuid(),
                commandType: commandType,
                payload: payload
            })
        }).done(function (resp) {
            if (resp.code === 0) toast('已提交 · ' + resp.data.status, true);
            else toast(resp.message || '失败', false);
        }).fail(function () {
            toast('请求失败', false);
        });
    }

    function renderLog(list, selector) {
        var html = '';
        (list || []).forEach(function (c) {
            var ok = c.status === 'ACKED' || c.status === 'SENT';
            html += '<div class="fp-list-row"><div><div class="fp-strong">' + c.commandType + '</div>'
                + '<div class="fp-muted">' + (c.commandId || '') + ' · ' + (c.operator || 'system') + '</div></div>'
                + '<div style="text-align:right;"><span class="fp-pill ' + (ok ? 'ok' : 'warn') + '" style="height:24px;">' + c.status + '</span>'
                + '<div class="fp-muted" style="margin-top:4px;">' + fmt(c.createdAt) + '</div></div></div>';
        });
        $(selector || '#cmdLog').html(html || '<div class="fp-empty">还没有控制指令。</div>');
    }

    function actuatorMap(actuators) {
        var map = {};
        (actuators || []).forEach(function (a) {
            map[a.actuatorId] = a.state || {};
        });
        return map;
    }

    return {
        DEVICE_KEY: DEVICE_KEY,
        ready: ready,
        toast: toast,
        fmt: fmt,
        fetchDashboard: fetchDashboard,
        sendCommand: sendCommand,
        renderLog: renderLog,
        actuatorMap: actuatorMap,
        setControlEnabled: setControlEnabled
    };
})();
