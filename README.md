# سنابل — تطبيق توزيع الطحين (Android + PWA)

تم تحديث المستودع ليبقى مشروع Android بالحزمة `com.tahen.app` مع نسخة الواجهة الحديثة وWorkflow للبناء عبر GitHub Actions.

## البناء المباشر على GitHub

أي Push إلى `main` يشغّل Workflow **Build Android APK** تلقائياً، ويبني نسخة Debug APK ثم يرفعها كـ Artifact باسم `flour-distribution-apk`.

## ملاحظات

- الحزمة Android: `com.tahen.app`.
- لا تضع keystore أو كلمات المرور داخل المستودع.
- عند استخدام Google Sign-In يجب تسجيل SHA-1 لشهادة التوقيع الفعلية في Google Cloud Console مع الحزمة `com.tahen.app`.
- بيانات العوائل والنسخ الاحتياطية لا ينبغي رفعها إلى مستودع عام.
