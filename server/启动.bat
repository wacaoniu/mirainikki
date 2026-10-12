@echo off
chcp 65001 >nul
cd /d %~dp0
echo ============================================
echo   知己 - 后端启动
echo ============================================
if not exist node_modules (
  echo 首次运行，安装依赖中...
  call npm install
)
echo.
echo 启动后用浏览器打开: http://localhost:8787
echo 手机 App 填的服务器地址: http://本机局域网IP:8787
echo 查本机 IP 方法: 另开一个命令行窗口运行 ipconfig
echo.
echo 关闭此窗口即停止服务
echo ============================================
echo.
call npm start
pause
