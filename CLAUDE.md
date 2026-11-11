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
- A goal done several times a day can have a time for each one, like 9 am and 9 pm, each with
  its own reminder. Without set times, the app spreads them evenly between when the user's day
  starts and the finish time, and Today shows when the next one is due ("next one by 2:30 pm").
- **Routines:** a goal with a checklist inside, like "Morning: water, stretch, make bed", ticked
  item by item. **Details (default):** a routine is a habit; it is done, and pays its points,
  when every item is ticked. Items pay nothing on their own, and start fresh each time it is due.
- Icons are picked from the full standard emoji set. **Details (default):** grouped by category,
  with search by name, a skin tone choice, and the most recent ones first. Emoji the phone can't
  show are left out. The list comes from Unicode's emoji data (via the MIT-licensed
  `unicode-emoji-json` package) and is bundled with the app.
- A goal can have an amount in the user's own unit, like 20 pages or 1 mile. The user enters how
  much they did each time, and it is done, and pays its points, when the amount is reached. For
  pacing, how long it takes comes from a time the user types in for the whole amount, or is
  learned from timing it with Start and Stop; a typed time wins until it is cleared.
  **Details (default):** the usual speed comes from the latest 10 timings. Done from a reminder,
  the widget or "I forgot to tick it" enters what is left. A goal with an amount is done once
  each time it is due, so it isn't also counted or a routine. One timing runs at a time, and
  keeps running when the app is closed.
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
- A reward can have what it costs in money. Its price in points then works itself out from that,
  and keeps in step as the goals change, so rewards of very different costs are priced fairly.
  **Details (default):** the weekly treat budget in Settings links money and points: doing
  everything due in a week earns that much in rewards, at Normal. Typing a money value turns
  "Price follows what it costs" on for that reward, and it can be turned off to set the price by
  hand. Prices move when what a full week of goals pays drifts more than a tenth from the rate in
  use, or the budget changes. Rewards given a money value before 0.56.0 keep their price until it
  is turned on. Bought rewards keep what they cost.

## Celebrations

- Finishing something shows feedback in the app, like a GIF or an animation.
- It scales with the win: a quick burst and a buzz for a tick, full screen for finishing a project,
  and its own for buying a reward **(default)**.
- The GIFs come with the app, or the user picks one with the Photo Picker. That hands over a single
  file and needs no storage permission.
- **Details (default):** four GIFs come with the app (confetti, a star, fireworks, a big tick),
  drawn for this project by `tools/make-celebration-gifs.py`. A goal with its own GIF shows it
  full screen when it is finished, in place of the usual burst. A picked GIF (up to 10 MB) is
  copied into the app's own storage and goes in the backup file. GIFs move on Android 9 and
  later; on Android 8 they show still.

## Pacing and the order to do things

- The app tells the user how quickly they need to go to get everything done on time.
- A goal can have a length (how long it takes), a clock time (when to do it), both, or neither.
  Timed goals keep their time; untimed goals share what is left evenly.
- Pacing counts down to a finish time and date. Settings can hold defaults, but the app never
  assumes an end date the user hasn't set.
- Goals due over a week, month or year, and projects with a deadline, are paced too, like "2 left,
  about one every 2 days".
- Settings hold "My day starts at". Spreading a goal's times over the day needs it, so until it
  is set they aren't spread.
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
- The main streak's target starts at 90%. Days with nothing due neither count nor break a streak.
  A skip, or going over the limit, breaks its streak the same day **(default)**.

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
- **Details (default):** each goal starts with 1 skip or miss allowed per week, changeable per
  goal, per week or month. Skipping a single-day goal skips that day; skipping a "so many times a
  week" goal skips one of its times. A skipped goal can still be ticked. A project's missed
  deadline counts against its allowance in the week of the deadline and, over the limit, costs
  half its finishing bonus; "I forgot to tick it" isn't offered for it. A penalty can be paid off
  by making up any goal skipped or missed in the last two weeks. Misses are first looked for from
  the day this was built, never further back.

## Gaps

- When it looks like the user stopped for a while, the app flags that stretch and asks what
  happened. A gap is 2 or more days in a row with nothing ticked, or a big point loss within one
  week. The user can change both numbers **(default)**.
- One reason and one penalty task cover the whole stretch. Points still come off for every miss in
  it.
- The stretch is marked in the history, with what the user wrote.
- **Details (default):** a big point loss is 50 points or more taken off for going over the limit
  within any 7 days in a row. Days with nothing due neither count toward a gap nor break one.
  Gaps are looked for from the day tracking began (the first time the app checked for misses),
  never further back, and at most 60 days back. A gap that is still unanswered grows if the
  stretch carries on. Its misses need no reasons of their own and owe no penalty tasks of their
  own; the gap's one penalty task covers them.

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
- Today's tick button says what it does: "Done" for a single goal, "+1" with "0 of 2" for a goal
  done several times.
- Reminders can be snoozed, can keep reminding until the goal is done, and have a +1 button for
  a goal done several times. **Details (default):** Snooze puts a reminder off for the length
  chosen in Settings (10 minutes, 30 minutes or an hour; 10 to start); "keep reminding" repeats
  every 30 minutes until the goal is done or its finish time passes (at most 12 times), set per
  goal. The backup reminder shows on Today, with "Back up" and "Later" (tomorrow).
- A morning plan: one notification when the user's day starts, with today's list and the pace
  **(default: on once "My day starts at" is set, and can be turned off)**.
