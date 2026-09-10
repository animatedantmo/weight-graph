# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Project

An Android app for tracking body weight: a graph over time, a section for entering new
readings, and import of existing history from a CSV file or a Google spreadsheet.

- Package `org.animatedantmo.weightgraph`, Kotlin + Jetpack Compose (Material 3)
- AGP 9.3.2, Kotlin 2.2.10, Gradle 9.5, Room 2.8.4 via KSP
- `compileSdk` 37, `minSdk` 36 (Android 16), `targetSdk` 37
- Target device: Samsung Galaxy S24 Ultra (SM-S928U1), Android 16

## Commands

Gradle needs a JDK on PATH. Android Studio's bundled JBR is the one that matches the
project's `toolchainVersion = 25`:

    export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"

- Build: `./gradlew.bat assembleDebug`
- Install on the connected phone: `./gradlew.bat installDebug`
- JVM unit tests: `./gradlew.bat testDebugUnitTest`
- Instrumented tests (needs the phone connected): `./gradlew.bat connectedDebugAndroidTest`

`adb` lives under the Android SDK, at `$ANDROID_HOME/platform-tools/adb.exe` — on Windows the
SDK defaults to `%LOCALAPPDATA%/Android/Sdk`. The exact path for this checkout is in
`local.properties` (gitignored, machine-specific).

## Side-by-side installs

Each machine signs debug builds with its own `~/.android/debug.keystore`, so a debug build from
a second machine cannot update an install from the first — Android rejects it with
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Rather than uninstall (which deletes the entered weights),
a secondary machine gets its own `applicationId` by setting a suffix in `local.properties`:

    installSuffix=laptop

That gives `org.animatedantmo.weightgraph.laptop`, labelled `Weight Graph (laptop)` with a red
launcher icon instead of indigo, installed alongside the real app. The desktop leaves
`installSuffix` unset and is unaffected.

The two are separate apps with separate Room databases — nothing syncs between them. Moving data
across means Export to CSV from one and Import into the other.

`app_name` and the launcher colour are therefore set with `resValue` in `app/build.gradle.kts`,
not in `res/values/`, since a resource cannot be defined in both. This needs
`buildFeatures { resValues = true }`, which AGP 9 defaults to off.

## Architecture

`data/` holds the persistence layer:

- `WeightEntry` — Room entity. The date is stored as `epochDay` (`LocalDate.toEpochDay()`)
  rather than a millisecond timestamp, so the unique index genuinely enforces one entry per
  calendar day. Weight is stored in pounds, the same unit used for entry and display.
- `WeightDao` — queries and upserts. `OnConflictStrategy.REPLACE` combined with the unique
  index is what makes re-importing a CSV overwrite days instead of duplicating them.
- `WeightDatabase` — database plus app-wide singleton.
- `WeightRepository` — the boundary. Everything above it speaks `LocalDate` and pounds; the
  epoch-day encoding stays an implementation detail.
- `Units.kt` — display formatting and tolerant parsing of pound input.

## Conventions

- Dates are entered and displayed as M/D/YYYY (US).
- Weights are decimal pounds to one decimal place, e.g. `178.8`.
- `exportSchema` is off while the app is pre-release. Turn it on and commit `app/schemas/`
  before shipping a version that must migrate real user data.
- `android.disallowKotlinSourceSets=false` in `gradle.properties` is required: AGP 9's
  built-in Kotlin support otherwise rejects how KSP registers its generated sources, and Room
  generates nothing. Do not remove it.

## General guidelines

- Use the single-line comment style (`//`) for every comment that fits on one line. Reserve
  block syntax (`/* */`, `/** */`) for comments that genuinely span multiple lines.
- Do not run git commands. The repository owner handles all staging, commits, and pushes.
- Do not add files to this repository unless asked for that specific file.
