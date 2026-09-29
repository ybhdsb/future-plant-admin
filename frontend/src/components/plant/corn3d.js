/**
 * Canvas 伪 3D 玉米：叶片 + 圆柱茎 + 立体花盆 + 雄穗/顶芽
 */

    function clamp(v, a, b) { return Math.max(a, Math.min(b, v)); }
    function n(v, d) { var x = Number(v); return isNaN(x) ? (d || 0) : x; }
    function lerp(a, b, t) { return a + (b - a) * t; }

    function hexToRgb(hex) {
        var h = (hex || '#15803d').replace('#', '');
        if (h.length === 3) h = h[0] + h[0] + h[1] + h[1] + h[2] + h[2];
        return {
            r: parseInt(h.slice(0, 2), 16),
            g: parseInt(h.slice(2, 4), 16),
            b: parseInt(h.slice(4, 6), 16)
        };
    }

    function shade(rgb, k) {
        return 'rgb(' +
            clamp(Math.round(rgb.r * k), 0, 255) + ',' +
            clamp(Math.round(rgb.g * k), 0, 255) + ',' +
            clamp(Math.round(rgb.b * k), 0, 255) + ')';
    }

    function rgba(rgb, a, k) {
        k = k == null ? 1 : k;
        return 'rgba(' +
            clamp(Math.round(rgb.r * k), 0, 255) + ',' +
            clamp(Math.round(rgb.g * k), 0, 255) + ',' +
            clamp(Math.round(rgb.b * k), 0, 255) + ',' + a + ')';
    }

    function stageColor(stage) {
        if (stage === 'R1' || stage === 'VT') return '#ca8a04';
        if (stage === 'V9') return '#16a34a';
        if (stage === 'V6') return '#15803d';
        return '#22c55e';
    }

    function rotY(p, a) {
        var c = Math.cos(a), s = Math.sin(a);
        return { x: p.x * c - p.z * s, y: p.y, z: p.x * s + p.z * c };
    }

    function project(p, cx, cy, scale) {
        var f = 230;
        var z = p.z + 170;
        var s = f / z;
        return { x: cx + p.x * s * scale, y: cy - p.y * s * scale, s: s, z: p.z };
    }

    function buildModel(p) {
        var latest = p.latest || {};
        var heightCm = clamp(n(latest.height_cm, 40), 12, 120);
        var leafCount = clamp(Math.round(n(latest.leaf_count, 7)), 3, 16);
        var stemMm = clamp(n(latest.stem_diameter_mm, 12), 6, 28);
        var stage = p.growthStage || 'V6';
        var leafRgb = hexToRgb(stageColor(stage));
        var stemH = 42 + heightCm * 0.95;
        var stemR = 2.4 + stemMm * 0.13;

        var leaves = [];
        var nodes = [];
        for (var i = 0; i < leafCount; i++) {
            var t = (i + 1) / (leafCount + 1);
            var y = 10 + stemH * t;
            var side = i % 2 === 0 ? 1 : -1;
            var yaw = side * (0.55 + (i % 5) * 0.22) + i * 0.31;
            var len = 26 + (1 - Math.abs(t - 0.5)) * 22 + heightCm * 0.08;
            leaves.push({
                y: y,
                yaw: yaw,
                len: len,
                lift: 0.35 + t * 0.55,
                droop: 0.15 + t * 0.35,
                width: 7 + (1 - t) * 4,
                t: t
            });
            nodes.push(y);
        }

        return {
            stemH: stemH,
            stemR: stemR,
            leafRgb: leafRgb,
            stemRgb: { r: 62, g: 110, b: 28 },
            potRgb: { r: 145, g: 96, b: 58 },
            leaves: leaves,
            nodes: nodes,
            stage: stage,
            hasTassel: stage === 'VT' || stage === 'R1',
            hasEar: stage === 'R1'
        };
    }

    function leafRibbon(leaf, angle) {
        var pts = [];
        var segs = 10;
        for (var i = 0; i <= segs; i++) {
            var u = i / segs;
            var local = {
                x: leaf.len * u,
                y: Math.sin(u * Math.PI) * leaf.lift * leaf.len * 0.22 - u * u * leaf.droop * leaf.len * 0.35,
                z: Math.sin(u * Math.PI) * leaf.width * 0.15
            };
            var spun = rotY(local, leaf.yaw);
            spun.y += leaf.y;
            pts.push(rotY(spun, angle));
        }
        return pts;
    }

    function leafQuad(pts, width) {
        var left = [], right = [];
        for (var i = 0; i < pts.length; i++) {
            var prev = pts[Math.max(0, i - 1)];
            var next = pts[Math.min(pts.length - 1, i + 1)];
            var tx = next.x - prev.x;
            var tz = next.z - prev.z;
            var len = Math.sqrt(tx * tx + tz * tz) || 1;
            var bx = -tz / len;
            var bz = tx / len;
            var w = width * (1 - Math.pow((i / (pts.length - 1)) * 0.92, 1.2));
            w *= 0.35 + 0.65 * Math.sin((i / (pts.length - 1)) * Math.PI);
            left.push({ x: pts[i].x + bx * w, y: pts[i].y, z: pts[i].z + bz * w });
            right.push({ x: pts[i].x - bx * w, y: pts[i].y, z: pts[i].z - bz * w });
        }
        return { left: left, right: right, midZ: pts[Math.floor(pts.length / 2)].z };
    }

    /** 圆柱侧面采样：返回左右轮廓 + 中线，用于连续茎秆 */
    function stemSilhouette(model, angle, segs) {
        var left = [], right = [], mid = [], rings = [];
        for (var i = 0; i <= segs; i++) {
            var t = i / segs;
            var y = t * model.stemH;
            var r = model.stemR * (1.18 - t * 0.38);
            // 取面向相机的左右切点（世界 X 轴方向在旋转后）
            var L = rotY({ x: -r, y: y, z: 0 }, angle);
            var R = rotY({ x: r, y: y, z: 0 }, angle);
            var M = rotY({ x: 0, y: y, z: 0 }, angle);
            // 高光侧：朝向光源的圆周点
            var hi = rotY({ x: r * 0.35, y: y, z: r * 0.75 }, angle);
            left.push(L); right.push(R); mid.push(M);
            rings.push({ y: y, r: r, t: t, mid: M, hi: hi, L: L, R: R });
        }
        return { left: left, right: right, mid: mid, rings: rings };
    }

    function drawSoftShadow(ctx, cx, cy, model) {
        var g = ctx.createRadialGradient(cx, cy + 6, 4, cx, cy + 6, 48);
        g.addColorStop(0, 'rgba(25,45,20,0.28)');
        g.addColorStop(0.45, 'rgba(25,45,20,0.12)');
        g.addColorStop(1, 'rgba(25,45,20,0)');
        ctx.fillStyle = g;
        ctx.beginPath();
        ctx.ellipse(cx, cy + 6, 42 + model.stemR, 12, 0, 0, Math.PI * 2);
        ctx.fill();
    }

    function drawPot(ctx, angle, cx, cy, scale, model) {
        var potH = 16;
        var topR = 22;
        var botR = 16;
        var topY = 2;
        var botY = -potH + 2;

        function ring(y, r) {
            var pts = [];
            for (var i = 0; i <= 24; i++) {
                var a = (i / 24) * Math.PI * 2;
                pts.push(project(rotY({ x: Math.cos(a) * r, y: y, z: Math.sin(a) * r }, angle), cx, cy, scale));
            }
            return pts;
        }

        var top = ring(topY, topR);
        var bot = ring(botY, botR);

        // 盆壁：分后壁 / 前壁，带陶土渐变
        function drawWall(i0, i1, dark) {
            ctx.beginPath();
            ctx.moveTo(top[i0].x, top[i0].y);
            for (var i = i0; i <= i1; i++) ctx.lineTo(top[i].x, top[i].y);
            for (var j = i1; j >= i0; j--) ctx.lineTo(bot[j].x, bot[j].y);
            ctx.closePath();
            var gx0 = top[i0].x, gy0 = top[i0].y;
            var gx1 = top[i1].x, gy1 = top[i1].y;
            var grad = ctx.createLinearGradient(gx0, gy0, gx1, gy1);
            if (dark) {
                grad.addColorStop(0, shade(model.potRgb, 0.55));
                grad.addColorStop(0.5, shade(model.potRgb, 0.72));
                grad.addColorStop(1, shade(model.potRgb, 0.48));
            } else {
                grad.addColorStop(0, shade(model.potRgb, 0.7));
                grad.addColorStop(0.35, shade(model.potRgb, 1.05));
                grad.addColorStop(0.7, shade(model.potRgb, 0.9));
                grad.addColorStop(1, shade(model.potRgb, 0.6));
            }
            ctx.fillStyle = grad;
            ctx.fill();
        }

        // 后壁 → 底 → 前壁
        drawWall(12, 24, true);
        // 底部椭圆
        ctx.beginPath();
        ctx.moveTo(bot[0].x, bot[0].y);
        for (var b = 1; b < bot.length; b++) ctx.lineTo(bot[b].x, bot[b].y);
        ctx.closePath();
        ctx.fillStyle = shade(model.potRgb, 0.42);
        ctx.fill();

        drawWall(0, 12, false);

        // 盆口外沿
        ctx.beginPath();
        ctx.moveTo(top[0].x, top[0].y);
        for (var t = 1; t < top.length; t++) ctx.lineTo(top[t].x, top[t].y);
        ctx.closePath();
        var rimGrad = ctx.createLinearGradient(top[18].x, top[18].y, top[6].x, top[6].y);
        rimGrad.addColorStop(0, shade(model.potRgb, 0.65));
        rimGrad.addColorStop(0.5, shade(model.potRgb, 1.12));
        rimGrad.addColorStop(1, shade(model.potRgb, 0.7));
        ctx.fillStyle = rimGrad;
        ctx.fill();

        // 土壤面（略低于盆口）
        var soil = ring(topY - 2.5, topR * 0.86);
        ctx.beginPath();
        ctx.moveTo(soil[0].x, soil[0].y);
        for (var s = 1; s < soil.length; s++) ctx.lineTo(soil[s].x, soil[s].y);
        ctx.closePath();
        var soilGrad = ctx.createRadialGradient(cx, cy - 8, 2, cx, cy - 4, 26);
        soilGrad.addColorStop(0, '#5a3d28');
        soilGrad.addColorStop(0.6, '#3d2918');
        soilGrad.addColorStop(1, '#2a1b10');
        ctx.fillStyle = soilGrad;
        ctx.fill();

        // 土壤颗粒
        ctx.fillStyle = 'rgba(90,70,40,0.45)';
        for (var g = 0; g < 8; g++) {
            var ga = (g / 8) * Math.PI * 2 + angle * 0.3;
            var gr = 6 + (g % 3) * 3;
            var gp = project(rotY({ x: Math.cos(ga) * gr, y: topY - 2.2, z: Math.sin(ga) * gr }, angle), cx, cy, scale);
            ctx.beginPath();
            ctx.arc(gp.x, gp.y, 1.1 + (g % 2) * 0.5, 0, Math.PI * 2);
            ctx.fill();
        }

        // 盆口内圈高光
        ctx.beginPath();
        ctx.moveTo(top[0].x, top[0].y);
        for (var u = 1; u < top.length; u++) ctx.lineTo(top[u].x, top[u].y);
        ctx.strokeStyle = 'rgba(255,230,190,0.28)';
        ctx.lineWidth = 1.2;
        ctx.stroke();

        return { topY: topY, soilY: topY - 2.5 };
    }

    function drawStem(ctx, model, angle, cx, cy, scale) {
        var sil = stemSilhouette(model, angle, 22);
        var leftP = sil.left.map(function (p) { return project(p, cx, cy, scale); });
        var rightP = sil.right.map(function (p) { return project(p, cx, cy, scale); });
        var midP = sil.mid.map(function (p) { return project(p, cx, cy, scale); });

        // 主体轮廓
        ctx.beginPath();
        ctx.moveTo(leftP[0].x, leftP[0].y);
        for (var i = 1; i < leftP.length; i++) ctx.lineTo(leftP[i].x, leftP[i].y);
        for (var j = rightP.length - 1; j >= 0; j--) ctx.lineTo(rightP[j].x, rightP[j].y);
        ctx.closePath();

        var x0 = Math.min(leftP[0].x, rightP[0].x) - 4;
        var x1 = Math.max(leftP[0].x, rightP[0].x) + 4;
        var bodyGrad = ctx.createLinearGradient(x0, 0, x1, 0);
        // 圆柱光照：暗 → 亮 → 暗
        var lightSide = Math.cos(angle - 0.4);
        if (lightSide >= 0) {
            bodyGrad.addColorStop(0, shade(model.stemRgb, 0.45));
            bodyGrad.addColorStop(0.28, shade(model.stemRgb, 0.75));
            bodyGrad.addColorStop(0.52, shade(model.stemRgb, 1.18));
            bodyGrad.addColorStop(0.78, shade(model.stemRgb, 0.85));
            bodyGrad.addColorStop(1, shade(model.stemRgb, 0.5));
        } else {
            bodyGrad.addColorStop(0, shade(model.stemRgb, 0.5));
            bodyGrad.addColorStop(0.22, shade(model.stemRgb, 0.85));
            bodyGrad.addColorStop(0.48, shade(model.stemRgb, 1.18));
            bodyGrad.addColorStop(0.72, shade(model.stemRgb, 0.75));
            bodyGrad.addColorStop(1, shade(model.stemRgb, 0.45));
        }
        ctx.fillStyle = bodyGrad;
        ctx.fill();

        // 高光条
        ctx.beginPath();
        var hiOff = lightSide >= 0 ? 0.35 : 0.65;
        for (var h = 0; h < midP.length; h++) {
            var lx = lerp(leftP[h].x, rightP[h].x, hiOff);
            var ly = midP[h].y;
            if (h === 0) ctx.moveTo(lx, ly);
            else ctx.lineTo(lx, ly);
        }
        ctx.strokeStyle = 'rgba(220,255,170,0.28)';
        ctx.lineWidth = 1.6;
        ctx.lineCap = 'round';
        ctx.stroke();

        // 节间环（玉米茎特征）
        for (var ni = 0; ni < model.nodes.length; ni++) {
            var ny = model.nodes[ni];
            var nt = ny / model.stemH;
            var idx = Math.round(nt * (sil.rings.length - 1));
            var ring = sil.rings[idx];
            var rL = project(ring.L, cx, cy, scale);
            var rR = project(ring.R, cx, cy, scale);
            var rM = project(ring.mid, cx, cy, scale);
            ctx.beginPath();
            ctx.ellipse(
                rM.x, rM.y,
                Math.abs(rR.x - rL.x) * 0.52,
                Math.max(1.2, ring.r * 0.35 * rM.s * scale),
                0, 0, Math.PI * 2
            );
            ctx.strokeStyle = rgba(model.stemRgb, 0.55, 0.55);
            ctx.lineWidth = 1.3;
            ctx.stroke();
            // 叶鞘微鼓
            ctx.beginPath();
            ctx.ellipse(rM.x, rM.y + 1.5, Math.abs(rR.x - rL.x) * 0.58, 2.2, 0, 0, Math.PI * 2);
            ctx.fillStyle = rgba(model.stemRgb, 0.25, 0.9);
            ctx.fill();
        }

        // 茎顶收口
        var top = midP[midP.length - 1];
        var topR = Math.abs(rightP[rightP.length - 1].x - leftP[leftP.length - 1].x) * 0.45;
        ctx.beginPath();
        ctx.ellipse(top.x, top.y, topR, topR * 0.55, 0, 0, Math.PI * 2);
        ctx.fillStyle = shade(model.stemRgb, 0.7);
        ctx.fill();
    }

    function drawTipBud(ctx, model, angle, cx, cy, scale) {
        // 顶芽：几片卷曲嫩叶簇
        var baseY = model.stemH;
        var buds = [
            { yaw: 0.2, len: 9, lift: 0.9 },
            { yaw: 2.2, len: 8, lift: 0.85 },
            { yaw: 4.0, len: 7.5, lift: 0.8 },
            { yaw: 1.2, len: 6, lift: 1.0 }
        ];
        var parts = [];
        for (var i = 0; i < buds.length; i++) {
            var b = buds[i];
            var pts = [];
            for (var s = 0; s <= 6; s++) {
                var u = s / 6;
                var local = {
                    x: b.len * u * 0.7,
                    y: baseY + u * b.len * b.lift,
                    z: Math.sin(u * Math.PI) * 1.5
                };
                var spun = rotY(local, b.yaw + i * 0.15);
                pts.push(rotY(spun, angle));
            }
            parts.push({ pts: pts, z: pts[3].z, i: i });
        }
        parts.sort(function (a, b) { return a.z - b.z; });
        var young = { r: 90, g: 160, b: 55 };
        for (var k = 0; k < parts.length; k++) {
            var pts2 = parts[k].pts;
            ctx.beginPath();
            var p0 = project(pts2[0], cx, cy, scale);
            ctx.moveTo(p0.x, p0.y);
            for (var p = 1; p < pts2.length; p++) {
                var pp = project(pts2[p], cx, cy, scale);
                ctx.lineTo(pp.x, pp.y);
            }
            // 回描形成窄叶
            for (var q = pts2.length - 1; q >= 0; q--) {
                var back = rotY({
                    x: pts2[q].x * 0.15,
                    y: pts2[q].y,
                    z: pts2[q].z
                }, 0);
                // 简化：向中轴收拢
                var mid = project({
                    x: pts2[0].x * (1 - q / pts2.length) + pts2[q].x * 0.15,
                    y: pts2[q].y,
                    z: pts2[0].z * (1 - q / pts2.length) + pts2[q].z * 0.15
                }, cx, cy, scale);
                ctx.lineTo(mid.x, mid.y);
            }
            ctx.closePath();
            ctx.fillStyle = shade(young, 0.75 + parts[k].i * 0.08);
            ctx.globalAlpha = 0.9;
            ctx.fill();
            ctx.globalAlpha = 1;
        }
        // 中心生长点
        var tip = project(rotY({ x: 0, y: baseY + 8, z: 0 }, angle), cx, cy, scale);
        var tg = ctx.createRadialGradient(tip.x - 1, tip.y - 1, 0.5, tip.x, tip.y, 5);
        tg.addColorStop(0, '#b6f0a0');
        tg.addColorStop(0.5, '#4ade80');
        tg.addColorStop(1, '#166534');
        ctx.beginPath();
        ctx.fillStyle = tg;
        ctx.arc(tip.x, tip.y, 3.8 * tip.s * scale, 0, Math.PI * 2);
        ctx.fill();
    }

    function drawTassel(ctx, model, angle, cx, cy, scale) {
        var baseY = model.stemH;
        var branches = [];
        // 中央主穗轴
        branches.push({ yaw: 0, pitch: 1.15, len: 18, thick: 1.4, main: true });
        for (var i = 0; i < 7; i++) {
            var yaw = (i / 7) * Math.PI * 2;
            branches.push({
                yaw: yaw,
                pitch: 0.55 + (i % 3) * 0.12,
                len: 11 + (i % 3) * 2,
                thick: 0.9,
                main: false
            });
        }
        // 再加一圈短枝
        for (var j = 0; j < 5; j++) {
            branches.push({
                yaw: (j / 5) * Math.PI * 2 + 0.4,
                pitch: 0.35,
                len: 7,
                thick: 0.7,
                main: false
            });
        }

        var drawn = [];
        for (var b = 0; b < branches.length; b++) {
            var br = branches[b];
            var pts = [];
            for (var s = 0; s <= 8; s++) {
                var u = s / 8;
                var local = {
                    x: Math.sin(br.pitch) * br.len * u,
                    y: baseY + 2 + Math.cos(br.pitch * 0.35) * br.len * u,
                    z: 0
                };
                var spun = rotY(local, br.yaw);
                pts.push(rotY(spun, angle));
            }
            drawn.push({ pts: pts, z: pts[4].z, br: br });
        }
        drawn.sort(function (a, b) { return a.z - b.z; });

        var tasselRgb = { r: 196, g: 150, b: 40 };
        for (var k = 0; k < drawn.length; k++) {
            var d = drawn[k];
            ctx.beginPath();
            var p0 = project(d.pts[0], cx, cy, scale);
            ctx.moveTo(p0.x, p0.y);
            for (var p = 1; p < d.pts.length; p++) {
                var pp = project(d.pts[p], cx, cy, scale);
                ctx.lineTo(pp.x, pp.y);
            }
            ctx.strokeStyle = shade(tasselRgb, d.br.main ? 0.85 : 0.7);
            ctx.lineWidth = d.br.thick * (d.br.main ? 2.1 : 1.4);
            ctx.lineCap = 'round';
            ctx.stroke();

            // 小穗粒
            for (var g = 2; g < d.pts.length; g += 1) {
                var gp = project(d.pts[g], cx, cy, scale);
                ctx.beginPath();
                ctx.fillStyle = shade(
                    g % 2 ? { r: 234, g: 179, b: 8 } : { r: 161, g: 98, b: 7 },
                    0.9
                );
                ctx.arc(gp.x, gp.y, (d.br.main ? 1.5 : 1.1) * gp.s * scale, 0, Math.PI * 2);
                ctx.fill();
            }
        }

        // R1：茎中部侧生果穗
        if (model.hasEar) {
            var earYaw = 1.1;
            var earY = model.stemH * 0.55;
            var earPts = [];
            for (var e = 0; e <= 8; e++) {
                var eu = e / 8;
                var elocal = {
                    x: 4 + eu * 14,
                    y: earY + Math.sin(eu * Math.PI) * 3,
                    z: 0
                };
                earPts.push(rotY(rotY(elocal, earYaw), angle));
            }
            // 苞叶
            ctx.beginPath();
            var e0 = project(earPts[0], cx, cy, scale);
            ctx.moveTo(e0.x, e0.y);
            for (var ei = 1; ei < earPts.length; ei++) {
                var ep = project(earPts[ei], cx, cy, scale);
                ctx.lineTo(ep.x, ep.y - 5 * (1 - ei / earPts.length));
            }
            for (var ej = earPts.length - 1; ej >= 0; ej--) {
                var eq = project(earPts[ej], cx, cy, scale);
                ctx.lineTo(eq.x, eq.y + 5 * (1 - ej / earPts.length));
            }
            ctx.closePath();
            var earGrad = ctx.createLinearGradient(e0.x, e0.y - 10, e0.x, e0.y + 10);
            earGrad.addColorStop(0, '#4d7c0f');
            earGrad.addColorStop(0.5, '#a3e635');
            earGrad.addColorStop(1, '#3f6212');
            ctx.fillStyle = earGrad;
            ctx.fill();
            // 花丝
            var tipE = project(earPts[earPts.length - 1], cx, cy, scale);
            ctx.strokeStyle = 'rgba(232, 180, 90, 0.85)';
            ctx.lineWidth = 1;
            for (var si = -2; si <= 2; si++) {
                ctx.beginPath();
                ctx.moveTo(tipE.x, tipE.y);
                ctx.quadraticCurveTo(tipE.x + si * 4, tipE.y + 6, tipE.x + si * 5, tipE.y + 14);
                ctx.stroke();
            }
        }
    }

    function drawLeafPart(ctx, part, model, cx, cy, scale) {
        var q = part.quad;
        ctx.beginPath();
        var pFirst = project(q.left[0], cx, cy, scale);
        ctx.moveTo(pFirst.x, pFirst.y);
        for (var a = 1; a < q.left.length; a++) {
            var pa = project(q.left[a], cx, cy, scale);
            ctx.lineTo(pa.x, pa.y);
        }
        for (var b = q.right.length - 1; b >= 0; b--) {
            var pb = project(q.right[b], cx, cy, scale);
            ctx.lineTo(pb.x, pb.y);
        }
        ctx.closePath();
        var grad = ctx.createLinearGradient(pFirst.x, pFirst.y - 20, pFirst.x, pFirst.y + 40);
        grad.addColorStop(0, shade(model.leafRgb, part.lit * 1.15));
        grad.addColorStop(1, shade(model.leafRgb, part.lit * 0.75));
        ctx.fillStyle = grad;
        ctx.globalAlpha = 0.78 + part.t * 0.2;
        ctx.fill();
        ctx.globalAlpha = 1;
        ctx.beginPath();
        var mid0 = project({
            x: (q.left[0].x + q.right[0].x) / 2,
            y: (q.left[0].y + q.right[0].y) / 2,
            z: (q.left[0].z + q.right[0].z) / 2
        }, cx, cy, scale);
        ctx.moveTo(mid0.x, mid0.y);
        for (var m = 1; m < q.left.length; m++) {
            var pm = project({
                x: (q.left[m].x + q.right[m].x) / 2,
                y: (q.left[m].y + q.right[m].y) / 2,
                z: (q.left[m].z + q.right[m].z) / 2
            }, cx, cy, scale);
            ctx.lineTo(pm.x, pm.y);
        }
        ctx.strokeStyle = shade(model.leafRgb, part.lit * 0.55);
        ctx.lineWidth = 1;
        ctx.globalAlpha = 0.55;
        ctx.stroke();
        ctx.globalAlpha = 1;
    }

    function drawPlant(ctx, model, angle, sway, w, h) {
        var cx = w * 0.5;
        var cy = h * 0.86;
        var scale = 1.12;
        var light = { x: 0.45, y: 0.35, z: 0.82 };

        drawSoftShadow(ctx, cx, cy, model);
        drawPot(ctx, angle, cx, cy, scale, model);

        // 叶片与顶部分层：先画背后的叶，再茎，再前面的叶与穗
        var backLeaves = [];
        var frontLeaves = [];
        for (var i = 0; i < model.leaves.length; i++) {
            var leaf = model.leaves[i];
            var leafAngle = angle + sway * (0.6 + leaf.t);
            var ribbon = leafRibbon(leaf, leafAngle);
            var quad = leafQuad(ribbon, leaf.width);
            var L = quad.left[Math.floor(quad.left.length / 2)];
            var R = quad.right[Math.floor(quad.right.length / 2)];
            var nx = L.x - R.x, ny = L.y - R.y, nz = L.z - R.z;
            var nl = Math.sqrt(nx * nx + ny * ny + nz * nz) || 1;
            nx /= nl; ny /= nl; nz /= nl;
            var ndot = Math.abs(nx * light.x + ny * light.y + nz * light.z);
            var part = { quad: quad, lit: 0.45 + ndot * 0.7, t: leaf.t, z: quad.midZ };
            if (quad.midZ < 0) backLeaves.push(part);
            else frontLeaves.push(part);
        }
        backLeaves.sort(function (a, b) { return a.z - b.z; });
        frontLeaves.sort(function (a, b) { return a.z - b.z; });

        for (var bi = 0; bi < backLeaves.length; bi++) drawLeafPart(ctx, backLeaves[bi], model, cx, cy, scale);
        drawStem(ctx, model, angle, cx, cy, scale);
        for (var fi = 0; fi < frontLeaves.length; fi++) drawLeafPart(ctx, frontLeaves[fi], model, cx, cy, scale);

        if (model.hasTassel) drawTassel(ctx, model, angle, cx, cy, scale);
        else drawTipBud(ctx, model, angle, cx, cy, scale);
    }

    function PlantView(canvas, specimen) {
        this.canvas = canvas;
        this.ctx = canvas.getContext('2d');
        this.specimen = specimen;
        this.model = buildModel(specimen);
        this.angle = -0.35;
        this.sway = 0;
        this._dragging = false;
        this._lastX = 0;
        this._moved = false;
        this._raf = 0;
        this._alive = true;
        this.onSelect = null;
        this._bind();
        this._resize();
        this._loop();
    }

    PlantView.prototype._bind = function () {
        var self = this;
        var el = this.canvas;
        el.style.cursor = 'grab';
        el.addEventListener('pointerdown', function (e) {
            if (e.button != null && e.button !== 0) return;
            self._dragging = true;
            self._moved = false;
            self._lastX = e.clientX;
            el.setPointerCapture && el.setPointerCapture(e.pointerId);
            el.style.cursor = 'grabbing';
            e.preventDefault();
        });
        el.addEventListener('pointermove', function (e) {
            if (!self._dragging) return;
            var dx = e.clientX - self._lastX;
            self._lastX = e.clientX;
            if (Math.abs(dx) > 2) self._moved = true;
            self.angle += dx * 0.012;
        });
        function up() {
            if (!self._dragging) return;
            self._dragging = false;
            el.style.cursor = 'grab';
            if (!self._moved && typeof self.onSelect === 'function') {
                self.onSelect(self.specimen);
            }
        }
        el.addEventListener('pointerup', up);
        el.addEventListener('pointercancel', up);
    };

    PlantView.prototype._resize = function () {
        var el = this.canvas;
        var w = el.clientWidth || 140;
        var h = el.clientHeight || 220;
        var dpr = Math.min(window.devicePixelRatio || 1, 2);
        el.width = Math.round(w * dpr);
        el.height = Math.round(h * dpr);
        this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        this._cssW = w;
        this._cssH = h;
    };

    PlantView.prototype._loop = function () {
        var self = this;
        function frame(t) {
            if (!self._alive) return;
            self._raf = requestAnimationFrame(frame);
            if (!self._dragging) self.sway = Math.sin(t * 0.0018) * 0.045;
            self.render();
        }
        this._raf = requestAnimationFrame(frame);
    };

    PlantView.prototype.render = function () {
        var ctx = this.ctx;
        var w = this._cssW;
        var h = this._cssH;
        ctx.clearRect(0, 0, w, h);
        drawPlant(ctx, this.model, this.angle + this.sway, this.sway, w, h);
    };

    PlantView.prototype.setSpecimen = function (specimen) {
        this.specimen = specimen;
        this.model = buildModel(specimen);
    };

    PlantView.prototype.destroy = function () {
        this._alive = false;
        cancelAnimationFrame(this._raf);
    };

export const CornPlant3D = {
  create: function (canvas, specimen) {
    return new PlantView(canvas, specimen);
  }
};
