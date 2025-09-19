# Copilot Coding Agent Onboarding: Spark-Launcher

Welcome! This guide is for Copilot coding agents working on
the [Spark-Launcher](https://github.com/cww2697/Spark-Launcher) repository. Follow these high-level instructions to
minimize rejected PRs, build/test failures, and wasted exploration time. Trust these instructions unless you discover
errors or missing information—search only if necessary.

---

## High-Level Repository Overview

- **Purpose:**  
  Spark-Launcher is a lightweight desktop game launcher (Compose Multiplatform for JVM) that aggregates games from
  Steam, EA, Battle.net, and Ubisoft Connect, displaying them with IGDB-sourced box art and metadata.

- **Type:**  
  Desktop application (JVM target only).

- **Languages/Frameworks:**
    - **Primary Language:** Kotlin
    - **Framework:** JetBrains Compose Multiplatform for Desktop
    - **Build Tool:** Gradle (wrapper included, no global install required).
    - **Target OSes:** Windows (MSI), macOS (DMG), Linux (DEB).

- **Size & Structure:**  
  Moderate size; main logic and UI in `composeApp/` subdirectory; entry point at `net.canyonwolf.sparklauncher.MainKt`.

---

## Building, Running, and Validating Changes

### Prerequisites

- **Java:** JDK 17+ (required for Compose Multiplatform).
- **Git:** Used for source control.
- **Gradle:** Use the included wrapper; do not install Gradle globally.

#### Environment Setup

- Always confirm JAVA_HOME and PATH point to JDK 17+.
- No further global dependencies required.
- The project does not require Docker or system-level dependencies beyond Java.

### Typical Workflow

#### 1. Clone and Prepare

```sh
git clone https://github.com/cww2697/Spark-Launcher.git
cd Spark-Launcher
```

#### 2. Run (Development Mode)

- **Windows:** `.\\gradlew.bat :composeApp:run`
- **macOS/Linux:** `./gradlew :composeApp:run`

> _Note: Always run `gradlew` via the wrapper. Do **not** use a global Gradle._

#### 3. Build (Distributable Packages)

- **Build for current OS:**  
  `./gradlew :composeApp:packageDistributionForCurrentOS`
- **Specific formats:**
    - Windows: `./gradlew :composeApp:createDistributable` or `:composeApp:packageMsi`
    - macOS: `./gradlew :composeApp:packageDmg`
    - Linux: `./gradlew :composeApp:packageDeb`

_On Windows, replace `./gradlew` with `.\\gradlew.bat`._

#### 4. Testing & Validation

- There is no explicit test suite documented in README.
    - If tests exist, they should be run with `./gradlew test` or similar; check for a `test` task in Gradle files.
    - **Validation:** Successful build and app launch are primary check-ins.

#### 5. Linting

- If present, linting is typically via `./gradlew lint` or similar.
    - No linting configuration is documented—search for Ktlint or Detekt config files if needed.

#### 6. Configuration

- Settings are stored in a human-readable JSON file:
    - Windows: `%APPDATA%/SparkLauncher/config.json`
    - Otherwise: `$HOME/SparkLauncher/config.json`
- This file is created on first run and managed via the app’s Settings window.

#### 7. Maintenance Actions

- Use the Settings window in the app to trigger:
    - **Reload Libraries**: Updates game library view.
    - **Rebuild Caches**: Clears and rebuilds cached data (cover art, discovery results).

#### 8. IGDB Credentials

- Required for box art fetching.
    - Obtain from [Twitch Developer Portal](https://dev.twitch.tv/).
    - Add `Client ID` and `Client Secret` in Settings → Integrations.

---

## Project Layout & Key Files

- **Entry Point:**
    - Main class: `net.canyonwolf.sparklauncher.MainKt`
    - Compose config: `composeApp/build.gradle.kts` (`compose.desktop.application` block)
- **Directories:**
    - `composeApp/`: Main source and UI logic.
    - `.github/`: Contains workflows and CI/CD.
- **Root Files:**
    - `README.md` (this summary)
    - `.gitignore`
    - `gradlew`, `gradlew.bat`, `gradle/wrapper/`: Gradle wrapper scripts and properties.

### Configuration and Validation

- **CI/CD:**
    - GitHub Actions present:
        - Dependency Graph auto-submission (`.github/workflows/dependency-graph/auto-submission.yml`)
        - CodeQL analysis (`.github/workflows/codeql.yml`)
    - These check for code quality and security before PR merge.
- **Manual Validation:**
    - Always build (`./gradlew :composeApp:packageDistributionForCurrentOS`) and launch the app to confirm changes work.
    - If making changes to game discovery or IGDB integration, validate using the Settings window and config file edits.

### Error Handling & Workarounds

- **Common Issues:**
    - **Java version errors:** Ensure JDK 17+—older versions will fail.
    - **Game paths not detected:** Use Settings → Libraries and click Reload Libraries.
    - **Box art missing:** Verify IGDB credentials and network connectivity.

---

## Best Practices for Copilot Agents

- **Always use Gradle wrapper, never a global Gradle.**
- **Always check Java version before building.**
- **Trust these instructions first; search the repository only if information is missing or fails in practice.**
- **Build and launch the app after changes to validate, especially for UI or integration updates.**
- **When in doubt, review README.md and Settings window for configuration and troubleshooting.**

---

_Reference: README.md and project structure as of September 2025._