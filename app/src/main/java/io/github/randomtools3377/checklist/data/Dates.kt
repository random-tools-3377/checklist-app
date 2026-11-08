package io.github.randomtools3377.checklist.data

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// The database keeps days as epoch days and moments as epoch milliseconds.

fun Long.toDay(): LocalDate = LocalDate.fromEpochDays(toInt())

fun LocalDate.toEpochDay(): Long = toEpochDays().toLong()

fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** The phone's local date and time, in whatever time zone it is set to right now. */
fun nowLocal(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

fun Long.toLocalDateTime(): LocalDateTime =
    kotlinx.datetime.Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault())
