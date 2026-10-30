# MAGIC SMART – Developer Unlock Engine (المرحلة 1)

الوحدات: `core` (منطق مشترك) · `app-mobile` (com.magicsmart.developer) · `app-tv` (com.magicsmart.developertv)

## التوقيع (GitHub Secrets)
`KEYSTORE_BASE64` (base64 لملف jks) · `KEYSTORE_PASSWORD` · `KEY_ALIAS` · `KEY_PASSWORD`
بدونها يُبنى APK بتوقيع debug. استخدم نفس الـ keystore دائماً.

## البناء محلياً
لا يتضمن المستودع gradle-wrapper.jar؛ نفّذ `gradle wrapper --gradle-version 8.9` مرة واحدة أو اعتمد على GitHub Actions.

## المرحلة 2
`core/engine`: TestEngine (تشغيل/إيقاف/استكمال/إعادة الفاشل) · Developer Options Discovery · Settings Scanner · Android TV Analyzer.
إضافة اختبار جديد = إضافة `TestDefinition` إلى قائمة المحرك المناسب. مسارات الإعدادات تُولَّد من قائمة واحدة (`SettingsActions.kt`) مطابقة لـ `<queries>` في manifest الوحدة `core`.

## المرحلة 3
USB Debugging (4) · Wireless Debugging (6) · ADB Analyzer (4) · Advanced Permissions (6، منها PERM-002 بموافقة صريحة فقط).
الصلاحيات المضافة في manifest الوحدة core: ACCESS_NETWORK_STATE و INTERNET (اكتشاف mDNS واختبار منفذ محلي فقط، لا رفع بيانات).
عند العودة من صفحة إعدادات فتحها المستخدم تُعاد المحركات المرتبطة تلقائياً للتحقق من النتيجة.
