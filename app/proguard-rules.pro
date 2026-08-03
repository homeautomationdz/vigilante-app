# Vigilante ProGuard rules
-keep class com.vigilante.app.data.local.entity.** { *; }
-keep class org.dhatim.fastexcel.** { *; }
-dontwarn org.dhatim.fastexcel.**
# javax.xml.stream (StAX) is unavailable on Android — nothing at runtime uses it
-keep class com.google.zxing.** { *; }

# Office encryption is implemented in-house (data/excel/crypto) — no Apache POI
# at runtime. Keep the crypto layer intact so reflection-free binary layout code
# is never reshaped by R8.
-keep class com.vigilante.app.data.excel.crypto.** { *; }
