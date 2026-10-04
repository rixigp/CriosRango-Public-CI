# Crashlytics needs source/line metadata to deobfuscate R8 stack traces.
-keepattributes SourceFile,LineNumberTable

# SLF4J's optional binding is not packaged by the Android application.
# R8 sees the API's optional LoggerFactory binding reference but no runtime
# binding is required by the app.
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Gson persists the local cart snapshot through reflection. Preserve field
# names for the concrete object graph serialized by ShopRepository.
-keepclassmembers,allowoptimization class es.criosrango.app.CartLine { <fields>; }
-keepclassmembers,allowoptimization class es.criosrango.app.CartLineTotals { <fields>; }
-keepclassmembers,allowoptimization class es.criosrango.app.CartVariation { <fields>; }
-keepclassmembers,allowoptimization class es.criosrango.app.QuantityLimits { <fields>; }
-keepclassmembers,allowoptimization class es.criosrango.app.ProductPrices { <fields>; }
-keepclassmembers,allowoptimization class es.criosrango.app.ProductImage { <fields>; }
