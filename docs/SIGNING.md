# التوقيع والإصدارات

## إنشاء مفتاح الإصدار (مرة واحدة فقط، واحتفظ به نسخة آمنة)
```
keytool -genkeypair -v -keystore release.jks -alias magicsmart \
  -keyalg RSA -keysize 4096 -validity 36500
base64 -w0 release.jks > release.jks.b64     # على macOS: base64 -i release.jks
```
## أسرار المستودع (Settings ← Secrets and variables ← Actions)
| السر | القيمة |
|---|---|
| KEYSTORE_BASE64 | محتوى release.jks.b64 |
| KEYSTORE_PASSWORD | كلمة مرور الـ keystore |
| KEY_ALIAS | magicsmart |
| KEY_PASSWORD | كلمة مرور المفتاح |

استخدم **المفتاح نفسه دائماً**؛ تغييره يمنع تحديث التطبيق المثبّت ويجبر على إلغاء تثبيته.
لا تضع الملف أو كلمات المرور داخل المستودع (`*.jks` مستثنى في `.gitignore`).

## سياسة الإصدار
- `versionName` = `MAJOR.MINOR.RUN` حيث MAJOR وMINOR من `version.properties` وRUN رقم تشغيل الـ Workflow.
- `versionCode` = رقم تشغيل الـ Workflow (يزيد دائماً).
- لا يُغيَّر `applicationId` بعد اعتماده: `com.magicsmart.developer` و`com.magicsmart.developertv`.
