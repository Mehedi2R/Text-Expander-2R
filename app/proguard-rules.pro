# ===================================================================
# Optimization & Shrinking Configuration for Text Expander 2R
# ===================================================================

# Multi-pass bytecode optimization & R8 full mode dead-code elimination
-optimizationpasses 5
-allowaccessmodification
-overloadaggressively
-mergeinterfacesaggressively
-repackageclasses ""

# -------------------------------------------------------------------
# Strip debug, verbose, info, and warning logs in release builds to
# eliminate string allocations and dex string table entries.
# -------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

# -------------------------------------------------------------------
# Android Components (Never rename activities or services)
# -------------------------------------------------------------------
-keep public class com.textexpander.app.MainActivity
-keep public class com.textexpander.app.AddEditShortcutActivity
-keep public class com.textexpander.app.OnboardingActivity
-keep public class com.textexpander.app.TextExpanderApp
-keep public class com.textexpander.app.service.** { *; }
-keep public class com.textexpander.app.receiver.** { *; }
-keep public class com.textexpander.app.worker.** { *; }

# WorkManager Proguard Rules
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# -------------------------------------------------------------------
# Room Database (Keep Entities and DAOs)
# -------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep class com.textexpander.app.data.** { *; }
-keepclassmembers class com.textexpander.app.data.** { *; }
-dontwarn androidx.room.paging.**

# -------------------------------------------------------------------
# ViewModel and ViewHolders (Constructor reflection safety)
# -------------------------------------------------------------------
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keepclassmembers class * extends androidx.recyclerview.widget.RecyclerView$ViewHolder {
    public <init>(...);
}

# -------------------------------------------------------------------
# AndroidX DocumentFile (SAF backup and file writing)
# -------------------------------------------------------------------
-keep class androidx.documentfile.provider.** { *; }

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
