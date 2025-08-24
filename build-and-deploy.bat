@echo off
REM 构建和部署脚本 (Windows版本)

echo 开始构建Spring Boot应用程序...

REM 清理并编译项目
call mvn clean package -DskipTests

if %errorlevel% neq 0 (
    echo Maven构建失败!
    exit /b 1
)

echo Maven构建成功!

echo 构建Docker镜像...
docker build -t acutor-module:latest .

if %errorlevel% neq 0 (
    echo Docker镜像构建失败!
    exit /b 1
)

echo Docker镜像构建成功!

echo 启动容器...
docker-compose up -d

if %errorlevel% neq 0 (
    echo 容器启动失败!
    exit /b 1
)

echo 应用已成功部署并启动!
echo 可以通过 http://localhost:8080 访问应用
echo 使用 'docker-compose logs -f' 查看应用日志
pause