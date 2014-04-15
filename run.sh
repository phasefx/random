JAVA_HOME=jdk1.8.0
JETTY_HOME=jetty-distribution-9.1.4.v20140401

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
