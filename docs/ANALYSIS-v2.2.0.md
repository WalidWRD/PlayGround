# LOKTV Hook Pro — ملف التحليل والنتائج v2.2.0
**الغرض:** المرجع المعتمد للبناء والصيانة (يغني عن إعادة التحليل).
**التاريخ:** 2026-10-08 — **الإصدار:** 2.2.0 (220) — **الوسم:** 2026.10.08-r1

## 1) ما الجديد في v2.2.0 (مبني على classes3.dex + smali المرفق سابقاً)
- **F18 إخفاء واجهات شراء VIP:** الأصناف `HideBuyVip/HideVipCard` في التطبيق الهدف تُخفي view بالأرقام `0x7f0a0844/0x7f0a02c8`. الموديول يغطيها انعكاسياً (أي اسم مشوّش يحتوي buyvip/vipcard/vippay/vipdialog): دوال العرض void ← no-op، وبوابات should/show/is/has ← false. لا يلمس دوال `View` العامة.
- **F19 تقوية VipItem:** الصنف `VipItem#isVipItem` كان يُربط سابقاً بقيمة واحدة فقط. الآن: boolean ← true، int (level/coin) ← 9999، long (expiry) ← 4102444800000، مع fallback لأي صنف باسم vipitem.
- **F20 منع الارتداد إلى Play Store + الترخيص:** من ملاحظة المانيفست (حذف pairip/provider حتى لا يرتد التطبيق). الموديول يحيّدها برمجياً: `isLicensed/checkLicense/verifyLicense` ← true، وفاتحات المتجر (`openPlayStore/openMarket/rateApp/goMarket`) ← no-op، وسلاسل url/key الفارغة ← "". قطبية معاكسة لكشف التعديل (الترخيص true مقابل التعديل false) — موثقة هنا لمنع الخلط مستقبلاً.
- **منع التكرار HookRegistry:** إعادة التطبيق (حتى 3 مرات للمحمل المتأخر) كانت ستُكدّس نفس الهوك مرتين. سجل `HookRegistry` بمفتاح `class#method(params)->return` يمنع ذلك، و`Engine` أصبح عدّاداً (يسمح بإعادة فحص نفس المحمل للـ dex المتأخر) بدل مجموعة المحملات التي كانت تُبطل الـ retry.
- **كاش الأصناف:** `ClassScanner.classes()` تُخزّن نتائجها لكل `(loader,prefixes,limit)` — فحوصات F06–F20 العشرة تشترك في تمريرة `Class.forName` واحدة بدل 10.
- **إصلاح المانيفست:** كان هناك `">>` زائد بعد `versionName` (سطر 5). أُصلح — البناء كان ينجح بالصدفة عبر aapt2 المتساهل، لكنه XML غير صالح.

## 2) جدول القطبية (مهم للصيانة — لا تعكسه)
| النوع | القيمة | أمثلة |
|---|---|---|
| امتياز/ترخيص | TRUE | isVip, isLicensed, checkLicense, isVipItem, hasPremium |
| كشف/تحديث/إعلان | FALSE | isModded, isRoot, isVpn, hasUpdate, forceUpdate, showAd |
| عرض/فتح متجر | no-op (null) | showPopup, displayAd, openPlayStore, rateApp |
| سلاسل إعلان/ترخيص | "" | adUnitId, licenseKey, adUrl |

## 3) المعمارية (تقبل التطوير)
```
LokTvHook → Engine.apply (حتى 3 تمريرات، HookRegistry يمنع التكرار)
  → HookConfig.load (مفاتيح جديدة: hide_vip_ui, vip_item, license_bypass)
  → Features.userStatus F01-F05 → antiDetect F06 → antiVpn F07
  → floatingView F08 → rotation F09 → genericScanner F10
  → adsBlock F15 → trackerBlock F16 → updateDialogBlock F17
  → hideVipUi F18 (جديد) → vipItemUnlock F19 (جديد) → licenseBypass F20 (جديد)
  → crashGuard F12 → Log (pass/unique/classes)
```
- **لإضافة ميزة F21+:** دالة في `Features.java` + مفتاح في `HookConfig.java` + استدعاء في `Engine.java` + سطر في `ModuleInfo.FEATURES` + سطر في `loktv_module.json` + قسم هنا. لا تلمس `Reflect`/`ClassScanner`/`HookRegistry`.
- **قاعدة الـ dex الدائمة (من v2.1.1):** stubs الـ compile-only في `src-xposed` لا تدخل `classes.dex` أبداً — `build.sh` يفشل البناء لو تسرّب أي `de/robv`.

## 4) مواصفات البناء والتحقق
| البند | القيمة |
|---|---|
| الحزمة | com.loktv.hook |
| الإصدار | 2.2.0 / 220 / 2026.10.08-r1 |
| minSdk / targetSdk | 21 / 34 |
| xposedminversion | 82 |
| الأصناف | com.loktv.hook فقط (صفر de/robv في الـ dex — يُتحقق بـ dexdump) |
| التوقيع | v1+v2+v3 (apksigner verify) |
| الملف | dist/LOKTV-Hook-Pro-v2.2.0.apk |
| الدخول | com.loktv.hook.LokTvHook (assets/xposed_init) |

## 5) التركيب بدون روت
1. ثبّت APK الموديول. 2. في NPatch/LSPatch/HKP اختر تطبيق الهدف + الموديول وابدأ الترقيع.
3. احذف الأصلية وثبّت المرقعة (احذف pairip license/provider من التطبيق الهدف كما في شرح الفيديو).
4. شغّل وابحث في logcat عن `LOKTV-HOOK-PRO` — يجب رؤية البانر وسطر `apply() done | pass=.. | hooks=.. | unique=..`.
5. الإعدادات `loktv_hook.conf`: `vip,skip_update,valid_collection,video_data,disable_popup,anti_detect,anti_vpn,floating_view,generic_scanner,ads_block,tracker_block,update_dialog,hide_vip_ui,vip_item,license_bypass,rotation(-1/0/1),debug`.
