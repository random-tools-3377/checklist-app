package io.github.randomtools3377.checklist.ui

import io.github.randomtools3377.checklist.core.Feature

/**
 * A short guide to one part of the app: what it's for, then how to use it, step by step.
 * "{points}" in the text stands for what the user calls points (they can rename them), and the
 * other words in braces for the numbers set in Points and rules (see [guideWords]).
 */
enum class HelpTopic(val title: String, val intro: String, val steps: List<String>) {
    TODAY(
        "Today",
        "Your list for today: what's due, how fast to go, and your balance at the top. Tick things off here to earn {points}.",
        listOf(
            "Tap \"Done\" to tick a goal and earn its {points}. Tapped by mistake? Tap it again to take it back.",
            "A goal done several times a day shows \"+1\" and how far along it is, like \"0 of 2\".",
            "Tap \"Skip\" to skip a goal ahead of time, or \"Move\" to do it on another day this week.",
            "Changed your mind about a skip? Tap \"Take back the skip\" on its card, any time until the day ends.",
            "Pick \"Recommended order\" for a good order to do things in, or \"My order\" for your own.",
            "Set \"Finish each day's goals by\" in Settings, and Today shows how fast to go.",
            "Tap your balance to see your {points} history, and the gear at the top for Settings.",
            "Choose, hide and reorder Today's sections in Settings → Appearance.",
        ),
    ),
    PLAN(
        "Plan",
        "The planner shows today and the next two weeks, a day at a time, so you can see what's coming and spread things out.",
        listOf(
            "Tap a day at the top to see what's due then, with times. The small number says how many.",
            "Tap \"Move\" on a goal to do it on another day of the same week, for free.",
            "If a moved goal isn't done on its new day, that's a miss as usual.",
            "\"This week\" lists goals due so many times a week; \"Deadlines\" shows projects due that day.",
            "A to-do moves by changing its date in its editor.",
            "With the Timeline extra on in Settings → Features, \"Timeline\" shows the day by the hour.",
            "Don't need this tab? Hide it in Settings → Appearance, under Tabs.",
        ),
    ),
    GOALS(
        "Goals",
        "Every goal you have, in one list: habits that repeat, projects made of steps, and to-dos.",
        listOf(
            "Tap \"New goal\" and pick Habit, Project or To-do. To-dos need the \"To-dos on Today\" extra.",
            "Tap a goal to change it. Changes apply from now on; {points} already earned stay.",
            "Type in \"Search goals\" to find goals by name or category.",
            "Tap \"Reorder\" and use the arrows. \"My order\" on Today follows this order.",
            "Tap \"Select\" to choose several goals, then change their category or difficulty, or remove them together.",
            "At the bottom, \"Done list\" holds finished projects and done to-dos, and \"removed goals\" lets you bring one back.",
        ),
    ),
    SHOP(
        "Shop",
        "Your shop of real-life rewards, priced by you. Spend the {points} you earn on them.",
        listOf(
            "Tap \"New reward\" to add something you'd enjoy, like a film night, at a price you choose.",
            "Tap \"Buy\" when your balance covers the price. There's no limit on how often.",
            "Bought rewards wait under \"Bought\". Tap \"Use it\" when you use it, or \"Return\" for a full refund.",
            "A reward you can't afford yet shows a \"Saving for it\" bar.",
            "Each price says how long it takes you to earn, like \"≈ 4 days of your usual\".",
            "If your balance goes below zero, the shop opens again once you're back above it.",
            "Tap a reward to change it, or \"Reorder\" to change the order.",
        ),
    ),
    REVIEW(
        "Review",
        "Look back at how it went: a day, a week, a month or a year at a time.",
        listOf(
            "Pick \"Day\", \"Week\", \"Month\" or \"Year\" at the top, and use the arrows to go earlier or later.",
            "See the {points} you earned, spent and lost in that stretch, and any savings interest.",
            "\"Each goal\" shows how often it was done, skipped and missed, and its longest run in a row. Done means really ticked; a skip only shows under Skipped.",
            "In the Day view, \"Missed\" lists what was missed. Tap one, or a goal's row with a miss, to change its reason or note, or within 14 days \"I did it, I forgot to tick it\".",
            "In a month, the calendar marks each day: everything done, some, none, or a gap. Days still to come aren't counted yet.",
            "Below are the reasons that came up most, any gaps, and stats like your most consistent goal.",
            "Stats shows your current main streak with your best ever beside it, and the best run of each other streak. In an earlier week, month or year it shows the longest each ran in that stretch.",
            "With the mood check-in on, your moods show beside what got done.",
            "Tap \"Badges\" to see every badge, earned or still to earn.",
        ),
    ),
    BUDGET(
        "Budget",
        "Your own money, typed in by hand: pay filling up as you work, spending by category, bills and savings. Budget is an extra, turned on in Settings → Features.",
        listOf(
            "Tap \"Pay and hours\" to set what you're paid and your usual work week.",
            "Watch today's pay and the month's pay fill up as you work.",
            "Tap \"Spent\" to add spending, or \"Add money in\" for other income.",
            "Tap \"Edit\" by Categories to rename them or give each a monthly limit.",
            "Add bills once under \"Bills coming up\", and saving goals under \"Saving for\", with \"Put in\" and \"Take out\".",
            "Use the arrows to look back at earlier months, or open that month's report.",
            "With the \"Budget game\" extra on, a day within its share of the month's money earns {points}.",
        ),
    ),
    SETTINGS(
        "Settings",
        "Everything you can set up in the app, in one place. Open it with the gear at the top of Today.",
        listOf(
            "Type in \"Search settings\" to find a setting quickly.",
            "\"Appearance\" changes colours, Today's layout and the tabs; \"Features\" turns extras on or off.",
            "\"Points and rules\" holds every number for {points}, penalties and bonuses.",
            "\"Help and tips\" has every guide like this one, and \"How the app works\" is a short tour.",
            "Under \"Your day\", set when your day ends, when it starts, and when to finish each day's goals.",
            "Further down: streaks, the weekly target, penalty tasks, your own reasons, gaps and notifications.",
            "Back up your data under \"Backup\", and choose what's locked under \"Privacy\".",
        ),
    ),
    FEATURES(
        "Features",
        "Extras you can add when you want them. They start off, to keep the app simple.",
        listOf(
            "Turn on a switch to add that extra; turn it off to hide it again.",
            "They're grouped: Today, Goals and rewards, Points, Game, Money and AI.",
            "Anything already in use stays where it is, even when its switch is off.",
            "Some extras offer a choice once they're on, like how much savings interest pays.",
            "The Plan tab isn't here: choose which tabs show in Settings → Appearance.",
        ),
    ),
    RULES(
        "Points and rules",
        "Every number the app uses for {points}, penalties, bonuses, the game and prices, so you can make it your own.",
        listOf(
            "The numbers are grouped, from penalties and difficulty to the game, the budget and prices.",
            "Each one says what it's usually set to. Type a new number to change it.",
            "For example, a skip or miss over the limit usually costs 50% of what the goal pays.",
            "Tap \"Reset\" beside a changed number to put it back to its usual value.",
            "Tap \"Reset all\" at the top to put every number back at once.",
            "Changes apply from now on; anything already paid or taken stays as it was.",
            "Some numbers only matter when their extra is on in Settings → Features, like quests or bosses.",
        ),
    ),
    APPEARANCE(
        "Appearance",
        "Make the app look the way you like: colours, Today's layout, the tabs, fonts and more.",
        listOf(
            "Pick a theme: \"Light\", \"Dark\", \"Phone\" to follow your phone, or \"Black\" for pure black.",
            "Choose a colour, \"Wallpaper\" to match your phone, or \"Any colour…\" for the colour picker.",
            "Turn on \"Colour-blind friendly\" to show good things in blue and bad things in orange, with marks too.",
            "For Today, pick a list or a grid of big tiles, the card and text size, and a background.",
            "Tick Today's sections to show them, and use the arrows to put them in any order: the mood check-in, your character, quests and the rest each move on their own.",
            "Choose which tabs show, use the arrows to put them in your order, and pick the one the app opens on.",
            "Rename {points}, and pick a font, an app icon, the widgets' style and your celebrations.",
        ),
    ),
    HABIT_EDITOR(
        "A habit",
        "A habit is something you do again and again, like drinking water or reading. Changes apply from now on, never to the past.",
        listOf(
            "Tap \"Choose icon\", fill in \"Name\" and the {points} each tick pays, then tap \"Save\".",
            "Under \"How often\", tap \"Change\" to pick every day, chosen days, so many times a week, and more.",
            "Set \"Times each day it's due\" above 1 for a counted goal, like 8 glasses, with a bonus for finishing.",
            "Add a checklist to make it a routine, or an amount, like 20 pages, to fill in as you go.",
            "Give it a length, a set time or a \"Finish by\" time for pacing, and a reminder if you like.",
            "Choose how many skips and misses are free each week or month, and turn on \"Private\" to lock it.",
            "With extras on in Settings → Features, it can be a goal to quit, a challenge, or a habit chain.",
        ),
    ),
    PROJECT_EDITOR(
        "A project",
        "A project is done once, step by step, like painting a room. Each step pays, and finishing pays a bonus.",
        listOf(
            "Tap \"Choose icon\" and fill in \"Name\".",
            "Write each step with its {points}, and tap \"Add a step\" for more.",
            "Set \"Bonus for finishing the project\", paid when the last step is ticked.",
            "Add a \"Deadline\" and Today shows how many steps a day it takes. Missing it counts as a miss.",
            "Tick the steps one by one in the Projects section of Today.",
            "With the AI helper on, \"Suggest steps\" offers steps from the project's name.",
            "Tap \"Save\". \"Make a copy of this project\" starts a new one like it.",
        ),
    ),
    TODO_EDITOR(
        "A to-do",
        "A to-do is a quick one-off task, with or without a date. To-dos are an extra: turn on \"To-dos on Today\" in Settings → Features.",
        listOf(
            "Fill in \"What to do\" and the {points} it pays. A new to-do pays 2 to start.",
            "Add steps if you like. They pay nothing; ticking the last one does the to-do.",
            "Pick \"Due on\" to give it a date, and \"Remind me at\" for a reminder that day.",
            "Without a date it waits on Today until it's done, and is never missed.",
            "With a date, not doing it by the end of that day is a miss, like any goal.",
            "To-dos share one allowance, usually 1 free skip or miss a week between them.",
            "For a quick one, type in the \"Add a to-do\" box on Today.",
        ),
    ),
    REWARD_EDITOR(
        "A reward",
        "A reward is a real-life treat you buy with {points}, like a coffee out or a new book. You set the price.",
        listOf(
            "Tap \"Choose icon\", fill in \"Name\", and set \"Price in {points}\".",
            "Pick a price that feels earned. The shop shows how many days of your usual it takes.",
            "Turn on \"Mystery reward\" and write two or more things it could be; buying it draws one at random.",
            "With \"Suggested points and prices\" on in Settings → Features, \"How big is it?\" suggests a price.",
            "That extra also lets you type what it costs in money, and the price follows it as your goals change.",
            "With Budget on, a reward that costs money goes into the budget's Rewards category when you buy it.",
            "Tap \"Save\". \"Remove this reward\" takes it out of the shop; anything bought stays under \"Bought\".",
        ),
    ),
    BALANCE_CHECK(
        "Balance check",
        "A check that your prices and {points} feel fair. It's an extra: turn on \"Balance check\" in Settings → Features.",
        listOf(
            "Open it in the Shop with \"Balance check: are prices and {points} right?\".",
            "The top line shows about how much you usually earn in a day and in a week.",
            "Each reward shows the days it takes to earn, and whether it may be too cheap or out of reach.",
            "Goals done nearly every time could pay less; goals often missed could pay more.",
            "Goals are judged over the last 4 weeks, once they were due at least 4 times.",
            "Tap a suggestion, like \"Make it 40\" or \"Pay 6 instead of 4\", to use it.",
        ),
    ),
    HISTORY(
        "Points history",
        "Every change to your balance: what it was for, when, and how much.",
        listOf(
            "Open it by tapping your balance at the top of Today.",
            "Changes are grouped by day, newest first, with each day's total.",
            "Ticks, bonuses, rewards bought or returned, and penalties all show here.",
            "Tap a penalty to open its skip or miss, to change the reason or put it right.",
            "Tap a tick to fix when it was done or add a note, like \"ran 5 km\". Its {points} stay.",
            "Gaps are marked here, with what you wrote about them.",
            "Lock it in Settings → Privacy, or save it as a spreadsheet in Settings → Backup.",
        ),
    ),
    CHARACTER(
        "Your character",
        "A drawn character that levels up with the {points} your goals pay. It's an extra: turn on \"Character and skills\" in Settings → Features.",
        listOf(
            "All the {points} your goals pay count as experience. Spending and penalties never take it away.",
            "Its card on Today shows your level and how far to the next. Tap it to open your character's page.",
            "Six skills, Fitness, Health, Mind, Home, Work and Creative, level up from their own goals.",
            "Each goal's skill is guessed from its name; change it under \"Skill\" in the goal's editor.",
            "Skills earn bronze at level 5, silver at 10 and gold at 20, and gear for your character to wear.",
            "Under \"Style\", pick its skin, hair, hair colour and eyes. Tap \"💡 Ideas\" by a skill for ways to level it up.",
            "With \"Character check-ins\" on too, it talks to you on Today: it suggests one small goal after a slip, answers how you feel, and cheers a new level.",
        ),
    ),
    PATHS(
        "Paths and titles",
        "A path and titles for your character, shown under its level. They're part of \"Character and skills\" in Settings → Features.",
        listOf(
            "At level 10, choose a path on your character's page: Athlete, Healer, Scholar, Homemaker, Professional or Maker.",
            "Its skill gets {pathBonus}% more experience, and its title shows under your level. You can change it later.",
            "Titles are earned from how you play, like \"Early bird\" for ticking 20 goals before 8 am.",
            "Under \"Titles\", each one still to earn says how. Once earned, a title is kept.",
            "Tap a title to show it, or \"None\" to show none.",
        ),
    ),
    IDEAS(
        "Ideas to level up",
        "Ideas for growing each skill, from stepping up what you already do to trying something new.",
        listOf(
            "Open it from a skill on your character's page, or from your character's card on Today.",
            "Pick a skill at the top to see its ideas.",
            "Under \"Build on what you do\", \"Step up\" makes one of your goals a bit longer, bigger or more often.",
            "Under \"Try something new\", tap \"Add\" to make an idea a goal, or \"Not for me\" to hide it.",
            "Tap \"More ideas\" for others. When they run out, tap \"Bring back hidden ones\".",
            "With the AI helper on, \"Make new ideas\" asks the AI for fresh ones.",
            "Nothing is saved until you check it in the goal editor and tap \"Save\".",
        ),
    ),
    QUESTS(
        "Daily quests",
        "Three small quests each day, made from your own goals, each paying a few {points}. It's an extra: turn on \"Daily quests\" in Settings → Features.",
        listOf(
            "They show on Today under \"🗺️ Today's quests\", with how far along each one is.",
            "A quest is like \"Tick 2 goals before noon\", \"Do 📖 Read\" or \"Finish everything due by 8 pm\".",
            "Each one pays {quest} {points} once it's done; finishing everything by 8 pm pays {questBig}.",
            "They're picked from what's due that day, and stay the same all day.",
            "A quest for a goal that's moved or removed today drops off the list.",
            "Change what they pay in Settings → Points and rules.",
        ),
    ),
    RACE(
        "Beat last week",
        "A race against yourself: this week's {points} against last week's, by the same time. It's an extra: turn on \"Beat last week\" in Settings → Features.",
        listOf(
            "A card on Today shows how far ahead or behind you are, like \"12 {points} ahead of last week\".",
            "Below it: this week so far, and last week by the same day and time.",
            "Weeks start on Monday.",
            "What your goals pay counts, with bonuses and refunds. Spending in the shop doesn't.",
            "It's just for fun: it pays nothing and takes nothing.",
        ),
    ),
    BOSSES(
        "Items and bosses",
        "A boss to beat each week with items from your ticks. It's an extra: turn on \"Items and bosses\" in Settings → Features.",
        listOf(
            "Every tick earns an item from its goal's skill, like ⚡ for Fitness or 📜 for Mind.",
            "Each Monday a new boss arrives on Today, weaker to two skills: their items hit {bossWeak}% as hard.",
            "Tap \"Use … items\" on its card to use every item you hold on it.",
            "Beat it by Sunday night for +{bossReward} {points}.",
            "A boss still standing just leaves on Sunday night. Nothing is lost, and items you didn't use are kept.",
            "Its health is set so an ordinary good week of your goals beats it.",
            "With \"Character and skills\" on, change a goal's skill under \"Skill\" in its editor.",
        ),
    ),
    LUCKY(
        "Lucky ticks",
        "Now and then a tick pays extra. It's an extra: turn on \"Lucky ticks\" in Settings → Features.",
        listOf(
            "About one tick in {luckyOneIn} is lucky.",
            "A lucky tick pays its {points} twice, or opens a chest of {chestMin} to {chestMax} {points}.",
            "The bar at the bottom of Today says so, like \"🍀 lucky chest: +8\".",
            "Luck never takes anything away. Taking a tick back takes its lucky extra with it.",
            "Lucky ticks show in your {points} history as \"Lucky tick\".",
        ),
    ),
    COMBOS(
        "Combos",
        "Perfect days in a row build a combo that pays a little more each day. It's an extra: turn on \"Combos\" in Settings → Features.",
        listOf(
            "A perfect day is one with everything due done and nothing skipped.",
            "The first perfect day pays {comboDay} {points}, the next {comboDay2}, and so on, up to {comboMost} a day.",
            "It's paid once the day ends, and shows in your history as \"Combo of perfect days\".",
            "A day with something skipped or left undone ends the combo, and the next perfect day starts again from one.",
            "Days with nothing due neither add to it nor end it.",
            "If you later say you forgot to tick something and the day is perfect after all, it's paid then, and the combo carries on as if it always had been.",
        ),
    ),
    SHIELDS(
        "Streak shields",
        "Shields that save your main streak on a bad day. It's an extra: turn on \"Streak shields\" in Settings → Features.",
        listOf(
            "Every {shieldEvery} days of main streak earns a shield. You can hold {shieldsMost} at most.",
            "A day that would break your main streak uses up a shield instead, and the streak goes on.",
            "Under your streaks on Today, \"🛡️\" shows the shields you hold, when the next comes, and the last day one saved.",
            "Shields count from the day you turn them on.",
            "Change how often they come, and how many you can hold, in Settings → Points and rules.",
        ),
    ),
    BADGES(
        "Badges",
        "Milestones you earn once and keep, found from your own history, like your first tick or a long streak.",
        listOf(
            "Earned badges show the day you got them; the rest say how to earn them.",
            "Badges in a series go bronze, silver, then gold.",
            "A new badge shows as a card on Today.",
            "Open this page from the \"Badges\" card in the Review.",
            "With \"Character and skills\" on, there are badges for your character's levels too.",
        ),
    ),
    MOOD_GOALS(
        "Mood and your goals",
        "Which of your habits, and which of the things you note at the mood check-ins, go with a better mood or a lower one, over the last 90 days. It needs the \"Mood check-in\" extra in Settings → Features.",
        listOf(
            "Answer the mood check-in on Today in the morning. Once everything due today is done, Today asks again: \"How do you feel now?\"",
            "After a couple of weeks of moods, \"Goals that go with a better mood\" lists the goals on whose days you tend to feel better in the morning, and by how much.",
            "\"Goals that go with a lower mood\" lists the others, each with a few ways that may make it easier.",
            "Tap \"Make it come after\" to put it right after a goal that goes with a better mood (needs \"Habit chains\" on), \"Edit it\" to make it smaller, or \"Pay …\" to make it pay more.",
            "\"Things that go with a better mood\" and \"…a lower mood\" do the same for what you pick under \"What played a part?\", like \"Slept well\".",
            "It shows what goes together, not what causes what.",
            "Open it from the card once everything is done, or from \"Mood\" in the Review. Tap \"Mood journal\" for every day's notes.",
        ),
    ),
    MOOD_JOURNAL(
        "Mood journal",
        "Every day you checked in on your mood: how you felt in the morning and once everything was done, what played a part, and your notes.",
        listOf(
            "After picking a face on Today, tap what's behind it, like \"Slept well\" or \"Work\", and write a note if you like. It's kept when you tap \"Save\" or \"Done\".",
            "Once everything is done, the second check-in asks \"What played a part?\" the same way, with a note of its own.",
            "The things you pick are for the whole day: either check-in can add or take them off.",
            "Tap \"+ Add your own\" to add something of your own to the list.",
            "Here, tap a day to change what played a part, its notes or its faces, then \"Save\".",
            "Tap \"Edit the list\" to take things off the list or add your own. Days you already picked them on keep them.",
            "Notes never show in widgets or reminders.",
        ),
    ),
    TIMELINE(
        "Timeline",
        "Your day by the hour, to see when things happen and move them around. It's an extra: turn on \"Timeline\" in Settings → Features.",
        listOf(
            "Open it with \"Timeline\" on Today, or on any day in the planner.",
            "Goals with a time sit at that time; the rest wait at the top under \"Any time\".",
            "Hold a goal and drag it to another time. Times snap to quarter hours.",
            "Or tap a goal to pick a time, or \"Usual time\" to put it back.",
            "Changes are for this day only; reminders, pacing and the order follow them.",
            "A goal done several times a day sits at each of its times and keeps them.",
            "With \"Phone calendar\" on, your calendar's events show here too.",
        ),
    ),
    WEEK_PLAN(
        "Planning the week",
        "A few minutes each week to look back and plan ahead. It's an extra: turn on \"Weekly planning\" in Settings → Features.",
        listOf(
            "From Monday, a \"Plan your week\" card shows on Today. Tap \"Plan\", or \"Not this week\".",
            "\"Last week\" sums up what got done, and which goal was missed most.",
            "Under \"This week's target\", type the {points} to aim for and tap \"Set\".",
            "\"The days ahead\" shows what's due each day until Sunday.",
            "Tap \"Move things in the planner\" to spread things out.",
            "Tap \"Done planning\" when you're happy, and the card goes away until next week.",
        ),
    ),
    REMINDERS(
        "Reminders",
        "Reminders come at the times you set, the way a calendar reminds you. They're set on this device and need no internet.",
        listOf(
            "In a habit's editor, under Reminders, give it a set time, or a time under \"Remind me at\".",
            "With a set time, \"Also alert before\" adds an earlier one, like 10 minutes before.",
            "Turn on \"Keep reminding until it's done\" to be reminded every 30 minutes until it's done or its finish time passes.",
            "With \"Reminders at your usual time\" on in Settings → Features, goals with no reminder remind you around when you usually tick them.",
            "For one check in the evening, set a time under \"Evening check\" in Settings → Notifications. One notification then lists what's still open today, so you can do it before it counts as a miss. Nothing comes on a day when everything is done.",
            "While private goals are locked, their reminders say \"A private goal\".",
        ),
    ),
    DONE_LIST(
        "Done list",
        "Achieved projects and done to-dos, newest first, so you can see what you've got through.",
        listOf(
            "Open it with \"Done list\" at the bottom of the Goals tab.",
            "Each one says whether it was a project or a to-do, and the day it was done.",
            "Tap one to open it.",
            "Done things leave the Goals list, but stay in your history and the review.",
        ),
    ),
    REMOVED_GOALS(
        "Removed goals",
        "Goals you removed, kept here in case you want them back.",
        listOf(
            "Open it from the bottom of the Goals tab, where it says how many were removed.",
            "Tap \"Bring back\" to put a goal back in your lists.",
            "Removing a goal never takes back the {points} it already earned.",
            "To remove a goal, open it and tap \"Remove this habit\", project or to-do.",
            "Right after removing one, \"Undo\" at the bottom of the screen brings it back too.",
        ),
    ),
    PAY_AND_HOURS(
        "Pay and hours",
        "What you're paid and your usual work week, so the Budget tab fills up with money as you work.",
        listOf(
            "Type your pay, then pick \"an hour\", \"a day\", \"a week\", \"a month\" or \"a year\".",
            "Under \"Kept after tax, in %\", put 100 if that pay is already what you take home.",
            "Under \"Your usual week\", turn on each work day and set its start, stop and unpaid break.",
            "Tap \"Same hours as Monday on every work day\" to fill in the week quickly.",
            "A night shift that stops before it starts ends the next morning.",
            "To change just one day, use \"Change today\" or \"Change a day…\" on the Budget tab.",
            "Tap \"Save\" when you're done.",
        ),
    ),
    WORK_DAY(
        "Changing a day",
        "For one day that isn't like your usual week, like a late start or a day off. Only that day changes.",
        listOf(
            "Pick the \"Day\". Under it, you'll see its usual hours.",
            "Choose \"As usual\", \"Other hours\", \"Day off, paid\" or \"Day off, unpaid\".",
            "With \"Other hours\", set that day's start, stop and break.",
            "A paid day off pays what the usual day would.",
            "Tap \"Save\", and the pay bars on the Budget tab follow it.",
        ),
    ),
    MONEY_ENTRY(
        "Spending and money in",
        "Note down what you spend, and any money that comes in besides your pay, so the month adds up.",
        listOf(
            "Tap \"Spent\" on the Budget tab for spending, or \"Add money in\" for other income.",
            "Type \"How much\", and for spending pick what it was for.",
            "Add a note if you like, and change the \"Day\" if it wasn't today.",
            "Tap \"Save\". It shows in the month's list and in its category.",
            "Tap any entry later to change it, or \"Remove this\" to take it off.",
            "Shop rewards with a money value are added by themselves when bought; returning one takes it off.",
        ),
    ),
    CATEGORIES(
        "Categories",
        "The groups your spending goes in, like Food or Bills, each with an optional monthly limit.",
        listOf(
            "Open them with \"Edit\" next to Categories on the Budget tab.",
            "Tap a category to rename it, change its icon or set a \"Monthly limit (optional)\".",
            "A category with a limit shows a bar on the Budget tab, with what's left or how much over.",
            "Tap \"Add\" for a new category, and use the arrows to change the order.",
            "Rewards is where rewards bought in the shop go, so it can't be removed.",
        ),
    ),
    BILLS(
        "Bills",
        "Rent, phone, subscriptions and other regular bills. Add each one once and it's counted as spent on its day, by itself.",
        listOf(
            "Open it from \"Bills coming up\" on the Budget tab, with \"Add bills\" or \"All bills\".",
            "Tap \"Add\" for a new bill, or tap a bill to change it.",
            "Each bill shows how often it comes and when it's next due.",
            "The top line says about how much they all come to in a month.",
            "The Budget tab lists the bills due in the next 30 days.",
        ),
    ),
    BILL_EDITOR(
        "A bill",
        "One regular bill or subscription: what it's for, how much, and how often.",
        listOf(
            "Fill in what it's for, like Rent, and \"How much\".",
            "Under \"How often\", set every so many weeks or months.",
            "Pick the first (or next) date, and \"Ends\" if it stops.",
            "Pick its category. New bills go under Bills to start.",
            "It's counted from today on. Add anything already paid this month as spending.",
            "Tap \"Stop this bill\" when it ends. What it already counted stays in the budget.",
        ),
    ),
    BUDGET_REPORT(
        "Monthly report",
        "A month at a glance: what came in, what went out, and where it went.",
        listOf(
            "Open it with \"Report for\" and the month's name on the Budget tab.",
            "See pay, other money in, spending, money put into savings, and what's left.",
            "It says what share of your pay went on spending.",
            "\"Each day's spending\" charts every day of the month, bills aside, and names the biggest.",
            "\"Where it went\" shows each category's spending and its share.",
            "Use ◀ and ▶ at the top to see other months.",
        ),
    ),
    BUDGET_GAME(
        "Budget game",
        "Earn {points} for keeping within each day's share of your money. It's an extra: turn on \"Budget game\" in Settings → Features, with Budget.",
        listOf(
            "Each day gets a share of the month's money: your pay and money in, less bills, spread over the month.",
            "The \"🎮 Money game\" card on the Budget tab shows what's spent today against the share.",
            "A day within its share pays +{goodMoneyDay}, and a day with no spending +{noSpendDay}. Bills don't count against it.",
            "The {points} come the next day. Good days in a row make a money streak.",
            "A month within every category's limit pays +{monthBonus}, and reaching a saving goal +{savedUp}.",
            "Change what it pays in Settings → Points and rules.",
        ),
    ),
    SKIPS_AND_MISSES(
        "Skips and misses",
        "Life happens. Each goal has a few skips and misses allowed for free; past that, they cost a little.",
        listOf(
            "Each goal starts with 1 free skip or miss a week; change it, per week or month, in its editor.",
            "Tap \"Skip\" on Today to skip a goal ahead of time. Within the allowance it needs no reason.",
            "A skip never counts as done, and it isn't held against you either: the goal is just left out of that day. It can't make a perfect day.",
            "\"Take back the skip\" on Today, until the day ends, puts everything back as if you never skipped. Ticking the goal that day does the same.",
            "A goal not done or skipped by the end of its day is a miss, and asks for a reason.",
            "Pick Forgot, No time, Ill, Chose not to, or your own reason, and add a note if you like.",
            "Did it but forgot to tick it? Tap \"I did it, I forgot to tick it\". The goal is ticked for that day and what the miss cost comes back.",
            "With two or more misses waiting, \"Same reason for all\" at the top fills every card with one tap.",
            "A miss can be put right later too, from the Review or from its penalty's line in your {points} history.",
            "Past the allowance, each needs a reason, costs {penalty}% of the goal's {points}, and brings a penalty task.",
            "Set \"My day ends at\" in Settings, so a late night still counts as that day.",
        ),
    ),
    PENALTY(
        "Penalty tasks",
        "Going over the limit means a penalty task, done right away. It pays nothing, but it keeps you on track.",
        listOf(
            "The penalty screen fills the app until the task is done.",
            "Pick a goal under \"Make up a missed goal\" to make up one skipped or missed lately.",
            "Or draw one of your own penalty tasks at random, like 20 push-ups.",
            "Once it's picked, do it, then tap \"Done\".",
            "Write your penalty tasks in Settings → Penalty tasks, or right on the penalty screen.",
            "A gap gets one penalty task for the whole stretch.",
            "Went over the limit with a skip today? \"Take back the skip\" on the penalty screen drops the task and gives the {points} back.",
        ),
    ),
    GAPS(
        "Gaps",
        "When it looks like you stopped for a while, the app asks what happened, once, for the whole stretch.",
        listOf(
            "A gap is 2 or more days in a row with nothing ticked.",
            "Or a big loss: 50 or more {points} taken off for going over the limit within a week.",
            "Change both numbers in Settings → Gaps. Days with nothing due don't count, and neither do days where everything was skipped.",
            "When the app opens, pick one reason, add what happened if you like, and tap \"Carry on\".",
            "One reason and one penalty task cover the whole stretch.",
            "The check-in lists each goal missed, by day. Tap \"I forgot to tick it\" on any you did. If that goes for all of them, the gap goes away.",
            "Misses over the limit in it still cost {points} as usual.",
            "The gap is marked in your history and the review, with what you wrote.",
        ),
    ),
    STREAKS(
        "Streaks",
        "Streaks count the days in a row you kept going. Today shows them near the top.",
        listOf(
            "The main streak counts days when you did enough: 90% of the day's goals to start.",
            "Change that in Settings → Streaks, to a percentage or a number of goals.",
            "Other streaks run beside it: Miss-free, Skip-free, Within the limit, and Perfect days.",
            "Days with nothing due neither count nor break a streak.",
            "A skipped goal is left out of its day: it isn't done, and it doesn't break the main streak. It does end the Skip-free streak, and a perfect day has nothing skipped.",
            "A goal due so many times a week or month counts on the day its week or month ends.",
            "When every streak is going at once, Today shows it big.",
            "Extras in Settings → Features: \"Streak bonus\" pays more on a long streak, and \"Streak shields\" save a bad day.",
        ),
    ),
    DIFFICULTY(
        "Difficulty",
        "One setting that makes the whole app easier or harder: how much goals pay and how many skips and misses are free.",
        listOf(
            "Pick a level in Settings → Difficulty, from Very easy to Very hard.",
            "Usually, Very easy pays double, with 2 more free skips or misses; Very hard pays half, with none free.",
            "Normal pays what each goal is set to. Penalties stay {penalty}% of the goal's own {points}.",
            "Reward prices don't change with the level.",
            "Tap \"Get harder over time…\" to move from one level to another over the weeks you choose.",
            "It moves a level at a time on Mondays. Picking a level yourself stops it.",
            "The numbers for each level can be changed in Settings → Points and rules.",
        ),
    ),
    PRIVACY(
        "Privacy lock",
        "Keep chosen parts of the app behind your phone's own lock. Nothing is locked until you choose it.",
        listOf(
            "In Settings → Privacy, pick what to lock, like the history, the shop, reasons for misses, or the review.",
            "Your moods, their notes, the mood journal and \"Mood and your goals\" are locked with reasons and notes, or with the review.",
            "With Budget on, \"The budget\" locks the Budget tab and every page of it.",
            "Turn on \"Private\" in a goal's editor to lock just that goal.",
            "Tap \"Unlock\" and use your fingerprint, face, PIN or pattern. It stays unlocked until you leave the app.",
            "While anything is locked, turning a lock off or exporting asks you to unlock first.",
            "While locked, private goals are left off the widgets, and their reminders don't name them.",
        ),
    ),
    BACKUP(
        "Backups",
        "Your data stays on this phone, and phone backup is off for this app, so your backup file is the only copy.",
        listOf(
            "In Settings → Backup, tap \"Export everything\" and choose where to save the file.",
            "Tap \"Import a backup\" to bring a file back. It shows what's in it, then replaces everything here.",
            "Tap \"Choose a folder\" for a dated backup each day you use the app. The last 7 are kept.",
            "\"Restore an automatic backup\" brings back one of those.",
            "The same file moves your data to another phone, Android or iPhone.",
            "Today reminds you when your last backup is over a week old: tap \"Back up\" or \"Later\".",
            "\"Export history as a spreadsheet\" saves your {points} history for a spreadsheet app.",
        ),
    ),
    FOCUS(
        "Focus timer",
        "A countdown that fills the screen while you work on a goal, then ticks it for you.",
        listOf(
            "Give a habit a length in its editor, under \"How long it takes, in minutes\".",
            "On Today, tap \"▶ Focus for\" on that goal's card.",
            "When the time's up, the goal is ticked for you.",
            "Tap \"Pause\" for a break, and \"Carry on\" to go again.",
            "Done early? Tap \"Done now\". Or tap \"Stop without ticking\" to leave it for later.",
        ),
    ),
    AI(
        "AI helper",
        "Optional help from an AI that runs on this phone, with no internet. It's an extra: turn on \"AI helper\" in Settings → Features.",
        listOf(
            "It needs the separate Checklist AI app. Tap \"Get Checklist AI\" to find it on the Releases page.",
            "Open Checklist AI once to download and add its AI file, then tap \"Check again\".",
            "On a new habit, type a sentence under \"Describe it\" and tap \"Fill in\".",
            "In a project, tap \"Suggest steps\", tick the ones to keep, and tap \"Add these\".",
            "In the review's week, tap \"Write a note\" for a short note on your week.",
            "With its speech file added, tap 🎙 on Today and say \"done reading\" or \"add buy milk\".",
            "It always asks you first: nothing is saved, ticked or spent on its own.",
        ),
    ),
    WIDGETS(
        "Home-screen widgets",
        "Widgets put your day on the home screen, so you can tick things off without opening the app.",
        listOf(
            "Add one from your phone's widget list, usually by holding an empty spot on the home screen.",
            "\"Checklist today\" shows your day as a timeline, with what's next and a big Done button.",
            "\"Checklist calendar\" shows today's goals with tick boxes. Make it wider for the week, taller for the month.",
            "\"Checklist balance and streaks\" shows your balance, your streak and how much of today is done.",
            "\"Checklist quick to-do\" adds a to-do in one tap.",
            "\"Checklist budget\" shows today's pay filling up, while Budget is on.",
            "Pick a solid or see-through look in Settings → Appearance, under Widgets.",
        ),
    ),
}

