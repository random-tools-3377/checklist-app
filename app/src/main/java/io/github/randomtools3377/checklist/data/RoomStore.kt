package io.github.randomtools3377.checklist.data

import androidx.room.withTransaction
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.RewardSize
import io.github.randomtools3377.checklist.core.Effort
import io.github.randomtools3377.checklist.core.Mood
import io.github.randomtools3377.checklist.core.Challenge
import io.github.randomtools3377.checklist.core.Mystery
import io.github.randomtools3377.checklist.core.Celebration
import io.github.randomtools3377.checklist.core.Check
import io.github.randomtools3377.checklist.core.Days
import io.github.randomtools3377.checklist.core.Entry
import io.github.randomtools3377.checklist.core.EntryKind
import io.github.randomtools3377.checklist.core.Gap
import io.github.randomtools3377.checklist.core.GapKind
import io.github.randomtools3377.checklist.core.Goal
import io.github.randomtools3377.checklist.core.GoalKind
import io.github.randomtools3377.checklist.core.Lapse
import io.github.randomtools3377.checklist.core.LapseKind
import io.github.randomtools3377.checklist.core.Penalty
import io.github.randomtools3377.checklist.core.PenaltyChoice
import io.github.randomtools3377.checklist.core.PenaltyTask
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Reason
import io.github.randomtools3377.checklist.core.RepeatText
import io.github.randomtools3377.checklist.core.Purchase
import io.github.randomtools3377.checklist.core.Reward
import io.github.randomtools3377.checklist.core.Step
import io.github.randomtools3377.checklist.core.Store
import io.github.randomtools3377.checklist.core.Tick
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

const val SETTING_DAY_END_HOUR = "day_end_hour"

fun parseDayEndHour(value: String?): Int = value?.toIntOrNull()?.coerceIn(0, Days.MAX_DAY_END_HOUR) ?: 0

/** A habit's times for each repetition, as stored: minutes after midnight, separated by commas. */
fun encodeTimes(times: List<LocalTime>): String? = times.takeIf { it.isNotEmpty() }?.joinToString(",") { (it.hour * 60 + it.minute).toString() }

fun decodeTimes(text: String?): List<LocalTime> =
    text?.split(",")?.mapNotNull { it.trim().toIntOrNull()?.takeIf { m -> m in 0 until 24 * 60 }?.let { m -> LocalTime(m / 60, m % 60) } }.orEmpty()

/** The [Store] the app uses: the Room database on the phone. */
class RoomStore(private val db: AppDatabase) : Store {
    private val dao = db.dao()

    override suspend fun <T> transaction(block: suspend () -> T): T = db.withTransaction { block() }

    override suspend fun dayEndHour(): Int = parseDayEndHour(dao.setting(SETTING_DAY_END_HOUR))

    override suspend fun goal(id: Long): Goal? = dao.goal(id)?.toCore()
    override suspend fun step(id: Long): Step? = dao.step(id)?.toCore()
    override suspend fun activeSteps(goalId: Long): List<Step> = dao.activeSteps(goalId).map { it.toCore() }

    override suspend fun habitTicks(goalId: Long, periodStart: LocalDate): List<Tick> =
        dao.habitTicks(goalId, periodStart.toEpochDay()).map { it.toCore() }

    override suspend fun stepTick(stepId: Long): Tick? = dao.stepTick(stepId)?.toCore()
    override suspend fun stepTicks(goalId: Long): List<Tick> = dao.stepTicks(goalId).map { it.toCore() }
    override suspend fun checks(goalId: Long, periodStart: LocalDate): List<Check> =
        dao.checks(goalId, periodStart.toEpochDay()).map { it.toCore() }
    override suspend fun addCheck(check: Check): Long = dao.insertCheck(check.toEntity())
    override suspend fun removeCheck(id: Long) = dao.deleteCheck(id)
    override suspend fun tick(id: Long): Tick? = dao.tick(id)?.toCore()
    override suspend fun updateTick(tick: Tick) = dao.updateTick(tick.toEntity())
    override suspend fun updateEntry(entry: Entry) = dao.updateEntry(entry.toEntity())
    override suspend fun addTick(tick: Tick): Long = dao.insertTick(tick.toEntity())
    override suspend fun removeTick(tickId: Long) = dao.deleteTick(tickId)

