# The AAR has no consumer keeps; native code looks up Java types and callbacks by name.
# Preserve this JNI boundary until a complete native registration audit supports narrowing.
-keep class net.sf.sevenzipjbinding.** { *; }
