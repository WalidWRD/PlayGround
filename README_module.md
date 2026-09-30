# GenSubs UpdateKiller — v2.0.1 (build 201)

> تصحيح الاشتراك بنجاح (App): يُفعَّل الاشتراك افتراضيًا عبر
> `subscriptionBridge=true + subscriptionForceActive=true` في
> `config/genspark_updatekiller.json`، فيمنح الجسر حالة ACTIVE فورًا
> (plan=premium لسنة) ويُسوِّف مفاتيح الاشتراك في HTTP وقنوات Flutter
> والفوترة والتفضيلات. الخادم (endpoint) يبقى اختياريًا ومصدر الحقيقة
> عند ضبطه.

موديول/هوك احترافي كامل لتعطيل **التحديث الإجباري** و**حوار التحديث الفوري/الاختياري** في تطبيق
**Genspark**، يعمل على **LSPatch / NPatch / HKP Patch / LSPosed** و**بدون روت**، ويتعامل مع
التطبيقات المضغوطة والمحمية والمشوّشة (R8/ProGuard).

- **التطبيق المستهدف:** Genspark — `ai.mainfunc.genspark.pro` (ويدعم `ai.mainfunc.genspark` وأي حزمة تضيفها في `packages`)
- **الإصدار المُحلَّل:** 2.9.8 (29800) · minSdk 28 · targetSdk 36
- **الموديول:** الحزمة `com.genspark.updatekiller` — الإصدار `2.0.0` (build 200)

## ما الجديد في 2.0.0

| # | الميزة | الملف |
|---|---|---|
| 1 | **فهرس أسماء الفئات من DEX الهدف** — استكشاف بنيوي بدل الأسماء الثابتة (مسح تدفّقي لـ`classes*.dex` من `sourceDir` + `splitSourceDirs`، ثم احتياط `DexFile.entries()`) | `Discovery.java` |
| 2 | **رصد تحميل الفئات (Lazy)** — `ClassLoader.loadClass` + `BaseDexClassLoader.findClass` → إعادة محاولة فورية عند وصول أي فئة مُهمّة (يصمد للمحمي/المشوّش) | `Discovery.java` |
| 3 | **تثبيت مُؤجَّل متعدّد المرور** — 7 مرورات (0/0.4/1.5/4/10/25/60s) + مهمة تُعلَن منجزة فقط عند نجاح ربط ≥1 خطّاف | `DeferredInstaller.java` |
| 4 | **اكتشاف PackageManager الديناميكي** — يربط **كل** تنفيذ في التطبيق + `getInstalledPackages`/`getApplicationInfo`/`PackageInfo.getLongVersionCode` ويُزوّر الإصدار للهدف فقط | `hooks/PackageManagerDiscoveryHook.java` |
| 5 | **اكتشاف Flutter + اعتراض القنوات بـProxy** — تغليف `setMethodCallHandler` بمُعترِض: تحييد `openStoreListing`، ورصد بقية نداءات التحديث، وتمرير كل ما عداها بلا كسر أي عقد | `hooks/FlutterDiscoveryHook.java` |
| 6 | **إعادة كتابة `package_info`** — تحويل `Result.success(Map)` إلى نسخة مُزوَّرة (version/buildNumber/versionCode) فتُبطل مقارنة الإصدار داخل Dart | `hooks/FlutterDiscoveryHook.java` |
| 7 | **مُبطِل البوابات العام** — يكتشف أصناف البوابة/الحوار بنيويًا ويُغلقها لحظة عرضها ويُعيد `false` لكل استفهام فرض | `hooks/UniversalGateHook.java` |
| 8 | **مسح الطبقة الأصلية (قراءة فقط)** — `/proc/self/maps` + حجم + SHA-256 + عدّ نصوص قرار التحديث داخل `libapp.so`/`libflutter.so` | `hooks/NativeProbeHook.java` |
| 9 | **تقرير ذاتي JSON** — `genspark_updatekiller_report.json`: البيئة + الفهرس + عدد خطّافات كل مكوّن + النتيجة (OK/PARTIAL/NO_HOOKS/KILL_SWITCH) | `SelfTest.java` |
| 10 | **حزم إضافية من الإعدادات** — `packages: []` لتغطية نسخ أخرى دون إعادة بناء | `Config.java` |

