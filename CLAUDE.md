# Checklist app — house rules (working name)

An app for Android, and later iPhone, for setting goals, ticking them off and earning points, then
spending the points in a shop of real-life rewards the user stocks and prices. It is for one
person: no sharing, no accounts, no sync.

Designed on 2026-11-08. Plain bullets are the owner's decisions. Bullets marked **(default)** are
suggestions the owner has not overruled and can still change. Anything not written here is not
decided, so ask the owner rather than filling it in.

## This repository is public

Anyone can read the code, the commits, the issues, the Actions logs and the Releases. So none of
them may ever contain the owner's name or username, what the owner said about the app (feedback,
quotes, requests), or any of the owner's own goals, rewards, reasons or exports.

- Write "the owner" or "the user".
- Decisions are recorded as decisions, not as who said what.
- Release notes are written for a non-developer and name nobody.
- Commits are authored as `Claude <noreply@anthropic.com>`.

## No internet

The app has no network access unless the owner decides otherwise for this app.

- **No network permission, checked on the APK.** Declare
  `<uses-permission android:name="android.permission.INTERNET" tools:node="remove"/>`, and have CI
  fail the build if `aapt2 dump permissions` on the release APK lists `INTERNET`,
  `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` or any other network permission. Libraries add
  permissions through manifest merging, which is why the check is on the built APK.
- **Nothing that would need it.** No GIF search, cloud backup or sync, analytics, crash reporter,
  update check or Play Services. When a feature would be easier online, offer the offline version
  and ask the owner.
- **No phone backup either (default).** `android:allowBackup="false"` and data-extraction rules
  that exclude everything, because Google or Samsung backup would upload the goals and the reasons
  for misses. The backup is the export file below.

## Goals

- Habits, which repeat, and projects, which are made of steps.
- Repeats are very flexible, with free choice of the days of the week. The goal editor offers
  every pattern below **(default list)**:
  - every day, or every few days (every 2nd, 3rd, … day)
  - chosen days of the week in any combination, every week or every 2nd, 3rd, … week
  - a number of times per week, month or year, on any days
  - chosen dates of the month (the last day included), or a weekday of the month such as the
    first Monday or the last Friday, every month or every few months
  - a date every year, or once on a single date
  - an optional start date and end date for any of these
- Each goal has a name, an icon, the points it pays, how often it repeats, its allowance, an
  optional reminder, and optionally its own celebration GIF.
- The user can reorder goals, and rewards in the shop.
- Counted goals, like "8 glasses", pay for each tick plus a bonus for finishing **(default)**.
- Each step of a project pays, and finishing the project pays a bonus. A project can have a
  deadline, and missing it counts as a miss **(default)**.
- Changing a goal applies from then on, never to the past **(default)**.

## Points and the shop

- Ticking a goal earns its points. The shop holds real-life rewards with prices the user sets.
- No limits on how often a reward can be bought.
- Big rewards get a "saving for" progress bar.
- Each price also shows how long it takes the user to earn, like "≈ 4 days of your usual", worked
  out from their own history **(default)**.
- A bought reward waits in an inventory until it is used **(default)**.
- A bought reward can be returned for a full refund until it is used.
- Undoing a tick takes its points back **(default)**.
- A points history lists every change to the balance: what it was for, when, and how much.
- The balance can go negative. A reward can't be bought for more than the balance, so a negative
  balance means the shop is effectively closed until the user earns back.

## Celebrations

- Finishing something shows feedback in the app, like a GIF or an animation.
- It scales with the win: a quick burst and a buzz for a tick, full screen for finishing a project,
  and its own for buying a reward **(default)**.
- The GIFs come with the app, or the user picks one with the Photo Picker. That hands over a single
  file and needs no storage permission.

## Pacing and the order to do things

- The app tells the user how quickly they need to go to get everything done on time.
- A goal can have a length (how long it takes), a clock time (when to do it), both, or neither.
  Timed goals keep their time; untimed goals share what is left evenly.
