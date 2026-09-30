# Genspark UpdateKiller — تقرير التحليل والنتائج
**الإصدار 1.2.0 (build 120) · تاريخ الإعداد 2026-09-25**

> ملف مرجعي يُغني عن إعادة التحليل: كل ما استُخرج من ملفات الهدف + تدقيق الإصدار 1.1.0 + إضافات وتعديلات 1.2.0 وأدلّتها.

## 1) الملفات المُحلَّلة
| الملف | الحجم | ما قدّمه |
|---|---|---|
| `Genspark Classes.zip` | 4.85 MB | `classes.dex` (9.9 MB) + `classes2.dex` (1 KB) + `classes3.dex` (437 KB) |
| `AndroidManifest.txt` | 43,501 B | الحزمة، الأنشطة، البيانات الوصفية، إعداد الشبكة |
| `arm64-v8a.zip` | 53 MB | `libapp.so` (46 MB) + 31 مكتبة أصلية |

الأدوات: `androguard 4.1.4` (DEX) · `strings`/grep (`libapp.so`) · `aapt2`/`apksigner`.

## 2) بيانات التطبيق الهدف
| الحقل | القيمة |
|---|---|
| الحزمة | `ai.mainfunc.genspark.pro` (ويدعم `ai.mainfunc.genspark`) |
| النشاط الرئيسي | `ai.mainfunc.genspark.MainActivity` |
| versionCode / versionName | `29800` / `2.9.8` |
| minSdk / targetSdk / compileSdk | `28` / `36` / `37` |
| `usesCleartextTraffic` | `true` |
| `networkSecurityConfig` | `@7f140009` |
| Play Core | `PlayCoreDialogWrapperActivity` (مراجعة فقط) — لا `play.core.appupdate.*` |
| SSL Pinning | **غير مُفعَّل** (`setTrustedCertificates=0`, `sslPinning=0`, `pinSha256=0`, لا بصمات base64‑44) |

## 3) موضع منطق التحديث (الحكم الجوهري)
لا يوجد منطق تحديث إجباري/فوري في الطبقة الأصلية أو في DEX — **كل المنطق في طبقة Dart داخل `libapp.so`** (AOT snapshot).

| السلسلة | الإزاحة | الحجم |
|---|---|---|
| `A new version of Genspark is available. Update now to get the latest features and fixes.` | 3,807,110 | 88 B |
| `New version available` | 1,687,018 | 21 B |
| `mine.appUpdate.dialogTitle` | 5,033,623 | 26 B |
| `mine.appUpdate.dialogMessage` | 3,739,638 | 28 B |
| `mine.appUpdate.updateNow` | 4,579,542 | 24 B |
| `mine.appUpdate.later` | 4,782,441 | 20 B |
| `forceUpgrade` | 2,301,211 | 13 B |
| `minAppVersionCode` | 2,826,671 | 17 B |
| `SasUpgradeGateSheet` | 859,382 | 19 B |
| `/api/config/new_feature/dialog` | 3,917,020 | 29 B |

**نقطة النهاية الحاسمة:** `GET /api/config/new_feature/dialog` وتُعيد: `forceUpgrade` (bool)،
`forceUpgradeD`، `directUpgrade`، `minAppVersionCode` (int)، `minAppRequireAppVersion` (string)، `requiresAppVersion` (string).

**منطق القرار المُستنتج:**
```
localVersionCode = package_info.versionCode          # 29800
if (localVersionCode < cfg.minAppVersionCode):
        if cfg.forceUpgrade: show SasUpgradeGateSheet   # إجباري بلا «لاحقًا»
        else:                show UpgradePromptWidget   # اختياري فيه «لاحقًا»
```
وجود `mine.appUpdate.later` يثبت أن الحوار قابل للتأجيل إلا عند `forceUpgrade=true`.

**فتح المتجر:** قناة Flutter `dev.britannio.in_app_review` — `LR8/b;->onMethodCall` تحمل `openStoreListing`
وتبني `https://play.google.com/store/apps/details?id=`، والاحتياطي `Lz5/f;->a(...)` ينشئ Intent `market://details`;
قراءة الإصدار في `LW8/a` و`LV8/b` (version / installerStore / updateTime).

