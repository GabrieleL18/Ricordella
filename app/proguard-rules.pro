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
