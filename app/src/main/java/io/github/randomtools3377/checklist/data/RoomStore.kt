package io.github.randomtools3377.checklist.data

import androidx.room.withTransaction
import io.github.randomtools3377.checklist.core.Days
import io.github.randomtools3377.checklist.core.Entry
import io.github.randomtools3377.checklist.core.EntryKind
import io.github.randomtools3377.checklist.core.Goal
import io.github.randomtools3377.checklist.core.GoalKind
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.RepeatText
import io.github.randomtools3377.checklist.core.Purchase
import io.github.randomtools3377.checklist.core.Reward
import io.github.randomtools3377.checklist.core.Step
import io.github.randomtools3377.checklist.core.Store
import io.github.randomtools3377.checklist.core.Tick
import kotlinx.datetime.LocalDate

const val SETTING_DAY_END_HOUR = "day_end_hour"

fun parseDayEndHour(value: String?): Int = value?.toIntOrNull()?.coerceIn(0, Days.MAX_DAY_END_HOUR) ?: 0

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
    override suspend fun addTick(tick: Tick): Long = dao.insertTick(tick.toEntity())
    override suspend fun removeTick(tickId: Long) = dao.deleteTick(tickId)

    override suspend fun addEntry(entry: Entry): Long = dao.insertEntry(entry.toEntity())
    override suspend fun entriesForTicks(tickIds: List<Long>): List<Entry> =
        if (tickIds.isEmpty()) emptyList() else dao.entriesForTicks(tickIds).map { it.toCore() }
    override suspend fun removeEntry(entryId: Long) = dao.deleteEntry(entryId)
    override suspend fun balance(): Int = dao.balance()

    override suspend fun reward(id: Long): Reward? = dao.reward(id)?.toCore()
    override suspend fun purchase(id: Long): Purchase? = dao.purchase(id)?.toCore()
    override suspend fun addPurchase(purchase: Purchase): Long = dao.insertPurchase(purchase.toEntity())
    override suspend fun markUsed(purchaseId: Long, at: Long) = dao.markUsed(purchaseId, at)
    override suspend fun markReturned(purchaseId: Long, at: Long) = dao.markReturned(purchaseId, at)
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
)

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
)

fun Tick.toEntity() = TickEntity(
    id = id,
    goalId = goalId,
    stepId = stepId,
    at = at,
    day = day.toEpochDay(),
    period = period?.name,
    periodStart = periodStart.toEpochDay(),
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
)

fun RewardEntity.toCore() = Reward(
    id = id, name = name, icon = icon, price = price, archived = archived, position = position, createdAt = createdAt,
)

fun Reward.toEntity() = RewardEntity(
    id = id, name = name, icon = icon, price = price, archived = archived, position = position, createdAt = createdAt,
)

fun PurchaseEntity.toCore() = Purchase(
    id = id, rewardId = rewardId, name = name, icon = icon, price = price, at = at, usedAt = usedAt, returnedAt = returnedAt,
)

fun Purchase.toEntity() = PurchaseEntity(
    id = id, rewardId = rewardId, name = name, icon = icon, price = price, at = at, usedAt = usedAt, returnedAt = returnedAt,
)
