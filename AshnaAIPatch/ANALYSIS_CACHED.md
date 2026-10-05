# ملف التحليل والنتائج المحفوظ — AshnaAI 1.0.6
# (يُغني عن إعادة التحليل في كل مرة — اعتمد عليه للصيانة)

## 1) بيانات الحزمة (مؤكدة من التحليل الثابت)
- اسم التطبيق الظاهر: AshnaAI
- اسم الحزمة: ai.ashna.mobile
- الإصدار المحلَّل: 1.0.6 — versionCode 10006
- نوع الملف: ‎.apks مقسّمة (base.apk ~13.8MB + split config/arch/lang/density ~14.8MB) — الإجمالي ~28.9MB
- الإطار: Expo SDK 54 / React Native / Expo Router — محرك Hermes Bytecode v96
- النظام: minSdk 24 / targetSdk 36 / compileSdk 36 — سلسلة البناء Kotlin 2.1.20 / Gradle 8.14.3 / Java 17
- الشبكة: usesCleartextTraffic=false (HTTPS فقط) / allowBackup=true / لا Network Security Config مخصص
- النطاقات: تطبيق الويب https://app.ashna.ai — الـ API https://api.ashna.ai — الموقع https://www.ashna.ai — مخطط Deep Link ‏ashna://
- مسارات API المستخرجة (مصادقة/اشتراك — لا تُعترَض): api/user, api/otp, api/session, api/auth, api/plan, api/payment-history, api/connectors
- الأذونات: INTERNET / RECORD_AUDIO / MODIFY_AUDIO_SETTINGS / VIBRATE / READ+WRITE_EXTERNAL_STORAGE / SYSTEM_ALERT_WINDOW (معلَن بلا استخدام فعلي — غالباً تلقائي من مكوّن) / com.android.vending.CHECK_LICENSE (مرتبط بـ PairIP)
- المكوّنات: MainActivity + com.pairip.licensecheck.LicenseActivity — لا Services ولا Receivers معلنة سوى androidx الداخلية — مزوّدو RNCWebViewFileProvider و expo filesystem/sharing
- المكوّن المحوري: AshnaWebShell يعرض https://app.ashna.ai داخل WebView (javaScriptEnabled / domStorage / injectedJavaScript / onShouldStartLoadWithRequest / onMessage / onOpenWindow / onNavigationStateChange / onError)

## 2) نتيجة التحديث الإجباري/الفوري: غير موجود في الحزمة
- لا forceUpdate / mandatoryUpdate / updateRequired / minVersion / latestVersion كمنطق تطبيقي (المطابقة الوحيدة enqueueForceUpdate دالة React داخلية لتحديث الواجهة).
- لا روابط متجر (play.google.com / market://) ولا كلاس com.google.android.play.core.appupdate (لا Play In-App Updates).
- وحدة OTA معطّلة صراحة: expo.modules.updates.ENABLED=false.
- الاستنتاج: أي حوار تحديث يظهر للمستخدم مصدره طبقة الويب (app.ashna.ai) أو نسخة أخرى غير المرفوعة — يُعالَج على مستوى العرض (Dialog filter + حقن JS) لا على مستوى الحزمة.
- تحذير PairIP: أي تعديل بايتكود Hermes + إعادة توقيع يُبطل فحص الترخيص — لذلك الموديول يعمل Runtime فقط بلا لمس الحزمة.

## 3) نتيجة الإعلانات: لا يوجد أي كود إعلاني أصلي
- غياب كامل: AdMob / AppLovin / Unity Ads / IronSource / Facebook Audience Network — لا ca-app-pub / adUnitId — لا loadAd / showAd / Interstitial / Rewarded — لا أذونات AD_ID / AdServices — لا سكربتات إعلانات في WebView.
- كلمات Segment/banner/Overlay المطابَقة سابقاً هي أسماء داخلية (FlatList/Navigation) وليست إعلانات.
- الاستنتاج: أي إعلان يُرى فعلياً مصدره محتوى الويب أو إضافة خارجية أو نسخة مختلفة — يُحجَب على مستوى الشبكة/WebView (اعتراض النطاقات + حقن JS) + تعطيل loadAd/showAd بالانعكاس إن ظهرت في إصدار آخر.
- نطاقات الحجب المعتمدة: doubleclick.net / googlesyndication.com / googleadservices.com / admob / applovin.com / unityads / ironsrc / facebook audience — مع حظر صارم لإضافة api.ashna.ai للقائمة.

## 4) قرارات بناء الموديول (لماذا صُمم هكذا)
- استهداف الحزمة ai.ashna.mobile فقط، مع تحمّل غياب الكلاسات (توافق أعلى/أقل).
- lpparam.classLoader + findClassIfExists + hookAllMethods — لا أسماء ثابتة ملزمة، يعمل مع multidex والحزم المضغوطة/المحمية.
- Dialog.show يُخطَف بعد العرض ثم يُغلَق فوراً عند مطابقة الكلمات — لا يُمنَع العرض مسبقاً حتى لا يكسر دورة حياة النافذة (منع الكراشات).
- قائمة بيضاء (otp/login/password/payment/plan) تمنع كسر الدخول والتحقق والدفع.
- كل مجموعة هوكات معزولة try/catch — فشل مجموعة لا يوقف البقية ولا يرمي استثناء أبداً.
- لا اعتراض لمسارات المصادقة/الاشتراك/الدفع ولا لمس لفحص الترخيص PairIP/LicenseActivity — خارج النطاق عمداً.
- متوافق LSPosed (روت) و LSPatch/NPatch/HKP (بدون روت عبر باتش الحزمة الأصلية مع الموديول).

## 5) إزالة المانيفست (لإعادة التغليف فقط — ليست Runtime)
- AshnaAI 1.0.6 لا تحتوي عناصر إعلانية في المانيفست أصلاً — لا شيء يُزال حالياً.
- عند ظهور عناصر في إصدار آخر: راجع MANIFEST_PATCH_GUIDE.md (إزالة AD_ID والـ AdActivity/Service/Receiver + إعادة التوقيع عبر LSPatch/NPatch).

## 6) سجل المصادر
- AshnaAI_1.0.6.apks (الملف المرفوع) / AshnaAI_1.0.6_Analysis_Report_AR.pdf / Xposed_AshnaAI_Technical_Analysis.pdf / AshnaAI_1.0.6_-_.pdf — تحليل ثابت دون تشغيل، Confidence: تحديث (عالية) / إعلانات (مؤكدة الغياب) / مصادقة (متوسطة — مستنتجة من السلاسل).
- للتشخيص المستقبلي: mitmproxy على جهاز معزول بموافقة مالك التطبيق لتحديد سكربت/نقطة نهاية حوار التحديث بدقة ثم حجبه.

آخر تحديث للملف: مع إصدار الموديول v1.0.0.
