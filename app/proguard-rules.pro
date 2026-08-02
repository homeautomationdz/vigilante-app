# Vigilante ProGuard rules
-keep class com.vigilante.app.data.local.entity.** { *; }
-keep class org.dhatim.fastexcel.** { *; }
-dontwarn org.dhatim.fastexcel.**
-dontwarn javax.xml.stream.**
-keep class com.google.zxing.** { *; }

# Apache POI (used only for Office-native encryption of exports)
-keep class org.apache.poi.poifs.** { *; }
-keep class org.apache.poi.hssf.record.crypto.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.apache.commons.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn org.osgi.**
-dontwarn aQute.bnd.annotation.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.tukaani.xz.**
