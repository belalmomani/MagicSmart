package com.magicsmart.developer

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.unit.sp
import com.magicsmart.core.DeviceInspector
import com.magicsmart.core.ResultStatus
import com.magicsmart.core.SettingReading
import com.magicsmart.core.SettingState
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.magicsmart.core.adb.AdbConnectResult
import com.magicsmart.core.adb.AdbConnection
import com.magicsmart.core.adb.AdbInstall
import com.magicsmart.core.adb.InstallResult
import com.magicsmart.core.assistant.Assistant
import com.magicsmart.core.assistant.AssistantState
import com.magicsmart.core.assistant.Diagnosis
import com.magicsmart.core.assistant.Feedback
import com.magicsmart.core.engine.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.magicsmart.core.engine.ScanController
import com.magicsmart.core.engine.TestDefinition
import com.magicsmart.core.engine.TestResult
import com.magicsmart.core.link.LinkController
import com.magicsmart.core.report.ErrorAnalyzer
import com.magicsmart.core.report.Finding
import com.magicsmart.core.report.ReportBuilder
import com.magicsmart.core.report.StoredResult
import com.magicsmart.core.report.StoredRun

private enum class Screen { HOME, RESULTS, REPORTS, LINK, ASSIST, SHELL, DASHBOARD, INSTALL }

