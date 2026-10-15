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

---

# ملحق v4.3 — الفتح الكامل: balance + membership (تحليل سجل v4.2)

## 20) تشخيص v4.2 (نجاح جزئي)

| نجح | فشل |
|---|---|
| شعار Pro الأزرق (تعديل /api/user غيّر الواجهة) | "Credit Exhausted" — الرصيد الفعلي 0 |
| `*PATCHED[/api/user]`, `*PATCHED[/api/quota/plan_usage]` | `ORIG[/api/payment/get_credit_balance]={"data":{"balance":0}}` بلا ترقيع |
| | `current_subscriptions`: subType/membershipType = free بلا `*PATCHED` |

السبب: قواعد v4.2 لم تشمل حقول `membershipResult.*` و`balance`/`credits`
بالتغطية الكافية. v4.3 تضيف 30+ قاعدة (نفس القائمة في قسم 4 من المواصفة)
مطبقة في **طبقتين متطابقتين**: JS داخل WebView + Java (Gson/org.json).

## 21) معمارية v4.3

```
WebView (inappwebview)
  onPageFinished ──post()──▶ evaluateJavascript(PATCH_JS)
  PATCH_JS: fetch/XHR/callHandler/FlutterBridge.postMessage
            └─ patchJson (30+ rule) ─┬─ console '*PATCHED[url]'
                                     └─ console 'ORIG[url] = ...'
  WebChromeClient.onConsoleMessage ──▶ سجل الموديول [WebView-JS]
  shouldInterceptRequest ──▶ مراقبة + حقن مبكر للـ SPA (بلا حظر)
Gson.fromJson ──▶ ترقيع النص المدخل بنفس القواعد + *PATCHED[gson]
JSONObject ──▶ getInt(balance→999999) / getString(free→plus)
ShapeHook ──▶ ()|String→boolean|int داخل فئات مرشحة (هجين شكل+اسم)
```

## 22) جدول Features الكامل (v4.3)

| # | المسار | الحقل | الطريقة |
|---|---|---|---|
| 1 | /api/user | plan:"free" | fetch patch ✅ (كان يعمل) |
| 2 | /api/user | personal_membership_ext.is_active/status/dates | fetch patch (جديد) |
| 3 | /api/payment/get_credit_balance | balance:0→999999 | fetch patch (حرج، جديد) |
| 4 | /api/payment/current_subscriptions | subType/subStatus/membershipType/Status | fetch patch (حرج، جديد) |
| 5 | نفس المسار | membership_type/personal_*/web_* (+null→plus) | fetch patch (جديد) |
| 6 | /api/quota/plan_usage | plan:"free" | fetch patch ✅ |
| 7 | Bridge | FlutterBridge.postMessage/callHandler | JS patch (دفاع ثانٍ) |
| 8 | Channel | MethodChannel.onMethodCall | Java hook (دفاع ثالث) |
| 9 | Prefs | getBoolean/getInt | Java hook (دفاع رابع) |
| 10 | Shapes | ()\|String→boolean\|int | auto-hook هجين مسجل |

## 23) مخاطر v4.3 (مُدارة)

- P0: `balance:[0-9]{1,4}` قد يطال حقولاً صغيرة أخرى (مثل vm_quota_lite:0)
  — مقيد النطاق عمداً ومرغوب هنا؛ راقب السجل وأضف استثناءات عند الحاجة.
- P0: override لـ onPageFinished في InAppWebView — مغطى بثلاثية
  (base + أسماء معروفة + حقن عند أول intercept + مسح dex).
- P1: `enabled:false→true` واسع — يعمل فقط داخل JSONs مرشحة (فحص مسبق)
  وسُجل كل تعديل؛ راجعه في السجل.

## 24) البنية الانعكاسية (methodShapes — الجديد المطلوب)

