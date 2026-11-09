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

The owner tries the app once the calendar and the later features are built. Until then, each
step goes on without waiting for feedback, and still ends in a Release. Questions about the
iPhone version are on hold.

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
    Today until done with no more misses, and the next one counts from when it is done. A goal to
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
    chosen each Monday. A perfect day, everything due done, pays 10 points when it ends. A reward
    costs 25% more for each time it was already bought that week, back to its price on Monday;
    a return refunds what was paid. The Undo bar shows for 5 seconds after a tick, removing a
    goal or buying a reward. The quick settings tile ticks the next goal in Today's order and
    leaves out locked ones. Dragging on the timeline changes a goal's time for that day only. The
    calendar is off until turned on in Settings, which asks for the calendar permission; events
    are only read, from the calendars the user picks, and pacing counts them as busy time. "I
    slipped" on a goal to quit records the miss straight away. A skip neither counts toward a
    challenge nor restarts it; finishing pays its bonus, and the goal carries on as a habit. The
    streak bonus adds 10% to a tick's points from a 7-day main streak, 20% from 30 and 30% from
    100, rounded down, can be turned off, and goes with an undone tick. The mood check-in is five
    faces and an optional note once a day, as a card on Today, shown in the review beside how
    much got done, and behind the lock with reasons and notes. Point changes stack by
    multiplying (difficulty, the boost, the streak bonus) and are rounded down once.

## Talking to the owner

- Short and plain. The owner is not an Android developer. Say what changed, what to try, and what
  is not verified.
- "Done" means a Release the owner can install, not a green check.
