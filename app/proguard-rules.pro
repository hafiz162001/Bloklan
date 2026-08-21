# Proguard rules for Bloklan
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
