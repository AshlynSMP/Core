![Logo](https://raw.githubusercontent.com/AshlynSMP/.github/refs/heads/main/assets/core.png)

---
[![forthebadge](https://forthebadge.com/images/badges/built-by-codebabes.svg)](https://forthebadge.com)
[![forthebadge](https://forthebadge.com/images/badges/does-not-contain-msg.svg)](https://forthebadge.com)

**Core** is the core plugin for the AshlynSMP server.

## Description

Core is a comprehensive Spigot/Paper plugin designed specifically for the AshlynSMP server.

## Web API

Core can serve a small API for the AshlynSMP website. Turn it on in `config.yml`:

```yaml
web:
    enabled: true
    port: 8150
    allowed-origin: 'https://your-website.example'
```

`GET /api/status` returns online players (health, level, dimension, ping and stats), every player that ever joined with their playtime, deaths and kills, the latest joins/leaves/deaths/advancements, lore mode, ceasefire, radio tower status, the world day/time/weather and the installed Ashlyn plugins as JSON. It's refreshed every `refresh-seconds` on the main thread. Set `web.status-enabled: false` to turn this endpoint off; the whitelist requests keep working, and the website then shows the server as offline.

## Whitelist requests

Before the season starts, the website shows a form where players can ask to be whitelisted. Requests go to `POST /api/whitelist` and are kept in `plugins/Core/requests.yml` until a leader handles them. Online leaders get a chat message for every new one, and leaders who join later are told how many are waiting. This needs the web API to be on (`web.enabled: true`).

- `/aanvragen` - list the open requests, with buttons to accept or deny
- `/aanvragen accept <naam>` - put the player on the whitelist (the request stays if that fails, e.g. when the account doesn't exist)
- `/aanvragen deny <naam>` - drop the request

Close the form with `web.whitelist-requests.open: false`; the website then hides it. Each visitor can send 5 requests per 10 minutes, and at most `max-pending` requests can be open at once.

## Wrapped

Core keeps track of season stats Minecraft doesn't store itself, for the end of season recap on the website: time per dimension, which hours people play, days played, longest session and advancements. They're kept in `plugins/Core/wrapped.yml`.

- `/wrapped` - show how many players are tracked
- `/wrapped export` - write `plugins/Core/wrapped.json` for the website, with these stats plus each player's Minecraft statistics (playtime, deaths, kills, distance on foot, jumps, blocks mined), and the story and event timeline
- `/wrapped reset confirm` - start counting from zero, for just before the season starts

## Story

The website shows which act of the season the story is in. Later act titles stay hidden until a leader reveals them:

- `/act` - show the current act (everyone)
- `/act 2 The Mist Expands` - move the story to act 2 and give it its title
- `/act info [nummer] <tekst>` - set the short description shown under an act, the current one if you leave out the number

## Events

Leaders plan events in-game. The website counts down to the next one and keeps every event that has started on its timeline.

- `/event set 30-10-2026 20:00 De mist trekt op` - plan an event on a date and time (in `event.timezone`)
- `/event in 1d2h De mist trekt op` - plan an event in a while from now
- `/event recap <tekst>` - write what happened at the last event, shown on the timeline
- `/event clear` - cancel the next planned event
- `/event remove <naam>` - remove an event from the timeline
- `/event` and `/event list` - show the next or all planned events (everyone)

## Building from Source

### Prerequisites
- Java 21 or higher
- Maven 3.6+

### Build Steps

```bash
# Clone the repository
git clone https://github.com/AshlynSMP/Core.git
cd Core

# Build with Maven
mvn clean package

# The compiled jar will be in the target/ directory
```

## Requirements

- **Minecraft Version**: 1.21+
- **Server Software**: Spigot/Paper 1.21+
- **Java Version**: 21

## License

This project is proprietary software for the AshlynSMP server.

## Bug Reports & Features

If you encounter any bugs or have feature suggestions, please open an issue on the GitHub repository.

## Projects using Core

- [Propaganda](https://github.com/AshlynSMP/Propaganda)
- [Radio](https://modrinth.com/plugin/Radio)

---