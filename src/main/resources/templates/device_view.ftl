<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>设备管理</title>
    <script src="/static/js/jquery-3.4.1.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js"></script>
    <script src="https://api.map.baidu.com/api?v=2.0&ak=yocuuOtkggWMDh2HeVzbqHAWDGipiFhK"></script>
    <link rel="stylesheet" href="/static/layui/css/layui.css">
    <link rel="stylesheet" href="/static/layui/css/glass-ui.css">
    <style>
        html,
        body {
            min-height: 100%;
            margin: 0;
        }

        body {
            padding: 18px;
            box-sizing: border-box;
            color: #1f342f;
        }

        .device-page {
            max-width: 1320px;
            margin: 0 auto;
        }

        .page-head {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 16px;
            margin-bottom: 18px;
            padding: 18px 20px;
            border: 1px solid rgba(255, 255, 255, 0.58);
            border-radius: 18px;
            background: rgba(255, 255, 255, 0.58);
            box-shadow: 0 18px 45px rgba(26, 64, 54, 0.10), inset 0 1px 0 rgba(255, 255, 255, 0.68);
            backdrop-filter: blur(18px) saturate(145%);
            -webkit-backdrop-filter: blur(18px) saturate(145%);
        }

        .page-title {
            margin: 0;
            font-size: 24px;
            font-weight: 700;
            color: #153b33;
        }

        .page-subtitle {
            margin-top: 6px;
            color: #6b7d78;
            font-size: 13px;
        }

        .view-filter {
            display: flex;
            gap: 10px;
            flex-wrap: wrap;
            align-items: center;
        }

        .action-btn {
            height: 36px;
            padding: 0 14px;
            border: 0;
            border-radius: 18px;
            background: linear-gradient(135deg, rgba(0, 150, 136, 0.92), rgba(22, 163, 116, 0.88));
            color: #fff;
            cursor: pointer;
            font-size: 13px;
            font-weight: 700;
            box-shadow: 0 10px 24px rgba(0, 150, 136, 0.18);
        }

        .action-btn.secondary {
            background: rgba(255, 255, 255, 0.72);
            color: #0f766e;
            border: 1px solid rgba(0, 150, 136, 0.24);
            box-shadow: none;
        }

        .card-actions {
            display: flex;
            gap: 8px;
            margin-top: 14px;
        }

        .card-actions .danger-btn {
            height: 32px;
            padding: 0 12px;
            border: 1px solid rgba(239, 68, 68, 0.28);
            border-radius: 10px;
            background: rgba(254, 242, 242, 0.9);
            color: #b91c1c;
            cursor: pointer;
            font-size: 12px;
            font-weight: 700;
        }

        .form-modal-mask,
        .qr-modal-mask {
            position: fixed;
            inset: 0;
            z-index: 10000;
            display: none;
            align-items: center;
            justify-content: center;
            padding: 24px;
            background: rgba(9, 29, 25, 0.48);
            box-sizing: border-box;
        }

        .form-modal,
        .qr-modal {
            width: min(480px, 100%);
            border-radius: 16px;
            background: #fff;
            box-shadow: 0 30px 90px rgba(0, 0, 0, 0.28);
            overflow: hidden;
        }

        .form-modal-head,
        .qr-modal-head {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 16px 18px;
            border-bottom: 1px solid #e7efec;
        }

        .form-modal-body,
        .qr-modal-body {
            padding: 18px;
        }

        .form-row {
            margin-bottom: 14px;
        }

        .form-row label {
            display: block;
            margin-bottom: 6px;
            color: #4b635c;
            font-size: 13px;
            font-weight: 600;
        }

        .form-row input,
        .form-row select {
            width: 100%;
            height: 40px;
            border: 1px solid rgba(0, 150, 136, 0.22);
            border-radius: 10px;
            padding: 0 12px;
            box-sizing: border-box;
        }

        .form-actions {
            display: flex;
            justify-content: flex-end;
            gap: 10px;
            margin-top: 8px;
        }

        .qr-box {
            display: flex;
            justify-content: center;
            padding: 8px 0 14px;
        }

        .qr-url {
            word-break: break-all;
            font-size: 12px;
            color: #56736c;
            background: #f3faf7;
            border-radius: 10px;
            padding: 10px;
            line-height: 1.5;
        }

        .filter-chip {
            height: 36px;
            padding: 0 16px;
            border: 1px solid rgba(0, 150, 136, 0.18);
            border-radius: 18px;
            background: rgba(255, 255, 255, 0.45);
            color: #31544c;
            cursor: pointer;
            transition: all 0.18s ease;
        }

        .filter-chip:hover {
            background: rgba(232, 248, 240, 0.78);
            color: #008f6a;
        }

        .filter-chip.active {
            border-color: rgba(255, 255, 255, 0.62);
            background: linear-gradient(135deg, rgba(0, 150, 136, 0.92), rgba(22, 163, 116, 0.88));
            color: #fff;
            box-shadow: 0 10px 24px rgba(0, 150, 136, 0.22);
        }

        .summary-grid {
            display: grid;
            grid-template-columns: repeat(4, minmax(0, 1fr));
            gap: 14px;
            margin-bottom: 14px;
        }

        .type-filter-panel {
            display: flex;
            flex-wrap: wrap;
            gap: 10px;
            align-items: center;
            margin-bottom: 18px;
            padding: 14px 16px;
            border: 1px solid rgba(255, 255, 255, 0.58);
            border-radius: 18px;
            background: rgba(255, 255, 255, 0.58);
            box-shadow: 0 12px 30px rgba(26, 64, 54, 0.08);
            backdrop-filter: blur(18px) saturate(145%);
            -webkit-backdrop-filter: blur(18px) saturate(145%);
        }

        .type-filter-label {
            color: #4b635c;
            font-size: 13px;
            font-weight: 700;
            margin-right: 4px;
        }

        .type-chip {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            height: 36px;
            padding: 0 14px;
            border: 1px solid rgba(0, 150, 136, 0.18);
            border-radius: 18px;
            background: rgba(255, 255, 255, 0.45);
            color: #31544c;
            cursor: pointer;
            transition: all 0.18s ease;
            font-size: 13px;
        }

        .type-chip img {
            width: 18px;
            height: 18px;
            object-fit: contain;
        }

        .type-chip .type-count {
            min-width: 18px;
            padding: 1px 6px;
            border-radius: 999px;
            background: rgba(0, 150, 136, 0.12);
            color: #0f766e;
            font-size: 12px;
            font-weight: 700;
        }

        .type-chip:hover {
            background: rgba(232, 248, 240, 0.78);
            color: #008f6a;
        }

        .type-chip.active {
            border-color: rgba(255, 255, 255, 0.62);
            background: linear-gradient(135deg, rgba(0, 150, 136, 0.92), rgba(22, 163, 116, 0.88));
            color: #fff;
            box-shadow: 0 10px 24px rgba(0, 150, 136, 0.22);
        }

        .type-chip.active .type-count {
            background: rgba(255, 255, 255, 0.22);
            color: #fff;
        }

        .summary-card {
            position: relative;
            min-height: 132px;
            padding: 18px;
            overflow: hidden;
            border: 1px solid rgba(255, 255, 255, 0.58);
            border-radius: 18px;
            background: rgba(255, 255, 255, 0.58);
            box-shadow: 0 18px 45px rgba(26, 64, 54, 0.10), inset 0 1px 0 rgba(255, 255, 255, 0.68);
            box-sizing: border-box;
            cursor: pointer;
            backdrop-filter: blur(18px) saturate(145%);
            -webkit-backdrop-filter: blur(18px) saturate(145%);
            transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
        }

        .summary-card:hover,
        .summary-card.active {
            transform: translateY(-3px);
            border-color: rgba(0, 150, 136, 0.34);
            box-shadow: 0 22px 52px rgba(0, 105, 86, 0.16), inset 0 1px 0 rgba(255, 255, 255, 0.72);
        }

        .summary-card::after {
            content: "";
            position: absolute;
            right: -28px;
            bottom: -36px;
            width: 116px;
            height: 116px;
            border-radius: 50%;
            background: rgba(0, 150, 136, 0.10);
        }

        .summary-top {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 14px;
        }

        .summary-icon {
            width: 46px;
            height: 46px;
            padding: 10px;
            border-radius: 14px;
            background: rgba(255, 255, 255, 0.62);
            box-sizing: border-box;
        }

        .summary-count {
            font-size: 34px;
            font-weight: 800;
            line-height: 1;
        }

        .summary-name {
            margin-bottom: 6px;
            font-size: 16px;
            font-weight: 700;
            color: #243b35;
        }

        .summary-desc {
            color: #6b7d78;
            font-size: 13px;
        }

        .online-color {
            color: #009688;
        }

        .offline-color {
            color: #64748b;
        }

        .warning-color {
            color: #e28a10;
        }

        .total-color {
            color: #2563eb;
        }

        .content-panel {
            border: 1px solid rgba(255, 255, 255, 0.58);
            border-radius: 18px;
            background: rgba(255, 255, 255, 0.58);
            box-shadow: 0 18px 45px rgba(26, 64, 54, 0.10), inset 0 1px 0 rgba(255, 255, 255, 0.68);
            backdrop-filter: blur(18px) saturate(145%);
            -webkit-backdrop-filter: blur(18px) saturate(145%);
        }

        .panel-head {
            display: flex;
            justify-content: space-between;
            gap: 12px;
            padding: 18px 20px 8px;
        }

        .panel-title {
            margin: 0;
            color: #153b33;
            font-size: 19px;
            font-weight: 700;
        }

        .panel-meta {
            margin-top: 5px;
            color: #71837e;
            font-size: 13px;
        }

        .device-grid {
            display: grid;
            grid-template-columns: repeat(3, minmax(0, 1fr));
            gap: 14px;
            padding: 14px 20px 20px;
        }

        .detail-card {
            overflow: hidden;
            border: 1px solid rgba(255, 255, 255, 0.58);
            border-radius: 16px;
            background: rgba(255, 255, 255, 0.46);
            box-shadow: 0 12px 30px rgba(29, 70, 60, 0.08);
        }

        .device-photo {
            position: relative;
            height: 142px;
            overflow: hidden;
            background: linear-gradient(135deg, rgba(0, 150, 136, 0.12), rgba(255, 255, 255, 0.52));
        }

        .device-photo img {
            width: 100%;
            height: 100%;
            object-fit: contain;
            padding: 24px;
            box-sizing: border-box;
            filter: drop-shadow(0 12px 18px rgba(31, 63, 54, 0.16));
        }

        .status-pill {
            position: absolute;
            left: 14px;
            top: 14px;
            padding: 5px 11px;
            border-radius: 14px;
            color: #fff;
            font-size: 12px;
            line-height: 1;
        }

        .status-online {
            background: #009688;
        }

        .status-offline {
            background: #64748b;
        }

        .status-warning {
            background: #e28a10;
        }

        .detail-body {
            padding: 16px;
        }

        .device-name-row {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 8px;
            margin-bottom: 12px;
        }

        .device-name {
            color: #183b33;
            font-size: 17px;
            font-weight: 700;
        }

        .device-code {
            color: #71837e;
            font-size: 12px;
        }

        .info-list {
            display: grid;
            gap: 9px;
        }

        .info-item {
            display: grid;
            grid-template-columns: 74px 1fr;
            gap: 8px;
            color: #31443f;
            font-size: 13px;
            line-height: 1.55;
        }

        .info-label {
            color: #758780;
        }

        .location-action {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 10px;
            min-height: 42px;
            padding: 8px 10px;
            border: 1px solid rgba(0, 150, 136, 0.16);
            border-radius: 10px;
            background: linear-gradient(135deg, rgba(236, 253, 245, 0.82), rgba(255, 255, 255, 0.68));
            color: #24534a;
            cursor: pointer;
            box-sizing: border-box;
            transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
        }

        .location-action:hover {
            transform: translateY(-1px);
            border-color: rgba(0, 150, 136, 0.34);
            box-shadow: 0 10px 22px rgba(0, 105, 86, 0.12);
        }

        .location-action.disabled {
            cursor: default;
            color: #8b9a96;
            background: rgba(246, 248, 247, 0.72);
            border-color: rgba(148, 163, 184, 0.18);
        }

        .location-action.disabled:hover {
            transform: none;
            box-shadow: none;
        }

        .location-thumb {
            position: relative;
            flex: 0 0 54px;
            height: 34px;
            overflow: hidden;
            border-radius: 8px;
            background:
                linear-gradient(90deg, rgba(20, 184, 166, 0.13) 1px, transparent 1px),
                linear-gradient(rgba(20, 184, 166, 0.13) 1px, transparent 1px),
                linear-gradient(135deg, rgba(230, 252, 245, 0.96), rgba(214, 245, 235, 0.78));
            background-size: 14px 14px, 14px 14px, 100% 100%;
        }

        .location-pin {
            position: absolute;
            left: 50%;
            top: 50%;
            width: 12px;
            height: 12px;
            border: 2px solid #fff;
            border-radius: 50% 50% 50% 0;
            background: #ef4444;
            box-shadow: 0 4px 10px rgba(239, 68, 68, 0.28);
            transform: translate(-50%, -62%) rotate(-45deg);
        }

        .location-text {
            min-width: 0;
            flex: 1;
        }

        .location-title {
            display: block;
            font-weight: 700;
            line-height: 1.25;
        }

        .location-subtitle {
            display: block;
            margin-top: 2px;
            color: #6f817c;
            font-size: 12px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }

        .location-open {
            flex: 0 0 auto;
            color: #008f6a;
            font-size: 12px;
            font-weight: 700;
        }

        .map-modal-mask {
            position: fixed;
            inset: 0;
            z-index: 9998;
            display: none;
            align-items: center;
            justify-content: center;
            padding: 24px;
            background: rgba(9, 29, 25, 0.48);
            box-sizing: border-box;
        }

        .map-modal {
            width: min(960px, 100%);
            overflow: hidden;
            border-radius: 16px;
            background: #fff;
            box-shadow: 0 30px 90px rgba(0, 0, 0, 0.28);
        }

        .map-modal-head {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 14px;
            padding: 16px 18px;
            border-bottom: 1px solid #e7efec;
        }

        .map-modal-title {
            margin: 0;
            color: #153b33;
            font-size: 18px;
            font-weight: 800;
        }

        .map-modal-subtitle {
            margin-top: 4px;
            color: #6f817c;
            font-size: 13px;
        }

        .map-actions {
            display: flex;
            align-items: center;
            gap: 10px;
            flex: 0 0 auto;
        }

        .map-amap-link {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            height: 36px;
            padding: 0 14px;
            border-radius: 18px;
            background: #008f6a;
            color: #fff;
            font-size: 13px;
            font-weight: 700;
            text-decoration: none;
            white-space: nowrap;
        }

        .map-amap-link:hover {
            color: #fff;
            background: #00785c;
        }

        .map-close {
            width: 36px;
            height: 36px;
            border: 0;
            border-radius: 50%;
            background: #eef5f2;
            color: #31544c;
            cursor: pointer;
            font-size: 22px;
            line-height: 36px;
        }

        .map-frame-wrap {
            height: min(66vh, 560px);
            min-height: 360px;
            background: #eef5f2;
        }

        .map-frame,
        .map-canvas {
            display: block;
            width: 100%;
            height: 100%;
            border: 0;
        }

        .map-canvas {
            position: relative;
        }

        .map-fallback {
            display: none;
            align-items: center;
            justify-content: center;
            height: 100%;
            padding: 24px;
            color: #61736e;
            text-align: center;
            box-sizing: border-box;
        }

        .empty-state {
            display: none;
            padding: 54px 20px 64px;
            color: #71837e;
            text-align: center;
        }

        @media (max-width: 1100px) {
            .summary-grid,
            .device-grid {
                grid-template-columns: repeat(2, minmax(0, 1fr));
            }
        }

        @media (max-width: 720px) {
            body {
                padding: 12px;
            }

            .page-head {
                align-items: flex-start;
                flex-direction: column;
            }

            .summary-grid,
            .device-grid {
                grid-template-columns: 1fr;
            }

            .map-modal-mask {
                padding: 12px;
            }

            .map-frame-wrap {
                height: 62vh;
                min-height: 320px;
            }
        }

        .capability-panel {
            margin-top: 12px;
            padding: 12px;
            border-radius: 14px;
            background: rgba(243, 250, 247, 0.92);
            border: 1px solid rgba(0, 150, 136, 0.12);
        }

        .capability-title {
            font-size: 12px;
            font-weight: 700;
            color: #0f766e;
            margin-bottom: 8px;
        }

        .capability-meta {
            display: flex;
            flex-wrap: wrap;
            gap: 8px;
            margin-bottom: 10px;
            font-size: 12px;
            color: #4b635c;
        }

        .capability-tag {
            padding: 2px 8px;
            border-radius: 999px;
            background: rgba(0, 150, 136, 0.10);
            color: #0f766e;
        }

        .cap-bar-row {
            display: grid;
            grid-template-columns: 54px 1fr 38px;
            gap: 8px;
            align-items: center;
            margin-bottom: 6px;
            font-size: 11px;
            color: #56736c;
        }

        .cap-bar-track {
            height: 8px;
            border-radius: 999px;
            background: rgba(0, 0, 0, 0.06);
            overflow: hidden;
        }

        .cap-bar-fill {
            height: 100%;
            border-radius: 999px;
            background: linear-gradient(90deg, #16a374, #009688);
            transition: width 0.25s ease, background 0.25s ease;
        }

        .cap-bar-fill.level-good {
            background: linear-gradient(90deg, #16a374, #009688);
        }

        .cap-bar-fill.level-fair {
            background: linear-gradient(90deg, #84cc16, #65a30d);
        }

        .cap-bar-fill.level-warning {
            background: linear-gradient(90deg, #fb923c, #f97316);
        }

        .cap-bar-fill.level-critical {
            background: linear-gradient(90deg, #f87171, #ef4444);
        }

        .cap-value {
            font-weight: 700;
            text-align: right;
        }

        .cap-value.level-good { color: #0f766e; }
        .cap-value.level-fair { color: #4d7c0f; }
        .cap-value.level-warning { color: #c2410c; }
        .cap-value.level-critical { color: #b91c1c; }

        .capability-tag.tag-good {
            background: rgba(0, 150, 136, 0.12);
            color: #0f766e;
        }

        .capability-tag.tag-fair {
            background: rgba(132, 204, 22, 0.16);
            color: #4d7c0f;
        }

        .capability-tag.tag-warning {
            background: rgba(249, 115, 22, 0.16);
            color: #c2410c;
        }

        .capability-tag.tag-critical {
            background: rgba(239, 68, 68, 0.14);
            color: #b91c1c;
        }

        .capability-strategy {
            margin-top: 8px;
            font-size: 11px;
            line-height: 1.5;
            color: #56736c;
        }

        .capability-empty {
            margin-top: 10px;
            font-size: 12px;
            color: #8aa39c;
        }

        .capability-banner {
            margin-bottom: 14px;
            padding: 12px 16px;
            border-radius: 14px;
            background: rgba(232, 248, 240, 0.82);
            border: 1px solid rgba(0, 150, 136, 0.16);
            font-size: 13px;
            color: #35655b;
            display: none;
        }
    </style>
</head>
<body>
<div class="device-page">
    <div class="page-head">
        <div>
            <h1 class="page-title">设备管理</h1>
            <div class="page-subtitle">设备以稳定标识识别，IP 随心跳自动更新；已启用多维能力表征与自适应管理策略。</div>
        </div>
        <div class="view-filter">
            <button class="action-btn" type="button" id="btnAddDevice">新增设备</button>
            <button class="action-btn secondary" type="button" id="btnQrJoin">扫码添加</button>
            <button class="action-btn secondary" type="button" id="btnCapabilityEval">策略评测</button>
            <button class="filter-chip" type="button" onclick="window.location.href='/device/robots'">机器人协同</button>
            <button class="filter-chip active" type="button" data-filter="all">全部设备</button>
            <button class="filter-chip" type="button" data-filter="online">在线</button>
            <button class="filter-chip" type="button" data-filter="offline">离线</button>
            <button class="filter-chip" type="button" data-filter="warning">告警</button>
        </div>
    </div>

    <div class="capability-banner" id="capabilityBanner"></div>

    <div class="summary-grid">
        <div class="summary-card active" data-filter="all">
            <div class="summary-top">
                <img src="/static/images/icons/total.png" alt="设备总数" class="summary-icon">
                <div class="summary-count total-color" id="count-all">0</div>
            </div>
            <div class="summary-name">设备总数</div>
            <div class="summary-desc">查看全部设备基础信息</div>
        </div>
        <div class="summary-card" data-filter="online">
            <div class="summary-top">
                <img src="/static/images/icons/online.png" alt="在线设备" class="summary-icon">
                <div class="summary-count online-color" id="count-online">0</div>
            </div>
            <div class="summary-name">在线设备</div>
            <div class="summary-desc">运行正常，持续上报数据</div>
        </div>
        <div class="summary-card" data-filter="offline">
            <div class="summary-top">
                <img src="/static/images/icons/leave.png" alt="离线设备" class="summary-icon">
                <div class="summary-count offline-color" id="count-offline">0</div>
            </div>
            <div class="summary-name">离线设备</div>
            <div class="summary-desc">超过阈值未收到心跳</div>
        </div>
        <div class="summary-card" data-filter="warning">
            <div class="summary-top">
                <img src="/static/images/icons/alert.png" alt="告警设备" class="summary-icon">
                <div class="summary-count warning-color" id="count-warning">0</div>
            </div>
            <div class="summary-name">告警设备</div>
            <div class="summary-desc">存在异常，需要处理</div>
        </div>
    </div>

    <div class="type-filter-panel">
        <span class="type-filter-label">设备类型</span>
        <button class="type-chip active" type="button" data-type="all">全部类型 <span class="type-count" id="type-count-all">0</span></button>
        <button class="type-chip" type="button" data-type="jetson">
            <img src="/static/images/icons/jetson.svg" alt=""> Jetson <span class="type-count" id="type-count-jetson">0</span>
        </button>
        <button class="type-chip" type="button" data-type="raspberry">
            <img src="/static/images/icons/raspberry.svg" alt=""> 树莓派 <span class="type-count" id="type-count-raspberry">0</span>
        </button>
        <button class="type-chip" type="button" data-type="robot">
            <img src="/static/images/icons/robot.svg" alt=""> 机器人 <span class="type-count" id="type-count-robot">0</span>
        </button>
        <button class="type-chip" type="button" data-type="phone">
            <img src="/static/images/icons/phone.svg" alt=""> 手机 <span class="type-count" id="type-count-phone">0</span>
        </button>
    </div>

    <div class="content-panel">
        <div class="panel-head">
            <div>
                <h2 class="panel-title" id="panelTitle">全部设备</h2>
                <div class="panel-meta" id="panelMeta">共 0 台设备</div>
            </div>
        </div>
        <div class="device-grid" id="deviceGrid"></div>
        <div class="empty-state" id="emptyState">暂无该类型设备</div>
    </div>
</div>

<div class="form-modal-mask" id="addModalMask">
    <div class="form-modal">
        <div class="form-modal-head">
            <strong>新增设备</strong>
            <button class="map-close" type="button" id="addModalClose">&times;</button>
        </div>
        <div class="form-modal-body">
            <div class="form-row">
                <label for="addDeviceName">设备名称</label>
                <input id="addDeviceName" type="text" placeholder="例如：robot-002 / jetson-lab-01">
            </div>
            <div class="form-row">
                <label for="addDeviceType">设备类型</label>
                <select id="addDeviceType">
                    <option value="jetson">Jetson</option>
                    <option value="raspberry">树莓派</option>
                    <option value="robot">机器人</option>
                    <option value="phone">手机</option>
                    <option value="device">其他</option>
                </select>
            </div>
            <div class="form-row">
                <label for="addDeviceIp">设备 IP（板端必填，用于自动部署心跳）</label>
                <input id="addDeviceIp" type="text" placeholder="例如：192.168.123.41">
            </div>
            <div class="form-row">
                <label for="addSshUsername">SSH 用户名（Jetson/树莓派/机器人必填）</label>
                <input id="addSshUsername" type="text" placeholder="例如：hzauaiot / pi">
            </div>
            <div class="form-row">
                <label for="addSshPassword">SSH 密码</label>
                <input id="addSshPassword" type="password" placeholder="SSH 登录密码">
            </div>
            <div class="form-row">
                <label for="addDeviceKey">设备标识 deviceKey（可选，留空自动生成）</label>
                <input id="addDeviceKey" type="text" placeholder="例如：robot-001">
            </div>
            <div class="panel-meta" style="margin-bottom:10px;">
                添加 Jetson/树莓派/机器人时会自动 SSH 部署心跳并设为开机自启；手机请用「扫码添加」。
            </div>
            <div class="form-actions">
                <button class="action-btn secondary" type="button" id="addModalCancel">取消</button>
                <button class="action-btn" type="button" id="addModalSubmit">创建</button>
            </div>
        </div>
    </div>
</div>

<div class="qr-modal-mask" id="qrModalMask">
    <div class="qr-modal">
        <div class="qr-modal-head">
            <strong>手机扫码加入</strong>
            <button class="map-close" type="button" id="qrModalClose">&times;</button>
        </div>
        <div class="qr-modal-body">
            <div class="qr-box"><div id="qrcode"></div></div>
            <div class="qr-url" id="qrJoinUrl">正在生成邀请...</div>
            <div class="panel-meta" style="margin-top:10px;">
                邀请默认 30 分钟有效。手机须与电脑同一 Wi‑Fi；链接须是局域网 IP（如 192.168.124.8），不能是 localhost。<br>
                微信若提示网络错误，可点右上角用系统浏览器打开，或用相机/Safari 扫码。
            </div>
            <div class="form-actions">
                <button class="action-btn secondary" type="button" id="qrRefresh">重新生成</button>
            </div>
        </div>
    </div>
</div>

<div class="map-modal-mask" id="mapModalMask">
    <div class="map-modal">
        <div class="map-modal-head">
            <div>
                <h3 class="map-modal-title" id="mapModalTitle">设备位置</h3>
                <div class="map-modal-subtitle" id="mapModalSubtitle">-</div>
            </div>
            <div class="map-actions">
                <a class="map-amap-link" id="mapAmapLink" href="#" target="_blank" rel="noopener">在高德地图打开</a>
                <button class="map-close" type="button" id="mapModalClose" aria-label="关闭地图">&times;</button>
            </div>
        </div>
        <div class="map-frame-wrap">
            <div class="map-canvas" id="deviceMapCanvas"></div>
            <div class="map-fallback" id="mapFallback">地图组件加载失败，请检查网络后重试，或点击右上角在高德地图打开。</div>
        </div>
    </div>
</div>

<script>
    var devices = [];
    var currentFilter = 'all';
    var currentType = 'all';

    var titleMap = {
        all: '全部设备',
        online: '在线设备',
        offline: '离线设备',
        warning: '告警设备'
    };

    var typeTitleMap = {
        all: '',
        jetson: ' · Jetson',
        raspberry: ' · 树莓派',
        robot: ' · 机器人',
        phone: ' · 手机'
    };

    function normalizeType(device) {
        var type = String(device.deviceType || '').toLowerCase();
        var name = String(device.name || device.deviceName || '').toLowerCase();
        var text = String(device.deviceTypeText || '');
        if (type.indexOf('jetson') >= 0 || name.indexOf('jetson') >= 0) return 'jetson';
        if (type.indexOf('raspberry') >= 0 || name.indexOf('raspberry') >= 0 || text.indexOf('树莓') >= 0) return 'raspberry';
        if (type.indexOf('robot') >= 0 || name.indexOf('robot') >= 0 || text.indexOf('机器') >= 0) return 'robot';
        if (type.indexOf('phone') >= 0 || type.indexOf('mobile') >= 0 || name.indexOf('phone') >= 0 || text.indexOf('手机') >= 0) return 'phone';
        return 'other';
    }

    function getStatusClass(status) {
        if (status === 'online') return 'status-online';
        if (status === 'offline') return 'status-offline';
        return 'status-warning';
    }

    function escapeHtml(value) {
        return String(value == null ? '-' : value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function parseCoordinate(value) {
        if (value == null) return null;
        var number = parseFloat(String(value).trim());
        return isNaN(number) ? null : number;
    }

    function getDeviceLocation(device) {
        var lat = parseCoordinate(device.latituded);
        var lng = parseCoordinate(device.longituded);
        if (lat === null || lng === null) {
            return null;
        }

        if (Math.abs(lat) > 90 && Math.abs(lng) <= 90) {
            var temp = lat;
            lat = lng;
            lng = temp;
        }

        if (Math.abs(lat) > 90 || Math.abs(lng) > 180) {
            return null;
        }

        return {
            lat: lat,
            lng: lng,
            text: lat.toFixed(6) + ', ' + lng.toFixed(6)
        };
    }

    function hasLocation(device) {
        return getDeviceLocation(device) !== null;
    }

    function renderLocationAction(device) {
        var location = getDeviceLocation(device);
        if (!location) {
            return '' +
                '<div class="location-action disabled">' +
                '  <div class="location-thumb"></div>' +
                '  <span class="location-text">' +
                '    <span class="location-title">未设置位置</span>' +
                '    <span class="location-subtitle">实验室固定坐标</span>' +
                '  </span>' +
                '</div>';
        }

        return '' +
            '<button class="location-action js-open-map" type="button" data-device-id="' + escapeHtml(device.id) + '">' +
            '  <span class="location-thumb"><span class="location-pin"></span></span>' +
            '  <span class="location-text">' +
            '    <span class="location-title">查看地图位置</span>' +
            '    <span class="location-subtitle">' + escapeHtml(location.text) + '</span>' +
            '  </span>' +
            '  <span class="location-open">放大</span>' +
            '</button>';
    }

    function buildAmapUrl(lat, lng, name) {
        return 'https://uri.amap.com/marker?position=' +
            encodeURIComponent(lng + ',' + lat) +
            '&name=' + encodeURIComponent(name || '设备位置') +
            '&src=' + encodeURIComponent('robot-collaboration-admin') +
            '&coordinate=gaode' +
            '&callnative=0';
    }

    function openDeviceMap(device) {
        var location = getDeviceLocation(device);
        if (!location) {
            return;
        }

        var amapUrl = buildAmapUrl(location.lat, location.lng, device.name);
        $('#mapModalTitle').text(device.name + ' 位置');
        $('#mapModalSubtitle').text('经纬度：' + location.text + ' | IP：' + (device.ip || '-'));
        $('#mapAmapLink').attr('href', amapUrl);
        $('#mapFallback').hide();
        $('#deviceMapCanvas').show().empty();
        $('#mapModalMask').css('display', 'flex');

        window.setTimeout(function () {
            renderBaiduMap(location.lat, location.lng, device.name);
        }, 80);
    }

    function closeDeviceMap() {
        $('#mapModalMask').hide();
        $('#deviceMapCanvas').empty();
        $('#mapAmapLink').attr('href', '#');
    }

    function renderBaiduMap(lat, lng, name) {
        if (!window.BMap) {
            $('#deviceMapCanvas').hide();
            $('#mapFallback').css('display', 'flex');
            return;
        }

        var point = new BMap.Point(lng, lat);
        var map = new BMap.Map('deviceMapCanvas');
        map.centerAndZoom(point, 16);
        map.enableScrollWheelZoom(true);
        map.addControl(new BMap.NavigationControl());
        map.addControl(new BMap.ScaleControl());

        var marker = new BMap.Marker(point);
        map.addOverlay(marker);

        var info = new BMap.InfoWindow(name || '设备位置');
        marker.addEventListener('click', function () {
            map.openInfoWindow(info, point);
        });
        map.openInfoWindow(info, point);
    }

    function renderCounts() {
        $('#count-all').text(devices.length);
        $('#count-online').text(devices.filter(function (item) { return item.status === 'online'; }).length);
        $('#count-offline').text(devices.filter(function (item) { return item.status === 'offline'; }).length);
        $('#count-warning').text(devices.filter(function (item) { return item.status === 'warning'; }).length);

        var typeKeys = ['jetson', 'raspberry', 'robot', 'phone'];
        var typeCounts = { all: devices.length, jetson: 0, raspberry: 0, robot: 0, phone: 0 };
        devices.forEach(function (item) {
            var t = normalizeType(item);
            if (typeCounts[t] !== undefined) {
                typeCounts[t] += 1;
            }
        });
        $('#type-count-all').text(typeCounts.all);
        typeKeys.forEach(function (key) {
            $('#type-count-' + key).text(typeCounts[key]);
        });
    }

    function getFilteredDevices() {
        return devices.filter(function (item) {
            var statusOk = currentFilter === 'all' || item.status === currentFilter;
            var typeOk = currentType === 'all' || normalizeType(item) === currentType;
            return statusOk && typeOk;
        });
    }

    function getCapabilityLevel(pct) {
        if (pct >= 75) {
            return { cls: 'level-good', label: '充足' };
        }
        if (pct >= 50) {
            return { cls: 'level-fair', label: '一般' };
        }
        if (pct >= 30) {
            return { cls: 'level-warning', label: '偏不足' };
        }
        return { cls: 'level-critical', label: '严重不足' };
    }

    function renderCapabilityPanel(device) {
        var cap = device.capability;
        if (!cap) {
            return '<div class="capability-empty">能力画像生成中，请等待下一次心跳...</div>';
        }

        var vector = cap.capabilityVector || {};
        var strategy = cap.strategy || {};
        var healthPct = Math.round(Number(cap.healthScore || 0) * 100);
        var healthLevel = getCapabilityLevel(healthPct);
        var bars = ['C_comp', 'C_sense', 'C_comm', 'C_act', 'C_health'].map(function (key) {
            var labelMap = {
                C_comp: '计算',
                C_sense: '感知',
                C_comm: '通信',
                C_act: '执行',
                C_health: '健康'
            };
            var value = Number(vector[key] || 0);
            var pct = Math.round(value * 100);
            var level = getCapabilityLevel(pct);
            return '' +
                '<div class="cap-bar-row">' +
                '  <span title="' + escapeHtml(level.label) + '">' + labelMap[key] + '</span>' +
                '  <div class="cap-bar-track"><div class="cap-bar-fill ' + level.cls + '" style="width:' + pct + '%"></div></div>' +
                '  <span class="cap-value ' + level.cls + '" title="' + escapeHtml(level.label) + '">' + pct + '%</span>' +
                '</div>';
        }).join('');

        return '' +
            '<div class="capability-panel">' +
            '  <div class="capability-title">能力画像 · 自适应策略</div>' +
            '  <div class="capability-meta">' +
            '    <span class="capability-tag">场景 ' + escapeHtml(cap.sceneText || cap.sceneTag || '-') + '</span>' +
            '    <span class="capability-tag">角色 ' + escapeHtml(cap.roleText || cap.roleHint || '-') + '</span>' +
            '    <span class="capability-tag tag-' + healthLevel.cls.replace('level-', '') + '">健康 ' + healthPct + '% · ' + healthLevel.label + '</span>' +
            '  </div>' +
            bars +
            '  <div class="capability-strategy">' +
            '    策略：' + escapeHtml(strategy.summary || '-') + '<br>' +
            '    接入：' + escapeHtml(strategy.accessPolicy || '-') +
            ' · 监测：' + escapeHtml(strategy.monitorLevel || '-') +
            ' · 交互：' + escapeHtml(strategy.interactionMode || '-') +
            ' · 控制：' + escapeHtml(strategy.controlMode || '-') + '<br>' +
            '    下发：' + escapeHtml(cap.strategyPushStatusText || '未下发') +
            (cap.lastStrategyAckAt ? ' · 设备已回执' : '') +
            '  </div>' +
            '</div>';
    }

    function loadCapabilityBanner() {
        $.when(
            $.getJSON('/device/api/capability/ontology'),
            $.getJSON('/device/api/capability/evaluate')
        ).done(function (ontologyRes, evalRes) {
            var ontology = ontologyRes[0] || {};
            var evaluation = evalRes[0] || {};
            var text = '能力本体已加载：' + (ontology.nodeCount || 0) + ' 个节点 / '
                + (ontology.edgeCount || 0) + ' 条关系 / '
                + (ontology.deviceClassCount || 0) + ' 类设备模板；'
                + '策略适配评测准确率 ' + (evaluation.accuracy || 0) + '%（'
                + (evaluation.matchedCases || 0) + '/' + (evaluation.totalCases || 0) + '）';
            $('#capabilityBanner').text(text).show();
        }).fail(function () {
            $('#capabilityBanner').hide();
        });
    }

    function renderDevices(filter, type) {
        if (typeof filter === 'string') {
            currentFilter = filter;
        }
        if (typeof type === 'string') {
            currentType = type;
        }

        var list = getFilteredDevices();
        $('#panelTitle').text(titleMap[currentFilter] + (typeTitleMap[currentType] || ''));
        $('#panelMeta').text('共 ' + list.length + ' 台设备');

        $('.summary-card').removeClass('active');
        $('.summary-card[data-filter="' + currentFilter + '"]').addClass('active');
        $('.filter-chip[data-filter]').removeClass('active');
        $('.filter-chip[data-filter="' + currentFilter + '"]').addClass('active');
        $('.type-chip').removeClass('active');
        $('.type-chip[data-type="' + currentType + '"]').addClass('active');

        if (list.length === 0) {
            $('#deviceGrid').empty();
            $('#emptyState').text('暂无该类型设备').show();
            return;
        }

        $('#emptyState').hide();
        var html = list.map(function (device) {
            return '' +
                '<div class="detail-card">' +
                '  <div class="device-photo">' +
                '    <span class="status-pill ' + getStatusClass(device.status) + '">' + escapeHtml(device.statusText) + '</span>' +
                '    <img src="' + escapeHtml(device.image) + '" alt="' + escapeHtml(device.name) + '">' +
                '  </div>' +
                '  <div class="detail-body">' +
                '    <div class="device-name-row">' +
                '      <div class="device-name">' + escapeHtml(device.name) + '</div>' +
                '      <div class="device-code">' + escapeHtml(device.code) + '</div>' +
                '    </div>' +
                '    <div class="info-list">' +
                '      <div class="info-item"><span class="info-label">设备ID</span><span>' + escapeHtml(device.id) + '</span></div>' +
                '      <div class="info-item"><span class="info-label">标识Key</span><span>' + escapeHtml(device.deviceKey || device.code) + '</span></div>' +
                '      <div class="info-item"><span class="info-label">类型</span><span>' + escapeHtml(device.deviceTypeText || device.deviceType || '-') + '</span></div>' +
                '      <div class="info-item"><span class="info-label">IP地址</span><span>' + escapeHtml(device.ip) + '</span></div>' +
                '      <div class="info-item"><span class="info-label">位置</span><span>' + renderLocationAction(device) + '</span></div>' +
                '      <div class="info-item"><span class="info-label">最近上报</span><span>' + escapeHtml(device.lastReport) + '</span></div>' +
                '    </div>' +
                renderCapabilityPanel(device) +
                '    <div class="card-actions">' +
                '      <button class="danger-btn js-delete-device" type="button" data-device-id="' + escapeHtml(device.id) + '" data-device-name="' + escapeHtml(device.name) + '">删除设备</button>' +
                (needsRedeployButton(device)
                    ? '      <button class="action-btn secondary js-redeploy-device" type="button" style="height:32px;padding:0 12px;font-size:12px;" data-device-id="' + escapeHtml(device.id) + '">重新部署心跳</button>'
                    : '') +
                '    </div>' +
                '  </div>' +
                '</div>';
        }).join('');

        $('#deviceGrid').html(html);
    }

    function needsRedeployButton(device) {
        var t = normalizeType(device);
        return t === 'jetson' || t === 'raspberry' || t === 'robot';
    }

    function openAddModal() {
        $('#addDeviceName').val('');
        $('#addDeviceIp').val('');
        $('#addDeviceKey').val('');
        $('#addSshUsername').val('');
        $('#addSshPassword').val('');
        $('#addDeviceType').val('jetson');
        $('#addModalMask').css('display', 'flex');
    }

    function closeAddModal() {
        $('#addModalMask').hide();
    }

    function submitAddDevice() {
        var deviceType = $('#addDeviceType').val();
        var payload = {
            deviceName: $.trim($('#addDeviceName').val()),
            deviceType: deviceType,
            ipAddress: $.trim($('#addDeviceIp').val()),
            deviceKey: $.trim($('#addDeviceKey').val()),
            sshUsername: $.trim($('#addSshUsername').val()),
            sshPassword: $('#addSshPassword').val(),
            autoDeploy: true
        };
        if (!payload.deviceName) {
            alert('请填写设备名称');
            return;
        }
        if (deviceType !== 'phone' && deviceType !== 'device') {
            if (!payload.ipAddress || !payload.sshUsername || !payload.sshPassword) {
                alert('Jetson/树莓派/机器人需要填写 IP、SSH 用户名和密码，才能自动部署心跳');
                return;
            }
        }
        $('#addModalSubmit').prop('disabled', true).text('创建并部署中...');
        $.ajax({
            url: '/device/api',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(payload),
            timeout: 180000
        }).done(function (res) {
            closeAddModal();
            loadDevices();
            var msg = (res && res.message) || '完成';
            if (res && res.brokerHost) {
                msg += '\nMQTT: ' + res.brokerHost;
            }
            if (res && res.device && res.device.deviceKey) {
                msg += '\ndeviceKey: ' + res.device.deviceKey;
            }
            alert(msg);
            if (res && res.deploySuccess) {
                window.setTimeout(loadDevices, 8000);
                window.setTimeout(loadDevices, 15000);
            }
        }).fail(function (xhr) {
            var res = xhr.responseJSON || {};
            // 207 Multi-Status：设备已创建但部署失败
            if (xhr.status === 207 && res.device) {
                closeAddModal();
                loadDevices();
                var tip = (res.message || '设备已创建，但心跳部署失败') + '\n\n'
                    + '说明：设备记录已入库；若显示在线，可能来自该机器上的旧心跳，不代表本次部署成功。\n'
                    + '建议立即重新部署心跳。';
                if (res.deployMessage) {
                    tip += '\n\n失败原因：' + res.deployMessage;
                }
                if (res.device && res.device.deviceKey) {
                    tip += '\ndeviceKey: ' + res.device.deviceKey;
                }
                tip += '\n\n是否现在重新部署？';
                if (window.confirm(tip) && res.device && res.device.id) {
                    redeployDevice(res.device.id);
                }
                return;
            }
            alert(res.message || '创建失败');
        }).always(function () {
            $('#addModalSubmit').prop('disabled', false).text('创建');
        });
    }

    function redeployDevice(id) {
        if (!window.confirm('确认重新部署该设备的心跳服务？')) {
            return;
        }
        $.ajax({
            url: '/device/api/' + id + '/redeploy',
            type: 'POST',
            timeout: 180000
        }).done(function (res) {
            alert((res && res.message) || '部署完成');
            window.setTimeout(loadDevices, 8000);
        }).fail(function (xhr) {
            alert((xhr.responseJSON && xhr.responseJSON.message) || '重新部署失败');
        });
    }

    function deleteDevice(id, name) {
        if (!window.confirm('确认删除设备「' + name + '」？此操作不可恢复。')) {
            return;
        }
        $.ajax({
            url: '/device/api/' + id,
            type: 'DELETE'
        }).done(function (res) {
            if (!res || !res.success) {
                alert((res && res.message) || '删除失败');
                return;
            }
            loadDevices();
            alert((res && res.message) || '设备已删除');
        }).fail(function (xhr) {
            alert((xhr.responseJSON && xhr.responseJSON.message) || '删除失败');
        });
    }

    var qrcodeInstance = null;

    function renderQr(url) {
        $('#qrcode').empty();
        if (window.QRCode) {
            qrcodeInstance = new QRCode(document.getElementById('qrcode'), {
                text: url,
                width: 220,
                height: 220
            });
        } else {
            $('#qrcode').html('<img alt="qr" src="https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=' + encodeURIComponent(url) + '">');
        }
        $('#qrJoinUrl').text(url);
    }

    function createQrInvite() {
        $('#qrJoinUrl').text('正在生成邀请...');
        $('#qrcode').empty();
        $.ajax({
            url: '/device/api/invite',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({ ttlMinutes: 30 })
        }).done(function (res) {
            if (!res || !res.success || !res.joinUrl) {
                $('#qrJoinUrl').text((res && res.message) || '生成失败');
                return;
            }
            renderQr(res.joinUrl);
        }).fail(function () {
            $('#qrJoinUrl').text('生成邀请失败，请检查登录状态或服务接口');
        });
    }

    function openQrModal() {
        $('#qrModalMask').css('display', 'flex');
        createQrInvite();
    }

    function closeQrModal() {
        $('#qrModalMask').hide();
    }

    function loadDevices() {
        return $.ajax({
            url: '/device/api/list',
            type: 'GET',
            dataType: 'json',
            cache: false
        }).done(function (data) {
            devices = $.isArray(data) ? data : [];
            renderCounts();
            renderDevices(currentFilter);
        }).fail(function () {
            devices = [];
            renderCounts();
            renderDevices(currentFilter);
            $('#emptyState').text('设备数据加载失败，请检查服务接口').show();
        });
    }

    function connectDeviceSocket() {
        if (!window.WebSocket) {
            return;
        }

        var protocol = window.location.protocol === 'https:' ? 'wss://' : 'ws://';
        var socket = new WebSocket(protocol + window.location.host + '/ws/device');

        socket.onmessage = function () {
            loadDevices();
        };

        socket.onclose = function () {
            window.setTimeout(connectDeviceSocket, 3000);
        };
    }

    $(function () {
        renderCounts();
        renderDevices(currentFilter);
        loadDevices();
        connectDeviceSocket();

        $('.summary-card, .filter-chip[data-filter]').on('click', function () {
            var filter = $(this).data('filter');
            if (!filter) {
                return;
            }
            renderDevices(filter, currentType);
        });

        $('.type-chip').on('click', function () {
            renderDevices(currentFilter, $(this).data('type'));
        });

        $('#deviceGrid').on('click', '.js-open-map', function () {
            var deviceId = String($(this).data('device-id'));
            var device = devices.find(function (item) {
                return String(item.id) === deviceId;
            });
            if (device) {
                openDeviceMap(device);
            }
        });

        $('#deviceGrid').on('click', '.js-delete-device', function () {
            deleteDevice($(this).data('device-id'), $(this).data('device-name'));
        });

        $('#deviceGrid').on('click', '.js-redeploy-device', function () {
            redeployDevice($(this).data('device-id'));
        });

        $('#btnAddDevice').on('click', openAddModal);
        $('#addModalClose, #addModalCancel').on('click', closeAddModal);
        $('#addModalSubmit').on('click', submitAddDevice);
        $('#addModalMask').on('click', function (event) {
            if (event.target === this) {
                closeAddModal();
            }
        });

        $('#btnQrJoin').on('click', openQrModal);
        $('#btnCapabilityEval').on('click', function () {
            $.getJSON('/device/api/capability/evaluate').done(function (res) {
                var lines = ['策略适配评测结果：准确率 ' + (res.accuracy || 0) + '%'];
                if (res.details && res.details.length) {
                    res.details.forEach(function (item) {
                        lines.push((item.pass ? '[通过]' : '[未通过] ') + item.case
                            + ' · 角色 ' + item.actualRole + ' · 降级 ' + item.actualDegrade);
                    });
                }
                alert(lines.join('\n'));
            }).fail(function () {
                alert('策略评测请求失败');
            });
        });
        loadCapabilityBanner();
        $('#qrModalClose').on('click', closeQrModal);
        $('#qrRefresh').on('click', createQrInvite);
        $('#qrModalMask').on('click', function (event) {
            if (event.target === this) {
                closeQrModal();
            }
        });

        $('#mapModalClose').on('click', closeDeviceMap);
        $('#mapModalMask').on('click', function (event) {
            if (event.target === this) {
                closeDeviceMap();
            }
        });

        $(document).on('keydown', function (event) {
            if (event.key === 'Escape') {
                closeDeviceMap();
                closeAddModal();
                closeQrModal();
            }
        });

        window.setInterval(loadDevices, 15000);
    });
</script>
</body>
</html>
