#!/bin/bash
JAVA_BIN="/c/Program Files/Android/Android Studio/jbr/bin"
KSP232_JAR="/c/Users/kochn/.gradle/caches/modules-2/files-2.1/com.google.devtools.ksp/symbol-processing-gradle-plugin/2.3.2/1309b67d3441365312ddc8138db2393afbeb03c/symbol-processing-gradle-plugin-2.3.2.jar"
KSP0128_JAR="/c/Users/kochn/.gradle/caches/modules-2/files-2.1/com.google.devtools.ksp/symbol-processing-gradle-plugin/2.0.21-1.0.28/fa12be104ddc31ad85e821ad461a6adc7de6383b/symbol-processing-gradle-plugin-2.0.21-1.0.28.jar"

echo "=== KSP 2.3.2 task classes ==="
"$JAVA_BIN/jar" tf "$KSP232_JAR" 2>&1 | grep -i task

echo "=== KSP 2.0.21-1.0.28 task classes ==="
"$JAVA_BIN/jar" tf "$KSP0128_JAR" 2>&1 | grep -i task
