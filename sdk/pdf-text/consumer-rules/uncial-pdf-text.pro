# Consumer rules shipped inside the AAR.
#
# PDFBox-Android references an OPTIONAL JPEG-2000 decoder (com.gemalto.jp2) that it does
# not depend on. R8 treats the dangling reference as a missing class and fails the build
# outright, so every consumer of uncial-pdf-text would have to discover and silence this
# themselves. JPX-encoded images inside a PDF simply will not decode, which is the same
# behaviour as on a debug build.
-dontwarn com.gemalto.jp2.**

# PDFBox-Android resolves fonts and glyph lists reflectively from its bundled resources.
-keep class com.tom_roush.pdfbox.pdmodel.font.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# PDFBox uses javax.imageio / AWT names that do not exist on Android but are never reached.
-dontwarn javax.imageio.**
-dontwarn java.awt.**
