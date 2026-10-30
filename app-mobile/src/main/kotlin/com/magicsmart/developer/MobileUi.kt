package com.magicsmart.developer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.magicsmart.core.DeviceInspector
import com.magicsmart.core.SettingReading
import com.magicsmart.core.SettingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagicMobileApp() {
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF7C4DFF))) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            val context = LocalContext.current
            var snap by remember { mutableStateOf(DeviceInspector.inspect(context)) }
            Scaffold(topBar = {
                CenterAlignedTopAppBar(title = { Text("MAGIC SMART", fontWeight = FontWeight.Bold) })
            }) { pad ->
                LazyColumn(
                    modifier = Modifier.padding(pad).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    item {
                        InfoCard(
                            "معلومات الجهاز",
                            listOf(
                                "الشركة المصنّعة" to snap.manufacturer,
                                "الموديل" to snap.model,
                                "إصدار Android" to "${snap.androidRelease} (API ${snap.sdkInt})",
                                "نوع الجهاز" to if (snap.isTv) "تلفاز" else "هاتف / جهاز لوحي"
                            )
                        )
                    }
                    item { StatusCard("خيارات المطوّر (Developer Options)", snap.developerOptions) }
                    item { StatusCard("تصحيح USB (USB Debugging)", snap.usbDebugging) }
                    item { StatusCard("التصحيح اللاسلكي (Wireless Debugging)", snap.wirelessDebugging) }
                    item {
                        Button(
                            onClick = { snap = DeviceInspector.inspect(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("إعادة الفحص") }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, rows: List<Pair<String, String>>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            rows.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(k, color = Color.Gray)
                    Text(v)
                }
            }
        }
    }
}

@Composable
private fun StatusCard(title: String, reading: SettingReading) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(reading.state.arabic, color = stateColor(reading.state), fontWeight = FontWeight.Bold)
            reading.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
        }
    }
}

private fun stateColor(s: SettingState) = when (s) {
    SettingState.ENABLED -> Color(0xFF4CAF50)
    SettingState.DISABLED -> Color(0xFFFFA726)
    SettingState.UNSUPPORTED -> Color(0xFF9E9E9E)
    SettingState.UNREADABLE -> Color(0xFFEF5350)
}
