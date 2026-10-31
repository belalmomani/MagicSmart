#!/usr/bin/env python3
"""يولّد SettingsActions.kt و AndroidManifest للوحدة core (<queries>) من:
   tools/settings_actions.txt و core/src/main/assets/oem/*.json
   شغّله بعد إضافة مسار أو ملف OEM جديد (يعمل تلقائياً أيضاً في GitHub Actions)."""
import glob, json, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CORE = os.path.join(ROOT, "core", "src", "main")

actions = []
for line in open(os.path.join(ROOT, "tools", "settings_actions.txt"), encoding="utf-8"):
    line = line.strip()
    if line and not line.startswith("#"):
        actions.append(line.split("|"))

packages = {"com.android.settings", "com.android.tv.settings", "moe.shizuku.privileged.api"}
intent_actions = {"android.settings.APPLICATION_DEVELOPMENT_SETTINGS"} | {a[1] for a in actions}
for f in sorted(glob.glob(os.path.join(CORE, "assets", "oem", "*.json"))):
    d = json.load(open(f, encoding="utf-8"))
    packages.update(d.get("packages", []))
    for c in d.get("components", []):
        packages.add(c["package"])
    for a in d.get("actions", []):
        intent_actions.add(a["action"])

kt = ["package com.magicsmart.core.engine", "",
      "// ملف مُولَّد بواسطة tools/sync_queries.py — لا تعدّله يدوياً.",
      "internal class SettingsAction(val id: String, val action: String, val title: String)", "",
      "internal val SETTINGS_ACTIONS = listOf("]
kt += ['    SettingsAction("%s", "%s", "%s"),' % tuple(a) for a in actions]
kt.append(")")
open(os.path.join(CORE, "kotlin/com/magicsmart/core/engine/SettingsActions.kt"), "w", encoding="utf-8").write("\n".join(kt) + "\n")

mf = ['<manifest xmlns:android="http://schemas.android.com/apk/res/android">',
      '    <!-- ملف مُولَّد بواسطة tools/sync_queries.py — لا تعدّله يدوياً. -->',
      '    <!-- قراءة حالة الشبكة لتحديد اتصال Wi-Fi (صلاحية عادية، لا بيانات شخصية) -->',
      '    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />',
      '    <!-- اكتشاف خدمات ADB عبر mDNS واختبار منفذ محلي؛ لا يُرسل التطبيق أي بيانات خارج الجهاز -->',
      '    <uses-permission android:name="android.permission.INTERNET" />',
      '    <!-- Package Visibility (Android 11+): الحزم والمسارات التي يفحصها المحرك فقط -->',
      '    <queries>']
mf += ['        <package android:name="%s" />' % p for p in sorted(packages)]
mf += ['        <intent><action android:name="%s" /></intent>' % a for a in sorted(intent_actions)]
mf += ['    </queries>', '</manifest>']
open(os.path.join(CORE, "AndroidManifest.xml"), "w", encoding="utf-8").write("\n".join(mf) + "\n")
print("actions:", len(actions), "packages:", len(packages), "intents:", len(intent_actions))
