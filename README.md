# **Shiina-Web** — taksiegra.ovh fork

![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/python0n/Shiina-Web/maven.yml?branch=dev&label=Tests&color=1783a3)
![GitHub last commit](https://img.shields.io/github/last-commit/python0n/Shiina-Web/dev?label=Last%20commit&color=1783a3)
![GitHub License](https://img.shields.io/github/license/python0n/Shiina-Web?label=License&color=1783a3)
![Static Badge](https://img.shields.io/badge/upstream-osu--NoLimits%2FShiina--Web-1783a3)

A Java-based web frontend for **bancho.py-ex** osu! private servers, with extensive features and plugin support.

> **This is a fork.** The original project is
> [osu-NoLimits/Shiina-Web](https://github.com/osu-NoLimits/Shiina-Web) by
> **osu!NoLimits**, released under the MIT License. This fork powers
> [osu.taksiegra.ovh](https://osu.taksiegra.ovh) and adds features specific to
> that server. All upstream code remains under its original license and
> copyright — see [LICENSE](/LICENSE).
>
> For general installation and theming, use the
> [upstream documentation](https://osu-nolimits.github.io/wiki/) — this fork does
> not diverge in setup.

---

## **Feature List**

### Core Features (upstream)
- ✅ User authentication and authorization
- ✅ Homepage with server statistics
- ✅ User profiles with customizable userpages
- ✅ Profile picture changing
- ✅ Beatmap browsing and search
- ✅ Comprehensive leaderboard system
  - Global leaderboards
  - Country-specific leaderboards
  - Clan leaderboards with competitive statistics
- ✅ Score tracking and display
  - First place scores
  - Personal best scores
  - Most recent scores
  - Playcount graphs
- ✅ Admin panel with extensible functionality

### Customization & Extensibility (upstream)
- ✅ Good theming support — [documented here](https://osu-nolimits.github.io/wiki/theming/)
- ✅ Java plugin system with event hooks
- ✅ Extensive API integration
- ✅ Donation system (Ko-fi)

### Technical Features (upstream)
- ✅ API request caching for improved performance
- ✅ Configurable error and request logging
- ✅ Easy customization via `.config/customization.yml`
- ✅ Multiple webhook support

---

## **Added in this fork**

### Scores & profiles
- ✅ **Pinned Scores** — pin/unpin your own scores, drag & drop ordering, dedicated section above Best Scores
- ✅ **Score v3 tab** — lazer standardised score on beatmap leaderboards (osu!std), using the 2026-06 lazer mod multiplier rebalance
- ✅ **Badge system** — `user_badges` table, upload from the admin panel, badge grid on profiles with hover captions (tournament name + date), deletion and ordering
- ✅ **PP Era Hypo** (`/hypo`) — calculate hypothetical PP under previous ranking-system eras
- ✅ **Profile URL resolution** — `/users/<id>` → `/u/<id>`, and `@name` / `name` resolved to IDs, so `/u/<nick>` and `/users/@<nick>` both work
- ✅ **IRC key display** on the auth settings page, auto-generated when missing

### Replays
- ✅ **Replay Submission System** — players upload `.osr` files for admin review
- ✅ **Replay Analyzer** (`/analyzer`) — timing error and aim accuracy analysis with interactive Chart.js graphs, plus map recommendations by skill type (Stream / Jump / Burst / Tech)
- ✅ **Admin Replay Panel** — approve or reject submissions, download `.osr` files

### Multiplayer
- ✅ **Match History** — match list (`/matches`) and detailed view (`/matches/:id`) with full event log and score tables
- ✅ **Match visibility controls** — admin toggle to hide/show individual matches, with pagination
- ✅ Correct accuracy, grade and mods for match scores, computed from hit counters when absent in the database

### Beatmaps
- ✅ **Beatmap Submission System UI** — frontend for the bancho.py-ex BSS; submit maps straight from the osu! editor
- ✅ **Mapset grouping** on the beatmap listing — one tile per mapset instead of one per difficulty
- ✅ Beatmap listing sorted by most recently updated

### Misc
- ✅ **Score comments** — comment system on score pages
- ✅ **Bot & Docs pages** — Discord bot command reference and server documentation
- ✅ Avatar upload limit raised to 8 MB, with cache-busting on all avatar URLs
- ✅ One-year donor duration option
- ✅ Full English UI (upstream ships partially localised templates)

---

## 🔒 Security

This fork tracks upstream security patches. Most recently, the fixes from
upstream **2.2** ([#26](https://github.com/osu-NoLimits/Shiina-Web/pull/26)) were
backported: stored XSS in comments and clan names, missing `clan_priv`
authorization on clan actions and disbanding, `HttpOnly` + `Secure` session
cookies, and Freemarker auto-escaping.

Fork-specific fixes on top of that:

- Registration now stores passwords as `bcrypt(md5(password))`, matching what the
  osu! stable client sends. Accounts created before this fix are migrated
  automatically on their next web login.
- Build configuration updated for JDK 23+, where `javac` no longer runs
  annotation processors by default — Lombok is now declared explicitly via
  `annotationProcessorPaths`.

If you run a fork of this project, apply upstream's patch as well.

---

## 📋 Changelog

Full changelog with all updates: **[taksiegra.ovh/changelog/](https://taksiegra.ovh/changelog/)**

---

## **License**

MIT — see [LICENSE](/LICENSE).

Copyright for the original work belongs to **osu!NoLimits** (2024–2025). This
fork is distributed under the same license, with the original copyright notice
retained as MIT requires.

---

## **Credits**

Built on [Shiina-Web](https://github.com/osu-NoLimits/Shiina-Web) by osu!NoLimits
and its contributors:

<a href="https://github.com/osu-NoLimits/Shiina-Web/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=osu-NoLimits/Shiina-Web" />
</a>

Fork maintained by [python0n](https://github.com/python0n) for
[taksiegra.ovh](https://taksiegra.ovh).