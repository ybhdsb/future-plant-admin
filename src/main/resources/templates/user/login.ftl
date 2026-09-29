<#--<!DOCTYPE html>-->
<#--<html>-->
<#--<head>-->
<#--    <title>登录</title>-->
<#--    <script src="/static/js/jquery-3.4.1.min.js"></script>-->
<#--    <script src="/static/layui/layui.js"></script>-->
<#--    <link rel="stylesheet" href="/static/layui/css/layui.css">-->
<#--</head>-->

<#--<style>-->
<#--    body {-->
<#--        background-image: url("/static/images/main-back.png");-->
<#--        background-repeat: no-repeat;-->
<#--        background-size: 100% 150%;-->
<#--        background-attachment: fixed;-->
<#--    }-->
<#--    .main-div {-->
<#--    }-->
<#--</style>-->

<#--<script>-->
<#--    doLogin = function() {-->
<#--        layui.use('layer', function () {-->
<#--            var username = $("#username").val();-->
<#--            var password = $("#password").val();-->
<#--            if ($("#username").val().trim().length <= 0) {-->
<#--                layer.tips('帐号不能为空', '#username', {-->
<#--                    tipsMore: true-->
<#--                });-->
<#--            }-->
<#--            if ($("#password").val().trim().length <= 0) {-->
<#--                layer.tips('密码不能为空', '#password', {-->
<#--                    tipsMore: true-->
<#--                });-->
<#--            }-->
<#--            if ($("#username").val().trim().length <= 0 || $("#password").val().trim().length <= 0) {-->
<#--                return false;-->
<#--            }-->
<#--            $.ajax({-->
<#--                url: '/auths/login',-->
<#--                method: 'post',-->
<#--                data: {-->
<#--                    username: username,-->
<#--                    password: password-->
<#--                },-->
<#--                success: function (res) {-->
<#--                    console.log(res);-->
<#--                    if (res.code == 200) {-->
<#--                        parent.window.location.href = "/index";-->
<#--                    } else {-->
<#--                        parent.window.location.href = "/login";-->
<#--                    }-->
<#--                    //parent.window.location.href = "/";-->
<#--                }-->
<#--            });-->
<#--        });-->

<#--    }-->
<#--    $(document).ready(function () {-->
<#--        $("#reset").click(function () {-->
<#--            $("input[name='username']").val("");-->
<#--            $("input[name='password']").val("");-->
<#--        });-->
<#--        $("#login").click(function () {-->
<#--            doLogin();-->
<#--        });-->
<#--        $(document).keyup(function (event) {-->
<#--            if (event.keyCode == 13) {-->
<#--                doLogin();-->
<#--            }-->
<#--        })-->
<#--    });-->

<#--</script>-->

<#--<body>-->
<#--<div class="main-div">-->
<#--    <div class="layui-layout layui-layout-admin" style="height: 100%">-->
<#--&lt;#&ndash;        <div class="layui-header" style="background-color: #2066CE">&ndash;&gt;-->
<#--&lt;#&ndash;            <div style="padding-left: 5px; width: 600px; height: 60px">&ndash;&gt;-->
<#--&lt;#&ndash;                <!-- 在这里替换具体的LOGO和标语 &ndash;&gt;&ndash;&gt;-->
<#--&lt;#&ndash;                <img width="120px" src="static/images/logos/pig.png" style="float: left; margin-top: 5px">&ndash;&gt;-->
<#--&lt;#&ndash;                <!-- color:#009688&ndash;&gt;&ndash;&gt;-->
<#--&lt;#&ndash;                <div style="color: #E6E6E6; margin-left:20px;float:left;width:350px;height:100%;line-height:60px;text-align:left;font-size:16px;">智慧生猪协同与决策系统</div>&ndash;&gt;-->
<#--&lt;#&ndash;            </div>&ndash;&gt;-->