## 4) لماذا فشل تعديل `libapp.so` وحده
عُدّل النصّ فعليًا عند الإزاحة 3,807,110 (طول ثابت 88 بايت، حشو مسافات، 40 بايت متغيّرة، الملف بقي ELF سليم 47,383,440 بايت) — **لكن الحوار لم يُحذف** لأن:
1. النصّ بياناتٌ يُعرضها كود مُترجَم، والشرط المُظهر للحوار في تعليمات ARM نفسها.
2. تغيير طول أي سلسلة يُزحزح الإزاحات ويُفسد الـsnapshot → كراش.
3. لا يمكن «حذف» Widget بتعديل ثنائي آمن.
**الخلاصة:** نستهدف **نقاط القرار** لا النصوص: تزوير الإصدار / إلغاء حقول الفرض / منع فتح المتجر.

## 5) لماذا موديول Xposed هو الحل الصحيح
تزوير `PackageManager.getPackageInfo().versionCode` في طبقة Java يُغيّر **ما يراه Dart** عبر قناة `package_info`
(تقرأ الحزمة من `PackageManager` عبر JNI)، فيصبح `localVersionCode < minAppVersionCode` غير محقّق دائمًا
مهما كان الإصدار. تجاوز محصَّن ضد تغيّر إصدارات التطبيق.

## 6) تدقيق الإصدار 1.1.0 (نتائج الفحص الفعلي، ليس افتراضًا)
- **الـAPK المُسلَّم سليم:** `xposed_init` → `com.genspark.updatekiller.HookEntry`؛ توقيع v1+v2+v3 صالح (شهادة `CN=Genspark UpdateKiller, O=UpdateKiller Project`، SHA‑256 `a7aad85a…58dac`)؛ فئات الخطّافات الثمانية + `Guard`/`Guardian`/`Config`/`Reflect` موجودة في `classes.dex`.
- **تغطية آليات التحديث الثلاث في الهدف:** تزوير الإصدار (يُبطل الشرط) ✓ · حجب Intent المتجر (الاحتياطيان `Lz5/f` و`openStoreListing`) ✓ · Play Core ✓. حجب ردود JSON عبر okhttp/okio ⚠️ احتياطي بحكم التصميم (Flutter يستخدم `dart:io` الذي لا يمرّ من Java) — غير مؤثّر لأن تزوير الإصدار هو الآلية الحاسمة.
- **الفجوات المكتشفة (صُلحت في 1.2.0):**
  1. إمكانية فتح صفحة التحديث داخل WebView داخلي دون حجب.
  2. تعديل الإعدادات يتطلب إعادة تشغيل التطبيق.
  3. توقيت أول قراءة إصدار كان غير مُؤكَّد في الملف المرجعي — قُوّي الخطّاف باحتياطي `PackageManager` المجرد.
  4. سكربت `build_apk.sh` كان يفشل بصمت عند الحزم (`zip error: Nothing to do`) لأنه لا ينسخ `classes.dex` إلى مجلد الحزم قبل الضغط.

## 7) الجديد في 1.2.0 (build 120)
| # | الميزة | الملف | الأثر |
|---|---|---|---|
| 1 | **`WebViewHook`** — حجب/تحويل روابط المتجر داخل WebView | `hooks/WebViewHook.java` | طريقتان آمنتان: تعديل وسيط `loadUrl(String)` في مكانه + `beforeHookedMethod` على `shouldOverrideUrlLoading` (لا `setResult` على void، لا استثناء) |
| 2 | **تزوير إصدار مُقوّى** — احتياطي `PackageManager` المجرد + كتابة الحقول انعكاسيًا | `hooks/VersionSpoofHook.java` | يغطي البيئات الافتراضية التي تُعيد صنف `PackageManager` مختلفًا؛ الكتابة الانعكاسية تضمن انسداد كلا مساري القراءة (API القديم والجديد) |
| 3 | **Hot-Reload للإعدادات** — فحص `lastModified` كل 3 ثوانٍ وإعادة تحميل تلقائي | `Config.java` | تغيير `genspark_updatekiller.json` يُلتقط دون إعادة تشغيل التطبيق |
| 4 | **`storeRedirectUrl`** — رابط تحويل أزرار المتجر قابل للتخصيص | `Config.java` + `IntentHook` + `WebViewHook` | القيمة الافتراضية `https://www.genspark.ai/` |
| 5 | **`killSwitchThreshold`** — عتبة Kill-Switch من الإعدادات | `Config.java` + `Guard.java` | بدل الثابت 20؛ يعمل مع `Guard.setThreshold` |
| 6 | **إصلاح سكربت البناء** — نسخ `classes.dex` قبل الضغط | `scripts/build_apk.sh` | البناء يعيد `OK` كاملًا من مسار aapt2+javac+d8+apksigner |
| 7 | **تحديث الوصف المدمج** — بنود الميزات 7–9 | `res/values/strings.xml` | يظهر في مدير الموديولات ومعلومات التطبيق وسجل التشغيل |

