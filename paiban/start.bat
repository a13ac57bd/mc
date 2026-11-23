@echo off
cd /d "%~dp0"
title Paiban Server
py -3 server.py --open || python server.py --open
pause