- Pacing counts down to a finish time and date. Settings can hold defaults, but the app never
  assumes an end date the user hasn't set.
- Goals due over a week, month or year, and projects with a deadline, are paced too, like "2 left,
  about one every 2 days".
- Each goal can have its own finish-by time, and a date where it fits. Goals without one use the
  default finish time in Settings, and until that is set, today's list shows no pace
  **(default)**.
- A goal with no finish date, like a "3 times a week" goal or a project with no deadline, is not
  paced until the user gives it one **(default)**.
- Today's list can be shown in a recommended order that makes each thing more likely to get done.
- The recommended order **(default)**: goals with a clock time sit at that time; then the earliest
  finish-by first; goals falling behind their pace move up; goals the user usually does around
  this time of day (learned from their own ticks) come next; quick ones before long ones. The user
  can switch Today between the recommended order and their own order.

## Streaks

- Streaks count days. For the main streak the user sets what a day needs: a number of goals
  done, or a percentage of the goals due that day. At 90%, the other 10% can be missed or skipped
  without breaking it.
- Several streaks run side by side: goals done (the target above), no misses, no skips, and
  others **(default: never over the limit, and perfect days with everything done)**.
- Today shows the current streaks. When every streak is running at once, it is shown big and
  flashy.
- A goal due a number of times per week or month counts on the day its period ends
  **(default)**.

## Skips, misses and the limit

- **One allowance per goal, shared by skips and misses**, counted per week or per month. It resets
  when a new calendar week or month starts. The week starts on Monday **(default)**.
- **A skip** is said in advance. It needs no reason; it is grace. It uses up allowance. The user
  can skip any time until the day ends **(default)**.
- **A miss** is a goal that was neither done nor skipped when its period ended. Every miss needs a
  reason. The user picks one from a short list (forgot, no time, ill, chose not to) and can add a
  note **(default)**.
- **"I forgot to tick it"** is an option on the miss screen. It ticks the goal and refunds any
  penalty that miss caused.
- **The user sets when the day ends**, like 4am, so a late night isn't a miss **(default)**.
- **Within the allowance**, a miss costs only its reason **(default)**.
- **Over the limit**, every skip or miss needs a reason and costs **half the points the goal
  pays**, rounded down **(default)**. The penalty is kept deliberately mild. The balance can go
  negative.
- **Every time the user goes over the limit, there is a penalty task, and it is done right then.**
  There's no putting it off. When it comes up, the user picks either to make up one of the missed
  goals, or to draw a random one from a preset list of penalty tasks they write. Penalty tasks pay
  nothing **(default)**.
- **When "right then" is (default):** a skip over the limit warns the user and triggers its
  penalty the moment the skip is confirmed. A miss is only found when the day ends, often while the
  user is asleep, so its penalty waits for the next time the app opens and fills the screen until
  it is ticked done.
- **There is no pause button.**

## Gaps

- When it looks like the user stopped for a while, the app flags that stretch and asks what
  happened. A gap is 2 or more days in a row with nothing ticked, or a big point loss within one
  week. The user can change both numbers **(default)**.
- One reason and one penalty task cover the whole stretch. Points still come off for every miss in
  it.
- The stretch is marked in the history, with what the user wrote.

## Opening the app (default)

1. Misses waiting for a reason, or the gap check-in.
2. The penalty task, if anything went over the limit.
3. Today's list, with the balance at the top.

## Notifications and the widget

- Notifications at set times, the way a calendar app reminds you. They are scheduled on the phone
  and need no internet.
- A home-screen widget that looks like a calendar, to see the schedule at a glance.
- A goal with a clock time notifies at that time, and the user can add an earlier alert, like a
  calendar's "10 minutes before" **(default)**.
- Notifications have Done and Skip buttons. A Skip that would go over the limit opens the app
  instead, because it needs the warning, the reason and the penalty **(default)**.
- The widget can be resized: small shows today's agenda with times and tick buttons, larger shows
  the week or the month **(default)**.

## Review

