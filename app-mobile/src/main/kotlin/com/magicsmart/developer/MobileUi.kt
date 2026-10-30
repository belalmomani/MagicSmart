package com.magicsmart.developer

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.magicsmart.core.ResultStatus
import com.magicsmart.core.SettingReading
import com.magicsmart.core.SettingState
import com.magicsmart.core.engine.Engine
import com.magicsmart.core.engine.Launcher
import com.magicsmart.core.engine.ScanController
import com.magicsmart.core.engine.TestDefinition
import com.magicsmart.core.engine.TestResult

private enum class Screen { HOME, RESULTS }

@Composable
fun MagicMobileApp() {
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF7C4DFF))) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            val context = LocalContext.current
            val controller = remember { ScanController.get(context) }
            var screen by remember { mutableStateOf(Screen.HOME) }
            BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }
            when (screen) {
                Screen.HOME -> HomeScreen(controller) { screen = Screen.RESULTS }
                Screen.RESULTS -> ResultsScreen(controller) { screen = Screen.HOME }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(controller: ScanController, onOpenResults: () -> Unit) {
    val context = LocalContext.current
    var snap by remember { mutableStateOf(DeviceInspector.inspect(context)) }
    val results by controller.results.collectAsState()
    val running by controller.running.collectAsState()
    val planned = controller.planned()
    // اختبارات الموافقة تُحسب منفصلة عن الاختبارات التلقائية المخطَّطة
    val done = results.count { !it.definition.requiresConsent }
    var pending by remember { mutableStateOf<TestDefinition?>(null) }
    pending?.let { t ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("موافقة مطلوبة") },
            text = { Text("سيُنفَّذ الاختبار «${t.name}»:\n${t.description}\n\nلن يُغيَّر أي إعداد. هل توافق؟") },
            confirmButton = { TextButton(onClick = { controller.runConsented(t.id); pending = null }) { Text("أوافق وأنفّذ") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("إلغاء") } }
        )
    }
    val consentTests = controller.consentTests().filter { t -> results.none { it.definition.id == t.id } }

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
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("الاختبارات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("نُفذ $done من $planned اختباراً")
                        LinearProgressIndicator(
                            progress = { if (planned == 0) 0f else done.toFloat() / planned },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                when {
                                    running -> controller.stop()
                                    planned in 1..done -> controller.resetAndStart()
                                    else -> controller.start()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                when {
                                    running -> "إيقاف الفحص"
                                    done == 0 -> "فحص شامل"
                                    planned in 1..done -> "إعادة الفحص الشامل"
                                    else -> "استكمال الفحص"
                                }
                            )
                        }
                        OutlinedButton(
                            onClick = { controller.rerunFailed() },
                            enabled = !running && results.any {
                                it.status == ResultStatus.FAILED || it.status == ResultStatus.UNKNOWN
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("إعادة اختبار الفاشل / غير المعروف") }
                        OutlinedButton(
                            onClick = onOpenResults,
                            enabled = done > 0,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("نتائج الاختبارات") }
                    }
                }
            }
            if (consentTests.isNotEmpty()) item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("اختبارات تتطلب موافقتك", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        consentTests.forEach { t ->
                            Text(t.name, fontWeight = FontWeight.Bold)
                            Text(t.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            OutlinedButton(onClick = { pending = t }, modifier = Modifier.fillMaxWidth()) { Text("تشغيل بموافقتي") }
                        }
                    }
                }
            }
            item {
                TextButton(
                    onClick = { snap = DeviceInspector.inspect(context) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("تحديث معلومات الجهاز") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultsScreen(controller: ScanController, onBack: () -> Unit) {
    val results by controller.results.collectAsState()
    var selected by remember { mutableStateOf<Engine?>(null) }
    val shown = results.filter { selected == null || it.definition.engine == selected }
    val counts = results.groupingBy { it.status }.eachCount()

    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text("نتائج الاختبارات") },
            navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } }
        )
    }) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = selected == null, onClick = { selected = null }, label = { Text("الكل (${results.size})") })
                controller.plannedEngines().forEach { e ->
                    FilterChip(
                        selected = selected == e, onClick = { selected = e },
                        label = { Text("${e.arabic} (${results.count { it.definition.engine == e }})") }
                    )
                }
            }
            Text(
                counts.entries.joinToString("  ·  ") { "${it.key.arabic}: ${it.value}" },
                style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(shown, key = { it.definition.id }) { ResultCard(it) }
            }
        }
    }
}

@Composable
private fun ResultCard(r: TestResult) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(r.definition.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(r.status.arabic, color = Color(r.status.argb), fontWeight = FontWeight.Bold)
            }
            Text("${r.definition.id} · ${r.definition.engine.arabic}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text("المتوقع: ${r.definition.expected}", style = MaterialTheme.typography.bodySmall)
            Text("الفعلي: ${r.actual}", style = MaterialTheme.typography.bodySmall)
            r.failureReason?.let { Text("السبب: $it", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFB74D)) }
            r.nextAction?.let { Text("الإجراء التالي: $it", style = MaterialTheme.typography.bodySmall, color = Color(0xFF81D4FA)) }
            val spec = r.definition.launch
            if (spec != null && (r.status == ResultStatus.SUCCESS || r.status == ResultStatus.USER_ACTION_REQUIRED)) {
                OutlinedButton(onClick = {
                    Toast.makeText(context, ScanController.get(context).launch(context, spec).actual, Toast.LENGTH_LONG).show()
                }) { Text("فتح الصفحة") }
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
