# 后端运行镜像:固定 JRE 26 环境(构建产物 jar 由宿主机 mvnw 打包)
# 构建方式见仓库根目录 docker-compose.yml 注释
FROM eclipse-temurin:26-jre

WORKDIR /app

COPY target/travelist-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 5222

ENTRYPOINT ["java", "-jar", "app.jar"]
