placeholder
icon:fa-solid fa-code
route:api
krz  :API
title:API
navbar:true
footer:true
behindLogin:false
<div class="doc-api"></div>


The server has a public, read-only API for players, scores, beatmaps, clans and rankings. Use it for your own bot, overlay or stats page.

```text
https://api.taksiegra.ovh/v1
```

> [!NOTE]
> Every endpoint is a `GET` request returning JSON (except `get_replay`, which returns a file). An API key is only needed for `calculate_pp`.

## Basics

> [!WARNING]
> The API is open to everyone, but please don't flood it. If you're building something that queries the server more often than once every few seconds, talk to the admin first.

- Parameters go in the query string, e.g. `?id=3&mode=0`.
- Successful responses contain `"status": "success"`. On errors `status` holds a message and the HTTP code tells you what went wrong: `400` bad parameters, `401` missing or invalid key, `404` not found.
- Times (`creation_time`, `latest_activity`, `last_seen`, `login_time`) are unix seconds. Score dates (`play_time`) are UTC.

```json
{"status": "Player not found."}
```

A missing required parameter or an invalid value returns `422` with details in the `detail` field.

Pick a player with `id` **or** `name`, never both.

### Game modes

The `mode` parameter takes:

| Value | Mode |
|---|---|
| `0` | osu! |
| `1` | osu!taiko |
| `2` | osu!catch |
| `3` | osu!mania |
| `4` | osu! Relax |
| `5` | osu!taiko Relax |
| `6` | osu!catch Relax |
| `8` | osu! Autopilot |

Values `7`, `9`, `10` and `11` don't exist in the game, and endpoints returning scores or rankings answer `400` for them.

### Mods

The `mods` parameter takes acronyms (`HDDT`) or the osu! number (`72`). By default you get scores that include **all** of the given mods (they may have others too). With a `~` prefix (e.g. `~HDHR`) **any** of them is enough.

### Statuses

Beatmap `status`: `-1` not submitted, `0` pending, `1` update available, `2` ranked, `3` approved, `4` qualified, `5` loved.

Score `status`: `0` failed, `1` submitted, `2` the player's best score on the map.

## Players

### GET /v1/get_player_count

Players online and total accounts.

```bash
curl "https://api.taksiegra.ovh/v1/get_player_count"
```

```json
{"status": "success", "counts": {"online": 4, "total": 57}}
```

### GET /v1/online

Players currently in game. Bots are listed separately.

```bash
curl "https://api.taksiegra.ovh/v1/online"
```

```json
{"status": "success", "players": [{"id": 3, "name": "Python-"}], "bots": [{"id": 1, "name": "…"}]}
```

### GET /v1/search_players

Search players by part of their name.

| Parameter | Type | Description |
|---|---|---|
| `q` | text | Part of the name, 2–32 characters. Without it every player is returned. |

```bash
curl "https://api.taksiegra.ovh/v1/search_players?q=pyth"
```

```json
{"status": "success", "results": 1, "result": [{"id": 3, "name": "Python-"}]}
```

### GET /v1/get_player_info

A player's profile, statistics or both.

| Parameter | Type | Description |
|---|---|---|
| `id` / `name` | number / text | The player |
| `scope` | text | **Required.** `info`, `stats` or `all` |

```bash
curl "https://api.taksiegra.ovh/v1/get_player_info?name=Python-&scope=all"
```

```json
{
  "status": "success",
  "player": {
    "info": {
      "id": 3, "name": "Python-", "safe_name": "python-", "priv": 31, "country": "pl",
      "creation_time": 1766620800, "latest_activity": 1793376000, "clan_id": 2, "clan_priv": 3,
      "preferred_mode": 0, "play_style": 0, "custom_badge_name": null, "custom_badge_icon": null
    },
    "stats": {
      "0": {
        "mode": 0, "pp": 5579, "acc": 97.25, "plays": 4210, "playtime": 812345,
        "tscore": 9928111294, "rscore": 2104558120, "max_combo": 2450, "total_hits": 1830211,
        "replay_views": 12, "xh_count": 3, "x_count": 41, "sh_count": 120, "s_count": 380, "a_count": 610,
        "rank": 4, "country_rank": 3
      }
    }
  }
}
```

`stats` has one object per mode the player has played, keyed by the mode number.

### GET /v1/get_player_status

Whether a player is online and what they're doing. When the player is offline the response only has `online: false` and `last_seen`, as in the second example.

| Parameter | Type | Description |
|---|---|---|
| `id` / `name` | number / text | The player |

```bash
curl "https://api.taksiegra.ovh/v1/get_player_status?name=Python-"
```

```json
{
  "status": "success",
  "player_status": {
    "online": true,
    "login_time": 1793370000,
    "status": {"action": 2, "info_text": "Artist - Title [Insane]", "mode": 0, "mods": 8, "beatmap": {"id": 129891, "md5": "…"}}
  }
}
```

