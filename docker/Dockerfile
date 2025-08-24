FROM openjdk:8-jre-alpine

RUN apk add --no-cache docker

LABEL maintainer="monitor_module"

WORKDIR /app

COPY target/monitor_module-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

# 创建用户并将其添加到docker组
RUN addgroup -S appuser && \
    adduser -S appuser -G appuser && \
    # 检查docker组是否存在, 如果不存在则创建
    (getent group docker || addgroup -S -g 999 docker) && \
    # 将appuser添加到docker组
    adduser appuser docker

RUN chown appuser:appuser app.jar

USER appuser

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app/app.jar"]