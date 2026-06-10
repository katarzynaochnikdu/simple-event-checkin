# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK's proguard-android-optimize.txt

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Runtime annotations — wymagane przez Moshi/Retrofit (m.in. @JsonClass, @Json,
# adnotacje parametrów Retrofit). NIE usuwać.
-keepattributes *Annotation*

# WO-MOB-034 (F2A-007): Moshi codegen DTO.
# Wszystkie DTO sieciowe mają @JsonClass(generateAdapter=true) (ksp moshi-kotlin-codegen) —
# parsowanie jest BEZ refleksji, generowane adaptery R8 utrzymuje przez normalną
# osiągalność. KotlinJsonAdapterFactory (NetworkModule) to fallback refleksyjny dla DTO
# BEZ wygenerowanego adaptera — keep zawężony do pakietu DTO chroni ten przypadek,
# a jednocześnie pozwala minifikować/obfuskować resztę core.network (serwis, DI,
# interceptory). core.model = modele domenowe budowane przez core-mappers, NIGDY
# deserializowane przez Moshi → keep usunięty (zbędny, ułatwiał reverse-engineering).
-keep class pl.medidesk.mobile.core.network.dto.** { *; }

# Room: kod jest generowany przez KSP w czasie kompilacji (zero refleksji na encjach/DAO).
# Utrzymujemy tylko abstrakcyjną podklasę RoomDatabase, do której odwołuje się
# wygenerowany *_Impl. Keepy @Entity/@Dao usunięte (KSP ich nie wymaga; ujawniały
# nazwy pól PII encji w APK).
-keep class * extends androidx.room.RoomDatabase

# Keep PostHog
-keep class com.posthog.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Coil
-dontwarn coil.**