/** The groups the guides are listed in, in Help, each with its guides in order. */
enum class HelpGroup(val title: String, val topics: List<HelpTopic>) {
    BASICS(
        "Basics",
        listOf(HelpTopic.TODAY, HelpTopic.GOALS, HelpTopic.REVIEW, HelpTopic.STREAKS, HelpTopic.SKIPS_AND_MISSES, HelpTopic.PENALTY, HelpTopic.GAPS, HelpTopic.BADGES),
    ),
    GOALS(
        "Goals",
        listOf(HelpTopic.HABIT_EDITOR, HelpTopic.PROJECT_EDITOR, HelpTopic.TODO_EDITOR, HelpTopic.FOCUS, HelpTopic.DONE_LIST, HelpTopic.REMOVED_GOALS),
    ),
    PLANNING(
        "Today and planning",
        listOf(HelpTopic.PLAN, HelpTopic.TIMELINE, HelpTopic.WEEK_PLAN, HelpTopic.REMINDERS, HelpTopic.WIDGETS),
    ),
    SHOP(
        "Shop and points",
        listOf(HelpTopic.SHOP, HelpTopic.REWARD_EDITOR, HelpTopic.HISTORY, HelpTopic.BALANCE_CHECK, HelpTopic.RULES, HelpTopic.DIFFICULTY),
    ),
    EXTRAS(
        "Extras",
        listOf(
            HelpTopic.FEATURES, HelpTopic.CHARACTER, HelpTopic.PATHS, HelpTopic.IDEAS, HelpTopic.QUESTS, HelpTopic.COMBOS,
            HelpTopic.RACE, HelpTopic.BOSSES, HelpTopic.LUCKY, HelpTopic.SHIELDS, HelpTopic.AI,
        ),
    ),
    MONEY(
        "Money",
        listOf(
            HelpTopic.BUDGET, HelpTopic.PAY_AND_HOURS, HelpTopic.WORK_DAY, HelpTopic.MONEY_ENTRY, HelpTopic.CATEGORIES,
            HelpTopic.BILLS, HelpTopic.BILL_EDITOR, HelpTopic.BUDGET_REPORT, HelpTopic.BUDGET_GAME,
        ),
    ),
    MOOD("Mood", listOf(HelpTopic.MOOD_GOALS, HelpTopic.MOOD_JOURNAL)),
    DATA("Settings and data", listOf(HelpTopic.SETTINGS, HelpTopic.APPEARANCE, HelpTopic.PRIVACY, HelpTopic.BACKUP)),
}

