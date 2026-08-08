# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\souic\AppData\Local\Android\Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Crashlyticsのスタックトレースを行番号付きで読めるようにする。
# 難読化（#24）を有効にするまでは効果を持たないが、有効にした時点で
# マッピングファイルのアップロード設定（app/build.gradle.kts）と揃って機能する。
-keepattributes SourceFile,LineNumberTable

# 元のソースファイル名は残さない（行番号はマッピングで復元できる）
-renamesourcefileattribute SourceFile
