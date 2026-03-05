# **Shiina-Web**
![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/osu-NoLimits/Shiina-Web/maven.yml?label=Tests&color=1783a3&link=https%3A%2F%2Fgithub.com%2Fosu-NoLimits%2FShiina-Web)
![Discord](https://img.shields.io/discord/1295422749807743037?label=Discord&link=https%3A%2F%2Fdiscord.gg%2F6DH8bB24p6&color=1783a3&link=https%3A%2F%2Fgithub.com%2Fosu-NoLimits%2FShiina-Web)
![GitHub contributors](https://img.shields.io/github/contributors/osu-NoLimits/Shiina-Web?label=Contributors&color=1783a3&link=https%3A%2F%2Fgithub.com%2Fosu-NoLimits%2FShiina-Web)
![GitHub License](https://img.shields.io/github/license/osu-NoLimits/Shiina-Web?label=License&color=1783a3&link=https%3A%2F%2Fgithub.com%2Fosu-NoLimits%2FShiina-Web)
![GitHub Created At](https://img.shields.io/github/created-at/osu-NoLimits/Shiina-Web?label=Created&color=1783a3&link=https%3A%2F%2Fgithub.com%2Fosu-NoLimits%2FShiina-Web)
![Static Badge](https://img.shields.io/badge/available%20-%20Test?label=Documentation&color=1783a3&link=https%3A%2F%2Fosu-nolimits.github.io%2Fwiki%2F)
![GitHub Repo stars](https://img.shields.io/github/stars/osu-NoLimits/Shiina-Web)

A Java-based web frontend for **bancho.py-ex** osu! private servers, with extensive features and plugin support.

---

## 🛠️ Installation

For installation look up our [new documentation](https://osu-nolimits.github.io/wiki/)

---

## **Feature List**

### Core Features
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

### Customization & Extensibility
- ✅ Good theming support [Seen here](https://osu-nolimits.github.io/wiki/theming/)
- ✅ Java plugin system with event hooks
- ✅ Extensive API integration
- ✅ Donation system (Kofi)

### Technical Features
- ✅ API request caching for improved performance
- ✅ Configurable error and request logging
- ✅ Easy customization via `.config/customization.yml`
- ✅ Multiple webhook support

### Extended Features (taksiegra.ovh fork)
- ✅ **Replay Submission System** — players can upload `.osr` files for admin review
- ✅ **Replay Analyzer** (`/analyzer`) — timing error and aim accuracy analysis with interactive Chart.js graphs and map recommendations based on skill type (Stream / Jump / Burst / Tech)
- ✅ **Match History Pages** — match list and detailed match view with full event log and score tables
- ✅ **Admin Replay Panel** — approve or reject submitted replays, download `.osr` files
- ✅ **Beatmap Submission System UI** — frontend for the bancho.py-ex BSS, submit maps directly from osu! editor
- ✅ **Profile & beatmap URL redirects** — `/users/<id>` → `/u/<id>`, `/beatmaps/<id>` → `/b/<id>` (HTTP 301)
- ✅ **Score comments** — comment system on score pages
- ✅ **Bot & Docs pages** — dedicated pages for Discord bot commands and server documentation

---

## 📋 Changelog

Full changelog with all updates: **[taksiegra.ovh/changelog/](https://taksiegra.ovh/changelog/)**

---

## **License**

This project is licensed under the MIT LICENSE found in the [LICENSE](/LICENSE) file.

---

## **Contributing**

Contributions are welcome! Please feel free to submit a Pull Request.

---

### **Contributors**

<a href="https://github.com/osu-NoLimits/Shiina-Web/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=osu-NoLimits/Shiina-Web" />
</a>