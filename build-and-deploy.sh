#!/bin/bash

# 构建和部署脚本

echo "开始构建Spring Boot应用程序..."

# 清理并编译项目
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "Maven构建失败!"
    exit 1
fi

echo "Maven构建成功!"

echo "构建Docker镜像..."
docker build -t acutor-module:latest .

if [ $? -ne 0 ]; then
    echo "Docker镜像构建失败!"
    exit 1
fi

echo "Docker镜像构建成功!"

echo "启动容器..."
docker-compose up -d

if [ $? -ne 0 ]; then
    echo "容器启动失败!"
    exit 1
fi

echo "应用已成功部署并启动!"
echo "可以通过 http://localhost:8080 访问应用"
echo "使用 'docker-compose logs -f' 查看应用日志"