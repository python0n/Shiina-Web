# Shiina-Web - taksiegra fork

![GitHub last commit](https://img.shields.io/github/last-commit/python0n/Shiina-Web/dev?label=Last%20commit&color=1783a3)
![GitHub License](https://img.shields.io/github/license/python0n/Shiina-Web?label=License&color=1783a3)
![Upstream](https://img.shields.io/badge/upstream-osu--NoLimits%2FShiina--Web-1783a3)

Web frontend of the [taksiegra](https://osu.taksiegra.ovh) osu! private server, a fork of
[Shiina-Web](https://github.com/osu-NoLimits/Shiina-Web) by osu!NoLimits. It runs next to our
fork of [bancho.py-ex](https://github.com/python0n/bancho.py-ex) and reads the same database.

Setup and theming work like upstream, so the [upstream wiki](https://osu-nolimits.github.io/wiki/)
still applies. This page lists what the fork changes and the extra settings it needs.

## What the fork adds

### Players and scores
- Pinned scores on profiles, with drag and drop ordering.
- Badges (`user_badges`): uploaded in the admin panel, shown on profiles with the event name and date.
- Profile links by id or nick: `/u/<id>`, `/u/<nick>`, `/users/<id>`, `/users/@<nick>`.
- In score based modes the #1, placements, personal bests and first places go by the highest score,
  the same rule bancho uses; Relax and Autopilot go by pp.
- Score comments.
- IRC key shown on the account settings page, created when missing.
- Active sessions list in the settings, with logging out of single sessions.

### Replays
- Replay submission: players upload `.osr` files, admins review them in the admin panel.
- Replay analyzer (`/analyzer`): timing error and aim per note, stability (UR), early and late hits,
  and map recommendations for the weakest skill (stream, jump, burst, tech).

### Beatmaps
- Interface for bancho.py-ex's beatmap submission (BSS): maps can be uploaded straight from the editor.
- One tile per mapset on the beatmap list, newest first.
- Map statuses follow bancho (Pending, Update available, Ranked, Approved, Qualified, Loved),
  with osu-web like colours.

### Multiplayer
- Match list (`/matches`) and match view (`/matches/<id>`, `/mp<id>`) with the full event log.
- Accuracy, grade and mods of match scores computed from hit counts.
- Admins can hide single matches.

### PP reworks
- `/reworks`: the server's scores recalculated with other pp formulas, from the pp-lab service.
- `/hypo`: pp of a score under earlier ranking eras.

### Server pages
- Documents written in Markdown (`docs/`, Polish and English versions): rules, how to connect,
  about the server, API reference.
- Discord bot command list.
- English and Polish interface (`lang/en.properties`, `lang/pl.properties`), switched with `/lang/<code>`.
  The text files can be edited on the server without rebuilding.

### Accounts and moderation
- Restricted players are hidden from everyone except staff: profile, scores, rankings, first places,
  comments and the public API. They cannot change their profile.
- Nick and password rules are the same as in bancho (`safe_name` must be unique, `_` and space
  cannot be mixed, blocked names and passwords from `.env`), with hints on the registration form.
- A warning before creating a second account, when the browser or the network already belongs
  to a player. Ignoring it is logged for the multi-account detection.
- Account deletion is a request that an admin accepts or rejects (`/ap/deletions`).
- Clan changes reach the game at once (Redis event to bancho).

### Security
- Player, clan and map names are escaped in templates and in score tiles built by JavaScript.
- Requests that change something (`/api/v1/*`, `/ap/*`, settings forms) are refused when the browser
  marks them as coming from another site. Following, clan actions and pinning are POST only.
- Only local redirects after logging in.
- Session cookies are `HttpOnly` and `Secure`; passwords are stored as `bcrypt(md5(password))`,
  matching what the osu! client sends (older accounts are converted on their next login).
- Upstream security fixes are backported (upstream 2.2, [#26](https://github.com/osu-NoLimits/Shiina-Web/pull/26)).

### Performance
- Static files and avatars carry a version in their address (`?v=`), so they can be cached for a long time.
- Chart.js, the WysiBB editor and select2 load only on the pages that use them.
- Connection leaks in the background jobs fixed (try-with-resources, also in the rank cache).

## Extra settings (`.config/.env`)

| Setting | What it does |
| --- | --- |
| `BANCHO_DATA_DIR` | bancho's `.data` folder (replays, `.osu` files) |
| `INTERNAL_API_KEY` | same value as in bancho's `.env`; lets Shiina see restricted players through the API |
| `DISALLOWED_NAMES`, `DISALLOWED_PASSWORDS` | comma separated, checked on registration and nick changes |
| `CRON_THREADS` | threads for the background jobs (default 3) |

Everything else is described in `.config/.env.example`.

## Changelog

[taksiegra.ovh/changelog](https://taksiegra.ovh/changelog/)

## License and credits

MIT, see [LICENSE](/LICENSE). The original work is copyright osu!NoLimits (2024-2025); this fork
keeps their notice and the same licence.

Built on [Shiina-Web](https://github.com/osu-NoLimits/Shiina-Web) by osu!NoLimits and its contributors:

<a href="https://github.com/osu-NoLimits/Shiina-Web/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=osu-NoLimits/Shiina-Web" alt="Shiina-Web contributors" />
</a>

Fork maintained by [python0n](https://github.com/python0n).
