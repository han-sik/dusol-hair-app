FROM openjdk:11-jdk

# Install Android SDK
ENV ANDROID_HOME /opt/android-sdk
ENV ANDROID_SDK_ROOT /opt/android-sdk
ENV PATH ${PATH}:${ANDROID_HOME}/tools:${ANDROID_HOME}/platform-tools

# Install required packages
RUN apt-get update && apt-get install -y \
    wget \
    unzip \
    git \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Download and install Android SDK
RUN wget -q https://dl.google.com/android/repository/commandlinetools-linux-8512546_latest.zip -O android-sdk.zip \
    && unzip android-sdk.zip -d /opt \
    && rm android-sdk.zip \
    && mkdir -p ${ANDROID_HOME}/cmdline-tools \
    && mv /opt/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest

# Accept licenses and install required SDK components
RUN yes | ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager --licenses
RUN ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager \
    "platform-tools" \
    "platforms;android-33" \
    "build-tools;33.0.0"

# Set working directory
WORKDIR /app

# Copy project files
COPY . .

# Build APK
CMD ["./gradlew", "assembleDebug"] 