# JNI resolves sherpa configuration/result fields and class names reflectively.
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepattributes InnerClasses,EnclosingMethod

# MediaPipe 0.10.35 AARs ship no consumer rules. Their JNI callbacks,
# generated protobuf schemas and stack-based Flogger caller lookup need stable members.
# Retain only these native/reflection boundaries; keep app/AndroidX shrinking enabled.
-keep class com.google.mediapipe.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keep class com.google.common.flogger.** { *; }
# Optional graph-profiling/template proto types are referenced by unused SDK APIs
# but absent from the Maven AARs. This app uses neither profiler nor graph templates.
-dontwarn com.google.mediapipe.proto.CalculatorProfileProto$CalculatorProfile
-dontwarn com.google.mediapipe.proto.GraphTemplateProto$CalculatorGraphTemplate
