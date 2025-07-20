@echo off
echo 🌸 DUSOL HAIR APK 빌드 시작 🌸

REM Docker가 설치되어 있는지 확인
docker --version >nul 2>&1
if %errorlevel% equ 0 (
    echo 🐳 Docker를 사용하여 APK 빌드 중...
    
    REM Docker 이미지 빌드
    docker build -t dusol-hair-builder .
    
    REM APK 빌드 실행
    docker run --rm -v %cd%:/app dusol-hair-builder
    
    REM APK 파일 확인
    if exist "app\build\outputs\apk\debug\app-debug.apk" (
        echo ✅ APK 빌드 성공!
        echo 📱 APK 위치: app\build\outputs\apk\debug\app-debug.apk
        for %%A in ("app\build\outputs\apk\debug\app-debug.apk") do echo 📏 파일 크기: %%~zA bytes
    ) else (
        echo ❌ APK 빌드 실패
        exit /b 1
    )
) else (
    echo ❌ Docker가 설치되지 않았습니다.
    echo 📥 Docker 설치: https://docs.docker.com/get-docker/
    echo 🔗 또는 GitHub Actions를 사용하세요: https://github.com/features/actions
    exit /b 1
)

pause 