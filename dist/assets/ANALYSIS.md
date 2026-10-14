# AppHook v2.0 – تحليل آلية الاشتراك ونقاط الهوك

> هذا الملف هو **نتيجة التحليل النهائية** — يُسلَّم مع الموديول **لعدم التحليل كل مرة**.
> المحرك يقرأ `fingerprints.json` المرافق ويطابق بالبصمة بدل إعادة المسح الكامل.

## 0) بطاقة الموديول

| البند | القيمة |
|---|---|
| اسم الموديول | AppHook (الموديول الاحترافي الشامل) |
| الحزمة | `com.apphook.log` |
| رقم الإصدار | **2.0.0** (code 2) |
| أندرويد | **8.0 (API 26)** حتى **15 (API 35)** — المحرك version-agnostic حتى 16 |
| libxposed | minApi **101** / targetApi **102** |
| Xposed legacy | minVersion **82** (shim) |
| الدخول | `MainHook` (حديث: `META-INF/xposed/java_init.list`) + `LegacyHook` (قديم: `assets/xposed_init`) |
| البيئات | LSPosed / **LSPatch** / **NPatch** / **HKP** — **بدون روت** |
| الوصف المدمج | `AppHook v2.0 \| billing+features+network+integrity \| انعكاس+بصمة بلا أسماء ثابتة \| آمن ضد الكراش \| LSPatch/NPatch/HKP بلا روت` |

## 1. نموذج التحقق في التطبيقات المستهدفة

| النموذج | آلية العمل | نقاط الضعف | مثال |
|---------|-----------|------------|------|
| **محلي بالكامل** | المنطق داخل APK، يُخزَّن في SharedPreferences أو متغيرات | Hook مباشر على الدالة المنطقية | `isVip()`, `isPremium()` |
| **خادمي بالكامل** | الخادم يتحقق من Receipt ويعيد حالة الاشتراك | اعتراض استجابة HTTPS | `checkSubscription` API |
| **هجين (الأكثر شيوعاً)** | تحقق محلي + تحقق خادمي عند التزامن | يجب تجاوز الطبقتين معاً | Billing + Server Sync |
| **Google Play Billing** | `BillingClient` + `Purchase.getPurchaseState()` | Hook على `getPurchaseState` وإرجاع 0 | PBL v5-v8 |
| **RevenueCat** | `CustomerInfo.getEntitlements()` + `isActive()` | Hook على `EntitlementInfo.isActive` | RevenueCat SDK |

## 2. نقاط الهوك الرئيسية (Hook Points)

### 2.1 Google Play Billing Library (PBL)

| الفئة | الدالة | المُعدّات | طريقة الهوك | الهشاشة | الأثر الجانبي |
|-------|--------|-----------|-------------|---------|---------------|
| `com.android.billingclient.api.Purchase` | `getPurchaseState()` | `()` → int | `intercept` → `proceedWith(0)` / `setResult(0)` | مستقرة (PBL v5-v8) | قد يتخطى Acknowledge → نغطيه بـ `isAcknowledged→true` |
| `Purchase` | `isAcknowledged()` | `()` → boolean | إجبار `true` | مستقرة | منع إعادة الشراء |
| `BillingClient` | `queryPurchasesAsync()` | `(Params, Listener)` → void | مراقبة before/after (الحقن الكامل هش) | مستقرة | تزامن خادمي |
| `Purchase.PurchasesResult` | `getPurchasesList()` | `()` → List | مراقبة فقط | مستقرة | قائمة فارغة |
| `BillingClient` | `isReady()` | `()` → boolean | إجبار `true` | مستقرة | الاتصال |

PBL v7 مدعوم حتى أغسطس 2026، وv8 منذ يونيو 2025. فئات `Purchase`/`BillingClient` محمية بقواعد keep → مستقرة للهوك.

### 2.2 RevenueCat SDK

| الفئة | الدالة | المُعدّات | طريقة الهوك | الهشاشة | الأثر الجانبي |
|-------|--------|-----------|-------------|---------|---------------|
| `com.revenuecat.purchases.EntitlementInfo` | `isActive()` | `()` → boolean | إجبار `true` | مستقرة | يحتاج تصفير billingIssue |
| `EntitlementInfo` | `getBillingIssueDetectedAt()` | `()` → Date | إجبار `null` | مستقرة | Grace Period |
| `CustomerInfo` | `getEntitlements()` | `()` → Entitlements | مراقبة فقط | R8-sensitive | مزامنة الخادم |

