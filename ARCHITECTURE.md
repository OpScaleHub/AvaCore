# معماری موتور متن‌به‌گفتار آوا (AvaCore TTS Architecture)

این سند جزئیات فنی و معماری پروژه **AvaCore** را برای توسعه‌های آتی تشریح می‌کند. این پروژه به گونه‌ای طراحی شده که ۱۰۰٪ آفلاین و بدون وابستگی به مخازن آنلاین (Maven) در زمان بیلد، عمل کند.

## ۱. ساختار کامپوننت‌های محلی (Local Assets)
پروژه برای اجرا به ۵ جزء فنی حیاتی نیاز دارد که توسط اسکریپت `download_assets.sh` تامین می‌شوند:

| نام فایل | مسیر در پروژه | توضیحات فنی |
| :--- | :--- | :--- |
| **Sherpa-ONNX Engine** | `app/libs/sherpa-onnx.aar` | نسخه ۱.۱۰.۴۱ (شامل کتابخانه‌های JNI و Wrapper کاتلین). |
| **Neural Voice Model** | `app/src/main/assets/tts/persian_model.onnx` | مدل VITS (Piper) با متادیتای `sample_rate=22050`. |
| **Model Config** | `app/src/main/assets/tts/persian_model.onnx.json` | تنظیمات واج‌شناسی و نگاشت کاراکترها. |
| **Phoneme Tokens** | `app/src/main/assets/tts/tokens.txt` | لیست توکن‌های مورد نیاز برای موتور Sherpa. |
| **eSpeak-NG Data** | `app/src/main/assets/tts/espeak-ng-data/` | پایگاه داده زبانی برای تبدیل متن به واج (G2P). |

## ۲. پیاده‌سازی سرویس (TTS Service Implementation)
سرویس `AvaTtsService` از کلاس استاندارد `TextToSpeechService` اندروید ارث‌بری می‌کند.

### فرآیند مقداردهی (Initialization):
- **Lazy Loading**: مدل ۶۱ مگابایتی در یک ترد (Thread) پس‌زمینه بارگذاری می‌شود تا باعث بروز ANR یا Timeout در تنظیمات سیستم نشود.
- **Asset Migration**: به دلیل محدودیت کتابخانه‌های Native در دسترسی مستقیم به Assets، فایل‌ها در اولین اجرا به `filesDir` اپلیکیشن کپی می‌شوند.

### مدیریت پخش (Audio Streaming):
- **Audio Chunking**: داده‌های صوتی تولید شده به تکه‌های **8KB** تقسیم می‌شوند تا با محدودیت‌های سخت‌گیرانه برخی برندها (مانند Oppo/OnePlus) سازگار باشند و خطای `buffer too large` رخ ندهد.
- **Interruption Handling**: استفاده از `AtomicBoolean` برای پایش وضعیت توقف پخش توسط کاربر و آزادسازی سریع منابع.

## ۳. نکات حیاتی بیلد و استقرار (Deployment Notes)

### سازگاری با اندروید ۱۵ (16KB Page Size):
در `app/build.gradle.kts` تنظیم `useLegacyPackaging = true` قرار داده شده است. این کار باعث می‌شود کتابخانه‌های نیتیو در زمان نصب استخراج شده و با Page Size دستگاه (حتی در معماری‌های جدید ARM) هم‌تراز شوند.

### مجوزهای خاص (Oplus/Background Execution):
برای پایداری سرویس در پس‌زمینه روی دستگاه‌های چینی، کاربر باید در مسیر زیر تنظیمات را دستی انجام دهد:
`Settings > Battery > App Battery Management > AvaCore > Allow background activity`

## ۴. راهنمای توسعه آتی
- **تغییر مدل**: اگر مدل جدیدی جایگزین شد، حتماً از وجود متادیتای `sample_rate` درون فایل `.onnx` مطمئن شوید.
- **به‌روزرسانی موتور**: در صورت آپدیت فایل `.aar` در پوشه `libs` ممکن است نام پارامترهای کلاس‌های `OfflineTtsConfig` تغییر کند که باید در کد `AvaTtsService` اصلاح شود.

---
**توسعه‌دهنده:** AliRezaTaleghani
**تاریخ آخرین ویرایش:** فوریه ۲۰۲۶