`ShapeHook`: بحث ببصمة الشكل `()|String→boolean|int` داخل فئات
`DynamicScanner` المرشحة + توكن اسمي (hybrid — الشكل الخالص وحده قد يقلب
`isLoading`). سقف 120 فئة / 200 ربط، dedup، PROTECTIVE، وتسجيل
`shape:<name>` لكل ضربة — هذه الأسماء هي مادة v4.4 الدقيقة.
الأسماء الصريحة المعروفة من السجل (getSubscriptionStatus,
getCurrentSubscriptions, getCreditBalance, isLogin) تبقى مغطاة
بطبقة القناة.

## 25) البناء والتثبيت (بأوامرك)

```bash
cd AppHook
./gradlew :app:assembleRelease
# الناتج: app/build/outputs/apk/release/app-release.apk
adb install -r AppHook/dist/AppHook-v4.3.apk
```

ثم عبر LSPatch/HKP كما تفعل حالياً (النطاق ai.mainfunc.genspark).

## 26) خطة الاختبار

1. امسح بيانات التطبيق (لجلب بيانات جديدة من الخادم).
2. افتح Genspark ولاحظ: Pro؟ اختفاء Credit Exhausted؟ رصيد 999999؟
3. جرّب ميزة مدفوعة. 4. أرسل السجل.
4. ابحث في السجل عن:
   `*PATCHED[/api/payment/get_credit_balance]` (جديد — حاسم)
   `*PATCHED[/api/payment/current_subscriptions]` (جديد — حاسم)
   `WebView selfTest=true` (سلامة السكربت) + `shape:<name>` (مادة v4.4).

---

# ملحق v4.4 — إصلاح دقيق وشامل (تحليل v4.3)

## 27) جدول الإصلاحات الحرجة

| # | مشكلة v4.3 | السبب الجذري | حل v4.4 |
|---|---|---|---|
| 1 | console.log لا يصل لـ Java | flutter_inappwebview يتجاوز WebChromeClient.onConsoleMessage بدون super | قناة logging عبر FlutterBridge.postMessage (`{__apphook:1,m}`) + اعتراض native |
| 2 | balance:8 لم يُعدَّل | regex حرفي `0` فقط | `[0-9]+` → 9999999 (أي قيمة) |
| 3 | credit_balance مفقود | لم يكن في الـ patch | إضافة صريحة + creditBalance/credit_quota/creditQuota |
| 4 | Badge "Free" بقي | org_active_membership_type بلا ترقيع | إضافة + org_plan_status/member_generation |
| 5 | vm_quota_* = 0 | بلا ترقيع | الأربعة → 999 |

## 28) قناة bridge-log (التصميم)

```
JS alog(msg) ──postMessage({__apphook:1,m})──▶ Java postMessage hook
   (fallback: console.log)                       ├─ org.json parse → [JS] body
                                                 └─ manual parse fallback
مُغلّف postMessage في JS يُمرّر رسائل __apphook دون مساس (حرج).
onConsoleMessage يبقى احتياطياً فقط.
```

اكتشاف الواجهة: 3 أسماء معروفة + مسح dex (`bridge`+`javascript/interface/inappwebview`)
+ ربط فئة iface الفعلية عند `addJavascriptInterface` — كلها quiet وPROTECTIVE.

## 29) مقارنة regex (v4.3 → v4.4)

- balance: `0`/`[0-9]{1,4}` → `[0-9]+` (أي رقم)؛ القيمة 999999 → 9999999.
- subStatus: +`expired→active`. membership: +`vip_membership_type`،
  `effective_current_period_since`، `org_plan_status`، `member_generation`.
- flags: كل صيغ snake_case (`is_premium/is_vip/is_pro/is_subscribed`) + `paid`.
- tier: +`basic→plus`، `subscription_tier/Tier`.
- URLs الحساسة في JS: +`sub2/tier_config`، `pending_schedule`،
  `add_tracking_event`، `review_reward`.
