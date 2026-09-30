package com.ricordella.app.data.local.converter

import androidx.room.TypeConverter
import com.ricordella.app.domain.model.TripInfo
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Converter Room per i tipi java.time.
 * Le date sono salvate come epochDay (numeri ordinabili e indicizzabili),
 * gli orari come secondi dal mezzanotte, gli istanti in millisecondi UTC.
 */
class RoomConverters {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @TypeConverter
    fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun localDateToLong(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun longToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun localTimeToInt(value: LocalTime?): Int? = value?.toSecondOfDay()

    @TypeConverter
    fun intToLocalTime(value: Int?): LocalTime? = value?.let { LocalTime.ofSecondOfDay(it.toLong()) }

    @TypeConverter
    fun tripToJson(value: TripInfo?): String? = value?.let { json.encodeToString(TripInfo.serializer(), it) }

    @TypeConverter
    fun jsonToTrip(value: String?): TripInfo? = value?.let { runCatching { json.decodeFromString(TripInfo.serializer(), it) }.getOrNull() }

    @TypeConverter
    fun daysOfWeekToMask(value: Set<DayOfWeek>): Int = value.fold(0) { mask, day -> mask or (1 shl day.ordinal) }

    @TypeConverter
    fun maskToDaysOfWeek(value: Int): Set<DayOfWeek> =
        DayOfWeek.entries.filterTo(mutableSetOf()) { value and (1 shl it.ordinal) != 0 }
}
