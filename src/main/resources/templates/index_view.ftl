<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>未来植物原型系统</title>
    <script src="static/js/jquery-3.4.1.min.js"></script>
    <script src="static/layui/layui.js"></script>
    <link rel="stylesheet" href="/Users/liuzhenhao/Downloads/刘嚎嚎的科研之路/code/springboot-layui-admin-master/src/main/resources/static/layui/css/liu.css">
    <link rel="stylesheet" href="static/layui/css/layui.css"  media="all">
    <style>
        .layui-side,
        .layui-side-scroll,
        .layui-side .layui-nav {
            background: #ffffff !important;
        }

        .layui-side .layui-nav {
            padding: 10px 8px;
            box-sizing: border-box;
        }

        .layui-side .layui-nav .layui-nav-item {
            margin-bottom: 6px;
            background: transparent !important;
        }

        .layui-side .layui-nav > .layui-nav-item > a {
            display: block;
            height: 44px;
            line-height: 44px;
            padding: 0 18px;
            border-radius: 8px;
            color: #263a35 !important;
            text-align: center;
            transition: background-color 0.18s ease, color 0.18s ease, box-shadow 0.18s ease;
        }

        .layui-side .layui-nav > .layui-nav-item:not(.layui-this) > a:hover {
            background: #eef8f3 !important;
            color: #008f6a !important;
        }

        .layui-side .layui-nav > .layui-nav-item.layui-this > a,
        .layui-side .layui-nav > .layui-nav-item.layui-this > a:hover {
            background: #009688 !important;
            color: #ffffff !important;
            box-shadow: 0 6px 14px rgba(0, 150, 136, 0.22);
        }

        .layui-side .layui-nav .layui-nav-more {
            right: 16px;
            border-top-color: #7b8d88;
        }

        .layui-side .layui-nav .layui-nav-mored {
            border-color: transparent transparent #ffffff;
        }

        .layui-side .layui-nav .robot-collab-nav.layui-nav-itemed > a {
            background: #f2faf7 !important;
            color: #008f6a !important;
            box-shadow: none;
        }

        .layui-side .layui-nav .robot-collab-nav .layui-nav-child {
            position: static;
            display: none;
            margin: 4px 0 8px 14px;
            padding: 4px 0 4px 12px;
            border-left: 2px solid #dcece7;
            background: transparent !important;
            box-shadow: none;
        }

        .layui-side .layui-nav .robot-collab-nav.layui-nav-itemed > .layui-nav-child {
            display: block;
        }

        .layui-side .layui-nav .robot-collab-nav .layui-nav-child dd {
            margin: 2px 0;
            background: transparent !important;
        }

        .layui-side .layui-nav .robot-collab-nav .layui-nav-child dd a {
            display: block;
            height: 34px;
            line-height: 34px;
            padding: 0 12px;
            border-radius: 6px;
            color: #5d706b !important;
            text-align: left;
            font-size: 13px;
            background: transparent !important;
        }

        .layui-side .layui-nav .robot-collab-nav .layui-nav-child dd a:hover {
            background: #eef8f3 !important;
            color: #008f6a !important;
        }

        .layui-side .layui-nav .robot-collab-nav .layui-nav-child dd.layui-this a,
        .layui-side .layui-nav .robot-collab-nav .layui-nav-child dd.layui-this a:hover {
            background: #e4f4ef !important;
            color: #007864 !important;
            font-weight: 600;
        }

        /* 未来植物：二级可展开项（设备控制 / 作物表型 / 历史数据） */
        .layui-side .layui-nav .robot-collab-nav .plant-subnav {
            position: relative;
        }

        .layui-side .layui-nav .robot-collab-nav .plant-subnav > a {
            position: relative;
            padding-right: 28px !important;
            cursor: pointer;
        }

        .layui-side .layui-nav .robot-collab-nav .plant-subnav > a:after {
            content: "";
            position: absolute;
            right: 12px;
            top: 50%;
            width: 0;
            height: 0;
            margin-top: -3px;
            border-left: 5px solid transparent;
            border-right: 5px solid transparent;
            border-top: 5px solid #7b8d88;
            transition: transform 0.18s ease;
        }

        .layui-side .layui-nav .robot-collab-nav .plant-subnav.is-open > a {
            background: #eef8f3 !important;
            color: #008f6a !important;
            font-weight: 700;
        }

        .layui-side .layui-nav .robot-collab-nav .plant-subnav.is-open > a:after {
            transform: rotate(180deg);
            border-top-color: #008f6a;
        }

        .layui-side .layui-nav .robot-collab-nav .plant-subnav > .plant-level3 {
            display: none !important;
            margin: 2px 0 6px 10px !important;
            padding: 2px 0 2px 10px !important;
            border-left: 2px solid #cfe8e1;
            background: transparent !important;
            box-shadow: none !important;
            position: static !important;
        }

        .layui-side .layui-nav .robot-collab-nav .plant-subnav.is-open > .plant-level3 {
            display: block !important;
        }

        .layui-side .layui-nav .robot-collab-nav .plant-subnav > .plant-level3 dd a {
            height: 30px;
            line-height: 30px;
            font-size: 12px;
            color: #6b7f79 !important;
            padding-left: 10px;
        }

        .layui-side .layui-nav .robot-collab-nav .plant-subnav > .plant-level3 dd.layui-this a,
        .layui-side .layui-nav .robot-collab-nav .plant-subnav > .plant-level3 dd.layui-this a:hover {
            background: #dff3ec !important;
            color: #007864 !important;
            font-weight: 700;
        }

        .layui-side .layui-nav .layui-this:after,
        .layui-side .layui-nav-bar {
            display: none !important;
        }

        .layui-layout-admin .layui-body {
            bottom: 0;
            overflow: hidden;
        }

        .admin-frame-wrap {
            width: 100%;
            height: 100%;
            padding: 15px;
            box-sizing: border-box;
        }

        .admin-frame {
            display: block;
            width: 100%;
            height: 100%;
            border: 0;
        }

        .header-weather {
            position: absolute;
            right: 24px;
            top: 0;
            height: 60px;
            display: flex;
            align-items: center;
            gap: 10px;
            color: #1f3b36;
            user-select: none;
        }

        .header-weather-icon {
            width: 28px;
            height: 28px;
            flex-shrink: 0;
        }

        .header-weather-icon svg {
            display: block;
            width: 100%;
            height: 100%;
        }

        .header-weather-meta {
            display: flex;
            flex-direction: column;
            justify-content: center;
            line-height: 1.2;
        }

        .header-weather-city {
            font-size: 14px;
            font-weight: 600;
            color: #0f766e;
        }

        .header-weather-temp {
            margin-top: 2px;
            font-size: 12px;
            color: #5b716c;
        }
    </style>