@Composable
fun MagicMobileApp() {
    MaterialTheme(colorScheme = magicColors()) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            val context = LocalContext.current
            val controller = remember { ScanController.get(context) }
            var screen by remember { mutableStateOf(Screen.HOME) }
            var resultsEngine by remember { mutableStateOf<Engine?>(null) }
            BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }
            when (screen) {
                Screen.HOME -> HomeScreen(controller, { e -> resultsEngine = e; screen = Screen.RESULTS }, { screen = Screen.REPORTS }, { screen = Screen.LINK }, { screen = Screen.ASSIST }, { screen = Screen.SHELL }, { screen = Screen.DASHBOARD }, { screen = Screen.INSTALL })
                Screen.RESULTS -> ResultsScreen(controller, resultsEngine) { screen = Screen.HOME }
                Screen.REPORTS -> ReportsScreen(controller) { screen = Screen.HOME }
                Screen.LINK -> LinkScreen { screen = Screen.HOME }
                Screen.ASSIST -> AssistScreen { screen = Screen.HOME }
                Screen.SHELL -> AdbShellScreen { screen = Screen.HOME }
                Screen.DASHBOARD -> AdbDashboardScreen { screen = Screen.HOME }
                Screen.INSTALL -> AdbInstallScreen { screen = Screen.HOME }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(controller: ScanController, onOpenResults: (Engine?) -> Unit, onOpenReports: () -> Unit, onOpenLink: () -> Unit, onOpenAssist: () -> Unit, onOpenShell: () -> Unit, onOpenDashboard: () -> Unit, onOpenInstall: () -> Unit) {
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
            title = { Text("موافقة مطلوبة / Consent required") },
            text = { Text("سيُنفَّذ الاختبار «${t.name}»:\n${t.description}\n\nلن يُغيَّر أي إعداد. هل توافق؟") },
            confirmButton = { TextButton(onClick = { controller.runConsented(t.id); pending = null }) { Text("أوافق وأنفّذ / I agree, run it") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("إلغاء / Cancel") } }
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
                Button(onClick = onOpenAssist, modifier = Modifier.fillMaxWidth()) {
                    Text("مساعد تفعيل خيارات المطوّر / Developer Options Assistant")
                }
            }
            item {
                InfoCard(
                    "معلومات الجهاز / Device info",
                    listOf(
                        "الشركة المصنّعة / Manufacturer" to snap.manufacturer,
                        "الموديل / Model" to snap.model,
                        "إصدار Android / Android version" to "${snap.androidRelease} (API ${snap.sdkInt})",
                        "نوع الجهاز / Device type" to if (snap.isTv) "تلفاز / TV" else "هاتف / جهاز لوحي / Phone/Tablet"
                    )
                )
            }
            item { StatusCard("خيارات المطوّر / Developer Options", snap.developerOptions) }
            item { StatusCard("تصحيح USB / USB Debugging", snap.usbDebugging) }
            item { StatusCard("التصحيح اللاسلكي / Wireless Debugging", snap.wirelessDebugging) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("الاختبارات / Tests", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(if (running) "جارٍ الفحص / Running: $done / $planned" else "نُفذ $done من $planned اختباراً / Ran $done of $planned tests")
                        LinearProgressIndicator(
                            progress = { if (planned == 0) 0f else (done.toFloat() / planned).coerceAtMost(1f) },
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
                                    running -> "إيقاف الفحص / Stop scan"
                                    done == 0 -> "فحص شامل / Full scan"
                                    planned in 1..done -> "إعادة الفحص الشامل / Rescan all"
                                    else -> "استكمال الفحص / Resume scan"
                                }
                            )
                        }
                        OutlinedButton(
                            onClick = { controller.rerunFailed() },
                            enabled = !running && results.any { it.status == ResultStatus.FAILED || it.status == ResultStatus.UNKNOWN },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("إعادة اختبار الفاشل وغير المعروف / Retry failed & unknown") }
                        OutlinedButton(onClick = { onOpenResults(null) }, enabled = results.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                            Text("نتائج الاختبارات / Test results")
                        }
                        OutlinedButton(onClick = { onOpenResults(Engine.ADVANCED_PERMISSIONS) }, enabled = results.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                            Text("تحليل الصلاحيات / Permission analysis")
                        }
                        OutlinedButton(onClick = onOpenReports, modifier = Modifier.fillMaxWidth()) {
                            Text("التقارير وتحليل الأخطاء / Reports & error analysis")
                        }
                        OutlinedButton(onClick = onOpenLink, modifier = Modifier.fillMaxWidth()) {
                            Text("ربط التلفاز / Link the TV")
                        }
                        OutlinedButton(onClick = onOpenShell, modifier = Modifier.fillMaxWidth()) {
                            Text("تنفيذ أوامر ADB / Run ADB commands")
                        }
                        OutlinedButton(onClick = onOpenDashboard, modifier = Modifier.fillMaxWidth()) {
                            Text("لوحة معلومات النظام (عبر ADB) / System dashboard (via ADB)")
                        }
                        OutlinedButton(onClick = onOpenInstall, modifier = Modifier.fillMaxWidth()) {
                            Text("تثبيت وإدارة تطبيقات APK / Install & manage APKs")
                        }
                    }
                }
            }
            if (consentTests.isNotEmpty()) item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("اختبارات تتطلب موافقتك / Tests needing your consent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        consentTests.forEach { t ->
                            Text(t.name, fontWeight = FontWeight.Bold)
                            Text(t.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            OutlinedButton(onClick = { pending = t }, modifier = Modifier.fillMaxWidth()) { Text("تشغيل بموافقتي / Run with my consent") }
                        }
                    }
                }
            }
            item {
                TextButton(onClick = { snap = DeviceInspector.inspect(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("تحديث معلومات الجهاز / Refresh device info")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultsScreen(controller: ScanController, initial: Engine?, onBack: () -> Unit) {
    val results by controller.results.collectAsState()
    var selected by remember { mutableStateOf(initial) }
    val shown = results.filter { selected == null || it.definition.engine == selected }
    val counts = results.groupingBy { it.status }.eachCount()

    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text("نتائج الاختبارات / Test results") },
            navigationIcon = { TextButton(onClick = onBack) { Text("رجوع / Back") } }
        )
    }) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = selected == null, onClick = { selected = null }, label = { Text("الكل / All (${results.size})") })
                results.map { it.definition.engine }.distinct().forEach { e ->
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportsScreen(controller: ScanController, onBack: () -> Unit) {
    val context = LocalContext.current
    val results by controller.results.collectAsState()
    val version by controller.historyVersion.collectAsState()
    val history = remember(version) { controller.history() }
    var viewing by remember { mutableStateOf<StoredRun?>(null) }
    BackHandler(enabled = viewing != null) { viewing = null }
    val current = remember(results) { controller.currentRun() }
    val run = viewing ?: current
    val findings = remember(run) { ErrorAnalyzer.analyze(run.results) }

    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text(if (viewing == null) "التقارير / Reports" else "سجل ${ReportBuilder.date(run.id)} / Log ${ReportBuilder.date(run.id)}") },
            navigationIcon = { TextButton(onClick = { if (viewing != null) viewing = null else onBack() }) { Text("رجوع / Back") } }
        )
    }) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (viewing == null) "الدورة الحالية / Current run" else "دورة محفوظة / Saved run", fontWeight = FontWeight.Bold)
                        Text("${run.device} — Android ${run.androidInfo}", style = MaterialTheme.typography.bodySmall)
                        Text("ملف الشركة: ${run.oem}", style = MaterialTheme.typography.bodySmall)
                        if (run.results.isEmpty()) Text("لا نتائج بعد. شغّل «فحص شامل» أولاً. / No results yet. Run a full scan first.", color = Color.Gray)
                        else Text("${run.results.size} اختباراً / tests — ${ReportBuilder.counts(run)}", style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = { shareText(context, ReportBuilder.build(run)) },
                            enabled = run.results.isNotEmpty(), modifier = Modifier.fillMaxWidth()
                        ) { Text("مشاركة التقرير (نص) / Share report (text)") }
                    }
                }
            }
            if (findings.isNotEmpty()) {
                item { Text("تحليل الأخطاء / Error analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(findings) { FindingCard(it) }
            }
            if (viewing == null) {
                item { Text("سجل الدورات السابقة / Previous runs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                if (history.isEmpty()) item { Text("لا توجد دورات محفوظة بعد. / No saved runs yet.", color = Color.Gray) }
                items(history, key = { it.id }) { h ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${ReportBuilder.date(h.id)} \u00b7 ${h.device}", fontWeight = FontWeight.Bold)
                            Text(
                                "${h.results.size} اختباراً / tests \u00b7 ${if (h.complete) "مكتملة / complete" else "غير مكتملة / incomplete"}",
                                style = MaterialTheme.typography.bodySmall, color = Color.Gray
                            )
                            OutlinedButton(onClick = { viewing = h }) { Text("عرض / View") }
                        }
                    }
                }
                if (history.isNotEmpty()) item {
                    TextButton(onClick = { controller.clearHistory() }, modifier = Modifier.fillMaxWidth()) { Text("مسح السجل / Clear history") }
                }
            } else {
                item { Text("نتائج الدورة / Run results", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(run.results, key = { it.id }) { StoredCard(it) }
            }
        }
    }
}

private fun shareText(context: Context, text: String) {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "تقرير MAGIC SMART / MAGIC SMART report")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(i, "مشاركة التقرير / Share report"))
}

