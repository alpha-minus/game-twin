# Game Twin - Open Beta

![0-5-0-tablet](https://github.com/user-attachments/assets/193fb199-25a0-4f11-88e9-cb17ed98138d)

**Your game-aware companion for guides, images, checklists, and achievements**

Every time you switch games, you lose your place. The guide tab you had open is gone. The map you bookmarked isn't where you left it. The checklist you started last night is buried three folders deep.

Game Twin keeps all of that tied to whatever game you're playing. Guides, images, checklists, achievements, YouTube links, and search history, organized per game, waiting exactly where you left them. Switch games, and your whole workspace switches with it.

**Open Beta.** Free to download and use, no purchase or setup required. **Ad-free, offline-capable, no analytics or data collection. Requires Android 12+.**
Note: This app was developed with AI assistance.

<p align="center">
  <a href="https://github.com/alpha-minus/game-twin/releases/latest">Download Latest Release</a>
</p>

[![Add Game Twin to Obtainium](https://github.com/ImranR98/Obtainium/raw/main/assets/graphics/badge_obtainium.png?raw=true)](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.twinlore.app%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Falpha-minus%2Fgame-twin%22%2C%22author%22%3A%22alpha-minus%22%2C%22name%22%3A%22Game%20Twin%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%7D%22%7D)

[![Version](https://img.shields.io/github/v/release/alpha-minus/game-twin?include_prereleases&label=version&color=blue)](https://github.com/alpha-minus/game-twin/releases/latest)
[![Android](https://img.shields.io/badge/Android-12%2B-green)](https://github.com/alpha-minus/game-twin/releases/latest)

---

## The problem it solves

Looking something up mid-game usually means losing your place: dig up the guide, scroll back to where you were, and then lose it all again the moment you switch games. On a handheld it's even worse. There's no good spot to keep reference material that stays organized per game.

Game Twin gives every game its own vault: guides with your exact scroll position, images and maps with custom markers, checklists for collectibles and side quests, Retro and Steam achievement lists filtered to what matters, YouTube walkthrough links, and a per-game search history so you never retype the same query twice. Everything is saved offline, so no connection is needed once it's stored.

---

## Works on your device

**Any Android phone or tablet (Android 12+).** Fully supported. Game Twin auto-detects your most recently played game via RetroAchievements or Steam and loads the right vault automatically. Prefer to pick manually? That works too. Everything is organized per game and available offline.

**Android handhelds (AYN, Retroid, ANBERNIC, and others).** On top of RA and Steam auto-detection, Game Twin integrates directly with ES-DE, Cocoon and RetroArch on the same device. Launch a game and your vault loads with it. Full controller support throughout.

**AYN Thor and other dual-screen handhelds.** The experience Game Twin was built around. The companion panel lives on your second screen while you play on the first, the same way DS and 3DS games used both screens so naturally. Game switching is near-instant, no input from you. Joystick LEDs can also adapt to the current game's artwork color (opt in from Settings).

![549692130-9e18ea9a-a89f-4bf0-af7a-60fa0fd76c55](https://github.com/user-attachments/assets/3bee69c7-1ab9-46aa-85db-5046a6a66e63)

---

## Features

### Guides

- **Single-tap walkthroughs.** Game Twin matches your current game and surfaces ready-to-read guides from sources like StrategyWiki, GameFAQs, and IGN, SteamCommunity plus level maps from VGMaps with a single tap.
- **Online search.** Scoped web search per game with fully customizable search templates. Set up your preferred sites/patterns once and reach them in one tap.
- **Built-in browser.** A multi-tab in-app browser with per-game bookmarks ("saved pages") and optional ad-, tracker-, and cookie-banner blocking for cleaner reading.
- **Reader Mode.** Read clean and mobile device optimized text, epub and web based documents.
- **Per-game search history.** Your recent searches are saved per game, so you can pick up where you left off without retyping.
- **Offline saving.** Save any guide as a complete offline copy, including multi-page guides bundled as a single **guide set**.
- **PDF support.** Open PDF walkthroughs directly in the app with text search, bookmarks, and select-to-search. Large files are handled smoothly.
- **Saved pages.** Bookmark any page per game, online or offline. Reading position is tracked automatically so you resume exactly where you left off.
- **YouTube links.** Save YouTube walkthrough URLs as one-tap shortcuts per game.
- **Find in Page.** Fast in-page search throughout any guide.
- **Scroll Markers.** Drop position markers in a guide and jump between them.

### Images & Maps

- Save **multiple images and maps per vault** and switch between them. The app remembers your pan/zoom position for each one.
- Drop custom markers for anything you want to remember.
- **Screenshot Capture.** A button in the image picker grabs whatever's on your screen (or your second screen, Thor folks) and saves it straight into the vault. Perfect for in-game visual notes.
- One-tap switch between Image and Guide views.

> Privacy note on screenshots: Android asks for screen-capture permission once per session and reuses it while you're capturing. Restart the app and it'll politely ask again. The OS never lets an app watch your screen silently. See Android's [Media Projection overview](https://developer.android.com/media/grow/media-projection) if you're curious how it works.

### Checklists

- Per-game checklists. Collectibles, side quests, passwords, notes, whatever you want to track.
- Color-tag items drag to reorder, search, filter, and hide completed ones.
- **Import from markdown files** if you'd rather build your list elsewhere and bring it in all at once.

### Achievement Tracking

- **RetroAchievements & Steam.** Unified view, grid layout with logos, missable/unlocked filters, offline caching, and per-achievement guide search.
- **Auto-detection.** Queries both APIs in parallel and picks your most recently played game automatically, on any device.

### Library & Sync

- Grid and list views for your game library, with scraped artwork (box, logo, hero, grid).
- **Synced folder support.** Works seamlessly with folder sync apps like DriveSync for cross-device access.
- **Launcher home screen** and a curated catalog of recommended companion apps to get your device setup quicker.

### In-App Updates

The app checks GitHub Releases on launch and lets you download and install new versions without leaving Game Twin.

### Game Detection

- **ES-DE & Cocoon.** Reliable last-played detection. Auto-detection takes about 5 to 10 seconds to kick in.
- **RetroArch.** Core-based detection on the same device.
- **RetroAchievements** & **Steam.** Last-played detection across any device via their respective APIs.

### Controller

- **L1/R1** switch tabs inside the vault view
- **D-pad** moves focus
- **Left stick** scrolls
- **Right stick** zooms

| Button | Library | Guide | Image | Achievements | Checklist |
|--------|---------|-------|-------|--------------|-----------|
| A | Open vault | Confirm | Confirm | - | Toggle check |
| X | Context menu | Find on page | - | Refresh | Edit item |
| Y | - | Table of Contents | - | Wallpaper grid | Add item |
| Start | Cycle views | Next marker | Next marker | - | - |
| Select | - | Previous marker | Previous marker | - | - |
| L2 | - | Previous guide | Previous image | - | Previous image |
| R2 | - | Next guide | Next image | - | Next image |
| L3 | - | Scroll to top | Add marker | - | - |
| R3 | - | Scroll to bottom | - | - | - |

### System & UX

- Controller-first design with customizable layouts.
- Artwork adaptive accent color.
- LED accent support on AYN Thor/Odin (opt in from Settings).
- External storage support and custom folder locations.
- Optimized CPU and battery footprint.
- One-button second-display sleep.

---

## Getting Started

1. Download the latest APK from [Releases](https://github.com/alpha-minus/game-twin/releases/latest) or add via Obtainium using the button above.
2. Install on your device (Android 12+).
3. Choose a folder to save your files.
4. Add your RetroAchievements/Steam credentials, or point Settings at your RetroArch/ES-DE folder for auto game detection.
5. Launch a game. Game Twin will create a library entry automatically.
6. Start adding your resources via file selection or the built-in browser.

For a full walkthrough, see the [YouTube Setup Guide](https://www.youtube.com/results?search_query=game+twin+setup).

---

## Privacy

Game Twin runs **no analytics, ads, or behavioral trackers, and never sells your data.** It makes network requests only to:

- fetch game artwork from public art sources,
- query the **RetroAchievements** and **Steam** APIs using credentials you provide (sent only to those services),
- check **GitHub Releases** for app updates, and
- activate and validate your beta license.

**About license validation.** The beta is gated by a license check handled by the licensing provider, **[Lemon Squeezy](https://www.lemonsqueezy.com/privacy)**. The app sends the beta license key together with your **device model** (e.g. "Samsung SM-S921B") and a **hashed device identifier** (derived from your Android ID) to Lemon Squeezy. This is used solely to validate the beta, never for advertising, profiling, or sale and is the only device information Game Twin transmits about you. Its handling is governed by Lemon Squeezy's privacy policy.

Everything else (your library, guides, images, maps, checklists, and any credentials) stays on your device (and in any folders you choose to sync). The built-in browser additionally offers optional ad-, tracker-, and cookie-banner blocking.

---

## Legal

### Third-Party Content & Trademarks

Game Twin is an independent project and is **not affiliated with, endorsed by, or sponsored by** any of the platforms, services, or companies it works with, including (without limitation) RetroAchievements, Valve / Steam, GameFAQs, GameSpot, Fandom, IGN, StrategyWiki, VGMaps, Neoseeker, YouTube / Google, RetroArch, EmulationStation Desktop Edition (ES-DE), and Obtainium. All product names, logos, and trademarks are the property of their respective owners and are used here for identification purposes only.

Guides, walkthroughs, maps, box art, logos, and other reference material that you view or save through Game Twin are the **copyrighted property of their original authors and the sites that host them**. Game Twin does not host, create, sell, or redistribute this content — it is a personal tool that helps you find and keep your own copy of material you are entitled to access, stored only on your own device for personal, non-commercial use. **You are responsible for complying with the terms of service and copyright of each source you access.** If you are a rights holder with a concern about how content is accessed, please reach out using the contact below.

### Disclaimer of Warranty

Game Twin is provided **"AS IS", without warranty of any kind**, express or implied, including but not limited to the warranties of merchantability, fitness for a particular purpose, and non-infringement. This is **beta software**: it may contain bugs, lose data, or stop working at any time. To the maximum extent permitted by applicable law, the author shall not be liable for any claim, damages, or other liability arising from the use of, or inability to use, the application. Keep backups of anything you can't afford to lose.

### Licensing

Game Twin is **proprietary software. Copyright © 2026 Mert Aslanturk. All rights reserved.** It is distributed free of charge for personal beta testing only. You may not redistribute, sell, decompile, reverse-engineer, or create derivative works from the application or its assets.

---

## Project Transparency

### Why GitHub?
I am a professional software engineer, and GitHub is my primary environment for release management and version tracking. Using GitHub allows for seamless update support via **Obtainium** and keeps hosting costs at $0 while the project is in its hobby phase.

### Note on "Source Code" Downloads
The "Source code (zip)" links in the Releases section are **automatically generated by GitHub**. These zips only contain the repository documentation (README, assets, etc.). The application source code itself is currently private as I evaluate future commercial potential.

### Development & AI
This project is an **Open Beta** and still has some rough edges. I'm polishing them as fast as my schedule allows.

To bring this vision to life alongside a busy full-time job, I used AI-assisted development to accelerate the process. As a developer, I understand the skepticism around AI-generated content. My goal is to use these tools responsibly as a force multiplier, to deliver a high-quality, polished utility for the gaming community. There are no bad intentions here; I'm simply using modern tools to build a better experience for the games we love.

---

## Feedback & Support

- [GitHub Discussions](https://github.com/alpha-minus/game-twin/discussions)
- Email: twinlore.soft@gmail.com

---

**Copyright © 2026 Mert Aslanturk. All rights reserved.**
Distributed free of charge for personal beta testing only.