- Notifications have Done and Skip buttons. A Skip that would go over the limit opens the app
  instead, because it needs the warning, the reason and the penalty **(default)**.
- The widget can be resized: small shows today's agenda with times and tick buttons, larger shows
  the week or the month **(default)**.
- A today widget: the day as a timeline, to tick off what's to do today and what's next without
  opening the app. Every widget shows a tick made on it straight away. **Details (default):**
  today's goals in Today's order (recommended or the user's own), with the next one at the top,
  big, with its button; then the rest, each with its time ("~" for when to get to it, red when
  late) and its own button: "Done", "+1" for a goal done several times a day, "Avoided" for one
  to quit, and a routine's next item; to-dos after. Done ones fold to the bottom, crossed off,
  and tapping one takes it back. Once everything is done it shows tomorrow's list. Small, it
  shows only what's up next and what comes after. It redraws every half hour so the times stay
  right.

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
- A locked private goal's notification says "A private goal". A phone with no screen lock can't
  be asked, so there the lock opens, with a note **(default)**.
- The app's preview in the recent-apps screen is hidden. On Android 13 and later that leaves
  screenshots working. On older phones it also blocks screenshots, so there it is a switch in the
  Privacy menu **(default)**.

## Backup (default)

- Export everything to a file, and import it back, through the system dialogs (`CreateDocument` and
  `OpenDocument`). Where that file goes is the user's choice.
- A backup reminder, and an automatic backup to a folder the user chooses. **Details
  (default):** the reminder comes when the last export is over a week old, and can be turned
  off. The automatic backup is off until the user picks a folder; then it writes a dated file
  there once a day the app is used and keeps the last 7.
- Apart from that folder, the app makes no copy of its own anywhere else.

## Budget

- A budget part of the app, for the user's own money, typed in by hand. There are no bank links
  (see "No internet").
- The user enters their pay, per hour or per any other unit, and their work start and stop
  times. A progress bar fills with money as they work through the day and the month, to see
  whenever they check.
- Work hours are a set weekly schedule. When one day is different (a late start, a day off),
  just that day is changed.
- A shop reward with a money value counts as spent in the budget when it is bought. Returning it
  takes it back off.
- A full suite of budget features. The budget can be gamified too, with a setting.
- **Details (default), built in two Releases:**
  - **0.57.0, the budget itself.** Budget is an extra on the Features page that adds a Budget
    tab. Pay is an amount per hour, day, week, month or year, with an optional take-home
    percentage for tax. Each weekday has a start and stop time and an unpaid break, or is a day
    off; a shift that stops before it starts ends the next day. A changed day has other times,
    or is a day off with or without pay. Pay by the hour pays its rate for each minute worked;
    pay by the day pays its amount for each day worked; pay by the week, month or year is spread
    over the usual work minutes of that week or month (a year is 12 months), so a usual month
    pays exactly the monthly amount, and a changed day pays for its own minutes. A paid day off
    pays what the usual day would. The today bar fills smoothly from start to stop, and the
    month bar shows what is earned so far of the month's pay. Spending and other income are
    typed in with an amount, a category, a note and a date. Categories start as Food, Home,
    Bills, Transport, Fun, Health, Shopping, Rewards and Other, and can be renamed, added,
    reordered, removed (except Rewards) and given a monthly limit, shown as a bar. The month adds
    up pay earned so far, other income, spending and what's left, and earlier months can be
    looked back at. Spending and shop rewards with a money value show how long they take to
    earn, like "≈ 2 h 15 min of work". Rewards are only counted while Budget is on. The budget
    can be put behind the privacy lock, and is in the backup file.
  - **0.58.0, more of it.** Bills and subscriptions (an amount every so many weeks or months
    from a first date, counted as spent on the day, with what's coming up), savings goals (a
    target, money put in and taken out, a bar, and how long it takes at the user's pay), a
    report for each month (earned, other income, spent and saved, each category, and a chart of
    each day), and the today bar as a card on Today. A new bill is counted from the day it is
    added (earlier ones are the user's to add), only while Budget is on, and a stopped bill keeps
    what it counted; a monthly bill on the 29th to 31st falls on the last day of shorter months.
    The Budget tab lists the bills due in the next 30 days. Then, chosen by the owner, a budget
    widget for the home screen (0.59.0, Android): today's pay filling up, and wider, the month's
    pay, what's spent today, what's left and the next bill; it redraws when money changes and
    every half hour, says when it was drawn, and stays blank behind the privacy lock. Money put into savings comes off
    what's left in the month, and taking it out puts it back. Today's pay on Today is one of
    Today's sections, just under the balance, shown while Budget is on and pay is set; a section
    a newer version adds now goes just after the one before it in the usual order.
  - **Also in 0.58.0, the budget game,** turned on with its own setting ("Budget game" on the Features
    page, with Budget on). Each day's share is the month's pay (after tax) and other money in,
    less its bills, spread evenly over its days. A day that spends no more than its share (bills
    and savings aside, rewards bought included) pays 3 points; a day that spends nothing pays 5;
    good days in a row are a money streak, shown on the Budget tab. A month within every
    category's limit pays 25 when it ends, and reaching a saving goal pays 20, once each. Days
    are paid the next time the app opens on a new day, from the day the game was turned on,
    never further back; points go in the points history as bonuses. What was paid shows in a
    note on the Budget tab.

## Order

- The user can rearrange the order of things as they see fit. **Details (default):** every card
  on Today is its own section (the mood check-in, the balance, the character, daily quests, the
  race, bets, the boss, Today's pay and the rest), moved and hidden in Settings → Appearance; the
  tabs along the bottom can be moved there too. Goals, rewards, budget categories and Today's
  sections were already in the user's order. A section a newer version adds goes where it
  usually sits, so nothing moves until the user moves it.

## Points and rules

- Every number that decides points, penalties, bonuses, the game and prices can be changed by
  the user. **Details (default):** one "Points and rules" page in Settings lists them in groups,
  each with its usual value and a Reset, and "Reset all". Changes apply from then on; what was
  already paid stays. The usual values are the ones written elsewhere in this file. The share of
  lucky ticks that pay again rather than open a chest stays half and half.

## Help

- There are helpful hints and guides for the different parts of the app, so no user is confused
  about what things do or how to use them. **Details (default):** each screen has ⓘ at the top
  for a short guide (what it's for, then how to use it, with the real button names); the first
  time each part opens, its guide shows as a tip with "Got it" and "No more tips"; Settings →
  Help and tips lists every guide, turns tips on or off and can show them all again.

## Windows

- A Windows version, decided after 0.59.0, as an installer on every Release from 0.60.0.
- **Details (default):** the same screens in a window (Compose Multiplatform desktop, the
  `:desktop` module, with the computer's side in `shared/src/jvmMain` as DesktopPhone). Data is
  kept in `%LOCALAPPDATA%\Checklist`, never the roaming folder, so it isn't copied to a server.
  Reminders show from the notification area only while the app is open; there are no widgets,
  phone calendar, AI, voice, icon choice or automatic backup folder; the privacy lock opens with a
  note, as on a phone without a screen lock. The installer is an unsigned per-user `.msi` with its
  own Java; Windows warns about it the first time. CI builds it on a Windows runner, checks the
  desktop code has no networking, and opens the app to check it keeps running before a Release
  gets it. Data moves to and from a phone by export and import.

## iPhone

- The app is planned for iPhone too. The code is restructured now, before 0.2.0, so Android and
  iPhone share it through Kotlin Multiplatform and each feature is built once.
- The iPhone app is installed with SideStore or AltStore, using the owner's free Apple ID and
  refreshed every week. There is no Mac and no paid Apple account: GitHub's Macs build the app
  and every Release carries an unsigned `.ipa` beside the APKs, for SideStore or AltStore to sign
  on the phone. Android releases carry on as before.
- **How it gets there (default):** in stages, Android unchanged for the user at each one. First
  the database and its code move to a shared module (Room, which runs on iPhone too); then the
  screens and their logic move there too (Compose Multiplatform), with what only a phone can do
  (reminders, files, the lock, buzz, widgets, the AI, the calendar) behind a small interface each
  phone fills in; then the iPhone app itself: reminders, the Face ID lock, export and import, and
  the `.ipa` on the Releases, with a step-by-step guide to installing it. Not in the first iPhone
  version: widgets, the AI and voice, the phone's calendar, the quick settings tile and app icon
  choices; widgets come next, written in Swift.
- **The first iPhone version (default):** the same screens; reminders handed to the iPhone ahead
  of time (the next three days, at most 60) and set again on every change, without Done and Skip
  buttons; no morning plan or weekly summary notifications yet; export through "Save to" (the
  file is written first) and import through Files; the photo picker for the background and GIFs;
  Face ID, Touch ID or the passcode for the privacy lock; misses looked for when the app comes
  to the front. Its data sits in a folder left out of iCloud and computer backups, and a plain
  cover hides the app switcher's preview. It needs iOS 15 or later. CI builds the unsigned
  `.ipa` on GitHub's Macs and checks for networking in the code and in the built app; a broken
  iPhone build doesn't hold back an Android release.
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

The owner tries the app once the calendar and the later features are built. Until then, each
step goes on without waiting for feedback, and still ends in a Release. The iPhone version is
under way (see iPhone).

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
6. Celebration GIFs (0.6.0).
7. Clear tick buttons, the full emoji set, "My day starts at", times for each repetition or
   "next one by", and the morning plan.
8. Snooze, keep reminding and +1 on reminders; the backup reminder and automatic backup.
9. Routines.
10. More, chosen as useful for a schedule and checklist **(default, in this order)**: a focus
    timer for goals with a length; a weekly summary; "every few hours" goals; the user's own
    miss reasons; duplicating a goal and bringing back a removed one; fixing a tick's time;
    notes on ticks; categories; charts in the review; badges; a balance and streaks widget; a
    mystery reward and a weekly points target. **Details (default):** a category is a name the
    user types on a goal or picks from the ones in use; Today can show one category at a time,
    and the review sums each one up. The week and month reviews chart each day: how much of
    what was due got done, and points earned above the line with points lost below it.
    Badges are milestones earned once and kept, found from the user's own history: the first
    tick, 100 and 1,000 ticks, a perfect day and a perfect week, main streaks of 7, 30, 100 and
    365 days, 100, 1,000 and 10,000 points earned, the first reward bought, the first project
    finished, and reaching the weekly target once and 4 weeks in a row. New ones show as a card
    on Today, not full screen. The weekly points target is a number the user sets in Settings,
    counted Monday to Sunday from points earned (ticks, bonuses and refunds); Today shows how far
    along it is, the weekly summary says whether it was reached, and reaching it pays nothing
    extra. A mystery reward is a shop reward with a list of things it could be, written by the
    user; buying it draws one at random, which waits in the inventory at the mystery reward's
    price and can be returned like any other. The balance and streaks widget shows the balance,
    the main streak and how much of today is done, and every streak when made wider.
11. Customising the look, after all of step 10. Decided: dark mode choices (light, dark, follow
    the phone, pure black); an accent colour from a palette, any colour from a colour picker,
    or matched to the wallpaper; a colour for each goal (its card, the calendar, the widget),
    also from the palette or the colour picker; a background behind Today (a
    gradient or the user's own photo, dimmed); card size and the app's own text size; Today as
    a grid of big tiles; choosing, hiding and ordering Today's sections; choosing the tabs and
    the one that opens first; renaming points (coins, XP, an emoji); app icon choices; a few
    bundled fonts; celebration settings (sound, buzz, how much confetti, a default GIF for
    every goal) and the widget's style. **(default:** ready-made colour themes too, like
    Ocean, Forest and Sunset; the colour picker has a colour square, a hue slider and a hex
    code box, and the app builds the whole theme from the picked colour so text stays
    readable**)**.
12. Goals with an amount in the user's own unit (0.20.0).
13. Next, chosen by the owner: a timer notification while Start and Stop runs, with Stop on it;
    amounts more than once (like 1 mile, 3 times a week); a planner to see and plan the coming
    days; moving one day's goal to another day; quick to-dos; and a first-run guide, search in
    the Goals list and app shortcuts. Decided: moving a goal to another day is free, but only to
    another day in the same Monday-to-Sunday week, and if it isn't done on the new day that is a
    miss as usual. A to-do left undone past its date is a miss, with a reason, the allowance and
    penalties, like any goal; to-dos pay points. **Details (default):** a to-do's date is
    optional, and one without a date is never missed; to-dos share one allowance between them;
    each time of an amount goal done more than once needs the whole amount, and amounts entered
    add up across entries. A new to-do pays 2 points, and is added from a box in its own To-dos
    section on Today; one without a date takes the day it is done as its date. Its date can be
    changed in its editor, to today or later. Done and past to-dos leave the Goals list but stay
    in the history and the review. The planner and moving apply to goals; a to-do moves by
    changing its date.
    The guide is a card on Today for someone with no goals, opening six short pages that end
    with example goals and rewards (one of each kind) or none; it is also in Settings. The Goals
    list's search matches every word typed against names and categories. The icon's shortcuts are
    New to-do, New goal, Plan and Shop.

14. Then, chosen by the owner, each step a Release: to-dos in the widget, the morning plan and
    reminders, steps on to-dos, and a list of what's done (0.25.0); suggested points and prices,
    and a difficulty setting that ramps up (0.26.0); a balance check with goal tuning hints, and
    savings interest (0.27.0); a boost on the goal missed most, a perfect-day bonus, and prices
    that rise on repeat buys (0.28.0); an Undo bar and a quick settings tile (0.29.0); "since
    last done" repeats (0.30.0); a day timeline (0.31.0); the phone's calendar in the planner
    (0.32.0); goals to quit and challenges (0.33.0); a streak bonus and a mood check-in (0.34.0).

    Decided: a "since last done" goal not done on its day is one miss as usual, then stays on
    Today until done with no more misses, and the next one counts from when it is done; editing it keeps it late, "I forgot to tick it" starts the next one from
    the day it was missed, and it can't be moved to another day. A goal to
    quit is ticked each day it was avoided, like a habit, and a day not ticked is a miss. A
    missed day restarts a challenge from day 1. The difficulty setting changes the points goals
    pay and how many skips and misses are free, not reward prices, and ramps up on a schedule the
    user sets (where it starts, where it ends up, over how many weeks), changeable any time. A
    reward's suggested price comes from its value, typed as money or picked as a size; a goal's
    suggested points come from its difficulty and its length.

    **Details (default):** a to-do's steps pay nothing, and it is done, and pays, when every step
    is ticked; the Done list holds finished projects and done to-dos, newest first. Difficulty
    has five levels: Very easy pays double and allows 2 more free skips or misses, Easy pays 1.5
    times and allows 1 more, Normal is as set, Hard pays three quarters and allows 1 fewer, Very
    hard pays half and allows none; points are rounded down but a goal paying something pays at
    least 1, and penalties stay half the goal's own points. The ramp moves a level at a time on
    Mondays, spread evenly over the weeks; picking a level by hand turns the ramp off. Suggested
    points per tick: easy 2, medium 5, hard 10, very hard 20, plus 1 for every 10 minutes of its
    length, shared out over the times of a counted goal. Suggested prices come from the user's
    usual earnings at Normal: a small treat a day's worth, medium 3 days, big 2 weeks, huge 6
    weeks; a money value uses a weekly treat budget the user types once (a normal week's
    earnings are worth that budget). The balance check sits in the shop: usual earnings, each
    reward's days of earning, rewards too cheap (under half a day) or out of reach (over 2
    months), and goals done 95% of the time or more (could pay less) or half the time or less
    (could pay more) over the last 4 weeks, once due at least 4 times, suggesting three quarters
    or one and a half times the points. Savings interest is 2% of a positive balance at the end
    of each week, rounded down, paid on the Monday, in the points history, and can be set to off,
    1%, 2% or 5%; it counts from the first week the app looked. The
    goal missed most over the last 4 weeks (2 misses or more) pays 50% more for the week,
    chosen each Monday, marked on Today. A perfect day, everything due done and none of it
    skipped, pays 10 points when it ends (off, 5, 10 or 20 in Settings). A reward
    costs 25% more for each time it was already bought that week, back to its price on Monday;
    a return refunds what was paid and takes it off the count. Each of these can be turned off.
    The Undo bar shows for 5 seconds after a tick, removing a goal or buying a reward. The quick settings tile ticks the next goal in Today's order and
    leaves out locked ones. Dragging on the timeline changes a goal's time for that day only, snapping to quarter
    hours; tapping a goal picks a time, and "Usual time" puts it back. The timeline opens from
    Today and from each day in the planner, and reminders, pacing and the order follow it. The
    calendar is off until turned on in Settings, which asks for the calendar permission; events
    are only read, from the calendars the user picks, and pacing counts them as busy time;
    all-day events are only shown. "I
    slipped" on a goal to quit records the miss straight away and takes back that day's tick;
    Today shows the days in a row avoided. A challenge starts at 30 days with a 50-point bonus,
    is offered on habits not counted per week, month or year, and is paid by the tick that
    finishes it (undoing the tick takes the bonus back). A skip neither counts toward a
    challenge nor restarts it; finishing pays its bonus, and the goal carries on as a habit. The
    streak bonus adds 10% to a tick's points from a 7-day main streak, 20% from 30 and 30% from
    100, rounded down, can be turned off, and goes with an undone tick; it counts the main streak to
    the end of yesterday, so it holds all day. The mood check-in is five
    faces and an optional note once a day, as a card on Today, shown in the review beside how
    much got done, and behind the lock with reasons and notes; "Not today" puts it off until the
    next day, and it can be turned off. Point changes stack by
    multiplying (difficulty, the boost, the streak bonus) and are rounded down once.

15. Then, chosen by the owner: keeping the app uncluttered with a Features page, and a search
    and clearer sections in Settings (0.35.0); reminders at the usual time and habit chains
    (0.36.0); a quick-add widget and editing several goals at once (0.37.0); weekly planning and a
    year in review (0.38.0); a spreadsheet export and restoring an automatic backup (0.39.0);
    tablet and landscape layouts (0.40.0); and the previews PDF brought up to date.

    Decided: the app stays uncluttered. One Features page in Settings lists every optional
    feature with a switch. The core (goals, ticking, points, the shop, skips and misses,
    reminders, streaks, the review, backups and the lock) is on; extras start off and are turned
    on there. Anything empty hides on its own, like an empty To-dos box, and an extra already in
    use stays shown.

    **Details (default):** the extras are to-dos on Today, the timeline, the mood check-in, the
    phone's calendar, suggested points and prices, the balance check, savings interest, the boost,
    the perfect-day bonus, the streak bonus, rising prices on repeat buys, goals to quit,
    challenges, reminders at the usual time, habit chains and weekly planning; the Plan tab stays
    in Appearance → Tabs. The Undo bar, search, the Done list, editing several goals, the year in
    review, the spreadsheet export and restoring a backup are part of the core, since they only
    show when there is something to act on. A reminder at the usual time is for a goal with no
    time and no reminder, at the time of day it is usually ticked (from at least 5 ticks over the
    last 4 weeks). A habit chain names the goal that comes before; ticking that one reminds of
    the next straight away if it is due and not done. Weekly planning is a card on Today from
    Monday until the week is planned or put off, opening last week's summary, this week's target and the week's goals to move. The
    quick-add widget adds a to-do from the home screen. The spreadsheet export is a CSV of the
    points history, with the note on each tick, saved where the user picks; words that start
    like a formula get a ' in front so a spreadsheet shows them as written. Restoring an
    automatic backup lists the dated files in the chosen folder and goes through the same
    confirm step as importing a file. A wide window (600 dp or more across, counting the
    whole window: a tablet, or a phone on its side) puts the tabs in a rail down the side and
    holds each screen to a readable width (720 dp) in the middle; a short one (under 480 dp)
    lays the focus timer out side by side and shrinks the big celebrations to fit.

    Decided: a colour-blind friendly look (0.41.0). **Details (default):** a
    switch in Settings → Appearance. Good things (points earned, everything done, on time) are
    blue and bad things (points lost, nothing done, late, over the limit) are vermillion, the
    pair from Okabe and Ito's palette, whatever the app's colour, in the app and the widgets.
    Wherever colour alone said something, a mark says it too: the review calendar shows ✓, ◐,
    ✕ and –. Goals are offered Okabe and Ito's colours. A test simulates the three common
    kinds of colour blindness and checks the colours stay apart and readable.

16. Then an optional AI that runs on the phone, after 0.41.0 and the previews PDF.

    Decided: it is a one-time download that works offline, and the app is complete without it.
    The app keeps no internet permission: a button opens the phone's browser to download the AI
    file, and the user picks the file in the app, like a GIF. It is an extra on the Features
    page, off to start. It adds a goal from a sentence (the goal editor fills itself in from
    plain words), steps suggested for a project or to-do, a short coach note in the weekly
    summary from the user's own stats, reasons and notes, and voice (saying "done reading" or
    "add buy milk"), which needs its own speech file.

    Decided: the AI engine is a separate app, "Checklist AI", so the checklist app stays the
    same size for anyone who doesn't use AI. Both apps are always released together: every
    Release carries both APKs, built from the same code with the same version number, and every
    new feature updates both without the owner having to ask. The checklist app says so when
    the AI app is older than itself.

    **Details (default):** every suggestion is shown for the user to check before anything is
    saved, and the AI never ticks, skips or spends on its own. The engine is Google's LiteRT-LM
    (Apache 2.0) and the AI file is Qwen3 0.6B (Apache 2.0, 614 MB), from its Hugging Face page
    at a fixed commit; a CI job downloads it from the same address and checks the size and
    checksum the apps expect. Checklist AI answers the checklist app through a door guarded by a
    signature permission, so only apps signed with the release key can ask it, and keeps nothing
    it is asked. Its APK carries the engine for 64-bit phones only. The first feature is "Describe
    it" on a new habit (0.42.0). Then (0.43.0) "Suggest steps" in a project, from its name, with
    each suggestion ticked to keep; and "A note on your week" in the review's week view, written
    from that week's numbers only, with private goals unnamed and the reasons for misses left out
    while they are locked. Then (0.44.0) voice: a 🎙 button on Today opens Checklist AI's
    listening screen, which turns one short command into words with Vosk (Apache 2.0) and its
    small English speech file (41 MB, picked and checked like the AI file); the checklist app
    works out "done <goal due today>" or "add <to-do>" and asks before doing it. Only Checklist
    AI has the microphone, and nothing is recorded or kept. The files come from the models'
    own public download pages, under licences that allow it, and the app checks each file's
    checksum before using it. They are kept in the app's own storage, left out of the backup
    file, and can be removed in Settings to free the space.

17. Then a game layer, chosen by the owner, every part an extra that starts off.

    Decided: a drawn character that levels up, with six skill areas (Fitness, Health, Mind,
    Home, Work, Creative) that each level up from their own goals, and whose look (clothes,
    gear) changes to match as they do; the user picks skin, hair and face. Each goal belongs to
    one area, guessed from its name and category and changeable. Levels for the character and
    each skill; badge tiers. Daily quests, combos, and racing last week. A boss of the week,
    weaker to some areas than others; ticks earn items that help beat bosses, and items from the
    area a boss is weak to hit it harder. A boss not beaten by Sunday just leaves: nothing is
    lost, a new one comes on Monday, and items are kept. Lucky ticks, which never take points
    away. Bet on yourself: stake points on doing a goal so many times in a week, win double or
    lose the stake. Streak shields: one is earned every 30 days of main streak, at most 2 are
    held, and one saves only the main streak, once.

    **Details (default):** everything is original to this app, its names and art included.
    Experience is every point a goal has ever paid (ticks and their bonuses), worked out from the
    points history, so past ticks count from the start; spending and penalties don't lower it.
    The character is a friendly front-facing figure, drawn by the app from simple shapes in the
    shared code: 6 skin tones, 6 hair styles, 6 hair colours and 3 kinds of eyes. Each skill
    earns a piece of gear at levels 5, 10 and 20 (Fitness: trainers, headband, gold medal;
    Health: water bottle, healthy glow, heart; Mind: glasses, book, graduation cap; Home: apron,
    spatula, potted plant; Work: name badge, briefcase, blazer; Creative: beret, paintbrush,
    scarf). Where two pieces go in the same place, the stronger skill's is worn, and the shirt
    takes the leading skill's colour.
    Daily quests: three a day, picked from the goals due that day (the same all day): a number
    of goals before noon, a named goal, every goal of one area, everything by 8 pm, or a number
    of points from goals; each pays 5 points (everything by 8 pm pays 10), once. Combos: each
    perfect day in a row adds one and pays 2 points for each, up to 20; a day left undone ends
    it, and a day with nothing due does neither. The race compares points earned from Monday
    until now with last week until the same moment.
    Items and bosses: every tick earns an item from its goal's area (an energy bolt, a potion, a
    scroll, a hammer, a clip or a paint bomb), counted from when the extra is turned on. Each
    Monday a boss arrives, picked at random but never the same twice running, weaker to two
    areas; an item hits it for 10, or 20 from an area it is weaker to. Its health is what plain
    items from 70% of the week's goals would deal. The items in hand are used together from
    Today, and beating it pays 25 points once. Lucky ticks: one tick in ten, in the app, pays its
    points again or opens a chest of 5 to 15; undoing the tick takes the extra back. Quest,
    combo, boss and lucky points are bonuses: they add to the balance, not to experience.
    Bets: stakes of 10, 25 or 50 points, taken when placed and never more than the balance; one
    bet per habit a week and three at a time; reaching the count wins twice the stake straight
    away, and undoing the tick that won it takes the win back; a week ending short loses it.
    Shields count from the day they are turned on; badges count streaks without them.
    Paths: Athlete, Healer, Scholar, Homemaker, Professional and Maker, one per area; the path's
    area gets 10% more experience, worked out over the whole history, so the path can be
    changed. Titles: Early bird (20 ticks before 8 am), Night owl (20 after 10 pm), Weekend
    warrior (everything due on 8 weekend days), Unstoppable (a 50-day main streak), All-rounder
    (every skill at level 5), Boss slayer (4 bosses), Quest hunter (30 quests) and Lucky star
    (10 lucky ticks). The user picks the title shown: the path's, an earned one, or none.
    **Details (default):** moods and moves are their own switch, "Character check-ins", on the
    character's card on Today. Its face is calm, happy, proud, tired or worried. In order, it
    cheers a new level (and jumps) until the user taps it or opens the character; asks the mood
    there instead of the separate card; on a mood of 1 or 2 is gentle and offers the quickest
    goal left; when everything is done is proud (and jumps); after 2 or more days with something
    due and nothing done welcomes the user back; after something left undone yesterday, with
    nothing done yet today, suggests the quickest goal left; from 6 pm with under half done,
    suggests one more. A suggestion can be ticked from the card. Slips count only from the day
    tracking began, and goals with an amount or to quit are never suggested. Decided: on a hard
    day each thing done counts as a win: the card shows a 🌱 for each, the character hops, and
    its words go on from there (you did it, you could stop here, or one more if you like)
    instead of the same nudge again; from 3 wins it is proud.
    Then, chosen by the owner: ideas to level up each skill area, always with a way to get more,
    including ideas that build on what the user already does there. **Details (default):** a
    page of ideas for each area, opened from each skill and a "level up next" card (the skill
    with the least experience) on the character page, and from the character's card on Today
    once the day is done or nothing is due. It has three kinds: stepping up the user's own goals
    in that area (5 minutes longer up to half an hour, 10 up to an hour, then 15; about a fifth
    more of an amount in a round step; once more a week), which can always be stepped up again;
    eight ideas that come with the app for each of three stages in each area (getting started
    below skill level 5, building up below 10, pushing further from 10), best suited first,
    three at a time, with "More ideas" going round the list again and bringing back hidden ones
    when nothing else is left; and, with the AI helper ready, three new ideas from the AI at a
    time, told what was already shown so each ask brings different ones. "Not for me" hides an
    idea; ideas like a goal the user has are left out. Adding opens the goal editor filled in,
    with the area, a difficulty and its suggested points; stepping up opens the goal's editor
    with the new value. Nothing is saved until the user saves it.
    Then, also chosen by the owner: paths and titles (at level 10 the user chooses a path, like
    Athlete, Scholar or Maker, which gives a title and a little more experience in its skill;
    more titles come from habits, like "Early bird"); and moods and moves, where the character
    does more than look the part: it checks in when the user falls behind and motivates getting
    back on track, like suggesting one small goal to start with, and it responds to the mood
    check-in, gentle on a hard day and celebrating on a good one. It jumps for joy at a new level.
    Build order: skill areas, levels and badge tiers (0.45.0); the drawn character (0.46.0);
    quests, combos and racing last week (0.47.0); items, lucky ticks and bosses (0.48.0); bets
    and streak shields (0.49.0); paths and titles (0.50.0); moods and moves (0.51.0); ideas to level up
    each area (0.52.0).

    Then, chosen by the owner after 0.59.0 (0.60.0), part of the mood check-in rather than the
    game. Decided: a second mood check-in once all of the day's goals are done, mattering most
    after a low start. It encourages carrying on, shows which goals go with feeling better, works
    out which go with feeling worse, and looks for ways to make those easier to get done.
    **Details (default):** with the Mood check-in on, once every goal due
    today is done (at least one, skipped ones left out) and the morning's mood was given, Today
    asks "How do you feel now?" with the same five faces; after a morning of 1 or 2 it says the
    day started low first. It is a second mood for the day (in the backup file from format 26),
    asked once a day; "Not now" puts it off until the next day, and without a morning mood the
    usual card asks instead. The answer says how the mood moved, and since 0.61.0 nothing about
    why (see below), the goals that often go with a better mood, and opens "Mood and your
    goals". That page, also opened from the review's mood part, looks at each active habit
    over the 90 days before today, using the mood once everything was done where given and the
    morning's otherwise: the mood on days it was done against days it was due and not done (a
    goal counted per week, month or year: days ticked against days it was still to do), and the
    change from the morning on days it was done with both moods. It needs at least 3 days each
    way, and half a point or more counts; both are in "Points and rules" under "Mood and your
    goals". A goal done on better days lifts; one done on worse days, or whose days fall from the
    morning by half a point or more, weighs; biggest first, each with its numbers in plain words
    (the page says "goes with a better mood" and "goes with a lower mood", never "lifts" or
    "helped"). Each one that weighs gets up to three ways in: doing it right after the goal that
    lifts most (with Habit chains on, a button sets the chain), making it smaller (its editor),
    paying half as much again (one tap, rounded, at least 1 more) and getting it out of the way
    early. On a
    morning of 1 or 2, Today suggests starting with a goal due that usually lifts the user. The
    words are kind, never blaming, and say what goes together, not what causes what. The page is
    behind the lock with reasons and notes, or with the review; the lifts on Today and the
    morning suggestion hide with reasons and notes; private goals are left out while locked.

    Then, chosen by the owner (0.61.0). Decided: mood check-ins take notes and the things behind
    a mood, kept as a journal; the app doesn't claim that getting things done is what changed the
    mood. **Details (default):** the answer once everything is done only says how the mood moved
    ("Up from 🙁 to 🙂.", "Same as this morning.", "Down from 🙂 to 🙁 — that's all right.") and
    asks "What played a part?"; after the morning's face Today asks "What's behind it?" until
    "Done", or until the check-in once everything is done takes over. Both offer the same chips
    and a short note with "Save": the chips are for the whole day, so either check-in can add or
    take one off, and each check-in has its own note (in the backup file from format 27). The
    chips start as Slept well, Slept badly, Exercise, Outdoors, Friends and family, Alone time,
    Work, Health, Weather, Food, Money, Screen time and News; "+ Add your own" adds one to the
    list and picks it. The "Mood journal" page (from the answer card, the review's mood part and
    "Mood and your goals") lists every day with a mood, newest first: both faces, the chips and
    both notes; tapping a day changes its faces, chips and notes. "Edit the list" there takes
    any chip off the list (days that had it keep it), adds the user's own, and puts back the
    app's own. "Mood and your goals" also lists the things that go with a better or a lower
    mood, compared like the goals (days noted against days with a mood not noted, the same 90
    days, day mood and rules), like "Slept well: your mood is about 1.1 higher on days you note
    it.", under "These go together; it doesn't say what causes what." The journal is locked like
    "Mood and your goals"; on Today the chips and notes ask to unlock first while reasons and
    notes are locked; notes never go in a widget or a notification.

18. Then, chosen by the owner, a budget (see "Budget"): the budget itself (0.57.0), then bills,
    savings goals, reports and the budget game (0.58.0).
19. Then, chosen by the owner: a budget widget, every number changeable ("Points and rules"),
    and help and tips everywhere (0.59.0); then a Windows version.
20. Also chosen by the owner after 0.59.0: a mood check-in once everything is done, with the
    goals that lift the mood and ways into those that weigh on it, and the Windows version
    (0.60.0; see the end of 17, and "Windows").
21. Then, chosen by the owner: notes and the things behind each mood, kept as a mood journal,
    without claiming that getting things done changed the mood (0.61.0; see the end of 17).
22. Then a review of the whole app: the confirmed bugs fixed first (0.62.0), then these, chosen
    by the owner from the review's suggestions, built in Releases grouped by part of the app:
    - Bets are removed from the app; open bets give their stake back. Streak shields stay.
    - Guides don't pop up by themselves. A new user gets a short, simple setup, and screens
      explain themselves with small hints; the full guides stay behind ⓘ and in Help, to read
      later. Guides on the misses, penalty, gap check-in and focus timer screens; guides for
      reminders and every game extra; Help in groups with search; guides point to each other
      where parts connect.
    - Skips: a skip is never counted as done and never benefits (no perfect day, no bonus, not
      "done" in the review), but it doesn't directly punish either: a skipped goal drops out of
      what the main streak's target counts. A skip can be taken back, which puts everything
      back as if it never happened. Free skips left are not shown on Today, so as not to
      encourage skipping. "Perfect day" means the same everywhere: everything due done, none
      skipped.
    - Money: with Budget on, any reward can have a money value, and its points price follows
      the money only when the user links them. What is left at the end of a month can be moved
      into a saving goal, or, with none chosen, into a plain savings account, so the money can be
      seen growing while it isn't spent; nothing carries over into the next month's budget. A
      reward can be paid from savings: the money comes out of a saving goal instead of counting
      as that month's spending, so it isn't counted twice and doesn't spoil the budget game or a
      category's limit. The Buy box says what a reward costs in money and how much is left of
      the Rewards category's monthly limit, warning before going over. Pay changes apply from a
      date the user picks, so past months stay as they were. Bill reminders and regular money
      in; quick "Spent" from the app icon, the budget widget and Today.
    - Positive things are worded positively everywhere: finished habits and projects are
      "Completed" or "Achieved", never "Ended".
    - Also: the gap check-in offers "I forgot to tick it" for each goal and past misses can be
      corrected; to-dos take categories; "Plan your week" shows flexible goals, deadlines and
      the phone's calendar events where allowed; dated to-dos open from Today and the planner;
      "I forgot to tick it" brings back that day's perfect-day bonus and combo; every screen
      shows the numbers set in "Points and rules"; Today, the widgets, the tile and the morning
      plan agree on what's next, and the calendar widget is as informative as a calendar app's
      own; the review lists projects finished, to-dos done and amounts done; Today's mood opens
      in full, with the journal one tap away; on a low morning, one "start with" suggestion,
      perhaps with a small reward to look forward to; badges for the budget and mood; editors
      ask before throwing away changes; a better day timeline; the same buttons on every goal
      card; the tour asks before adding examples; Undo after "Used it" and a list of used
      rewards; "Same reason for all" for several misses; a late first check-in asks once; mood
      in the review as a summary, with the day-by-day notes a tap away; Undo after raising
      points from the mood page; Settings search finds features and look options; sections and
      settings for extras that are off stay hidden; Privacy says the lock covers moods; a page
      for each goal; a reward to save for, starred, on Today; an optional evening check; project
      deadline reminders; best streaks; more spreadsheets; a fuller mood journal; a safety copy
      before importing; an optional passphrase on backup files; on Windows, an automatic backup
      folder, reminders that keep coming with the window closed, and "computer" in place of
      "phone"; "Erase everything"; focus time kept, with optional breaks; "at most" goals;
      reordering steps and checklist items; one "days of earning" figure everywhere; quieter
      notification types for the morning plan and weekly summary; a stricter build check of the
      no-cloud-backup rules; small accessibility tidying; and, in "Mood and your goals", a
      switch that highlights what shows up on days something was noted.
    - Not chosen: a note when a money-priced reward's price moves, celebrations for game wins,
      and carrying money over into the next month.

## Talking to the owner

- Short and plain. The owner is not an Android developer. Say what changed, what to try, and what
  is not verified.
- "Done" means a Release the owner can install, not a green check.