### 2.3 اعتراض الشبكة (OkHttp / HTTPS)

| الفئة | الدالة | طريقة الهوك | الهشاشة | ملاحظة |
|-------|--------|-------------|---------|--------|
| `okhttp3.Response` | `body()` | مراقبة (لا نستهلك one-shot body) | R8-sensitive | تعديل الـ Proxy اختياري |
| `okhttp3.RealCall` | `execute()` | مراقبة URL+code | مستقرة | تدفق الطلب |
| `java.net.HttpURLConnection` | `getResponseCode()` | مراقبة فقط (لا نجبر 200) | مستقرة | إجبار 200 يكسر التدفق |
| `RealInterceptorChain` | `proceed()` | **نتجنبه عمداً** | هشّ (internal) | يُكسر بين إصدارات OkHttp |

### 2.4 Play Integrity

| الفئة | الدالة | طريقة الهوك | الهشاشة |
|-------|--------|-------------|---------|
| `IntegrityManager` | `requestIntegrityToken()` | مراقبة افتراضياً (التعديل العدواني يكسر التطبيق) | R8-sensitive |
| `StandardIntegrityManager` | وجود/مراقبة | رصد فقط | R8-sensitive |

### 2.5 أعلام Premium المحلية

| النمط | طريقة الهوك | الهشاشة |
|-------|-------------|---------|
| `isPremium/isVip/isPro/hasPremium/isSubscribed/...` → boolean | إجبار `true` | R8-sensitive → نغطيه بالمسح الجزئي + مسح البصمة الشامل |
| `getVipStatus` وأشباهها → int | إجبار `1` | نفس الغطاء |

## 3. مخاطر الكسر عند التحديث القادم

| الأولوية | نقطة الهوك | السبب | الحل المطبق |
|----------|-----------|-------|------|
| **P0** | `Purchase.getPurchaseState` | PBL v9 قد يغير enum | بصمة مرنة + fallback `getPurchase*` + تجاهل آمن |
| **P0** | `queryPurchasesAsync` | تغيير التوقيع في v9 | مسح كل overloads بالاسم + مراقبة بدل حقن |
| **P1** | `EntitlementInfo.isActive` | RevenueCat v9+ قد يعيد التسمية | fallback `active` + مسح شامل |
| **P1** | `Response.body` | OkHttp v5/R8 | مراقبة + تخطي آمن |
| **P2** | دوال Premium المخصصة | R8 يعيد التسمية | قاموس جزئي + `scanPremiumFlags()` الشامل |
| **P2** | Integrity | Google تغير API | مطابقة جزئية `integritytoken` + مراقبة |

## 4. استراتيجية تجنب الكراش (مطبقة بالكامل)

1. **ExceptionMode.PROTECTIVE** على كل Hook حديث — أي استثناء يُلتقط ويستمر التطبيق.
2. **LegacyBridge يدوي**: كل `hookMethod` داخل try/catch + جسم الهوك داخل try/catch.
3. **findClassQuiet**: `Class.forName(..., false, cl)` تُرجع null بدل `ClassNotFoundError`.
4. **نطاق آمن**: استثناء `android / systemui / gms / vending / الموديول نفسه` لمنع البوت لوب.
5. **Packed apps**: الهوك الحقيقي في `onPackageReady.getClassLoader()` (بعد فك الـ stub)، لا في ClassLoader مبكر.
6. **لا تعديل للقيم الحرجة**: `getPurchasesList`/`getResponseCode`/`Integrity` مراقبة فقط.
7. **تسجيل شامل**: `[Billing] / [Feature] / [Network] / [Safe]` مع الحدث والوسائط والنتيجة.

## 5. آلية القياس (Metrics)

