# MAGIC SMART – Developer Unlock Engine (المرحلة 1)

الوحدات: `core` (منطق مشترك) · `app-mobile` (com.magicsmart.developer) · `app-tv` (com.magicsmart.developertv)

## التوقيع (GitHub Secrets)
`KEYSTORE_BASE64` (base64 لملف jks) · `KEYSTORE_PASSWORD` · `KEY_ALIAS` · `KEY_PASSWORD`
بدونها يُبنى APK بتوقيع debug. استخدم نفس الـ keystore دائماً.

## البناء محلياً
لا يتضمن المستودع gradle-wrapper.jar؛ نفّذ `gradle wrapper --gradle-version 8.9` مرة واحدة أو اعتمد على GitHub Actions.