@Composable
private fun FindingCard(f: Finding) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${f.title} (${f.ids.size})", fontWeight = FontWeight.Bold)
            Text(f.explanation, style = MaterialTheme.typography.bodySmall)
            Text("المقترح / Suggestion: ${f.suggestion}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF81D4FA))
            Text(
                f.ids.take(8).joinToString("، ") + if (f.ids.size > 8) " …" else "",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray
            )
        }
    }
}

@Composable
private fun StoredCard(r: StoredResult) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(r.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(ReportBuilder.label(r.status), color = Color(ReportBuilder.argb(r.status)), fontWeight = FontWeight.Bold)
            }
            Text("${r.id} \u00b7 ${ReportBuilder.engineLabel(r.engine)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text("الفعلي / Actual: ${r.actual}", style = MaterialTheme.typography.bodySmall)
            r.failureReason?.let { Text("السبب / Reason: $it", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFB74D)) }
            r.nextAction?.let { Text("الإجراء التالي / Next step: $it", style = MaterialTheme.typography.bodySmall, color = Color(0xFF81D4FA)) }
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
            Text("المتوقع / Expected: ${r.definition.expected}", style = MaterialTheme.typography.bodySmall)
            Text("الفعلي / Actual: ${r.actual}", style = MaterialTheme.typography.bodySmall)
            r.failureReason?.let { Text("السبب / Reason: $it", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFB74D)) }
            r.nextAction?.let { Text("الإجراء التالي / Next step: $it", style = MaterialTheme.typography.bodySmall, color = Color(0xFF81D4FA)) }
            val spec = r.definition.launch
            if (spec != null && (r.status == ResultStatus.SUCCESS || r.status == ResultStatus.USER_ACTION_REQUIRED)) {
                OutlinedButton(onClick = {
                    Toast.makeText(context, ScanController.get(context).launch(context, spec).actual, Toast.LENGTH_LONG).show()
                }) { Text("فتح الصفحة / Open page") }
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(10.dp).background(stateColor(reading.state), CircleShape))
                Text(reading.state.arabic, color = stateColor(reading.state), fontWeight = FontWeight.Bold)
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lc = remember { LinkController.get(context) }
    val tvs by lc.tvs.collectAsState()
    val paired by lc.paired.collectAsState()
    val status by lc.status.collectAsState()
    val sas by lc.sas.collectAsState()
    val remote by lc.remote.collectAsState()
    var manual by remember { mutableStateOf("") }
    DisposableEffect(Unit) {
        lc.startDiscovery()
        onDispose { lc.stopDiscovery() }
    }
    sas?.let { p ->
        AlertDialog(
            onDismissRequest = { lc.answerSas(false) },
            title = { Text("تأكيد رمز الاقتران / Confirm pairing code") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("قارن هذا الرمز مع الظاهر على شاشة التلفاز «${p.peerName}». اضغط «متطابق» فقط إذا تساوى الرمزان تماماً.\nCompare this code with the one on the TV \u201c${p.peerName}\u201d. Tap Match only if they\u2019re identical.")
                    Text(p.code, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = { TextButton(onClick = { lc.answerSas(true) }) { Text("متطابق / Match") } },
            dismissButton = { TextButton(onClick = { lc.answerSas(false) }) { Text("غير متطابق / Cancel") } }
        )
    }
    fun targetFor(id: String): Pair<String, Int>? =
        tvs.firstOrNull { it.id == id }?.let { it.host to it.port } ?: lc.parseManual(manual)

    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text("ربط التلفاز / Link the TV") },
            navigationIcon = { TextButton(onClick = onBack) { Text("رجوع / Back") } }
        )
    }) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("الحالة / Status", fontWeight = FontWeight.Bold)
                        Text(status, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "على التلفاز: افتح MAGIC SMART TV ← «ربط الهاتف» ← «بدء وضع الربط». يعمل الربط على الشبكة المحلية فقط، ولا يستطيع تغيير أي إعداد في التلفاز.\n" +
                                "On the TV: open MAGIC SMART TV \u2192 \u201cLink the phone\u201d \u2192 \u201cStart pairing mode\u201d. Linking works on the local network only and can\u2019t change any TV setting.",
                            style = MaterialTheme.typography.bodySmall, color = Color.Gray
                        )
                    }
                }
            }
            item { Text("تلفازات مقترنة / Paired TVs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            if (paired.isEmpty()) item { Text("لا توجد أجهزة مقترنة. / No paired devices.", color = Color.Gray) }
            items(paired, key = { it.id }) { p ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(p.name, fontWeight = FontWeight.Bold)
                        Text("اقترن في ${ReportBuilder.date(p.pairedAt)} / Paired on ${ReportBuilder.date(p.pairedAt)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val t = targetFor(p.id)
                                if (t == null) Toast.makeText(context, "لم يُعثر على التلفاز. شغّل وضع الربط عليه أو أدخل العنوان يدوياً. / TV not found. Start pairing mode on it or enter the address manually.", Toast.LENGTH_LONG).show()
                                else lc.fetch(p, t.first, t.second)
                            }) { Text("جلب التقرير / Fetch report") }
                            OutlinedButton(onClick = {
                                val t = targetFor(p.id)
                                lc.unpair(p, t?.first, t?.second ?: 0)
                            }) { Text("إلغاء الربط / Unpair") }
                        }
                    }
                }
            }
            item { Text("تلفازات مكتشفة / Discovered TVs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            if (tvs.isEmpty()) item { Text("لم يُكتشف شيء بعد. / Nothing discovered yet.", color = Color.Gray) }
            items(tvs, key = { it.serviceName }) { t ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(t.name, fontWeight = FontWeight.Bold)
                        Text("${t.host}:${t.port}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        val already = paired.any { it.id == t.id }
                        OutlinedButton(onClick = { lc.pair(t.host, t.port) }, enabled = !already) {
                            Text(if (already) "مقترن / Paired" else "اقتران / Pair")
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("عنوان يدوي (إن تعذّر الاكتشاف) / Manual address (if discovery fails)", fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = manual, onValueChange = { manual = it },
                            label = { Text("مثال / Example: 192.168.1.20:43210") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(onClick = {
                            val t = lc.parseManual(manual)
                            if (t == null) Toast.makeText(context, "صيغة غير صحيحة. المطلوب: عنوان:منفذ / Invalid format. Required: address:port", Toast.LENGTH_LONG).show()
                            else lc.pair(t.first, t.second)
                        }) { Text("اقتران بهذا العنوان / Pair with this address") }
                    }
                }
            }
            remote?.let { rr ->
                val findings = ErrorAnalyzer.analyze(rr.run.results)
                item { Text("تقرير ${rr.peerName} / ${rr.peerName}\u2019s report", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${rr.run.device} — Android ${rr.run.androidInfo}", style = MaterialTheme.typography.bodySmall)
                            if (rr.run.results.isEmpty()) Text("لا نتائج على التلفاز بعد. شغّل «فحص شامل» عليه. / No results on the TV yet. Run a full scan there.", color = Color.Gray)
                            else Text("${rr.run.results.size} اختباراً / tests — ${ReportBuilder.counts(rr.run)}", style = MaterialTheme.typography.bodySmall)
                            Button(
                                onClick = { shareText(context, ReportBuilder.build(rr.run)) },
                                enabled = rr.run.results.isNotEmpty(), modifier = Modifier.fillMaxWidth()
                            ) { Text("مشاركة التقرير (نص) / Share report (text)") }
                        }
                    }
                }
                items(findings) { FindingCard(it) }
                items(rr.run.results, key = { "r-" + it.id }) { StoredCard(it) }
            }
        }
    }
}

