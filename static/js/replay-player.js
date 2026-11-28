/*
 * Replay player for osu!standard, used on the analyzer and on score pages.
 * The markup comes from templates/freemarker/replayplayer.ftl. The server answers
 * <base>?replay=1 (objects, slider paths, cursor frames) and <base>?media=audio|bg.
 *
 *   const player = shiinaReplayPlayer(rootElement);
 *   player.load().then(() => player.play());
 *   player.seek(songTimeMs);
 */
window.shiinaReplayPlayer = function(root) {
  const q = (name) => root.querySelector('[data-rp="' + name + '"]');
  const base = root.dataset.base;
  const body = root;
  const canvas = q('canvas');
  const ctx = canvas.getContext('2d');
  const playBtn = q('play');
  const seek = q('seek');
  const timeLabel = q('time');
  const speedSel = q('speed');
  const trailBox = q('trail');
  const box300 = q('show300');
  const hsBox = q('hitsounds');
  const fpBox = q('followpoints');
  const hdBox = q('hd');
  const flBox = q('fl');
  const skinSel = q('maniaskin');
  const skinWrap = root.querySelector('[data-rp-wrap="maniaskin"]');
  const volume = q('volume');
  const marks = q('marks');
  const audioNote = q('noaudio');
  const errorNote = q('error');
  // staff tools (only in the analyzer for the staff)
  const acInfo = q('ac-info');
  const acFt = q('ac-ft');
  const cmpId = q('ac-cmp-id'), cmpGo = q('ac-cmp-go'), cmpInfo = q('ac-cmp-info'), cmpErr = q('ac-cmp-err');
  const staff = !!acInfo;
  let cmp = null; // second replay to compare: {frames, n}
  try { const v = localStorage.getItem('analyzer-volume'); if (v !== null) volume.value = v; } catch (e) {}
  // remembered switches (per browser)
  [[hsBox, 'analyzer-hitsounds'], [fpBox, 'analyzer-followpoints']].forEach(([box, key]) => {
    try { const v = localStorage.getItem(key); if (v !== null) box.checked = v === '1'; } catch (e) {}
    box.addEventListener('change', () => { try { localStorage.setItem(key, box.checked ? '1' : '0'); } catch (e) {} if (data) draw(); });
  });
  try { const v = localStorage.getItem('analyzer-mania-skin'); if (v === 'bars' || v === 'circles') skinSel.value = v; } catch (e) {}
  skinSel.addEventListener('change', () => { try { localStorage.setItem('analyzer-mania-skin', skinSel.value); } catch (e) {} if (data) draw(); });
  [hdBox, flBox].forEach(box => box.addEventListener('change', () => { if (data) draw(); }));
  const hdOn = () => data.hidden && hdBox.checked;
  let audio = null, bg = null;
  const COMBO = ['#ff6b8b', '#5ec8ff', '#8ce08a', '#ffc85e'];
  const VERDICT = { '300': ['300', '#66ccff'], '100': ['100', '#88dd55'], '50': ['50', '#ffbb44'], 'miss': ['×', '#ff4444'], 'slider_miss': ['×', '#f472b6'] };
  let data = null, playing = false, now = 0, last = 0, start = 0, end = 0, pendingSeek = null, loading = null;

  // An <audio> element that can play src; when it fails, tries the fallback address.
  function audioElement(src, fallback) {
    return new Promise((resolve) => {
      const a = new Audio();
      a.preload = 'auto';
      a.addEventListener('canplay', () => resolve(a), { once: true });
      a.addEventListener('error', () => resolve(fallback ? audioElement(fallback) : null), { once: true });
      a.src = src;
    });
  }

  // Browsers seek in an MP3 by guessing the position from the bitrate, which for many files
  // lands tens or hundreds of ms away from the real spot (more the further into the song), so
  // the music drifts away from the notes after a seek. An MP3 is decoded once and played as
  // plain PCM (WAV), where seeking is exact. Other formats are played as they are.
  function loadMusic(url) {
    return fetch(url, { credentials: 'same-origin' })
      .then(r => {
        if (!r.ok) throw new Error();
        const type = r.headers.get('content-type') || '';
        return r.arrayBuffer().then(buf => ({ buf, type }));
      })
      .then(({ buf, type }) => {
        const original = URL.createObjectURL(new Blob([buf], { type: type || 'audio/mpeg' }));
        const OAC = window.OfflineAudioContext || window.webkitOfflineAudioContext;
        if (!type.includes('mpeg') || !OAC) return audioElement(original, url);
        let ctx;
        try { ctx = new OAC(2, 1, 44100); } catch (e) { return audioElement(original, url); }
        return ctx.decodeAudioData(buf)
          .then(decoded => audioElement(URL.createObjectURL(wavBlob(decoded)), original))
          .catch(() => audioElement(original, url));
      })
      .catch(() => audioElement(url));
  }

  // 16-bit WAV from decoded audio (mono for long songs, to keep the memory use down)
  function wavBlob(d) {
    const ch = d.duration > 360 ? 1 : Math.min(2, d.numberOfChannels);
    const n = d.length, rate = d.sampleRate;
    const buf = new ArrayBuffer(44 + n * ch * 2);
    const v = new DataView(buf);
    const str = (o, t) => { for (let i = 0; i < t.length; i++) v.setUint8(o + i, t.charCodeAt(i)); };
    str(0, 'RIFF'); v.setUint32(4, 36 + n * ch * 2, true); str(8, 'WAVE');
    str(12, 'fmt '); v.setUint32(16, 16, true); v.setUint16(20, 1, true); v.setUint16(22, ch, true);
    v.setUint32(24, rate, true); v.setUint32(28, rate * ch * 2, true); v.setUint16(32, ch * 2, true); v.setUint16(34, 16, true);
    str(36, 'data'); v.setUint32(40, n * ch * 2, true);
    const chans = [];
    for (let c = 0; c < d.numberOfChannels; c++) chans.push(d.getChannelData(c));
    const out = new Int16Array(buf, 44);
    for (let i = 0; i < n; i++) {
      for (let c = 0; c < ch; c++) {
        let x = ch === 1 && chans.length > 1 ? (chans[0][i] + chans[1][i]) / 2 : chans[c][i];
        x = Math.max(-1, Math.min(1, x));
        out[i * ch + c] = x < 0 ? x * 0x8000 : x * 0x7fff;
      }
    }
    return new Blob([buf], { type: 'audio/wav' });
  }

  function load() {
    if (data) return Promise.resolve(true);
    if (loading) return loading;
    // music and background are optional: the player works without them
    const music = loadMusic(base + '?media=audio');
    loading = Promise.all([
      fetch(base + '?replay=1', { credentials: 'same-origin' }).then(r => { if (!r.ok) throw new Error(); return r.json(); }),
      music
    ])
      .then(([d, a]) => {
        data = d;
        if (a) {
          audio = a;
          audio.volume = parseFloat(volume.value);
          if ('preservesPitch' in audio) audio.preservesPitch = !data.nightcore;
        } else if (audioNote) {
          audioNote.style.display = '';
        }
        const img = new Image();
        img.onload = () => { bg = img; draw(); };
        img.src = base + '?media=bg';
        prepare();
        body.style.display = '';
        resize();
        drawMarks();
        now = pendingSeek !== null ? pendingSeek : start;
        pendingSeek = null;
        draw();
        return true;
      })
      .catch(() => {
        if (errorNote) { body.style.display = ''; errorNote.style.display = ''; }
        loading = null;
        return false;
      });
    return loading;
  }

  function prepare() {
    data.objects.forEach(o => {
      if (data.mode !== 0 || o.k !== 's') return;
      // path as points plus cumulative length, to place the slider ball
      const pts = [];
      for (let i = 0; i < o.p.length; i += 2) pts.push([o.p[i], o.p[i + 1]]);
      const cum = [0];
      for (let i = 1; i < pts.length; i++) cum.push(cum[i - 1] + Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]));
      o.pts = pts; o.cum = cum;
    });
    const f = data.frames;
    // frames: time, x, y, keys (osu!) or time, keys / pressed columns (taiko, mania)
    data.stride = data.mode === 0 ? 4 : data.mode === 2 ? 3 : 2;
    data.n = f.length / data.stride;
    let last = 0;
    data.objects.forEach(o => { last = Math.max(last, o.e !== undefined ? o.e : o.t); });
    start = data.objects.length ? data.objects[0].t - 1500 : 0;
    end = data.objects.length ? last + 1500 : (data.n ? f[(data.n - 1) * data.stride] : 0);
    seek.min = start; seek.max = end;
    canvas.parentElement.style.aspectRatio = data.mode === 1 ? '16 / 7' : '4 / 3';
    if (data.mode !== 0) {
      // cursor trail and 300s are osu!-only switches (catch shows its own trail of the catcher)
      if (data.mode !== 2) trailBox.closest('.form-check').style.display = 'none';
      box300.closest('.form-check').style.display = 'none';
    }
    const sw = (box, show) => { box.closest('.form-check').style.display = show ? '' : 'none'; };
    sw(fpBox, data.mode === 0);
    sw(hdBox, !!data.hidden);
    if (data.mode === 3) skinWrap.style.setProperty('display', 'flex', 'important');
    sw(flBox, !!data.flashlight);
    prepareFollowPoints();
    prepareCombo();
    prepareSounds();
  }

  // where an object ends (a slider ends at the tail or, after an even number of repeats, at the head)
  function endPos(o) {
    if (o.k === 's' && o.pts && o.pts.length) return along(o, o.r % 2 === 1 ? o.cum[o.cum.length - 1] : 0);
    return [o.x, o.y];
  }

  // follow points between objects of the same combo, like lazer: every 32 px, from 48 px on
  function prepareFollowPoints() {
    data.fp = [];
    if (data.mode !== 0) return;
    const objs = data.objects;
    for (let i = 1; i < objs.length; i++) {
      const a = objs[i - 1], b = objs[i];
      if (a.k === 'p' || b.k === 'p' || b.n === 1) continue;
      const p = endPos(a);
      const dx = b.x - p[0], dy = b.y - p[1], dist = Math.hypot(dx, dy);
      if (dist < 80) continue;
      data.fp.push({ x: p[0], y: p[1], dx: dx / dist, dy: dy / dist, dist, t0: a.k === 's' ? a.e : a.t, t1: b.t, ang: Math.atan2(dy, dx) });
    }
  }

  function drawFollowPoints() {
    const PRE = 800, FADE = 400, size = 5 * scale;
    ctx.fillStyle = '#ffffff';
    for (const c of data.fp) {
      if (now < c.t0 - PRE || now > c.t1 + FADE) continue;
      for (let d = 48; d < c.dist - 32; d += 32) {
        const f = d / c.dist;
        const tp = c.t0 + f * (c.t1 - c.t0); // the point fades out at this moment
        const fin = tp - PRE;
        if (now < fin || now > tp + FADE) continue;
        const a = now < tp ? Math.min(1, (now - fin) / FADE) : 1 - (now - tp) / FADE;
        ctx.globalAlpha = a * 0.7;
        ctx.save();
        ctx.translate(X(c.x + c.dx * d), Y(c.y + c.dy * d));
        ctx.rotate(c.ang);
        ctx.beginPath(); ctx.moveTo(size, 0); ctx.lineTo(-size * 0.6, -size * 0.7); ctx.lineTo(-size * 0.6, size * 0.7); ctx.closePath(); ctx.fill();
        ctx.restore();
      }
    }
    ctx.globalAlpha = 1;
  }

  // combo over time, from the verdicts (flashlight size, mania hidden); a slider counts its head and end
  function prepareCombo() {
    const ev = [];
    let c = 0;
    const MISS = { miss: 1, wrong: 1, slider_miss: 1 };
    if (data.mode === 1 || data.mode === 3) {
      for (const o of data.objects) if (o.v) ev.push([o.t, MISS[o.v] ? (c = 0) : ++c]);
    } else if (data.mode === 2) {
      for (const o of data.objects) if (o.k === 'f' || o.k === 'd') ev.push([o.t, o.v === 'miss' ? (c = 0) : ++c]);
    } else if (data.mode === 0) {
      for (const o of data.objects) {
        if (o.k === 'p') { ev.push([o.e, ++c]); continue; }
        if (o.k === 's') {
          ev.push([o.t, o.v === 'slider' ? ++c : (c = 0)]);
          ev.push([o.e, o.sr === 'miss' || o.sr === '50' ? (c = 0) : ++c]);
        } else {
          ev.push([o.t, o.v === 'miss' ? (c = 0) : ++c]);
        }
      }
    }
    ev.sort((a, b) => a[0] - b[0]);
    data.comboEv = ev;
  }

  function comboAt(t) {
    const ev = data.comboEv;
    let lo = 0, hi = ev.length - 1, c = 0;
    while (lo <= hi) { const m = (lo + hi) >> 1; if (ev[m][0] <= t) { c = ev[m][1]; lo = m + 1; } else hi = m - 1; }
    return c;
  }

  // Flashlight: only a circle around the cursor is visible; smaller at 100 and 200 combo (80% and 60%)
  function drawFlashlight(W, H, cx, cy) {
    const combo = comboAt(now);
    darkAround(W, H, X(cx), Y(cy), 112 * (combo >= 200 ? 0.6 : combo >= 100 ? 0.8 : 1) * scale);
  }

  // everything dark except a soft circle (canvas pixels)
  function darkAround(W, H, px, py, rad) {
    const g = ctx.createRadialGradient(px, py, rad * 0.8, px, py, rad * 1.2);
    g.addColorStop(0, 'rgba(0,0,0,0)');
    g.addColorStop(1, 'rgba(0,0,0,1)');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, W, H);
  }
  const flOn = () => data.flashlight && flBox.checked;
  // lazer's combo scale for the taiko and catch flashlight
  const comboScale = () => { const c = comboAt(now); return c >= 200 ? 0.77 : c >= 100 ? 0.885 : 1; };
  // 1 until the fade starts, then down to 0 over dur ms
  const fadeOut = (from, dur) => Math.max(0, Math.min(1, 1 - (now - from) / dur));

  // ---- hitsounds: short sounds made in the browser, played when an object was hit ----
  let actx = null, sfx = null, prevNow = null;
  function prepareSounds() {
    const ev = [];
    const hitT = (o) => o.t + (o.te || 0) * data.rate;
    for (const o of data.objects) {
      if (data.mode === 0) {
        const hs = o.hs || 0;
        if (o.k === 'p') { ev.push([o.e, 'n', hs]); continue; }
        if (o.k === 's') {
          if (o.v === 'slider') ev.push([hitT(o), 'n', hs]);
          if (o.sr && o.sr !== 'miss') {
            const span = (o.e - o.t) / o.r;
            for (let k = 1; k <= o.r; k++) ev.push([o.t + k * span, 'n', hs]);
          }
        } else if (o.v && o.v !== 'miss') ev.push([hitT(o), 'n', hs]);
      } else if (data.mode === 1) {
        if ((o.k === 'don' || o.k === 'kat') && o.v && o.v !== 'miss' && o.v !== 'wrong') ev.push([hitT(o), o.k, o.b ? 4 : 0]);
      } else if (data.mode === 2) {
        if (o.k === 'f' && o.v === 'fruit') ev.push([o.t, 'n', 0]);
      } else if (o.v && o.v !== 'miss') {
        ev.push([hitT(o), 'n', 0]);
      }
    }
    ev.sort((a, b) => a[0] - b[0]);
    data.sounds = ev;
  }

  function initSounds() {
    if (actx) return;
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return;
    try { actx = new AC(); } catch (e) { return; }
    const rate = actx.sampleRate;
    const make = (dur, fn) => {
      const n = Math.round(rate * dur), b = actx.createBuffer(1, n, rate), d = b.getChannelData(0);
      let prev = 0;
      for (let i = 0; i < n; i++) {
        const t = i / rate, noise = Math.random() * 2 - 1, bright = noise - prev;
        prev = noise;
        d[i] = fn(t, noise, bright);
      }
      return b;
    };
    const TAU = Math.PI * 2;
    sfx = {
      n: make(0.08, (t, w, b) => Math.sin(TAU * 1100 * t) * Math.exp(-t * 70) * 0.3 + b * Math.exp(-t * 160) * 0.25),
      whistle: make(0.25, (t) => Math.sin(TAU * (1500 + 900 * t) * t) * Math.exp(-t * 14) * 0.18),
      finish: make(0.6, (t, w, b) => b * Math.exp(-t * 6) * 0.22),
      clap: make(0.15, (t, w) => w * (Math.exp(-t * 90) + (t > 0.012 ? 0.7 * Math.exp(-(t - 0.012) * 70) : 0)) * 0.3),
      don: make(0.3, (t) => Math.sin(TAU * (75 * t + 2 * (1 - Math.exp(-t * 30)))) * Math.exp(-t * 12) * 0.9),
      kat: make(0.15, (t, w, b) => b * Math.exp(-t * 60) * 0.35 + Math.sin(TAU * 750 * t) * Math.exp(-t * 45) * 0.3)
    };
  }

  function playSfx(name, gain) {
    if (!actx || !sfx[name]) return;
    const src = actx.createBufferSource();
    src.buffer = sfx[name];
    const g = actx.createGain();
    g.gain.value = gain * parseFloat(volume.value) * 1.5;
    src.connect(g); g.connect(actx.destination);
    src.start();
  }

  // sounds of the objects between the last frame and now (nothing after a jump)
  function playSounds(from, to) {
    if (!hsBox.checked || !actx || !data.sounds.length) return;
    if (to <= from || to - from > 250) return;
    const ev = data.sounds;
    let lo = 0, hi = ev.length;
    while (lo < hi) { const m = (lo + hi) >> 1; if (ev[m][0] <= from) lo = m + 1; else hi = m; }
    for (let i = lo; i < ev.length && ev[i][0] <= to; i++) {
      const [, kind, bits] = ev[i];
      if (kind === 'don' || kind === 'kat') { playSfx(kind, bits ? 1.3 : 1); continue; }
      playSfx('n', 1);
      if (bits & 2) playSfx('whistle', 1);
      if (bits & 4) playSfx('finish', 1);
      if (bits & 8) playSfx('clap', 1);
    }
  }

  // 100s, 50s and misses along the whole replay, above the seek bar
  function drawMarks() {
    const r = marks.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;
    marks.width = Math.max(1, Math.round(r.width * dpr));
    marks.height = Math.max(1, Math.round(r.height * dpr));
    const c = marks.getContext('2d');
    c.clearRect(0, 0, marks.width, marks.height);
    const col = { '100': '#88dd55', '50': '#ffbb44', 'GOOD': '#88dd55', 'miss': '#ff4444', 'wrong': '#ff4444', 'slider_miss': '#f472b6', 'tiny_miss': '#f472b6' };
    data.objects.forEach(o => {
      // a slider is judged at its end by how much of it was held (o.sr)
      const v = o.sr || o.v;
      const k = col[v];
      if (!k) return;
      const x = ((o.sr ? o.e : o.t) - start) / Math.max(1, end - start) * marks.width;
      c.fillStyle = k;
      c.fillRect(Math.round(x), v === 'miss' || v === 'wrong' ? 0 : marks.height * 0.35, Math.max(1, dpr * 1.5), marks.height);
    });
    // staff: snaps and early moves from the anticheat card, on the upper half
    if (staff && Array.isArray(window.analyzerAcMarks)) {
      window.analyzerAcMarks.forEach(m => {
        const x = (m.t - start) / Math.max(1, end - start) * marks.width;
        c.fillStyle = m.c;
        c.fillRect(Math.round(x), 0, Math.max(1, dpr * 1.5), marks.height * 0.5);
      });
    }
  }

  function along(o, dist) {
    const c = o.cum, p = o.pts;
    if (!p.length) return [o.x, o.y];
    let i = 1;
    while (i < c.length - 1 && c[i] < dist) i++;
    const seg = c[i] - c[i - 1] || 1;
    const t = Math.max(0, Math.min(1, (dist - c[i - 1]) / seg));
    return [p[i - 1][0] + (p[i][0] - p[i - 1][0]) * t, p[i - 1][1] + (p[i][1] - p[i - 1][1]) * t];
  }

  // white arrow on a slider end, pointing back along the path (towards dist "to")
  function reverseArrow(o, at, to, r, alpha) {
    const a = along(o, at), b = along(o, to);
    const ang = Math.atan2(b[1] - a[1], b[0] - a[0]);
    const s = r * 0.5;
    ctx.save();
    ctx.globalAlpha = alpha;
    ctx.translate(X(a[0]), Y(a[1]));
    ctx.rotate(ang);
    // arrow: a short stem and a triangle head, with a dark edge so it shows on any colour
    ctx.beginPath();
    ctx.moveTo(-s * 0.8, -s * 0.18); ctx.lineTo(0, -s * 0.18); ctx.lineTo(0, -s * 0.55);
    ctx.lineTo(s * 0.8, 0);
    ctx.lineTo(0, s * 0.55); ctx.lineTo(0, s * 0.18); ctx.lineTo(-s * 0.8, s * 0.18);
    ctx.closePath();
    ctx.lineJoin = 'round';
    ctx.strokeStyle = 'rgba(0,0,0,0.6)'; ctx.lineWidth = Math.max(2, r * 0.12); ctx.stroke();
    ctx.fillStyle = '#ffffff'; ctx.fill();
    ctx.restore();
  }

  // index of the last frame at or before time t
  function frameAt(t) {
    const f = data.frames;
    let lo = 0, hi = data.n - 1;
    if (hi < 0) return -1;
    while (lo < hi) {
      const mid = (lo + hi + 1) >> 1;
      if (f[mid * data.stride] <= t) lo = mid; else hi = mid - 1;
    }
    return lo;
  }

  let scale = 1, ox = 0, oy = 0;
  function resize() {
    const r = canvas.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;
    canvas.width = Math.round(r.width * dpr);
    canvas.height = Math.round(r.height * dpr);
    // playfield 512x384 with a margin, like the game
    scale = Math.min(canvas.width / 640, canvas.height / 480);
    ox = (canvas.width - 512 * scale) / 2;
    oy = (canvas.height - 384 * scale) / 2 + 8 * scale;
  }
  window.addEventListener('resize', () => { if (data) { resize(); drawMarks(); draw(); } });

  const X = (x) => ox + x * scale, Y = (y) => oy + y * scale;

  function draw() {
    const W = canvas.width, H = canvas.height;
    ctx.clearRect(0, 0, W, H);
    if (bg) {
      // cover the canvas, dimmed like in the game
      const k = Math.max(W / bg.width, H / bg.height);
      ctx.globalAlpha = 0.22;
      ctx.drawImage(bg, (W - bg.width * k) / 2, (H - bg.height * k) / 2, bg.width * k, bg.height * k);
      ctx.globalAlpha = 1;
    }
    if (data.mode === 3) { drawMania(W, H); finish(W, H); return; }
    if (data.mode === 1) { drawTaiko(W, H); finish(W, H); return; }
    if (data.mode === 2) { drawCatch(W, H); finish(W, H); return; }
    ctx.strokeStyle = 'rgba(255,255,255,0.06)';
    ctx.strokeRect(X(0), Y(0), 512 * scale, 384 * scale);
    const r = data.radius * scale;
    const pre = data.preempt;
    const fadeIn = 400 * Math.min(1, pre / 450);
    const objs = data.objects;
    if (fpBox.checked) drawFollowPoints();
    // visible objects, drawn from the last to the first so earlier ones are on top
    const vis = [];
    for (let i = 0; i < objs.length; i++) {
      const o = objs[i];
      if (o.t - pre > now) break;
      if (o.e + 600 < now) continue;
      vis.push(o);
    }
    for (let i = vis.length - 1; i >= 0; i--) {
      const o = vis[i];
      let appear = Math.max(0, Math.min(1, (now - (o.t - pre)) / fadeIn));
      const gone = now > o.e ? Math.max(0, 1 - (now - o.e) / 200) : 1;
      let bodyAlpha = appear;
      if (hdOn()) {
        // Hidden: fade in over 40% of the approach time, then fade out over the next 30%;
        // a slider body fades out until the slider ends
        const hdIn = pre * 0.4, outStart = o.t - pre + hdIn;
        appear = Math.max(0, Math.min(1, (now - (o.t - pre)) / hdIn));
        bodyAlpha = appear;
        if (now > outStart) {
          appear *= Math.max(0, 1 - (now - outStart) / (pre * 0.3));
          bodyAlpha = Math.max(0, 1 - (now - outStart) / Math.max(1, o.e - outStart));
        }
      }
      const alpha = appear * gone;
      if (o.k === 'p') {
        if (now < o.t - 300 || now > o.e + 200) continue;
        const prog = Math.max(0, Math.min(1, (now - o.t) / Math.max(1, o.e - o.t)));
        // a spinner stays visible until it ends, also with Hidden (only its shrinking circle is hidden)
        const sa = Math.max(0, Math.min(1, (now - (o.t - 300)) / 300)) * (now > o.e ? Math.max(0, 1 - (now - o.e) / 200) : 1);
        ctx.globalAlpha = sa * 0.8;
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 3 * scale;
        ctx.beginPath(); ctx.arc(X(256), Y(192), 188 * scale, 0, Math.PI * 2); ctx.stroke();
        ctx.fillStyle = '#ffffff';
        ctx.beginPath(); ctx.arc(X(256), Y(192), 6 * scale, 0, Math.PI * 2); ctx.fill();
        if (!hdOn()) {
          ctx.globalAlpha = sa * 0.6;
          ctx.beginPath(); ctx.arc(X(256), Y(192), 180 * scale * (1 - prog) + 8 * scale, 0, Math.PI * 2); ctx.stroke();
        }
        ctx.globalAlpha = 1;
        continue;
      }
      const col = COMBO[o.c % COMBO.length];
      if (o.k === 's' && now <= o.e) {
        ctx.globalAlpha = bodyAlpha * gone * 0.9;
        ctx.lineCap = 'round'; ctx.lineJoin = 'round';
        ctx.beginPath();
        o.pts.forEach((p, j) => j ? ctx.lineTo(X(p[0]), Y(p[1])) : ctx.moveTo(X(p[0]), Y(p[1])));
        ctx.strokeStyle = '#ffffff'; ctx.lineWidth = r * 2; ctx.stroke();
        ctx.strokeStyle = '#1b1f2a'; ctx.lineWidth = r * 2 - 4 * scale; ctx.stroke();
        // reverse arrows: the next repeat still to come at the tail and at the head
        if (o.r > 1) {
          const len = o.cum[o.cum.length - 1];
          const span = (o.e - o.t) / o.r;
          const passed = now < o.t ? 0 : Math.floor((now - o.t) / span);
          let atTail = false, atHead = false;
          for (let j = passed; j <= o.r - 2; j++) { if (j % 2 === 0) atTail = true; else atHead = true; }
          if (atTail) reverseArrow(o, len, len - Math.min(len, 12), r, bodyAlpha * gone);
          if (atHead && now >= o.t) reverseArrow(o, 0, Math.min(len, 12), r, bodyAlpha * gone);
        }
        // slider ball
        if (now >= o.t) {
          const len = o.cum[o.cum.length - 1];
          const span = (o.e - o.t) / o.r;
          const k = Math.min(o.r - 1e-6, (now - o.t) / span);
          let f = k - Math.floor(k);
          if (Math.floor(k) % 2 === 1) f = 1 - f;
          const p = along(o, f * len);
          ctx.globalAlpha = gone; // the ball stays visible, also with Hidden
          ctx.fillStyle = col;
          ctx.beginPath(); ctx.arc(X(p[0]), Y(p[1]), r * 0.85, 0, Math.PI * 2); ctx.fill();
          ctx.strokeStyle = '#ffffff'; ctx.lineWidth = 2 * scale;
          ctx.beginPath(); ctx.arc(X(p[0]), Y(p[1]), r * 1.9, 0, Math.PI * 2); ctx.stroke();
        }
      }
      // head circle (fades once it was hit)
      if (now <= o.t + 150) {
        const ha = now > o.t ? alpha * Math.max(0, 1 - (now - o.t) / 150) : alpha;
        ctx.globalAlpha = ha;
        ctx.fillStyle = col;
        ctx.beginPath(); ctx.arc(X(o.x), Y(o.y), r * 0.92, 0, Math.PI * 2); ctx.fill();
        ctx.strokeStyle = '#ffffff'; ctx.lineWidth = 3 * scale;
        ctx.beginPath(); ctx.arc(X(o.x), Y(o.y), r * 0.92, 0, Math.PI * 2); ctx.stroke();
        ctx.fillStyle = '#ffffff';
        ctx.font = 'bold ' + Math.round(r * 0.8) + 'px sans-serif';
        ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
        ctx.fillText(o.n, X(o.x), Y(o.y) + 1);
        if (!hdOn() && now < o.t) {
          const ap = 1 + 3 * (o.t - now) / pre;
          ctx.strokeStyle = col; ctx.lineWidth = 2.5 * scale;
          ctx.beginPath(); ctx.arc(X(o.x), Y(o.y), r * ap, 0, Math.PI * 2); ctx.stroke();
        }
      }
      // verdict from the analyzer, shortly after the hit time
      // verdict from the analyzer: circles at the hit time, sliders at their end
      const vk = o.sr || o.v;
      const v = VERDICT[vk];
      const vt = o.sr ? o.e : o.t;
      if (v && now >= vt && now < vt + 600 && (vk !== '300' || box300.checked)) {
        let vx = o.x, vy = o.y;
        if (o.sr && o.pts && o.pts.length) {
          const len = o.cum[o.cum.length - 1];
          const p = along(o, o.r % 2 === 1 ? len : 0);
          vx = p[0]; vy = p[1];
        }
        ctx.globalAlpha = 1 - (now - vt) / 600;
        ctx.fillStyle = v[1];
        ctx.font = 'bold ' + Math.round(r * 0.7) + 'px sans-serif';
        ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
        ctx.fillText(v[0], X(vx), Y(vy) - r * 1.2);
      }
    }
    ctx.globalAlpha = 1;
    // cursor and trail
    const i = frameAt(now);
    if (i >= 0) {
      const f = data.frames;
      let cx = f[i * 4 + 1] / 10, cy = f[i * 4 + 2] / 10;
      if (i + 1 < data.n) {
        const t0 = f[i * 4], t1 = f[(i + 1) * 4];
        const k = t1 > t0 ? Math.max(0, Math.min(1, (now - t0) / (t1 - t0))) : 0;
        cx += (f[(i + 1) * 4 + 1] / 10 - cx) * k;
        cy += (f[(i + 1) * 4 + 2] / 10 - cy) * k;
      }
      if (data.flashlight && flBox.checked) drawFlashlight(W, H, cx, cy);
      if (trailBox.checked) {
        ctx.lineWidth = 2 * scale;
        let j = i;
        let px = cx, py = cy;
        while (j > 0 && now - f[j * 4] < 120) {
          const a = 1 - (now - f[j * 4]) / 120;
          ctx.strokeStyle = 'rgba(255,220,120,' + (a * 0.7) + ')';
          ctx.beginPath(); ctx.moveTo(X(px), Y(py));
          px = f[j * 4 + 1] / 10; py = f[j * 4 + 2] / 10;
          ctx.lineTo(X(px), Y(py)); ctx.stroke();
          j--;
        }
      }
      const keys = f[i * 4 + 3];
      const down = (keys & 15) !== 0;
      ctx.fillStyle = down ? '#ffd34d' : '#ffffff';
      ctx.beginPath(); ctx.arc(X(cx), Y(cy), (down ? 7 : 5.5) * scale, 0, Math.PI * 2); ctx.fill();
      // keys
      const k1 = (keys & 4) !== 0, k2 = (keys & 8) !== 0;
      const m1 = (keys & 1) !== 0 && !k1, m2 = (keys & 2) !== 0 && !k2;
      [['K1', k1], ['K2', k2], ['M1', m1], ['M2', m2]].forEach((k, n) => {
        const bx = Math.min(W - 40 * scale, X(512) + 10 * scale), by = H / 2 - 60 * scale + n * 32 * scale;
        ctx.fillStyle = k[1] ? '#ffd34d' : 'rgba(255,255,255,0.12)';
        ctx.fillRect(bx, by, 34 * scale, 26 * scale);
        ctx.fillStyle = k[1] ? '#111' : 'rgba(255,255,255,0.6)';
        ctx.font = 'bold ' + Math.round(12 * scale) + 'px sans-serif';
        ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
        ctx.fillText(k[0], bx + 17 * scale, by + 13 * scale);
      });
      if (cmp) drawCompare(cx, cy);
    }
    finish(W, H);
  }

  // ---- staff tools ----

  // cursor of the second replay at time t (osu! frames: time, x*10, y*10, keys)
  function cmpCursor(t) {
    const f = cmp.frames;
    let lo = 0, hi = cmp.n - 1;
    if (hi < 0) return null;
    while (lo < hi) { const mid = (lo + hi + 1) >> 1; if (f[mid * 4] <= t) lo = mid; else hi = mid - 1; }
    let x = f[lo * 4 + 1] / 10, y = f[lo * 4 + 2] / 10;
    if (lo + 1 < cmp.n) {
      const t0 = f[lo * 4], t1 = f[(lo + 1) * 4];
      const k = t1 > t0 ? Math.max(0, Math.min(1, (t - t0) / (t1 - t0))) : 0;
      x += (f[(lo + 1) * 4 + 1] / 10 - x) * k; y += (f[(lo + 1) * 4 + 2] / 10 - y) * k;
    }
    return [x, y, lo];
  }

  function drawCompare(cx, cy) {
    const p = cmpCursor(now);
    if (!p) return;
    const f = cmp.frames;
    // its trail
    ctx.lineWidth = 2 * scale;
    let j = p[2], px = p[0], py = p[1];
    while (j > 0 && now - f[j * 4] < 120) {
      const a = 1 - (now - f[j * 4]) / 120;
      ctx.strokeStyle = 'rgba(34,211,238,' + (a * 0.7) + ')';
      ctx.beginPath(); ctx.moveTo(X(px), Y(py));
      px = f[j * 4 + 1] / 10; py = f[j * 4 + 2] / 10;
      ctx.lineTo(X(px), Y(py)); ctx.stroke();
      j--;
    }
    ctx.strokeStyle = 'rgba(255,255,255,0.35)'; ctx.lineWidth = 1;
    ctx.beginPath(); ctx.moveTo(X(cx), Y(cy)); ctx.lineTo(X(p[0]), Y(p[1])); ctx.stroke();
    ctx.fillStyle = '#22d3ee';
    ctx.beginPath(); ctx.arc(X(p[0]), Y(p[1]), 5.5 * scale, 0, Math.PI * 2); ctx.fill();
    if (cmpInfo) cmpInfo.textContent = Math.hypot(p[0] - cx, p[1] - cy).toFixed(1) + ' px';
  }

  function keyNames(k) {
    const n = [];
    if (data.mode === 0) {
      if (k & 4) n.push('K1'); else if (k & 1) n.push('M1');
      if (k & 8) n.push('K2'); else if (k & 2) n.push('M2');
    } else {
      for (let b = 0; b < 18; b++) if (k & (1 << b)) n.push(String(b + 1));
    }
    return n.length ? n.join(' ') : '-';
  }

  // the frame under the cursor of time, what changed since the previous one, frame spacing around it
  function staffPanel() {
    if (!staff || !data) return;
    const f = data.frames, st = data.stride;
    const i = frameAt(now);
    if (i < 0) { acInfo.textContent = '-'; return; }
    const t = f[i * st];
    const dt = i > 0 ? t - f[(i - 1) * st] : 0;
    const real = dt / (data.rate || 1);
    let txt = 'frame ' + i + '/' + data.n + '  t ' + t + ' ms  +' + dt + ' ms (' + real.toFixed(1) + ' ms real)';
    if (data.mode === 0) {
      const x = f[i * 4 + 1] / 10, y = f[i * 4 + 2] / 10;
      txt += '  x ' + x.toFixed(1) + ' y ' + y.toFixed(1);
      if (i > 0 && dt > 0) {
        const d = Math.hypot(x - f[(i - 1) * 4 + 1] / 10, y - f[(i - 1) * 4 + 2] / 10);
        txt += '  moved ' + d.toFixed(1) + ' px (' + (d / dt).toFixed(2) + ' px/ms)';
      }
      txt += '  keys ' + keyNames(f[i * 4 + 3]);
    } else if (data.mode === 2) {
      txt += '  x ' + (f[i * 3 + 1] / 10).toFixed(1) + (f[i * 3 + 2] ? '  dash' : '');
    } else {
      txt += '  keys ' + keyNames(f[i * 2 + 1]);
    }
    acInfo.textContent = txt;
    // frame spacing, one second each way
    const r = acFt.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;
    acFt.width = Math.max(1, Math.round(r.width * dpr));
    acFt.height = Math.max(1, Math.round(r.height * dpr));
    const c = acFt.getContext('2d');
    c.clearRect(0, 0, acFt.width, acFt.height);
    const span = 1000 * (data.rate || 1), from = now - span, to = now + span;
    const expected = 1000 / 60 * (data.rate || 1);
    const yOf = (d) => acFt.height - 3 * dpr - Math.min(1, d / (expected * 2)) * (acFt.height - 6 * dpr);
    const xOf = (tt) => (tt - from) / (to - from) * acFt.width;
    c.fillStyle = '#ef4444';
    c.fillRect(0, Math.round(yOf(expected)), acFt.width, dpr);
    c.fillStyle = 'rgba(255,255,255,0.25)';
    c.fillRect(Math.round(xOf(now)), 0, dpr, acFt.height);
    let k = Math.max(1, frameAt(from));
    for (; k < data.n && f[k * st] <= to; k++) {
      const d = f[k * st] - f[(k - 1) * st];
      c.fillStyle = k === i ? '#ffd34d' : '#7dd3fc';
      c.fillRect(Math.round(xOf(f[k * st])) - dpr, Math.round(yOf(d)) - dpr, 2 * dpr + (k === i ? dpr : 0), 2 * dpr + (k === i ? dpr : 0));
    }
  }

  // one replay frame back or forward
  function stepFrame(dir) {
    const f = data.frames, st = data.stride;
    let i = frameAt(now);
    if (dir > 0) {
      let j = i + 1;
      while (j < data.n && f[j * st] <= now) j++;
      if (j < data.n) now = f[j * st];
    } else {
      let j = i;
      while (j >= 0 && f[j * st] >= now) j--;
      if (j >= 0) now = f[j * st];
    }
    setPlaying(false);
    draw();
  }

  if (cmpGo) cmpGo.addEventListener('click', () => {
    const id = parseInt(cmpId.value, 10);
    cmpErr.style.display = 'none';
    if (!data || !(id > 0)) return;
    fetch('/analyzer/' + id + '?replay=1', { credentials: 'same-origin' })
      .then(r => { if (!r.ok) throw new Error(); return r.json(); })
      .then(d => {
        // the same map: same objects at the same times
        const a = data.objects, b = d.objects || [];
        if (d.mode !== 0 || data.mode !== 0 || a.length !== b.length || (a.length && a[0].t !== b[0].t) || (a.length && a[a.length - 1].t !== b[b.length - 1].t)) throw new Error();
        cmp = { frames: d.frames, n: d.frames.length / 4 };
        draw();
      })
      .catch(() => { cmp = null; cmpInfo.textContent = ''; cmpErr.style.display = ''; draw(); });
  });


  // hit error meter (hits of the last 3 seconds), seek bar and time
  const BANDS = { 2: ['#66ccff', '#88dd55'], 3: ['#66ccff', '#88dd55', '#ffbb44'], 5: ['#d8f3ff', '#66ccff', '#88dd55', '#ffbb44', '#ff8844'] };
  function finish(W, H) {
    staffPanel();
    const w = data.meter || [];
    if (w.length) {
      const pal = BANDS[w.length] || BANDS[3];
      const unit = 150 * scale / w[w.length - 1];
      const my = H - 18 * scale, mx = W / 2;
      for (let i = w.length - 1; i >= 0; i--) {
        ctx.globalAlpha = 0.55;
        ctx.fillStyle = pal[i];
        ctx.fillRect(mx - w[i] * unit, my - 2 * scale, w[i] * 2 * unit, 4 * scale);
      }
      ctx.globalAlpha = 1;
      ctx.fillStyle = '#ffffff';
      ctx.fillRect(mx - scale, my - 8 * scale, 2 * scale, 16 * scale);
      const objs = data.objects;
      for (let j = objs.length - 1; j >= 0; j--) {
        const o = objs[j];
        if (o.t > now) continue;
        if (now - o.t > 3000) break;
        if (o.te === undefined) continue;
        let band = w.findIndex(x => Math.abs(o.te) <= x);
        if (band < 0) band = w.length - 1;
        ctx.globalAlpha = 1 - (now - o.t) / 3000;
        ctx.fillStyle = pal[band];
        ctx.fillRect(mx + o.te * unit - scale, my - 7 * scale, 2 * scale, 14 * scale);
      }
      ctx.globalAlpha = 1;
    }
    seek.value = now;
    timeLabel.textContent = fmt(now - start) + ' / ' + fmt(end - start);
  }

  const VCOLOR = { MAX: '#d8f3ff', '300': '#66ccff', '200': '#88dd55', '100': '#88dd55', '50': '#ffbb44', GREAT: '#66ccff', GOOD: '#88dd55', miss: '#ff4444', wrong: '#ff4444' };
  // the newest judgement up to 400 ms old, shown at (x, y)
  function popup(x, y, size, filter) {
    const objs = data.objects;
    for (let j = objs.length - 1; j >= 0; j--) {
      const o = objs[j];
      if (o.t > now || !o.v || (filter && !filter(o))) continue;
      if (now - o.t > 400) break;
      ctx.globalAlpha = 1 - (now - o.t) / 400;
      ctx.fillStyle = VCOLOR[o.v] || '#ffffff';
      ctx.font = 'bold ' + Math.round(size) + 'px sans-serif';
      ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
      ctx.fillText(o.v === 'wrong' || o.v === 'miss' ? '\u00d7' : o.v, x, y);
      ctx.globalAlpha = 1;
      return;
    }
  }

  function keysAt(t) {
    const i = frameAt(t);
    return i >= 0 ? data.frames[i * data.stride + 1] : 0;
  }

  // osu!mania: notes fall to the judgement line, pressed columns light up
  function drawMania(W, H) {
    const K = data.keys;
    const colW = Math.min(W * 0.8 / K, 64 * scale);
    const x0 = (W - colW * K) / 2;
    const lineY = H - 70 * scale;
    const speed = lineY / 650; // px per ms of the song
    const COLS = K % 2 === 1 ? i => (i === (K - 1) / 2 ? '#f2c94c' : (Math.min(i, K - 1 - i) % 2 === 0 ? '#e8e8e8' : '#4fa3ff'))
                             : i => (Math.min(i, K - 1 - i) % 2 === 0 ? '#e8e8e8' : '#4fa3ff');
    ctx.fillStyle = 'rgba(0,0,0,0.55)';
    ctx.fillRect(x0, 0, colW * K, H);
    const mask = keysAt(now);
    for (let c = 0; c < K; c++) {
      if (mask & (1 << c)) {
        const g = ctx.createLinearGradient(0, lineY, 0, lineY - 160 * scale);
        g.addColorStop(0, 'rgba(255,255,255,0.28)'); g.addColorStop(1, 'rgba(255,255,255,0)');
        ctx.fillStyle = g;
        ctx.fillRect(x0 + c * colW, lineY - 160 * scale, colW, 160 * scale);
      }
      ctx.strokeStyle = 'rgba(255,255,255,0.08)';
      ctx.beginPath(); ctx.moveTo(x0 + c * colW, 0); ctx.lineTo(x0 + c * colW, H); ctx.stroke();
    }
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(x0, lineY, colW * K, 2 * scale);
    const nh = 12 * scale;
    const circles = skinSel.value === 'circles';
    const dot = (cx, cy, rr) => {
      ctx.beginPath(); ctx.arc(cx, cy, rr, 0, Math.PI * 2); ctx.fill();
      ctx.save(); ctx.globalAlpha *= 0.9; ctx.strokeStyle = 'rgba(0,0,0,0.45)'; ctx.lineWidth = 2 * scale; ctx.stroke(); ctx.restore();
    };
    for (const o of data.objects) {
      if (o.t - now > 700) break;
      const endT = o.e !== undefined ? o.e : o.t;
      if (endT - now < -300) continue;
      const hit = o.v && o.v !== 'miss';
      const x = x0 + o.c * colW + 2 * scale, w = colW - 4 * scale;
      const y = lineY - (o.t - now) * speed;
      const held = hit && now >= o.t;
      const rr = Math.min(w * 0.45, 26 * scale);
      ctx.globalAlpha = o.v === 'miss' && now > o.t ? 0.35 : 1;
      ctx.fillStyle = COLS(o.c);
      if (o.e !== undefined) {
        const yEnd = lineY - (o.e - now) * speed;
        const yHead = held ? lineY : y;
        ctx.globalAlpha *= 0.65;
        if (circles) {
          // body between the two circles, with a smaller circle as the tail
          const bw = rr * 1.3;
          const top = yEnd, bottom = held ? lineY : y - rr;
          ctx.fillRect(x + w / 2 - bw / 2, top, bw, Math.max(0, bottom - top));
          if (yEnd < lineY) dot(x + w / 2, yEnd, rr * 0.7);
        } else {
          ctx.fillRect(x + w * 0.15, yEnd, w * 0.7, Math.max(0, yHead - yEnd));
        }
        ctx.globalAlpha = o.v === 'miss' && now > o.t ? 0.35 : 1;
      }
      if (!held) {
        if (circles) dot(x + w / 2, y - rr, rr);
        else ctx.fillRect(x, y - nh, w, nh);
      }
    }
    ctx.globalAlpha = 1;
    const laneCover = (top, bottom, fadeUp) => {
      // solid between top and bottom, with a soft edge on the side away from the line
      const edge = 40 * scale;
      const g = fadeUp ? ctx.createLinearGradient(0, top - edge, 0, top) : ctx.createLinearGradient(0, bottom + edge, 0, bottom);
      g.addColorStop(0, 'rgba(11,13,18,0)'); g.addColorStop(1, 'rgba(11,13,18,1)');
      ctx.fillStyle = g;
      if (fadeUp) ctx.fillRect(x0, top - edge, colW * K, edge); else ctx.fillRect(x0, bottom, colW * K, edge);
      ctx.fillStyle = 'rgb(11,13,18)';
      ctx.fillRect(x0, top, colW * K, bottom - top);
    };
    if (hdOn()) {
      // Hidden: the lane above the line is covered, more as the combo grows (like lazer: 20% to 50%)
      const cov = 0.2 + 0.3 * Math.min(1, comboAt(now) / 480);
      laneCover(lineY - lineY * cov, lineY, true);
    }
    if (flOn()) {
      // Flashlight: only a band above the line is visible
      laneCover(0, lineY - lineY * 0.3, false);
    }
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(x0, lineY, colW * K, 2 * scale);
    popup(W / 2, lineY - 150 * scale, 30 * scale);
    // keys
    for (let c = 0; c < K; c++) {
      ctx.fillStyle = mask & (1 << c) ? COLS(c) : 'rgba(255,255,255,0.12)';
      if (skinSel.value === 'circles') {
        const kr = Math.min((colW - 6 * scale) / 2, 16 * scale);
        ctx.beginPath(); ctx.arc(x0 + c * colW + colW / 2, lineY + 10 * scale + kr, kr, 0, Math.PI * 2); ctx.fill();
      } else {
        ctx.fillRect(x0 + c * colW + 3 * scale, lineY + 10 * scale, colW - 6 * scale, 24 * scale);
      }
    }
  }

  // osu!taiko: notes scroll to the hit circle on the left, the drum shows the keys
  function drawTaiko(W, H) {
    const s = H / 300;
    const cy = H * 0.45, half = 55 * s;
    const hitX = 170 * s;
    const speed = (W - hitX) / 1100;
    ctx.fillStyle = 'rgba(0,0,0,0.6)';
    ctx.fillRect(0, cy - half, W, half * 2);
    ctx.strokeStyle = 'rgba(255,255,255,0.35)'; ctx.lineWidth = 2 * s;
    ctx.beginPath(); ctx.arc(hitX, cy, 30 * s, 0, Math.PI * 2); ctx.stroke();
    ctx.beginPath(); ctx.arc(hitX, cy, 44 * s, 0, Math.PI * 2); ctx.stroke();
    const objs = data.objects;
    const vis = [];
    for (const o of objs) {
      if (o.t - now > 1200) break;
      const endT = o.e !== undefined ? o.e : o.t;
      if (hitX + (endT - now) * speed < -60 * s) continue;
      vis.push(o);
    }
    for (let i = vis.length - 1; i >= 0; i--) {
      const o = vis[i];
      const x = hitX + (o.t - now) * speed;
      if (o.k === 'roll') {
        const x2 = hitX + (o.e - now) * speed;
        ctx.fillStyle = '#f2b02c';
        ctx.beginPath(); ctx.arc(x, cy, 26 * s, Math.PI / 2, Math.PI * 1.5); ctx.lineTo(x2, cy - 26 * s);
        ctx.arc(x2, cy, 26 * s, -Math.PI / 2, Math.PI / 2); ctx.closePath(); ctx.fill();
        continue;
      }
      if (o.k === 'swell') {
        const sx = now >= o.t ? hitX : x;
        if (now > o.e) continue;
        ctx.fillStyle = '#f2c94c';
        ctx.beginPath(); ctx.arc(sx, cy, 34 * s, 0, Math.PI * 2); ctx.fill();
        continue;
      }
      const hit = o.v && o.v !== 'miss' && o.v !== 'wrong';
      if (hit && now >= o.t) continue;
      const r = (o.b ? 42 : 28) * s;
      ctx.globalAlpha = !hit && now > o.t ? 0.4 : 1;
      // Hidden (lazer): a note fades out over the first 37.5% of its way to the hit circle
      if (hdOn()) ctx.globalAlpha *= fadeOut(o.t - 1100, 1100 * 0.375);
      ctx.fillStyle = o.k === 'kat' ? '#448aca' : '#eb452c';
      ctx.beginPath(); ctx.arc(x, cy, r, 0, Math.PI * 2); ctx.fill();
      ctx.strokeStyle = '#ffffff'; ctx.lineWidth = 3 * s; ctx.stroke();
      ctx.globalAlpha = 1;
    }
    if (flOn()) darkAround(W, H, hitX, cy, 200 * s * comboScale());
    // drum on the left: 1 left centre, 4 right centre, 2 left rim, 8 right rim
    const k = keysAt(now);
    const dx = 70 * s;
    ctx.fillStyle = 'rgba(20,20,28,0.95)';
    ctx.fillRect(0, cy - half, 130 * s, half * 2);
    const part = (on, colour, startA, endA, r1, r2) => {
      ctx.fillStyle = on ? colour : 'rgba(255,255,255,0.08)';
      ctx.beginPath(); ctx.arc(dx, cy, r2, startA, endA); ctx.arc(dx, cy, r1, endA, startA, true); ctx.closePath(); ctx.fill();
    };
    part(k & 2, '#448aca', Math.PI / 2, Math.PI * 1.5, 34 * s, 46 * s);
    part(k & 8, '#448aca', -Math.PI / 2, Math.PI / 2, 34 * s, 46 * s);
    part(k & 1, '#eb452c', Math.PI / 2, Math.PI * 1.5, 0, 32 * s);
    part(k & 4, '#eb452c', -Math.PI / 2, Math.PI / 2, 0, 32 * s);
    popup(hitX, cy - half - 24 * s, 26 * s, o => o.k === 'don' || o.k === 'kat');
  }

  // osu!catch: objects fall to the catcher, which moves with the replay; red outline = hyperdash
  const FRUIT_COLS = ['#ff6b6b', '#ffd166', '#6ee7b7', '#7dd3fc'];
  function drawCatch(W, H) {
    ctx.strokeStyle = 'rgba(255,255,255,0.06)';
    ctx.strokeRect(X(0), Y(0), 512 * scale, 384 * scale);
    const pre = data.preempt;
    const catcherY = 350;
    const r = 30 * Math.abs(data.scale);
    const f = data.frames;
    const i = frameAt(now);
    let cx = 256, dash = false;
    if (i >= 0) {
      cx = f[i * 3 + 1] / 10;
      dash = (f[i * 3 + 2] & 1) !== 0;
      if (i + 1 < data.n) {
        const t0 = f[i * 3], t1 = f[(i + 1) * 3];
        const k = t1 > t0 ? Math.max(0, Math.min(1, (now - t0) / (t1 - t0))) : 0;
        cx += (f[(i + 1) * 3 + 1] / 10 - cx) * k;
      }
    }
    const objs = data.objects;
    let colour = 0;
    for (let j = 0; j < objs.length; j++) {
      const o = objs[j];
      if (o.k === 'f') colour++;
      if (o.t - pre > now) break;
      const caught = o.v && o.v !== 'miss' && o.v !== 'tiny_miss';
      if (now > o.t && (caught || o.k === 'b' || now > o.t + 400)) continue;
      const y = catcherY - (o.t - now) / pre * catcherY;
      const rr = o.k === 'f' ? r : o.k === 'd' ? r * 0.55 : o.k === 'y' ? r * 0.3 : r * 0.7;
      ctx.globalAlpha = now > o.t ? 0.45 : 1;
      // Hidden (lazer): visible for the first 60% of the fall, then fades out
      if (hdOn() && o.k !== 'b') ctx.globalAlpha *= fadeOut(o.t - pre * 0.4, pre * 0.44);
      ctx.fillStyle = o.k === 'b' ? '#facc15' : o.k === 'f' ? FRUIT_COLS[colour % FRUIT_COLS.length] : '#e0f2fe';
      ctx.beginPath(); ctx.arc(X(o.x), Y(y), rr * scale, 0, Math.PI * 2); ctx.fill();
      if (o.h) { ctx.strokeStyle = '#ef4444'; ctx.lineWidth = 3 * scale; ctx.stroke(); }
      ctx.globalAlpha = 1;
    }
    if (flOn()) darkAround(W, H, X(cx), Y(catcherY), 130 * comboScale() * scale);
    // catcher: the part that catches, plus its trail while dashing
    const half = data.half;
    if (trailBox.checked && i > 0) {
      ctx.strokeStyle = 'rgba(125,211,252,0.35)';
      ctx.lineWidth = 2 * scale;
      ctx.beginPath();
      let j = i, first = true;
      while (j > 0 && now - f[j * 3] < 200) {
        const px = X(f[j * 3 + 1] / 10), py = Y(catcherY + 18 + (now - f[j * 3]) / 12);
        if (first) { ctx.moveTo(px, py); first = false; } else ctx.lineTo(px, py);
        j--;
      }
      ctx.stroke();
    }
    ctx.fillStyle = dash ? '#7dd3fc' : '#f3f4f6';
    ctx.fillRect(X(cx - half), Y(catcherY), half * 2 * scale, 10 * scale);
    ctx.fillStyle = 'rgba(255,255,255,0.15)';
    ctx.fillRect(X(cx - half), Y(catcherY + 10), half * 2 * scale, 18 * scale);
    popup(X(cx), Y(catcherY - 40), 26 * scale, o => o.v === 'miss');
  }

  function fmt(ms) {
    const s = Math.max(0, Math.floor(ms / 1000));
    return Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0');
  }

  function tick(ts) {
    if (!playing) return;
    if (!document.body.contains(canvas)) { playing = false; if (audio) audio.pause(); return; } // page left (Turbo)
    const dt = last ? ts - last : 0;
    last = ts;
    if (audio && !audio.paused && !audio.ended) {
      now = audio.currentTime * 1000; // the music is the clock, so the picture stays in sync
    } else {
      now += dt * data.rate * parseFloat(speedSel.value);
      if (audio && audio.paused && !audio.ended && now >= 0 && now < audio.duration * 1000) startAudio();
    }
    if (now >= end) { now = end; setPlaying(false); }
    if (prevNow !== null) playSounds(prevNow, now);
    prevNow = now;
    draw();
    requestAnimationFrame(tick);
  }

  function startAudio() {
    if (!audio || now < 0) return;
    audio.playbackRate = data.rate * parseFloat(speedSel.value);
    audio.currentTime = now / 1000;
    audio.play().catch(() => {});
  }

  function setPlaying(p) {
    playing = p;
    playBtn.innerHTML = p ? '<i class="fa-solid fa-pause"></i>' : '<i class="fa-solid fa-play"></i>';
    prevNow = null;
    if (p) { initSounds(); if (actx && actx.state === 'suspended') actx.resume(); last = 0; startAudio(); requestAnimationFrame(tick); }
    else if (audio) audio.pause();
  }

  playBtn.addEventListener('click', () => { if (!data) return; if (now >= end) now = start; setPlaying(!playing); });
  seek.addEventListener('input', () => { if (!data) return; now = parseFloat(seek.value); if (playing) startAudio(); draw(); });
  speedSel.addEventListener('change', () => { if (audio) audio.playbackRate = data.rate * parseFloat(speedSel.value); });
  volume.addEventListener('input', () => {
    if (audio) audio.volume = parseFloat(volume.value);
    try { localStorage.setItem('analyzer-volume', volume.value); } catch (e) {}
  });
  box300.addEventListener('change', () => { if (data) draw(); });
  canvas.addEventListener('keydown', (e) => {
    if (!data) return;
    if (e.code === 'Space') { e.preventDefault(); setPlaying(!playing); }
    else if (e.code === 'ArrowLeft') { now = Math.max(start, now - 2000); if (playing) startAudio(); draw(); }
    else if (e.code === 'ArrowRight') { now = Math.min(end, now + 2000); if (playing) startAudio(); draw(); }
    else if (staff && e.code === 'Comma') { e.preventDefault(); stepFrame(-1); }
    else if (staff && e.code === 'Period') { e.preventDefault(); stepFrame(1); }
  });
  canvas.addEventListener('click', () => { canvas.focus(); if (data) setPlaying(!playing); });

  // jump to a moment of the song (ms) and pause there, loading the replay first if needed
  function seekTo(t) {
    const target = t - 1000;
    if (!data) {
      pendingSeek = target;
      return load().then(() => { canvas.scrollIntoView({ behavior: 'smooth', block: 'center' }); });
    }
    setPlaying(false);
    now = Math.max(start, Math.min(end, target));
    draw();
    canvas.scrollIntoView({ behavior: 'smooth', block: 'center' });
    return Promise.resolve(true);
  }

  return {
    load: load,
    play: () => { if (data) setPlaying(true); },
    pause: () => { if (data) setPlaying(false); },
    seek: seekTo
  };
};
