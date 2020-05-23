# 简介

基于 Spring Boot + MyBatis + Thymeleaf 搭建的社区网站，还使用到 Redis、Kafka 和 Elasticsearch。实现的功能包括注册登录、发帖评论、私信、点赞关注、系统通知、搜索等。

这个项目主要适合 Java 开发初学者，能从中学到如何将基础知识运用到实战中。我将用技术对应业务的形式，分步骤讲解本项目的实现，希望能帮助各位理解。

## 系统架构图

<img src="https://github.com/Jiebupup/community/blob/master/pic/architecture.png" width="50%">

## 注册登录

### 首页

<img src="https://github.com/Jiebupup/community/blob/master/pic/index.png" width="50%">

用户和帖子，分页

### 发送邮件

客户端启用 SMTP 服务，JavaMailSender 发送邮件

### 注册

CommunityUtil 生成随机字符串，MD5 加密，Thymeleaf 发送 HTML 激活邮件，CommunityConstant 激活状态

### 验证码

Kaptcha

### 登录

生成登录凭证 LoginTicket，生成 Cookie 和重定向到首页，*Redis 重构

### 登录信息

HostHolder 代替 Session，Interceptor 和 Webmvcconfig，@LoginRequired 检查登录状态

### 上传头像

文件上传

## 发帖评论

### 敏感词过滤

Trie 树

### 发帖

AJAX

### 帖子详情

### 评论

## 私信

### 统一异常处理

@ControllerAdvice

### 统一记录日志

AOP

## 点赞关注

Redis

### 登录模块优化

存储验证码，用 Cookie 代替 Session，验证码需要频繁的访问与刷新，对性能要求较高。验证码不需要永久保存，通常在很短时间就失效。分布式部署时都从 Redis 读，避免了 Session 共享问题。
存储登录凭证，每次请求都要查询用户登录凭证，访问频率很高。
缓存用户信息，每次根据凭证查用户信息 findUserById，访问频率很高。
高级数据结构：HyperLogLog和Bitmap统计UV和DAU
帖子热度排行 zset

## 系统通知

Kafka 事件生产者消费者

## 搜索

Elasticsearch，和 Kafka 事件配合

## 其他

Spring Security 认证授权：置顶加精，删除
Quartz 线程：刷新帖子分数任务
wk 分享长图：和 Kafka 事件配合
文件上传到云服务器：客户端上传和服务器上传，传头像和分享的 wk 长图
缓存：本地缓存 Caffeine，缓存帖子列表，二级缓存。造 30W 数据，JMeter 压测发现缓存提高了 10 倍的吞吐量
SpringBootTest 单元测试
SpringBootActuator 监控