private fun magicColors() = darkColorScheme(
    primary = Color(0xFF8E6BFF), onPrimary = Color.White,
    secondary = Color(0xFF4DD0C4),
    background = Color(0xFF0F1115), surface = Color(0xFF0F1115),
    surfaceVariant = Color(0xFF1C2029), surfaceContainerHighest = Color(0xFF1C2029),
    error = Color(0xFFEF5350)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val controller = remember { ScanController.get(context) }
    val results by controller.results.collectAsState()
    val running by controller.running.collectAsState()
    val fb by AssistantState.feedback.collectAsState()
    var tick by remember { mutableStateOf(0) }
    val diag by produceState<Diagnosis?>(null, tick, fb, results.size, running) {
        value = withContext(Dispatchers.Default) { Assistant.diagnose(context, fb) }
    }

    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text("مساعد خيارات المطوّر / Developer Options Assistant") },
            navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } }
        )
    }) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            val d = diag
            if (d == null) {
                item { Text("جارٍ فحص الجهاز…", color = Color.Gray) }
            } else {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(d.headline, color = Color(d.tone.argb), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "التطبيق لا يفعّل الخيارات بنفسه؛ يشخّص ويرشدك ويتحقق من النتيجة. التفعيل يتم منك داخل إعدادات النظام.\nThis app does not enable the setting itself; it diagnoses, guides, and verifies. You enable it inside system Settings.",
                                style = MaterialTheme.typography.bodySmall, color = Color.Gray
                            )
                        }
                    }
                }
                items(d.steps) { st ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(st.state.mark, color = Color(st.state.argb), fontWeight = FontWeight.Bold)
                                Text(st.title, fontWeight = FontWeight.Bold)
                            }
                            Text(st.detail, style = MaterialTheme.typography.bodySmall)
                            val spec = st.launch
                            if (spec != null) OutlinedButton(onClick = {
                                Toast.makeText(context, ScanController.get(context).launch(context, spec).actual, Toast.LENGTH_LONG).show()
                            }) { Text(st.launchLabel ?: "فتح") }
                        }
                    }
                }
                if (d.showFeedback) item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("ماذا حدث عندما ضغطت؟ / What happened when you tapped?", fontWeight = FontWeight.Bold)
                            OutlinedButton(onClick = { AssistantState.feedback.value = Feedback.MESSAGE_SHOWN }, modifier = Modifier.fillMaxWidth()) { Text("ظهرت رسالة أنني مطوّر / A \"you are a developer\" message appeared") }
                            OutlinedButton(onClick = { AssistantState.feedback.value = Feedback.NO_BUILD_NUMBER }, modifier = Modifier.fillMaxWidth()) { Text("لا أجد Build number / I can\u2019t find Build number") }
                            OutlinedButton(onClick = { AssistantState.feedback.value = Feedback.NOTHING_HAPPENED }, modifier = Modifier.fillMaxWidth()) { Text("ضغطت ولم يحدث شيء / I tapped it and nothing happened") }
                            OutlinedButton(onClick = { AssistantState.feedback.value = Feedback.SEARCHED_EVERYWHERE }, modifier = Modifier.fillMaxWidth()) { Text("بحثت في كل مكان ولم أجده فعلاً / I searched everywhere and truly can\u2019t find it") }
                        }
                    }
                }
                item { Button(onClick = { tick++ }, modifier = Modifier.fillMaxWidth()) { Text("تحقّق الآن / Check now") } }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("معلومات التشخيص / Diagnostic info", fontWeight = FontWeight.Bold)
                            d.facts.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                        }
                    }
                }
            }
        }
    }
}

