@echo off
rem Build and run script for Java_Learning
set SRC=src
set OUT=out
if exist %OUT% rmdir /s /q %OUT%
mkdir %OUT%

javac -encoding UTF-8 -d %OUT% %SRC%\com\david\game\*.java
if errorlevel 1 (
  echo Compilation failed.
  pause
  exit /b 1
)

echo Launching TestGame...
java -cp %OUT% com.david.game.TestGame
pause