package dan.sonora.ui.screens.stats.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun InsightSectionTitle(title: String, subtitle: String? = null) {
	Column(modifier = Modifier.fillMaxWidth()) {
		Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
		subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
		Spacer(Modifier.height(12.dp))
	}
}

@Composable
fun AnalyticsCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
	Card(
		modifier = Modifier.fillMaxWidth(),
		shape = RoundedCornerShape(24.dp),
		colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
	) {
		Column(Modifier.padding(18.dp)) {
			Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
			subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
			Spacer(Modifier.height(16.dp))
			content()
		}
	}
}
