placeholder
icon:fa-solid fa-code
route:api-pl
krz  :API
title:API
navbar:false
footer:true
behindLogin:false
<div class="doc-api"></div>


Serwer udostępnia publiczne API do odczytu danych: graczy, wyników, map, klanów i rankingów. Możesz go użyć we własnym bocie, nakładce albo stronie ze statystykami.

```text
https://api.taksiegra.ovh/v1
```

> [!NOTE]
> Wszystkie endpointy to zapytania `GET` zwracające JSON (poza `get_replay`, który zwraca plik). Klucz API jest potrzebny tylko do `calculate_pp`.

## Jak to działa

> [!WARNING]
> API jest dostępne dla wszystkich, ale nie zalewaj go zapytaniami. Jeśli robisz coś, co pyta serwer częściej niż raz na kilka sekund, napisz najpierw do admina.

- Parametry podajesz w adresie, np. `?id=3&mode=0`.
- Odpowiedź ma pole `"status": "success"`. Przy błędzie `status` zawiera opis, a kod HTTP mówi, co poszło nie tak: `400` błędne parametry, `401` brak lub zły klucz, `404` nie znaleziono.
- Czasy (`creation_time`, `latest_activity`, `last_seen`, `login_time`) to sekundy uniksowe. Daty wyników (`play_time`) to czas UTC.

```json
{"status": "Player not found."}
```

Brak wymaganego parametru albo zła wartość daje kod `422` z opisem w polu `detail`.

Gracza wskazujesz przez `id` **albo** `name`, nigdy oba naraz.

### Tryby gry

Parametr `mode` przyjmuje:

| Wartość | Tryb |
|---|---|
| `0` | osu! |
| `1` | osu!taiko |
| `2` | osu!catch |
| `3` | osu!mania |
| `4` | osu! Relax |
| `5` | osu!taiko Relax |
| `6` | osu!catch Relax |
| `8` | osu! Autopilot |

Wartości `7`, `9`, `10` i `11` nie istnieją w grze i endpointy z wynikami lub rankingami zwracają dla nich błąd `400`.

### Mody

Parametr `mods` przyjmuje skróty (`HDDT`) albo liczbę z osu! (`72`). Domyślnie zwracane są wyniki zawierające **wszystkie** podane mody (mogą mieć też inne). Z prefiksem `~` (np. `~HDHR`) wystarczy **dowolny** z nich.

### Statusy

Status mapy (`status` w mapie): `-1` niewysłana, `0` pending, `1` dostępna aktualizacja, `2` rankingowa, `3` zatwierdzona, `4` kwalifikowana, `5` uwielbiana.

Status wyniku (`status` w wyniku): `0` niezaliczony, `1` wysłany, `2` najlepszy wynik gracza na mapie.

## Gracze

### GET /v1/get_player_count

Liczba graczy online i wszystkich kont.

```bash
curl "https://api.taksiegra.ovh/v1/get_player_count"
```

```json
{"status": "success", "counts": {"online": 4, "total": 57}}
```

### GET /v1/online

Lista graczy aktualnie w grze. Boty są w osobnej liście.

```bash
curl "https://api.taksiegra.ovh/v1/online"
```

```json
{"status": "success", "players": [{"id": 3, "name": "Python-"}], "bots": [{"id": 1, "name": "…"}]}
```

### GET /v1/search_players

Szukanie graczy po fragmencie nicku.

| Parametr | Typ | Opis |
|---|---|---|
| `q` | tekst | Fragment nicku, 2–32 znaki. Bez parametru zwraca wszystkich. |

```bash
curl "https://api.taksiegra.ovh/v1/search_players?q=pyth"
```

```json
{"status": "success", "results": 1, "result": [{"id": 3, "name": "Python-"}]}
```

### GET /v1/get_player_info

Dane gracza: profil, statystyki albo jedno i drugie.

| Parametr | Typ | Opis |
|---|---|---|
| `id` / `name` | liczba / tekst | Gracz |
| `scope` | tekst | **Wymagany.** `info`, `stats` albo `all` |

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

`stats` zawiera obiekt dla każdego trybu, w którym gracz grał. Kluczem jest numer trybu.

### GET /v1/get_player_status

Czy gracz jest online i co robi. Gdy gracz jest offline, odpowiedź ma tylko `online: false` i `last_seen`, jak w drugim przykładzie.

| Parametr | Typ | Opis |
|---|---|---|
| `id` / `name` | liczba / tekst | Gracz |

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

Ostatnie albo najlepsze wyniki gracza.

