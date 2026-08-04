package dan.sonora.ui.screens.stats.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import dan.sonora.data.database.entities.ScrobbleEntity
import dan.sonora.domain.manager.PreferenceManager
import dan.sonora.domain.models.settings.InsightsCalendarMode
import kotlin.time.Clock
import kotlinx.datetime.Instant

@Composable
fun HeatmapCard(
	scrobbles: List<ScrobbleEntity>,
	activityByDate: Map<String, Int>,
	listeningSecondsByDate: Map<String, Long>
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val today = remember {
		Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
			.toLocalDateTime(TimeZone.currentSystemDefault()).date
	}
	val mode = preferenceManager.insightsCalendarMode
	var selectedDate by rememberSaveable { mutableStateOf<String?>(null) }
	val maxActivity = activityByDate.values.maxOrNull()?.coerceAtLeast(1) ?: 1

	AnalyticsCard("Listening calendar", "Tap a day to see the listening detail") {
		SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
			SegmentedButton(
				shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
				selected = mode == InsightsCalendarMode.Month,
				onClick = { preferenceManager.insightsCalendarMode = InsightsCalendarMode.Month },
				label = { Text("Month") }
			)
			SegmentedButton(
				shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
				selected = mode == InsightsCalendarMode.Year,
				onClick = { preferenceManager.insightsCalendarMode = InsightsCalendarMode.Year },
				label = { Text("Year") }
			)
		}
		Spacer(Modifier.height(16.dp))
		AnimatedContent(targetState = mode, label = "heatmap range") { selectedMode ->
			when (selectedMode) {
				InsightsCalendarMode.Month -> MonthHeatmap(
					date = today,
					activityByDate = activityByDate,
					maxActivity = maxActivity,
					onDayClick = { selectedDate = it }
				)
				InsightsCalendarMode.Year -> YearHeatmap(
					year = today.year,
					activityByDate = activityByDate,
					maxActivity = maxActivity,
					onDayClick = { selectedDate = it }
				)
			}
		}
	}

	selectedDate?.let { date ->
		val dayScrobbles = remember(date, scrobbles) {
			scrobbles.filter { scrobbleDate(it) == date }
		}
		AlertDialog(
			onDismissRequest = { selectedDate = null },
			title = { Text(date) },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
					Text(formatListeningTime(listeningSecondsByDate[date]), style = MaterialTheme.typography.titleMedium)
					Text("${dayScrobbles.size} tracks", color = MaterialTheme.colorScheme.onSurfaceVariant)
					Text("${dayScrobbles.map { it.artistName }.distinct().size} artists · ${dayScrobbles.mapNotNull { it.albumName }.distinct().size} albums", color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
			},
			confirmButton = { TextButton(onClick = { selectedDate = null }) { Text("Done") } }
		)
	}
}

@Composable
private fun MonthHeatmap(
	date: LocalDate,
	activityByDate: Map<String, Int>,
	maxActivity: Int,
	onDayClick: (String) -> Unit
) {
	val firstDay = LocalDate(date.year, date.month, 1)
	val leadingEmpty = firstDay.dayOfWeek.ordinal
	val daysInMonth = daysInMonth(date.year, date.month.ordinal + 1)
	val rows = (leadingEmpty + daysInMonth + 6) / 7
	Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
		Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
			listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
		}
		repeat(rows) { row ->
			Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
				repeat(7) { column ->
					val day = row * 7 + column - leadingEmpty + 1
					if (day in 1..daysInMonth) {
						val cellDate = LocalDate(date.year, date.month, day).toString()
						ActivityCell(
							count = activityByDate[cellDate] ?: 0,
							maxActivity = maxActivity,
							label = day.toString(),
							modifier = Modifier.weight(1f).aspectRatio(1f),
							onClick = { onDayClick(cellDate) }
						)
					} else Spacer(Modifier.weight(1f).aspectRatio(1f))
				}
			}
		}
	}
}

@Composable
private fun YearHeatmap(
	year: Int,
	activityByDate: Map<String, Int>,
	maxActivity: Int,
	onDayClick: (String) -> Unit
) {
	val firstDay = LocalDate(year, 1, 1)
	val daysInYear = if (daysInMonth(year, 2) == 29) 366 else 365
	val leadingEmpty = firstDay.dayOfWeek.ordinal
	val totalCells = ((leadingEmpty + daysInYear + 6) / 7) * 7
	val dates = remember(year) {
		List(totalCells) { index ->
			val dayIndex = index - leadingEmpty
			if (dayIndex in 0 until daysInYear) firstDay.plus(dayIndex, DateTimeUnit.DAY).toString() else null
		}
	}
	val emptyColor = MaterialTheme.colorScheme.surfaceContainerHighest
	val primaryColor = MaterialTheme.colorScheme.primary
	Canvas(
		Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f)).padding(4.dp)
			.pointerInput(dates, activityByDate) {
				detectTapGestures { offset ->
					val cellWidth = size.width / (totalCells / 7).toFloat()
					val column = (offset.x / cellWidth).toInt().coerceIn(0, totalCells / 7 - 1)
					val row = (offset.y / (size.height / 7f)).toInt().coerceIn(0, 6)
					dates.getOrNull(column * 7 + row)?.let(onDayClick)
				}
			}
	) {
		val gap = 3f
		val columns = totalCells / 7
		val cell = ((size.width - gap * (columns - 1)) / columns).coerceAtMost(14f)
		for (column in 0 until columns) {
			for (row in 0..6) {
				val date = dates[column * 7 + row]
				val intensity = (activityByDate[date]?.toFloat() ?: 0f) / maxActivity
				val color = if (date == null || intensity == 0f) emptyColor else primaryColor.copy(alpha = .25f + intensity * .75f)
				drawRoundRect(color, androidx.compose.ui.geometry.Offset(column * (cell + gap), row * (cell + gap)), androidx.compose.ui.geometry.Size(cell, cell), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f))
			}
		}
	}
}

@Composable
private fun ActivityCell(count: Int, maxActivity: Int, label: String, modifier: Modifier, onClick: () -> Unit) {
	val color = if (count == 0) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primary.copy(alpha = .25f + count.toFloat() / maxActivity * .75f)
	Box(modifier.clip(RoundedCornerShape(10.dp)).background(color).pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }, contentAlignment = Alignment.Center) {
		Text(label, style = MaterialTheme.typography.labelMedium, color = if (count == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary)
	}
}

private fun scrobbleDate(scrobble: ScrobbleEntity): String =
	Instant.fromEpochSeconds(scrobble.timestamp).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

private fun formatListeningTime(seconds: Long?): String {
		if (seconds == null || seconds <= 0) return "Listening time unavailable for local tracks"
		val hours = seconds / 3600
		val minutes = (seconds % 3600) / 60
		return if (hours > 0) "$hours hr $minutes min listening time" else "$minutes min listening time"
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
		2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
		4, 6, 9, 11 -> 30
		else -> 31
}
