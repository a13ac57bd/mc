@echo off
cd /d "%~dp0"
py -3 backup.py || python backup.py