| المقياس | التنفيذ | مثال السجل |
|---------|---------|-----------|
| استدعاءات الفوترة | `logBilling(event, args, result)` | `[Billing] getPurchaseState args=0 result=2` ثم `-> forced 0` |
| الميزات المفتوحة | عدّاد `AtomicInteger` لكل ميزة | `[Feature] isPremium unlocked: 5 times` |
| الشبكة | URL + code + size | `[Network] GET /api/sub -> 200 (-1B)` |
| عزل الكراش | PROTECTIVE + try/catch | `[Safe] X skipped: method-not-found` |

## 6. كاش البصمة — لعدم التحليل كل مرة

- `docs/fingerprints.json`: قاعدة البصمات المستقرة (تُقرأ وقت التطوير/المراجعة).
- وقت التشغيل: `findClassQuiet + findMethodByFingerprint + scanPremiumFlags` مع
  سلسلة: **مستقر → جزئي → مسح شامل → تخطي آمن**.
- أي Hit جديد يُسجل في اللوج بصيغة `Hook OK: <event> -> <method>` فيُضاف للـ JSON في الإصدار التالي.

## 7. البناء والتحقق

```bash
cd AppHook
./gradlew :app:assembleRelease
# الناتج: app/build/outputs/apk/release/app-release.apk
adb install -r app/build/outputs/apk/release/app-release.apk
# LSPosed: فعّل AppHook + حدد النطاق
# السجل المتوقع: "AppHook v2.0 | billing+features+..."
```

```bash
# حزمة السورس
zip -r AppHook-v2.0-source.zip AppHook/ -x "*/build/*" "*/.gradle/*"
```

## 8. التوافق والصيانة

- أندرويد 8.0–15، libxposed 101→102، PBL v5–v8، OkHttp 3/4، RevenueCat v7/v8.
- كل Feature في ملف مستقل (`BillingHook / RevenueCatHook / FeatureHook / NetworkHook / IntegrityHook`)
  + `SafeHook` + `FingerprintEngine` مشتركة → إضافة ميزة = ملف جديد + سطر في `MainHook.installAll`.
- `MainHook.inScope()` للتضييق على حزم معينة عند الحاجة.

---

# ملحق v3.0 — Flutter-Aware + Dynamic Discovery (التطبيق: ai.mainfunc.genspark)

## 9) لماذا تغيرت الاستراتيجية؟

التطبيق **Flutter + Native** والتحقق **خادمي**: كود Dart مترجم AOT في
`libapp.so` (لا Hook مباشر). الهدف الحقيقي انتقل إلى الجسر:

```
Flutter (Dart AOT, libapp.so) -- لا Hook مباشر
        ↕ MethodChannel (JNI Bridge)  ← الاعتراض هنا (الأهم)
Native (Java/Kotlin + libflutter.so) ← Prefs + HTTP + JSON هنا
        ↕ HTTPS
Server (تحقق خادمي) ← اعتراض الاستجابة قبل وصولها لـ Flutter
```

## 10) طبقات v3 الجديدة

| # | الطبقة | الملف | الهوك | الهشاشة | ملاحظة |
|---|--------|-------|-------|---------|--------|
| 1 | Flutter Channel | `FlutterChannelHook` | `invokeMethod` مراقبة + `setMethodCallHandler` لفّ Proxy (success→true/1/ترقيع Map) | مستقرة | الأهم — الرد يُعدل قبل Dart |
| 2 | Prefs | `PrefsHook` | `SharedPreferencesImpl.getBoolean→true` / `getInt 0→1` (+ مراقبة getString/getAll) | مستقرة | Flutter يخزن `flutter.*` هنا |
| 3 | HTTP | `HttpHook` | `Response.body` + `RealCall.execute` مراقبة؛ `Chain.proceed` اختياري هش (P0)؛ Cronet رصد | R8/internal | قبل وصول الاستجابة لـ Flutter |
| 4 | JSON | `JsonHook` | `Gson.fromJson` ترقيع حقول + `JSONObject.getBoolean→true`؛ Moshi رصد | مستقرة/R8 | يعترض موديلات الاشتراك |
| 5 | Billing/Flags/Integrity | الموجودة سابقاً | كما في v2 (احتياطي native) | — | لا تُحذف |
| 6 | Scanner | `DynamicScanner` | `DexFile(sourceDir+splitSourceDirs)` يسجل الفئات المرشحة (سقف 200) | مستقرة | يُظهر الأسماء بعد R8 → أساس v3.1 |

