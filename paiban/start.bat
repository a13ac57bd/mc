@echo off
cd /d "%~dp0"
title Paiban Server
py -3 server.py --port 8080 || python server.py --port 8080
pause
