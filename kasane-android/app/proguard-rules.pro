# JNI entry points into liblumecore.so
-keep class app.kasane.LumeCore { native <methods>; }
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class app.kasane.core.**$$serializer { *; }
-keepclassmembers class app.kasane.core.** { *** Companion; kotlinx.serialization.KSerializer serializer(...); }
# Widget actions are created by class name.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
# Glance remembers each widget by class name, and R8 renames classes differently on every build.
# After an update a widget slot could resolve to another widget's class, so every widget keeps its name.
-keepnames class * extends androidx.glance.appwidget.GlanceAppWidget
-keepnames class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { <init>(); }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { <init>(); }
