<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
    <title>加入设备</title>
    <script src="/static/js/jquery-3.4.1.min.js"></script>
    <style>
        body {
            margin: 0;
            min-height: 100vh;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
            background: linear-gradient(160deg, #e8f7f2 0%, #f7fbf9 45%, #eef4ff 100%);
            color: #163b33;
        }
        .wrap {
            max-width: 440px;
            margin: 0 auto;
            padding: 28px 18px 40px;
            box-sizing: border-box;
        }
        .card {
            border: 1px solid rgba(255, 255, 255, 0.7);
            border-radius: 18px;
            background: rgba(255, 255, 255, 0.78);
            box-shadow: 0 18px 40px rgba(26, 64, 54, 0.12);
            padding: 22px 18px;
        }
        h1 {
            margin: 0 0 8px;
            font-size: 24px;
        }
        .sub {
            margin: 0 0 18px;
            color: #6b7d78;
            font-size: 14px;
            line-height: 1.5;
        }
        label {
            display: block;
            margin: 14px 0 6px;
            font-size: 13px;
            color: #4b635c;
            font-weight: 600;
        }
        input, select {
            width: 100%;
            height: 44px;
            border: 1px solid rgba(0, 150, 136, 0.22);
            border-radius: 12px;
            padding: 0 12px;
            box-sizing: border-box;
            font-size: 15px;
            background: #fff;
        }
        .btn {
            width: 100%;
            height: 46px;
            margin-top: 20px;
            border: 0;
            border-radius: 14px;
            background: linear-gradient(135deg, #009688, #16a374);
            color: #fff;
            font-size: 16px;
            font-weight: 700;
            cursor: pointer;
        }
        .btn:disabled {
            opacity: 0.55;
            cursor: not-allowed;
        }
        .msg {
            margin-top: 14px;
            padding: 12px;
            border-radius: 12px;
            font-size: 13px;
            line-height: 1.5;
            display: none;
        }
        .msg.ok {
            display: block;
            background: #ecfdf5;
            color: #047857;
        }
        .msg.err {
            display: block;
            background: #fef2f2;
            color: #b91c1c;
        }
        .status-line {
            margin-top: 10px;
            font-size: 13px;
            color: #56736c;
        }
        .key-box {
            margin-top: 10px;
            word-break: break-all;
            font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
            font-size: 12px;
            background: #f3faf7;
            border-radius: 10px;
            padding: 10px;
        }
    </style>
</head>
<body>
<div class="wrap">
    <div class="card">
        <h1>扫码加入设备</h1>
        <p class="sub">加入后本页会自动向后台发送心跳；位置默认使用实验室固定坐标。</p>

        <label for="deviceName">设备显示名称</label>
        <input id="deviceName" type="text" maxlength="64" placeholder="例如：张老师手机">

        <label for="deviceType">设备类型</label>
        <select id="deviceType">
            <option value="phone" selected>手机</option>
            <option value="device">其他设备</option>
        </select>

        <button class="btn" id="joinBtn" type="button">确认加入</button>
        <div class="msg" id="msg"></div>
        <div class="status-line" id="statusLine"></div>
        <div class="key-box" id="keyBox" style="display:none;"></div>
    </div>
</div>

<script>
    var inviteToken = "<#if token?? && token?has_content>${token?js_string}</#if>";
    var storageKey = 'aiot_joined_device';
    var heartbeatTimer = null;
    var locationTimer = null;
    var lastLocation = {
        lat: 30.475800,
        lng: 114.353600
    };
    var appliedStrategy = null;
    var heartbeatIntervalMs = 10000;

    function showMsg(text, ok) {
        var $msg = $('#msg');
        $msg.removeClass('ok err').addClass(ok ? 'ok' : 'err').text(text).show();
    }

    function formatLocationText(location) {
        if (!location) {
            return '位置未获取';
        }
        return location.lat.toFixed(6) + ', ' + location.lng.toFixed(6);
    }

    function refreshLocation() {
        if (!navigator.geolocation) {
            return;
        }
        navigator.geolocation.getCurrentPosition(function (pos) {
            lastLocation = {
                lat: pos.coords.latitude,
                lng: pos.coords.longitude
            };
        }, function () {
            // 用户拒绝或定位失败时继续心跳，只是不上报坐标
        }, {
            enableHighAccuracy: true,
            maximumAge: 30000,
            timeout: 12000
        });
    }

    function startHeartbeat(device) {
        if (heartbeatTimer) {
            clearTimeout(heartbeatTimer);
        }
        $('#statusLine').text('已加入，正在保持在线心跳...');
        $('#keyBox').show().text('deviceKey: ' + device.deviceKey);
        localStorage.setItem(storageKey, JSON.stringify(device));

        function scheduleNextBeat() {
            if (heartbeatTimer) {
                clearTimeout(heartbeatTimer);
            }
            heartbeatTimer = setTimeout(function () {
                beat();
            }, heartbeatIntervalMs);
        }

        function applyServerStrategy(strategy) {
            if (!strategy) {
                return;
            }
            appliedStrategy = strategy;
            var sec = Number(strategy.heartbeatIntervalSec || 10);
            if (!isNaN(sec) && sec >= 5) {
                heartbeatIntervalMs = sec * 1000;
            }
        }

        function beat() {
            var payload = {
                deviceKey: device.deviceKey,
                deviceName: device.name || device.deviceName,
                deviceType: device.deviceType || 'phone'
            };
            if (lastLocation) {
                payload.latituded = String(lastLocation.lat);
                payload.longituded = String(lastLocation.lng);
            }
            if (appliedStrategy) {
                payload.applied_strategy = appliedStrategy;
            }
            $.ajax({
                url: '/device/api/heartbeat',
                type: 'POST',
                contentType: 'application/json',
                data: JSON.stringify(payload)
            }).done(function (res) {
                if (res && res.strategy) {
                    applyServerStrategy(res.strategy);
                }
                var strategyText = appliedStrategy && appliedStrategy.summary
                    ? (' · 策略 ' + appliedStrategy.summary) : '';
                var locationText = ' · 位置 ' + formatLocationText(lastLocation);
                $('#statusLine').text('心跳正常 · ' + new Date().toLocaleTimeString() + locationText + strategyText);
                scheduleNextBeat();
            }).fail(function () {
                $('#statusLine').text('心跳失败，将自动重试...');
                scheduleNextBeat();
            });
        }

        beat();
    }

    function restoreJoined() {
        try {
            var raw = localStorage.getItem(storageKey);
            if (!raw) return;
            var device = JSON.parse(raw);
            if (device && device.deviceKey) {
                $('#deviceName').val(device.name || '');
                $('#joinBtn').prop('disabled', true).text('已加入');
                showMsg('检测到本机已加入，正在恢复心跳。', true);
                startHeartbeat(device);
            }
        } catch (e) {}
    }

    $('#joinBtn').on('click', function () {
        if (!inviteToken) {
            showMsg('缺少邀请码，请重新扫描管理端二维码。', false);
            return;
        }
        var name = $.trim($('#deviceName').val());
        var type = $('#deviceType').val() || 'phone';
        $('#joinBtn').prop('disabled', true).text('加入中...');
        $.ajax({
            url: '/device/api/join',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({
                token: inviteToken,
                deviceName: name,
                deviceType: type
            })
        }).done(function (res) {
            if (!res || !res.success) {
                showMsg((res && res.message) || '加入失败', false);
                $('#joinBtn').prop('disabled', false).text('确认加入');
                return;
            }
            showMsg('加入成功！请保持此页面打开以维持在线。', true);
            var device = res.device || {};
            device.deviceKey = res.deviceKey || device.deviceKey;
            device.name = device.name || name;
            device.deviceType = device.deviceType || type;
            $('#joinBtn').text('已加入');
            startHeartbeat(device);
        }).fail(function (xhr) {
            var message = (xhr.responseJSON && xhr.responseJSON.message) || '加入失败，请重新扫码';
            showMsg(message, false);
            $('#joinBtn').prop('disabled', false).text('确认加入');
        });
    });

    $(function () {
        if (!inviteToken) {
            showMsg('邀请码为空。请在设备管理页点击「扫码添加」后扫描二维码。', false);
            $('#joinBtn').prop('disabled', true);
            restoreJoined();
            return;
        }
        $.getJSON('/device/api/invite/' + encodeURIComponent(inviteToken))
            .done(function () {
                $('#statusLine').text('邀请码有效，填写名称后即可加入');
            })
            .fail(function () {
                showMsg('邀请码无效或已过期，请让管理员重新生成二维码。', false);
                $('#joinBtn').prop('disabled', true);
                restoreJoined();
            });
    });
</script>
</body>
</html>
