package com.magicsmart.developertv

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magicsmart.core.DeviceInspector
import com.magicsmart.core.ResultStatus
import com.magicsmart.core.SettingReading
import com.magicsmart.core.SettingState
import com.magicsmart.core.assistant.Assistant
import com.magicsmart.core.assistant.AssistantState
import com.magicsmart.core.assistant.Diagnosis
import com.magicsmart.core.assistant.Feedback
import com.magicsmart.core.engine.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.magicsmart.core.engine.ScanController
import com.magicsmart.core.engine.TestDefinition
import com.magicsmart.core.engine.TestResult
import com.magicsmart.core.link.LinkServer
import com.magicsmart.core.link.PairedPeer
import com.magicsmart.core.report.ErrorAnalyzer
import com.magicsmart.core.report.ReportBuilder
import com.magicsmart.core.report.StoredResult
import com.magicsmart.core.report.StoredRun

private enum class Screen { HOME, RESULTS, REPORTS, LINK, ASSIST }

@Composable
fun MagicTvApp() {
    MaterialTheme(colorScheme = magicColors()) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            val context = LocalContext.current
            val controller = remember { ScanController.get(context) }
            var screen by remember { mutableStateOf(Screen.HOME) }
            BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }
            Surface(Modifier.fillMaxSize()) {
                when (screen) {
                    Screen.HOME -> HomeScreen(controller, { screen = Screen.RESULTS }, { screen = Screen.REPORTS }, { screen = Screen.LINK }, { screen = Screen.ASSIST })
                    Screen.RESULTS -> ResultsScreen(controller) { screen = Screen.HOME }
                    Screen.REPORTS -> ReportsScreen(controller) { screen = Screen.HOME }
                    Screen.LINK -> LinkScreen { screen = Screen.HOME }
                    Screen.ASSIST -> AssistScreen { screen = Screen.HOME }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeScreen(controller: ScanController, onOpenResults: () -> Unit, onOpenReports: () -> Unit, onOpenLink: () -> Unit, onOpenAssist: () -> Unit) {
    val context = LocalContext.current
    var snap by remember { mutableStateOf(DeviceInspector.inspect(context)) }
    val results by controller.results.collectAsState()
    val running by controller.running.collectAsState()
    val planned = controller.planned()
    // اختبارات الموافقة تُحسب منفصلة عن الاختبارات التلقائية المخطَّطة
    val done = results.count { !it.definition.requiresConsent }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(running) { snap = DeviceInspector.inspect(context) }
    var listOpen by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<TestDefinition?>(null) }
    val consentTests = controller.consentTests().filter { t -> results.none { it.definition.id == t.id } }
    if (listOpen) {
        AlertDialog(
            onDismissRequest = { listOpen = false },
            title = { Text("اختبارات تتطلب موافقتك / Tests needing your consent") },
            text = {
                Column {
                    consentTests.forEach { t -> TextButton(onClick = { pending = t; listOpen = false }) { Text(t.name, fontSize = 20.sp) } }
                }
            },
            confirmButton = { TextButton(onClick = { listOpen = false }) { Text("إغلاق / Close") } }
        )
    }
    pending?.let { t ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("موافقة مطلوبة / Consent required") },
            text = { Text("سيُنفَّذ الاختبار «${t.name}»:\n${t.description}\n\nلن يُغيَّر أي إعداد. هل توافق؟") },
            confirmButton = { TextButton(onClick = { controller.runConsented(t.id); pending = null }) { Text("أوافق وأنفّذ / I agree, run it") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("إلغاء / Cancel") } }
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp)) {
        Text("MAGIC SMART TV", fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Section("معلومات الجهاز / Device info")
                InfoRow("الشركة المصنّعة / Manufacturer", snap.manufacturer)
                InfoRow("الموديل / Model", snap.model)
                InfoRow("إصدار Android / Android version", "${snap.androidRelease} (API ${snap.sdkInt})")
                InfoRow("نوع الجهاز / Device type", if (snap.isTv) "تلفاز / TV" else "غير تلفاز / Not a TV")
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Section("حالة الإعدادات / Settings status")
                StatusRow("خيارات المطوّر / Developer options", snap.developerOptions)
                StatusRow("تصحيح USB / USB debugging", snap.usbDebugging)
                StatusRow("التصحيح اللاسلكي / Wireless debugging", snap.wirelessDebugging)
                Spacer(Modifier.height(4.dp))
                Text(if (running) "جارٍ الفحص / Running: $done / $planned" else "نُفذ $done من $planned اختباراً / Ran $done of $planned", fontSize = 22.sp, color = Color(0xFFB39DDB))
                LinearProgressIndicator(
                    progress = { if (planned == 0) 0f else (done.toFloat() / planned).coerceAtMost(1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusButton("المساعد / Assistant", Modifier.focusRequester(focus)) { onOpenAssist() }
            FocusButton(
                text = when {
                    running -> "إيقاف / Stop"
                    done == 0 -> "فحص شامل / Full scan"
                    planned in 1..done -> "إعادة الفحص / Rescan"
                    else -> "استكمال / Resume"
                },
            ) {
                when {
                    running -> controller.stop()
                    planned in 1..done -> controller.resetAndStart()
                    else -> controller.start()
                }
            }
            FocusButton("النتائج / Results") { if (results.isNotEmpty()) onOpenResults() }
            FocusButton("التقارير / Reports") { onOpenReports() }
            FocusButton("إعادة الفاشل / Retry failed") { controller.rerunFailed() }
            FocusButton("ربط الهاتف / Link the phone") { onOpenLink() }
            if (consentTests.isNotEmpty()) FocusButton("بموافقتي / With consent (${consentTests.size})") { listOpen = true }
        }
    }
}

@Composable
private fun ResultsScreen(controller: ScanController, onBack: () -> Unit) {
    val results by controller.results.collectAsState()
    var selected by remember { mutableStateOf<Engine?>(null) }
    val shown = results.filter { selected == null || it.definition.engine == selected }
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { backFocus.requestFocus() }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FocusButton("رجوع / Back", Modifier.focusRequester(backFocus), 20.sp, 52.dp) { onBack() }
            FocusButton("الكل / All (${results.size})", fontSize = 20.sp, height = 52.dp) { selected = null }
            results.map { it.definition.engine }.distinct().forEach { e ->
                FocusButton(e.arabic, fontSize = 20.sp, height = 52.dp) { selected = e }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("اضغط OK على أي نتيجة لفتح صفحتها (إن وُجد مسار) / Press OK on a result to open its page (if any)", fontSize = 16.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(shown, key = { it.definition.id }) { ResultRow(it) }
        }
    }
}

@Composable
private fun ReportsScreen(controller: ScanController, onBack: () -> Unit) {
    val results by controller.results.collectAsState()
    val version by controller.historyVersion.collectAsState()
    val history = remember(version) { controller.history() }
    var viewing by remember { mutableStateOf<StoredRun?>(null) }
    BackHandler(enabled = viewing != null) { viewing = null }
    val current = remember(results) { controller.currentRun() }
    val run = viewing ?: current
    val findings = remember(run) { ErrorAnalyzer.analyze(run.results) }
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { backFocus.requestFocus() }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FocusButton("رجوع / Back", Modifier.focusRequester(backFocus), 20.sp, 52.dp) { if (viewing != null) viewing = null else onBack() }
            if (viewing == null) FocusButton("مسح السجل / Clear history", fontSize = 20.sp, height = 52.dp) { controller.clearHistory() }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (run.results.isEmpty()) "لا نتائج بعد. شغّل «فحص شامل» أولاً. / No results yet. Run a full scan first."
            else "${ReportBuilder.date(run.id)} \u00b7 ${run.device} \u00b7 ${run.results.size} اختباراً / tests — ${ReportBuilder.counts(run)}",
            fontSize = 17.sp, color = Color.Gray
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            if (findings.isNotEmpty()) {
                item { Section("تحليل الأخطاء / Error analysis") }
                items(findings) { f ->
                    FocusCard {
                        Text("${f.title} (${f.ids.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(f.explanation, fontSize = 18.sp)
                        Text("المقترح / Suggestion: ${f.suggestion}", fontSize = 18.sp, color = Color(0xFF81D4FA))
                    }
                }
            }
            if (viewing == null) {
                item { Section("سجل الدورات / Run history") }
                items(history, key = { it.id }) { h ->
                    FocusCard(onClick = { viewing = h }) {
                        Text("${ReportBuilder.date(h.id)} \u00b7 ${h.device}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("${h.results.size} اختباراً / tests \u00b7 ${if (h.complete) "مكتملة / complete" else "غير مكتملة / incomplete"} — اضغط OK للعرض / press OK to view", fontSize = 18.sp, color = Color.Gray)
                    }
                }
            } else {
                item { Section("نتائج الدورة / Run results") }
                items(run.results, key = { it.id }) { StoredRow(it) }
            }
        }
    }
}

@Composable
private fun FocusCard(onClick: () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .border(if (focused) 4.dp else 1.dp, if (focused) Color.White else Color(0xFF333333), shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content
    )
}

@Composable
private fun ResultRow(r: TestResult) {
    val context = LocalContext.current
    FocusCard(onClick = {
        r.definition.launch?.let {
            Toast.makeText(context, ScanController.get(context).launch(context, it).actual, Toast.LENGTH_LONG).show()
        }
    }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${r.definition.id}  ${r.definition.name}", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(r.status.arabic, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(r.status.argb))
        }
        Text("الفعلي / Actual: ${r.actual}", fontSize = 18.sp)
        r.failureReason?.let { Text("السبب / Reason: $it", fontSize = 18.sp, color = Color(0xFFFFB74D)) }
        r.nextAction?.let { Text("الإجراء التالي / Next step: $it", fontSize = 18.sp, color = Color(0xFF81D4FA)) }
    }
}

@Composable
private fun StoredRow(r: StoredResult) {
    FocusCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${r.id}  ${r.name}", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(ReportBuilder.label(r.status), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(ReportBuilder.argb(r.status)))
        }
        Text("الفعلي / Actual: ${r.actual}", fontSize = 18.sp)
        r.failureReason?.let { Text("السبب / Reason: $it", fontSize = 18.sp, color = Color(0xFFFFB74D)) }
        r.nextAction?.let { Text("الإجراء التالي / Next step: $it", fontSize = 18.sp, color = Color(0xFF81D4FA)) }
    }
}

