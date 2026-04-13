# Game Twin - Open Beta

![0-5-0-tablet](https://github.com/user-attachments/assets/193fb199-25a0-4f11-88e9-cb17ed98138d)

**Your game-aware companion for guides, images, checklists, and achievements**

Every time you switch games, you lose your place. The guide tab you had open is gone. The map you bookmarked isn't where you left it. The checklist you started last night is buried three folders deep.

Game Twin keeps all of that tied to whatever game you're playing. Guides, images, checklists, achievements, YouTube links, and search history, organized per game, waiting exactly where you left them. Switch games, and your whole workspace switches with it.

**Open Beta.** Free to use, no purchase or setup required. **Ad-free, offline-capable, no data collection. Requires Android 12+.**
Note: This app was developed with AI assistance.

<p align="center">
  <a href="https://github.com/alpha-minus/game-twin/releases/latest">Download Latest Release</a>
</p>

[![Add Game Twin to Obtainium](https://github.com/ImranR98/Obtainium/raw/main/assets/graphics/badge_obtainium.png?raw=true)](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.twinlore.app%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Falpha-minus%2Fgame-twin%22%2C%22author%22%3A%22alpha-minus%22%2C%22name%22%3A%22Game%20Twin%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%7D%22%7D)

[![Version](https://img.shields.io/github/v/release/alpha-minus/game-twin?include_prereleases&label=version&color=blue)](https://github.com/alpha-minus/game-twin/releases/latest)
[![Android](https://img.shields.io/badge/Android-12%2B-green)](https://github.com/alpha-minus/game-twin/releases/latest)

---

## Features

### Game Detection
- Game Twin would automatically create entries based on the last played game. Multiple options are supported for feature to work.
- **ES-DE.** & **RetroArch.** based detection. (Great for Dual-handheld devices)
- **RetroAchievements** & **Steam.** Last-played detection across any android device via their respective APIs. (Great for tablets and smartphones)

### Guides

- **Offline saving.** Save any html guide as a complete offline copy and continue where you left off.
- **PDF support.** Open PDF walkthroughs directly in the app with text search, bookmarks, and select-to-search.
- **Text guide support.** Plain text guides are rendered cleanly, with a Table of Contents extracted automatically from section headers.
- **YouTube links.** Save YouTube walkthrough URLs as one-tap shortcuts per game.
- **Find on Page.** Fast in-page search throughout any guide.
- **Scroll Markers.** Drop position markers in a guide and jump between them quickly.
- **Online quick search.** Scoped web search per game with fully customizable search templates in-app browser. Set up your preferred sites once and reach them in one tap, no typing the game name every time.
  **Per-game search history.** Your recent searches are saved per game, so you can pick up where you left off without retyping.

### Images

- Save **multiple images per vault** and switch between them. The app remembers your pan/zoom position for each one.
- Drop custom markers for anything you want to remember.
- **Screenshot Capture.** A new button in the image picker grabs whatever's on your screen (or your second screen, Thor folks) and saves it straight into the vault. Perfect for in-game visual notes.
- One-tap switch between Image and Guide views.

> Privacy note on screenshots: Android asks for screen-capture permission once per session and reuses it while you're capturing. Restart the app and it'll politely ask again. The OS never lets an app watch your screen silently. See Android's [Media Projection overview](https://developer.android.com/media/grow/media-projection) if you're curious how it works.

### Checklists

- Per-game checklists inside any vault. Collectibles, side quests, passwords, notes, whatever you want to track.
- Color-tag items with 7 colors, drag to reorder, search, filter, and hide completed ones.
- Bulk **import from text** if you'd rather build your list elsewhere and bring it in all at once.
- Data persists to your storage folder and syncs over Wi-Fi with the rest of your vaults.

### Achievement Tracking

- **RetroAchievements & Steam.** Grid layout with logos, missable/unlocked filters, offline caching, and per-achievement guide search.
- **Auto-last played game detection.** Queries both APIs in parallel and picks your most recently played game automatically, on any android device.

### Library & Sync

- Grid and list views for your game library, with scraped artwork (box, logo, hero, grid).
- **Local Wi-Fi sync.** Share your vault library across devices with no account or setup required.
- **Synced folder support.** Works seamlessly with folder sync apps like DriveSync for cross-device access.

### In-App Updates

The app checks GitHub Releases on launch and lets you download and install new versions without leaving Game Twin. The changelog is shown before you update, and you can trigger a manual check any time from Settings.

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

- Controller supported navigation.
- LED accent support on AYN Thor/Odin (opt in from Settings) for adaptive LED colors based on the current vault artwork.
- External storage support and custom folder locations.
- Low CPU and battery footprint.
- One-tap second-display sleep to save battery.
- **Any Android phone or tablet (Android 12+).** Fully supported.

---

![549692130-9e18ea9a-a89f-4bf0-af7a-60fa0fd76c55](https://github.com/user-attachments/assets/3bee69c7-1ab9-46aa-85db-5046a6a66e63)

## Getting Started

1. Download the latest APK from [Releases](https://github.com/alpha-minus/game-twin/releases/latest) or add via Obtainium using the button above.
2. Install on your device (Android 12+).
3. Add your RetroAchievements/Steam credentials, or point Settings at your RetroArch/ES-DE folder for auto game detection. 
4. Launch a game. Game Twin will create a library entry automatically.
5. Start adding your resources via file selection or the built-in browser.

For a full walkthrough, see the [YouTube Setup Guide](https://www.youtube.com/results?search_query=game+twin+setup).

---

## Privacy

Game Twin does not collect usage data or personal information. The only network calls are image scraping for game artwork, RetroAchievements and Steam API queries, the in-app update check against GitHub Releases, and beta license validation.

## Project Transparency

### Why GitHub?
Because of my daily job, I’m very familiar with GitHub and use it regularly as my primary platform for release management and version control. This also enables seamless update distribution through Obtainium while keeping hosting costs at zero during the project’s hobby phase.

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

**Copyright 2026 Mert Aslanturk. All rights reserved.**
For personal beta testing only.
