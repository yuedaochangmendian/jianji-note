# Room 生成的实现类通过反射加载，保留其构造函数
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# 自定义 View 需要保留构造函数（XML 布局中以全限定名引用）
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-dontwarn org.jetbrains.annotations.**
