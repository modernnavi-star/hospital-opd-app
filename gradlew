#!/bin/sh
#
# Gradle startup script for UN*X
#

APP_HOME=$(dirname "$(readlink -f "$0" 2>/dev/null || ls -dl "$0" | awk '{print $NF}')" 2>/dev/null || echo ".")

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

exec "$JAVA_HOME/bin/java" \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"
