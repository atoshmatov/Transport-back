package uz.safecity.transportobserver.common.dto

import java.time.LocalDate

/**
 * Shared "count grouped by regionName" projection for the reports "regions-distribution"
 * screen (TZ section 7) — reused as-is by Employee/Checkpoint/Incident, since none of them
 * has a real `regions` table to join through yet (all three carry a plain-text `regionName`
 * column, see each entity's TODO kdoc). [regionName] is null for rows whose column is null;
 * `ReportService` relabels that group to a single "Belgilanmagan" bucket.
 */
interface RegionCountProjection {
	val regionName: String?
	val cnt: Long
}

/**
 * Shared "count grouped by local calendar day" projection for the reports "activity" widget
 * (TZ section 7) — reused as-is by Inspection/Incident. [day] is already computed in the
 * `Asia/Tashkent` zone by the backing native query (see InspectionRepository/IncidentRepository),
 * not in UTC, so callers must not re-shift it.
 */
interface DailyCountProjection {
	val day: LocalDate
	val cnt: Long
}