    override suspend fun addEntry(entry: Entry): Long = dao.insertEntry(entry.toEntity())
    override suspend fun entriesForTicks(tickIds: List<Long>): List<Entry> =
        if (tickIds.isEmpty()) emptyList() else dao.entriesForTicks(tickIds).map { it.toCore() }
    override suspend fun removeEntry(entryId: Long) = dao.deleteEntry(entryId)
    override suspend fun balance(): Int = dao.balance()
    override suspend fun balanceOn(day: LocalDate): Int = dao.balanceOn(day.toEpochDay())

    override suspend fun reward(id: Long): Reward? = dao.reward(id)?.toCore()
    override suspend fun purchase(id: Long): Purchase? = dao.purchase(id)?.toCore()
    override suspend fun purchasesOf(rewardId: Long): List<Purchase> = dao.purchasesOf(rewardId).map { it.toCore() }
    override suspend fun addPurchase(purchase: Purchase): Long = dao.insertPurchase(purchase.toEntity())
    override suspend fun markUsed(purchaseId: Long, at: Long) = dao.markUsed(purchaseId, at)
    override suspend fun markReturned(purchaseId: Long, at: Long) = dao.markReturned(purchaseId, at)

    override suspend fun activeGoals(): List<Goal> = dao.activeGoals().map { it.toCore() }
    override suspend fun setRepeat(goalId: Long, repeat: Repeat?) {
        val row = dao.goal(goalId) ?: return
        dao.updateGoal(row.copy(repeat = repeat?.let { RepeatText.encode(it) }, period = repeat?.windowPeriod?.name))
    }
    override suspend fun habitTicksSince(day: LocalDate): List<Tick> = dao.habitTicksSince(day.toEpochDay()).map { it.toCore() }
    override suspend fun lapses(goalId: Long, from: LocalDate, to: LocalDate): List<Lapse> =
        dao.lapses(goalId, from.toEpochDay(), to.toEpochDay()).map { it.toCore() }
    override suspend fun lapse(id: Long): Lapse? = dao.lapse(id)?.toCore()
    override suspend fun addLapse(lapse: Lapse): Long = dao.insertLapse(lapse.toEntity())
    override suspend fun updateLapse(lapse: Lapse) = dao.updateLapse(lapse.toEntity())
    override suspend fun entriesForLapse(lapseId: Long): List<Entry> = dao.entriesForLapse(lapseId).map { it.toCore() }
    override suspend fun addPenalty(penalty: Penalty): Long = dao.insertPenalty(penalty.toEntity())
    override suspend fun penalty(id: Long): Penalty? = dao.penalty(id)?.toCore()
    override suspend fun updatePenalty(penalty: Penalty) = dao.updatePenalty(penalty.toEntity())
    override suspend fun penaltyForLapse(lapseId: Long): Penalty? = dao.penaltyForLapse(lapseId)?.toCore()
    override suspend fun openPenalties(): List<Penalty> = dao.openPenalties().map { it.toCore() }
    override suspend fun penaltyTasks(): List<PenaltyTask> = dao.penaltyTasks().map { it.toCore() }
    override suspend fun setting(name: String): String? = dao.setting(name)
    override suspend fun putSetting(name: String, value: String) = dao.putSetting(SettingEntity(name, value))

    override suspend fun allLapses(from: LocalDate, to: LocalDate): List<Lapse> =
        dao.allLapses(from.toEpochDay(), to.toEpochDay()).map { it.toCore() }
    override suspend fun entries(from: LocalDate, to: LocalDate): List<Entry> =
        dao.entries(from.toEpochDay(), to.toEpochDay()).map { it.toCore() }
    override suspend fun tickedDays(from: LocalDate, to: LocalDate): Set<LocalDate> =
        dao.tickedDays(from.toEpochDay(), to.toEpochDay()).map { it.toDay() }.toSet()
    override suspend fun gaps(): List<Gap> = dao.gaps().map { it.toCore() }
    override suspend fun gap(id: Long): Gap? = dao.gap(id)?.toCore()
    override suspend fun addGap(gap: Gap): Long = dao.insertGap(gap.toEntity())
    override suspend fun updateGap(gap: Gap) = dao.updateGap(gap.toEntity())
}