- Day, week and month views, covering:
  - done, skipped and missed for each goal
  - which reasons come up most
  - points earned, spent and lost
  - longest runs
  - gaps marked on the calendar, with what was written at the check-in
- Stats, including the most skipped, most missed and most consistent goals **(default, also:
  most ticked, best and worst days of the week, points per goal, current and longest streaks)**.

## Privacy lock

- There is no lock on the whole app. In a Privacy menu in Settings, the user picks which things
  sit behind the lock, and only those ask to be unlocked.
- Unlocking uses the phone's own lock: fingerprint, face, or the phone's PIN or pattern. It stays
  unlocked until the user leaves the app.
- What can be put behind the lock **(default)**: reasons and notes for skips and misses, gap
  check-ins, the points history, the review and stats, the shop and bought rewards, and single
  goals marked private. Locked things don't show in the widget or reminders either.
- The app's preview in the recent-apps screen is hidden. On Android 13 and later that leaves
  screenshots working. On older phones it also blocks screenshots, so there it is a switch in the
  Privacy menu **(default)**.

## Backup (default)

- Export everything to a file, and import it back, through the system dialogs (`CreateDocument` and
  `OpenDocument`). Where that file goes is the user's choice.
- The app makes no copy of its own anywhere else.

## iPhone

- The app is planned for iPhone too. The code is restructured now, before 0.2.0, so Android and
  iPhone share it through Kotlin Multiplatform and each feature is built once.
- The iPhone app itself is built once there is a way to install it: a paid Apple Developer account
  (TestFlight) or a Mac. Until then, Android releases continue.
- Each phone keeps its own data. The export file is the same on both, so data moves between them
  only by exporting on one and importing on the other. No sync.
- The shared code holds the rules, repeats, pacing, the recommended order and the export format,
  and CI builds and tests it for iPhone on every push. The Android screens are written so they can
  move to Compose Multiplatform when the iPhone app starts. iPhone widgets are written separately
  in Swift **(default)**.
- iPhones have no internet permission to remove, so there "no internet" means no networking code,
  checked in the code rather than on a permission **(default)**.

## Builds and releases

- Kotlin Multiplatform for the shared code, Jetpack Compose for the Android screens. Work lands on
  `main` without pull requests.
- GitHub Actions builds the APK. **The owner installs from the Releases page in the phone's
  browser**, so every build meant to be tried is a Release with the APK attached.
- **Every release is signed with the same key**, or Android refuses to install it over the
  previous one. The key is in repository secrets, never in the repo: `SIGNING_KEYSTORE_B64` (a
  base64 PKCS12 keystore), `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.
  The owner keeps a backup offline. Never generate another key for this app.
- CI checks the release APK with `apksigner verify --print-certs` against this certificate, so a
  build signed with a debug key never becomes a Release:
  SHA-256 `7C:83:F8:FD:1E:3D:8B:B7:2C:D5:15:AC:24:2E:BE:3B:9A:1B:21:6F:66:7E:72:BC:7A:63:08:7D:3E:15:38:3E`.
- **Releases come only from pushes to `main`.** A public repo takes pull requests from strangers.
  Never use `pull_request_target`, and never give the signing secrets to a workflow that runs code
  from a pull request.
- CI on every push: the network-permission check, the signature check, and the tests.

Build order **(default)**, each step a Release the owner can try:

1. Goals, ticking, points and the shop. Released as 0.1.0.
2. First the code is restructured to be shared with iPhone (no Release on its own). Then export
   and import (decided: before skips and misses), flexible repeats, reordering, the points
   history, returning a bought reward, hiding the recent-apps preview, pacing and the recommended
   order.
3. Notifications and the calendar widget. They build on the times and repeats from step 2; the
   Skip button arrives with step 4.
4. Skips, misses, the limit and penalties, with streaks and the privacy lock.
5. Gaps, the review screen and stats.

## Talking to the owner

- Short and plain. The owner is not an Android developer. Say what changed, what to try, and what
  is not verified.
- "Done" means a Release the owner can install, not a green check.
