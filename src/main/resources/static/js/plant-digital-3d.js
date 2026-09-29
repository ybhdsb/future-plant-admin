/**
 * 数字化植株 · CSS 3D 阵列
 * 不依赖 WebGL / Three.js，用 perspective + preserve-3d，兼容 iframe / 低配环境。
 */
(function (global) {
    'use strict';

    var container = null;
    var stage = null;
    var floor = null;
    var plantsLayer = null;
    var selectedCode = null;
    var specimensCache = [];
    var callbacks = {
        onSelect: null,
        onHover: null,
        onLeave: null
    };

    var rotX = 62;
    var rotZ = -38;
    var autoRotate = true;
    var dragging = false;
    var lastX = 0;
    var lastY = 0;
    var animId = 0;
    var ready = false;

    function n(v, d) {
        var x = Number(v);
        return isNaN(x) ? (d || 0) : x;
    }

    function clamp(v, a, b) {
        return Math.max(a, Math.min(b, v));
    }

    function stageColor(stage) {
        if (stage === 'R1' || stage === 'VT') return '#ca8a04';
        if (stage === 'V9') return '#16a34a';
        if (stage === 'V6') return '#15803d';
        return '#22c55e';
    }

    function cornSvg(p) {
        var latest = p.latest || {};
        var height = clamp(n(latest.height_cm, 40), 12, 120);
        var leaves = clamp(Math.round(n(latest.leaf_count, 7)), 3, 16);
        var stage = p.growthStage || 'V6';
        var stemH = 40 + height * 1.35;
        var scale = 200 / (stemH + 40);
        var cx = 70;
        var groundY = 210;
        var topY = groundY - stemH * scale;
        var color = stageColor(stage);
        var html = '';
        html += '<svg viewBox="0 0 140 230" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">';
        html += '<defs><linearGradient id="g' + (p.plantCode || '') + '" x1="0" y1="0" x2="0" y2="1">';
        html += '<stop offset="0%" stop-color="' + color + '" stop-opacity="0.95"/>';
        html += '<stop offset="100%" stop-color="' + color + '" stop-opacity="0.55"/>';
        html += '</linearGradient></defs>';
        html += '<ellipse cx="70" cy="218" rx="42" ry="8" fill="rgba(40,70,30,.22)"/>';
        html += '<path d="M' + cx + ' ' + groundY + ' L' + cx + ' ' + topY + '" stroke="#4d7c0f" stroke-width="5" stroke-linecap="round"/>';
        for (var i = 0; i < leaves; i++) {
            var t = (i + 1) / (leaves + 1);
            var y = groundY - stemH * scale * t;
            var side = i % 2 === 0 ? 1 : -1;
            var len = 28 + (1 - Math.abs(t - 0.55)) * 22;
            var tipX = cx + side * len;
            var tipY = y - 10 - t * 8;
            var ctrlX = cx + side * (len * 0.55);
            var ctrlY = y - 18;
            html += '<path d="M' + cx + ' ' + y + ' Q' + ctrlX + ' ' + ctrlY + ' ' + tipX + ' ' + tipY + '" fill="none" stroke="' + color + '" stroke-width="3.2" stroke-linecap="round" opacity="' + (0.55 + t * 0.4) + '"/>';
            html += '<path d="M' + cx + ' ' + y + ' Q' + ctrlX + ' ' + (ctrlY + 6) + ' ' + tipX + ' ' + (tipY + 4) + ' Q' + ctrlX + ' ' + (y + 2) + ' ' + cx + ' ' + y + '" fill="url(#g' + (p.plantCode || '') + ')" opacity="' + (0.35 + t * 0.35) + '"/>';
        }
        if (stage === 'VT' || stage === 'R1') {
            html += '<ellipse cx="' + cx + '" cy="' + (topY - 6) + '" rx="7" ry="12" fill="#eab308"/>';
            html += '<path d="M' + cx + ' ' + (topY - 18) + ' l3 -10 M' + cx + ' ' + (topY - 18) + ' l-3 -9 M' + cx + ' ' + (topY - 18) + ' l0 -12" stroke="#854d0e" stroke-width="1.5" stroke-linecap="round"/>';
        } else {
            html += '<circle cx="' + cx + '" cy="' + topY + '" r="3.5" fill="#166534"/>';
        }
        html += '</svg>';
        return html;
    }

    function applyStageTransform() {
        if (!stage) return;
        stage.style.transform = 'rotateX(' + rotX + 'deg) rotateZ(' + rotZ + 'deg)';
        // 植株始终朝向镜头（反向抵消舞台旋转）
        var plants = plantsLayer ? plantsLayer.querySelectorAll('.css3d-plant') : [];
        var billboard = 'translate(-50%, -100%) rotateZ(' + (-rotZ) + 'deg) rotateX(' + (-rotX) + 'deg)';
        for (var i = 0; i < plants.length; i++) {
            plants[i].style.transform = billboard;
        }
    }

    function buildDom() {
        container.innerHTML =
            '<div class="css3d-viewport">' +
            '  <div class="css3d-stage" id="css3dStage">' +
            '    <div class="css3d-floor" id="css3dFloor">' +
            '      <div class="css3d-grid"></div>' +
            '      <div class="css3d-rim"></div>' +
            '      <div class="css3d-plants" id="css3dPlants"></div>' +
            '    </div>' +
            '  </div>' +
            '</div>';
        stage = container.querySelector('#css3dStage');
        floor = container.querySelector('#css3dFloor');
        plantsLayer = container.querySelector('#css3dPlants');
    }

    function layoutPlants(specimens) {
        specimensCache = specimens || [];
        if (!plantsLayer || !floor) return;

        var maxRow = 1;
        var maxCol = 1;
        specimensCache.forEach(function (p) {
            maxRow = Math.max(maxRow, n(p.posRow, 1));
            maxCol = Math.max(maxCol, n(p.posCol, 1));
        });

        var cell = 118;
        var pad = 56;
        var w = pad * 2 + Math.max(1, maxCol) * cell;
        var h = pad * 2 + Math.max(1, maxRow) * cell;
        floor.style.width = w + 'px';
        floor.style.height = h + 'px';

        var html = '';
        specimensCache.forEach(function (p) {
            var col = n(p.posCol, 1);
            var row = n(p.posRow, 1);
            var x = pad + (col - 0.5) * cell;
            var y = pad + (row - 0.5) * cell;
            var active = selectedCode === p.plantCode ? ' active' : '';
            var hScale = 0.72 + clamp(n((p.latest || {}).height_cm, 40), 12, 120) / 180;
            html +=
                '<div class="css3d-plant' + active + '" data-code="' + p.plantCode +
                '" style="left:' + x + 'px;top:' + y + 'px;--plant-scale:' + hScale + '">' +
                '<div class="css3d-plant-shadow"></div>' +
                '<div class="css3d-plant-body">' + cornSvg(p) + '</div>' +
                '<div class="css3d-plant-tag">' + p.plantCode + ' · ' + (p.slotCode || '') + '</div>' +
                '<div class="css3d-plant-ring"></div>' +
                '</div>';
        });
        plantsLayer.innerHTML = html;
        applyStageTransform();
        bindPlantEvents();
    }

    function bindPlantEvents() {
        var nodes = plantsLayer.querySelectorAll('.css3d-plant');
        for (var i = 0; i < nodes.length; i++) {
            (function (el) {
                el.addEventListener('mouseenter', function (e) {
                    var code = el.getAttribute('data-code');
                    var p = specimensCache.filter(function (x) { return x.plantCode === code; })[0];
                    if (p && callbacks.onHover) callbacks.onHover(p, e.clientX, e.clientY);
                });
                el.addEventListener('mousemove', function (e) {
                    var code = el.getAttribute('data-code');
                    var p = specimensCache.filter(function (x) { return x.plantCode === code; })[0];
                    if (p && callbacks.onHover) callbacks.onHover(p, e.clientX, e.clientY);
                });
                el.addEventListener('mouseleave', function () {
                    if (callbacks.onLeave) callbacks.onLeave();
                });
                el.addEventListener('click', function (e) {
                    e.stopPropagation();
                    var code = el.getAttribute('data-code');
                    var p = specimensCache.filter(function (x) { return x.plantCode === code; })[0];
                    selectedCode = code;
                    updateActiveClass();
                    if (p && callbacks.onSelect) callbacks.onSelect(p);
                });
            })(nodes[i]);
        }
    }

    function updateActiveClass() {
        var nodes = plantsLayer ? plantsLayer.querySelectorAll('.css3d-plant') : [];
        for (var i = 0; i < nodes.length; i++) {
            var el = nodes[i];
            if (el.getAttribute('data-code') === selectedCode) el.classList.add('active');
            else el.classList.remove('active');
        }
    }

    function onPointerDown(e) {
        if (e.target.closest && e.target.closest('.css3d-plant')) return;
        dragging = true;
        autoRotate = false;
        lastX = e.clientX;
        lastY = e.clientY;
        container.classList.add('is-dragging');
        try { container.setPointerCapture(e.pointerId); } catch (err) {}
    }

    function onPointerMove(e) {
        if (!dragging) return;
        var dx = e.clientX - lastX;
        var dy = e.clientY - lastY;
        lastX = e.clientX;
        lastY = e.clientY;
        rotZ += dx * 0.35;
        rotX = clamp(rotX + dy * 0.18, 42, 78);
        applyStageTransform();
    }

    function onPointerUp(e) {
        dragging = false;
        container.classList.remove('is-dragging');
        try { container.releasePointerCapture(e.pointerId); } catch (err) {}
    }

    function onWheel(e) {
        e.preventDefault();
        var floorEl = floor;
        if (!floorEl) return;
        var cur = parseFloat(floorEl.style.getPropertyValue('--zoom') || '1');
        var next = clamp(cur + (e.deltaY > 0 ? -0.06 : 0.06), 0.7, 1.45);
        floorEl.style.setProperty('--zoom', String(next));
        floorEl.style.transform = 'translateZ(0) scale(var(--zoom, 1))';
    }

    function tick() {
        animId = requestAnimationFrame(tick);
        if (autoRotate && !dragging) {
            rotZ += 0.12;
            applyStageTransform();
        }
    }

    function injectStyles() {
        if (document.getElementById('css3dPlantStyles')) return;
        var css = document.createElement('style');
        css.id = 'css3dPlantStyles';
        css.textContent = [
            '.css3d-viewport{position:absolute;inset:0;perspective:1100px;perspective-origin:50% 42%;overflow:hidden;cursor:grab;}',
            '.css3d-viewport.is-dragging,.is-dragging .css3d-viewport{cursor:grabbing;}',
            '.css3d-stage{position:absolute;left:50%;top:54%;transform-style:preserve-3d;transform-origin:center center;will-change:transform;}',
            '.css3d-floor{position:relative;margin-left:-50%;margin-top:-50%;transform-style:preserve-3d;transform:translateZ(0) scale(var(--zoom,1));',
            'background:radial-gradient(ellipse at center,#8fba68 0%,#6f9a4e 55%,#5a8340 100%);',
            'border-radius:28px;box-shadow:0 30px 60px rgba(20,50,30,.28), inset 0 0 0 2px rgba(255,255,255,.12);}',
            '.css3d-grid{position:absolute;inset:12px;border-radius:20px;pointer-events:none;',
            'background-image:linear-gradient(rgba(255,255,255,.16) 1px,transparent 1px),linear-gradient(90deg,rgba(255,255,255,.16) 1px,transparent 1px);',
            'background-size:118px 118px;background-position:56px 56px;opacity:.55;}',
            '.css3d-rim{position:absolute;inset:-6px;border-radius:32px;pointer-events:none;',
            'box-shadow:inset 0 0 0 6px rgba(55,80,35,.35),0 0 0 1px rgba(255,255,255,.1);}',
            '.css3d-plants{position:absolute;inset:0;transform-style:preserve-3d;}',
            '.css3d-plant{position:absolute;left:0;top:0;width:120px;transform-style:preserve-3d;transform-origin:50% 100%;',
            'cursor:pointer;z-index:2;filter:drop-shadow(0 14px 12px rgba(20,50,25,.22));transition:filter .18s ease;}',
            '.css3d-plant:hover,.css3d-plant.active{filter:drop-shadow(0 0 0 #009688) drop-shadow(0 16px 18px rgba(0,150,136,.35));z-index:5;}',
            '.css3d-plant-body{transform:scale(var(--plant-scale,1));transform-origin:50% 100%;}',
            '.css3d-plant-body svg{width:120px;height:190px;display:block;pointer-events:none;}',
            '.css3d-plant-tag{position:absolute;left:50%;bottom:-2px;transform:translateX(-50%);',
            'padding:2px 8px;border-radius:999px;background:rgba(255,255,255,.92);border:1px solid #cfe8e1;',
            'font-size:10px;font-weight:700;color:#0f766e;white-space:nowrap;pointer-events:none;',
            'box-shadow:0 4px 10px rgba(15,55,45,.12);}',
            '.css3d-plant-ring{display:none;position:absolute;left:50%;bottom:8px;width:58px;height:16px;margin-left:-29px;',
            'border:2px solid #009688;border-radius:50%;box-shadow:0 0 12px rgba(0,150,136,.55);pointer-events:none;}',
            '.css3d-plant.active .css3d-plant-ring{display:block;animation:css3dPulse 1.4s ease-in-out infinite;}',
            '.css3d-plant-shadow{position:absolute;left:50%;bottom:10px;width:54px;height:14px;margin-left:-27px;',
            'background:rgba(30,50,20,.22);border-radius:50%;filter:blur(2px);pointer-events:none;}',
            '@keyframes css3dPulse{0%,100%{transform:scale(1);opacity:.9}50%{transform:scale(1.12);opacity:1}}'
        ].join('');
        document.head.appendChild(css);
    }

    function init(el, opts) {
        container = el;
        callbacks = Object.assign({
            onSelect: null,
            onHover: null,
            onLeave: null
        }, opts || {});

        injectStyles();
        buildDom();
        applyStageTransform();

        container.addEventListener('pointerdown', onPointerDown);
        container.addEventListener('pointermove', onPointerMove);
        container.addEventListener('pointerup', onPointerUp);
        container.addEventListener('pointercancel', onPointerUp);
        container.addEventListener('wheel', onWheel, { passive: false });

        ready = true;
        cancelAnimationFrame(animId);
        tick();
        return true;
    }

    function render(specimens, selected) {
        if (!ready) return;
        selectedCode = selected || selectedCode;
        layoutPlants(specimens || []);
    }

    function setSelected(code) {
        selectedCode = code;
        updateActiveClass();
    }

    function resumeAutoRotate() {
        autoRotate = true;
    }

    function isReady() {
        return ready;
    }

    global.PlantField3D = {
        init: init,
        render: render,
        setSelected: setSelected,
        resumeAutoRotate: resumeAutoRotate,
        isReady: isReady
    };
})(window);
