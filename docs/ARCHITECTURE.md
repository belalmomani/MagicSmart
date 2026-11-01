# البنية

```
core/                       منطق مشترك (بلا واجهة)
  DeviceInspector, DeviceSnapshot, ResultStatus
  engine/   Model (TestDefinition…), TestEngine+TestRegistry, ScanController, Probes, Launcher, Env, NsdProbe
            *Tests.kt: DeveloperOptions, SettingsScanner, AndroidTv, UsbDebugging, WirelessDebugging,
                       AdbAnalyzer, AdvancedPermissions, OemTests (مُولَّد من ملفات oem)
            SettingsActions.kt (مُولَّد)
  oem/      OemProfile + OemProfiles (تحميل ومطابقة ملفات assets/oem/*.json)
  report/   ResultStore (سجل JSON)، ErrorAnalyzer، ReportBuilder
  link/     Wire/LinkCrypto/SecureChannel، LinkStore، LinkServer (تلفاز)، LinkClient+LinkController+LinkDiscovery (هاتف)
  assets/oem/*.json         ملفات تعريف الشركات
app-mobile/  com.magicsmart.developer     (Compose + Material 3)
app-tv/      com.magicsmart.developertv   (Compose + D-pad)
tools/       sync_queries.py + settings_actions.txt
```

## إضافة اختبار جديد
1. مسار إعدادات: سطر في `tools/settings_actions.txt` ثم `python3 tools/sync_queries.py`.
2. اختبار برمجي: `TestDefinition(...)` في قائمة المحرك المناسب (المعرّف فريد).
3. خاص بشركة: مكوّن أو مسار أو حزمة في ملف JSON داخل `assets/oem/` ثم نفس الأداة.

## الاختبارات الآلية (JVM)
`gradle :core:testDebugUnitTest` — تغطي: HKDF بمتجه RFC 5869، تناظر ECDH، رمز التحقق، القناة المشفّرة (كشف التلاعب وإعادة الإرسال)، تحليل الأخطاء، مطابقة ملفات OEM، انطباق الاختبارات.
هذه لا تختبر الواجهة ولا سلوك الإعدادات الفعلي على الأجهزة؛ ذلك بالتجربة اليدوية.
