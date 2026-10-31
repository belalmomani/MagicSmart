package com.magicsmart.developer

import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.unit.sp
import com.magicsmart.core.DeviceInspector
import com.magicsmart.core.ResultStatus
import com.magicsmart.core.SettingReading
import com.magicsmart.core.SettingState
import com.magicsmart.core.engine.Engine
import com.magicsmart.core.engine.ScanController
import com.magicsmart.core.engine.TestDefinition
import com.magicsmart.core.engine.TestResult
import com.magicsmart.core.link.LinkController
import com.magicsmart.core.report.ErrorAnalyzer
import com.magicsmart.core.report.Finding
import com.magicsmart.core.report.ReportBuilder
import com.magicsmart.core.report.StoredResult
import com.magicsmart.core.report.StoredRun

private enum class Screen { HOME, RESULTS, REPORTS, LINK }

@Composable
fun MagicMobileApp() {
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF7C4DFF))) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            val context = LocalContext.current
            val controller = remember { ScanController.get(context) }
            var screen by remember { mutableStateOf(Screen.HOME) }
            var resultsEngine by remember { mutableStateOf<Engine?>(null) }
            BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }
            when (screen) {
                Screen.HOME -> HomeScreen(controller, { e -> resultsEngine = e; screen = Screen.RESULTS }, { screen = Screen.REPORTS }, { screen = Screen.LINK })
                Screen.RESULTS -> ResultsScreen(controller, resultsEngine) { screen = Screen.HOME }
                Screen.REPORTS -> ReportsScreen(controller) { screen = Screen.HOME }
                Screen.LINK -> LinkScreen { screen = Screen.HOME }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(controller: ScanController, onOpenResults: (Engine?) -> Unit, onOpenReports: () -> Unit, onOpenLink: () -> Unit) {
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
                                    running -> "إيقاف الفحص"
                                    done == 0 -> "فحص شامل"
                                    planned in 1..done -> "إعادة الفحص الشامل"
                                    else -> "استكمال الفحص"
                                }
                            )
                        }
                        OutlinedButton(
                            onClick = { controller.rerunFailed() },
                            enabled = !running && results.any { it.status == ResultStatus.FAILED || it.status == ResultStatus.UNKNOWN },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("إعادة اختبار الفاشل / غير المعروف") }
                        OutlinedButton(onClick = { onOpenResults(null) }, enabled = results.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                            Text("نتائج الاختبارات")
                        }
                        OutlinedButton(onClick = { onOpenResults(Engine.ADVANCED_PERMISSIONS) }, enabled = results.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                            Text("تحليل الصلاحيات")
                        }
                        OutlinedButton(onClick = onOpenReports, modifier = Modifier.fillMaxWidth()) {
                            Text("التقارير وتحليل الأخطاء")
                        }
                        OutlinedButton(onClick = onOpenLink, modifier = Modifier.fillMaxWidth()) {
                            Text("ربط التلفاز")
                        }
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
                TextButton(onClick = { snap = DeviceInspector.inspect(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("تحديث معلومات الجهاز")
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
            title = { Text("نتائج الاختبارات") },
            navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } }
        )
    }) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = selected == null, onClick = { selected = null }, label = { Text("الكل (${results.size})") })
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
            title = { Text(if (viewing == null) "التقارير" else "سجل ${ReportBuilder.date(run.id)}") },
            navigationIcon = { TextButton(onClick = { if (viewing != null) viewing = null else onBack() }) { Text("رجوع") } }
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
                        Text(if (viewing == null) "الدورة الحالية" else "دورة محفوظة", fontWeight = FontWeight.Bold)
                        Text("${run.device} — Android ${run.androidInfo}", style = MaterialTheme.typography.bodySmall)
                        Text("ملف الشركة: ${run.oem}", style = MaterialTheme.typography.bodySmall)
                        if (run.results.isEmpty()) Text("لا نتائج بعد. شغّل «فحص شامل» أولاً.", color = Color.Gray)
                        else Text("${run.results.size} اختباراً — ${ReportBuilder.counts(run)}", style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = { shareText(context, ReportBuilder.build(run)) },
                            enabled = run.results.isNotEmpty(), modifier = Modifier.fillMaxWidth()
                        ) { Text("مشاركة التقرير (نص)") }
                    }
                }
            }
            if (findings.isNotEmpty()) {
                item { Text("تحليل الأخطاء", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(findings) { FindingCard(it) }
            }
            if (viewing == null) {
                item { Text("سجل الدورات السابقة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                if (history.isEmpty()) item { Text("لا توجد دورات محفوظة بعد.", color = Color.Gray) }
                items(history, key = { it.id }) { h ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${ReportBuilder.date(h.id)} · ${h.device}", fontWeight = FontWeight.Bold)
                            Text(
                                "${h.results.size} اختباراً · ${if (h.complete) "مكتملة" else "غير مكتملة"}",
                                style = MaterialTheme.typography.bodySmall, color = Color.Gray
                            )
                            OutlinedButton(onClick = { viewing = h }) { Text("عرض") }
                        }
                    }
                }
                if (history.isNotEmpty()) item {
                    TextButton(onClick = { controller.clearHistory() }, modifier = Modifier.fillMaxWidth()) { Text("مسح السجل") }
                }
            } else {
                item { Text("نتائج الدورة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(run.results, key = { it.id }) { StoredCard(it) }
            }
        }
    }
}

private fun shareText(context: Context, text: String) {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "تقرير MAGIC SMART")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(i, "مشاركة التقرير"))
}