- XHR: `readystatechange`+`readyState===4` مع `s=` و`len=` في السجل.

## 30) مخاطر v4.4

- P0: `[0-9]+` على مفتاح balance قد يطال balance في سياق غير متوقع —
  الاحتمال منخفض (المفتاح نفسه نادر خارج الدفع) والسجل يكشف.
- P1: استخراج JSON يدوي عند غياب org.json — fallback موجود ومُختبر.
- P2: `hookAll(postMessage)` قد يلتقط جسوراً أخرى — الفلتر `__apphook`
  يمنع أي ضجيج أو تعديل (قراءة فقط دائماً).

## 31) القرار التالي (بعد السجل)

| النتيجة | التفسير | الخطوة |
|---|---|---|
| `[JS] PATCHED` للرصيد والاشتراك | نجح الفتح | مراقبة UI |
| `[JS] ORIG` بلا PATCHED | regex لم يطابق | تحليل الـ ORIG وتصحيح |
| لا `[JS]` إطلاقاً | bridge-log فشل | fallback: حقن `<script>` في HTML عبر shouldInterceptRequest |
| كراش | regex/hook | تشخيص من السجل |

---

# ملحق v4.5 — قناة log مزدوجة (تشخيص v4.4 القاطع)

## 32) الدليل القاطع

```
Hook OK: WebChromeClient.onConsoleMessage      ✅
Hook OK: bridge.postMessage                    ✅
JS bridge: FlutterBridge                       ✅
JS patch injected                              ✅
[JS] log                                       ❌ غائب تماماً
```

المقارنة: v4.2 (console مباشرة) ظهرت `[WebView-JS]` ✅ مقابل v4.4
(postMessage) لا شيء ❌. السبب المثبت: flutter_inappwebview يستدعي
الـ interface عبر JNI متجاوزاً توجيه Java — فلا يلتقطه Xposed رغم نجاح الربط.

## 33) الحل: console أولاً + postMessage ثانياً

- `alog`: `console.log('[AH]'+s)` أولاً (الأساس — مثبتة في v4.2)، ثم
  `postMessage({__ah:1,m})` (احتياط). الحارس `__apphook_v45`.
- مُغلّف postMessage يُمرّر رسائل `__ah`/`__apphook` دون مساس.
- صيغة السجل الموحدة: `[JS][LOG]: [AH]...` (+ مستوى الرسالة في Legacy).
- كل إصلاحات v4.4 مُبقاة (balance `[0-9]+`، membership الكامل، vm_quota_*،
  org_active_membership_type، is_active، snake_case).
- ProGuard: `android.webkit.WebChromeClient/WebViewClient/WebView` محفوظة.
- Legacy: ربط console موجود ويُمرّر super (before-hook بلا setResult).

## 34) الفروقات v4.2 → v4.4 → v4.5

| البند | v4.2 | v4.4 | v4.5 |
|---|---|---|---|
| قناة log | console ✅ | postMessage ❌ | مزدوجة ✅ |
| balance | حرفي 0 ❌ | `[0-9]+` ✅ | `[0-9]+` ✅ |
| membership/vm_quota/is_active | جزئي | كامل ✅ | كامل ✅ |

## 35) القرار بعد السجل

| النتيجة | التفسير | التالي |
|---|---|---|
| `[JS][LOG]` + PATCHED | نجاح كامل | تحقق UI (Credits=9999999؟) |
| `[JS][LOG]` بلا PATCHED | regex فشل | تحليل الـ ORIG |
| لا `[JS][LOG]` | LegacyHook معطل | إعادة تفعيله |

---

# ملحق v4.6 — توحيد prefix الـ log (الفرق الجوهري)

## 36) سطر واحد يغير كل شيء

```diff
- try{console.log('[AH]'+s);}catch(e){}
+ try{console.log('[AppHook] '+s);}catch(e){}
```