## 11) أول ما سيفشل في v3 (مرتب)

| P | السبب | علامة السجل | الحل المطبق |
|---|-------|-------------|-------------|
| P0 | `RealInterceptorChain.proceed` internal | `Chain.proceed ... ignored` | اختياري — لا يوقف الفتح |
| P0 | تغير `MethodChannel.Result` | `setMethodCallHandler ... differs` | fallback: PrefsHook + JsonHook |
| P1 | تغير `Gson.fromJson(String,Class)` | `Gson.fromJson ... differs` | fallback: `org.json` |
| P1 | تغير `SharedPreferencesImpl` | `impl absent` | fallback: مراقبة الواجهة + Channel |
| P2 | PBL v9 | `method-not-found` | الاعتماد على ChannelHook |

## 12) تكييفات API 102 (مهم للمطور)

- لا يوجد `io.github.libxposed.api.XposedHelpers` في API 102 — الاستبدال:
  `Class.forName(name,false,cl)` + `fw.hook(Method).setExceptionMode(PROTECTIVE).intercept(...)`
  + `ReflectCall.call(...)` بدل `callMethod`.
- لا يوجد `onApplicationCreated` في الواجهة — الـ Scanner يعمل من
  `onPackageReady` عبر `param.getApplicationInfo()` (sourceDir) بلا Context.
- `PackageLoadedParam` لا يملك `getClassLoader()` — الهوك في `onPackageReady`
  (`getClassLoader()` الحقيقي بعد الـ stub) مع إشعار مبكر في `onPackageLoaded`.
- Legacy shim (`de.robv`) ما زال يستخدم `XposedHelpers` — متوفر هناك —
  وأُضيفت له مرايا Flutter/Prefs/JSON.
- النطاق: `scope.list` = `ai.mainfunc.genspark` + `.test` مع `staticScope=true`.
  للوضع الشامل: احذف `scope.list` وضع `staticScope=false` ووسّع `inScope()`.

## 13) السجل المتوقع (v3)

```
[AppHook] AppHook v3.0.0 | Flutter-aware: channel+prefs+http+json | ...
[AppHook] package ready: ai.mainfunc.genspark.test | cl=...
[AppHook] Hook OK: Flutter.MethodChannel.invokeMethod -> ...
[AppHook] Hook OK: Flutter.MethodChannel.setMethodCallHandler -> ...
[AppHook] [Prefs] SharedPreferences hooked
[AppHook] [Http] OkHttp ... / Cronet ...
[AppHook] [Json] Gson hooked / org.json hooked
[AppHook] All v3 layers installed for: ai.mainfunc.genspark.test
[AppHook] [Scan] ... -> N candidate(s)
[AppHook] [Scan]   -> com.genspark.model.SubscriptionInfo (مثال)
[AppHook] [Flutter] MethodChannel.getSubscriptionStatus(...) -> ...
[AppHook] [Feature] channel-query:... / prefs_bool:flutter.isPremium ...
```

الخطوة التالية: شغّل v3 وأرسل السجل — الفئات التي يكشفها الـ Scanner تحدد نقطة
الفتح الفعلية لـ v3.1.

---

# ملحق v3.0.1 — إصلاح كراش الـ Proxy (عاجل)

## 14) السبب الجذري للكراش في v3.0

```
IllegalArgumentException: setMethodCallHandler argument 1 has type
MethodChannel$MethodCallHandler, got $Proxy5
```

التسلسل: ربط `setMethodCallHandler` نجح → استبدلنا الـ handler بكائن
`Proxy.newProxyInstance` → عند تمريره للأصلي رفضه `Method.invoke` لأن
الفئة الديناميكية `$Proxy5` ليست من النوع الدقيق — كراش فوري.
الدرس: **لا تُستبدل وسائط دالة عبر Proxy في Xposed أبداً** إذا تحقق الأصلي من النوع.

## 15) الإصلاح المطبق (v3.0.1)

