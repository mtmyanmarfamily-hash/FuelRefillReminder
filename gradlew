#!/bin/sh
APP_HOME=$( cd "${0%"${0##*/}"}." && pwd -P ) || exit
APP_NAME="Gradle"
APP_BASE_NAME=${0##*/}
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
MAX_FD=maximum
die () { echo; echo "$*"; echo; exit 1; } >&2
case "$( uname )" in CYGWIN*) cygwin=true;; Darwin*) darwin=true;; MSYS*|MINGW*) msys=true;; NONSTOP*) nonstop=true;; esac
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
if [ -n "$JAVA_HOME" ]; then
    JAVACMD=$JAVA_HOME/bin/java
    [ ! -x "$JAVACMD" ] && die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
else
    JAVACMD=java
    command -v java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found."
fi
if ! "$cygwin" && ! "$darwin" && ! "$nonstop"; then
    case $MAX_FD in max*) MAX_FD=$( ulimit -H -n );; esac
    case $MAX_FD in ''|soft) :;; *) ulimit -n "$MAX_FD";; esac
fi
set -- "-Dorg.gradle.appname=$APP_BASE_NAME" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
eval "set -- $(printf '%s\n' "$DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS" | xargs -n1 | sed 's~[^-[:alnum:]+,./:=@_]~\\&~g' | tr '\n' ' ') \"$@\""
exec "$JAVACMD" "$@"
