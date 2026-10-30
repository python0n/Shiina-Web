placeholder
icon:fa-solid fa-plug
route:info
krz  :What is taksiegra?
title:What is taksiegra?
navbar:true
footer:true
behindLogin:false

> 🇵🇱 [Wersja polska](info-pl)

Honestly, on my own initiative, I wanted to try setting up my own homelab - as you can see at https://taksiegra.ovh/changelog, I eventually managed to pull it off. The name taksiegra was basically coined by Pioye_, as far as I can remember after submitting an insane score and a short discussion about what to name the site.

However, after the site was created, there wasn't much going on here - until I started experimenting with Cloudflare Tunnel. Using that, and of course AI, I managed to set up an osu! server called taksiegra, using nginx as a reverse proxy and CNAME records in DNS. The first version took me about three days over Christmas 2025 (roughly 22.5 hours of actual work), and in the end everything works great. I handle the development myself, occasionally testing things with AI.

## How the osu! server was built

All times below are Polish time, taken from my chat history from those days.

### The starting point

My home connection is behind CGNAT, so there is no public IP and no port forwarding. The official osu!NoLimits guide expects A records pointing at the server's IP, which I couldn't do. Instead, every subdomain (`osu`, `api`, `c`, `c4`, `a`, later `b`) goes through Cloudflare Tunnel as a CNAME, and nginx on the server sorts the traffic out.

The stack:

- **bancho.py-ex** in Docker (bancho, MySQL, Redis), API on port 10000
- **Shiina-Web** (Java, Maven) as the website, on port 3010
- **nginx** as the reverse proxy for everything
- **Cloudflare Tunnel** instead of a public IP

### Night of 24-25 December - from zero to the first login (23:23-03:22)

On Christmas Eve I came back to an old idea: an earlier attempt at an osu! server had failed on networking, not on code. This time the plan was Cloudflare Tunnel plus the osu!NoLimits guide, followed 1:1 with Docker.

- **00:05** - first vhost for `osu.taksiegra.ovh` answers through nginx.
- **00:14** - the tunnel catches `*.taksiegra.ovh` and forwards it to nginx.
- **00:38** - `make run`: bancho, MySQL and Redis start, and the API answers through Cloudflare.
- **00:50** - Shiina-Web builds after installing Maven, and the site is up.
- **01:09** - MySQL and Redis locked down to `127.0.0.1` after a few broken attempts at editing `docker-compose.yml`.
- **01:54** - first login with the game client using `-devserver osu.taksiegra.ovh`. My reaction at the time: *"WE GOT IT"* (with a few extra words).
- **02:13** - avatars on `a.taksiegra.ovh`, with a default avatar for every new account.
- **02:41** - everything moved to `/mnt/storage/osu/` so I can reach the files from Windows.
- **03:22** - favicon added, and bed.

### 25 December - the 13-hour marathon (13:21 - 02:19)

The longest day. Website and avatars in the morning, osu!direct in the afternoon, and score submission late into the night.

- **13:33-14:54** - changing avatars on the site crashed. I fixed the file-extension check in the avatar handler (two failed compiles because of brackets), then fixed avatars not showing on profiles by changing `AVATARSRV`.
- **15:00-15:24** - bancho and Shiina-Web turned into systemd services, so they start on boot.
- **15:27-15:47** - avatars not showing inside the game client: the client asks for `/3`, not `/avatars/3`. Fixed in nginx.
- **15:53-17:04** - osu!direct was stuck on "Searching". Two causes: Redis kept getting killed by the system (fixed with `vm.overcommit_memory=1`), and the `/web/` router existed in the code but was never plugged into the API.
- **17:07-18:11** - bancho redirected map downloads to osu.direct, which downgraded HTTPS to HTTP. I built my own download proxy in nginx at `c.taksiegra.ovh/d/`.
- **18:25-19:18** - maps finally showed up in osu!direct, but after a dozen plays everything was still at 0 and scores weren't submitting.
- **19:45-20:06** - downloaded maps were "corrupt". The mirror was sending the client a small page instead of the `.osz` file because of the `osu!` user agent. Fixed by sending a different user agent from the proxy.
- **20:13-23:26** - a long fight with score submission: an `osu-session.php` stub in nginx, many versions of the config, and validation errors (422) mixed with the first successful scores.
- **00:01** - first saved score: 14.29pp. Soon after, 92.34pp.
- **01:03** - submission confirmed working: *"submitted a score! (Best, 118.05pp / 265pp)"*. The key was letting the submit selector go through to bancho instead of answering it directly in nginx.
- **01:16-02:19** - map downloads were still coming out corrupt. Left for the next day.

### 26 December - downloads, previews and backgrounds (14:08-21:33)

- **14:08-14:40** - a new full nginx config broke submission for a moment. I merged it with the working version, and submission came back.
- **14:54-16:01** - downloads fixed for good: a backup mirror when the main one returns 451/404, and corrected `Content-Disposition` and `Accept-Ranges` headers. Maps stopped landing in the "Failed" folder.
- **16:09-17:51** - new subdomain `b.taksiegra.ovh` for osu!direct music previews, backgrounds and thumbnails (CNAME through the tunnel again).
- **19:07-20:47** - backgrounds now come from assets.ppy.sh, and thumbnails are resized to 80×60. A few of the first tiles stayed too big because of the client's cache, so I left it at that.
- **20:51-21:33** - an attempt at IRC through ZeroTier on port 6667. With IRC enabled, bancho wouldn't start (`socket.gaierror`) and the container kept restarting, so I dropped it for now.

### Time spent

About **22.5 hours** in total, over five sessions:

- **Session 1:** 24.12, 23:23 - 25.12, 03:22 (4 h)
- **Session 2:** 25.12, 13:21 - 26.12, 02:19 (13 h)
- **Session 3:** 26.12, 14:08 - 17:51 (3 h)
- **Session 4:** 26.12, 19:07 - 21:33 (2.5 h)
- **Session 5:** 27.12, 00:35 - 00:53, writing the changelog (20 min)

### What happened with IRC later

IRC came back about seven weeks later. On **12 February 2026** I decided to go with IRC instead of a WebSocket, because the server was about to get a public IP, and CGNAT was exactly what had blocked port 6667 in December. By **6 March 2026** it worked: `!mp make` and `!mp close` from an IRC client, chat sync with `#osu` and `#multi_*`, match timers, and a lot of crash fixes. By **12 March** it was polished (login, BanchoBot replies after reconnecting, duplicate messages).

### Where it ended up

After those three days the server was ready for players: logging in, the website, avatars, osu!direct with downloads, previews and backgrounds, and score submission with pp. IRC joined in March 2026. Everything after that is in the [changelog](https://taksiegra.ovh/changelog).
