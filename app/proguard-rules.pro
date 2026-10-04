# Crashlytics needs source/line metadata to deobfuscate R8 stack traces.
-keepattributes SourceFile,LineNumberTable

# Gson persists the local cart snapshot through reflection. Preserve the
# runtime field names for the concrete object graph used by that snapshot.
-keepclassmembers,allowoptimization class es.criosrango.app.CartLine { <fields>; }
-keepclassmembers,allowoptimization class es.criosrango.app.CartLineTotals { <fields>; }
-keepclassmembers,allowoptimization class es.criosrango.app.CartVariation { <fields>; }
-keepclassmembers,allowoptimization class es.criosrango.app.ProductPrices { <fields>; }
-keepclassmembers,allowoptimization class es.criosrango.app.ProductImage { <fields>; }
-keepclassmembers,allowoptimization class es.criosrango.app.QuantityLimits { <fields>; }
