# R8 rules for release builds.
#
# Most of what the app needs is covered by the libraries' own rules (Compose, WorkManager,
# DataStore, kotlinx.serialization). Components declared in the manifest (activity, receivers,
# service, workers) are kept automatically.

# State and settings are stored as JSON via kotlinx.serialization: keep the generated serializers.
-keepclassmembers @kotlinx.serialization.Serializable class com.silentcalendrer.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.silentcalendrer.**$$serializer { *; }
