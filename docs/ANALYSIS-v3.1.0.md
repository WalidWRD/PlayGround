# LOKTV Hook Pro — ملف التحليل والنتائج v3.1.0
**الغرض:** المرجع المعتمد للبناء والصيانة (يغني عن إعادة التحليل).
**التاريخ:** 2026-10-08 — **الإصدار:** 3.1.0 (310) — **الوسم:** 2026.10.08-r3

## 1) تقرير ميداني + السبب الجذري (تطبيق حقيقي، بدون كراش)
- **العرض:** الموديول لا يُسقط التطبيق (درع الكراشات يعمل)، لكن VIP والإعلانات لم تُفعَّل.
- **السبب الجذري (من smali v1):** دوال `isVip/isSkipUpdate/isValidCollection/isVideoDataEnabled/DisableSomePopup/isDisable/isVipItem/init` هي `()V` — **مثبتات تُثبّت الهوكات الحقيقية عند استدعائها**. v2.x كان يستبدلها بـ no-op/TRUE فعطّلها بصمت وسجّل "hooked" كاذباً.
- **السبب الثاني (إعلانات):** الفحص كان داخل حزمة الهدف فقط؛ SDKs الإعلانات تعيش في حزمها الخاصة فسقطت من الفحص.

## 2) إصلاحات v3.1.0
1. **F21 Legacy installer invoker (يعمل أولاً):** يستدعي كل مثبت void معروف (UserStatus الخمس + FloatingView.isDisable + VipItem.isVipItem + HideBuyVip/HideVipCard.init + ReplaceVipString.init/init2) عبر reflection — بلا استثناء never-throw، ومرة واحدة فقط لكل مثبت (`HookRegistry.markInvokedIfNew`).
2. **بوابات ذكية:** `gateTrue` (boolean ← TRUE، void ← استدعاء)، `gateNoop` (void ← استدعاء، boolean ← FALSE).
3. **حماية أسماء المثبتات:** `isInstallerName()` تُستثنى من كل حلقات الاستبدال العامة (antiDetect/genericScanner/floatingView) حتى لا تُخصى المثبتات.
4. **F15/F16 لحزم SDK الخارجية:** `AD_SDK_PREFIXES` (22 حزمة: gms.ads/facebook/unity/applovin/...) و`TRACKER_SDK_PREFIXES` (25 حزمة: umeng/appsflyer/firebase/...) تُفحص مع حزمة الهدف + أفعال موسعة (init/start/play/request/fetch) + heuristic لأفعال track/report.
5. **تشخيص ميداني:** كل خطوة تسجل `Fxx => +N hooks` دائماً + مسار `loktv_hook.conf` و`loktv_hook.log` في السجل — المستخدم يرسل هذه الأسطر بدل "لا يعمل".
6. **تثبيت مباشر:** `build.sh` يفشل البناء لو المحاذاة ناقصة (`zipalign -c`) + يطبع بصمة الشهادة. **قاعدة:** كل بناء في بيئة جديدة = مفتاح توقيع جديد → **احذف النسخة القديمة قبل تثبيت الجديدة** (سبب "App not installed" الذي كان MT Manager يخفيه بإعادة التوقيع).

## 3) جدول القطبية (محدّث — لا تعكسه)
| النوع | القيمة | أمثلة |
|---|---|---|
| امتياز/ترخيص boolean | TRUE | isVip(boolean), isLicensed, isVipItem(boolean) |
| مثبت void | INVOKE (مرة واحدة) | isVip()V, isDisable()V, init()V |
| كشف/تحديث/إعلان | FALSE | isModded, hasUpdate, showAd |
| عرض/متجر | no-op | showPopup, openPlayStore |
| سلاسل | "" | adUnitId, licenseKey |

## 4) بطاقة الإصدار
| البند | القيمة |
|---|---|
| الموديول | LOKTV Hook Pro 3.1.0 (310 / 2026.10.08-r3)، `com.loktv.hook` |
| أندرويد | 5.0 (API 21) → 14 (API 34)، xposedminversion 82 |
| الهدف | أي حزمة ببصمة morpha — أي إصدار |
| المحملات | LSPatch / NPatch / HKP / LSPosed / EdXposed — بدون روت |
| التوقيع | v1+v2+v3، محاذاة صفحات متحقق منها |
| الملف | dist/LOKTV-Hook-Pro-v3.1.0.apk |

## 5) التشخيص المطلوب من الميدان عند أي بلاغ
الصق من logcat (وسم `LOKTV-HOOK-PRO`): سطر `target detected ... [by package/signature]` + أسطر `Fxx => +N` + سطر `apply() done | pass=.. | hooks=.. | unique=..` + مسار `config:`.
- `F21 => +0` مع `UserStatus class not found` = الكلاسات غير ظاهرة للمحمل (تطبيق مضغوط بعمق) → جرّب إصدار هدف آخر.
- `F15 => +0` = لا SDK إعلانات معروف في الـ dex → أضف بادئة الحزمة لقوائم F15/F16.
