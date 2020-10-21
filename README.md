## 目录

<!-- GFM-TOC -->
* [简介](#简介)
* [系统架构图](#系统架构图)
* [运行网站](#运行网站)
* [注册登录](#注册登录)
* [发帖评论](#发帖评论)
* [私信](#私信)
* [点赞关注](#点赞关注)
* [系统通知](#系统通知)
* [搜索](#搜索)
* [其他](#其他)
<!-- GFM-TOC -->

## 简介

基于 Spring Boot + MyBatis + Thymeleaf 搭建的社区网站，还使用到 Redis、Kafka 和 Elasticsearch。实现的功能包括注册登录、发帖评论、私信、点赞关注、系统通知、搜索等。

这个项目主要适合 Java 开发初学者，能从中学到如何将基础知识运用到实战中。我将用技术对应业务的形式，针对后台部分，分步骤讲解本项目的实现，希望能帮助各位理解。

## 系统架构图

<img src="https://github.com/windwj000/community/blob/master/pics/architecture.png" width="75%">

## 运行网站

在运行 CommunityApplication 类之前，需要开启 Redis 和 Elasticsearch 服务器，Kafka 运行时需要先开启 ZooKeeper，再开启 Kafka 服务器。

## 注册登录

这里开始是本项目的业务部分理解。

### 首页

<img src="https://github.com/windwj000/community/blob/master/pics/index.png" width="75%">

用户 User 类和帖子 DiscussPost 类遵从一般后台业务开发流程：建表->entity->mapper->*_mapper.xml->service->controller，按照这个顺序实现对一个目标的增删改查操作。
这个过程中，主要涉及 MySQL、MyBatis 以及 Spring Boot 的各种注解。

#### 分页

Page 类分装了分页逻辑，包含当前页面 current、显示上限 limit、总行数 rows 和查询路径 path，除了自动生成的 get/set 方法，我们还需要加上：

```java

/**
 * 获取当前页的起始行
 */
public int getOffset(){
    return (current-1)*limit;
}

/**
 * 获取总页数
 */
public int getTotal(){
    if(rows%limit==0)
        return rows/limit;
    else
        return rows/limit+1;
}

/**
 * 获取起始页码
 */
public int getFrom(){
    int from=current-2;
    return from<1?1:from;
}

/**
 * 获取结束页码
 */
public int getTo(){
    int to=current+2;
    int total=getTotal();
    return to>total?total:to;
}

```

这些对页的 get 操作实现了首页的分页功能。

另外，controller 中方法调用前，SpringMVC 会自动实例化 Model 和 Page，并将 Page 注入 Model。所以，在 Thymeleaf 中可以注解访问 Page 对象中的数据。

### 发送邮件

这一块使用到了 Spring Email，需要在邮箱客户端启用 SMTP 服务，并在 application.properties 中添加邮箱的账户密码等配置信息。

使用到了 JavaMailSender 发送邮件。

### 注册

CommunityUtil 这个工具类中有生成随机字符串和 MD5 加密的方法。特别注意密码不能直接存储到数据库中，以防万一被获取而造成用户隐私泄露！

UserService 的 register() 传入 User 对象，需要进行账号&密码&邮箱非空的检查，还需要检查 User 和邮箱不存在，才能进行注册。
注册需要对这个 User 对象设值，然后 insert。

使用 Thymeleaf 可以发送 HTML 激活邮件。

CommunityConstant 常量类包含了激活状态，这个状态用于判断用户注册后是否点击了激活邮件中的链接。

### 验证码

用到了 Kaptcha，在 KaptchaConfig 中配置图片、验证码和干扰信息。

在 LoginController 的 getKaptcha() 中，传入 HttpSession 对象，将 String 类型的验证码存入 session，将图片输出给浏览器用于显示。

### 登录

UserService 的 login() 和 register() 类似，需要对账号进行非空、存在和激活的判断，对密码进行非空和正确的判断。然后生成登录凭证 LoginTicket，包含用户 ID 和过期时间等。

LoginController 的 login() 中，从 HttpSession 拿到验证码并检查，然后调用 UserService 的 login()，进行上述判断都正确后，将 ticket 放到 Cookie 中，并重定向到首页表示登录成功。

### 登录信息

LoginTicket 类保存了用户的登录信息。

CookieUtil 这个工具类实现了从 HttpServletRequest 取 Cookie。

HostHolder 代替 Session，使用的是 ThreadLocal 存储用户信息。

LoginTicketInterceptor.preHandle() 使用 CookieUtil 获得 ticket，然后将对应的 User 存入 HostHolder 中。
postHandle() 和 afterCompletion() 分别完成登记 User 和清除 HostHolder。
然后这个拦截器需要在 Webmvcconfig 的 addInterceptors() 中注册，然后在请求进入 MVC 框架后都可以使用 HostHolder 获取 User。

@LoginRequired 这个自定义注解检查登录状态，加在设置和上传头像两个方法上。同样也是使用拦截器实现，preHandle() 检查是否已经登录，没登录则重定向到登录界面。

### 上传头像

File 类文件上传，并且可以通过 url 访问头像图片。

## 发帖评论

发帖和评论是论坛类网站核心的两个功能。

### 敏感词过滤

使用 Trie 树，SensitiveFilter 类从敏感词文件 .txt 读取关键词，生成 Trie 树。filter() 将敏感词转换为特殊字符。

### 发帖

使用 AJAX 异步实现网页上的增量不需要刷新网页，而更新到页面上。CommunityUtil 的 getJSONString() 用到了 JSONObject。

DiscussPostService 的 addDiscussPost() 对一个帖子除了过滤敏感词，还需要转译 HTML 标记。

### 帖子详情

### 评论

和首页显示帖子时一样，评论 Comment 类也需要分页。继续使用 Page 类显示分页信息。

## 私信

发送私信和发帖一样，用到 AJAX。
看私信时涉及到未读和已读，即改变 Message 类的 status 属性。

### 统一异常处理

ExceptionAdvice 类用 @ControllerAdvice 修饰，进行全局配置。
@ExceptionHandler 修饰方法，返回异常信息时需要分为异步还是非异步。异步即 XMLHttpRequest，返回 JSON 数据。非异步则重定向到 error 页面。

### 统一记录日志

用到 AOP 思想，ServiceLogAspect 类记录用户访问某个 Service 的具体某个方法。

## 点赞关注

### 点赞

用到了 RedisTemplate 来对 Redis 的基础数据类型进行操作，如 redisTemplate.opsForSet()。
Redis 中存储数据的 key 都是 String 类型，需要利用 RedisKeyUtil 这个工具类将属性拼接成字符串后存入 Redis。

这里还用 userLikeKey 记录用户收到点赞的数量，由于已经使用 entityLikeKey 记录某个帖子收到点赞的数量，对于两个操作需要用到 Redis 事务的 multi() 和 exec()。

### 关注

同样使用 Redis 事务，这里的操作是增加关注者和被关注者。

### 登录模块优化

这里使用 Redis 来优化性能了。

- 用 Cookie 配合 Redis 代替之前用的 Session 存储验证码：由于验证码需要频繁的访问与刷新，对性能要求较高。验证码不需要永久保存，通常在很短时间就失效。还有考虑到分布式部署时都从 Redis 读，避免了 Session 共享问题。
具体做法是生成验证码时分别放到 Cookie 和 Redis 中，在登录检查验证码时取出 Cookie，这个值作为 key 查询到 Redis 里存储的验证码，进行验证。
- 存储登录凭证，每次请求都要查询用户登录凭证，访问频率很高。
- 缓存用户信息，通过查看 ServiceLogAspect 记录的日志，发现查询用户信息的方法 findUserById() 的访问频率很高，改为用 Redis 存放用户信息。
既然是缓存，就有取不到值的情况，这时候就要从数据库里查，然后放到缓存中。如果用户信息经常改变，那么从缓存取到的用户信息就存在一定的脏数据，这也是性能和一致性之间的权衡。

## 系统通知

Event 事件类，Kafka 的生产者消费者通过 KafkaTemplate 操作这个 Event。
生产者 send 将事件发布到主题。注意需要在之前写过的 Controller 里增加触发事件的代码，即创建 Event 类，然后调用生产者发布。
消费者 在方法上使用 @KafkaListener 注解消费对应主题的消息。消费的结果有通过 Message 类发送系统通知，告知用户的帖子被评论、被点赞或关注了。

显示消息的时候注意每个请求都要显示消息数量，因此很直接地想到用拦截器 MessageInterceptor，统计未读的通知和私信。

## 搜索

对帖子进行搜索，用注解的形式在 DiscussPost 这个实体类上完成 ES 的注解。
用 ElasticsearchRestTemplate 的 queryForPage() 进行搜索。

Elasticsearch 需要和 Kafka 事件配合，EventConsumer 会将发帖和评论保存到 Elasticsearch 服务器中。

## 其他

### 认证授权

废弃了之前采用拦截器实现的登录检查，使用 Spring Security，在 SecurityConfig 类中 configure() 配置了：

- 忽略对静态资源的访问
- 授权，分为管理员、版主和普通用户。版主能够置顶和加精，管理员能够删帖和进行项目管理相关的操作。
- 没有登录或未被授权时的处理

并在 LoginTicketInterceptor 中构建用户认证的结果，并存入 SecurityContext 中，方便 Spring Security 授权。

置顶、加精和删除都会触发对应的事件，并且需要在 EventConsumer 中消费。

### Redis 高级数据结构

#### HyperLogLog

采用基数算法进行统计，占用空间少，有较小误差。
用于统计独立访客 UV，根据 IP 进行统计，并且可以统计指定日期范围的 UV。
redisTemplate.opsForHyperLogLog()。

#### Bitmap

可以看成 byte[]，适合存储大量连续的布尔值。
用于统计日活跃用户 DAU，根据用户 ID 进行统计，同样可以统计指定日期范围的 DAU。
redisTemplate.opsForValue().setBit()。

### 任务执行和调度

任务调度器 Quartz 实现刷新帖子分数的任务，需要配置 RefreshJobDetail() 和 RefreshTrigger()。

### 帖子热度排行

PostScoreRefreshJob 类的 refresh() 中加入计算帖子分数的公式：log(精华分+评论数*10+点赞数*2+收藏数*2)+(发布时间-纪元)。时间和分数对应的曲线如下图：

<img src="https://github.com/windwj000/community/blob/master/pics/score_time.png" width="50%">

可以看到随着帖子的热度下降的形势。

配合 Quartz 刷新帖子分数，我们可以通过 orderMode 的值，在网站首页按照最新和最热排序帖子。

### 分享长图

用到 wk 将 HTML 转换为 PDF 或 image 格式的图片，产生图片的事件也要被消费。
由于使用 wk 转换图片实际是一条命令，我们用 Runtime.getRuntime().exec(command) 执行。

顺带一提，自从项目中加入 Kafka 后，很多原先在 controller 实现的逻辑，被转移到了 EventConsumer 类中实现，会享有消息队列的削峰解耦异步的优势。

### 文件上传到云服务器

使用第三方平台，需要在配置文件中加入第三方授予的 access 和 secret。

对于之前的上传头像和分享的 wk 长图业务进行重构，使用云服务器存储。分别为客户端上传和服务器直传。
上传头像需要客户端（即浏览器的页面上）提交表单，表单数据再提交到云服务器上。
分享的 wk 长图中使用 UploadTask 任务监视，同时使用 Future 定时器返回执行结果，一旦生成长图就上传至云服务器，不经过客户端。

### 多级缓存

首先是本地缓存 Caffeine，缓存帖子列表和帖子总数。当访问网站首页时，可以直接从缓存中拿到帖子。
进行一个小实验：造 30W 数据，JMeter 压测发现缓存提高了 10 倍的吞吐量。

再用分布式缓存 Redis 作为二级缓存。

