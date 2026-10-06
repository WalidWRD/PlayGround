# الصيانة والتطوير — KakuAudit Observe

## إضافة مراقب جديد (5 دقائق)
1. أنشئ كلاسًا يطبق `observers/KakuObserver` (انسخ `StoreObserver` كنموذج).
2. ضع المرشحين والأنماط في `assets/module-blueprint.json` (لا تصلب أسماء في الكود).
3. سجّله في `ObserverRegistry.all()`.
4. غلّف أي كود قد يرمي بـ `SafeGuard.runSafe("observer:<Name>", prot, ...)` — إلزامي.
5. حدّث `ModuleDescription.FEATURES` + `ANALYSIS_AND_RESULTS.md` §2 + ارفع الإصدار.

## قواعد السلامة (تمنع الكراشات — لا تستثنِ منها شيئًا)
- لا ترمِ استثناءً خارج أي هوك/مراقب/خيط — `SafeGuard` يبتلع ويعزل ويعدّ.
- لا تحجب خيط التطبيق: نشر الأحداث غير حاجب (`EventBus.offer`)، والكتابة للقرص في خيط `KakuAudit-Flush` فقط.
- لا تعدّل `param.args`/`result` أبدًا (OBSERVE_ONLY) — القراءة الانعكاسية فقط.
- أي API جديد (فوق minSdk 24) يُستدعى انعكاسيًا عبر `VersionCompat` مع fallback.
- حدّ الهوكات 25/كلاس؛ تجاوز العتبة = kill-switch تلقائي (20 فشلًا) بدل كسر التطبيق.

## تشخيص كراش في التطبيق الهدف (v3.1.1)
1. `NativeNetObserver` معطّل افتراضيًا — إن ثبتت النسخة الجديدة وما زال الكراش، الكراش غالبًا من فحص السلامة الذاتي للتطبيق المرقّع (signature/integrity check) لا من الموديول.
2. للتحقق: عطّل كل المراقبين من `assets/kakuaudit-config.json` (كلها `false`) وأعد الترقيع — إن استمر الكراش فالسبب اللودر/التوقيع لا الهوكات.
3. فعّل المراقبين واحدًا واحدًا: `StoreObserver` ← `LifecycleObserver` ← `NativeNetObserver`.
4. اطلب من المُبلّغ: اسم/إصدار التطبيق، إصدار Android، أداة الباتش وإصدارها، توقيت الكراش (فور الإقلاع؟ بعد ~15 ثانية؟)، و`logcat` + سجل LSPosed.

## الإصدارات
- `versionCode/versionName` في `app/build.gradle` + `ModuleDescription.VERSION` + `module-blueprint.json:moduleVersion` — الثلاثة معًا دائمًا.
- سجل التغيير في `README_AR.md`.

## الفحوص قبل كل إصدار
```bash
gradle :app:testDebugUnitTest   # يجب أن تنجح كل الاختبارات
gradle :app:assembleDebug       # ينتج APK
```
ثم انسخ APK إلى `KakuAudit-Observe-v<VERSION>.apk` بجانب المشروع.
