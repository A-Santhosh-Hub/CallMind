# Keep rules for the optimised (release) build.
# Hilt, Room, Compose and WorkManager ship their own consumer rules; only what CallMind itself
# creates by class name needs listing here.

# WorkManager builds the reminder worker from its stored class name.
-keep class com.example.callmind.data.repository.ReminderWorker { <init>(...); }

# Started by Android from the manifest (call screening) and looked up by Hilt entry points.
-keep class com.example.callmind.core.blocking.CallBlockingService
-keep interface com.example.callmind.core.blocking.CallBlockingEntryPoint
-keep interface com.example.callmind.WarmUpEntryPoint

# Room stores enum names as text (converters call valueOf/name).
-keepclassmembers enum com.example.callmind.** { *; }

# Looked up by Hilt entry point when a call recording is saved.
-keep interface com.example.callmind.core.phone.RecordingEntryPoint
