# Consumer rules shipped inside the AAR. (PLAN.md §8.5)
#
# Tesseract's native layer reaches Java through JNI reflection, which R8 cannot see. With
# these rules missing, a release build strips the very classes libtesseract calls back into,
# and OCR fails at runtime with an UnsatisfiedLinkError that points nowhere useful. This is
# not an optimisation hint -- it is a correctness requirement.
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }

# Any class holding native methods must keep its member NAMES, because that is how the JNI
# symbol lookup finds them.
-keepclasseswithmembernames class * {
    native <methods>;
}

# androidx.startup instantiates initializers reflectively from the merged manifest, so the
# engine would otherwise never register itself in a minified build.
-keep class io.github.andrewmalitchuk.uncial.engine.tesseract.source.install.TesseractEngineInitializer { *; }
