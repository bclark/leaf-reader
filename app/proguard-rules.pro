# JNI signatures and runtime metadata are supplied by proguard-android-optimize.txt.
# SevenZip has a separate Full-only JNI contract.

# SMBJ discovers these callbacks through runtime annotations, not direct calls.
# MBassador invokes Method objects, so names may still be obfuscated.
-keepclassmembers,allowobfuscation,includedescriptorclasses class com.hierynomus.smbj.** {
    @net.engio.mbassy.listener.Handler <methods>;
}

# Strip info/debug/verbose logging from release builds (errors and warnings are kept).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}


# MBassador's optional EL conditions are unused: SMBJ @Handler conditions are empty.
# These Java EE types are absent on Android; do not hide other MBassador warnings.
-dontwarn javax.el.BeanELResolver
-dontwarn javax.el.ELContext
-dontwarn javax.el.ELResolver
-dontwarn javax.el.ExpressionFactory
-dontwarn javax.el.FunctionMapper
-dontwarn javax.el.ValueExpression
-dontwarn javax.el.VariableMapper

# SMBJ 0.13.0 SmbConfig excludes the JVM-only SPNEGO factory on Android.
# Android uses its direct NTLM factory; do not suppress the whole SMBJ namespace.
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid
