# kotlinx.serialization: mantiene i serializer generati per le classi del backup e delle route.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.ricordella.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.ricordella.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ML Kit (scanner di codici, lettura da foto): i componenti si caricano per riflessione dal costruttore vuoto,
# che R8 in modalità completa toglierebbe: senza, SharedPrefManager manca e la fotocamera va in crash (solo in release).
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
-keep class com.google.mlkit.common.internal.MlKitInitProvider { <init>(); }