## الخطّافات (الطبقة الكاملة)

`VersionSpoof` · `PackageManagerDiscovery` · `FlutterDiscovery` · `UniversalGate` · `NativeProbe` ·
`ChannelHook` · `IntentHook` · `WebViewHook` · `HttpHook` · `PrefsHook` · `DialogHook` ·
`PlayCoreHook` · `NativeHook` · `AdsBlocker` · `BlutterTrace`

## البنية

```
app/src/main/java/com/genspark/updatekiller/
├── HookEntry.java                 # نقطة الدخول (lpparam.classLoader) + جدولة المهام
├── Discovery.java                 # فهرس أسماء الفئات من DEX + رصد التحميل
├── DeferredInstaller.java         # تثبيت مُؤجَّل متعدّد المرور + kick
├── EnvInfo.java                   # بيئة/هدف/إطار الدمج (انعكاسي)
├── SelfTest.java                  # تقرير ذاتي JSON
├── Reflect.java                   # انعكاس آمن (اسم/لمحات/أنواع/overloads)
├── Guard.java / Guardian.java     # مضاد الانهيار + Kill-Switch
├── Config.java                    # JSON + Hot-Reload
├── BuildInfo.java / UxLog.java / UpdateEventLogger.java / SubscriptionBridge.java
├── hooks/  (15 خطّافًا)
└── json/JsonNeutralizer.java
```

## البناء

- **Gradle:** `gradle :app:assembleRelease`
- **بدون Gradle (المسار المستخدم في التسليم):**
```bash
export BT=/path/to/build-tools/34.0.0
export ANDROID_JAR=/path/to/platforms/android-34/android.jar
export JAVAC=/path/to/jdk17/bin/javac
./scripts/build_apk.sh
```
> ملاحظة: `javac` يحتاج `-encoding UTF-8` (التعليقات العربية) والسكربت يتكفّل بذلك.

## الدمج بدون روت

```bash
# LSPatch
lspatch -m patch -o genspark_patched.apk genspark.apk GenSubs-UpdateKiller-v2.0.0.apk
# NPatch
java -jar NPatch.jar -m GenSubs-UpdateKiller-v2.0.0.apk genspark.apk -o genspark_patched.apk
# HKP Patch: افتح التطبيق → اختر الحزمة → اختر الموديول → Patch
adb install -r genspark_patched.apk && adb logcat -s GensparkUpdateKiller
```

## الإعداد (اختياري)

`/data/local/tmp/genspark_updatekiller.json` أو `/sdcard/Download/genspark_updatekiller.json`
(انظر `config/genspark_updatekiller.json`). `enabled=false` يوقف الموديول بالكامل.

## الصيانة والتطوير

- **خطّاف جديد:** أنشئ `hooks/XxxHook.java` فيه `public static int install(ClassLoader)` — وأضفه كمهمة في `HookEntry.registerTasks`.
- **بلا أسماء ثابتة:** `Discovery.classesByName / classesMatching / classesImplementing` ثم `Reflect.hookBySignature / hookWhere / hookByParamNames`.
- **لتوسيع النطاق:** `BuildInfo.PACKAGE_NAMES` أو مفتاح `packages` في JSON.
- **لتغيير الإصدار:** `BuildInfo` + `AndroidManifest.xml` + `res/values/strings.xml`.

## التحقق من التسليم

يُبنى بمسار `aapt2 + javac + d8 + zipalign + apksigner`، ثم يُتحقّق بـ`aapt2 dump badging`
و`apksigner verify`، ويُستخرج `classes.dex` ويُقرأ بـ`dexdump` للتأكد من وجود كل الأصناف —
والملف النهائي هو: `GenSubs-UpdateKiller-v2.0.0.apk`.

⚠️ تحذير: استخدم ذلك على تطبيق تملكه أو لديك إذن بتعديله فقط. توزيع نسخ معدّلة من تطبيقات الغير يخالف سياسات Google Play وقانون حقوق النشر.
