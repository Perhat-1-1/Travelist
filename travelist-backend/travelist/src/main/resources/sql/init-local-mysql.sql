CREATE DATABASE IF NOT EXISTS travelist_dev
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 兼容 127.0.0.1(TCP)与 localhost(命名管道/Unix socket)两种连接方式
CREATE USER IF NOT EXISTS 'travelist'@'localhost' IDENTIFIED BY 'travelist';
CREATE USER IF NOT EXISTS 'travelist'@'127.0.0.1' IDENTIFIED BY 'travelist';
CREATE USER IF NOT EXISTS 'travelist'@'%' IDENTIFIED BY 'travelist';

GRANT ALL PRIVILEGES ON travelist_dev.* TO 'travelist'@'localhost';
GRANT ALL PRIVILEGES ON travelist_dev.* TO 'travelist'@'127.0.0.1';
GRANT ALL PRIVILEGES ON travelist_dev.* TO 'travelist'@'%';

FLUSH PRIVILEGES;
