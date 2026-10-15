# AppHook v4.6

## الميزات
- اعتراض اشتراك WebView (Nuxt.js) في Genspark
- تعديل JSON تلقائياً (اشتراك + رصيد + حصص)
- قناة log مزدوجة (console + bridge) بفلتر `[AppHook]` موحد
- صفر كراش مع ExceptionMode.PROTECTIVE

## البناء
```bash
gradle :app:assembleRelease --project-dir AppHook
```

الناتج: `AppHook/app/build/outputs/apk/release/app-release-unsigned.apk`
(للدمج عبر LSPatch/HKP — الباتشر يعيد التوقيع)

## التثبيت

1. ثبّت `AppHook-v4.6.apk` (موقّع — تثبيت مباشر)
2. فعّله في LSPatch/HKP (أو الدمج) بالنطاق `ai.mainfunc.genspark`
3. أعد تشغيل Genspark

## التحقق

افتح السجل وابحث عن:

```
[WebView-JS] [AppHook] v4.6 loaded
[WebView-JS] [AppHook] PATCHED[/api/payment/get_credit_balance]
```
