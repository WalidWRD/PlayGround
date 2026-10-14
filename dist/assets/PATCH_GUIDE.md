# AppHook v2.0 — تشغيل بلا روت (LSPatch / NPatch / HKP) + صيانة

## 1) التثبيت بلا روت

### LSPatch
1. ثبّت `LSPatch` (manager) على الجهاز.
2. اختر APK التطبيق الهدف → `Patch` → أضف موديول `AppHook-v2.0.apk` كـ Embedded module.
3. حدد النطاق (Scope): حزمة التطبيق الهدف.
4. ثبّت الـ APK المدمج الناتج وشغّله.

### NPatch
1. افتح `NPatch` → اختر APK الهدف + موديول AppHook.
2. فعّل خيار تضمين الموديول (Embed) وحدد النطاق.
3. ثبّت الناتج. لا حاجة لروت.

### HKPatch (HKP)
1. نفس خطوات LSPatch: دمج + نطاق + تثبيت.
2. تأكد أن `minApi 101 / targetApi 102` مدعوم في نسخة HKP لديك (أي نسخة حديثة تدعمه).

### LSPosed (روت — اختياري)
1. ثبّت `AppHook-v2.0.apk` ثم فعّله في LSPosed Manager وحدد النطاق.
2. أعد تشغيل التطبيق الهدف.

## 2) التحقق من العمل
- افتح تطبيق AppHook (InfoActivity) للتأكد من البيانات.
- سجل LSPosed / Logcat (`tag=AppHook`):
  - `AppHook v2.0 | billing+features+...`
  - `[Billing] getPurchaseState args=0 result=...`
  - `[Feature] xxx unlocked: N times`
  - `[Safe] ... skipped` تعني حماية طبيعية (إصدار مختلف) وليست خطأ.

## 3) الصيانة والتطوير
- لإضافة ميزة: أنشئ `XxxHook.java` بنفس نمط `BillingHook` (findClassQuiet → hook PROTECTIVE → log)، ثم سطر واحد في `MainHook.installAll` + مرآته في `LegacyBridge.installAll`.
- لتضييق النطاق: عدّل `MainHook.inScope()` أو أضف `scope.list` (نطاق ثابت).
- عند ظهور `Hook FAIL / method-not-found` في السجل: راجع `docs/ANALYSIS.md §3` و`fingerprints.json` — البدائل جاهزة.
- النسخ: حدّث `versionName/Code` في `app/build.gradle` + `ModuleInfo` + `strings.xml` معاً.