fun GoalEntity.toCore() = Goal(
    id = id,
    kind = GoalKind.valueOf(kind),
    name = name,
    icon = icon,
    points = points,
    repeat = repeat?.let { RepeatText.decode(it) },
    target = target,
    bonus = bonus,
    deadline = deadline?.toDay(),
    minutes = minutes,
    at = atMinute?.toTime(),
    finishBy = finishByMinute?.toTime(),
    finishDay = finishDay,
    reminder = reminder?.let { RepeatText.decodeReminder(it) },
    allowance = allowance,
    allowancePer = allowancePer?.let { Period.valueOf(it) },
    private = isPrivate,
    celebration = celebration?.takeIf { Celebration.isValid(it) },
    times = decodeTimes(times),
    category = category?.takeIf { it.isNotBlank() },
    color = color,
    amount = amount?.takeIf { it > 0 },
    unit = unit?.takeIf { it.isNotBlank() },
    effort = Effort.entries.firstOrNull { it.name == effort },
    quit = quit,
    challenge = if (challengeDays != null && challengeDays > 0 && challengeFrom != null) Challenge(challengeDays, challengeBonus ?: 0, challengeFrom.toDay()) else null,
    after = afterGoal,
    skill = io.github.randomtools3377.checklist.core.SkillArea.entries.firstOrNull { it.name == skill },
    archived = archived,
    position = position,
    createdAt = createdAt,
)

fun Goal.toEntity() = GoalEntity(
    id = id,
    kind = kind.name,
    name = name,
    icon = icon,
    points = points,
    period = period?.name,
    target = target,
    bonus = bonus,
    deadline = deadline?.toEpochDay(),
    archived = archived,
    position = position,
    createdAt = createdAt,
    repeat = repeat?.let { RepeatText.encode(it) },
    minutes = minutes,
    atMinute = at?.toMinuteOfDay(),
    finishByMinute = finishBy?.toMinuteOfDay(),
    finishDay = finishDay,
    reminder = reminder?.let { RepeatText.encodeReminder(it) },
    allowance = allowance,
    allowancePer = allowancePer?.name,
    isPrivate = private,
    celebration = celebration,
    times = encodeTimes(times),
    category = category,
    color = color,
    amount = amount,
    unit = unit,
    effort = effort?.name,
    quit = quit,
    challengeDays = challenge?.days,
    challengeBonus = challenge?.bonus,
    challengeFrom = challenge?.from?.toEpochDay(),
    afterGoal = after,
    skill = skill?.name,
)

fun MoodEntity.toCore() = Mood(day = day.toDay(), score = score, note = note, at = at)

fun Mood.toEntity() = MoodEntity(day = day.toEpochDay(), score = score.coerceIn(1, 5), note = note, at = at)

fun StepEntity.toCore() = Step(id = id, goalId = goalId, name = name, points = points, position = position, archived = archived)

fun Step.toEntity() = StepEntity(id = id, goalId = goalId, name = name, points = points, position = position, archived = archived)

fun TickEntity.toCore() = Tick(
    id = id,
    goalId = goalId,
    stepId = stepId,
    at = at,
    day = day.toDay(),
    period = period?.let { Period.valueOf(it) },
    periodStart = periodStart.toDay(),
    note = note,
    amount = amount,
    seconds = seconds,
)

fun Tick.toEntity() = TickEntity(
    id = id,
    goalId = goalId,
    stepId = stepId,
    at = at,
    day = day.toEpochDay(),
    period = period?.name,
    periodStart = periodStart.toEpochDay(),
    note = note,
    amount = amount,
    seconds = seconds,
)

fun EntryEntity.toCore() = Entry(
    id = id,
    at = at,
    day = day.toDay(),
    kind = EntryKind.valueOf(kind),
    amount = amount,
    goalId = goalId,
    tickId = tickId,
    rewardId = rewardId,
    purchaseId = purchaseId,
    label = label,
    lapseId = lapseId,
)

