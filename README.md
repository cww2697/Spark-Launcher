# Spark Launcher

[![Automatic Dependency Submission](https://github.com/cww2697/Spark-Launcher/actions/workflows/dependency-graph/auto-submission/badge.svg)](https://github.com/cww2697/Spark-Launcher/actions/workflows/dependency-graph/auto-submission)

A lightweight desktop game launcher built with Compose Multiplatform for JVM. SparkLauncher aggregates games from
multiple PC stores into a single library and fetches box art from IGDB so your library looks great.

## What it does

- Aggregates libraries from the following PC launchers:
    - Steam
    - EA app (formerly Origin)
    - Battle.net
    - Ubisoft Connect
- Displays a unified Library view with box art and basic game info.
- Lets you configure where each store is installed so SparkLauncher can discover games.
- Integrates with IGDB to fetch cover art (requires your own IGDB credentials).
- Provides a simple Settings window to customize paths, theme, and integration keys.

## Current status and limitations

- Desktop (JVM) target only. Packaging presets are provided for Windows (MSI), macOS (DMG), and Linux (DEB).
- Theme selector currently offers "Default" only (Community themes will be supported in a future release).
- Game discovery depends on correctly configured paths to your store libraries.

## App layout

- Home: Recommendations style carousels for quick access to your favorite games.
- Library: Shows discovered games and their cover art when available.
- Settings: Configure theme, library paths, IGDB credentials, and maintenance actions.

## Settings
SparkLauncher stores its settings in a human-readable JSON file. On Windows the file is created on first run at:

- %APPDATA%/SparkLauncher/config.json
  If %APPDATA% is not available, it falls back to your home directory under SparkLauncher/config.json.

### All settings fields in the Settings window:

- Theme
    - Dropdown with available UI themes. Currently only "Default".
- Integrations
    - IGDB Client ID: Your Client ID from IGDB/Twitch (see below).
    - IGDB Client Secret: Your Client Secret from IGDB/Twitch.
- Libraries
    - Steam: Path to your Steam installation or library folder. Example: C:\Program Files (x86)\Steam or the steamapps
      common path.
    - EA: Path to your EA App installation/library. Example: C:\Program Files\EA Games
    - Battle.Net: Path to your Battle.net installation/library. Example: C:\Program Files (x86)\Battle.net
    - Ubisoft: Path to your Ubisoft Connect installation/library. Example: C:\Program Files (x86)\Ubisoft\Ubisoft Game
      Launcher

### Maintenance actions in Settings

- Reload Libraries: Re-scan the configured store paths to update the Library view.
- Rebuild Caches: Clears and rebuilds cached data such as fetched cover art and discovery results.

IGDB credentials (how to get them)
IGDB uses Twitch authentication. To get credentials:

1) Sign in at https://dev.twitch.tv/ and create an application.
2) Copy the Client ID and generate a Client Secret.
3) Paste both into Settings → Integrations.
   Note: Free usage may be rate-limited. SparkLauncher only needs basic access to fetch cover art data.

## Build from source
### Prerequisites

- JDK 17 or newer (Compose Multiplatform for Desktop requires Java 17+).
- Git (to clone the repository).
- No global Gradle install is required; the included Gradle Wrapper will be used.

### Clone

- git clone https://github.com/your-org-or-user/SparkLauncher.git
- cd SparkLauncher

### Run (development)

- Windows: .\gradlew.bat :composeApp:run
- macOS/Linux: ./gradlew :composeApp:run
  This will launch the app with a development JVM.

## Package a distributable
The project is configured to build native installers using Compose Desktop native distributions:

- Windows (MSI), macOS (DMG), Linux (DEB).

## Commands

- Build all: ./gradlew :composeApp:packageDistributionForCurrentOS
- Or build a specific format for your OS using the appropriate task.
    - Examples:
        - Windows: ./gradlew :composeApp:createDistributable or :composeApp:packageMsi
        - macOS: ./gradlew :composeApp:packageDmg
        - Linux: ./gradlew :composeApp:packageDeb
          On Windows, use .\gradlew.bat instead of ./gradlew.

## Where is the entry point?

- Main class: net.canyonwolf.sparklauncher.MainKt
- Compose configuration: composeApp/build.gradle.kts → compose.desktop.application

## Configuration file example
A typical config.json looks like this:
```json
{
"theme": "Default",
"steamPath": "C:\\Program Files (x86)\\Steam",
"eaPath": "C:\\Program Files\\EA Games",
"battleNetPath": "C:\\Program Files (x86)\\Battle.net",
"ubisoftPath": "C:\\Program Files (x86)\\Ubisoft\\Ubisoft Game Launcher",
"igdbClientId": "your-client-id",
"igdbClientSecret": "your-client-secret"
}
```
You can edit this file while the app is closed. The Settings window also writes to the same file when you click Save.

## Troubleshooting

- The app doesn’t find my games
    - Verify the paths in Settings → Libraries point to the correct install or library folders.
    - Click Reload Libraries after updating paths.
- Covers don’t appear
    - Ensure IGDB Client ID/Secret are set correctly.
    - Check your network connection and try Rebuild Caches.
- Build fails with Java version error
    - Ensure JDK 17+ is installed and selected (JAVA_HOME and PATH).