private object AdbTarget {
    var host by mutableStateOf("")
    var port by mutableStateOf("5555")
}

private data class ShellEntry(val command: String, val output: String, val ok: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdbShellScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var host by AdbTarget::host
    var port by AdbTarget::port
    var command by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("أدخل عنوان التلفاز ومنفذه (مثال: ADB-003/ADB-005 تعرض المنفذ المُعلن، غالباً 5555). / Enter the TV's address and port (e.g. from ADB-003/ADB-005, usually 5555).") }
    val history = remember { mutableStateListOf<ShellEntry>() }
    val presets = listOf(
        "getprop ro.build.version.release", "dumpsys battery", "pm list packages -3",
        "df -h", "cat /proc/meminfo", "settings get global adb_wifi_enabled"
    )

    fun execute(cmd: String) {
        val p = port.toIntOrNull()
        if (host.isBlank() || p == null) { status = "أدخل عنوانا ومنفذا صحيحين أولاً. / Enter a valid address and port first."; return }
        running = true
        status = "جارٍ التنفيذ… / Running…"
        scope.launch {
            try {
                val (conn, exec) = AdbConnection.connectAndExec(context, host.trim(), p, cmd)
                when (conn) {
                    is AdbConnectResult.Connected -> {
                        val out = exec?.output?.trim().orEmpty()
                        history.add(0, ShellEntry(cmd, out.ifEmpty { "(بلا مخرجات / no output)" }, true))
                        status = "تم التنفيذ. / Done."
                    }
                    is AdbConnectResult.AwaitingDeviceApproval ->
                        status = "انتظر: اقبل «السماح بتصحيح USB؟» على شاشة التلفاز بالريموت، ثم أعد الإرسال. / Waiting: accept \"Allow USB debugging?\" on the TV with the remote, then retry."
                    is AdbConnectResult.Failed ->
                        history.add(0, ShellEntry(cmd, "فشل / Failed: ${conn.reason}", false)).also { status = "فشل. / Failed." }
                }
            } catch (e: Exception) {
                history.add(0, ShellEntry(cmd, "${e.javaClass.simpleName}: ${e.message}", false))
                status = "فشل الاتصال. / Connection failed."
            } finally { running = false }
        }
    }

    pending?.let { cmd ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("تأكيد تنفيذ أمر ADB / Confirm ADB command") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("سيُنفَّذ هذا الأمر على التلفاز عبر ADB shell:")
                    Text(cmd, fontWeight = FontWeight.Bold)
                    Text(
                        "غير مُختبر بعد على جهاز حقيقي؛ أمر قد يُغيّر النظام يحتاج حذراً منك. / Not yet tested on a real device; a command that can change the system needs your own caution.",
                        style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFB74D)
                    )
                }
            },
            confirmButton = { TextButton(onClick = { pending = null; execute(cmd) }) { Text("أوافق وأنفّذ / I agree, run it") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("إلغاء / Cancel") } }
        )
    }

    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text("تنفيذ أوامر ADB / ADB Shell") },
            navigationIcon = { TextButton(onClick = onBack) { Text("رجوع / Back") } }
        )
    }) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "يعيد هذا التطبيق تنفيذ بروتوكول ADB القياسي بنفسه، بلا أداة adb على حاسوب. أول اتصال سيطلب منك قبول «السماح بتصحيح USB؟» على شاشة التلفاز بالريموت. كل أمر يُنفَّذ بموافقتك الصريحة في كل مرة.\n" +
                                "This app speaks the standard ADB protocol itself, no PC adb tool needed. The first connection asks you to accept \u201cAllow USB debugging?\u201d on the TV with the remote. Every command runs only after your explicit consent each time.",
                            style = MaterialTheme.typography.bodySmall, color = Color.Gray
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("عنوان التلفاز / TV address") }, singleLine = true, modifier = Modifier.weight(2f))
                            OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("منفذ / Port") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Text(status, style = MaterialTheme.typography.bodySmall, color = Color(0xFF81D4FA))
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("أمر جاهز / Quick commands", fontWeight = FontWeight.Bold)
                        presets.forEach { c ->
                            OutlinedButton(onClick = { pending = c }, enabled = !running, modifier = Modifier.fillMaxWidth()) { Text(c) }
                        }
                        OutlinedTextField(
                            value = command, onValueChange = { command = it },
                            label = { Text("أمر مخصص / Custom command") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { if (command.isNotBlank()) pending = command.trim() },
                            enabled = !running && command.isNotBlank(), modifier = Modifier.fillMaxWidth()
                        ) { Text(if (running) "جارٍ التنفيذ… / Running…" else "تنفيذ / Run") }
                    }
                }
            }
            item { Text("السجل / History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(history) { e ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("$ ${e.command}", fontWeight = FontWeight.Bold, color = if (e.ok) Color.Unspecified else Color(0xFFEF5350))
                        Text(e.output, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private data class DashField(val label: String, val value: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdbDashboardScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var host by AdbTarget::host
    var port by AdbTarget::port
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("اجلب لوحة المعلومات بأمر واحد يشغّل عدة أوامر قراءة فقط. / Fetch the dashboard with one action that runs several read-only commands.") }
    var fields by remember { mutableStateOf<List<DashField>>(emptyList()) }
    var confirmOpen by remember { mutableStateOf(false) }

    val commands = listOf(
        "model" to "getprop ro.product.model",
        "release" to "getprop ro.build.version.release",
        "uptime" to "uptime",
        "battery" to "dumpsys battery",
        "mem" to "cat /proc/meminfo",
        "storage" to "df -h /data",
        "apps" to "pm list packages -3"
    )

    fun parseBattery(s: String): String {
        val level = Regex("level: (\\d+)").find(s)?.groupValues?.get(1)
        val status = Regex("status: (\\d+)").find(s)?.groupValues?.get(1)
        val statusText = when (status) { "2" -> "يشحن / Charging"; "3" -> "غير متصل / Discharging"; "5" -> "ممتلئة / Full"; else -> status ?: "?" }
        return if (level != null) "$level% — $statusText" else "? / unknown"
    }
    fun parseMem(s: String): String {
        val total = Regex("MemTotal:\\s*(\\d+) kB").find(s)?.groupValues?.get(1)?.toLongOrNull()
        val free = Regex("MemAvailable:\\s*(\\d+) kB").find(s)?.groupValues?.get(1)?.toLongOrNull()
        return if (total != null && free != null) "${free / 1024} MB متاحة / free من ${total / 1024} MB" else "? / unknown"
    }
    fun parseStorage(s: String): String {
        val line = s.lines().getOrNull(1)?.trim()?.split(Regex("\\s+"))
        return if (line != null && line.size >= 5) "${line[2]} مستخدم / used — ${line[3]} متاح / free (${line[4]})" else "? / unknown"
    }
    fun parseApps(s: String): String {
        val n = s.lines().count { it.startsWith("package:") }
        return "$n تطبيق مثبَّت من المستخدم / user-installed apps"
    }

    fun fetch() {
        val p = port.toIntOrNull()
        if (host.isBlank() || p == null) { status = "أدخل عنوانا ومنفذا صحيحين أولاً. / Enter a valid address and port first."; return }
        loading = true
        status = "جارٍ الجلب… قد يطلب أول اتصال موافقتك على شاشة التلفاز. / Fetching… the first connection may ask for your approval on the TV screen."
        scope.launch {
            val results = mutableMapOf<String, String>()
            var authWait = false
            var failMsg: String? = null
            for ((key, cmd) in commands) {
                try {
                    val (conn, exec) = AdbConnection.connectAndExec(context, host.trim(), p, cmd)
                    when (conn) {
                        is AdbConnectResult.Connected -> results[key] = exec?.output?.trim().orEmpty()
                        is AdbConnectResult.AwaitingDeviceApproval -> { authWait = true; break }
                        is AdbConnectResult.Failed -> { failMsg = conn.reason; break }
                    }
                } catch (e: Exception) { failMsg = "${e.javaClass.simpleName}: ${e.message}"; break }
            }
            loading = false
            when {
                authWait -> status = "انتظر: اقبل «السماح بتصحيح USB؟» على شاشة التلفاز بالريموت، ثم اضغط جلب مرة أخرى. / Waiting: accept \"Allow USB debugging?\" on the TV, then fetch again."
                failMsg != null -> status = "فشل / Failed: $failMsg"
                else -> {
                    fields = listOf(
                        DashField("الموديل / Model", results["model"].orEmpty()),
                        DashField("إصدار Android / Android version", results["release"].orEmpty()),
                        DashField("مدة التشغيل / Uptime", results["uptime"].orEmpty().substringBefore(",")),
                        DashField("البطارية / Battery", parseBattery(results["battery"].orEmpty())),
                        DashField("الذاكرة / Memory", parseMem(results["mem"].orEmpty())),
                        DashField("التخزين (/data) / Storage", parseStorage(results["storage"].orEmpty())),
                        DashField("التطبيقات / Apps", parseApps(results["apps"].orEmpty()))
                    )
                    status = "تم التحديث. / Updated."
                }
            }
        }
    }

    if (confirmOpen) {
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            title = { Text("جلب لوحة المعلومات / Fetch dashboard") },
            text = {
                Text(
                    "سيُشغَّل عبر ADB عدد من أوامر القراءة فقط (معلومات الجهاز، البطارية، الذاكرة، التخزين، عدد التطبيقات) ولن يُغيَّر أي شيء.\n" +
                        "This runs several read-only ADB commands (device info, battery, memory, storage, app count) and changes nothing."
                )
            },
            confirmButton = { TextButton(onClick = { confirmOpen = false; fetch() }) { Text("أوافق وأجلب / I agree, fetch") } },
            dismissButton = { TextButton(onClick = { confirmOpen = false }) { Text("إلغاء / Cancel") } }
        )
    }

    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text("لوحة معلومات النظام / System dashboard") },
            navigationIcon = { TextButton(onClick = onBack) { Text("رجوع / Back") } }
        )
    }) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("عنوان التلفاز / TV address") }, singleLine = true, modifier = Modifier.weight(2f))
                            OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("منفذ / Port") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Button(onClick = { confirmOpen = true }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                            Text(if (loading) "جارٍ الجلب… / Fetching…" else "جلب / Fetch")
                        }
                        Text(status, style = MaterialTheme.typography.bodySmall, color = Color(0xFF81D4FA))
                    }
                }
            }
            items(fields) { f ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(f.label, color = Color.Gray)
                        Text(f.value, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdbInstallScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var host by AdbTarget::host
    var port by AdbTarget::port
    var picked by remember { mutableStateOf<Uri?>(null) }
    var pickedName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("اختر ملف APK من الهاتف لتثبيته على التلفاز. / Pick an APK file from the phone to install it on the TV.") }
    var confirmInstall by remember { mutableStateOf(false) }
    var uninstallTarget by remember { mutableStateOf<String?>(null) }
    var packages by remember { mutableStateOf<List<String>>(emptyList()) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            picked = uri
            pickedName = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && i >= 0) c.getString(i) else null
            } ?: uri.lastPathSegment ?: "app.apk"
        }
    }

    fun doInstall() {
        val p = port.toIntOrNull()
        val uri = picked
        if (host.isBlank() || p == null || uri == null) { status = "اختر ملفاً وأدخل عنواناً ومنفذاً صحيحين. / Pick a file and enter a valid address/port."; return }
        busy = true
        scope.launch {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) { status = "تعذّرت قراءة الملف. / Could not read the file."; busy = false; return@launch }
                val safeName = pickedName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "app.apk" }
                val (conn, res) = AdbInstall.pushAndInstall(context, host.trim(), p, bytes, safeName, { status = it })
                status = when {
                    conn is AdbConnectResult.AwaitingDeviceApproval ->
                        "انتظر: اقبل «السماح بتصحيح USB؟» على شاشة التلفاز، ثم حاول مجدداً. / Waiting: accept the prompt on the TV, then retry."
                    conn is AdbConnectResult.Failed -> "فشل الاتصال / Connection failed: ${conn.reason}"
                    res is InstallResult.Success -> "تم التثبيت بنجاح. / Installed successfully."
                    res is InstallResult.PmError -> "فشل التثبيت / Install failed: ${res.output}"
                    res is InstallResult.Failed -> "فشل الرفع / Upload failed: ${res.reason}"
                    else -> "نتيجة غير متوقعة. / Unexpected result."
                }
            } catch (e: Exception) {
                status = "${e.javaClass.simpleName}: ${e.message}"
            } finally { busy = false }
        }
    }

    fun listApps() {
        val p = port.toIntOrNull()
        if (host.isBlank() || p == null) { status = "أدخل عنواناً ومنفذاً صحيحين أولاً. / Enter a valid address/port first."; return }
        busy = true
        scope.launch {
            try {
                val (conn, exec) = AdbConnection.connectAndExec(context, host.trim(), p, "pm list packages -3")
                when (conn) {
                    is AdbConnectResult.Connected -> {
                        packages = exec?.output.orEmpty().lines()
                            .mapNotNull { it.removePrefix("package:").trim().takeIf { s -> s.isNotEmpty() } }
                            .sorted()
                        status = "${packages.size} تطبيقاً. / ${packages.size} apps."
                    }
                    is AdbConnectResult.AwaitingDeviceApproval -> status = "انتظر الموافقة على شاشة التلفاز ثم أعد المحاولة. / Wait for approval on the TV, then retry."
                    is AdbConnectResult.Failed -> status = "فشل / Failed: ${conn.reason}"
                }
            } catch (e: Exception) { status = "${e.javaClass.simpleName}: ${e.message}" }
            busy = false
        }
    }

    uninstallTarget?.let { pkg ->
        AlertDialog(
            onDismissRequest = { uninstallTarget = null },
            title = { Text("إلغاء تثبيت / Uninstall") },
            text = { Text("سيُلغى تثبيت: $pkg") },
            confirmButton = {
                TextButton(onClick = {
                    uninstallTarget = null; busy = true
                    scope.launch {
                        val p = port.toIntOrNull() ?: return@launch
                        val (conn, exec) = AdbConnection.connectAndExec(context, host.trim(), p, "pm uninstall $pkg")
                        status = when (conn) {
                            is AdbConnectResult.Connected -> exec?.output?.trim().orEmpty().ifEmpty { "تم / Done" }
                            is AdbConnectResult.AwaitingDeviceApproval -> "انتظر الموافقة على شاشة التلفاز. / Wait for approval on the TV."
                            is AdbConnectResult.Failed -> "فشل / Failed: ${conn.reason}"
                        }
                        if ((exec?.output ?: "").contains("Success", true)) packages = packages - pkg
                        busy = false
                    }
                }) { Text("إلغاء التثبيت / Uninstall") }
            },
            dismissButton = { TextButton(onClick = { uninstallTarget = null }) { Text("تراجع / Cancel") } }
        )
    }
    if (confirmInstall) {
        AlertDialog(
            onDismissRequest = { confirmInstall = false },
            title = { Text("تثبيت على التلفاز / Install on the TV") },
            text = {
                Text(
                    "سيُرفَع «$pickedName» إلى التلفاز ويُثبَّت بـ pm install -r. أول اتصال قد يطلب موافقتك على شاشة التلفاز.\n" +
                        "\u201c$pickedName\u201d will be uploaded to the TV and installed via pm install -r. The first connection may ask for your approval on the TV screen."
                )
            },
            confirmButton = { TextButton(onClick = { confirmInstall = false; doInstall() }) { Text("أوافق وأثبّت / I agree, install") } },
            dismissButton = { TextButton(onClick = { confirmInstall = false }) { Text("إلغاء / Cancel") } }
        )
    }

    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text("تثبيت وإدارة التطبيقات / Install & manage apps") },
            navigationIcon = { TextButton(onClick = onBack) { Text("رجوع / Back") } }
        )
    }) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "غير مُختبر بعد على جهاز حقيقي. أسوأ احتمال عند خطأ هو فشل الرفع أو فشل pm install برسالة واضحة، دون أي خطر على بقية النظام.\n" +
                                "Not yet tested on a real device. Worst case on a bug: upload or pm install fails with a clear message \u2014 no risk to the rest of the system.",
                            style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFB74D)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("عنوان التلفاز / TV address") }, singleLine = true, modifier = Modifier.weight(2f))
                            OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("منفذ / Port") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Text(status, style = MaterialTheme.typography.bodySmall, color = Color(0xFF81D4FA))
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("تثبيت APK / Install APK", fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = { picker.launch("application/vnd.android.package-archive") }, modifier = Modifier.fillMaxWidth()) {
                            Text(pickedName.ifEmpty { "اختر ملف APK / Pick an APK file" })
                        }
                        Button(
                            onClick = { confirmInstall = true }, enabled = !busy && picked != null,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (busy) "جارٍ العمل… / Working…" else "رفع وتثبيت / Upload & install") }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("التطبيقات المثبَّتة / Installed apps", fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = { listApps() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("تحديث القائمة / Refresh list") }
                    }
                }
            }
            items(packages) { pkg ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(pkg, modifier = Modifier.weight(1f))
                        TextButton(onClick = { uninstallTarget = pkg }) { Text("إلغاء التثبيت / Uninstall") }
                    }
                }
            }
        }
    }
}