@Composable
private fun FindingCard(f: Finding) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${f.title} (${f.ids.size})", fontWeight = FontWeight.Bold)
            Text(f.explanation, style = MaterialTheme.typography.bodySmall)
            Text("المقترح: ${f.suggestion}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF81D4FA))
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
            Text("${r.id} · ${ReportBuilder.engineLabel(r.engine)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text("الفعلي: ${r.actual}", style = MaterialTheme.typography.bodySmall)
            r.failureReason?.let { Text("السبب: $it", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFB74D)) }
            r.nextAction?.let { Text("الإجراء التالي: $it", style = MaterialTheme.typography.bodySmall, color = Color(0xFF81D4FA)) }
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
            title = { Text("تأكيد رمز الاقتران") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("قارن هذا الرمز مع الظاهر على شاشة التلفاز «${p.peerName}». اضغط «متطابق» فقط إذا تساوى الرمزان تماماً.")
                    Text(p.code, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = { TextButton(onClick = { lc.answerSas(true) }) { Text("متطابق") } },
            dismissButton = { TextButton(onClick = { lc.answerSas(false) }) { Text("غير متطابق / إلغاء") } }
        )
    }
    fun targetFor(id: String): Pair<String, Int>? =
        tvs.firstOrNull { it.id == id }?.let { it.host to it.port } ?: lc.parseManual(manual)

    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text("ربط التلفاز") },
            navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } }
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
                        Text("الحالة", fontWeight = FontWeight.Bold)
                        Text(status, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "على التلفاز: افتح MAGIC SMART TV ← «ربط الهاتف» ← «بدء وضع الربط». يعمل الربط على الشبكة المحلية فقط، ولا يستطيع تغيير أي إعداد في التلفاز.",
                            style = MaterialTheme.typography.bodySmall, color = Color.Gray
                        )
                    }
                }
            }
            item { Text("تلفازات مقترنة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            if (paired.isEmpty()) item { Text("لا توجد أجهزة مقترنة.", color = Color.Gray) }
            items(paired, key = { it.id }) { p ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(p.name, fontWeight = FontWeight.Bold)
                        Text("اقترن في ${ReportBuilder.date(p.pairedAt)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val t = targetFor(p.id)
                                if (t == null) Toast.makeText(context, "لم يُعثر على التلفاز. شغّل وضع الربط عليه أو أدخل العنوان يدوياً.", Toast.LENGTH_LONG).show()
                                else lc.fetch(p, t.first, t.second)
                            }) { Text("جلب التقرير") }
                            OutlinedButton(onClick = {
                                val t = targetFor(p.id)
                                lc.unpair(p, t?.first, t?.second ?: 0)
                            }) { Text("إلغاء الربط") }
                        }
                    }
                }
            }
            item { Text("تلفازات مكتشفة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            if (tvs.isEmpty()) item { Text("لم يُكتشف شيء بعد.", color = Color.Gray) }
            items(tvs, key = { it.serviceName }) { t ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(t.name, fontWeight = FontWeight.Bold)
                        Text("${t.host}:${t.port}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        val already = paired.any { it.id == t.id }
                        OutlinedButton(onClick = { lc.pair(t.host, t.port) }, enabled = !already) {
                            Text(if (already) "مقترن" else "اقتران")
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("عنوان يدوي (إن تعذّر الاكتشاف)", fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = manual, onValueChange = { manual = it },
                            label = { Text("مثال: 192.168.1.20:43210") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(onClick = {
                            val t = lc.parseManual(manual)
                            if (t == null) Toast.makeText(context, "صيغة غير صحيحة. المطلوب: عنوان:منفذ", Toast.LENGTH_LONG).show()
                            else lc.pair(t.first, t.second)
                        }) { Text("اقتران بهذا العنوان") }
                    }
                }
            }
            remote?.let { rr ->
                val findings = ErrorAnalyzer.analyze(rr.run.results)
                item { Text("تقرير ${rr.peerName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${rr.run.device} — Android ${rr.run.androidInfo}", style = MaterialTheme.typography.bodySmall)
                            if (rr.run.results.isEmpty()) Text("لا نتائج على التلفاز بعد. شغّل «فحص شامل» عليه.", color = Color.Gray)
                            else Text("${rr.run.results.size} اختباراً — ${ReportBuilder.counts(rr.run)}", style = MaterialTheme.typography.bodySmall)
                            Button(
                                onClick = { shareText(context, ReportBuilder.build(rr.run)) },
                                enabled = rr.run.results.isNotEmpty(), modifier = Modifier.fillMaxWidth()
                            ) { Text("مشاركة التقرير (نص)") }
                        }
                    }
                }
                items(findings) { FindingCard(it) }
                items(rr.run.results, key = { "r-" + it.id }) { StoredCard(it) }
            }
        }
    }
}