</head>


<body class="layui-layout-body">

<div class="layui-layout layui-layout-admin" style="height: 100%">
    <div class="layui-header" style="background-color: white; position: relative;">
        <div style="padding-left: 5px; width: 600px; height: 60px">
            <!-- 在这里替换具体的LOGO和标语 -->
            <img src="/static/images/huanong.jpg" alt="LOGO" style="height: 50px; float: left; margin-left: 10px; margin-top: 5px;">

            <!-- 标题 -->
            <div style="margin-left: 20px; float: left; width: 450px; height: 100%; line-height: 60px; text-align: left; color: #009688; font-size: 24px;">
                未来植物原型系统
            </div>

            <#--             <div style="margin-left:20px;float:left;width:500px;height:100%;line-height:60px;text-align:left;color:#009688;font-size:26px;">智慧农业生猪养殖协同与决策系统</div>-->
        </div>

        <div class="header-weather" id="headerWeather" title="武汉天气">
            <div class="header-weather-icon" id="headerWeatherIcon" aria-hidden="true"></div>
            <div class="header-weather-meta">
                <div class="header-weather-city">武汉市</div>
                <div class="header-weather-temp" id="headerWeatherTemp">天气加载中...</div>
            </div>
        </div>
    </div>

    <div class="layui-side layui-bg-white">
        <div class="layui-side-scroll layui-bg-white">
            <!-- 左侧导航区域（可配合layui已有的垂直导航） -->
            <ul class="layui-nav layui-nav-tree"  lay-filter="test">
                <li class="layui-nav-item layui-this"><a href="/plant/dashboard" target="admin-list">系统总览</a></li>
                <li class="layui-nav-item robot-collab-nav">
                    <a href="javascript:;">设备控制</a>
                    <dl class="layui-nav-child">
                        <dd><a href="/plant/control/led" target="admin-list">LED补光</a></dd>
                        <dd><a href="/plant/control/actuators" target="admin-list">通风供水与自动化</a></dd>
                        <dd><a href="/plant/control/camera" target="admin-list">云台相机</a></dd>
                        <dd><a href="/plant/control/logs" target="admin-list">指令日志</a></dd>
                    </dl>
                </li>
                <li class="layui-nav-item robot-collab-nav">
                    <a href="javascript:;">作物表型</a>
                    <dl class="layui-nav-child">
                        <dd><a href="/plant/phenotype/digital" target="admin-list">数字化植株</a></dd>
                        <dd><a href="/plant/phenotype/metrics" target="admin-list">表型指标</a></dd>
                        <dd><a href="/plant/phenotype/media" target="admin-list">多模态影像</a></dd>
                        <dd><a href="/plant/phenotype/jobs" target="admin-list">分析任务</a></dd>
                    </dl>
                </li>
                <li class="layui-nav-item robot-collab-nav">
                    <a href="javascript:;">历史与数据</a>
                    <dl class="layui-nav-child">
                        <dd><a href="/plant/history" target="admin-list">环境历史</a></dd>
                        <dd><a href="/plant/phenotype/metrics" target="admin-list">表型历史</a></dd>
                        <dd><a href="/plant/data" target="admin-list">数据导出</a></dd>
                    </dl>
                </li>
                <li class="layui-nav-item"><a href="/devices" target="admin-list">设备管理</a></li>
                <li class="layui-nav-item"><a href="/model_library" target="admin-list">模型库</a></li>
                <li class="layui-nav-item"><a href="/datasets" target="admin-list">数据集</a></li>
                <li class="layui-nav-item"><a href="/auth" target="admin-list">权限管理</a></li>
            </ul>
        </div>
    </div>

    <div class="layui-body" style="background-color: #f9f9f9;">
        <!-- 内容主体区域 - 局部刷新, 使用iframe进行实现 -->
        <div class="admin-frame-wrap">
            <iframe class="admin-frame" name="admin-list" scrolling="auto" src="/plant/dashboard"></iframe>
        </div>
    </div>
