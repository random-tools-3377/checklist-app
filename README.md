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

### Windows

Download the newest `checklist-<version>.msi` from the [Releases page](../../releases) and open
it. The installer isn't signed, so Windows may warn that it doesn't know the app: choose
**More info**, then **Run anyway**. It installs for your Windows account only, with a Start menu
entry and a desktop shortcut, and a newer `.msi` installs over the old one.

The data stays on that computer, in your account's `AppData\Local\Checklist` folder (never roamed to a server); nothing is
sent anywhere. To move goals between the computer and a phone, export them in Settings → Backup
on one and import the file on the other. Reminders show only while the app is open.

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
- 0.34.0: a streak bonus, and a daily mood check-in shown in the review.
- 0.35.0: a Features page where extras are turned on, starting off; empty things hide; search in
  Settings.
- 0.36.0: reminders at the usual time, and habit chains.
- 0.37.0: a quick-add to-do widget, and choosing several goals to change or remove together.
- 0.38.0: weekly planning, and a year in review.
- 0.39.0: the points history as a spreadsheet, and restoring an automatic backup.
- 0.40.0: tablets, and phones turned on their side.
- 0.41.0: a colour-blind friendly look.
- 0.42.0: the optional AI, in a separate app (Checklist AI), and a new habit from a sentence.
- 0.43.0: AI-suggested steps for a project, and a note on your week.
- 0.44.0: voice on Today, through Checklist AI.
- 0.45.0: a character with levels and six skills, and badge tiers.
- 0.46.0: the character drawn, styled your way, with gear from its skills.
- 0.47.0: daily quests, combos, and racing last week.
- 0.48.0: items, a boss each week, and lucky ticks.
- 0.49.0: bet on yourself, and streak shields.
- 0.50.0: paths and titles for the character.
- 0.51.0: the character checks in, answers your mood, and jumps for joy.
- 0.52.0: ideas to level up each skill, and always more of them.
- 0.53.0: a today widget to tick off the day from the home screen; widgets show ticks straight away.
- 0.54.0: on a hard day, the character counts each thing done as a win.
- 0.55.0: the app on the iPhone (an unsigned .ipa for SideStore or AltStore); the screens are shared.
- 0.56.0: rewards priced in money, with points prices that work themselves out and keep in step.
- 0.57.0: a budget: pay that fills up as you work, spending in categories with limits, and rewards bought.
- 0.58.0: bills, saving goals, a report for each month, today's pay on Today, and a budget game.
- 0.59.0: a budget widget, every number for points changeable, and help and tips on every screen.
- 0.60.0: the app on Windows, and a mood check-in once the day's done, with what lifts your mood.
- 0.61.0: a mood journal: notes and what's behind each mood, and what goes with better days; every card on Today and the tabs can be put in any order.
- 0.62.0: a check of the whole app, with 58 fixes: streaks, reminders, the review, privacy, the budget, backups, and iPhone and Windows.
- 0.63.0: a short setup for new users, guides only when asked for, in groups with search; bets removed; Completed goals; a Settings search that finds everything.
- 0.64.0: a skip is never done and never held against the day, and can be taken back; misses put right later; an evening check; best streaks.
- 0.65.0: your own celebration sounds and an edge glow; a calendar widget with the days ahead and the month; deadline reminders; a clearer timeline and week plan; editors that ask before discarding.
- 0.66.0: a page for each goal, a fuller review, more spreadsheets; a fuller mood journal with a reminder; focus time kept, with breaks.
- 0.67.0: the pace card no longer takes a late goal for a shortage of time; a money value on any reward, paying from savings, last month's leftover into savings, and a Used list.
- 0.67.1: the Money game says its daily figure is a line to stay under, and takes the user's own daily amount.
- 0.67.2: the calendar widget is 4 by 4 when added, with switches for what it shows.
- 0.68.0: quick Spent, bill reminders, pay from a date; a reward to save for on Today; a passphrase for backups and Erase everything; Windows backup folder and reminders with the window closed; at most goals.
- 0.69.0: nudges when a goal is late, worded differently each time, with Stop for today.
- 0.70.0: Ask a question and How am I doing?; a Today that says what each tick changed and what is within reach; a character that moves and reacts; badge progress.

Every step in CLAUDE.md's build order is built. The iPhone app is installed with SideStore or
AltStore (see the 0.55.0 release notes).

The emoji list is built from Unicode's emoji data via the MIT-licensed `unicode-emoji-json`
package; its notice ships in `shared/src/commonMain/composeResources/files/emoji-NOTICE.txt`. The fonts Nunito, Atkinson
Hyperlegible and Lora are under the SIL Open Font License; their notices ship in
`shared/src/commonMain/composeResources/files/fonts-NOTICE.txt`. The celebration sounds are made by `tools/make-sounds.py`.

## How it is built

- `core/` holds the rules in Kotlin Multiplatform, shared by Android and the iPhone, with their
  unit tests. `shared/` holds the database (Room) and every screen (Compose Multiplatform), also
  for both; what only a phone can do goes through `Phone`, which each phone fills in. `app/` is
  the Android app around them, with its reminders, widgets and tile; `iosApp/` is the iPhone app
  (a small Swift shell, made into an Xcode project by XcodeGen).
- On every push CI also runs the shared tests on an iPhone simulator, and builds the iPhone app
  as an unsigned `.ipa`, which SideStore or AltStore signs on the phone with the owner's Apple ID.
  The iPhone app has no networking code, and CI checks that in the code and in the built app.
- `desktop/` is the Windows app: the same screens in a window, with the computer's side in
  `shared/src/jvmMain` (`DesktopPhone`). On every push CI builds it on Windows as an unsigned
  `.msi` with its own Java inside, opens it to check it keeps running, and checks its code has no
  networking (`scripts/check-no-network-desktop.sh`). Its icon is drawn by
  `tools/make-windows-icon.py`.
- GitHub Actions, on every push: unit tests, emulator tests (ticking and buying, skips and
  misses, reminders, backups and database updates), a release build, the signature check and
  the network-permission check.
- The release APK is built without secrets, then signed in a separate job that runs no build
  code. Its certificate must match the one in CLAUDE.md, or the build fails.
- A push to `main` publishes a Release when `appVersionName` in `gradle.properties` has no
  Release yet. The notes come from `release-notes/<version>.md`.
- The database layout of each version is committed in `shared/schemas`. A version that changes
  it must add a migration, so updates never lose data.
