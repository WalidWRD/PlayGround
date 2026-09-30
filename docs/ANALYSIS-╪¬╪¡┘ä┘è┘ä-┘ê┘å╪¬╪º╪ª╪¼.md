# Genspark UpdateKiller — ملف التحليل والنتائج
**الإصدار 1.1.0 (build 110) · تاريخ الإعداد 2026-09-25**

> ملف مرجعي يُغني عن إعادة التحليل: كل ما استُخرج من الملفات المرفقة + القرارات التصميمية وأدلّتها.

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

## 6) الخلاصة التنفيذية
| الطريق | الطبيعة | الدوام | المخاطر |
|---|---|---|---|
| تعديل الخادم (`forceUpgrade=false`) | إعداد | دائم | يحتاج وصولًا للسيرفر |
| تعديل `libapp.so` | نصّي | دائم | لا يُزيل الحوار + خطر كراش |
| تعديل مصدر Flutter | حذف Widget | دائم | يحتاج المشروع الكامل + إعادة توقيع |
| **موديول Xposed (هذا)** | اعتراض وقت التشغيل | دائم طالما مُدمج | منخفض جدًا (Guard + Kill-Switch) |

## 7) الملاحظات المؤكَّدة والمتبقّية
- **مؤكَّد:** غياب أي منطق تحديث في DEX/native، الإزاحات أعلاه، غياب SSL Pinning، غياب `play.core.appupdate.*`.
- **غير مؤكَّد (يحتاج جهازًا):** نجاح اعتراض `getPackageInfo` على نسخة `ai.mainfunc.genspark.pro` فعليًا، وتوقيت أول قراءة إصدار قبل بناء الحوار.

⚠️ تحذير: استخدم ذلك على تطبيق تملكه أو لديك إذن بتعديله فقط. توزيع نسخ معدّلة من تطبيقات الغير يخالف سياسات Google Play وقانون حقوق النشر.


## 12) إضافة 1.4.0 (build 140)
**المُحرّكات الثلاثة جاءت من الأدلة المرفوعة:**

| الميزة | الدليل |
|---|---|
| شعار الموديول | logo.png المرفوع (400×400 PNG palette) — حُوّل إلى 5 أحجام Android + Adaptive |
| مانع الإعلانات | DEX الهدف يحوي `com.google.android.gms.ads` (3 مراجع) + `INTERSTITIAL`+`BANNER`+`admob_app_id`+`AdvertisingIdClient`+`beginAdUnitExposure` |
| تحسينات من Blutter | ملحق Blutter يُؤكّد إزاحات نصوص قرار التحديث في Object Pool — أُضيف `BlutterTrace` ليُسجّل كل ظهور فعلي في ردود HTTP |

**عدد الخطّافات بعد v1.4.0:** 11 خطّافًا عبر 11 ملفًا (`VersionSpoofHook`+`ChannelHook`+`IntentHook`+`WebViewHook`+`HttpHook`+`PrefsHook`+`DialogHook`+`PlayCoreHook`+`NativeHook`+`AdsBlocker`+`BlutterTrace`) — كل خطّاف مُغلَّف بـGuard ومستوفٍ Kill-Switch.
