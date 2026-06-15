@echo off
setlocal
set APP_HOME=%~dp0
set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
set GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT
"%JAVA_HOME%\bin\java.exe" %GRADLE_OPTS% -cp "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
endlocal