<#--&lt;#&ndash;            <ul class="layui-nav layui-layout-right" style="background-color: #2066CE">&ndash;&gt;-->
<#--&lt;#&ndash;                <li class="layui-nav-item">&ndash;&gt;-->
<#--&lt;#&ndash;                    <a id="name-a" href="javascript:;">&ndash;&gt;-->
<#--&lt;#&ndash;                        <img src="static/images/icons/diandian-icon.png" class="layui-nav-img">&ndash;&gt;-->
<#--&lt;#&ndash;                    </a>&ndash;&gt;-->
<#--&lt;#&ndash;                    <dl class="layui-nav-child">&ndash;&gt;-->
<#--&lt;#&ndash;                        <dd><a href="">基本资料</a></dd>&ndash;&gt;-->
<#--&lt;#&ndash;                        <dd><a href="">更改密码</a></dd>&ndash;&gt;-->
<#--&lt;#&ndash;                    </dl>&ndash;&gt;-->
<#--&lt;#&ndash;                </li>&ndash;&gt;-->
<#--&lt;#&ndash;&lt;#&ndash;                <li class="layui-nav-item"><a href="http://diandian2.cn">点点OJ</a></li>&ndash;&gt;&ndash;&gt;-->
<#--&lt;#&ndash;            </ul>&ndash;&gt;-->
<#--&lt;#&ndash;        </div>&ndash;&gt;-->
<#--    </div>-->
<#--    <div class="header">-->
<#--        <h1 class="site-title">智慧生猪养殖协同与决策平台</h1>-->
<#--    </div>-->
<#--    <div style="opacity: 0.9; position: absolute; background: #FFFFFF;border-radius: 10px;left: 50%; width: 450px; height: 300px; margin-left: -250px; margin-top: 100px; padding-left: 40px; padding-top: 50px;box-shadow: 0 0 10px 3px #a9a2a0">-->
<#--        <div style="text-align: center; font-size: 20px; margin-right: 50px; margin-bottom: 30px">登录</div>-->
<#--        <div class="layui-form">-->
<#--            <div class="layui-form-item" style="font-size: 16px; opacity: 1">-->
<#--                <label class="layui-form-label" style="width: 100px; margin-bottom: 10px">帐号</label>-->
<#--                <div class="layui-input-inline" style="width: 200px">-->
<#--                    <input type="text" id="username" required  lay-verify="required" placeholder="请输入帐号" autocomplete="off" class="layui-input">-->
<#--                </div>-->
<#--            </div>-->

<#--            <div class="layui-form-item" style="font-size: 16px; margin-bottom: 30px; opacity: 1">-->
<#--                <label class="layui-form-label" style="width: 100px">密码</label>-->
<#--                <div class="layui-input-inline" style="width: 200px">-->
<#--                    <input type="password" id="password" required lay-verify="required" placeholder="请输入密码" autocomplete="off" class="layui-input">-->
<#--                </div>-->
<#--            </div>-->
<#--            <div class="layui-form-item" style="font-size: 16px; margin-left: 5px; margin-top: 10px">-->
<#--                <label class="layui-form-label" style="width: 100px"></label>-->
<#--                <div class="layui-input-inline" style="width: 200px">-->
<#--                    <button type="submit" class="layui-btn" style="background-color: #0084ff; outline: none" id="login" lay-submit="">登录</button>-->
<#--                    <button type="button" class="layui-btn" style="background-color: #0084ff; outline: none" id="reset">重置</button>-->
<#--                </div>-->
<#--            </div>-->
<#--        </div>-->
<#--    </div>-->

<#--</div>-->
<#--<div style="color: black;font-size: 18px; position: fixed; bottom: 20px; right: 20px" class="layui-footer">-->

<#--</div>-->


<#--</body>-->
<#--</html>-->
<!DOCTYPE html>
<html>
<head>
    <title>登录</title>
    <script src="/static/js/jquery-3.4.1.min.js"></script>
    <script src="/static/layui/layui.js"></script>
    <link rel="stylesheet" href="/static/layui/css/layui.css">
</head>

