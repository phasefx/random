SET JAVA_HOME="C:\Program Files\Java\jdk1.8.0_31"
SET JETTY_HOME=jetty

%JAVA_HOME%\bin\javac -cp "%JETTY_HOME%\lib\*;%JETTY_HOME%\lib\websocket\*;lib\*" -Xdiags:verbose -d lib src\org\evergreen_ils\hatch\*.java

%JAVA_HOME%\bin\java -cp "%JETTY_HOME%\lib\*;%JETTY_HOME%\lib\websocket\*;lib\*;lib" org.evergreen_ils.hatch.Hatch
