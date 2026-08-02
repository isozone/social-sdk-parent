# ============================================================================
# Dockerfile — 闲鱼管理器（最小化构建版）
#
# 生产部署推荐使用 scripts/docker/Dockerfile（含前端构建、健康检查、非root用户）。
# 本文件仅保留基础能力，便于快速验证。
#
# 用法:
#   docker build -t xianyu-manager .
# ============================================================================

# ── Stage 1: Maven 构建 ────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
# 使用阿里云 Maven 镜像，避免容器内直连 Maven Central 超时
COPY scripts/docker/settings.xml /root/.m2/settings.xml
COPY social-sdk-core ./social-sdk-core
COPY social-sdk-xianyu ./social-sdk-xianyu
COPY social-sdk-chrome ./social-sdk-chrome
COPY social-sdk-proxys ./social-sdk-proxys
COPY social-sdk-cdp-auth ./social-sdk-cdp-auth
COPY social-sdk-xianyu-manager ./social-sdk-xianyu-manager
COPY social-sdk-spring-boot-starter ./social-sdk-spring-boot-starter
RUN mvn clean package -DskipTests -pl social-sdk-xianyu-manager -am \
    && for jar in social-sdk-xianyu-manager/target/*.jar; do \
        case "$jar" in \
          *.original|*-sources.jar|*-javadoc.jar) continue ;; \
        esac; \
        if jar tf "$jar" | grep -q '^BOOT-INF/'; then \
          cp "$jar" /app/app.jar; \
          break; \
        fi; \
      done \
    && test -f /app/app.jar \
    && jar xf /app/app.jar META-INF/MANIFEST.MF \
    && grep -q 'Main-Class:' META-INF/MANIFEST.MF

# ── Stage 2: 运行时镜像 ────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre
# Ubuntu 24.04+ 的 chromium apt 包是 snap 过渡包装，容器内无法运行；
# PPA 也不支持 Ubuntu 26.04。改用 Chrome for Testing 官方 zip（国内走 npmmirror 镜像），
# 解压后软链到 /usr/bin/chromium-browser。版本可用 --build-arg CHROME_VERSION=x.y.z 覆盖。
ARG CHROME_VERSION=146.0.7658.0
# 宿主机代理 fake-ip DNS 会污染 archive.ubuntu.com 解析(198.18.x.x 连不上)，
# 构建前先把 apt 源切换到阿里云镜像，保证 apt-get 可用。
RUN sed -i 's|//archive.ubuntu.com|//mirrors.aliyun.com|g; s|//security.ubuntu.com|//mirrors.aliyun.com|g' \
        /etc/apt/sources.list.d/ubuntu.sources /etc/apt/sources.list 2>/dev/null || true \
    && apt-get update && apt-get install -y --no-install-recommends \
    curl \
    ca-certificates \
    unzip \
    fonts-liberation \
    fonts-noto-cjk \
    libnss3 libnspr4 libatk1.0-0t64 libatk-bridge2.0-0t64 \
    libcups2t64 libdrm2 libgbm1 libasound2t64 libxkbcommon0 \
    libxcomposite1 libxdamage1 libxfixes3 libxrandr2 libxshmfence1 \
    && curl -fsSL -o /tmp/chrome-linux64.zip \
        https://registry.npmmirror.com/-/binary/chrome-for-testing/${CHROME_VERSION}/linux64/chrome-linux64.zip \
    && unzip -q /tmp/chrome-linux64.zip -d /opt \
    && rm -f /tmp/chrome-linux64.zip \
    && ln -sf /opt/chrome-linux64/chrome /usr/bin/chromium-browser \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=builder /app/app.jar app.jar
RUN mkdir -p /app/data /app/chrome-profiles /app/logs
EXPOSE 8080
ENV CHROME_BIN=/usr/bin/chromium-browser \
    CHROME_HEADLESS=true \
    CHROME_HEADLESS_MODE=new \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-Xmx512m -Xms256m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dfile.encoding=UTF-8 -jar /app/app.jar"]
