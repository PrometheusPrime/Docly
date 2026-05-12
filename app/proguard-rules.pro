# Preserve useful release stack traces while still allowing R8 to shrink and obfuscate app code.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep minification and shrinking enabled, but skip the optimizer for repeatable internal builds.
-dontoptimize