fun Entry.toEntity() = EntryEntity(
    id = id,
    at = at,
    day = day.toEpochDay(),
    kind = kind.name,
    amount = amount,
    goalId = goalId,
    tickId = tickId,
    rewardId = rewardId,
    purchaseId = purchaseId,
    label = label,
    lapseId = lapseId,
)

fun RewardEntity.toCore() = Reward(
    id = id, name = name, icon = icon, price = price, archived = archived, position = position, createdAt = createdAt,
    prizes = Mystery.decode(prizes),
    size = RewardSize.entries.firstOrNull { it.name == size },
    value = value?.takeIf { it > 0 },
)

fun Reward.toEntity() = RewardEntity(
    id = id, name = name, icon = icon, price = price, archived = archived, position = position, createdAt = createdAt,
    prizes = Mystery.encode(prizes),
    size = size?.name,
    value = value,
)

fun PurchaseEntity.toCore() = Purchase(
    id = id, rewardId = rewardId, name = name, icon = icon, price = price, at = at, usedAt = usedAt, returnedAt = returnedAt,
)

fun Purchase.toEntity() = PurchaseEntity(
    id = id, rewardId = rewardId, name = name, icon = icon, price = price, at = at, usedAt = usedAt, returnedAt = returnedAt,
)

fun LapseEntity.toCore() = Lapse(
    id = id,
    goalId = goalId,
    kind = LapseKind.valueOf(kind),
    day = day.toDay(),
    period = period?.let { Period.valueOf(it) },
    periodStart = periodStart.toDay(),
    at = at,
    overLimit = overLimit,
    cost = cost,
    reason = reason?.let { Reason.valueOf(it) },
    note = note,
    forgotten = forgotten,
    label = label,
    gapId = gapId,
    reasonText = reasonText,
)

fun Lapse.toEntity() = LapseEntity(
    id = id,
    goalId = goalId,
    kind = kind.name,
    day = day.toEpochDay(),
    period = period?.name,
    periodStart = periodStart.toEpochDay(),
    at = at,
    overLimit = overLimit,
    cost = cost,
    reason = reason?.name,
    note = note,
    forgotten = forgotten,
    label = label,
    gapId = gapId,
    reasonText = reasonText,
)

fun PenaltyEntity.toCore() = Penalty(
    id = id,
    lapseId = lapseId,
    createdAt = createdAt,
    choice = choice?.let { PenaltyChoice.valueOf(it) },
    goalId = goalId,
    taskId = taskId,
    label = label,
    doneAt = doneAt,
    cancelledAt = cancelledAt,
    gapId = gapId,
)

fun Penalty.toEntity() = PenaltyEntity(
    id = id,
    lapseId = lapseId,
    createdAt = createdAt,
    choice = choice?.name,
    goalId = goalId,
    taskId = taskId,
    label = label,
    doneAt = doneAt,
    cancelledAt = cancelledAt,
    gapId = gapId,
)

fun PenaltyTaskEntity.toCore() = PenaltyTask(id = id, name = name, archived = archived)

fun PenaltyTask.toEntity() = PenaltyTaskEntity(id = id, name = name, archived = archived)

fun CheckEntity.toCore() = Check(id = id, goalId = goalId, stepId = stepId, periodStart = periodStart.toDay(), at = at)

fun Check.toEntity() = CheckEntity(id = id, goalId = goalId, stepId = stepId, periodStart = periodStart.toEpochDay(), at = at)

fun GapEntity.toCore() = Gap(
    id = id,
    from = fromDay.toDay(),
    to = toDay.toDay(),
    kind = GapKind.valueOf(kind),
    createdAt = createdAt,
    reason = reason?.let { Reason.valueOf(it) },
    note = note,
    reasonText = reasonText,
)

fun Gap.toEntity() = GapEntity(
    id = id,
    fromDay = from.toEpochDay(),
    toDay = to.toEpochDay(),
    kind = kind.name,
    createdAt = createdAt,
    reason = reason?.name,
    note = note,
    reasonText = reasonText,
)
