# Crashlytics needs source/line metadata to deobfuscate R8 stack traces.
-keepattributes SourceFile,LineNumberTable

# SLF4J 1.x treats a missing binding as an optional no-op backend; the Android
# app does not package an SLF4J binding, so this optional class is not runtime-required.
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Gson persists the local cart snapshot through reflection. Preserve the
# runtime field names for the concrete object graph used by that snapshot.
-keepclassmembers,allowoptimization class es.criosrango.app.CartLine { <fields>; }
-keepclassmembers,allowoptimization class es.criosrango.app.CartLineTotals { <fields>; }
-keepclassmembers,allowoptimization class es.criosrango.app.CartVariation { <fields>; }
-keepclassmembers,allowoptimization class es.criosrango.app.ProductPrices { <fields>; }
-keepclassmembers,allowoptimization class es.criosrango.app.ProductImage { <fields>; }
-keepclassmembers,allowoptimization class es.criosrango.app.QuantityLimits { <fields>; }
