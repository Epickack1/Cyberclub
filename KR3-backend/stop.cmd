@echo off
rem CyberClub KR3: stop containers, database volume is kept.
cd /d "%~dp0"
docker compose --env-file .env down
pause
