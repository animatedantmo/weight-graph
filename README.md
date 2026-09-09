# Weight Graph

An Android app for tracking body weight over time. Log a reading each morning, see the trend on a
chart you can scrub through, and move data in and out as CSV.

Built with Kotlin and Jetpack Compose, storing data locally with Room. No account, no network
access, no analytics — everything stays on the device.

## Features

### Chart

- Line chart of every reading, drawn directly on a Compose `Canvas` with no charting library.
- **Y axis** scales automatically to whatever is on screen, with gridlines snapped to round
  numbers (140 / 160 / 180) rather than whatever an even division produces.
- **X axis** changes format with the span: bare years for multi-year views, `MMM yyyy` for a few
  months, `M/d` for shorter ranges.
- **Scrub to inspect.** Touch and drag across the chart for a vertical crosshair, a red marker
  where it crosses the line, and a readout of that day's exact date and weight. It snaps to real
  readings, so the number shown is always one you recorded.
- **Range selector** — 1 week, 1 month, 1 year, all, or a custom range. Changing range animates
  as a zoom rather than cutting to a new view.
- **Custom ranges** can be typed (slashes are inserted as you type) or picked from a calendar
  with a year selector for jumping back through older data.

### Recording weights

- Add a weight from the `+` button: a modal with the field already focused and the number pad up,
  dated today by default.
- Any date can be chosen, so a missed morning can be filled in later.
- Weights are entered and shown as decimal pounds to one place, e.g. `178.8`.
- Input is validated — non-numeric text and values outside 20–1000 lb are rejected.
- One entry per calendar day. Recording the same day again replaces the earlier value rather
  than creating a duplicate.

### Editing and deleting

- Swipe a row left or right to reveal a delete button. Both the date and the weight stay visible
  while it is open, so it is clear which reading is about to go.
- Deleting a single row asks for confirmation and names the date and weight.
- **Delete all** requires typing `delete` to confirm, warns that it cannot be undone, and points
  at Export first.
- Rows fade in and out, and the rows around them slide into place.

### Importing CSV

Export a sheet from Google Sheets, Excel or anywhere else and import it with the system file
picker. No permissions and no network access are involved.

- **Columns are found automatically** — by header name where there is a header, and by inspecting
  the data where there is not. Column order does not matter and extra columns are ignored.
- A column headed `AM` is preferred over `PM`, for sheets that record both.
- **Dates** are accepted as `M/D/YYYY`, `M/D/YY`, `YYYY-MM-DD`, `D-MMM-YYYY` or `MMM D, YYYY`.
- **Weights** are accepted as `178.8`, `178.8 lb`, `"179.2 lbs"`, `180#` or `1,078.8`.
- **Nothing is dropped silently.** Rows that cannot be read are reported with their line number
  and a reason, and the counts are shown before anything is saved.
- **Nothing is written until you confirm.** The import previews how many entries it found, the
  date range they cover, and how many rows are being skipped.
- Re-importing is safe: existing days are updated, not duplicated.

A minimal file looks like this:

```csv
Date,Weight
9/8/2026,179.4
9/7/2026,179.0
9/6/2026,180.6
```

### Exporting CSV

- Exports every entry, newest first, using the same date and weight formatting shown in the app.
- **Save to device** through the system file picker, anywhere you like.
- **Share** through the standard share sheet — email, Drive, messaging, or anything else that
  accepts a file.
- Exports re-import cleanly, so an exported file works as a backup you can actually restore from.

## Building

Requires a JDK matching the project's toolchain (Android Studio's bundled JBR works):

    export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"

| Task | Command |
| --- | --- |
| Build a debug APK | `./gradlew.bat assembleDebug` |
| Install on a connected device | `./gradlew.bat installDebug` |
| JVM unit tests | `./gradlew.bat testDebugUnitTest` |
| Instrumented tests (device required) | `./gradlew.bat connectedDebugAndroidTest` |

## Project layout

    app/src/main/java/org/animatedantmo/weightgraph/
      data/     Room entity, DAO, database, repository, CSV parsing and export, formatting
      ui/       Compose screens: chart, entry dialog, range selector, action menu

Dates are stored as `epochDay` rather than a timestamp, so "one entry per day" is enforced by a
unique index rather than by convention. Weights are stored in pounds, the same unit used for
entry, display and export, so nothing is converted on the way in or out.

## Requirements and limitations

- **`minSdk` is 36 (Android 16).** Carried over from the project template; the app will not
  install on older devices. Nothing in it actually requires Android 16.
- Room `exportSchema` is currently off. It should be turned on, with `app/schemas/` committed,
  before releasing a version that has to migrate existing data.
- `android.disallowKotlinSourceSets=false` in `gradle.properties` is required for KSP to run
  under AGP 9. Removing it makes Room silently generate nothing.
- Ambiguous dates such as `3/4/2026` are read as US month-first (March 4th).
- Deleting is immediate. There is no undo and no trash — export first if the data matters.
