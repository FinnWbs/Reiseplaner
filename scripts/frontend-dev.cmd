@echo off
setlocal

cd /d "%~dp0..\frontend"
set "NUXT_BUILD_DIR=.nuxt-dev"
call npm.cmd run dev -- --host 127.0.0.1 --port 3000 > dev-3000.log 2> dev-3000.err.log
