# Keep native bridge methods and engine entry points.
-keepclasseswithmembernames class com.ef101.p02.cricketscorer.engine.NativeEngine {
    native <methods>;
}
-keep class com.ef101.p02.cricketscorer.engine.NativeEngine { *; }
