@echo off
chcp 65001 > nul
echo [NetSend] Đang biên dịch mã nguồn...
javac -encoding UTF-8 src\*.java
if %errorlevel% equ 0 (
    echo [NetSend] Khởi chạy Client GUI...
    java -cp src ClientGUI
) else (
    echo [NetSend] Lỗi biên dịch!
    pause
)
