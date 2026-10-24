# PdfBox-Android: опциональные декодеры изображений, которых нет в сборке.
-dontwarn com.gemalto.jp2.**
-keep class com.tom_roush.pdfbox.** { *; }

# Tesseract вызывается из нативного кода.
-keep class com.googlecode.tesseract.android.** { *; }

# llama.cpp вызывает onText из нативного кода.
-keep class com.evgurov.expenses.assistant.LlamaNative { *; }
-keep interface com.evgurov.expenses.assistant.TextCallback { *; }
