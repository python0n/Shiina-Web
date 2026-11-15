placeholder
icon:fa-solid fa-scale-balanced
route:rules
krz  :Rules
title:Rules
navbar:true
footer:true
behindLogin:false

taksiegra is a private osu! server run by one person for friends and anyone who wants to join. There are only a few rules and most of them are common sense. By creating an account you accept them.

> [!NOTE]
> These rules apply from 30.10.2026, last changed 15.11.2026. Scores and accounts from before 30.10.2026 stay as they are.

## Accounts

- One account per player. **A second account is allowed only with the admin's permission**, for example to play with a mouse instead of a tablet.
- You may let a friend play on your account. Boosting someone else's pp or rank and playing for someone in a tournament are not allowed.
- The account owner is responsible for everything that happens on the account.
- You must be at least 10 years old to create an account. If you are under 16, you need a parent's or guardian's permission. A parent or guardian can ask for the account to be deleted at any time by writing to the address in [Contact](#contact).

## Fair play

- No cheats or anything that plays for you or changes gameplay: aim assist, relax/autopilot in normal modes, game speed changers, edited replays, faked scores.
- No modified osu! clients. Play on the normal client with the `-devserver` flag, as described in the connection guide.
- Skins, tablets, mice, any keyboard and overlays that only read data from the game (such as tosu) are fine.
- The server's Relax and Autopilot modes have their own rankings and only count there.

> [!TIP]
> Not sure if something is allowed? Ask the admin before you use it.

## Names, avatars, clans and comments

Your name, avatar, banner, profile page, clan name and tag, and comments are visible to everyone, including people outside the server. They must not contain:

- Nazi, fascist or other hate symbols and slogans,
- slurs aimed at people because of their origin, skin colour, religion, orientation or disability,
- pornography or anything illegal under Polish law,
- impersonation of other players or the admin.

Swearing and dark humour are fine as long as they don't target groups of people. The admin may change or remove such content without warning, and restrict the account if it happens again.

## Chat

- No spam, harassment of other players or posting other people's personal data.
- Private messages are stored on the server with no time limit: this way they reach players who are offline, and the chat on the website shows the history of your conversations. Only the people in a conversation can see its history.
- The latest messages of public channels (e.g. #osu) are visible in the chat on the website for 7 days.
- The chat of multiplayer matches (from the game, the website and IRC, also in rooms with a password) is saved with the match history. Only the server staff can see it, for example when checking reports.

## Uploaded beatmaps

- You can upload your own beatmaps through the Beatmap Submission System.
- **Want to upload a map by another mapper that was never published anywhere? Ask the admin first.**
- The admin may change a map's status or remove it if it breaks these rules or gets in the way of other players.
- You are responsible for what you upload (maps, avatars, banners, profile page) and you must have the right to upload it.
- **Copyright:** if you believe something on the server infringes your rights, write to the address in [Contact](#contact). Say what it is and where it is. The admin will remove it or explain why it stays.

## Scores and pp

- pp and rankings are calculated by the server. After a formula change or a bug fix scores may be recalculated, and your pp may change.
- A score set by abusing a server or map bug may be removed.

## Restrictions

The admin may restrict an account that breaks the rules. A restricted account:

- disappears from the rankings, and its new scores don't show up on map leaderboards,
- is invisible to other players in game and can't chat,
- can still play and log in to the website.

A restriction can be temporary or permanent, depending on the case.

**Appeals:** message the admin (Python-) in game, on the Discord shown on his profile or at the address in [Contact](#contact). Say which account it's about and why you think the restriction was wrong.

## What the server stores

The server only collects what it needs to run and to enforce these rules. Data is never sold or passed on to anyone.

| What | Why |
|---|---|
| Username, e-mail, country, registration date | Your account. Your e-mail is never shown publicly. |
| Password | Logging in. Stored only in encrypted form, the admin can't see it. |
| Scores, replays, statistics, favourite maps, map ratings | Rankings and your profile |
| Avatar, banner, profile page, Discord name (if you add it) | Your profile |
| Comments and private messages | Comments on the website, delivering messages, conversation history in the website chat |
| Latest messages of public channels (7 days) | Channel history in the website chat |
| Chat of multiplayer matches | Match history, visible only to the staff |
| Notifications (e.g. an accepted replay or a lost #1) | The bell on the website |
| IP address and client version when you log in from the game (last 90 days) | Security and abuse detection |
| Hashes of hardware identifiers sent by the osu! client (network adapter, disk, installation) | Detecting undeclared second accounts. The server only receives hashes, never the raw values. |
| IP address, browser and approximate country when you log in on the website | The active sessions list in your account settings |

Server and website logs that contain IP addresses are deleted within 30 days.

### Cookies

The website uses only three cookies, all needed for it to work:

- `shiina` keeps you logged in. It expires after 1 day without visiting the site, or after 30 days without a visit with "Remember me".
- `lang` remembers the language you picked. It expires after a year.
- `tsg_known` remembers which account was last used in this browser, also after logging out, so the site can remind you about the one-account rule before you register again. It expires after 2 years.

There are no advertising or tracking cookies, which is why there is no cookie banner.

### Third-party services

- **Cloudflare**: part of the website traffic goes through their servers, and their Turnstile check protects forms from bots.
- **catboy.best and osu.direct**: the sources of beatmaps downloaded in game and on the website. Downloads go through the taksiegra server, so they see the server's address, not yours.
- **ip-api.com**: when you log in from the game or on the website, the server looks up your country (and, for the session list, your city) from your IP address there. Only the IP address is sent.
- **YouTube**: videos that players put on their profile pages load from YouTube's servers (youtube-nocookie.com). When you view such a profile, YouTube sees your IP address.

### Your data

- You can download a copy of your data in your account settings, in the data section.
- You can ask for your account to be deleted in the settings or by writing to the admin. The admin reviews the request and then deletes the account together with its scores, statistics and messages. After that it can't be undone, but you can cancel the request until it is reviewed.
- If a deleted account had a restriction, its username and hardware hashes are kept for one year, so the restriction can't be dodged with a new account. They are removed automatically after that. Nothing is kept for accounts without a restriction.
- The server makes a backup every day and keeps the last 7. Deleted data is gone from the backups after 7 days at most.

## Running the server

taksiegra is a hobby server run in spare time. There may be downtime, and the server may be shut down one day. Backups protect against hardware failure, but there is no guarantee that accounts and scores will last forever.

## Contact

For questions about your account, your data or an appeal, message the admin (Python-) in game or write to **python@taksiegra.ovh**. People without an account, such as a player's parent or guardian, can write there too.

## Changes

These rules may change. Bigger changes will be posted in the [changelog](https://taksiegra.ovh/changelog/).
