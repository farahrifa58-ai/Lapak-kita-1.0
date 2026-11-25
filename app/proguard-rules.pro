# ProGuard / R8 Optimization Rules for Lapak Kita

# Keep Room entities, DAOs, and database implementations
-keep class com.example.data.model.** { *; }
-keep class com.example.data.database.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# Keep Firebase & Firestore data payloads
-keep class com.example.data.firebase.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,*Annotation*

# Keep Google Credential Manager & Google Identity Services
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }

# Keep Kotlin Coroutines & Compose runtime metadata cleanly
-dontwarn kotlinx.coroutines.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
