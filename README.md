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
- 0.4.0: skips and misses with an allowance per goal, reasons, going over the limit (half the
  points and a penalty task right away), streaks, Skip on notifications, and a privacy lock for
  the things the user picks.
- 0.5.0: gap check-ins, a review of each day, week and month with stats, and more for the
  privacy lock.
- 0.6.0: celebration GIFs for each goal, from the app or the user's own through the Photo
  Picker.
- 0.7.0: clear tick buttons, the full emoji set, "My day starts at", a time for each of a goal
  done several times a day (or spread out for you), and a morning plan.

- 0.8.0: snooze, keep reminding until done and +1 on reminders; a backup reminder and an
  automatic backup to a folder the user picks.

- 0.9.0: routines, a habit with a checklist inside.
- 0.10.0: a focus timer, a weekly summary, filling in times every few hours, copying a goal
  and bringing back a removed one.
- 0.11.0: the user's own miss reasons, notes on ticks, and fixing when a tick was done.
- 0.12.0: categories for goals, shown one at a time on Today, and charts in the review.
- 0.13.0: badges for milestones, and a weekly points target on Today.
- 0.14.0: mystery rewards in the shop, and a balance and streaks widget.
- 0.15.0: light, dark, phone or pure black; the app's colour from ready-made ones, the
  wallpaper or any colour from a colour picker; and a colour for each goal.
- 0.16.0: Today as a grid of big tiles, card and text sizes, and a gradient or the user's own
  photo behind Today.
- 0.17.0: choosing, hiding and ordering Today's sections; choosing the tabs and the one the app
  opens on.
- 0.18.0: renaming points (coins, stars, XP or your own words), three bundled fonts, and
  celebration settings: sound, buzz, how much confetti and a GIF for every goal.
- 0.19.0: app icon choices, and the widgets solid in the app's colours or see-through.
- 0.20.0: goals with an amount in the user's own unit, like 20 pages or 1 mile, entered as they
  go, with a typed time to budget or a usual speed learned from Start and Stop.
- 0.21.0: a notification while timing, with Stop on it, and amounts more than once, like 1 mile
  3 times a week.
- 0.22.0: a planner for the coming two weeks, and moving a day's goal to another day of its week.
- 0.23.0: quick to-dos, with an optional date, that pay points; one with a date that isn't done is
  a miss.
- 0.24.0: a first-run guide with example goals, search in the Goals list, and shortcuts on the
  app's icon.
- 0.25.0: steps and reminders on to-dos, to-dos in the widget and the morning plan, and a Done
  list.
- 0.26.0: suggested points and prices, and a difficulty setting that can ramp up over time.
- 0.27.0: a balance check of prices and points, and savings interest.
- 0.28.0: a boost for the goal missed most, a perfect-day bonus, and prices that rise on repeat
  buys.
- 0.29.0: an Undo bar, and a quick settings tile that ticks the next goal.
- 0.30.0: goals that repeat some days after they were last done.
- 0.31.0: a day timeline, where a goal can be dragged to another time for that day.
- 0.32.0: the phone's own calendar in the planner, the timeline and Today, read on the phone, with
  pacing leaving its events out.
- 0.33.0: goals to quit, with "I slipped", and challenges of so many days in a row with a bonus.

Every step in CLAUDE.md's build order is built. The iPhone app waits for a way to install it.

The emoji list is built from Unicode's emoji data via the MIT-licensed `unicode-emoji-json`
package; its notice ships in `app/src/main/assets/emoji-NOTICE.txt`. The fonts Nunito, Atkinson
Hyperlegible and Lora are under the SIL Open Font License; their notices ship in
`app/src/main/assets/fonts-NOTICE.txt`. The celebration sounds are made by `tools/make-sounds.py`.

## How it is built

- `core/` holds the rules in Kotlin Multiplatform, shared by Android and the planned iPhone app,
  with their unit tests. `app/` is the Android app, in Jetpack Compose.
- On every push CI also runs the shared tests on an iPhone simulator, so the shared code stays
  ready for the iPhone app.
- GitHub Actions, on every push: unit tests, emulator tests (ticking and buying, skips and
  misses, reminders, backups and database updates), a release build, the signature check and
  the network-permission check.
- The release APK is built without secrets, then signed in a separate job that runs no build
  code. Its certificate must match the one in CLAUDE.md, or the build fails.
- A push to `main` publishes a Release when `appVersionName` in `gradle.properties` has no
  Release yet. The notes come from `release-notes/<version>.md`.
- The database layout of each version is committed in `app/schemas`. A version that changes
  it must add a migration, so updates never lose data.