@Composable
private fun Section(title: String) =
    Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB39DDB))

@Composable
private fun InfoRow(k: String, v: String) {
    Column {
        Text(k, fontSize = 18.sp, color = Color.Gray)
        Text(v, fontSize = 24.sp)
    }
}

@Composable
private fun StatusRow(title: String, r: SettingReading) {
    Column {
        Text(title, fontSize = 18.sp, color = Color.Gray)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(14.dp).background(stateColor(r.state), CircleShape))
            Text(r.state.arabic, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = stateColor(r.state))
        }
        r.note?.let { Text(it, fontSize = 16.sp, color = Color.Gray) }
    }
}

@Composable
private fun FocusButton(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 20.sp,
    height: Dp = 56.dp,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)
    Button(
        onClick = onClick,
        shape = shape,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .border(if (focused) 4.dp else 0.dp, Color.White, shape)
            .height(height)
    ) { Text(text, fontSize = fontSize) }
}

private fun stateColor(s: SettingState) = when (s) {
    SettingState.ENABLED -> Color(0xFF4CAF50)
    SettingState.DISABLED -> Color(0xFFFFA726)
    SettingState.UNSUPPORTED -> Color(0xFF9E9E9E)
    SettingState.UNREADABLE -> Color(0xFFEF5350)
}

