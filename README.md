![Logo](https://raw.githubusercontent.com/AshlynSMP/.github/refs/heads/main/assets/core.png)

---
[![forthebadge](https://forthebadge.com/images/badges/built-by-codebabes.svg)](https://forthebadge.com)
[![forthebadge](https://forthebadge.com/images/badges/does-not-contain-msg.svg)](https://forthebadge.com)

**Core** is the core plugin for the AshlynSMP server.

## Description

Core is a comprehensive Spigot/Paper plugin designed specifically for the AshlynSMP server.

## Reloading

`/corereload` (leaders only) reloads `config.yml` and restarts the web API, so changes work without restarting the server. The activity feed on the website starts empty again after a reload.

## Web API

Core can serve a small API for the AshlynSMP website. Turn it on in `config.yml`:

```yaml
web:
    enabled: true
    port: 8150
    allowed-origin: 'https://your-website.example'
```

`GET /api/status` returns online players (health, level, dimension, ping and stats), every player that ever joined with their playtime, deaths and kills, the latest joins/leaves/deaths/advancements, lore mode, ceasefire, radio tower status, the world day/time/weather and the installed Ashlyn plugins as JSON. It's refreshed every `refresh-seconds` on the main thread. Set `web.status-enabled: false` to turn this endpoint off; the whitelist requests keep working, and the website then shows the server as offline.

## Player heads

The website gets every player head from Core, so it always shows the skin the server knows:

- `GET /api/head/<uuid>.png` - the face with the hat layer, 128x128
- `GET /api/body/<uuid>.png` - the whole player from the front, 128x256

Core takes the skin from an online player's profile, or asks Mojang for players who are offline, and draws the images with sharp pixels. Slim arms and old 64x32 skins are handled the way Minecraft does. The images are kept in `plugins/Core/skins/` and drawn again from the latest skin once they're an hour old. Only players who have been on the server get a head. When Core can't be reached, the website falls back to Crafatar.

## Whitelist requests

Before the season starts, the website shows a form where players can ask to be whitelisted. Requests go to `POST /api/whitelist` and are kept in `plugins/Core/requests.yml` until a leader handles them. Online leaders get a chat message for every new one, and leaders who join later are told how many are waiting. This needs the web API to be on (`web.enabled: true`).

- `/aanvragen` - list the open requests, with buttons to accept or deny
- `/aanvragen accept <naam>` - put the player on the whitelist (the request stays if that fails, e.g. when the account doesn't exist)
- `/aanvragen deny <naam>` - drop the request

Close the form with `web.whitelist-requests.open: false`; the website then hides it. Each visitor can send 5 requests per 10 minutes, and at most `max-pending` requests can be open at once.

When a request is accepted, the real name from the form is saved in `plugins/Core/names.yml`. Other plugins can read it with `RealNames.get(player)`, which falls back to an open request and then to the Minecraft name.

## Player portal

Players sign in on the website's portal with their Watchdog account and link their Minecraft account to see their stats, sign up for events and, for leaders, handle requests and settings. Turn it on in `config.yml`:

```yaml
web:
    portal:
        enabled: true
        watchdog-url: 'https://watchdog.ashlyn.nl'
        client-id: 'ashlyn-portal'
```

The client needs to exist in Watchdog as an interactive client with PKCE on and the website's `/portal` address as redirect URI. Core swaps the login code for a token itself, because Watchdog doesn't answer the browser directly, and checks every token with Watchdog's `/connect/userinfo`. Links are kept in `plugins/Core/links.yml`.

- `/koppel <code>` - link your account with the code from the portal (codes work for 10 minutes)
- `/koppel` - show which Watchdog account you're linked to
- `/koppel uit` - remove your link
- `/koppel reset <speler>` - remove the link of a player (leaders only)

Ranks and averages come from a table of every player's stats, rebuilt every 5 minutes from the world's stats files and the Wrapped tracker. Who signs up for an event, and who was online while it went on, is kept in `plugins/Core/signups.yml`.

During maintenance (`/onderhoud aan`) only leaders and ops can use the portal; everyone else gets the maintenance screen with the reason. Accounts that aren't linked yet can still get a code, since only leaders can join to type it.

Everything but login needs the Watchdog token as `Authorization: Bearer`:

- `POST /api/portal/login` - swap the login code for a token
- `GET /api/portal/me` - the player, their stats and ranks, the next event, the current act and who's online
- `POST` / `DELETE /api/portal/link` - get a link code, or unlink
- `GET /api/portal/events` - planned and past events
- `POST /api/portal/rsvp` - `{"time", "going": true | false | null}`
- `GET /api/portal/admin` and `POST /api/portal/admin/requests`, `/events`, `/settings` - for players linked to a leader or op account

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

## Arrival

How players start the season. While the arrival is on, every player who hasn't reached the forest yet gets a paper with their real name and is sent to the hok, where everyone waits together. When a leader starts it, they're spread over the forest at random safe spots on the ground, away from each other, and see the arrival title. Players who join for the first time after that pass through the hok and go to the forest after a few seconds. Who already arrived is kept in `plugins/Core/arrivals.yml`.

- `/aankomst` - show how the arrival is set up and how many players are waiting
- `/aankomst aan` / `/aankomst uit` - turn the arrival on or off
- `/aankomst hok` - set the hok at your position
- `/aankomst bos <straal> [afstand]` - set the forest center at your position, how far from it players can land and how far apart they land at least (25 by default)
- `/aankomst start` - make it night in the forest world and spread everyone in the hok over the forest
- `/aankomst naam <speler> <naam>` - set the real name of a player, for players whitelisted without a request
- `/aankomst papier <speler>` - give a player a new name paper
- `/aankomst reset <speler>` - let a player go through the arrival again, for testing

The title is the Act 1 image from the [resource pack](https://github.com/AshlynSMP/RS-S2), sent without a text shadow.

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