| Parametr | Typ | Opis |
|---|---|---|
| `id` / `name` | liczba / tekst | Gracz |
| `scope` | tekst | **Wymagany.** `recent` (od najnowszych) albo `best` (od największego pp, tylko mapy rankingowe i zatwierdzone) |
| `mode` | liczba | Tryb, domyślnie `0` |
| `mods` | tekst | Filtr modów, patrz [Mody](#mody) |
| `limit` | liczba | 1–100, domyślnie 25 |
| `include_loved` | true/false | Dla `best`: czy liczyć mapy uwielbiane. Domyślnie `false` |
| `include_failed` | true/false | Dla `recent`: czy pokazywać niezaliczone. Domyślnie `true` |

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

Najczęściej grane mapy gracza.

| Parametr | Typ | Opis |
|---|---|---|
| `id` / `name` | liczba / tekst | Gracz |
| `mode` | liczba | Tryb, domyślnie `0` |
| `limit` | liczba | 1–100, domyślnie 25 |

```bash
curl "https://api.taksiegra.ovh/v1/get_player_most_played?id=3&mode=0&limit=5"
```

```json
{"status": "success", "maps": [{"md5": "…", "id": 129891, "set_id": 39804, "status": 2, "artist": "…", "title": "…", "version": "…", "creator": "…", "plays": 87}]}
```

## Mapy

### GET /v1/get_map_info

Dane mapy.

| Parametr | Typ | Opis |
|---|---|---|
| `id` / `md5` | liczba / tekst | ID trudności albo jej skrót MD5 |

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

Ranking mapy.

| Parametr | Typ | Opis |
|---|---|---|
| `id` / `md5` | liczba / tekst | Mapa |
| `scope` | tekst | **Wymagany.** `best` albo `recent` |
| `mode` | liczba | Tryb, domyślnie `0` |
| `mods` | tekst | Filtr modów |
| `limit` | liczba | 1–100, domyślnie 50 |

Przy `best` w zwykłych trybach kolejność jest według score, a w Relax i Autopilot według pp. Każdy gracz pojawia się raz, z wynikiem, który daje mu najwięcej pp na tej mapie.

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

Oblicza pp i gwiazdki dla mapy. **Wymaga klucza API.**

> [!TIP] Jak zdobyć klucz
> Napisz `!apikey` w prywatnej wiadomości do bota serwera w grze. Bot odpowie linkiem z kluczem. Nowy klucz unieważnia poprzedni.

Klucz wysyłasz w nagłówku:

```text
Authorization: Bearer TWÓJ_KLUCZ
```

| Parametr | Typ | Opis |
|---|---|---|
| `id` | liczba | ID trudności |
| `mode` | liczba | Tryb, domyślnie `0` |
| `mods` | liczba | Mody jako liczba, domyślnie `0` |
| `acc` | liczba | Celność. Można podać kilka razy. Domyślnie 100, 99, 98 i 95 |
| `combo` | liczba | Combo, domyślnie pełne |
| `misses` | liczba | Liczba missów, domyślnie 0 |
| `n300`, `n100`, `n50`, `ngeki`, `nkatu` | liczba | Dokładne trafienia zamiast `acc` |

Z parametrem `acc` dostajesz listę wyników, po jednym na każdą celność. Z dokładnymi trafieniami dostajesz jeden wynik.

```bash
curl -H "Authorization: Bearer TWÓJ_KLUCZ" "https://api.taksiegra.ovh/v1/calculate_pp?id=129891&mods=72&acc=100&acc=98"
```

```json
[
  {"performance": {"pp": 512.3, "pp_aim": 240.1, "pp_speed": 198.7, "pp_acc": 61.2}, "difficulty": {"stars": 7.21, "aim": 3.52, "speed": 3.31}, "accuracy": 100},
  {"performance": {"pp": 455.8, "pp_aim": 231.4, "pp_speed": 176.9, "pp_acc": 38.5}, "difficulty": {"stars": 7.21, "aim": 3.52, "speed": 3.31}, "accuracy": 98}
]
```

## Wyniki

### GET /v1/get_score_info

Pełne dane jednego wyniku.

| Parametr | Typ | Opis |
|---|---|---|
| `id` | liczba | **Wymagany.** ID wyniku |

```bash
curl "https://api.taksiegra.ovh/v1/get_score_info?id=51234"
```

### GET /v1/get_replay

Pobiera powtórkę jako plik `.osr`, gotowy do otwarcia w osu!.

| Parametr | Typ | Opis |
|---|---|---|
| `id` | liczba | **Wymagany.** ID wyniku |
| `include_headers` | true/false | Domyślnie `true`, czyli pełny plik `.osr`. `false` zwraca same dane ruchów |

```bash
curl -OJ "https://api.taksiegra.ovh/v1/get_replay?id=51234"
```

## Rankingi i klany

### GET /v1/get_leaderboard

Ranking graczy.

| Parametr | Typ | Opis |
|---|---|---|
| `mode` | liczba | Tryb, domyślnie `0` |
| `sort` | tekst | `pp` (domyślnie), `tscore`, `rscore`, `acc`, `plays` albo `playtime` |
| `country` | tekst | Kod kraju, np. `pl` |
| `limit` | liczba | 1–100, domyślnie 50 |
| `offset` | liczba | Ile pozycji pominąć, do stronicowania |

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

Klan z listą członków. Ta odpowiedź nie ma pola `status`.

| Parametr | Typ | Opis |
|---|---|---|
| `id` | liczba | **Wymagany.** ID klanu |

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

## Multiplayer i turnieje

### GET /v1/get_match

Pokój multiplayer, który jest teraz otwarty. Zakończone mecze są w historii meczów na stronie.

| Parametr | Typ | Opis |
|---|---|---|
| `id` | liczba | **Wymagany.** Numer pokoju, 1–64 |

```bash
curl "https://api.taksiegra.ovh/v1/get_match?id=1"
```

```json
{
  "status": "success",
  "match": {
    "name": "ŚPT finał", "mode": 0, "mods": 0, "seed": 12345, "host": {"id": 3, "name": "Python-"}, "refs": [],
    "in_progress": false, "is_scrimming": false, "map": {"id": 129891, "md5": "…", "name": "…"},
    "active_slots": {"0": {"loaded": false, "mods": 0, "player": {"id": 3, "name": "Python-"}, "skipped": false, "status": 4, "team": 0}}
  }
}
```

### GET /v1/get_mappool

Mappool turniejowy.

| Parametr | Typ | Opis |
|---|---|---|
| `id` | liczba | **Wymagany.** ID mappoola |

Mapy są w obiekcie `maps`. Kluczem jest liczba modów sklejona z numerem slotu, np. `81` to HD (8), slot 1, a `01` to NoMod, slot 1.

> [!IMPORTANT]
> Na razie ten endpoint działa tylko wtedy, gdy twórca mappoola jest online. W przeciwnym razie zwraca `404`.

```bash
curl "https://api.taksiegra.ovh/v1/get_mappool?id=1"
```