@Composable
private fun LinkScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val server = remember { LinkServer.get(context) }
    val state by server.state.collectAsState()
    val prompt by server.prompt.collectAsState()
    val event by server.event.collectAsState()
    val peers by server.peers.collectAsState()
    var removing by remember { mutableStateOf<PairedPeer?>(null) }
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { backFocus.requestFocus() }
    // الاستماع يتوقف فور مغادرة هذه الشاشة
    DisposableEffect(Unit) { onDispose { server.stop() } }

    prompt?.let { p ->
        AlertDialog(
            onDismissRequest = { server.answerPrompt(false) },
            title = { Text("طلب ربط من «${p.peerName}» / Pairing request from \u201c${p.peerName}\u201d") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("قارن هذا الرمز مع الظاهر على الهاتف. اقبل فقط إذا تطابقا تماماً وكنت أنت من بدأ الربط.\nCompare this code with the phone\u2019s. Accept only if they match exactly and you started the pairing.")
                    Text(p.sas, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = { TextButton(onClick = { server.answerPrompt(true) }) { Text("قبول / Accept — الرمز متطابق", fontSize = 20.sp) } },
            dismissButton = { TextButton(onClick = { server.answerPrompt(false) }) { Text("رفض / Reject", fontSize = 20.sp) } }
        )
    }
    removing?.let { p ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("إلغاء ربط «${p.name}»؟ / Unpair \u201c${p.name}\u201d?") },
            text = { Text("لن يستطيع هذا الجهاز جلب التقارير بعد الآن حتى تقترنا من جديد. / This device won\u2019t be able to fetch reports until you pair again.") },
            confirmButton = { TextButton(onClick = { server.removePeer(p.id); removing = null }) { Text("إلغاء الربط / Unpair", fontSize = 20.sp) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("تراجع / Cancel", fontSize = 20.sp) } }
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FocusButton("رجوع / Back", Modifier.focusRequester(backFocus), 20.sp, 52.dp) { onBack() }
            FocusButton(if (state.listening) "إيقاف وضع الربط / Stop pairing mode" else "بدء وضع الربط / Start pairing mode", fontSize = 20.sp, height = 52.dp) {
                if (state.listening) server.stop() else server.start()
            }
        }
        Spacer(Modifier.height(12.dp))
        if (state.listening) {
            Text("وضع الربط يعمل — العنوان اليدوي: ${state.address ?: "غير معروف / unknown"}:${state.port} / Pairing mode is on — manual address: ${state.address ?: "unknown"}:${state.port}", fontSize = 22.sp, color = Color(0xFF4CAF50))
            Text("الخدمة: ${state.serviceName}. افتح «ربط التلفاز» في تطبيق الهاتف. / Service: ${state.serviceName}. Open \u201cLink the TV\u201d in the phone app.", fontSize = 18.sp, color = Color.Gray)
        } else {
            Text("وضع الربط متوقف. يعمل فقط أثناء بقاء هذه الشاشة مفتوحة، وعلى الشبكة المحلية فقط. / Pairing mode is off. It only runs while this screen stays open, on the local network only.", fontSize = 18.sp, color = Color.Gray)
        }
        event?.let { Text(it, fontSize = 18.sp, color = Color(0xFFB39DDB)) }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            item { Section("الأجهزة المقترنة / Paired devices") }
            if (peers.isEmpty()) item { Text("لا توجد أجهزة مقترنة. / No paired devices.", fontSize = 18.sp, color = Color.Gray) }
            items(peers, key = { it.id }) { p ->
                FocusCard(onClick = { removing = p }) {
                    Text(p.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("اقترن في ${ReportBuilder.date(p.pairedAt)} — اضغط OK لإلغاء الربط / Paired on ${ReportBuilder.date(p.pairedAt)} — press OK to unpair", fontSize = 18.sp, color = Color.Gray)
                }
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
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { backFocus.requestFocus() }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FocusButton("رجوع", Modifier.focusRequester(backFocus), 20.sp, 52.dp) { onBack() }
            FocusButton("تحقّق الآن / Check now", fontSize = 20.sp, height = 52.dp) { tick++ }
        }
        Spacer(Modifier.height(10.dp))
        val d = diag
        if (d == null) {
            Text("جارٍ فحص الجهاز…", fontSize = 20.sp, color = Color.Gray)
        } else {
            Text(d.headline, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(d.tone.argb))
            Text("التطبيق لا يفعّل الخيارات بنفسه؛ يشخّص ويرشدك ويتحقق. التفعيل يتم منك داخل إعدادات النظام. / This app diagnoses and guides only; you enable the setting in system Settings.", fontSize = 16.sp, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(d.steps) { st ->
                    FocusCard(onClick = {
                        st.launch?.let {
                            Toast.makeText(context, ScanController.get(context).launch(context, it).actual, Toast.LENGTH_LONG).show()
                        }
                    }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(st.state.mark, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(st.state.argb))
                            Text(st.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(st.detail, fontSize = 18.sp)
                        if (st.launch != null) Text("اضغط OK: ${st.launchLabel ?: "فتح"}", fontSize = 18.sp, color = Color(0xFF81D4FA))
                    }
                }
                if (d.showFeedback) {
                    item { Section("ماذا حدث عندما ضغطت؟ / What happened?") }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FocusButton("ظهرت رسالة أنني مطوّر / A \u201cdeveloper\u201d message appeared", fontSize = 20.sp, height = 52.dp) { AssistantState.feedback.value = Feedback.MESSAGE_SHOWN }
                            FocusButton("لا أجد Build number / Can\u2019t find it", fontSize = 20.sp, height = 52.dp) { AssistantState.feedback.value = Feedback.NO_BUILD_NUMBER }
                            FocusButton("ضغطت ولم يحدث شيء / Nothing happened", fontSize = 20.sp, height = 52.dp) { AssistantState.feedback.value = Feedback.NOTHING_HAPPENED }
                            FocusButton("بحثت في كل مكان ولم أجده فعلاً / Searched everywhere", fontSize = 20.sp, height = 52.dp) { AssistantState.feedback.value = Feedback.SEARCHED_EVERYWHERE }
                        }
                    }
                }
                item { Section("معلومات التشخيص / Diagnostic info") }
                items(d.facts) { Text(it, fontSize = 17.sp, color = Color.Gray) }
            }
        }
    }
}
