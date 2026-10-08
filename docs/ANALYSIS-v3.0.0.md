# LOKTV Hook Pro — ملف التحليل والنتائج v3.0.0
**الغرض:** المرجع المعتمد للبناء والصيانة (يغني عن إعادة التحليل).
**التاريخ:** 2026-10-08 — **الإصدار:** 3.0.0 (300) — **الوسم:** 2026.10.08-r2
**المصدر المحلَّل:** `LokTvHookPro_v1.0.0_txt.zip` (تفكيك v1.0.0 الأصلية)

## 1) جرد محتويات v1.0.0 (من الـ zip نفسه)
- `AndroidManifest.txt` — مانيفست التطبيق الهدف.
- `com/novan/morpha/` (13 صنفاً للهدف): `AntiVPN`، `FloatingView` + `$1`، `GetRotation`، `HideBuyVip` + `$1`، `HideVipCard` + `$1`، `LoadConfig` + `$1..$5`، `ReplaceVipString` + `$1`/`$2`، `UserLoader`، `UserStatus` + `$1`، `VipItem`.
- `top/canyie/pine/` (مكتبة Pine الأصلية): `Pine`، `Pine$CallFrame`، `Pine$HookRecord/Handler/Listener/Mode/LibLoader/$1`، `PineConfig` + `$1`، `R`، `Ruler` + `$I`، `callback/MethodHook(+$Unhook)`، `callback/MethodReplacement(+$1/$2)`، `entry/Arm32/Arm64/Arm64Marshmallow/X86(+ParamTypesCache/$1)`، `utils/Primitives/ReflectionHelper/ThreeTuple`.
- **الخلاصة المعمارية:** التطبيق الهدف يشحن محرك Pine الأصلي ويستدعيه مباشرة (`Pine.hook` + `MethodHook`/`MethodReplacement`) — وهذا سبب الهشاشة: أي تحديث/تشويش/ضغط أو تغيير حزمة يكسر الربط المباشر.

## 2) لماذا أُعيد البناء من الصفر (v1 → v3)
| مشكلة v1 | الحل في v3 |
|---|---|
| أسماء أصناف ثابتة + استدعاء Pine مباشرة | `lpparam.classLoader` + انعكاس + فاحص dex شامل |
| مكتبة Pine قديمة (غير متوافقة مع Android 10+) | واجهة Xposed القياسية v82 (يوفرها LSPatch/NPatch/HKP) |
| أي استثناء يُسقط التطبيق | خطوات معزولة + فحص مصدر الاستثناء + سجل HookRegistry ضد التكرار |
| المحمل الأول وهمي في المضغوط | خطف attachBaseContext/onCreate + حتى 3 تمريرات |
| اسم الحزمة شرط وحيد (ينكسر مع النسخ المعدلة) | **كشف مزدوج v3: alias الحزمة + بصمة dex** (أي صنف من SIGNATURE_CLASSES الأربعة = هدف) |
| فحوص الترخيص/المتجر ترتد بـ Play Store | F20: `isLicensed` ← true + فاتحات المتجر ← no-op |

## 3) خريطة التغطية (كل صنف من الـ zip ← ميزة)
| صنف v1 | ميزة v3 |
|---|---|
| UserStatus (isVip/isSkipUpdate/isValidCollection/isVideoDataEnabled/DisableSomePopup) | F01–F05 |
| VipItem#isVipItem | F19 (+F10) |
| AntiVPN (مقارنة NetworkCapabilities) | F07 |
| FloatingView.isDisable + $1 | F08 |
| GetRotation.afterCall | F09 |
| UserLoader.onCreate/DisableFloating (provider initOrder=1000) | F11 (خطف المحمل الحقيقي) |
| HideBuyVip (id `0x7f0a0844`) / HideVipCard (`0x7f0a02c8`) | F18 |
| ReplaceVipString init/init2 (نصوص `0x7f130f01/0x7f131010`) | مكمّل داخل التطبيق — لا نعيد تنفيذه |
| LoadConfig (7 دوال decode + تشويش) | لا نعيد تنفيذها عمداً (طبقة تشويش غير مطلوبة) |
| UserStatus$1.isModded (Toast "MOD BY YOUR NAME") | F06 |
| فحوص الترخيص/pairip + الارتداد للمتجر | F20 |
| إعلانات/تحليلات/حوارات تحديث | F15/F16/F17 |

## 4) جدول القطبية (لا تعكسه أبداً)
| النوع | القيمة | أمثلة |
|---|---|---|
| امتياز/ترخيص | TRUE | isVip, isLicensed, checkLicense, isVipItem, hasPremium |
| كشف/تعديل/تحديث/إعلان | FALSE | isModded, isRoot, isVpn, hasUpdate, forceUpdate, showAd |
| عرض/فتح متجر | no-op | showPopup, displayAd, openPlayStore, rateApp |
| سلاسل إعلان/ترخيص | "" | adUnitId, licenseKey, adUrl |

## 5) المعمارية (تقبل التطوير والصيانة)
```
LokTvHook (xposed_init)
  → isTarget(pkg) [alias] أو isTargetBySignature(cl) [بصمة dex — أي حزمة]
  → Engine.apply ×3 تمريرات (HookRegistry يمنع التكرار)
      → HookConfig.load (17 مفتاحاً ساخناً)
      → F01-F05 → F06 → F07 → F08 → F09 → F10
      → F15 → F16 → F17 → F18 → F19 → F20 → F12 → Log
```
- **لإضافة F21+:** دالة `Features` + مفتاح `HookConfig` + استدعاء `Engine` + سطر `ModuleInfo.FEATURES` + سطر `loktv_module.json` + قسم هنا.
- **قواعد دائمة:** (1) stubs الـ compile-only لا تدخل الـ dex أبداً. (2) لا أسماء ثابتة جديدة — كلمات مفتاحية فقط. (3) `ModuleInfo` ↔ `AndroidManifest` ↔ `loktv_module.json` ↔ هذا الملف متزامنة كل إصدار.

## 6) بطاقة الإصدار
| البند | القيمة |
|---|---|
| الموديول | LOKTV Hook Pro 3.0.0 (300 / 2026.10.08-r2) |
| حزمة الموديول | com.loktv.hook — الدخول `com.loktv.hook.LokTvHook` |
| أندرويد الموديول | حد أدنى 5.0 (API 21) — مستهدف 14 (API 34) |
| الهدف | أي حزمة تحوي بصمة morpha (الأصلية com.novan.morpha) — أي إصدار أعلى/أدنى |
| المحملات | LSPatch / NPatch / HKP-Patch / LSPosed / EdXposed — بدون روت |
| التوقيع | v1+v2+v3 (apksigner verify) |
| الملف | dist/LOKTV-Hook-Pro-v3.0.0.apk |

## 7) التركيب بدون روت
1. ثبّت APK الموديول. 2. في NPatch/LSPatch/HKP اختر التطبيق الهدف + الموديول ورقّع.
3. (للهدف الأصلي: احذف pairip license/provider كما في شرح الفيديو — وF20 يحيّد الباقي برمجياً).
4. ثبّت المرقعة، شغّل، وابحث في logcat عن `LOKTV-HOOK-PRO` — البانر + `apply() done | pass=.. | hooks=.. | unique=..`.
5. الإعدادات `loktv_hook.conf`: `vip,skip_update,valid_collection,video_data,disable_popup,anti_detect,anti_vpn,floating_view,generic_scanner,ads_block,tracker_block,update_dialog,hide_vip_ui,vip_item,license_bypass,rotation(-1/0/1),debug`.
