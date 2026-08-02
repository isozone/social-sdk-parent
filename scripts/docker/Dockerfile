# ============================================================================
# Dockerfile — 闲鱼管理器
#
# 支持三种构建模式（通过 --build-arg DB_MODE=）：
#   sqlite (默认) — 单容器，内嵌 SQLite
#   mysql         — 外接 MySQL 8
#   postgres      — 外接 PostgreSQL
#
# 用法:
#   docker build --build-arg DB_MODE=sqlite -t xianyu-manager:sqlite .
#   docker build --build-arg DB_MODE=mysql -t xianyu-manager:mysql .
#   docker build --build-arg DB_MODE=postgres -t xianyu-manager:postgres .
# ============================================================================

# ── Stage 1: Maven 构建 ────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# 复制项目文件
COPY pom.xml .
# 使用阿里云 Maven 镜像，避免容器内直连 Maven Central 超时
# 构建上下文是项目根目录，settings.xml 位于 scripts/docker/ 下
COPY scripts/docker/settings.xml /root/.m2/settings.xml
COPY social-sdk-core ./social-sdk-core
COPY social-sdk-xianyu ./social-sdk-xianyu
COPY social-sdk-chrome ./social-sdk-chrome
COPY social-sdk-proxys ./social-sdk-proxys
COPY social-sdk-cdp-auth ./social-sdk-cdp-auth
COPY social-sdk-xianyu-manager ./social-sdk-xianyu-manager
COPY social-sdk-spring-boot-starter ./social-sdk-spring-boot-starter

# 复制前端源码（用于 Maven 插件构建）
COPY social-sdk-xianyu-manager-web ./social-sdk-xianyu-manager-web

# 构建 JAR（含前端），只复制 Spring Boot 可执行 JAR。
# 不能简单 find 第一个 *.jar：target 中可能同时存在普通 jar / sources / javadoc / original，
# 普通 jar 没有 Main-Class，会导致容器启动时报 no main manifest attribute。
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

# 安装生产环境依赖
# 注意：Ubuntu 24.04+ 的 chromium apt 包是 snap 过渡包装，容器内无 snapd 无法运行
# （报错 "requires the chromium snap to be installed"）；PPA 也不支持 Ubuntu 26.04。
# 改用 Chrome for Testing 官方 zip（国内走 npmmirror 镜像），解压后软链到 /usr/bin/chromium-browser。
# 版本可用 --build-arg CHROME_VERSION=x.y.z 覆盖，或直接改下面的默认值。
ARG CHROME_VERSION=146.0.7658.0
# 宿主机代理 fake-ip DNS 会污染 archive.ubuntu.com 解析(198.18.x.x 连不上)，
# 构建前先把 apt 源切换到阿里云镜像，保证 apt-get 可用。
RUN sed -i 's|//archive.ubuntu.com|//mirrors.aliyun.com|g; s|//security.ubuntu.com|//mirrors.aliyun.com|g' \
        /etc/apt/sources.list.d/ubuntu.sources /etc/apt/sources.list 2>/dev/null || true \
    && apt-get update \
    && apt-get install -y --no-install-recommends \
        curl ca-certificates unzip \
        fonts-liberation fonts-noto-cjk \
        libnss3 libnspr4 libatk1.0-0t64 libatk-bridge2.0-0t64 \
        libcups2t64 libdrm2 libgbm1 libasound2t64 libxkbcommon0 \
        libxcomposite1 libxdamage1 libxfixes3 libxrandr2 libxshmfence1 \
    && curl -fsSL -o /tmp/chrome-linux64.zip \
        https://registry.npmmirror.com/-/binary/chrome-for-testing/${CHROME_VERSION}/linux64/chrome-linux64.zip \
    && unzip -q /tmp/chrome-linux64.zip -d /opt \
    && rm -f /tmp/chrome-linux64.zip \
    && ln -sf /opt/chrome-linux64/chrome /usr/bin/chromium-browser \
    && rm -rf /var/lib/apt/lists/*

# 创建非 root 用户
RUN groupadd -r xianyu && useradd -r -g xianyu -d /app -s /sbin/nologin xianyu

WORKDIR /app

# 复制 JAR
COPY --from=builder /app/app.jar server.jar

# 创建数据目录（持久化用）
RUN mkdir -p /app/data /app/data/openlist /app/chrome-profiles /app/logs /app/config /app/tmp \
    && chown -R xianyu:xianyu /app

# 数据库镜像变体：sqlite / mysql / postgres
ARG DB_MODE=sqlite

# 环境变量
ENV CHROME_BIN=/usr/bin/chromium-browser \
    CHROME_HEADLESS=true \
    CHROME_HEADLESS_MODE=new \
    SPRING_PROFILES_ACTIVE=${DB_MODE} \
    JAVA_OPTS="-Xmx512m -Xms256m -Djava.io.tmpdir=/app/tmp" \
    SERVER_PORT=8080 \
    DB_PATH=/app/data/xianyu-manager.db \
    TMPDIR=/app/tmp \
    TZ=Asia/Shanghai

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://127.0.0.1:${SERVER_PORT}/api/system/health || exit 1

# 暴露端口
EXPOSE 8080

# 数据卷
VOLUME ["/app/data", "/app/chrome-profiles", "/app/logs", "/app/config"]

# 方案 B:入口以 root 修正 bind mount 目录属主后降权运行(xianyu)
# 不再使用 USER xianyu —— 由 docker-entrypoint.sh 在启动时 chown 再 su 降权
COPY scripts/docker/docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh

ENTRYPOINT ["/app/docker-entrypoint.sh"]
CMD ["server.jar"]
