# 前端运行镜像:固定 Nginx 环境(构建产物 dist 由宿主机 npm run build 产出)
FROM nginx:stable-alpine

COPY dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 5215
