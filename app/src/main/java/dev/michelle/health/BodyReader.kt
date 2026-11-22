/*
 * Reading from Health Connect (v2): weights, entered in Google Health or any other app, and the
 * height, for the calorie formula. Only in the foreground; Michelle never writes weights (R25, R26).
 */
package dev.michelle.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.time.ZoneId

private const val TAG = "michelle"

class BodyReader(private val context: Context) {
    private val available get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    private suspend fun granted(): Set<String> = if (!available) emptySet() else
        runCatching { withTimeout(10_000) { client.permissionController.getGrantedPermissions() } }.getOrDefault(emptySet())

    suspend fun canReadWeight() = READ_WEIGHT in granted()
    suspend fun allGranted() = granted().containsAll(PERMISSIONS)

    /** The weight of each local day from [from] to [to] (the day's last one), kg; empty if not allowed. */
    suspend fun weights(from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, Double> {
        if (!canReadWeight()) return emptyMap()
        return try {
            val filter = TimeRangeFilter.between(from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant())
            val records = mutableListOf<WeightRecord>()
            var page: String? = null
            do {
                val r = client.readRecords(ReadRecordsRequest(WeightRecord::class, filter, pageSize = 1000, pageToken = page))
                records += r.records
                page = r.pageToken
            } while (page != null)
            val days = records.groupBy { (it.zoneOffset?.let { o -> it.time.atOffset(o).toLocalDate() } ?: it.time.atZone(zone).toLocalDate()) }
                .mapValues { (_, rs) -> rs.maxBy { it.time }.weight.inKilograms }
            Log.i(TAG, "health connect: ${records.size} weights on ${days.size} days, $from..$to")
            days
        } catch (e: Exception) {
            Log.w(TAG, "health connect: reading weights failed", e)
            emptyMap()
        }
    }

    /** The latest height in Health Connect, cm; null if none or not allowed. */
    suspend fun heightCm(): Double? {
        if (READ_HEIGHT !in granted()) return null
        return try {
            val filter = TimeRangeFilter.before(java.time.Instant.now())
            client.readRecords(ReadRecordsRequest(HeightRecord::class, filter, ascendingOrder = false, pageSize = 1))
                .records.firstOrNull()?.height?.inMeters?.times(100)
        } catch (e: Exception) {
            Log.w(TAG, "health connect: reading height failed", e)
            null
        }
    }

    companion object {
        val READ_WEIGHT = HealthPermission.getReadPermission(WeightRecord::class)
        val READ_HEIGHT = HealthPermission.getReadPermission(HeightRecord::class)
        val PERMISSIONS = setOf(READ_WEIGHT, READ_HEIGHT, HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)
    }
}