```json
{"status": "success", "player_status": {"online": false, "last_seen": 1793376000}}
```

### GET /v1/get_player_scores

A player's recent or best scores.

| Parameter | Type | Description |
|---|---|---|
| `id` / `name` | number / text | The player |
| `scope` | text | **Required.** `recent` (newest first) or `best` (highest pp first, ranked and approved maps only) |
| `mode` | number | Mode, default `0` |
| `mods` | text | Mod filter, see [Mods](#mods) |
| `limit` | number | 1–100, default 25 |
| `include_loved` | true/false | For `best`: include loved maps. Default `false` |
| `include_failed` | true/false | For `recent`: include failed plays. Default `true` |

```bash
curl "https://api.taksiegra.ovh/v1/get_player_scores?id=3&scope=best&mode=0&limit=5"
```

```json
{
  "status": "success",
  "scores": [
    {
      "id": 51234, "score": 48211450, "pp": 412.7, "acc": 98.91, "max_combo": 1820, "mods": 72,
      "n300": 1402, "n100": 12, "n50": 0, "nmiss": 0, "ngeki": 301, "nkatu": 9,
      "grade": "SH", "status": 2, "mode": 0, "play_time": "2026-10-16T19:42:11", "time_elapsed": 214000, "perfect": 0,
      "beatmap": {"id": 129891, "set_id": 39804, "md5": "…", "artist": "…", "title": "…", "version": "…", "status": 2}
    }
  ],
  "player": {"id": 3, "name": "Python-", "clan": {"id": 2, "name": "Taksiegrajejsi", "tag": "TSG"}}
}
```

### GET /v1/get_player_most_played

A player's most played beatmaps.

| Parameter | Type | Description |
|---|---|---|
| `id` / `name` | number / text | The player |
| `mode` | number | Mode, default `0` |
| `limit` | number | 1–100, default 25 |

```bash
curl "https://api.taksiegra.ovh/v1/get_player_most_played?id=3&mode=0&limit=5"
```

```json
{"status": "success", "maps": [{"md5": "…", "id": 129891, "set_id": 39804, "status": 2, "artist": "…", "title": "…", "version": "…", "creator": "…", "plays": 87}]}
```

## Beatmaps

### GET /v1/get_map_info

A beatmap's details.

| Parameter | Type | Description |
|---|---|---|
| `id` / `md5` | number / text | Difficulty ID or its MD5 hash |

```bash
curl "https://api.taksiegra.ovh/v1/get_map_info?id=129891"
```

```json
{
  "status": "success",
  "map": {
    "md5": "…", "id": 129891, "set_id": 39804, "artist": "…", "title": "…", "version": "…", "creator": "…",
    "last_update": "2014-05-18T15:41:48", "total_length": 238, "max_combo": 1820, "status": 2,
    "plays": 312, "passes": 140, "mode": 0, "bpm": 180, "cs": 4, "od": 8, "ar": 9, "hp": 6, "diff": 5.62
  }
}
```

### GET /v1/get_map_scores

A beatmap's leaderboard.

| Parameter | Type | Description |
|---|---|---|
| `id` / `md5` | number / text | The beatmap |
| `scope` | text | **Required.** `best` or `recent` |
| `mode` | number | Mode, default `0` |
| `mods` | text | Mod filter |
| `limit` | number | 1–100, default 50 |

With `best`, normal modes are sorted by score and Relax and Autopilot by pp. Each player appears once, with the play that gives them the most pp on the map.

```bash
curl "https://api.taksiegra.ovh/v1/get_map_scores?id=129891&scope=best&mode=0"
```

```json
{
  "status": "success",
  "scores": [
    {
      "map_md5": "…", "score": 48211450, "pp": 412.7, "acc": 98.91, "max_combo": 1820, "mods": 72,
      "n300": 1402, "n100": 12, "n50": 0, "nmiss": 0, "ngeki": 301, "nkatu": 9, "grade": "SH", "status": 2,
      "mode": 0, "play_time": "2026-10-16T19:42:11", "time_elapsed": 214000, "userid": 3, "perfect": 0,
      "player_name": "Python-", "player_country": "pl", "clan_id": 2, "clan_name": "Taksiegrajejsi", "clan_tag": "TSG"
    }
  ]
}
```

### GET /v1/calculate_pp

Calculates pp and star rating for a beatmap. **Requires an API key.**

> [!TIP] Getting a key
> Send `!apikey` to the server bot in a private message in game. The bot replies with a link containing your key. A new key replaces the old one.

Send the key in a header:

```text
Authorization: Bearer YOUR_KEY
```

| Parameter | Type | Description |
|---|---|---|
| `id` | number | Difficulty ID |
| `mode` | number | Mode, default `0` |
| `mods` | number | Mods as a number, default `0` |
| `acc` | number | Accuracy. Can be given several times. Default 100, 99, 98 and 95 |
| `combo` | number | Combo, default full combo |
| `misses` | number | Misses, default 0 |
| `n300`, `n100`, `n50`, `ngeki`, `nkatu` | number | Exact hit counts instead of `acc` |

With `acc` you get a list, one result per accuracy. With exact hit counts you get a single result.

```bash
curl -H "Authorization: Bearer YOUR_KEY" "https://api.taksiegra.ovh/v1/calculate_pp?id=129891&mods=72&acc=100&acc=98"
```

```json
[
  {"performance": {"pp": 512.3, "pp_aim": 240.1, "pp_speed": 198.7, "pp_acc": 61.2}, "difficulty": {"stars": 7.21, "aim": 3.52, "speed": 3.31}, "accuracy": 100},
  {"performance": {"pp": 455.8, "pp_aim": 231.4, "pp_speed": 176.9, "pp_acc": 38.5}, "difficulty": {"stars": 7.21, "aim": 3.52, "speed": 3.31}, "accuracy": 98}
]
```

## Scores

### GET /v1/get_score_info

Full details of a single score.

| Parameter | Type | Description |
|---|---|---|
| `id` | number | **Required.** Score ID |

```bash
curl "https://api.taksiegra.ovh/v1/get_score_info?id=51234"
```

### GET /v1/get_replay

Downloads a replay as an `.osr` file you can open in osu!.

| Parameter | Type | Description |
|---|---|---|
| `id` | number | **Required.** Score ID |
| `include_headers` | true/false | Default `true` for a complete `.osr` file. `false` returns only the raw replay frames |

```bash
curl -OJ "https://api.taksiegra.ovh/v1/get_replay?id=51234"
```

## Rankings and clans

### GET /v1/get_leaderboard

The player ranking.

| Parameter | Type | Description |
|---|---|---|
| `mode` | number | Mode, default `0` |
| `sort` | text | `pp` (default), `tscore`, `rscore`, `acc`, `plays` or `playtime` |
| `country` | text | Country code, e.g. `pl` |
| `limit` | number | 1–100, default 50 |
| `offset` | number | How many entries to skip, for paging |

```bash
curl "https://api.taksiegra.ovh/v1/get_leaderboard?mode=0&sort=pp&limit=3"
```

```json
{
  "status": "success",
  "leaderboard": [
    {
      "player_id": 3, "name": "Python-", "country": "pl", "pp": 5579, "acc": 97.25, "plays": 4210, "playtime": 812345,
      "tscore": 9928111294, "rscore": 2104558120, "max_combo": 2450,
      "xh_count": 3, "x_count": 41, "sh_count": 120, "s_count": 380, "a_count": 610,
      "clan_id": 2, "clan_name": "Taksiegrajejsi", "clan_tag": "TSG"
    }
  ]
}
```

### GET /v1/get_clan

A clan with its members. This response has no `status` field.

| Parameter | Type | Description |
|---|---|---|
| `id` | number | **Required.** Clan ID |

```bash
curl "https://api.taksiegra.ovh/v1/get_clan?id=2"
```

```json
{
  "id": 2, "name": "Taksiegrajejsi", "tag": "TSG",
  "members": [{"id": 3, "name": "Python-", "country": "pl", "rank": "Owner"}],
  "owner": {"id": 3, "name": "Python-", "country": "pl", "rank": "Owner"}
}
```

## Multiplayer and tournaments

### GET /v1/get_match

A multiplayer room that is open right now. Finished matches are in the match history on the website.

| Parameter | Type | Description |
|---|---|---|
| `id` | number | **Required.** Room number, 1–64 |

```bash
curl "https://api.taksiegra.ovh/v1/get_match?id=1"
```

```json
{
  "status": "success",
  "match": {
    "name": "ŚPT final", "mode": 0, "mods": 0, "seed": 12345, "host": {"id": 3, "name": "Python-"}, "refs": [],
    "in_progress": false, "is_scrimming": false, "map": {"id": 129891, "md5": "…", "name": "…"},
    "active_slots": {"0": {"loaded": false, "mods": 0, "player": {"id": 3, "name": "Python-"}, "skipped": false, "status": 4, "team": 0}}
  }
}
```

### GET /v1/get_mappool

A tournament mappool.

| Parameter | Type | Description |
|---|---|---|
| `id` | number | **Required.** Mappool ID |

Maps are in the `maps` object. Each key is the mods number joined with the slot number, e.g. `81` is HD (8), slot 1, and `01` is NoMod, slot 1.

> [!IMPORTANT]
> For now this endpoint only works while the mappool's creator is online. Otherwise it returns `404`.

```bash
curl "https://api.taksiegra.ovh/v1/get_mappool?id=1"
```
