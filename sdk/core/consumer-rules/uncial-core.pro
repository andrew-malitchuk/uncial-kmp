# Consumer rules shipped inside the AAR.
#
# androidx.startup names this class as a string in the merged manifest and instantiates it
# reflectively, so R8 sees no reference to it and removes it -- taking the SDK's Context
# with it.
-keep class io.github.andrewmalitchuk.uncial.core.source.android.UncialContextInitializer { *; }