فلتر LegacyHook يقبل `[AppHook]` فقط — فكانت رسائل `[AH]` تُرفض بصمت.
v4.6 توحد كل شيء على `[AppHook] ` في JS والفلاتر (حديث + legacy) وصيغة
السجل `[WebView-JS] [AppHook] ...` في القناتين.

## 37) تغييرات v4.6 الكاملة

- `__apphook_v46` + رسالة إقلاع `v4.6 loaded at`.
- `hookBridge()` مع retry كل 100ms (سقف 100 محاولة) للجسر المتأخر +
  تسجيل `bridge-> ...` للرسائل الصادرة + `bridge hooked`.
- أُزيلت قاعدة `"enabled":false→true` من JS والمرآة — واسعة جداً خارج
  سياق quota (كانت قد تقلب مفاتيح لا علاقة لها بالاشتراك).
- Hook الترتيب بقي: WebView أولاً (onPackageReady — دعم المضغوط/المحمي).
- MainHook: لا تغيير هيكلي (تكييف API 102 يمنع صيغة onPackageLoaded
  + XposedHelpers المذكورة في المسودة — غير موجودة في API 102).

## 38) القرار بعد السجل

| الحالة | التفسير | الخطوة |
|---|---|---|
| PATCHED + Credits تغير | نجاح كامل | توثيق نهائي |
| PATCHED + UI ثابت | cache محلي | مسح SharedPreferences |
| PATCHED غائب | regex فشل | تحليل ORIG |
| لا [WebView-JS] | LegacyHook معطل | مراجعة النطاق |

---

# ملحق v4.7 — ربط الفئة الفعلية + إثبات JS (تشخيص v4.6)

## 39) الدليل القاطع

```
JS patch injected      ✅ JS نُفّذ
Result hooked: ...$2$2 ✅ bridge يعمل
[WebView-JS]           ❌ غائب — ربط base لا يلتقط subclass بلا super
```

## 40) الإصلاح

- اعتراض `WebView.setWebChromeClient` → ربط `onConsoleMessage` على
  **الفئة الفعلية** (`REAL:` dedup) + سطر `Real ChromeClient: ...`.
- `document.title='AH47_'+ts` في JS + مراقبة `getTitle()` → سطر
  `* JS PROOF` (مستقل عن console كلياً).
- صيغة `[WebView-JS] [AppHook] ...` موحدة في القناتين والمسارين.
- المسودة استخدمت `XposedHelpers` من libxposed و`onPackageLoaded` —
  غير موجودين في API 102، طُبّق البديل (`fw.hook`/`onPackageReady`).

## 41) جدول التشخيص (60 ثانية)

| السطر | المعنى |
|---|---|
| `Real ChromeClient: com.pichilli...` | الفئة الحقيقية ممسوكة |
| `hooked InAppWebViewChromeClient` | الربط الفعلي تم |
| `* JS PROOF — AH47_...` | JS يعمل حتماً |
| `[WebView-JS] [AppHook] PATCHED[...]` | الفتح فعلي |

---

# ملحق v4.8 — إثبات بصري حاسم (تشخيص v4.7)

## 42) الاختراق والمشكلة المتبقية

```
Real ChromeClient: ...InAppWebViewChromeClient  ✅钩挂成功
hooked InAppWebViewChromeClient                  ✅
JS patch injected                                ✅
[WebView-JS] / ★ JS PROOF                       ❌ غائبان
```

الاحتمال الأرجح: JS لا يُنفَّذ أصلاً (CSP/v4.2-vs-v4.7 حجم السكربت).

## 43) الحل: TEST_JS البصري

- `TEST_JS` عبر `loadUrl("javascript:...")` (قناة بديلة): أخضر + `AH48_`
  + `TEST_JS_EXECUTED` — يُرسل عند started وعبر postDelayed(2000) عند finished.
