package com.magicsmart.developertv

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun MagicTvApp() {
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF7C4DFF))) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            val context = LocalContext.current
            val controller = remember { ScanController.get(context) }
            var screen by remember { mutableStateOf(Screen.HOME) }
            BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }
            Surface(Modifier.fillMaxSize()) {
                when (screen) {
                    Screen.HOME -> HomeScreen(controller) { screen = Screen.RESULTS }
                    Screen.RESULTS -> ResultsScreen(controller) { screen = Screen.HOME }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(controller: ScanController, onOpenResults: () -> Unit) {
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
            title = { Text("اختبارات تتطلب موافقتك") },
            text = {
                Column {
                    consentTests.forEach { t -> TextButton(onClick = { pending = t; listOpen = false }) { Text(t.name, fontSize = 20.sp) } }
                }
            },
            confirmButton = { TextButton(onClick = { listOpen = false }) { Text("إغلاق") } }
        )
    }
    pending?.let { t ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("موافقة مطلوبة") },
            text = { Text("سيُنفَّذ الاختبار «${t.name}»:\n${t.description}\n\nلن يُغيَّر أي إعداد. هل توافق؟") },
            confirmButton = { TextButton(onClick = { controller.runConsented(t.id); pending = null }) { Text("أوافق وأنفّذ") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("إلغاء") } }
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp)) {
        Text("MAGIC SMART TV", fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Section("معلومات الجهاز")
                InfoRow("الشركة المصنّعة", snap.manufacturer)
                InfoRow("الموديل", snap.model)
                InfoRow("إصدار Android", "${snap.androidRelease} (API ${snap.sdkInt})")
                InfoRow("نوع الجهاز", if (snap.isTv) "تلفاز" else "غير تلفاز")
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Section("حالة الإعدادات")
                StatusRow("خيارات المطوّر", snap.developerOptions)
                StatusRow("تصحيح USB", snap.usbDebugging)
                StatusRow("التصحيح اللاسلكي", snap.wirelessDebugging)
                Spacer(Modifier.height(4.dp))
                Text("نُفذ $done من $planned اختباراً", fontSize = 22.sp, color = Color(0xFFB39DDB))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FocusButton(
                text = when {
                    running -> "إيقاف الفحص"
                    done == 0 -> "فحص شامل"
                    planned in 1..done -> "إعادة الفحص الشامل"
                    else -> "استكمال الفحص"
                },
                modifier = Modifier.focusRequester(focus)
            ) {
                when {
                    running -> controller.stop()
                    planned in 1..done -> controller.resetAndStart()
                    else -> controller.start()
                }
            }
            FocusButton("النتائج") { if (done > 0) onOpenResults() }
            FocusButton("إعادة اختبار الفاشل") { controller.rerunFailed() }
            if (consentTests.isNotEmpty()) FocusButton("اختبارات بموافقتي (${consentTests.size})") { listOpen = true }
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
            FocusButton("رجوع", Modifier.focusRequester(backFocus), 20.sp, 52.dp) { onBack() }
            FocusButton("الكل (${results.size})", fontSize = 20.sp, height = 52.dp) { selected = null }
            controller.plannedEngines().forEach { e ->
                FocusButton(e.arabic, fontSize = 20.sp, height = 52.dp) { selected = e }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("اضغط OK على أي نتيجة لفتح صفحتها (إن وُجد مسار)", fontSize = 16.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(shown, key = { it.definition.id }) { ResultRow(it) }
        }
    }
}

@Composable
private fun ResultRow(r: TestResult) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .border(if (focused) 4.dp else 1.dp, if (focused) Color.White else Color(0xFF333333), shape)
            .clickable {
                r.definition.launch?.let {
                    Toast.makeText(context, ScanController.get(context).launch(context, it).actual, Toast.LENGTH_LONG).show()
                }
            }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${r.definition.id}  ${r.definition.name}", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(r.status.arabic, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(r.status.argb))
        }
        Text("الفعلي: ${r.actual}", fontSize = 18.sp)
        r.failureReason?.let { Text("السبب: $it", fontSize = 18.sp, color = Color(0xFFFFB74D)) }
        r.nextAction?.let { Text("الإجراء التالي: $it", fontSize = 18.sp, color = Color(0xFF81D4FA)) }
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
        Text(r.state.arabic, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = stateColor(r.state))
        r.note?.let { Text(it, fontSize = 16.sp, color = Color.Gray) }
    }
}

@Composable
private fun FocusButton(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 20.sp,
    height: androidx.compose.ui.unit.Dp = 56.dp,
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
