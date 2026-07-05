@echo off
REM test-windows.bat — Simple Windows batch script for testing SCRIPT tasks
REM Usage: cmd /c test-windows.bat arg1 arg2

echo Hello from Windows Batch!
echo Arguments: %*
echo Working directory: %CD%
echo OS: %OS%
echo Computer: %COMPUTERNAME%

exit /b 0