</div>

</body>

<script src="static/layui/layui.js" charset="utf-8"></script>
<script>
    layui.use(['element', 'layer'], function(){
        var element = layui.element;

        function openPlantSubnav($item) {
            if (!$item || !$item.length) return;
            $('.plant-subnav').not($item).removeClass('is-open')
                .find('.plant-level3 dd').removeClass('layui-this');
            $item.addClass('is-open');
            $item.closest('.layui-nav-item').addClass('layui-nav-itemed');
        }

        // 点二级可展开项：展开三级，并打开默认子页
        $(document).on('click', '.plant-subnav > a', function () {
            var $item = $(this).parent();
            openPlantSubnav($item);
            $item.addClass('layui-this').siblings().removeClass('layui-this');
            $item.find('.plant-level3 dd').removeClass('layui-this');
            $item.find('.plant-level3 dd:first').addClass('layui-this');
        });

        // 点三级叶子：保持对应二级展开，收起其他二级
        $(document).on('click', '.plant-subnav .plant-level3 a', function () {
            var $dd = $(this).parent();
            var $item = $dd.closest('.plant-subnav');
            openPlantSubnav($item);
            $dd.addClass('layui-this').siblings().removeClass('layui-this');
            $item.siblings().removeClass('layui-this');
        });

        // 让左侧菜单的选中状态跟随 iframe 页面切换。
        element.on('nav(test)', function(elem){
            var parent = elem.parent();
            if (parent.is('dd')) {
                parent.addClass('layui-this').siblings().removeClass('layui-this');
                var subnav = parent.closest('.plant-subnav');
                if (subnav.length) {
                    openPlantSubnav(subnav);
                    subnav.siblings().removeClass('layui-this');
                } else {
                    parent.siblings('.plant-subnav').removeClass('layui-this is-open')
                        .find('dd').removeClass('layui-this');
                }
                parent.closest('.layui-nav-item').addClass('layui-nav-itemed').siblings().removeClass('layui-this');
                parent.closest('.layui-nav-item').siblings().find('dd').removeClass('layui-this');
                return;
            }
            parent.addClass('layui-this').siblings().removeClass('layui-this');
            parent.siblings().find('dd').removeClass('layui-this');
        });
    });

    (function () {
        // 武汉市中心坐标，使用 Open-Meteo（无需 API Key）
        var WUHAN = { lat: 30.5928, lng: 114.3055 };

        function weatherIconSvg(code) {
            // WMO Weather interpretation codes -> simple SVG
            if (code === 0) {
                return '<svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><circle cx="12" cy="12" r="4.2" fill="#f59e0b"/><g stroke="#f59e0b" stroke-width="1.8" stroke-linecap="round"><path d="M12 2.5v2.2M12 19.3v2.2M2.5 12h2.2M19.3 12h2.2M5.1 5.1l1.6 1.6M17.3 17.3l1.6 1.6M5.1 18.9l1.6-1.6M17.3 6.7l1.6-1.6"/></g></svg>';
            }
            if (code === 1 || code === 2) {
                return '<svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><circle cx="8.5" cy="9" r="3.2" fill="#f59e0b"/><path d="M8.2 14.8h8.1a3.4 3.4 0 0 0 .2-6.8 4.6 4.6 0 0 0-8.7 1.6 2.8 2.8 0 0 0 .4 5.2z" fill="#94a3b8"/></svg>';
            }
            if (code === 3) {
                return '<svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M7.5 16.5h9.2a3.7 3.7 0 0 0 .2-7.4 5.1 5.1 0 0 0-9.8 1.8 3.1 3.1 0 0 0 .4 5.6z" fill="#94a3b8"/></svg>';
            }
            if (code === 45 || code === 48) {
                return '<svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M4 9.5h16M5 12.5h14M6 15.5h12" stroke="#94a3b8" stroke-width="1.8" stroke-linecap="round"/></svg>';
            }
            if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) {
                return '<svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M7.2 11.2h8.6a3.2 3.2 0 0 0 .2-6.4 4.4 4.4 0 0 0-8.4 1.5 2.7 2.7 0 0 0-.4 4.9z" fill="#94a3b8"/><g stroke="#38bdf8" stroke-width="1.6" stroke-linecap="round"><path d="M9 14.2v3.2M12 14.8v3.2M15 14.2v3.2"/></g></svg>';
            }
            if ((code >= 71 && code <= 77) || (code >= 85 && code <= 86)) {
                return '<svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M7.2 11.2h8.6a3.2 3.2 0 0 0 .2-6.4 4.4 4.4 0 0 0-8.4 1.5 2.7 2.7 0 0 0-.4 4.9z" fill="#94a3b8"/><g fill="#7dd3fc"><circle cx="9" cy="15.5" r="1"/><circle cx="12" cy="16.5" r="1"/><circle cx="15" cy="15.5" r="1"/></g></svg>';
            }
            if (code >= 95) {
                return '<svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M7.2 10.5h8.6a3.2 3.2 0 0 0 .2-6.4 4.4 4.4 0 0 0-8.4 1.5 2.7 2.7 0 0 0-.4 4.9z" fill="#64748b"/><path d="M11.2 12.8l-2.1 3.6h2.1l-1.2 3.4 4.1-4.6h-2.1l1.3-2.4z" fill="#f59e0b"/></svg>';
            }
            return '<svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M7.5 16.5h9.2a3.7 3.7 0 0 0 .2-7.4 5.1 5.1 0 0 0-9.8 1.8 3.1 3.1 0 0 0 .4 5.6z" fill="#94a3b8"/></svg>';
        }

        function weatherLabel(code) {
            if (code === 0) return '晴';
            if (code === 1) return '大部晴朗';
            if (code === 2) return '多云';
            if (code === 3) return '阴';
            if (code === 45 || code === 48) return '雾';
            if (code >= 51 && code <= 57) return '毛毛雨';
            if (code >= 61 && code <= 67) return '雨';
            if (code >= 71 && code <= 77) return '雪';
            if (code >= 80 && code <= 82) return '阵雨';
            if (code >= 85 && code <= 86) return '阵雪';
            if (code >= 95) return '雷暴';
            return '天气';
        }

        function renderWeather(data) {
            var code = (data.daily && data.daily.weathercode && data.daily.weathercode[0]) || 3;
            var tmax = data.daily && data.daily.temperature_2m_max && data.daily.temperature_2m_max[0];
            var tmin = data.daily && data.daily.temperature_2m_min && data.daily.temperature_2m_min[0];
            if (tmax == null || tmin == null) {
                throw new Error('missing temperature');
            }
            $('#headerWeatherIcon').html(weatherIconSvg(code));
            $('#headerWeatherTemp').text(
                weatherLabel(code) + '  ' + Math.round(tmin) + '°C ~ ' + Math.round(tmax) + '°C'
            );
        }

        function loadWuhanWeather() {
            var url = 'https://api.open-meteo.com/v1/forecast'
                + '?latitude=' + WUHAN.lat
                + '&longitude=' + WUHAN.lng
                + '&daily=weathercode,temperature_2m_max,temperature_2m_min'
                + '&timezone=Asia%2FShanghai'
                + '&forecast_days=1';

            $.ajax({
                url: url,
                dataType: 'json',
                timeout: 8000,
                success: function (data) {
                    try {
                        renderWeather(data);
                    } catch (e) {
                        $('#headerWeatherTemp').text('天气暂不可用');
                    }
                },
                error: function () {
                    $('#headerWeatherIcon').html(weatherIconSvg(3));
                    $('#headerWeatherTemp').text('天气暂不可用');
                }
            });
        }

        loadWuhanWeather();
    })();
</script>
</html>
