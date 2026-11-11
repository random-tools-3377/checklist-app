package io.github.randomtools3377.checklist.core

/**
 * The optional features, each with a switch on the Features page. They start off, so the app
 * stays uncluttered; the user turns on what they want. One already in use stays shown even when
 * off (a to-do that exists, a goal that is a challenge). [key] is the setting each is kept in,
 * and [onValue] what turning it on stores there; empty, missing or "0" is off.
 */
enum class Feature(val key: String, val onValue: String, val title: String, val about: String, val group: String) {
    TODOS("feature_todos", "1", "To-dos on Today", "A box on Today to add quick one-off to-dos, with or without a date.", "Today"),
    TIMELINE("feature_timeline", "1", "Timeline", "A Timeline button on Today and in the planner, to see the day by the hour and drag goals to other times.", "Today"),
    USUAL_REMINDERS("feature_usual_reminders", "1", "Reminders at your usual time", "Goals with no time and no reminder remind you around when you usually tick them, learned from the last 4 weeks.", "Today"),
    MOOD("mood_checkin", "1", "Mood check-in", "Today asks once a day how you feel, and the review shows it beside what got done.", "Today"),
    CALENDAR("phone_calendar", "1", "Phone calendar", "Your phone's calendar events in the planner, the timeline and Today, and pacing leaves their time out.", "Today"),
    WEEKLY_PLANNING("feature_weekly_planning", "1", "Weekly planning", "From Monday, a card on Today opens a few minutes of planning: last week, this week's target and the days ahead.", "Today"),
    SUGGESTIONS("feature_suggestions", "1", "Suggested points and prices", "\"How hard is it?\" on goals and \"What is it worth?\" on rewards, with a suggestion to use.", "Goals and rewards"),
    QUIT("feature_quit", "1", "Goals to quit", "A habit can be something to stop, ticked \"Avoided\" each day, with \"I slipped\".", "Goals and rewards"),
    CHALLENGES("feature_challenges", "1", "Challenges", "A habit can carry a challenge, like 30 days in a row with a bonus at the end.", "Goals and rewards"),
    CHAINS("feature_chains", "1", "Habit chains", "A goal can come after another, like journal after coffee: ticking the first reminds you of the next.", "Goals and rewards"),
    BALANCE_CHECK("feature_balance_check", "1", "Balance check", "A page in the shop comparing prices with what you earn, and goals with how often they get done.", "Goals and rewards"),
    INTEREST("interest_percent", "2", "Savings interest", "Unspent points grow a little at the end of each week.", "Points"),
    BOOST("boost", "1", "Boost the goal missed most", "Each week, the goal missed most lately pays 50% more.", "Points"),
    PERFECT_DAY("perfect_day_bonus", "10", "Perfect-day bonus", "A day with everything due done pays a bonus when it ends.", "Points"),
    STREAK_BONUS("streak_bonus", "1", "Streak bonus", "Goals pay 10% more from a 7-day main streak, 20% from 30 and 30% from 100.", "Points"),
    REPEAT_PRICE("repeat_price", "1", "Prices rise on repeat buys", "A reward costs 25% more for each time it was already bought that week.", "Points"),
    CHARACTER("feature_character", "1", "Character and skills", "Your points level up a character, and six skills, Fitness, Health, Mind, Home, Work and Creative, level up from their own goals.", "Game"),
    QUESTS("feature_quests", "1", "Daily quests", "Three small quests each day from your own goals, like \"tick 2 goals before noon\", each paying a few points.", "Game"),
    COMBOS("feature_combos", "1", "Combos", "Every perfect day in a row builds a combo that pays a little more each day, until a day is left undone.", "Game"),
    RACE("feature_race", "1", "Beat last week", "Today shows this week's points against last week's by the same time, so you can race yourself.", "Game"),
    BOSSES("feature_bosses", "1", "Items and bosses", "Every tick earns an item from its goal's skill. Each week a boss arrives, weaker to two skills; use your items on it from Today to beat it for a bonus.", "Game"),
    LUCKY("feature_lucky", "1", "Lucky ticks", "Now and then a tick pays double or opens a small chest of points. It never takes anything away.", "Game"),
    BETS("feature_bets", "1", "Bet on yourself", "Stake points that you'll do a goal so many times this week: do it and win twice the stake back, fall short and the stake is gone.", "Game"),
    SHIELDS("feature_shields", "1", "Streak shields", "Every 30 days of main streak earns a shield (hold 2 at most). A day that would break the main streak uses one up and the streak goes on.", "Game"),
    CHECKINS("feature_checkins", "1", "Character check-ins", "Your character talks to you on Today: after a slip it suggests one small goal to get going again, it answers how you feel, gentle on a hard day, and it jumps for joy at a new level. Works with Character and skills.", "Game"),
    AI("feature_ai", "1", "AI helper", "Help from an AI that runs on this phone, with no internet: a new habit from a sentence, steps for a project, a note on your week, and voice on Today. Needs the Checklist AI app and its AI file.", "AI"),
    ;

    /** Whether the stored [value] means on. */
    fun isOn(value: String?): Boolean = !value.isNullOrBlank() && value != "0"

    companion object {
        suspend fun isOn(store: Store, feature: Feature): Boolean = feature.isOn(store.setting(feature.key))
    }
}
