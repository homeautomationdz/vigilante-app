# Vigilante — نظام إدارة المتطوعين

تطبيق Android يعمل بالكامل دون إنترنت (Offline-First) لإدارة بيانات المتطوعين،
مبني وفق وثيقة المواصفات SRS v1.0.

## البنية التقنية

- **Kotlin 2.0 + Jetpack Compose (Material 3)** — واجهات عربية RTL بالكامل
- **MVVM + Repository Pattern + Hilt (DI)** — منطق الأعمال معزول عن الواجهات ومصدر البيانات
- **Room (SQLite)** كمخزن تشغيلي يوفر Atomic Transactions حقيقية وبحثًا فوريًا حتى مع +100,000 سجل
- **Volunteers_Master.xlsx** هو صيغة النقل والتبادل الرسمية (fastexcel — قراءة/كتابة تدفقية Streaming)
- **ZXing** لتوليد ومسح QR (يحوي Volunteer ID فقط)
- **BCrypt** لتشفير كلمات المرور — لا تُخزن أي كلمة مرور كنص عادي

## هيكل المشروع

```
app/src/main/java/com/vigilante/app/
├── core/          المعرفات (VOL-/ADM-/ATT-/LOG-/BKP-)، المجلدات، التحقق من البيانات
├── security/      BCrypt، الجلسة، الصلاحيات (RBAC)
├── data/
│   ├── local/     Room: الكيانات، DAOs، بانى الفلاتر المركبة
│   ├── excel/     التصدير، الاستيراد مع تقرير الأخطاء، محرك الدمج (سياسة ch.27)، النسخ الاحتياطي
│   ├── files/     الصور (ضغط + إزالة Metadata) وملفات QR
│   └── repository/ منطق الأعمال: المتطوعون، الحضور، المشرفون، الإحصائيات، فاحص السلامة
└── ui/            الشاشات (Compose): الدخول، الرئيسية، المتطوعون، الحضور، الإحصائيات،
                   الأرشيف، المشرفون، الإعدادات، النسخ الاحتياطي، سلة المحذوفات، صحة النظام
```

## البناء

1. افتح المشروع في **Android Studio** (Ladybug أو أحدث).
2. سيقوم Gradle بتنزيل التبعيات تلقائيًا عند أول مزامنة.
3. `Build → Build APK` أو من الطرفية:

```
./gradlew assembleRelease
```

- minSdk 26 (Android 8.0) — targetSdk 35

## ملفات البيانات على الجهاز

```
Android/data/com.vigilante.app/files/Vigilante/
├── Database/  Volunteers_Master.xlsx
├── Photos/    VOL-000001.jpg
├── QR/        VOL-000001.png
├── Backup/    Backup_YYYY-MM-DD_HH-MM.xlsx
├── Export/    Export_YYYY-MM-DD.xlsx
├── Import/    Volunteers_Import.xlsx
└── Logs/
```

`Template.xlsx` المرفق في جذر المشروع هو النموذج الفارغ الرسمي للاستيراد.

## المبادئ غير القابلة للتفاوض (من الوثيقة)

- لا حذف مباشر أبدًا: أرشيف → سلة محذوفات 30 يومًا → حذف نهائي
- نسخة احتياطية تلقائية قبل أي استيراد أو استعادة أو حذف نهائي
- كل عملية تُسجل في Audit Log داخل نفس الـ Transaction
- الأعمدة في Excel تُقرأ بالاسم لا بالترتيب، والملف يُفحص كاملًا قبل الاستيراد
- في الدمج: صلاحية المستخدم أهم من وقت التعديل
