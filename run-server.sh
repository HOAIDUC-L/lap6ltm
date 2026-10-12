#!/bin/bash
# Script khởi chạy Server NetSend trên Linux/macOS
cd "$(dirname "$0")" || exit
echo "[NetSend] Đang biên dịch mã nguồn..."
javac -encoding UTF-8 src/*.java
if [ $? -eq 0 ]; then
    echo "[NetSend] Khởi chạy Server GUI..."
    java -cp src ServerGUI
else
    echo "[NetSend] Lỗi biên dịch!"
fi
