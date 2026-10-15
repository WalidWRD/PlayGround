# AppHook v3.1 — تشغيل بلا روت (LSPatch / NPatch / HKP) + صيانة

## 0) بطاقة النسخة

| البند | القيمة |
|---|---|
| الاسم | AppHook |
| الحزمة | `com.apphook.log` |
| الإصدار | **3.1.0** (code 310) |
| أندرويد | 8.0 (API 26) → 15 (API 35) |
| libxposed | minApi 101 / targetApi 102 (+ shim قديم 82) |
| النطاق | `ai.mainfunc.genspark` + `ai.mainfunc.genspark.test` |
| الملف المثبت | `AppHook-v3.1.apk` (موقّع) |
| ملف الدمج | `AppHook-v3.1-release-unsigned.apk` (الباتشر يعيد التوقيع) |

## 1) التثبيت بلا روت

### LSPatch
1. ثبّت `LSPatch` (manager) على الجهاز.
2. اختر APK التطبيق الهدف → `Patch` → أضف موديول `AppHook-v3.1.apk` كـ Embedded module.
3. حدد النطاق (Scope): `ai.mainfunc.genspark` (أو `.test`).
4. ثبّت الـ APK المدمج الناتج وشغّله.

### NPatch
1. افتح `NPatch` → اختر APK الهدف + موديول `AppHook-v3.1.apk`.
2. فعّل خيار تضمين الموديول (Embed) وحدد النطاق.
3. ثبّت الناتج. لا حاجة لروت.

### HKPatch (HKP)
1. نفس خطوات LSPatch: دمج + نطاق + تثبيت.
2. تأكد أن `minApi 101 / targetApi 102` مدعوم في نسخة HKP لديك.

### LSPosed (روت — اختياري)
1. ثبّت `AppHook-v3.1.apk` ثم فعّله في LSPosed Manager وحدد النطاق.
2. أعد تشغيل التطبيق الهدف.

## 2) التحقق من العمل
- افتح تطبيق AppHook (InfoActivity) للتأكد من `v3.1.0 (code 310)`.
- سجل LSPosed / Logcat (`tag=AppHook`):
  - `AppHook v3.1.0 | Flutter-filtered: ...`
  - `All v3.1 layers installed for: ai.mainfunc.genspark...`
  - `[Flutter] * Native: <اسم>` للمهم فقط (الضجيج مُسكت)
  - `[Feature] ... unlocked: N`
  - `[WebView] REQ:` / `[AppsFlyer] event:` عند شاشة الاشتراك
  - `[Safe] ... skipped` = حماية طبيعية وليست خطأ.

## 3) خطة التشخيص (حاسمة لـ v3.2)
1. افتح شاشة الاشتراك (Premium/Upgrade) في Genspark.
2. حاول ميزة مدفوعة. 3. انتظر 10 ثوانٍ. 4. أرسل السجل.
3. المطلوب: `★ Native: <اسم>` + `args = ...` + `result class = ...` +
   `flutter.<key>` + `[WebView] REQ:` / `[AppsFlyer] event:`.

## 4) الصيانة والتطوير
- لإضافة ميزة: أنشئ `XxxHook.java` بنمط `findClassQuiet → fw.hook PROTECTIVE → log`،
  ثم سطر في `MainHook.installAll` + مرآته في `LegacyBridge.installAll`.
- القاعدة الذهبية: **لا تستبدل وسائط عبر Proxy أبداً** (سبب كراش v3.0) —
  اربط على الفئات الحقيقية + نفس النوع دائماً + dedup.
- عند `method-not-found`: راجع `ANALYSIS.md §3/§11` و`fingerprints.json`.
- النسخ: حدّث `versionName/Code` في `app/build.gradle` + `ModuleInfo` + `strings.xml` معاً.
