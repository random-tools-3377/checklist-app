# Checklist

An Android app for setting goals, ticking them off and earning points, then spending the points
in a shop of real-life rewards you stock and price yourself. It is for one person: no accounts,
no sharing, no sync.

The design and the house rules are in [CLAUDE.md](CLAUDE.md).

## Private and offline

- The app has no internet access. The manifest removes every network permission, and CI fails
  the build if the release APK asks for one.
- Phone backup and phone-to-phone transfer are off, so nothing is uploaded anywhere.
- Everything is kept in a database on the phone. Settings → Backup exports it to a file you
  choose, and imports it back; that file is the only copy anywhere else.

## Installing

Open the [Releases page](../../releases) in the phone's browser, download the newest
`checklist-<version>.apk`, open it and allow installing from the browser if Android asks.
Every release is signed with the same key, so a new version installs over the old one and
keeps the data.

## What is built

- 0.1.0: goals (habits and projects), ticking, points and the shop.
- 0.2.0: flexible repeats, lengths and clock times, pacing and a recommended order, export and
  import, a points history, returning bought rewards, reordering, and a hidden recent-apps
  preview.
- 0.3.0: notifications at set times with a Done button, and a calendar widget for the home
  screen.

Next: skips, misses, the allowance, penalties,
streaks and the privacy lock; then gaps, the review screen and stats. The data is laid out so
these fit in: every tick records the window it counts for, and the balance is the sum of a
ledger whose entries have a kind, so penalties and refunds become new kinds of entry.

## How it is built

- `core/` holds the rules in Kotlin Multiplatform, shared by Android and the planned iPhone app,
  with their unit tests. `app/` is the Android app, in Jetpack Compose.
- On every push CI also runs the shared tests on an iPhone simulator, so the shared code stays
  ready for the iPhone app.
- GitHub Actions, on every push: unit tests, an emulator test that ticks a goal and buys a
  reward, a release build, the signature check and the network-permission check.
- The release APK is built without secrets, then signed in a separate job that runs no build
  code. Its certificate must match the one in CLAUDE.md, or the build fails.
- A push to `main` publishes a Release when `appVersionName` in `gradle.properties` has no
  Release yet. The notes come from `release-notes/<version>.md`.
- The database layout of each version is committed in `app/schemas`. A version that changes
  it must add a migration, so updates never lose data.
