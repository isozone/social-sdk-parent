#!/bin/sh
# ============================================================================
# 容器入口脚本(方案 B:root 修正权限后降权运行)
#
# 背景:部署用 bind mount(./data:/app/data),其权限由宿主机目录决定,
#       容器内非 root 用户 xianyu 无法写入 root 属主的宿主机目录。
#       本脚本以 root 启动,先 chown 数据目录(容器内 root 会真正修改宿主机
#       bind mount 目录的属主),再降权到 xianyu 运行 Java —— 自动自愈权限。
#
# 用法:ENTRYPOINT ["/app/docker-entrypoint.sh"] + CMD ["server.jar"](jar 文件名)
# ============================================================================

set -e

# 1. 修正 bind mount 数据目录属主(目录不存在时忽略,失败不阻塞启动)
chown -R xianyu:xianyu \
  /app/data /app/chrome-profiles /app/logs /app/config /app/tmp 2>/dev/null || true

# 2. 显式定位 Java:eclipse-temurin 镜像的 java 在 $JAVA_HOME/bin。
#    注意:su(util-linux)即使加 -p 保留环境,也会把 PATH 安全重置为系统默认,
#    导致 "java: not found"。因此必须用绝对路径调用 java,不能依赖 PATH。
export JAVA_HOME="${JAVA_HOME:-/opt/java/openjdk}"

# 3. 降权运行 Java;xianyu 登录 shell 是 nologin,需显式指定 /bin/sh
#    -p 保留环境变量(JAVA_OPTS/SPRING_PROFILES_ACTIVE 等 Dockerfile ENV 不丢)
#    $1 为 jar 文件名(如 server.jar / app.jar),由 Dockerfile CMD 传入
exec su -p xianyu -s /bin/sh -c \
  "cd /app && exec $JAVA_HOME/bin/java $JAVA_OPTS -Dfile.encoding=UTF-8 -jar /app/$1"
