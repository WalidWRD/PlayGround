# SubscriptionAnalyzer v6.0 – وثيقة التحليل الشامل

> نتيجة التحليل النهائية — تُسلَّم مع الموديول **لعدم التحليل كل مرة**.

## 0) بطاقة الموديول

| البند | القيمة |
|---|---|
| الاسم | SubscriptionAnalyzer |
| الحزمة | `com.apphook.log` (نفس AppHook — ثبّت واحداً فقط) |
| الإصدار | **6.0.0** (code 600) |
| أندرويد | 8.0 (API 26) → 15 (API 35)، libxposed 101→102 + shim 82 |
| الوضع | **ANALYZE** افتراضياً (صفر تعديل) / PATCH اختياري |
| التقرير | `/sdcard/SubscriptionAnalyzer/report_*.json` (احتياطي: `/data/data/<pkg>/cache/...`) |

## 1. نموذج التحقق – تصنيف

| النموذج | آلية العمل | مصدر الحقيقة | قابلية Xposed |
|---------|-----------|--------------|---------------|
| **محلي (Local)** | isPremium/isVip في Java/Kotlin | APK | ✅ عالية |
| **خادمي (Server)** | JWT/token في HTTP response | API | ✅ متوسط |
| **Google Play Billing** | Purchase.getPurchaseState() | PBL SDK | ✅ عالية |
| **RevenueCat** | CustomerInfo.getEntitlements() | SDK + Server | ✅ عالية |
| **Flutter + WebView** | JS Bridge + NUXT state | WebView | ✅ متوسط |
| **Flutter Native** | Dart:io HTTP (AOT) | libapp.so | ❌ منخفض |
| **Hybrid** | كل ما سبق | متعدد | ⚠️ جزئي |

## 2. جدول Features – 12 نقطة اعتراض

| # | Feature | الفئة / الدالة | المُعدّات | طريقة الهوك | الهشاشة | أول ما يفشل |
|---|---------|---------------|-----------|-------------|---------|---------------|
| 1 | Billing PurchaseState | `Purchase.getPurchaseState` | `() → int` | إرجاع 0 (PATCH) | مستقرة (PBL v5-v8) | PBL v9 |
| 2 | Billing isAcknowledged | نفس الفئة `.isAcknowledged` | `() → boolean` | إرجاع true | مستقرة | لا شيء |
| 3 | Billing isReady | `BillingClient.isReady` | `() → boolean` | إرجاع true | مستقرة | لا شيء |
| 4 | RevenueCat Entitlement | `EntitlementInfo.isActive` | `() → boolean` | إرجاع true | مستقرة | SDK v9 |
| 5 | RevenueCat CustomerInfo | `CustomerInfo.getEntitlements` | `() → Entitlements` | مراقبة (R8) | R8-sensitive | SDK v9 |
| 6 | Flutter MethodChannel Result | `...MethodChannel$Result.success` | `(Object)` | ترقيع String/Map | مستقرة | Flutter v4 |
| 7 | WebView fetch | JS `window.fetch` (حقن شرائح loadUrl) | - | override | مستقرة | Nuxt v4 |
| 8 | WebView XHR | JS `XMLHttpRequest` | - | override | مستقرة | لا شيء |
| 9 | WebView DOM | `querySelectorAll` + MutationObserver | - | fixDom (PATCH) | مستقرة | Layout |
| 10 | Prefs Boolean | `SharedPreferencesImpl.getBoolean` | `(String,boolean)` | إرجاع true | مستقرة | لا شيء |
| 11 | Prefs String (JSON) | `.getString` | `(String,String)` | regex (65 قاعدة) | مستقرة | لا شيء |
| 12 | OkHttp Response | `RealInterceptorChain.proceed` | `(Request)` | تسجيل فقط | R8-sensitive | OkHttp v5 |

## 3. نموذج القياس

كل نقطة تسجل **4 عناصر**: اسم الحدث + عدد الوسائط + الأصلية + المُرقّعة (★ عند التغيير).

## 4. حالات الفتح المُصنَّفة

| الكود | الحالة | الوصف |
|-------|--------|-------|
| F000 | `LOCAL_FLAG` | boolean محلي |
| F001 | `PLAN_STRING` | حقل plan |
| F002 | `BALANCE` | الرصيد |
| F003 | `BILLING_STATE` | حالة الفوترة |
| F004 | `CHANNEL_RESULT` | Flutter channel |
| F005 | `JSON_FIELD` | JSON |
| F006 | `PREFS` | SharedPreferences |
| F007 | `WEB_RESPONSE` | استجابة WebView |

## 5. مخاطر الكسر

- **P0**: `RealInterceptorChain.proceed` (internal) / دوال premium (R8) / توقيع MethodChannel.
- **P1**: PBL v9 / `SharedPreferencesImpl` / RevenueCat v9.
- **P2**: `onPageFinished` / `onConsoleMessage` / `JSONObject` (مستقرة).

## 6. الأدلة (سجلات فعلية v4.x–v5.x)

| الدليل | المصدر |
|--------|--------|
| WebView console يعمل | `[WebView-JS] [AppHook] v4.2 loaded` |
| evaluateJavascript صامت الفشل على InAppWebView | `JS patch injected` بلا callback |
| loadUrl يعمل | `TEST_JS_EXECUTED` بعد `TEST_JS sent` |
| InAppWebViewChromeClient حقيقي | `Real ChromeClient: ...` |
| handlers لا تُستدعى للاشتراك | `capture handler: S7.g` ×80 بلا `← method` |
| MutationObserver يعمل | `DOM fix: 0` → `DOM fix: 897` |
| Prefs-JSON هو مخزن Flutter | `prefs_json:flutter.*` (v5.4) |

## 7. التشغيل

- **ANALYZE**: ثبّت → فعّل → تنقّل → اقرأ `report_*.json` (يُكتب كل 100 حدث).
- **PATCH**: غيّر `Config.PATCH_MODE=true` (أو طبقة واحدة) → أعد البناء.