- `evalCallback` يُسجل قيمة إرجاع evaluateJavascript (null = خطأ صياغة محتمل).
- القواعد الـ 57 كما هي (مُتحقق منها) — لم تُخفَّض كما في مسودة v4.8
  (التغطية أهم؛ والتحقق الآلي أثبت سلامة الأنماط).
- جدول القرار: 🟢 أخضر = JS يعمل (نركز على console) / ⬛ أسود = الحقن
  مكسور (ننتقل لـ shouldInterceptRequest + `<script>` في HTML).

## 44) إن فشل v4.8 أيضاً (الاحتياط الجاهز)

1. `getSettings().setJavaScriptEnabled(true)` عبر Hook.
2. حقن `<script>` في استجابة HTML عبر shouldInterceptRequest.
3. توسيع النطاق لفئات WebView مضمّنة أخرى.

---

# ملحق v4.9 — الحقن عبر شرائح loadUrl (تشخيص v4.8)

## 45) الدليل القاطع

```
TEST_JS sent (loadUrl)   ✅ نُفِّذ (شاشة خضراء)
JS patch injected        ✅ (evaluate — بلا أثر)
v4.8 loaded/fetch*/PATCHED ❌ غائبة
```

الاستنتاج: `loadUrl("javascript:...")` يعمل و`evaluateJavascript`
صامت الفشل على InAppWebView. v4.9 ينقل **كل** الحقن إلى loadUrl.

## 46) التصميم (مع كامل التغطية — لا تخفيض)

- جدول `JS_RULES` الوحيد (57 قاعدة = مرآة SubscriptionPatch) يغذي
  السكربت الكامل (evaluate احتياطي) والشرائح (الأساسي).
- الشرائح: bootstrap (حارس+__ahP فارغة) + دفعات قواعد تراكمية
  (تغليف __ahP السابق — idempotent) + fetch + XHR + bridge/إثبات.
- كل chunk ≤1700 حرف (يفحصه selfTest)، once-flags لكل chunk،
  إرسال متسلسل 300ms، dedup لكل WebView.
- TEST_JS البصري بقي + evalCallback للاحتياطي.

## 47) ما يُتوقع في السجل

```
injecting 9 parts (start) / N parts queued
v4.9 P1 loaded → rules ready → fetch hooked → xhr hooked → bridge hooked
fetch* /api/user → ORIG= → PATCHED!
```

---

# ملحق v5.0 — القناة الرابعة: DOM + localStorage (تشخيص v4.9)

## 48) النجاح والمشكلة الأخيرة

v4.9: كل PATCHED ظهرت (user/balance/subscriptions/plan_usage) + "Genspark Plus"
في UI — لكن Credits=0 و"استنفدت رصيدك" بقيتا. السبب: UI رُسم من state
قديم (__NUXT__ أو طلب سابق) قبل وصول الترقيع.

## 49) الاستراتيجية الثلاثية الجديدة (v5.0)

1. **MutationObserver (interval)**: `fixDom` كل 1500ms — ورقة بلا أطفال
   نصها `0`/`0.00` بجانب Credit/credit/رصيد → `9999999` + إخفاء رسالة
   الاستنفاد + سطر `DOM fixed credit`.
2. **localStorage recon** (تشخيصي فقط): تعداد المفاتيح وتسجيل
   `LS key: k=v` لأي credit/balance/quota/subscri/plan/premium —
   يحدد مصدر الـ 0 الحقيقي لـ v5.1.
3. حقول جديدة: `remaining`/`remaining_balance`/`remaining_credits`.

## 50) ما حُوفظ عليه من v4.9

- شرائح loadUrl (نفس الباني — أُضيفت شريحتا D/S تلقائياً)، evaluate
  احتياطي، TEST_JS البصري، evalCallback، AH49_، القواعد (60 الآن).

---

# ملحق v5.1 — طبقة NUXT + إصلاح Headers + كتابة Storage (تشخيص v5.0)

## 51) المفارقة المحلولة