<style>
    html,
    body {
        width: 100%;
        height: 100%;
        margin: 0;
    }

    body {
        background-image: url("/static/images/main-back.png");
        background-repeat: no-repeat;
        background-size: cover;
        background-position: center center;
        background-attachment: fixed;
        font-family: "Microsoft YaHei", Arial, sans-serif;
        overflow: hidden;
    }

    body::before {
        content: "";
        position: fixed;
        inset: 0;
        background: linear-gradient(90deg, rgba(5, 48, 36, 0.18), rgba(24, 107, 72, 0.05) 45%, rgba(232, 247, 208, 0.14));
        pointer-events: none;
    }

    .main-div {
        position: relative;
        z-index: 1;
        min-height: 100%;
        padding-top: 46px;
        box-sizing: border-box;
    }

    .header {
        text-align: center;
        margin-bottom: 90px;
    }

    /*.site-title {*/
    /*    margin: 0;*/
    /*    font-size: 48px;*/
    /*    font-weight: bold;*/
    /*    line-height: 1.2;*/
    /*    color: rgba(255, 255, 255, 0.92);*/
    /*    text-shadow: 0 3px 14px rgba(0, 52, 31, 0.45);*/
    /*}*/
    .site-title {
        position: relative;
        display: inline-block;
        margin: 0;

        font-size: 48px;
        font-weight: bold;
        line-height: 1.2;
        letter-spacing: 2px;

        color: rgba(255, 255, 255, 0.72);
        -webkit-text-stroke: 1px rgba(255, 255, 255, 0.88);

        background: linear-gradient(
                120deg,
                rgba(255, 255, 255, 0.95),
                rgba(220, 255, 235, 0.45),
                rgba(255, 255, 255, 0.82)
        );
        -webkit-background-clip: text;
        background-clip: text;
        -webkit-text-fill-color: transparent;

        text-shadow:
                0 2px 6px rgba(0, 60, 35, 0.35),
                0 0 18px rgba(255, 255, 255, 0.38);
    }

    .site-title::before {
        content: attr(data-text);
        position: absolute;
        inset: 0;
        z-index: -1;

        color: rgba(255, 255, 255, 0.35);
        -webkit-text-stroke: 2px rgba(255, 255, 255, 0.38);
        filter: blur(3px);

        text-shadow:
                0 0 12px rgba(255, 255, 255, 0.45),
                0 0 28px rgba(190, 255, 210, 0.28);
    }



    .login-card {
        width: 440px;
        /*margin-left: 54%;*/
        margin: 0 auto;
        padding: 46px 54px 44px;
        border: 1px solid rgba(255, 255, 255, 0.7);
        border-radius: 18px;
        background: linear-gradient(145deg, rgba(255, 255, 255, 0.55), rgba(240, 250, 240, 0.38));
        box-shadow: 0 22px 56px rgba(4, 42, 24, 0.30), inset 0 1px 0 rgba(255, 255, 255, 0.74);
        box-sizing: border-box;
        backdrop-filter: blur(12px);
        -webkit-backdrop-filter: blur(12px);
    }

    .login-title {
        margin-bottom: 34px;
        text-align: center;
        color: #12372a;
        font-size: 26px;
        font-weight: 600;
    }

    .login-card .layui-form-item {
        display: flex;
        align-items: center;
        margin-bottom: 24px;
    }

    .login-card .layui-form-label {
        width: 64px;
        padding: 9px 18px 9px 0;
        color: #143d2f;
        font-size: 18px;
        font-weight: 600;
        text-align: right;
        box-sizing: border-box;
    }

    .login-card .layui-input-inline {
        flex: 1;
        width: auto;
    }

    .login-card .layui-input {
        height: 46px;
        border-color: rgba(20, 82, 55, 0.18);
        border-radius: 8px;
        background: rgba(255, 255, 255, 0.62);
        color: #16392d;
        font-size: 17px;
        box-shadow: inset 0 1px 3px rgba(16, 64, 42, 0.08);
    }

    .login-card .layui-input:focus {
        border-color: #29a36f !important;
        background: rgba(255, 255, 255, 0.82);
        box-shadow: 0 0 0 3px rgba(41, 163, 111, 0.16);
    }

    .login-actions {
        display: flex;
        gap: 14px;
        margin: 34px 0 0 82px;
    }

    .login-actions .layui-btn {
        min-width: 112px;
        height: 44px;
        border: none;
        border-radius: 8px;
        font-size: 17px;
        outline: none;
    }

    .login-actions .btn-primary {
        background: linear-gradient(135deg, #188d63, #1fb38b);
        box-shadow: 0 8px 18px rgba(14, 103, 70, 0.28);
    }

    .login-actions .btn-secondary {
        background: rgba(255, 255, 255, 0.42);
        color: #14523c;
        border: 1px solid rgba(20, 82, 60, 0.2);
    }

    @media (max-width: 900px) {
        .main-div {
            padding: 34px 20px;
        }

        .header {
            margin-bottom: 56px;
        }

        .site-title {
            font-size: 34px;
        }

        .login-card {
            width: min(100%, 430px);
            margin: 0 auto;
        }
    }

    @media (max-width: 520px) {
        .site-title {
            font-size: 28px;
        }

        .login-card {
            padding: 34px 24px;
        }

        .login-card .layui-form-item {
            display: block;
        }

        .login-card .layui-form-label {
            width: auto;
            display: block;
            padding: 0 0 8px;
            text-align: left;
        }

        .login-actions {
            margin-left: 0;
        }

        .login-actions .layui-btn {
            flex: 1;
            min-width: 0;
        }
    }
</style>

<script>
    doLogin = function() {
        layui.use('layer', function () {
            var username = $("#username").val();
            var password = $("#password").val();
            if ($("#username").val().trim().length <= 0) {
                layer.tips('帐号不能为空', '#username', {
                    tipsMore: true
                });
            }
            if ($("#password").val().trim().length <= 0) {
                layer.tips('密码不能为空', '#password', {
                    tipsMore: true
                });
            }
            if ($("#username").val().trim().length <= 0 || $("#password").val().trim().length <= 0) {
                return false;
            }
            $.ajax({
                url: '/auths/login',
                method: 'post',
                data: {
                    username: username,
                    password: password
                },
                success: function (res) {
                    console.log(res);
                    if (res.code == 200) {
                        parent.window.location.href = "/index";
                    } else {
                        parent.window.location.href = "/login";
                    }
                }
            });
        });
    }

    $(document).ready(function () {
        $("#reset").click(function () {
            $("input[name='username']").val("");
            $("input[name='password']").val("");
        });
        $("#login").click(function () {
            doLogin();
        });
        $(document).keyup(function (event) {
            if (event.keyCode == 13) {
                doLogin();
            }
        });
    });
</script>

<body>
<div class="main-div">
    <div class="layui-layout layui-layout-admin" style="height: 100%"></div>
    <div class="header">
<#--        <h1 class="site-title">智慧生猪养殖协同与决策平台</h1>-->
        <h1 class="site-title" data-text="未来植物原型系统">未来植物原型系统</h1>
    </div>
    <div class="login-card">
        <div class="login-title">登录</div>
        <div class="layui-form">
            <div class="layui-form-item">
                <label class="layui-form-label">帐号</label>
                <div class="layui-input-inline">
                    <input type="text" id="username" name="username" required lay-verify="required" placeholder="请输入帐号" autocomplete="off" class="layui-input">
                </div>
            </div>

            <div class="layui-form-item">
                <label class="layui-form-label">密码</label>
                <div class="layui-input-inline">
                    <input type="password" id="password" name="password" required lay-verify="required" placeholder="请输入密码" autocomplete="off" class="layui-input">
                </div>
            </div>
            <div class="login-actions">
                <button type="submit" class="layui-btn btn-primary" id="login" lay-submit="">登录</button>
                <button type="button" class="layui-btn btn-secondary" id="reset">重置</button>
            </div>
        </div>
    </div>
</div>
<div style="color: black;font-size: 18px; position: fixed; bottom: 20px; right: 20px" class="layui-footer"></div>
</body>
</html>
