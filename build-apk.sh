#!/bin/bash

echo "🌸 DUSOL HAIR APK 빌드 시작 🌸"

# Docker가 설치되어 있는지 확인
if command -v docker &> /dev/null; then
    echo "🐳 Docker를 사용하여 APK 빌드 중..."
    
    # Docker 이미지 빌드
    docker build -t dusol-hair-builder .
    
    # APK 빌드 실행
    docker run --rm -v $(pwd):/app dusol-hair-builder
    
    # APK 파일 확인
    if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
        echo "✅ APK 빌드 성공!"
        echo "📱 APK 위치: app/build/outputs/apk/debug/app-debug.apk"
        echo "📏 파일 크기: $(du -h app/build/outputs/apk/debug/app-debug.apk | cut -f1)"
    else
        echo "❌ APK 빌드 실패"
        exit 1
    fi
else
    echo "❌ Docker가 설치되지 않았습니다."
    echo "📥 Docker 설치: https://docs.docker.com/get-docker/"
    echo "🔗 또는 GitHub Actions를 사용하세요: https://github.com/features/actions"
    exit 1
fi 