<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>模型库</title>
    <script src="/static/js/jquery-3.4.1.min.js"></script>
    <script src="/static/layui/layui.js"></script>
    <link rel="stylesheet" href="/static/layui/css/layui.css">
    <style>
        body { margin: 0; padding: 16px; background: #f5f7fb; color: #1f2937; font-family: Arial, "Microsoft YaHei", sans-serif; }
        .page { max-width: 1280px; margin: 0 auto; }
        .head { display: flex; justify-content: space-between; gap: 12px; align-items: flex-start; margin-bottom: 14px; }
        .title { font-size: 22px; font-weight: 700; margin: 0; }
        .sub { margin-top: 6px; color: #64748b; font-size: 13px; }
        .actions { display: flex; gap: 8px; flex-wrap: wrap; }
        .btn { height: 34px; padding: 0 14px; border: 0; border-radius: 6px; background: #009688; color: #fff; cursor: pointer; }
        .btn.secondary { background: #334155; }
        .stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 14px; }
        .stat { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 14px 16px; box-shadow: 0 8px 20px rgba(15,23,42,.04); }
        .stat .k { color: #64748b; font-size: 12px; }
        .stat .v { margin-top: 6px; font-size: 22px; font-weight: 700; color: #0f766e; }
        .toolbar { display: flex; gap: 10px; align-items: center; margin-bottom: 12px; flex-wrap: wrap; }
        .toolbar input { height: 34px; border: 1px solid #d8dee8; border-radius: 6px; padding: 0 10px; min-width: 220px; }
        .grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
        .card { background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; padding: 16px; box-shadow: 0 10px 24px rgba(15,23,42,.05); display: flex; flex-direction: column; gap: 10px; }
        .card-top { display: flex; justify-content: space-between; gap: 8px; align-items: flex-start; }
        .name { font-size: 16px; font-weight: 700; }
        .meta { color: #64748b; font-size: 12px; line-height: 1.6; }
        .tags { display: flex; flex-wrap: wrap; gap: 6px; }
        .tag { font-size: 11px; padding: 3px 8px; border-radius: 999px; background: #ecfeff; color: #0f766e; }
        .tag.gray { background: #f1f5f9; color: #475569; }
        .tag.blue { background: #eff6ff; color: #1d4ed8; }
        .tag.orange { background: #fff7ed; color: #c2410c; }
        .metrics { display: flex; gap: 10px; flex-wrap: wrap; }
        .metric { background: #f8fafc; border-radius: 8px; padding: 8px 10px; min-width: 72px; }
        .metric b { display: block; font-size: 14px; color: #0f172a; }
        .metric span { font-size: 11px; color: #64748b; }
        .card-actions { display: flex; gap: 8px; margin-top: auto; flex-wrap: wrap; }
        .card-actions a, .card-actions button { height: 30px; line-height: 30px; padding: 0 10px; border-radius: 6px; border: 0; cursor: pointer; font-size: 12px; text-decoration: none; }
        .a-primary { background: #009688; color: #fff; }
        .a-secondary { background: #e2e8f0; color: #334155; }
        .a-danger { background: #fee2e2; color: #b91c1c; }
        .empty { padding: 40px; text-align: center; color: #64748b; background: #fff; border-radius: 12px; border: 1px dashed #cbd5e1; }
        .hint { color: #888; font-size: 12px; margin-top: 6px; }
        .folder-name { color: #16a34a; font-size: 12px; margin-top: 4px; }
        @media (max-width: 1000px) { .stats, .grid { grid-template-columns: 1fr 1fr; } }
        @media (max-width: 700px) { .stats, .grid { grid-template-columns: 1fr; } .head { flex-direction: column; } }
    </style>
</head>
<body>
<div class="page">
    <div class="head">
        <div>
            <h1 class="title">模型库</h1>
            <div class="sub">卡片浏览 · 版本链 · 训练血缘。点击「详情」查看文件结构、指标与来源数据集。</div>
        </div>
        <div class="actions">
            <button class="btn" id="btnAdd">添加模型</button>
            <button class="btn secondary" id="btnRefresh">刷新</button>
        </div>
    </div>
    <div class="stats">
        <div class="stat"><div class="k">模型总数</div><div class="v" id="statTotal">-</div></div>
        <div class="stat"><div class="k">可部署</div><div class="v" id="statDeployable">-</div></div>
        <div class="stat"><div class="k">版本族</div><div class="v" id="statFamily">-</div></div>
        <div class="stat"><div class="k">总存储</div><div class="v" id="statSize">-</div></div>
    </div>
    <div class="toolbar">
        <input id="keyword" placeholder="搜索模型名 / 归属人">
        <button class="btn secondary" id="btnSearch">搜索</button>
    </div>
    <div class="grid" id="cardGrid"></div>
    <div class="empty" id="emptyBox" style="display:none;">暂无模型，点击右上角添加，或等待系统初始化样例资产。</div>
</div>

<script type="text/html" id="model-insert">
    <form class="layui-form" style="padding: 16px 24px 0 0;">
        <div class="layui-form-item"><label class="layui-form-label">模型名称</label><div class="layui-input-block"><input id="m-name" type="text" class="layui-input" placeholder="例如：PigBehavior-CNN" required></div></div>
        <div class="layui-form-item"><label class="layui-form-label">归属人</label><div class="layui-input-block"><input id="m-owner" type="text" class="layui-input" placeholder="例如：张三" required></div></div>
        <div class="layui-form-item"><label class="layui-form-label">框架</label><div class="layui-input-block"><select id="m-framework"><option value="PyTorch">PyTorch</option><option value="TensorFlow">TensorFlow</option><option value="ONNX">ONNX</option><option value="Other">其他</option></select></div></div>
        <div class="layui-form-item"><label class="layui-form-label">版本</label><div class="layui-input-block"><input id="m-version" type="text" class="layui-input" placeholder="例如：v1.0"></div></div>
        <div class="layui-form-item"><label class="layui-form-label">分类</label><div class="layui-input-block"><input id="m-category" type="text" class="layui-input" placeholder="例如：行为识别"></div></div>
        <div class="layui-form-item layui-form-text"><label class="layui-form-label">描述</label><div class="layui-input-block"><textarea id="m-desc" class="layui-textarea"></textarea></div></div>
        <div class="layui-form-item"><label class="layui-form-label">本地文件夹</label><div class="layui-input-block"><input id="m-folder" type="file" webkitdirectory directory multiple><div class="hint">选择模型所在本地文件夹</div><div id="m-folder-name" class="folder-name"></div></div></div>
    </form>
</script>

<script>
layui.use(['layer', 'form'], function () {
    var layer = layui.layer;
    var form = layui.form;
    var models = [];

    function fmtMetric(v) {
        if (v == null) return '-';
        if (typeof v === 'number') return v <= 1 ? (v * 100).toFixed(1) + '%' : String(v);
        return String(v);
    }

    function renderStats(list) {
        var total = list.length, deployable = 0, size = 0, families = {};
        list.forEach(function (m) {
            if (m.status === 'deployable' || m.status === 'deployed') deployable++;
            size += m.totalSizeBytes || 0;
            if (m.familyKey) families[m.familyKey] = 1;
        });
        $('#statTotal').text(total);
        $('#statDeployable').text(deployable);
        $('#statFamily').text(Object.keys(families).length);
        $('#statSize').text(formatSize(size));
    }

    function formatSize(bytes) {
        if (!bytes) return '0 B';
        var u = ['B','KB','MB','GB'], i = 0, n = bytes;
        while (n >= 1024 && i < u.length - 1) { n /= 1024; i++; }
        return (i === 0 ? n : n.toFixed(2)) + ' ' + u[i];
    }

    function renderCards(list) {
        renderStats(list);
        if (!list.length) {
            $('#cardGrid').empty();
            $('#emptyBox').show();
            return;
        }
        $('#emptyBox').hide();
        var html = '';
        list.forEach(function (m) {
            var metrics = m.metrics || {};
            html += '<div class="card">'
                + '<div class="card-top"><div class="name">' + escapeHtml(m.name) + '</div><div class="tag">' + escapeHtml(m.version || 'v1') + '</div></div>'
                + '<div class="tags">'
                + '<span class="tag blue">' + escapeHtml(m.libraryTypeText || '') + '</span>'
                + '<span class="tag gray">' + escapeHtml(m.framework || '-') + '</span>'
                + '<span class="tag orange">' + escapeHtml(m.statusText || '-') + '</span>'
                + (m.category ? '<span class="tag">' + escapeHtml(m.category) + '</span>' : '')
                + '</div>'
                + '<div class="meta">归属 ' + escapeHtml(m.ownerName || '-') + ' · ' + escapeHtml(m.totalSizeText || '0 B')
                + (m.sourceDatasetName ? ' · 来源 ' + escapeHtml(m.sourceDatasetName) : '')
                + (m.trainingMethod ? ' · ' + escapeHtml(m.trainingMethod) : '')
                + '</div>'
                + '<div class="metrics">'
                + '<div class="metric"><b>' + fmtMetric(metrics.accuracy) + '</b><span>Accuracy</span></div>'
                + '<div class="metric"><b>' + fmtMetric(metrics.f1) + '</b><span>F1</span></div>'
                + '<div class="metric"><b>' + escapeHtml(m.fileCount == null ? '-' : String(m.fileCount)) + '</b><span>文件数</span></div>'
                + '</div>'
                + '<div class="card-actions">'
                + '<a class="a-primary" href="' + (m.detailUrl || ('/model_library/' + m.id)) + '">详情</a>'
                + '<a class="a-secondary" href="' + (m.downloadUrl || '#') + '" target="_blank">下载</a>'
                + '<button class="a-secondary js-edit" data-id="' + m.id + '">编辑</button>'
                + '<button class="a-danger js-del" data-id="' + m.id + '" data-name="' + escapeHtml(m.name) + '">删除</button>'
                + '</div></div>';
        });
        $('#cardGrid').html(html);
    }

    function escapeHtml(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function loadList() {
        $.getJSON('/api/models', { keyword: $.trim($('#keyword').val()) }).done(function (res) {
            models = res.data || [];
            renderCards(models);
        }).fail(function () {
            layer.msg('加载模型库失败', {icon: 2});
        });
    }

    function findModel(id) {
        for (var i = 0; i < models.length; i++) if (models[i].id == id) return models[i];
        return null;
    }

    $('#btnRefresh,#btnSearch').on('click', loadList);
    $('#keyword').on('keydown', function (e) { if (e.keyCode === 13) loadList(); });

    $('#cardGrid').on('click', '.js-del', function () {
        var id = $(this).data('id'), name = $(this).data('name');
        layer.confirm('确认删除模型「' + name + '」？', function (index) {
            $.ajax({ url: '/api/models/' + id, type: 'DELETE' }).done(function (res) {
                if (res.code === 200) { layer.msg('删除成功', {icon: 1}); loadList(); }
                else layer.msg(res.msg || '删除失败', {icon: 2});
            });
            layer.close(index);
        });
    });

    $('#cardGrid').on('click', '.js-edit', function () {
        var data = findModel($(this).data('id'));
        if (!data) return;
        layer.open({
            type: 1, title: '编辑模型信息', area: ['520px', '480px'], btn: ['保存', '取消'],
            content: '<form class="layui-form" style="padding:16px 24px 0 0;">'
                + '<div class="layui-form-item"><label class="layui-form-label">模型名称</label><div class="layui-input-block"><input id="e-name" class="layui-input"></div></div>'
                + '<div class="layui-form-item"><label class="layui-form-label">归属人</label><div class="layui-input-block"><input id="e-owner" class="layui-input"></div></div>'
                + '<div class="layui-form-item"><label class="layui-form-label">框架</label><div class="layui-input-block"><input id="e-framework" class="layui-input"></div></div>'
                + '<div class="layui-form-item"><label class="layui-form-label">版本</label><div class="layui-input-block"><input id="e-version" class="layui-input"></div></div>'
                + '<div class="layui-form-item"><label class="layui-form-label">分类</label><div class="layui-input-block"><input id="e-category" class="layui-input"></div></div>'
                + '<div class="layui-form-item layui-form-text"><label class="layui-form-label">描述</label><div class="layui-input-block"><textarea id="e-desc" class="layui-textarea"></textarea></div></div></form>',
            success: function () {
                $('#e-name').val(data.name || '');
                $('#e-owner').val(data.ownerName || '');
                $('#e-framework').val(data.framework || '');
                $('#e-version').val(data.version || '');
                $('#e-category').val(data.category || '');
                $('#e-desc').val(data.description || '');
            },
            yes: function (index) {
                $.ajax({
                    url: '/api/models/' + data.id, type: 'PUT', contentType: 'application/json',
                    data: JSON.stringify({
                        name: $.trim($('#e-name').val()), ownerName: $.trim($('#e-owner').val()),
                        framework: $.trim($('#e-framework').val()), version: $.trim($('#e-version').val()),
                        category: $.trim($('#e-category').val()), description: $.trim($('#e-desc').val())
                    })
                }).done(function (res) {
                    if (res.code === 200) { layer.msg('更新成功', {icon: 1}); layer.close(index); loadList(); }
                    else layer.msg(res.msg || '更新失败', {icon: 2});
                });
            }
        });
    });

    $('#btnAdd').on('click', function () {
        layer.open({
            type: 1, title: '添加模型', area: ['560px', '620px'], btn: ['确认添加', '取消'],
            content: $('#model-insert').html(),
            success: function () {
                form.render();
                $('#m-folder').on('change', function () {
                    var files = this.files;
                    if (!files || !files.length) { $('#m-folder-name').text(''); return; }
                    var first = files[0].webkitRelativePath || files[0].name;
                    var folder = first.indexOf('/') >= 0 ? first.split('/')[0] : first;
                    $('#m-folder-name').text('已选择文件夹：' + folder + '（共 ' + files.length + ' 个文件）');
                });
            },
            yes: function (index) {
                var name = $.trim($('#m-name').val()), owner = $.trim($('#m-owner').val());
                var files = document.getElementById('m-folder').files;
                if (!name) { layer.msg('请填写模型名称', {icon: 0}); return; }
                if (!owner) { layer.msg('请填写归属人', {icon: 0}); return; }
                if (!files || !files.length) { layer.msg('请选择本地模型文件夹', {icon: 0}); return; }
                var fd = new FormData();
                fd.append('name', name); fd.append('ownerName', owner);
                fd.append('framework', $('#m-framework').val());
                fd.append('version', $.trim($('#m-version').val()));
                fd.append('category', $.trim($('#m-category').val()));
                fd.append('description', $.trim($('#m-desc').val()));
                for (var i = 0; i < files.length; i++) fd.append('files', files[i]);
                var loadIdx = layer.load(1, {shade: 0.2});
                $.ajax({ url: '/api/models', type: 'POST', data: fd, processData: false, contentType: false, timeout: 600000 })
                    .done(function (res) {
                        if (res.code === 200) { layer.msg('添加成功', {icon: 1}); layer.close(index); loadList(); }
                        else layer.msg(res.msg || '添加失败', {icon: 2});
                    }).fail(function (xhr) {
                        layer.msg((xhr.responseJSON && xhr.responseJSON.msg) || '上传失败', {icon: 2});
                    }).always(function () { layer.close(loadIdx); });
            }
        });
    });

    loadList();
});
</script>
</body>
</html>
