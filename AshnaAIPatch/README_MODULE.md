# دليل التطوير والصيانة — AshnaAIPatch

## البنية (كل ملف له مسؤولية واحدة)
- HookEntry.java — نقطة الدخول + عزل الأعطال + طباعة الوصف المدمج في سجل التشغيل
- ModuleInfo.java — رقم الإصدار + الوصف + سجل التغييرات (حدّثه مع كل نسخة)
- Config.java — **النقطة الوحيدة للصيانة الدورية**: الكلمات المفتاحية + نطاقات الإعلانات + المفاتيح
- util/LogUtil.java — تسجيل لا يرمي استثناء
- util/ReflectionHelper.java — بحث بالانعكاس عبر lpparam.classLoader + تحمّل غياب الكلاسات
- hooks/UpdateBlocker.java — Play In-App Updates + expo-updates + فلترة حوارات التحديث
- hooks/DialogSuppressor.java — كتم التحذيرات (Dialog + Toast) مع القائمة البيضاء
- hooks/AdBlocker.java — اعتراض WebViewClient + تعطيل loadAd/showAd بالانعكاس
- hooks/WebViewPatcher.java — حقن JS لإخفاء مودالات التحديث/الإعلانات
- hooks/DialogFilter.java — مرشّح الحوارات الموحّد (هوك Dialog.show واحد يصنّف: تحديث/تحذير/تجاهل)
- PrefsManager.java — مفاتيح حيّة عبر XSharedPreferences مع عودة آمنة لقيم Config
- SettingsActivity.java — شاشة تحكم (لانشر) بأربع مفاتيح دون إعادة بناء

## إضافة كلمة/نطاق جديد
1. عدّل Config.java فقط (UPDATE_KEYWORDS / WARNING_KEYWORDS / AD_HOST_BLOCKLIST)
2. حدّث CHANGELOG في ModuleInfo.java + xposed_description في res/values/strings.xml
3. أعد البناء: ./gradlew :app:assembleDebug (أو assembleRelease + توقيع)

## قواعد السلامة (لا تكسرها)
- أي كود هوك جديد يجب أن يكون داخل try/catch ولا يرمي أبداً — الفشل = ترك السلوك الأصلي
- ممنوع إضافة نطاقات api.ashna.ai أو مسارات user/otp/session/auth/plan/payment/connectors لأي قائمة حجب
- ممنوع لمس PairIP / LicenseActivity / CHECK_LICENSE / منطق الاشتراك والدفع — خارج النطاق عمداً
- أي Dialog جديد يُغلَق بعد العرض (afterHookedMethod + dismiss) لا قبله

## البناء والتوقيع
- debug (للتجربة وLSPatch): ./gradlew :app:assembleDebug -> app-debug.apk (موقّع تلقائياً)
- release: ./gradlew :app:assembleRelease ثم وقّع بـ apksigner مع keystore خاص بك
- التوافق: minSdk 24 (مطابق للتطبيق) / Xposed API 82 / يعمل LSPosed + LSPatch/NPatch/HKP

## التشخيص
- السجل يظهر في LSPosed Manager -> سجل + logcat بتاغ AshnaAIPatch
- رسائل البداية الإلزامية: DESCRIPTION ثم CHANGELOG ثم All hooks installed safely
- Ad request blocked: <host> تعني حجب إعلان فعلي — راقبها لتوسيع القائمة

## سجل النسخ
- v1.4.0: بطاقة اشتراك محلية (تذكير يدوي + عدّاد أيام + زر فتح رسمي — لا قراءة/تعديل للخادم)
- v1.3.0: تشخيص شبكي قراءة فقط (opt-in + تعقيم + تجاهل الحساس) + تسجيل أخطاء الويب
- v1.2.0: قوائم مستخدم مخصصة (نطاقات + كلمات من الشاشة) + اعتراض WebView.loadUrl + debounce للسجل مع عدّادات + سطر حالة عند الإقلاع + شاشة v2
- v1.1.0: مفاتيح حيّة + مرشّح موحّد + Toast لأندرويد 12+ + JS مقاوم لـ SPA + كل حملات Play + توسيع الكلمات/النطاقات + LRU
- v1.0.0: الإصدار الأول (Config ثابت + حقن مرة واحدة + هوكا Dialog مزدوجان)