| البند | v3.0 (مكسور) | v3.0.1 (مصحح) |
|---|---|---|
| `setMethodCallHandler` | استبدال الوسيط بـ Proxy | قراءة فقط — الوسيط يمر كما هو |
| اعتراض handler | Proxy | `hookAll(onMethodCall)` على فئة handler الحقيقية (تشمل الموروث/lambdas) |
| اعتراض Result | Proxy | `hookAll(success)` على فئة Result الحقيقية + `proceed(newArgs)` |
| نوع القيمة | قد يتغير | نفس النوع دائماً (Boolean→Boolean) |
| التكرار (60+ سطر) | بلا dedup | `hookedHandlers/hookedResults` — مرة واحدة لكل فئة |
| حارس Proxy | — | `ProxyMarker`: لا ربط على فئات Proxy أبداً |

تخطي الأصلي عند الاعتراض: استدعاء `result.success(TRUE)` الأصلي ثم إرجاع
null **دون** `proceed()` — إيقاف آمن بلا كراش. تكييف API 102 نفسه مطبق
(لا XposedHelpers — `hookAll` الخاصة بنا فوق `fw.hook`).

## 16) السجل المتوقع بعد الإصلاح (بلا كراش، بلا تكرار)

```
[AppHook] AppHook v3.0.1 | Flutter-safe: ...
[AppHook] Hook OK: Flutter.invokeMethod -> ...
[AppHook] Hook OK: Flutter.setMethodCallHandler -> ...
[AppHook] [Flutter] -> Native handler: getSubscriptionStatus
[AppHook] [Flutter] * intercepted: getSubscriptionStatus
[AppHook] [Feature] channel:getSubscriptionStatus unlocked: 1
[AppHook] [Scan] ... -> N candidate(s)
```

---

# ملحق v3.1 — فلترة الضجيج + مسار WebView (نتائج سجل v3.0.1)

## 17) نتائج السجل (نجاح)

- صفر crashes — إصلاح الـ Proxy مؤكد.
- 80+ handler مربوطة، Prefs/JSON/Billing مفعّلة، OkHttp غائب (طبيعي).
- اكتشاف حاسم: `flutter_inappwebview` + AppsFlyer + Firebase Crashlytics.
- ضجيج مسيطر: `onProgressChanged x20` (تحميل WebView) + `popRoute x3`.
- لا نشاط اشتراك — المستخدم لم يفتح شاشة Premium بعد.

## 18) تغييرات v3.1

1. **فلترة ضجيج**: مجموعة `NOISE` (15 دالة WebView/تنقل) تُسكت من السجل في
   المسارين (invoke/outgoing و onMethodCall/incoming) — المهم فقط يُسجل.
2. **كلمات Genspark**: `genspark/credit/quota/plan/tier/upgrade`,
   `is_subscribed/user_plan/account_type/subscription_status...`.
3. **WebViewHook** (مراقبة فقط): `addJavascriptInterface` (أسماء الجسور) +
   `shouldInterceptRequest` بالصيغتين (String و WebResourceRequest) —
   تسجيل URLs الدفع فقط، بلا حظر.
4. **AppsFlyerHook** (مراقبة): `logEvent` بأي توقيع — أحداث الشراء فقط.
5. **UrlConnectionHook** (مراقبة): `getInputStream` (URL قبل القراءة) +
   `getResponseCode` — يغطي `dart:io` عبر JNI.
6. **تشخيص أغنى**: طباعة وسائط الـ call (مختصرة 300 حرف) + فئة الـ result +
   مراقبة `Result.error/notImplemented`.
7. فتح القيم بقي كما هو (قناة/Prefs/JSON/Billing) — الجديد مراقبة أولاً.

## 19) خطة التشخيص (للمستخدم — بالترتيب)

1. افتح شاشة الاشتراك (Premium/Upgrade) في Genspark.
2. حاول ميزة مدفوعة. 3. انتظر 10 ثوانٍ. 4. أرسل السجل.
3. المطلوب رصده: `★ Native: <اسم>` + `args = ...` + `result class = ...` +
   `prefs_bool:flutter.<key>` + `[WebView] REQ:` + `[AppsFlyer] event:`.
4. هذه الأسماء = بصمة التحقق الحقيقية → v3.2 بأسماء دقيقة.