/** The extras a guide is about, which all have to be on for it to show under "Only extras that are on". */
val HelpTopic.extras: Set<Feature>
    get() = when (this) {
        HelpTopic.TODO_EDITOR -> setOf(Feature.TODOS)
        HelpTopic.TIMELINE -> setOf(Feature.TIMELINE)
        HelpTopic.WEEK_PLAN -> setOf(Feature.WEEKLY_PLANNING)
        HelpTopic.BALANCE_CHECK -> setOf(Feature.BALANCE_CHECK)
        HelpTopic.CHARACTER, HelpTopic.PATHS, HelpTopic.IDEAS -> setOf(Feature.CHARACTER)
        HelpTopic.QUESTS -> setOf(Feature.QUESTS)
        HelpTopic.COMBOS -> setOf(Feature.COMBOS)
        HelpTopic.RACE -> setOf(Feature.RACE)
        HelpTopic.BOSSES -> setOf(Feature.BOSSES)
        HelpTopic.LUCKY -> setOf(Feature.LUCKY)
        HelpTopic.SHIELDS -> setOf(Feature.SHIELDS)
        HelpTopic.AI -> setOf(Feature.AI)
        HelpTopic.BUDGET, HelpTopic.PAY_AND_HOURS, HelpTopic.WORK_DAY, HelpTopic.MONEY_ENTRY, HelpTopic.CATEGORIES,
        HelpTopic.BILLS, HelpTopic.BILL_EDITOR, HelpTopic.BUDGET_REPORT -> setOf(Feature.BUDGET)
        HelpTopic.BUDGET_GAME -> setOf(Feature.BUDGET, Feature.BUDGET_GAME)
        HelpTopic.MOOD_GOALS, HelpTopic.MOOD_JOURNAL -> setOf(Feature.MOOD)
        else -> emptySet()
    }

