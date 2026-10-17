# JNI entry points into liblumecore.so
-keep class app.kasane.LumeCore { native <methods>; }
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class app.kasane.core.**$$serializer { *; }
-keepclassmembers class app.kasane.core.** { *** Companion; kotlinx.serialization.KSerializer serializer(...); }
# Widget actions are created by class name.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