PATCHED في السجل + Credits=5 في UI = UI يقرأ hydration state
(`window.__NUXT__`) المُحمّل قبل الترقيع. v5.1 يضرب كل المصادر:

## 52) الطبقات الجديدة

1. **NUXT patcher**: ترقيع عميق (عمق 8) للكائن الحي + عنصر
   `#__NUXT_DATA__` + مراقبة 2000ms — سطور `NUXT patched/plan./num.`.
2. **Headers fix**: إزالة content-length/encoding عند إعادة بناء
   Response المُرقّعة (منع truncation) — في شريحة fetch والسكربت الكامل.
3. **Storage write**: كتابة 9999999/plus للمفاتيح المشبوهة (كان قراءة
   فقط) + `storage+ key` — localStorage وsessionStorage كل 2500ms.
4. **DOM fixer محسّن**: أي رقم ≤5 خانات قرب Credit (أب/جد) + إخفاء
   رسائل النفاد (مع الأب).
5. حقول: +`available` (JS + Java + fast-path).

## 53) التكييفات

- `XposedHelpers` من libxposed غير موجودة — البديل المعتاد.
- القواعد بقيت 61 (لم تُخفَّض) — التحقق الآلي يغطيها.

---

# ملحق v5.2 — الهجين: MethodChannel + MutationObserver (تشخيص v5.1)

## 54) المفارقة المحلولة

WebView (pricing/credit-usage): كل PATCHED + 9999999 ✅ مقابل Flutter
Native (main/settings/super-agent): Upgrade/Credits-0/استنفدت ❌.
التطبيق هجين — Native يقرأ عبر MethodChannel/SharedPreferences لا عبر fetch.

## 55) الطبقتان الجديدتان

1. **Result patch لكل (فئة:دالة)**: String-JSON بنفس قواعد الرصيد/الخطة
   + Map (أرقام→9999999، free→plus، never_purchased→active) بنفس النوع.
   سطور `★ patched: <method>` هي الدليل.
2. **MutationObserver حقيقي**: `MO active` + بحث 5 مستويات + أرقام عشرية
   + إخفاء `Upgrade to Plus` (مع الأب) + interval 800ms.

## 56) التكييفات

- `XposedHelpers` من libxposed غير موجودة في API 102 — البديل المعتاد
  (`fw.hook`/`hookAll`/`ReflectCall`) + `onPackageReady`.

---

# ملحق v5.3 — المسار الصحيح كان Legacy (تشخيص v5.2)

## 57) تصحيح التشخيص: ليست نسخة قديمة بل مسار آخر

سطرا `(read-only)` و`handler hooked: S7.g` اللذان ظنا أنهما من
`FlutterChannelHook.java` القديم — هما في الحقيقة من `LegacyBridge`
(شيم de.robv عبر `assets/xposed_init`). أي أن الباتشر عند المستخدم
يوجّه عبر المسار الـ legacy، ومرآة Flutter فيه كانت أضعف: ربط
`success()` لكل فئة (بلا سياق دالة) وبلا ترقيع String-JSON للردود.

## 58) ما تغير في v5.3

1. **Legacy mirror بنفس مستوى الحديث**: ربط `success()` لكل
   (فئة#دالة) + ترقيع String-JSON وMap عبر نفس دوال القناة
   (patchChannelString/patchChannelMap) + نفس علامات السجل
   (`installing v5.3` / `capture handler:` / `←` / `★ patched:`)
   في المسارين — التشخيص موحد.
2. **الحديث**: بوابة (فئة#دالة) فعلية (كانت تُحجب خطأً ببوابة الفئة)،
   +قواعد `credit`/`membership_status`/`isPremium`/`premium`،
   +تعمق خرائط متداخلة ومفاتيح remaining/available.
3. بناء نظيف كامل (`clean`) + تحقق من سلاسل الـ dex قبل التسليم.
