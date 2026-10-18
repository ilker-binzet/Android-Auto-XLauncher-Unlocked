# Rules for libxposed (https://github.com/libxposed/api)
-dontwarn io.github.libxposed.annotation.**
# Rewrite the entry list when the module entry class gets obfuscated
-adaptresourcefilecontents META-INF/xposed/java_init.list
# The framework instantiates the module entry class reflectively
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
