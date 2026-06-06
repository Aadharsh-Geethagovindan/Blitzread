# Preserve stack trace line numbers (helpful if a crash report comes in)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Readium ───────────────────────────────────────────────────────────────────
# Readium uses reflection and its own keep rules, but belt-and-suspenders:
-keep class org.readium.** { *; }
-dontwarn org.readium.**

# ── PDFBox Android ────────────────────────────────────────────────────────────
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# ── Kotlinx Coroutines ────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ── Kotlin serialization / reflection ────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# ── DataStore ─────────────────────────────────────────────────────────────────
-keep class androidx.datastore.** { *; }

# ── Coil ──────────────────────────────────────────────────────────────────────
-dontwarn coil.**