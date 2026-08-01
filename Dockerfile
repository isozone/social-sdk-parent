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
# Ubuntu 24.04+ 的 chromium apt 包是 snap 过渡包装，容器内无法运行，
# 改用 saiarcot895/chromium-beta PPA 安装真实 chromium 二进制。
RUN apt-get update && apt-get install -y --no-install-recommends \
    software-properties-common \
    curl \
    ca-certificates \
    && add-apt-repository -y ppa:saiarcot895/chromium-beta \
    && apt-get update && apt-get install -y --no-install-recommends \
    chromium-browser \
    fonts-liberation \
    fonts-noto-cjk \
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
