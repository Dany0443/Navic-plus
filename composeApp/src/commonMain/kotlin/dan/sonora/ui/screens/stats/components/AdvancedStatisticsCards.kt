package dan.sonora.ui.screens.stats.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dan.sonora.ui.screens.stats.viewmodels.AdvancedStatisticItem
import dan.sonora.ui.screens.stats.viewmodels.AdvancedStatisticsSection

@Composable
fun AdvancedStatisticsSectionCard(section: AdvancedStatisticsSection) {
	AnalyticsCard(
		title = section.title,
		subtitle = section.subtitle
	) {
		section.items.chunked(2).forEachIndexed { index, rowItems ->
			if (index > 0) {
				Spacer(Modifier.height(12.dp))
			}
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(12.dp)
			) {
				rowItems.forEach { item ->
					AdvancedStatisticItemCard(
						item = item,
						modifier = Modifier.weight(1f)
					)
				}
				repeat(2 - rowItems.size) {
					Spacer(Modifier.weight(1f))
				}
			}
		}
	}
}

@Composable
private fun AdvancedStatisticItemCard(
	item: AdvancedStatisticItem,
	modifier: Modifier = Modifier
) {
	Column(
		modifier = modifier
			.background(
				color = MaterialTheme.colorScheme.surfaceVariant,
				shape = RoundedCornerShape(18.dp)
			)
			.padding(14.dp),
		verticalArrangement = Arrangement.spacedBy(6.dp)
	) {
		Text(
			text = item.value,
			style = MaterialTheme.typography.titleMedium,
			fontWeight = FontWeight.Bold
		)
		Text(
			text = item.label,
			style = MaterialTheme.typography.labelMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
		item.supportingText?.let { supportingText ->
			Text(
				text = supportingText,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
	}
}
