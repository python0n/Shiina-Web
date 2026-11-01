/*
 * Website chat (/chat). Talks IRC over bancho's /ws/chat WebSocket, one IRC line per
 * WebSocket message. PM history and the last channel messages come from Shiina's
 * /api/v1/chat/* endpoints.
 */
(function () {
  'use strict';

  if (window.ShiinaChat) {
    window.ShiinaChat.mount();
    return;
  }

  var T = window.SHIINA_CHAT_T || {};
  var BOT = 'BanchoBot';
  var IRC_LINE = /^(?::(.+?) )?(PRIVMSG|NOTICE|JOIN|PART|QUIT|TOPIC|PONG|PING|ERROR|MODE|KICK|\d{3})(?: (.*))?$/;
  var STORE_KEY = 'shiinaChat.channels';

  var S = null; // state of the mounted page

  function safe(name) { return String(name || '').trim().toLowerCase().replace(/ /g, '_'); }
  function esc(s) {
    return String(s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
  function fmtTime(sec) {
    var d = new Date(sec * 1000);
    var now = new Date();
    var hm = ('0' + d.getHours()).slice(-2) + ':' + ('0' + d.getMinutes()).slice(-2);
    if (d.toDateString() === now.toDateString()) return hm;
    return ('0' + d.getDate()).slice(-2) + '.' + ('0' + (d.getMonth() + 1)).slice(-2) + ' ' + hm;
  }
  function nowSec() { return Math.floor(Date.now() / 1000); }

  // text -> safe html with links (plain urls, osu! [url title] and (title)[url])
  function richText(text) {
    var out = '';
    var re = /\[(https?:\/\/[^\s\]]+) ([^\]]+)\]|\(([^)]+)\)\[(https?:\/\/[^\s\]]+)\]|(https?:\/\/[^\s<]+)/g;
    var last = 0, m;
    while ((m = re.exec(text)) !== null) {
      out += esc(text.slice(last, m.index));
      var url = m[1] || m[4] || m[5];
      var label = m[2] || m[3] || m[5];
      out += '<a href="' + esc(url) + '" target="_blank" rel="noopener noreferrer">' + esc(label) + '</a>';
      last = re.lastIndex;
    }
    return out + esc(text.slice(last));
  }

  function loadChannels() {
    try {
      var v = JSON.parse(localStorage.getItem(STORE_KEY) || 'null');
      if (Array.isArray(v) && v.length) return v.filter(function (c) { return /^#[A-Za-z0-9_\-]{1,32}$/.test(c) && !/^#multi_/.test(c); });
    } catch (e) { /* storage unavailable */ }
    return ['#osu'];
  }
  function saveChannels() {
    if (!S) return;
    var list = [];
    S.tabs.forEach(function (t) { if (t.type === 'channel') list.push(t.name); });
    try { localStorage.setItem(STORE_KEY, JSON.stringify(list)); } catch (e) { /* ignore */ }
  }

  function api(url, opts) {
    return fetch(url, Object.assign({ credentials: 'same-origin', headers: { 'Accept': 'application/json' } }, opts || {}))
      .then(function (r) { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); });
  }

  // ---------- users (IRC knows names, avatars need ids)
  function knowUser(name, id) {
    if (!name || !id) return;
    S.users[safe(name)] = { id: id, name: name };
  }
  function userOf(name) { return S.users[safe(name)] || null; }
  function resolveUsers(names) {
    var missing = [];
    names.forEach(function (n) {
      var k = safe(n);
      if (k && !S.users[k] && !S.resolving[k]) { S.resolving[k] = true; missing.push(k); }
    });
    if (!missing.length) return Promise.resolve();
    return api('/api/v1/chat/users?names=' + encodeURIComponent(missing.join(','))).then(function (res) {
      Object.keys(res).forEach(function (k) { S.users[k] = res[k]; });
      missing.forEach(function (k) { delete S.resolving[k]; });
      applyAvatars();
    }).catch(function () { missing.forEach(function (k) { delete S.resolving[k]; }); });
  }
  function avatarUrl(id) { return S.avatar + '/' + id + (S.avatarV ? '?v=' + S.avatarV : ''); }
  function applyAvatars() {
    S.root.querySelectorAll('img[data-chat-user]').forEach(function (img) {
      var u = userOf(img.getAttribute('data-chat-user'));
      if (u && !img.getAttribute('src')) img.src = avatarUrl(u.id);
    });
    S.root.querySelectorAll('a[data-chat-user-link]').forEach(function (a) {
      var u = userOf(a.getAttribute('data-chat-user-link'));
      if (u) { a.href = '/u/' + u.id; a.textContent = u.name; }
    });
  }

  // ---------- tabs
  function tabKey(type, name) { return type === 'pm' ? '@' + safe(name) : name.toLowerCase(); }
  function getTab(type, name, create) {
    var key = tabKey(type, name);
    var tab = S.tabs.get(key);
    if (!tab && create) {
      tab = {
        key: key, type: type, name: name, target: type === 'pm' ? safe(name) : name,
        messages: [], unread: 0, historyLoaded: false, more: false, oldest: null, topic: ''
      };
      S.tabs.set(key, tab);
      if (type === 'pm' && safe(name) === safe(BOT)) tab.name = BOT;
    }
    return tab;
  }
  function channelType(name) { return /^#multi_/i.test(name) ? 'multi' : 'channel'; }

  function removeTab(tab) {
    S.tabs.delete(tab.key);
    if (S.active === tab.key) {
      var first = S.tabs.keys().next();
      S.active = first.done ? null : first.value;
    }
    saveChannels();
    renderSide();
    renderActive();
  }

  function activate(key) {
    var tab = S.tabs.get(key);
    if (!tab) return;
    S.active = key;
    tab.unread = 0;
    renderSide();
    renderActive(true);
    loadHistory(tab);
    markRead(tab);
    if (window.innerWidth >= 768) S.text.focus();
  }

  // ---------- messages
  function addMessage(tab, msg, opts) {
    opts = opts || {};
    if (opts.prepend) tab.messages = msg.concat(tab.messages);
    else { tab.messages.push(msg); if (msg.from) tab.lastTime = msg.time; }
    if (tab.messages.length > 600) tab.messages.splice(0, tab.messages.length - 600);
    if (!opts.prepend && msg.from) resolveUsers([msg.from]);
    var isActive = S.active === tab.key;
    if (!opts.prepend && !opts.self && msg.from && (!isActive || document.hidden)) {
      tab.unread++;
      renderSide();
    }
    if (isActive) {
      if (opts.prepend) renderActive(false, true);
      else appendToLog(tab, msg);
      if (!opts.self && msg.from) markRead(tab);
    }
  }
  function system(text, tab) {
    tab = tab || S.tabs.get(S.active);
    if (!tab) { setStatus(text, true); return; }
    addMessage(tab, { system: true, msg: text, time: nowSec() });
  }

  var readTimers = {};
  function markRead(tab) {
    if (tab.type !== 'pm' || document.hidden) return;
    var u = userOf(tab.name);
    if (!u) return;
    clearTimeout(readTimers[tab.key]);
    readTimers[tab.key] = setTimeout(function () {
      fetch('/api/v1/chat/read?user=' + u.id, { method: 'POST', credentials: 'same-origin' })
        .then(function () { if (window.shiinaChatUnread) window.shiinaChatUnread(true); })
        .catch(function () {});
    }, 600);
  }

  function loadHistory(tab, older) {
    if (tab.historyLoaded && !older) return;
    if (tab.loading) return;
    if (tab.type === 'multi') { tab.historyLoaded = true; return; }
    tab.loading = true;
    var p;
    if (tab.type === 'channel') {
      p = api('/api/v1/chat/channel?name=' + encodeURIComponent(tab.name)).then(function (list) {
        var live = {};
        tab.messages.forEach(function (m) { live[safe(m.from) + '\n' + m.msg] = true; });
        var hist = list.filter(function (h) { return !live[safe(h.from) + '\n' + h.msg]; }).map(function (h) {
          knowUser(h.from, h.from_id);
          return { from: h.from, msg: h.msg, time: h.time, history: true };
        });
        if (hist.length) addMessage(tab, hist, { prepend: true });
      });
    } else {
      p = resolveUsers([tab.name]).then(function () {
        var u = userOf(tab.name);
        if (!u) { if (safe(tab.name) !== safe(BOT)) system(T.unknownUser, tab); return; }
        if (safe(tab.name) === safe(BOT)) return;
        var url = '/api/v1/chat/history?user=' + u.id + (older && tab.oldest ? '&before=' + tab.oldest : '');
        return api(url).then(function (res) {
          var live = {};
          if (!older) tab.messages.forEach(function (m) { live[safe(m.from) + '\n' + m.msg] = true; });
          var hist = [];
          res.messages.forEach(function (h) {
            var fromName = h.from_id === S.me.id ? S.me.name : u.name;
            if (!older && live[safe(fromName) + '\n' + h.msg]) return;
            hist.push({ from: fromName, msg: h.msg, time: h.time, history: true });
          });
          if (res.messages.length) tab.oldest = res.messages[0].id;
          tab.more = res.more;
          if (hist.length) addMessage(tab, hist, { prepend: true });
          else if (S.active === tab.key) renderActive(false, true);
        });
      });
    }
    p.catch(function () {}).then(function () {
      tab.loading = false;
      tab.historyLoaded = true;
      if (S.active === tab.key) renderHead();
    });
  }

  // ---------- rendering
  function sideItem(tab) {
    var b = document.createElement('button');
    b.type = 'button';
    b.className = 'shiina-chat-item' + (S.active === tab.key ? ' active' : '') + (tab.unread ? ' unread' : '');
    var icon;
    if (tab.type === 'pm') {
      var u = userOf(tab.name);
      icon = '<img class="shiina-chat-avatar" alt="" data-chat-user="' + esc(tab.name) + '"' + (u ? ' src="' + esc(avatarUrl(u.id)) + '"' : '') + '>';
    } else {
      icon = '<span class="shiina-chat-icon">' + (tab.type === 'multi' ? '<i class="fa-solid fa-gamepad"></i>' : '#') + '</span>';
    }
    var label = tab.type === 'channel' ? tab.name.replace(/^#/, '') : (tab.type === 'multi' ? tab.name : (userOf(tab.name) || { name: tab.name }).name);
    b.innerHTML = icon + '<span class="shiina-chat-label">' + esc(label) + '</span>' +
      (tab.unread ? '<span class="shiina-chat-badge">' + (tab.unread > 99 ? '99+' : tab.unread) + '</span>' : '');
    b.addEventListener('click', function () { activate(tab.key); });
    return b;
  }
  function renderSide() {
    var lists = { channel: S.listChannel, multi: S.listMulti, pm: S.listPm };
    Object.keys(lists).forEach(function (k) { lists[k].innerHTML = ''; });
    var pms = [];
    S.tabs.forEach(function (tab) {
      if (tab.type === 'pm') pms.push(tab);
      else lists[tab.type].appendChild(sideItem(tab));
    });
    // BanchoBot first, then the rest in the order they were added (newest conversations first)
    pms.sort(function (a, b) {
      var ab = safe(a.name) === safe(BOT), bb = safe(b.name) === safe(BOT);
      if (ab !== bb) return ab ? -1 : 1;
      return (b.lastTime || 0) - (a.lastTime || 0);
    });
    pms.forEach(function (tab) { S.listPm.appendChild(sideItem(tab)); });
    S.sectionMulti.classList.toggle('d-none', !S.listMulti.children.length);
  }

  function renderHead() {
    var tab = S.tabs.get(S.active);
    if (!tab) { S.head.innerHTML = ''; return; }
    var html;
    if (tab.type === 'pm') {
      var u = userOf(tab.name);
      html = '<img class="shiina-chat-avatar lg" alt="" data-chat-user="' + esc(tab.name) + '"' + (u ? ' src="' + esc(avatarUrl(u.id)) + '"' : '') + '>' +
        '<div class="flex-grow-1 min-w-0"><div class="fw-bold text-truncate">' + esc(u ? u.name : tab.name) + '</div></div>' +
        (u && safe(tab.name) !== safe(BOT) ? '<a class="btn btn-sm btn-outline-secondary" href="/u/' + u.id + '">' + esc(T.profile) + '</a>' : '');
    } else {
      html = '<span class="shiina-chat-icon lg">' + (tab.type === 'multi' ? '<i class="fa-solid fa-gamepad"></i>' : '#') + '</span>' +
        '<div class="flex-grow-1 min-w-0"><div class="fw-bold text-truncate">' + esc(tab.name) + '</div>' +
        (tab.topic ? '<div class="small text-secondary text-truncate">' + esc(tab.topic) + '</div>' : '') + '</div>' +
        (tab.name.toLowerCase() !== '#osu' ? '<button type="button" class="btn btn-sm btn-outline-secondary" data-chat-leave>' + esc(T.leave) + '</button>' : '');
    }
    S.head.innerHTML = html;
    var leave = S.head.querySelector('[data-chat-leave]');
    if (leave) leave.addEventListener('click', function () { send('PART ' + tab.name); removeTab(tab); });
  }

  function messageRows(tab) {
    var html = '';
    var group = null;
    function close() { if (group) { html += '</div></div>'; group = null; } }
    tab.messages.forEach(function (m) {
      if (m.system) {
        close();
        html += '<div class="shiina-chat-system">' + richText(m.msg) + '</div>';
        return;
      }
      var k = safe(m.from);
      if (!group || group.k !== k || m.time - group.last > 300) {
        close();
        var u = userOf(m.from);
        var mine = k === S.me.safe;
        html += '<div class="shiina-chat-group' + (mine ? ' mine' : '') + '">' +
          '<a class="shiina-chat-who" data-chat-user-link="' + esc(m.from) + '"' + (u ? ' href="/u/' + u.id + '"' : '') + '>' +
          '<img class="shiina-chat-avatar lg" alt="" data-chat-user="' + esc(m.from) + '"' + (u ? ' src="' + esc(avatarUrl(u.id)) + '"' : '') + '>' +
          '<span class="shiina-chat-nick">' + esc(u ? u.name : m.from) + '</span></a><div class="shiina-chat-bubbles">';
        group = { k: k, last: m.time };
      }
      group.last = m.time;
      var text = m.msg;
      var action = /^\x01ACTION (.*)\x01?$/.exec(text);
      html += '<div class="shiina-chat-bubble' + (m.history ? ' history' : '') + (action ? ' action' : '') + '">' +
        '<div class="shiina-chat-text">' + (action ? '* ' + richText(action[1].replace(/\x01$/, '')) : richText(text)) + '</div>' +
        '<div class="shiina-chat-time">' + fmtTime(m.time) + '</div></div>';
    });
    close();
    return html;
  }

  function renderActive(scrollBottom, keepPosition) {
    var tab = S.tabs.get(S.active);
    renderHead();
    updateInput();
    if (!tab) { S.log.innerHTML = ''; return; }
    var oldHeight = S.log.scrollHeight, oldTop = S.log.scrollTop;
    var top = '';
    if (tab.type === 'pm' && tab.more) top = '<div class="text-center my-2"><button type="button" class="btn btn-sm btn-outline-secondary" data-chat-older>' + esc(T.older) + '</button></div>';
    S.log.innerHTML = top + (tab.messages.length ? messageRows(tab) : '<div class="shiina-chat-empty">' + esc(T.empty) + '</div>');
    var older = S.log.querySelector('[data-chat-older]');
    if (older) older.addEventListener('click', function () { loadHistory(tab, true); });
    if (keepPosition && !scrollBottom && oldTop > 0) S.log.scrollTop = S.log.scrollHeight - oldHeight + oldTop;
    else S.log.scrollTop = S.log.scrollHeight;
  }

  function appendToLog(tab, msg) {
    var nearBottom = S.log.scrollHeight - S.log.scrollTop - S.log.clientHeight < 120;
    renderActive(false, !nearBottom);
    if (nearBottom || msg.from && safe(msg.from) === S.me.safe) S.log.scrollTop = S.log.scrollHeight;
  }

  function updateInput() {
    var tab = S.tabs.get(S.active);
    var ok = S.connected && !!tab;
    S.text.disabled = !ok;
    S.sendBtn.disabled = !ok;
  }

  function setStatus(text, show) {
    S.status.textContent = text || '';
    S.status.classList.toggle('d-none', !show);
  }

  // ---------- IRC
  function send(line) {
    if (S && S.ws && S.ws.readyState === 1) S.ws.send(line);
  }

  function parse(line) {
    var m = IRC_LINE.exec(line);
    if (!m) return null;
    var prefix = m[1] || '';
    var nick = prefix.split('!')[0];
    var rest = m[3] || '';
    var params = [], trailing = null;
    var idx = rest.indexOf(' :');
    if (rest.charAt(0) === ':') { trailing = rest.slice(1); rest = ''; }
    else if (idx >= 0) { trailing = rest.slice(idx + 2); rest = rest.slice(0, idx); }
    if (rest) params = rest.split(' ').filter(Boolean);
    return { nick: nick, cmd: m[2].toUpperCase(), params: params, trailing: trailing };
  }

  function handleLine(line) {
    var p = parse(line);
    if (!p) return;
    var isMe = safe(p.nick) === S.me.safe;
    switch (p.cmd) {
      case '001':
        S.connected = true;
        S.retry = 0;
        setStatus('', false);
        S.wanted.forEach(function (c) { send('JOIN ' + c); });
        updateInput();
        break;
      case 'JOIN': {
        var chan = p.trailing || p.params[0];
        if (!chan || !isMe) break;
        var tab = getTab(channelType(chan), chan, true);
        if (tab.type === 'channel') saveChannels();
        renderSide();
        if (!S.active || S.pendingJoin === chan.toLowerCase()) { S.pendingJoin = null; activate(tab.key); }
        else loadHistory(tab);
        break;
      }
      case 'PART': {
        var pc = p.params[0] || p.trailing;
        if (!pc || !isMe) break;
        var pt = S.tabs.get(pc.toLowerCase());
        if (pt) removeTab(pt);
        break;
      }
      case 'PRIVMSG': {
        var target = p.params[0] || '';
        var text = p.trailing == null ? '' : p.trailing;
        if (isMe) break; // our own messages are shown when sent
        var t;
        if (target.charAt(0) === '#') t = getTab(channelType(target), target, true);
        else t = getTab('pm', p.nick, true);
        renderSide();
        addMessage(t, { from: p.nick, msg: text, time: nowSec() });
        if (safe(p.nick) === safe(BOT)) {
          var mm = /join (#multi_\d+)/i.exec(text);
          if (mm && !S.tabs.get(mm[1].toLowerCase())) { S.pendingJoin = mm[1].toLowerCase(); send('JOIN ' + mm[1]); }
        }
        break;
      }
      case 'NOTICE':
        if (p.trailing) system(p.trailing);
        break;
      case '332': {
        var ch = p.params[p.params.length - 1];
        var tt = ch && S.tabs.get(ch.toLowerCase());
        if (tt) { tt.topic = p.trailing || ''; if (S.active === tt.key) renderHead(); }
        break;
      }
      case '322':
        S.list.push({ name: p.params[1], users: p.params[2], topic: p.trailing || '' });
        break;
      case '323':
        renderJoinList();
        break;
      case 'ERROR':
        S.fatal = true;
        setStatus(p.trailing || T.disconnected, true);
        break;
      default:
        if (/^4\d\d$/.test(p.cmd) && p.trailing) system(p.trailing);
    }
  }

  function connect() {
    if (!S) return;
    var my = S;
    setStatus(S.retry ? T.reconnecting : T.connecting, true);
    api('/api/v1/chat/ticket', { method: 'POST' }).then(function (res) {
      if (S !== my) return;
      var base = S.root.getAttribute('data-ws') || ((location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws/chat');
      var ws = new WebSocket(base + '?ticket=' + encodeURIComponent(res.ticket));
      S.ws = ws;
      ws.onmessage = function (e) { if (S === my) String(e.data).split(/\r?\n/).forEach(function (l) { if (l) handleLine(l); }); };
      ws.onclose = function () {
        if (S !== my) return;
        S.connected = false;
        updateInput();
        scheduleReconnect();
      };
    }).catch(function () { if (S === my) scheduleReconnect(); });
  }
  function scheduleReconnect() {
    if (!S || S.fatal) return;
    var delay = Math.min(30000, 1000 * Math.pow(2, S.retry++));
    setStatus(T.reconnecting, true);
    clearTimeout(S.retryTimer);
    S.retryTimer = setTimeout(connect, delay);
  }

  // ---------- input
  function submit(e) {
    e.preventDefault();
    var tab = S.tabs.get(S.active);
    var text = S.text.value.trim();
    if (!text || !tab || !S.connected) return;
    S.text.value = '';
    var m;
    if ((m = /^\/(join|j) +(#\S+)$/i.exec(text))) { S.pendingJoin = m[2].toLowerCase(); send('JOIN ' + m[2]); return; }
    if (/^\/(part|leave|close)$/i.test(text)) {
      if (tab.type !== 'pm') send('PART ' + tab.name);
      removeTab(tab);
      return;
    }
    if ((m = /^\/(query|msg) +(\S+)(?: +(.+))?$/i.exec(text))) {
      var pm = getTab('pm', m[2], true);
      renderSide();
      activate(pm.key);
      if (m[3]) { send('PRIVMSG ' + pm.target + ' :' + m[3]); addMessage(pm, { from: S.me.name, msg: m[3], time: nowSec() }, { self: true }); }
      return;
    }
    if (/^\/help$/i.test(text)) { system(T.help, tab); return; }
    if ((m = /^\/me +(.+)$/i.exec(text))) text = '\x01ACTION ' + m[1] + '\x01';
    else if (text.charAt(0) === '/') text = text.replace(/^\/np\b/i, '!np');
    send('PRIVMSG ' + tab.target + ' :' + text);
    addMessage(tab, { from: S.me.name, msg: text, time: nowSec() }, { self: true });
  }

  function openPm(name) {
    name = String(name || '').trim();
    if (!name) return;
    var tab = getTab('pm', name, true);
    renderSide();
    activate(tab.key);
  }

  function renderJoinList() {
    var box = S.root.querySelector('#chat-join-list');
    var items = S.list.filter(function (c) { return c.name && !/^#multi_/i.test(c.name); });
    if (!items.length) { box.innerHTML = '<div class="text-secondary small">' + esc(T.noChannels) + '</div>'; return; }
    box.innerHTML = '';
    items.forEach(function (c) {
      var joined = S.tabs.has(c.name.toLowerCase());
      var b = document.createElement('button');
      b.type = 'button';
      b.className = 'shiina-chat-join-item' + (joined ? ' joined' : '');
      b.innerHTML = '<span class="fw-bold">' + esc(c.name) + '</span><span class="small text-secondary ms-2">' + esc(c.users) + ' ' + esc(T.users) + '</span>' +
        (c.topic ? '<div class="small text-secondary">' + esc(c.topic) + '</div>' : '');
      b.addEventListener('click', function () {
        S.joinPanel.classList.add('d-none');
        var key = c.name.toLowerCase();
        if (S.tabs.has(key)) { activate(key); return; }
        S.pendingJoin = key;
        send('JOIN ' + c.name);
      });
      box.appendChild(b);
    });
  }

  // ---------- mount / unmount
  function mount() {
    var root = document.getElementById('shiina-chat');
    if (!root || (S && S.root === root)) return;
    unmount();
    var meName = root.getAttribute('data-me-name');
    S = {
      root: root,
      me: { id: parseInt(root.getAttribute('data-me-id'), 10), name: meName, safe: safe(meName) },
      avatar: root.getAttribute('data-avatar'),
      avatarV: root.getAttribute('data-avatar-v'),
      tabs: new Map(), active: null, users: {}, resolving: {}, list: [],
      wanted: loadChannels(), connected: false, retry: 0, fatal: false, ws: null,
      head: root.querySelector('#chat-head'), log: root.querySelector('#chat-log'),
      status: root.querySelector('#chat-status'), text: root.querySelector('#chat-text'),
      sendBtn: root.querySelector('#chat-send'),
      listChannel: root.querySelector('#chat-list-channel'), listMulti: root.querySelector('#chat-list-multi'),
      listPm: root.querySelector('#chat-list-pm'), sectionMulti: root.querySelector('#chat-section-multi'),
      joinPanel: root.querySelector('#chat-join-panel')
    };
    knowUser(S.me.name, S.me.id);

    root.querySelector('#chat-form').addEventListener('submit', submit);
    root.querySelector('#chat-new-pm').addEventListener('submit', function (e) {
      e.preventDefault();
      var input = root.querySelector('#chat-new-pm-name');
      openPm(input.value);
      input.value = '';
    });
    root.querySelector('#chat-join-btn').addEventListener('click', function () {
      S.list = [];
      root.querySelector('#chat-join-list').innerHTML = '<div class="text-secondary small">...</div>';
      S.joinPanel.classList.remove('d-none');
      send('LIST');
    });
    root.querySelector('#chat-join-close').addEventListener('click', function () { S.joinPanel.classList.add('d-none'); });
    S.log.addEventListener('scroll', function () {
      var tab = S.tabs.get(S.active);
      if (tab && tab.type === 'pm' && tab.more && !tab.loading && S.log.scrollTop < 40) loadHistory(tab, true);
    });
    S.onVisible = function () {
      if (!document.hidden && S) { var t = S.tabs.get(S.active); if (t) { t.unread = 0; renderSide(); markRead(t); } }
    };
    document.addEventListener('visibilitychange', S.onVisible);

    getTab('pm', BOT, true);
    var openWith = root.getAttribute('data-open-with');
    api('/api/v1/chat/conversations').then(function (list) {
      list.forEach(function (c) {
        knowUser(c.name, c.id);
        var tab = getTab('pm', c.name, true);
        tab.unread = c.unread || 0;
        tab.lastTime = Math.max(tab.lastTime || 0, c.time || 0);
      });
    }).catch(function () {}).then(function () {
      renderSide();
      if (openWith) openPm(openWith);
    });

    S.pinger = setInterval(function () { send('PING shiina'); }, 30000);
    renderSide();
    renderActive();
    connect();
  }

  function unmount() {
    if (!S) return;
    var old = S;
    S = null;
    clearInterval(old.pinger);
    clearTimeout(old.retryTimer);
    document.removeEventListener('visibilitychange', old.onVisible);
    if (old.ws) { try { old.ws.send('QUIT :page closed'); } catch (e) { /* closed */ } try { old.ws.close(); } catch (e) { /* closed */ } }
  }

  window.ShiinaChat = { mount: mount, unmount: unmount };
  document.addEventListener('turbo:before-visit', function () { unmount(); });
  document.addEventListener('turbo:load', mount);
  window.addEventListener('pagehide', unmount);
  window.addEventListener('pageshow', function (e) { if (e.persisted) mount(); });
  mount();
})();
