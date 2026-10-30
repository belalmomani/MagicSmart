package com.magicsmart.developertv

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magicsmart.core.DeviceInspector
import com.magicsmart.core.SettingReading
import com.magicsmart.core.SettingState

@Composable
fun MagicTvApp() {
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF7C4DFF))) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            val context = LocalContext.current
            var snap by remember { mutableStateOf(DeviceInspector.inspect(context)) }
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }

            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp)) {
                    Text("MAGIC SMART TV", fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Section("معلومات الجهاز")
                            InfoRow("الشركة المصنّعة", snap.manufacturer)
                            InfoRow("الموديل", snap.model)
                            InfoRow("إصدار Android", "${snap.androidRelease} (API ${snap.sdkInt})")
                            InfoRow("نوع الجهاز", if (snap.isTv) "تلفاز" else "غير تلفاز")
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Section("حالة الإعدادات")
                            StatusRow("خيارات المطوّر", snap.developerOptions)
                            StatusRow("تصحيح USB", snap.usbDebugging)
                            StatusRow("التصحيح اللاسلكي", snap.wirelessDebugging)
                        }
                    }
                    FocusButton("إعادة الفحص", focus) { snap = DeviceInspector.inspect(context) }
                }
            }
        }
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
private fun FocusButton(text: String, requester: FocusRequester, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)
    Button(
        onClick = onClick,
        shape = shape,
        modifier = Modifier
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .border(if (focused) 4.dp else 0.dp, Color.White, shape)
            .height(64.dp)
    ) { Text(text, fontSize = 24.sp) }
}

private fun stateColor(s: SettingState) = when (s) {
    SettingState.ENABLED -> Color(0xFF4CAF50)
    SettingState.DISABLED -> Color(0xFFFFA726)
    SettingState.UNSUPPORTED -> Color(0xFF9E9E9E)
    SettingState.UNREADABLE -> Color(0xFFEF5350)
}
