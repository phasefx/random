JAVA_HOME=jdk1.8
JETTY_HOME=jetty

# compile
$JAVA_HOME/bin/javac \
    -cp "$JETTY_HOME/lib/*:$JETTY_HOME/lib/websocket/*:lib/*" \
    -Xdiags:verbose -d lib \
    src/org/evergreen_ils/hatch/*.java

[ -z "$1" ] && exit;

# run
$JAVA_HOME/bin/java \
    -cp "$JETTY_HOME/lib/*:$JETTY_HOME/lib/websocket/*:lib/*:lib" \
    org.evergreen_ils.hatch.Hatch
