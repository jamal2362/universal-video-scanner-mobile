# 📱 Universal Video Scanner — Mobile

An Android app for [Universal Video Scanner](https://github.com/jamal2362/universal-video-scanner):
the library, the statistics, the media directory and the scanner itself, on the
phone. Built with Jetpack Compose and Material 3, and it speaks the whole of the
scanner's versioned API — every endpoint, every query parameter, the event
stream and the poster cache.

---

## Table of Contents

1. [What It Does](#1-what-it-does)
2. [Requirements](#2-requirements)
3. [Setting It Up](#3-setting-it-up)
4. [Two Servers, One App](#4-two-servers-one-app)
5. [The Screens](#5-the-screens)
6. [What of the API Is Used](#6-what-of-the-api-is-used)
7. [How It Saves Data](#7-how-it-saves-data)
8. [Building It](#8-building-it)
9. [Project Layout](#9-project-layout)
10. [Troubleshooting](#10-troubleshooting)
11. [License](#11-license)

---

## 1. What It Does

- **The library** as a grid of covers or a list of rows — searched, filtered,
  sorted and paged by the server, in every order the web interface offers,
  ranked the same way.
- **One title in full**: HDR format down to the enhancement layer and CM
  version, the codecs with their profile and encoder, the bitrates, the static
  HDR metadata including the mastering display and the L5 active area, and every
  rating the lookups came back with — IMDb, TMDB, both Rotten Tomatoes scores,
  Trakt, Metacritic and the Top 250 rank.
- **The statistics** as counts per HDR format, resolution class, frame size,
  video codec and audio codec — every row a way into the library, filtered.
- **The media directory** with what has been scanned and what has not; pick the
  gaps and hand exactly those to the scanner, or read a single file and wait for
  the answer.
- **The scan itself**: start it, watch it move file by file over the event
  stream, stop it, and empty the library when it should be rebuilt.
- **Two addresses**, so the same instance is reachable from the sofa and from a
  train — see [4](#4-two-servers-one-app).
- Material You colours from the wallpaper, light and dark, German and English.

## 2. Requirements

- Android 8.0 (API 26) or newer
- A reachable Universal Video Scanner with its API switched on:

  ```yaml
  environment:
    - API_TOKEN=a-long-random-secret   # the API is off without it
  ```

  Generate one with `openssl rand -hex 32`. Without `API_TOKEN` every request
  answers `503 api_disabled`, and the app says so.

`API_CORS_ORIGINS` is **not** needed: CORS is a browser rule, and this is not a
browser.

## 3. Setting It Up

1. Install the APK (see [8](#8-building-it), or take it from the CI artifacts).
2. Open **Settings → Local server** and fill in:
   - **Host or IP address** — `192.168.1.10`, or a name your network resolves.
     Pasting a whole URL works: `https://scanner.example.com:8443/` fills the
     host, the port and the HTTPS switch in one go.
   - **Port** — `2367` unless you moved it.
   - **API token** — the `API_TOKEN` of your instance.
3. Tap **Test connection**. It reports the API version when the address, the
   token and the instance all agree, and says which of the three did not
   otherwise.

That is enough. Everything else has a working default.

## 4. Two Servers, One App

The address that reaches an instance at home is not the address that reaches it
from anywhere else: a different scheme, a different port, and usually a
different token, because the way in from outside is typically a reverse proxy of
its own. So the app stores **two** complete servers rather than one, and the
**connection mode** decides which is used:

| Mode | What happens |
|------|--------------|
| **Automatic** | The local address first; the remote one takes over the moment the local one does not answer |
| **Local only** | Never reaches for the remote address |
| **Remote only** | Never reaches for the local address |

In automatic mode the failover is per request and invisible: the local address
is given a short connect timeout so it fails fast, the remote one answers
instead, and the one that last answered is tried first from then on — so leaving
the flat costs one failed connection, not one per request. Which of the two is
currently answering is shown at the top of the settings.

An answer is never grounds for failover. A `401` from the local server means the
token is wrong; asking the remote one with the same wrong token would only turn
a clear error into a confusing one.

The same applies to posters and to the event stream: both follow the mode, and
the stream walks the list itself when an address will not open at all.

> Plain `http` is permitted, because an instance on the local network is reached
> by IP. An instance published to the internet belongs behind `https` — switch
> HTTPS on for the remote server and the platform's certificate checks apply in
> full. Certificates from a private authority work too, as long as the authority
> is installed on the device.

## 5. The Screens

| Screen | What it is for |
|--------|----------------|
| **Library** | The titles. Search, the filter sheet, the order, grid or list, and the next page as you reach the end |
| **Statistics** | The library in numbers; tap a row to open it filtered in the library |
| **Files** | The media directory, what is scanned and what is not, and the two ways to change that |
| **Scan** | Progress, start, stop, the live activity, and emptying the library |
| **Settings** | The two servers, the connection mode, the appearance and how much to fetch at a time |

**The filter sheet** offers every field the API narrows by. The ones the
statistics can count — HDR format, enhancement layer, resolution and its class,
the two codecs — are offered as the values your library actually holds, rather
than a guess; the three the counts do not cover — HDR detail, encoder, CM
version — are free text, which the API matches case-insensitively anyway.
Every range is typed in the unit a person thinks in (minutes, gigabytes,
megabits, a year, a score) and converted to what the API stores. "Modified in
the last N days" is the `min_mtime` range, spelled the way it is meant.

**The order** includes the combined modes: HDR format + audio track, HDR format
+ video bitrate, HDR format + audio bitrate, audio track + audio bitrate. Those
travel as one comma-separated `sort`, so the server settles the ties — the phone
never re-sorts a page it was handed.

## 6. What of the API Is Used

All of it.

| Endpoint | Where it shows up |
|----------|-------------------|
| `GET /api/v1` | **Test connection** in the settings |
| `GET /api/v1/library` | The library, with `search`, every exact filter, every `min_`/`max_` range, `sort` (single and combined), `order`, `limit`/`offset`, `fields` and `updated_since` |
| `GET /api/v1/library/stats` | The statistics screen, and the values the filter sheet offers |
| `GET /api/v1/entries` | One title's own screen |
| `GET /api/v1/files` | The files screen |
| `GET /api/v1/posters/<name>` | Every cover, at `?w=160/320/480/640` |
| `GET /api/v1/scan/status` | The scan screen when it opens |
| `GET /api/v1/events` | Live progress and changes on every screen |
| `POST /api/v1/scan` | **Scan everything new** |
| `POST /api/v1/scan/files` | **Scan selection** on the files screen |
| `POST /api/v1/scan/cancel` | **Stop scan** |
| `POST /api/v1/entries/scan` | The play button on a single unscanned file |
| `POST /api/v1/entries/rescan` | **Rescan** on a title's screen |
| `POST /api/v1/entries/delete` | **Delete** on a title's screen |
| `POST /api/v1/database/clear` | **Empty library**, behind a confirmation |

The token travels as `X-API-Token` on every request, and as `?token=` on the two
the API documents that for: the event stream, which carries no headers, and the
posters, which are fetched by an image loader that sets none.

The error `code` every failure carries is what the app reacts to, so
`api_disabled`, `unauthorized`, `scan_running` and the rest each get a sentence
that says what to do, rather than a status number.

## 7. How It Saves Data

A phone on a mobile connection should not download a library to show twelve
titles, and the API is built so it does not have to:

- **`fields`** — a list row needs a dozen of the thirty-odd fields an entry
  carries, and asks for exactly those. For a realistic entry that is roughly
  0.5 kB instead of 1.7 kB; a library of 2000 titles is 0.9 MB rather than
  3.3 MB before compression. A title's own screen asks for the whole record.
- **The ETag** — the answer to a question is remembered with the tag the server
  gave it. Asking the same question again sends it back as `If-None-Match`, and
  an unchanged library answers `304` with no body at all. That is what makes
  reopening the app instant.
- **`updated_since`** — while a scan is running the server publishes an event per
  file. Instead of fetching the page again for each, the app asks for what was
  written since its last sync and replaces those rows in place, so the list
  neither jumps nor reorders under your finger. A finished scan can have added
  titles the window never held, so that one does get a fresh page.
- **Poster widths** — `?w=` asks for the resized copy the grid actually needs. A
  1000×1500 poster of 24 kB is 1.2 kB at `w=320`. The server makes each copy
  once and keeps it, and marks it cacheable for a week; the app keeps it on
  disk too, because a cached poster never changes under its name.
- **One event stream** for the whole app, dropped a few seconds after the last
  screen stops listening.

## 8. Building It

```bash
git clone https://github.com/jamal2362/universal-video-scanner-mobile.git
cd universal-video-scanner-mobile

./gradlew testDebugUnitTest    # the unit tests
./gradlew lintDebug            # Android Lint
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

JDK 17 and an Android SDK with platform 35 are all it needs; Android Studio
brings both. On a bare machine point Gradle at the SDK:

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
```

`assembleRelease` produces an unsigned APK; add your own signing config to
`app/build.gradle.kts` to sign it. The release build is shrunk and obfuscated,
and the rules for that are in `app/proguard-rules.pro`.

## 9. Project Layout

```
app/src/main/java/com/jamal2367/uvsmobile/
├── data/
│   ├── model/       the API's shapes, and what a library query is made of
│   ├── prefs/       the two servers and the rest of the settings (DataStore)
│   ├── remote/      Retrofit, the failover between the two addresses, the
│   │                event stream and the connection test
│   └── repository/  every call the app can make, with the ETag cache
├── di/              the one graph, built by hand
├── ui/
│   ├── components/  the pieces the screens are built from
│   ├── library/     the grid, the list, the filter and order sheets
│   ├── detail/      one title in full
│   ├── stats/       the library in numbers
│   ├── files/       the media directory
│   ├── scan/        progress, control and the live activity
│   ├── settings/    the two servers and everything else
│   ├── navigation/  the routes and the five destinations
│   └── theme/       colours, type, Material You
└── util/            formatting, poster URLs, error wording
```

**Technology**: Kotlin, Jetpack Compose with Material 3, Navigation Compose,
DataStore, Retrofit and OkHttp with kotlinx.serialization, OkHttp's SSE client,
and Coil 3 for images. No dependency-injection framework — the graph is small
enough to read in one file.

## 10. Troubleshooting

**"The API is switched off on the server"** — the instance has no `API_TOKEN`.
Set one in its environment and restart it.

**"The API token is missing or wrong"** — the token in the app is not the one
the instance was started with. Note that each of the two servers has its own.

**"No connection to …"** — the address or the port is wrong, the instance is not
running, or the phone is not on that network. **Test connection** in the
settings says which address was tried.

**"The server does not know this endpoint"** — something answered, but it was
not a Universal Video Scanner. Check the port.

**Covers stay blank** — the scanner caches a poster only when it could fetch
one; without a `TMDB_API_KEY` or `FANART_API_KEY` there is nothing to show. An
entry whose image could not be cached carries the remote URL and is loaded from
that host instead, which needs the phone to have internet access.

**No live updates** — the stream needs the same token and address as everything
else; the dot in the scan screen's title bar says whether it is connected. It
can also simply be switched off under **Settings → Live updates**.

## 11. License

MIT — see [LICENSE](LICENSE).
