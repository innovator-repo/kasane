# JNI entry points into liblumecore.so
-keep class app.kasane.LumeCore { native <methods>; }
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class app.kasane.core.**$$serializer { *; }
-keepclassmembers class app.kasane.core.** { *** Companion; kotlinx.serialization.KSerializer serializer(...); }