/** The guides to read next, where parts of the app connect: the "See also" at the end of each. */
val HelpTopic.seeAlso: List<HelpTopic>
    get() = when (this) {
        HelpTopic.TODAY -> listOf(HelpTopic.GOALS, HelpTopic.SKIPS_AND_MISSES, HelpTopic.STREAKS, HelpTopic.REMINDERS, HelpTopic.PLAN)
        HelpTopic.PLAN -> listOf(HelpTopic.TODAY, HelpTopic.TIMELINE, HelpTopic.WEEK_PLAN)
        HelpTopic.GOALS -> listOf(HelpTopic.HABIT_EDITOR, HelpTopic.PROJECT_EDITOR, HelpTopic.TODO_EDITOR, HelpTopic.DONE_LIST)
        HelpTopic.SHOP -> listOf(HelpTopic.REWARD_EDITOR, HelpTopic.HISTORY, HelpTopic.CATEGORIES, HelpTopic.BALANCE_CHECK)
        HelpTopic.REVIEW -> listOf(HelpTopic.MOOD_GOALS, HelpTopic.MOOD_JOURNAL, HelpTopic.BADGES, HelpTopic.GAPS, HelpTopic.STREAKS)
        HelpTopic.BUDGET -> listOf(HelpTopic.PAY_AND_HOURS, HelpTopic.MONEY_ENTRY, HelpTopic.CATEGORIES, HelpTopic.BILLS, HelpTopic.BUDGET_REPORT, HelpTopic.BUDGET_GAME)
        HelpTopic.SETTINGS -> listOf(HelpTopic.FEATURES, HelpTopic.RULES, HelpTopic.APPEARANCE, HelpTopic.PRIVACY, HelpTopic.BACKUP)
        HelpTopic.FEATURES -> listOf(HelpTopic.SETTINGS, HelpTopic.APPEARANCE, HelpTopic.RULES)
        HelpTopic.RULES -> listOf(HelpTopic.DIFFICULTY, HelpTopic.SKIPS_AND_MISSES, HelpTopic.FEATURES)
        HelpTopic.APPEARANCE -> listOf(HelpTopic.TODAY, HelpTopic.WIDGETS, HelpTopic.SETTINGS)
        HelpTopic.HABIT_EDITOR -> listOf(HelpTopic.GOALS, HelpTopic.REMINDERS, HelpTopic.FOCUS, HelpTopic.SKIPS_AND_MISSES)
        HelpTopic.PROJECT_EDITOR -> listOf(HelpTopic.GOALS, HelpTopic.DONE_LIST, HelpTopic.SKIPS_AND_MISSES)
        HelpTopic.TODO_EDITOR -> listOf(HelpTopic.GOALS, HelpTopic.DONE_LIST, HelpTopic.REMINDERS)
        HelpTopic.REWARD_EDITOR -> listOf(HelpTopic.SHOP, HelpTopic.CATEGORIES, HelpTopic.BALANCE_CHECK)
        HelpTopic.BALANCE_CHECK -> listOf(HelpTopic.SHOP, HelpTopic.RULES)
        HelpTopic.HISTORY -> listOf(HelpTopic.SHOP, HelpTopic.GAPS, HelpTopic.BACKUP)
        HelpTopic.CHARACTER -> listOf(HelpTopic.PATHS, HelpTopic.IDEAS, HelpTopic.BADGES, HelpTopic.BOSSES)
        HelpTopic.PATHS -> listOf(HelpTopic.CHARACTER, HelpTopic.QUESTS, HelpTopic.BOSSES, HelpTopic.LUCKY)
        HelpTopic.IDEAS -> listOf(HelpTopic.CHARACTER, HelpTopic.HABIT_EDITOR)
        HelpTopic.QUESTS -> listOf(HelpTopic.TODAY, HelpTopic.PATHS, HelpTopic.RULES)
        HelpTopic.RACE -> listOf(HelpTopic.HISTORY, HelpTopic.WEEK_PLAN)
        HelpTopic.BOSSES -> listOf(HelpTopic.CHARACTER, HelpTopic.PATHS, HelpTopic.RULES)
        HelpTopic.LUCKY -> listOf(HelpTopic.HISTORY, HelpTopic.PATHS)
        HelpTopic.COMBOS -> listOf(HelpTopic.STREAKS, HelpTopic.HISTORY)
        HelpTopic.SHIELDS -> listOf(HelpTopic.STREAKS, HelpTopic.RULES)
        HelpTopic.BADGES -> listOf(HelpTopic.REVIEW, HelpTopic.CHARACTER)
        HelpTopic.MOOD_GOALS -> listOf(HelpTopic.MOOD_JOURNAL, HelpTopic.REVIEW, HelpTopic.PRIVACY)
        HelpTopic.MOOD_JOURNAL -> listOf(HelpTopic.MOOD_GOALS, HelpTopic.REVIEW, HelpTopic.PRIVACY)
        HelpTopic.TIMELINE -> listOf(HelpTopic.PLAN, HelpTopic.REMINDERS)
        HelpTopic.WEEK_PLAN -> listOf(HelpTopic.PLAN, HelpTopic.REVIEW)
        HelpTopic.REMINDERS -> listOf(HelpTopic.HABIT_EDITOR, HelpTopic.SKIPS_AND_MISSES, HelpTopic.PRIVACY, HelpTopic.WIDGETS)
        HelpTopic.DONE_LIST -> listOf(HelpTopic.GOALS, HelpTopic.REMOVED_GOALS)
        HelpTopic.REMOVED_GOALS -> listOf(HelpTopic.GOALS, HelpTopic.DONE_LIST)
        HelpTopic.PAY_AND_HOURS -> listOf(HelpTopic.BUDGET, HelpTopic.WORK_DAY)
        HelpTopic.WORK_DAY -> listOf(HelpTopic.PAY_AND_HOURS, HelpTopic.BUDGET)
        HelpTopic.MONEY_ENTRY -> listOf(HelpTopic.BUDGET, HelpTopic.CATEGORIES, HelpTopic.SHOP)
        HelpTopic.CATEGORIES -> listOf(HelpTopic.BUDGET, HelpTopic.MONEY_ENTRY, HelpTopic.SHOP)
        HelpTopic.BILLS -> listOf(HelpTopic.BILL_EDITOR, HelpTopic.BUDGET)
        HelpTopic.BILL_EDITOR -> listOf(HelpTopic.BILLS, HelpTopic.CATEGORIES)
        HelpTopic.BUDGET_REPORT -> listOf(HelpTopic.BUDGET, HelpTopic.CATEGORIES)
        HelpTopic.BUDGET_GAME -> listOf(HelpTopic.BUDGET, HelpTopic.CATEGORIES, HelpTopic.RULES)
        HelpTopic.SKIPS_AND_MISSES -> listOf(HelpTopic.PENALTY, HelpTopic.GAPS, HelpTopic.STREAKS)
        HelpTopic.PENALTY -> listOf(HelpTopic.SKIPS_AND_MISSES, HelpTopic.GAPS)
        HelpTopic.GAPS -> listOf(HelpTopic.SKIPS_AND_MISSES, HelpTopic.PENALTY, HelpTopic.HISTORY)
        HelpTopic.STREAKS -> listOf(HelpTopic.SHIELDS, HelpTopic.COMBOS, HelpTopic.SKIPS_AND_MISSES)
        HelpTopic.DIFFICULTY -> listOf(HelpTopic.RULES, HelpTopic.SKIPS_AND_MISSES)
        HelpTopic.PRIVACY -> listOf(HelpTopic.MOOD_JOURNAL, HelpTopic.BUDGET, HelpTopic.HISTORY, HelpTopic.REMINDERS)
        HelpTopic.BACKUP -> listOf(HelpTopic.PRIVACY, HelpTopic.HISTORY)
        HelpTopic.FOCUS -> listOf(HelpTopic.HABIT_EDITOR, HelpTopic.TODAY)
        HelpTopic.AI -> listOf(HelpTopic.HABIT_EDITOR, HelpTopic.PROJECT_EDITOR, HelpTopic.REVIEW)
        HelpTopic.WIDGETS -> listOf(HelpTopic.TODAY, HelpTopic.APPEARANCE, HelpTopic.PRIVACY)
    }