## 8) الخلاصة التنفيذية
| الطريق | الطبيعة | الدوام | المخاطر |
|---|---|---|---|
| تعديل الخادم (`forceUpgrade=false`) | إعداد | دائم | يحتاج وصولًا للسيرفر |
| تعديل `libapp.so` | نصّي | دائم | لا يُزيل الحوار + خطر كراش |
| تعديل مصدر Flutter | حذف Widget | دائم | يحتاج المشروع الكامل + إعادة توقيع |
| **موديول Xposed (هذا، v1.2.0)** | اعتراض وقت التشغيل | دائم طالما مُدمج | منخفض جدًا (Guard + Kill-Switch قابل للضبط) |

## 9) التحقق من إصدار 1.2.0 (نتائج فعلية)
- `aapt2 dump badging`: `versionCode='120' versionName='1.2.0'`، `sdkVersion:'21'`.
- `resources.arsc` يحتوي الوصف المحدَّث (بنود WebView / Hot-Reload / storeRedirectUrl: 3 تطابقات).
- `apksigner verify` → سليم؛ البنية: `AndroidManifest.xml` + `resources.arsc` + `classes.dex` + `assets/xposed_init` + توقيع `META-INF/UK.*`.
- الوصف المدمج (`BuildInfo.describe()`): الإصدار 1.2.0 (120) والبيئة والميزات التسعة.

## 10) الملاحظات المؤكَّدة والمتبقّية
- **مؤكَّد:** كل ما سبق من نتائج التحليل الساكن والتدقيق والبناء والتوقيع.
- **غير مؤكَّد (يحتاج جهازًا):** نجاح اعتراض `getPackageInfo` على نسخة `ai.mainfunc.genspark.pro` المثبّتة فعليًا، وتوقيت أول قراءة إصدار قبل بناء الحوار — يُتحقق منه بسطر السجل `ALL DONE — hooks=N failures=0 killSwitch=false` عند أول تشغيل.

⚠️ تحذير: استخدم ذلك على تطبيق تملكه أو لديك إذن بتعديله فقط. توزيع نسخ معدّلة من تطبيقات الغير يخالف سياسات Google Play وقانون حقوق النشر.


## 11) إضافة 1.3.0 (build 130) — تسجيل أحداث التحديث
مكوّن جديد `UpdateEventLogger.java`: عند أي دليل على ظهور حوار/بوابة تحديث يُكتب سطر في ملف log بالوقت المحلي (ميلي ثانية). المصادر المتابعة: نداءات قنوات التحديث (`ChannelHook`)، الحوارات الأصلية الظاهرة (`DialogHook`)، نية المتجر المحوَّلة (`IntentHook`)، ردّ إعداد التحديث المُعطَّل (`HttpHook`)، وبدء الجلسة (`HookEntry`). المسارات بالترتيب: `/data/local/tmp/` ثم مجلدات التطبيق (خارجي خاص ثم داخلي عبر Context انعكاسي من `ActivityThread.currentApplication()`) ثم `/sdcard/Download/`؛ يُعاد الكشف تلقائيًا عند تعذّر الكتابة، مع تقليص تلقائي عند 1MB. مفتاح الإعداد: `eventLog` (افتراضي true). جرى رفعه إلى: Manifest 130/1.3.0، `BuildInfo`، الوصف المدمج، README، والإعدادات.
