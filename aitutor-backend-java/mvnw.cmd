@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.2
@REM ----------------------------------------------------------------------------

@REM ==== ENSURE JAVA_HOME IS SET ====
@if not defined JAVA_HOME (
    echo JAVA_HOME environment variable must be set. Please set it and try again.
    exit /b 1
)

@REM ==== START VALIDATION ====
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo JAVA_HOME is set to "%JAVA_HOME%" but java.exe was not found there.
    exit /b 1
)

@REM Find the project base dir, i.e. the directory that contains the folder ".mvn".
set "MAVEN_PROJECTBASEDIR=%~dp0"

@REM ==== Download maven-wrapper.jar ====
set "WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"
set "WRAPPER_URL_FILE=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties"

if not exist "%WRAPPER_JAR%" (
    echo Downloading Maven Wrapper...
    for /f "usebackq tokens=2 delims==" %%a in ("%WRAPPER_URL_FILE%") do (
        set "WRAPPER_URL=%%a"
    )
    powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%WRAPPER_URL%' -OutFile '%WRAPPER_JAR%'}" 
    if not exist "%WRAPPER_JAR%" (
        echo Failed to download Maven Wrapper. Using curl fallback...
        curl -L -o "%WRAPPER_JAR%" "%WRAPPER_URL%"
        if exist "%WRAPPER_JAR%" goto endWrapperDownload
        echo Download failed. Please download Maven wrapper jar manually.
        exit /b 1
    )
)
:endWrapperDownload

@REM ==== Download Maven distribution if needed ====
set "WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain"
set "MAVEN_USER_HOME=%USERPROFILE%\.m2"
set "MAVEN_DOWNLOAD_URL_FILE=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties"
for /f "usebackq tokens=2 delims==" %%a in ("%MAVEN_DOWNLOAD_URL_FILE%") do (
    set "MAVEN_DOWNLOAD_URL=%%a"
)

"%JAVA_HOME%\bin\java.exe" ^
    %MAVEN_OPTS% ^
    -classpath "%WRAPPER_JAR%" ^
    "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" ^
    %WRAPPER_LAUNCHER% %MAVEN_CONFIG% %*
