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

基于 Spring Boot + MyBatis + Thymeleaf 开发的论坛社区网站，使用到了 MySQL、Redis、MyBatis、Kafka、Elasticsearch 等技术，实现了注册登录、发帖评论、私信、点赞关注、系统通知、搜索等功能。

这个项目主要适合 Java 开发初学者，将一些基础知识运用到实战中。我将分析各个功能用到技术，罗列一些我认为重要的点，针对后台部分，分步骤讲解本项目的实现，希望能帮助各位理解。

## 系统架构图

<img src="https://github.com/windwj000/community/blob/master/pics/architecture.png" width="75%">

## 运行网站

在运行 CommunityApplication 类之前，需要启动 Redis 和 Elasticsearch 服务器。Kafka 运行时需要先启动 ZooKeeper 服务器，再启动 Kafka 服务器。

## 注册登录

这里开始是本项目的业务部分理解。

### 首页

<img src="https://github.com/windwj000/community/blob/master/pics/index.png" width="75%">

用户 User 类和帖子 DiscussPost 类遵从一般后台业务开发流程：MySQL 建表->entity->mapper->_mapper.xml->service->controller，按照这个顺序实现对一个目标的增删改查操作。
这个过程中，主要涉及 MySQL、MyBatis 以及 Spring Boot 的各种注解。

#### 分页

Page 类封装了分页逻辑，包含当前页面 current、显示上限 limit、总行数 rows 和查询路径 path，除了自动生成的 get/set 方法，我们还需要加上一些对页的 get 操作实现了首页的分页功能。帖子的评论功能也是使用 Page 类完成分页的功能。

```java

...
/**
 * 获取当前页的起始行
 */
public int getOffset() {
    return (current - 1) * limit;
}

/**
 * 获取总页数
 */
public int getTotal() {
    if (rows % limit == 0) {
        return rows / limit;
    } else {
        return rows / limit + 1;
    }
}

/**
 * 获取起始页码
 */
public int getFrom() {
    int from = current - 2;
    return from < 1 ? 1 : from;
}

/**
 * 获取结束页码
 */
public int getTo() {
    int to = current + 2;
    int total = getTotal();
    return to > total ? total : to;
}

```

另外对于 Page 类，在 controller 中的方法调用前，SpringMVC 会自动实例化 Model 和 Page，并将 Page 注入 Model。之后，在 Thymeleaf 中就可以访问 Page 对象中的数据。

```java

@RequestMapping(path = "/index", method = RequestMethod.GET)
public String getIndexPage(Model model, Page page,
                           @RequestParam(name = "orderMode", defaultValue = "0") int orderMode) {
    page.setRows(discussPostService.findDiscussPostRows(0));
    // 加上 orderMode
    page.setPath("/index?orderMode=" + orderMode);

    List<DiscussPost> list = discussPostService.findDiscussPosts(0, page.getOffset(), page.getLimit(), orderMode);
    List<Map<String, Object>> discussPosts = new ArrayList<>();
    if (list != null) {
        for (DiscussPost post : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("post", post);
            User user = userService.findUserById(post.getUserId());
            map.put("user", user);

            long likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_POST, post.getId());
            map.put("likeCount", likeCount);

            discussPosts.add(map);
        }
    }
    model.addAttribute("discussPosts", discussPosts);
    model.addAttribute("orderMode", orderMode);
    return "/index";
}

```

### 发送邮件

使用 Spring Email，需要在邮箱客户端启用 SMTP 服务，并在 application.properties 中添加邮箱的账户密码等配置信息。

使用到了 JavaMailSender 类发送邮件，并在用户注册时需要配合 Thymeleaf 的 TemplateEngine 类发送 HTML 激活邮件。

```java

@Autowired
private TemplateEngine templateEngine;
...
// 激活邮箱
Context context = new Context();
context.setVariable("email",user.getEmail());
// http://localhost:8080/community/activation/id/code
String url = domain + contextPath + "/activation/" + user.getId() + "/" + user.getActivationCode();
context.setVariable("url",url);
String content = templateEngine.process("/mail/activation", context);
mailClient.sendMail(user.getEmail(),"激活账号",content);

```

### 注册

UserService 类的 register() 中，需要进行账号&密码&邮箱非空的检查，还需要检查账号和邮箱未被注册，才能进行注册。

CommunityUtil 这个工具类中有生成随机字符串和 MD5 加密的方法，在数据库中只保存随机字符串 salt 和经过 MD5 加密的 password。特别注意密码不能直接存储到数据库中，以防万一被获取而造成用户隐私泄露！

```java

// 生成随机字符串
public static String generateUUID() {
    return UUID.randomUUID().toString().replaceAll("-", "");
}

// MD5 加密
public static String md5(String key) {
    if (StringUtils.isBlank(key)) {
        return null;
    }
    return DigestUtils.md5DigestAsHex(key.getBytes());
}
...
// 注册用户
user.setSalt(CommunityUtil.generateUUID().substring(0,5));
user.setPassword(CommunityUtil.md5(user.getPassword()+user.getSalt()));

```

CommunityConstant 常量接口包含了激活状态，这个状态用于判断用户注册后是否点击了激活邮件中的链接。

```java

public interface CommunityConstant {
    /**
     * 激活成功
     */
    int ACTIVATION_SUCCESS=0;

    /**
     * 重复激活
     */
    int ACTIVATION_REPEAT=1;

    /**
     * 激活失败
     */
    int ACTIVATION_FAIL=2;
    ...
}

```

### 验证码

用到了 Kaptcha，在 KaptchaConfig 中配置图片、验证码和干扰信息。

在 LoginController 类的 getKaptcha() 中，传入 HttpSession 对象，将 String 类型的验证码存入 session，将图片输出给浏览器用于显示。后续需要验证时，再从 HttpSession 对象中取出验证码进行检查。注意这里在后文会使用 Redis 进行优化！

```java

@RequestMapping(path = "/kaptcha", method = RequestMethod.GET)
public void getKaptcha(HttpServletResponse response HttpSession session) {
    // 生成验证码
    String text = kaptchaProducer.createText();
    BufferedImage image = kaptchaProducer.createImage(text);

    // 将验证码存入 session
    session.setAttribute("kaptcha", text);

    // 验证码的归属
    String kaptchaOwner = CommunityUtil.generateUUID();
    Cookie cookie = new Cookie("kaptchaOwner", kaptchaOwner);
    cookie.setMaxAge(60);
    cookie.setPath(contextPath);
    response.addCookie(cookie);

    // 将图片输出给浏览器
    response.setContentType("image/png");

    try {
        OutputStream os = response.getOutputStream();
        ImageIO.write(image, "png", os);
    } catch (IOException e) {
        logger.error("响应验证码失败：",e.getMessage());
    }
}

```

### 登录

UserService 类的 login() 和 register() 在检查用户信息正确性的逻辑上类似，需要对账号进行非空、存在和激活的判断，对密码进行非空和正确的判断。

登录成功后生成登录凭证 LoginTicket，包含用户 ID 和过期时间等。LoginTicket 会被放到 Cookie 中。

然后会重定向到首页表示登录成功。

### 登录信息

LoginTicket 类保存了用户的登录信息。后续可以通过浏览器的 Cookie 中保存的用户信息实现自动登录。

通过 HostHolder 类持有用户信息，代替 Session，使用的是 ThreadLocal 存储用户信息。

**ThreadLocal 的优势**

- 浏览器访问服务器，是多对一的方式，服务器会使用独立的线程处理每个浏览器的请求，所以处理请求是一个多线程的环境，需要考虑线程之间的隔离，因此使用了 ThreadLocal。

拦截器 LoginTicketInterceptor 的 preHandle() 使用 CookieUtil 类获得 ticket，然后根据 ticket 得到对应的 User 并存入 HostHolder 中。postHandle() 和 afterCompletion() 则分别完成在页面上登记 User 和清除 HostHolder。

拦截器需要在 WebMvcConfig 的 addInterceptors() 中注册才能生效，然后在请求进入 MVC 框架后都可以使用 HostHolder 获取 User。

```java

@Component
public class LoginTicketInterceptor implements HandlerInterceptor {

    @Autowired
    private UserService userService;

    @Autowired
    private HostHolder hostHolder;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 从 cookie 中获取凭证
        String ticket = CookieUtil.getValue(request, "ticket");
        if (ticket != null) {
            LoginTicket loginTicket = userService.findLoginTicket(ticket);
            if (loginTicket != null && loginTicket.getStatus() == 0 && loginTicket.getExpired().after(new Date())) {
                User user = userService.findUserById(loginTicket.getUserId());
                hostHolder.setUser(user);

                // 构建用户认证的结果，并存入 SecurityContext，以便于 Security 进行授权
                Authentication authentication = new UsernamePasswordAuthenticationToken(user, user.getPassword(), userService.getAuthorities(user.getId()));
                SecurityContextHolder.setContext(new SecurityContextImpl(authentication));
            }
        }

        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        User user = hostHolder.getUser();
        if (user != null && modelAndView != null) {
            modelAndView.addObject("loginUser", user);
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        hostHolder.clear();
        SecurityContextHolder.clearContext();
    }
}

```

@LoginRequired 这个自定义注解检查登录状态，用在用户设置和上传头像上。同样也是使用拦截器实现，preHandle() 检查是否已经登录，没登录则重定向到登录界面。

```java

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginRequired {}
...
@Component
public class LoginRequiredInterceptor implements HandlerInterceptor {

    @Autowired
    private HostHolder hostHolder;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (handler instanceof HandlerMethod) {
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            Method method = handlerMethod.getMethod();
            LoginRequired loginRequired = method.getAnnotation(LoginRequired.class);
            if (loginRequired != null && hostHolder.getUser() == null) {
                response.sendRedirect(request.getContextPath() + "/login");
                return false;
            }
        }

        return true;
    }
}

```

### 上传头像

File 类文件上传，并且可以通过 url 访问头像图片。这里是保存文件到本地，后续会改为上传到云端。

```java

@LoginRequired
@RequestMapping(path = "/upload", method = RequestMethod.POST)
public String uploadHeader(MultipartFile headerImage, Model model) {
    if (headerImage == null) {
        model.addAttribute("error", "您还没有选择图片！");
        return "/site/setting";
    }

    String filename = headerImage.getOriginalFilename();
    String suffix = filename.substring(filename.lastIndexOf("."));
    if (StringUtils.isBlank(suffix)) {
        model.addAttribute("error", "文件格式不正确！");
        return "/site/setting";
    }

    filename = CommunityUtil.generateUUID() + suffix;
    File dest = new File(uploadPath + "/" + filename);
    try {
        headerImage.transferTo(dest);
    } catch (IOException e) {
        logger.error("上传文件失败：");
        throw new RuntimeException("上传文件失败，服务器发生异常！", e);
    }

    // 把用户头像的路径变为 web 访问路径
    // http://localhost:8080/community/user/header/xxx.png
    User user = hostHolder.getUser();
    String headerUrl = domain + contextPath + "/user/header/" + filename;
    userService.updateHeader(user.getId(), headerUrl);

    return "redirect:/index";
}

```

## 发帖评论

发帖和评论是论坛类网站核心的两个功能。

### 敏感词过滤

使用 Trie 树的数据结构，SensitiveFilter 类从敏感词文件 sensitive_word.txt 读取关键词，生成 Trie 树。filter() 将敏感词转换为特殊字符。

```java

@Component
public class SensitiveFilter {

    private static final Logger logger = LoggerFactory.getLogger(SensitiveFilter.class);

    private static final String REPLACEMENT = "***";

    private TrieNode root = new TrieNode();

    @PostConstruct
    public void init() {
        try {
            InputStream is = this.getClass().getClassLoader().getResourceAsStream("sensitive_word.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String keyword;
            while ((keyword = reader.readLine()) != null) {
                this.addKeyword(keyword);
            }
        } catch (IOException e) {
            logger.error("加载敏感词文件失败！" + e.getMessage());
        }
    }

    private void addKeyword(String keyword) {
        TrieNode tempNode = root;
        for (int i = 0; i < keyword.length(); i++) {
            char c = keyword.charAt(i);
            TrieNode subNode = tempNode.getSubNode(c);
            if (subNode == null) {
                subNode = new TrieNode();
                tempNode.addSubNode(c, subNode);
            }
            tempNode = subNode;
            if (i == keyword.length() - 1)
                tempNode.setKeywordEnd(true);
        }
    }

    public String filter(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        TrieNode tempNode = root;
        int begin = 0;
        int position = 0;
        StringBuilder sb = new StringBuilder();
        while (position < text.length()) {
            char c = text.charAt(position);
            if (isSymbol(c)) {
                if (tempNode == root) {
                    sb.append(c);
                    begin++;
                }
                position++;
                continue;
            }
            tempNode = tempNode.getSubNode(c);
            if (tempNode == null) {
                sb.append(text.charAt(begin));
                begin++;
                position = begin;
                tempNode = root;
            } else if (tempNode.isKeywordEnd()) {
                sb.append(REPLACEMENT);
                position++;
                begin = position;
                tempNode = root;
            } else {
                position++;
            }
        }
        sb.append(text.substring(begin));

        return sb.toString();
    }

    private boolean isSymbol(Character c) {
        // 0x2E80 - 0x9FFF 是东亚文字
        return !CharUtils.isAsciiAlphanumeric(c) && (c < 0x2E80 || c > 0x9FFF);

    }

    private class TrieNode {
        private boolean isEnd = false;

        private Map<Character, TrieNode> subNodes = new HashMap<>();

        public boolean isKeywordEnd() {
            return isEnd;
        }

        public void setKeywordEnd(boolean keywordEnd) {
            isEnd = keywordEnd;
        }

        public void addSubNode(Character c, TrieNode node) {
            subNodes.put(c, node);
        }

        public TrieNode getSubNode(Character c) {
            return subNodes.get(c);
        }
    }
}

```

### 发帖

使用 AJAX 异步实现网页上的增量不需要刷新网页，而更新到页面上。CommunityUtil 类使用 fastjson 得到 JSON 对象。

在发帖时除了过滤敏感词，还需要转译 HTML 标记。

```java

// 转译 HTML 标记
post.setTitle(HtmlUtils.htmlEscape(post.getTitle()));
post.setContent(HtmlUtils.htmlEscape(post.getContent()));
// 过滤敏感词
post.setTitle(sensitiveFilter.filter(post.getTitle()));
post.setContent(sensitiveFilter.filter(post.getContent()));

```

### 评论

这里的评论可以是对一个帖子的评论，也可以是对评论的回复，还可以是对回复的回复。

Comment 类中 entityType == 1 表示是对帖子的评论， entityType == 2 为回复。entityId 指评论所在的贴子 ID。targetId 是针对回复的回复中，回复对象的 userId:) 如果是对评论的回复，则 targetId 为 0。

```java

public class Comment {
    private int id;
    private int userId;
    private int entityType;
    private int entityId;
    private int targetId;
    private String content;
    private int status;
    private Date createTime;
    ...
}

```

<img src="https://github.com/windwj000/community/blob/master/pics/reply.png" width="75%">

## 私信

发送私信和发帖一样，用到 AJAX 进行网页的异步更新。

看私信时涉及到未读和已读，可以通过改变 Message 类的 status 属性实现。

### 统一异常处理

@ControllerAdvice 修饰 ExceptionAdvice 类，进行全局配置，就可以实现统一的异常处理。

@ExceptionHandler 修饰方法，返回异常信息时需要分为异步请求还是非异步请求。异步即字符串 "XMLHttpRequest"，希望返回的是 JSON 数据。非异步则重定向到 error 页面。

```java

@ControllerAdvice(annotations = Controller.class)
public class ExceptionAdvice {

    private static final Logger logger = LoggerFactory.getLogger(ExceptionAdvice.class);

    @ExceptionHandler(Exception.class)
    public void handleException(Exception e, HttpServletRequest request, HttpServletResponse response) throws IOException {
        logger.error("服务器发生异常：" + e.getMessage());
        for (StackTraceElement element : e.getStackTrace()) {
            logger.error(element.toString());
        }

        String xRequestedWith = request.getHeader("x-requested-with");
        // 表示异步请求，希望返回 XML
        if ("XMLHttpRequest".equals(xRequestedWith)) {
            response.setContentType("application/plain;charset=utf-8");
            PrintWriter writer = response.getWriter();
            writer.write(CommunityUtil.getJSONString(1, "服务器异常！"));
        } else {
            response.sendRedirect(request.getContextPath()+"/error");
        }
    }
}

```

### 统一记录日志

用到 AOP 思想，通过创建一个切面 ServiceLogAspect 类，实现日志功能，记录用户访问某个 Service 的具体某个方法。

```java

@Aspect
public class ServiceLogAspect {

    private static final Logger logger = LoggerFactory.getLogger(ServiceLogAspect.class);

    @Pointcut("execution(* com.jieb.community.service.*.*(..))")
    public void pointcut() {

    }

    @Before("pointcut()")
    public void before(JoinPoint joinPoint) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        // 增加生产者消费者之后会有空的可能
        if (attributes == null) {
            return;
        }
        HttpServletRequest request = attributes.getRequest();
        String ip = request.getRemoteHost();
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String target = joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName();
        logger.info(String.format("用户[%s]，在[%s]，访问了[%s]。", ip, now, target));
    }

}

```

## 点赞关注

### 点赞

用到了 RedisTemplate 来对 Redis 的基础数据类型进行操作，如 redisTemplate.opsForValue().set(redisKey, text, 60, TimeUnit.SECONDS)。

Redis 中存储数据的 key 都是 String 类型，需要利用 RedisKeyUtil 这个工具类将属性拼接成字符串后存入 Redis。

```java

private static final String SPLIT = ":";
private static final String PREFIX_ENTITY_LIKE = "like:entity";
private static final String PREFIX_USER_LIKE = "like:user";

// 某个实体的赞
public static String getEntityLikeKey(int entityType, int entityId) {
    return PREFIX_ENTITY_LIKE + SPLIT + entityType + SPLIT + entityId;
}

// 某个用户的赞
public static String getUserLikeKey(int userId) {
    return PREFIX_USER_LIKE + SPLIT + userId;
}

```

点赞分为对帖子或回复的赞，以及用户收到的所有赞。用 userLikeKey 记录用户收到点赞的数量，entityLikeKey 记录某个帖子或回复收到点赞的数量。

一次点赞，二次则是取消点赞，使用 isMember 标志表示，通过 Redis 的 Set 数据结构来实现。

对于两个操作需要用到 Redis 事务的开启与执行 multi() 和 exec()。

```java

// 点赞
public void like(int userId, int entityType, int entityId,int entityUserId) {
    // 使用事务
    redisTemplate.execute(new SessionCallback() {
        @Override
        public Object execute(RedisOperations redisOperations) throws DataAccessException {
            String entityLikeKey = RedisKeyUtil.getEntityLikeKey(entityType, entityId);
            String userLikeKey = RedisKeyUtil.getUserLikeKey(entityUserId);
            // 某人是否对已经某帖子进行点赞
            Boolean isMember = redisOperations.opsForSet().isMember(entityLikeKey, userId);

            redisOperations.multi();

            if (isMember) {
                redisOperations.opsForSet().remove(entityLikeKey, userId);
                redisOperations.opsForValue().decrement(userLikeKey);
            } else {
                redisOperations.opsForSet().add(entityLikeKey, userId);
                redisOperations.opsForValue().increment(userLikeKey);
            }
            return redisOperations.exec();
        }
    });
}

```

### 关注

关注的对象可以是用户、帖子、评论。和点赞的区别在于：key 中需要包含关注者和被关注者这两个变量。

```java

private static final String SPLIT = ":";
private static final String PREFIX_FOLLOWEE= "followee";
private static final String PREFIX_FOLLOWER= "follower";

// 某个用户关注的实体
public static String getFolloweeKey(int userId, int entityType) {
    return PREFIX_FOLLOWEE + SPLIT + userId + SPLIT + entityType;
}

// 某个实体拥有的粉丝
public static String getFollowerKey(int entityType, int entityId) {
    return PREFIX_FOLLOWER + SPLIT + entityType + SPLIT + entityId;
}

```

同时，关注用到了 Redis 的 ZSet 数据结构，可以实现按（被）关注时间顺序做排序显示。

### 登录模块优化

使用 Redis 缓存来优化性能，是本项目的一大亮点。

**Redis 代替 Session 存储验证码**

- 由于验证码需要频繁的访问与刷新，对性能要求较高。

- 验证码不需要永久保存，通常在很短时间就失效。

- 还有考虑到分布式部署时都从 Redis 读，避免了 Session 共享问题。

具体做法是生成验证码时分别放到 Cookie 和 Redis 中，在登录检查验证码时取出 Cookie，这个值作为 key 查询到 Redis 里存储的验证码，进行验证。

```java

// 登录验证码
public static String getKaptchaKey(String owner) {
    return PREFIX_KAPTCHA + SPLIT + owner;
}
...
// 验证码的归属
String kaptchaOwner = CommunityUtil.generateUUID();
Cookie cookie = new Cookie("kaptchaOwner", kaptchaOwner);

// 将验证码存入 Redis
String redisKey = RedisKeyUtil.getKaptchaKey(kaptchaOwner);
redisTemplate.opsForValue().set(redisKey, text, 60, TimeUnit.SECONDS);

```

**存储登录凭证**

每次请求都要查询 LoginTicket，访问频率很高。

**缓存用户信息**

通过查看 ServiceLogAspect 类记录的日志，发现查询用户信息的方法 findUserById() 的访问频率很高，改为用 Redis 存放用户信息。

既然是缓存，就有取不到值的情况，这时候就要从数据库里查，然后放到缓存中。如果用户信息经常改变，那么从缓存取到的用户信息就存在一定的脏数据，这也是性能和一致性之间的权衡。

## 系统通知

Kafka 消息队列同样也能优化性能，是本项目的另一大亮点。

很多原先在 controller 层实现的逻辑，被转移到了 EventConsumer 类中实现，会享有消息队列的削峰解耦异步的优势。例如 ES 相关的更新或者删除帖子的操作，与点赞关注等的关联性不是很强，串行执行的话会影响系统性能，适合异步操作。

**Kafka 的优势**

- Kafka 可分为多个 Topic，每个 Topic 又分为多个 Partition，多线程可以同时向多个 Partition 中写数据，增强并发能力。

Event 事件类，事件包括 DiscussPost 帖子事件，Comment 相关的评论回复事件，点赞关注事件，以及后续的分享长图事件。

Kafka 的生产者通过 KafkaTemplate 操作这个 Event。

```java

@Component
public class EventProducer {

    @Autowired
    private KafkaTemplate kafkaTemplate;

    // 处理事件
    public void fireEvent(Event event) {
        // 将事件发布到主题
        kafkaTemplate.send(event.getTopic(), JSONObject.toJSONString(event));
    }
}

```

消费者需要在方法上使用 @KafkaListener 注解消费对应主题的消息。

```java

@KafkaListener(topics = {TOPIC_COMMENT, TOPIC_LIKE, TOPIC_FOLLOW})
public void handleCommentMessage(ConsumerRecord record) {}

```

消费的结果是通过复用 Message 类发送系统通知，用 fromId 为 1 表示是由系统发来的。

显示消息的时候注意每个请求都要显示消息数量，因此很直接地想到用拦截器 MessageInterceptor，统计未读的通知和私信。做法和拦截器 LoginTicketInterceptor 类似。

## 搜索

针对帖子进行搜索，用注解的形式在 DiscussPost 这个实体类上完成 ES 的注解。

```java

// ES 设置
@Document(indexName = "discusspost",type = "_doc",shards = 6,replicas = 3)
public class DiscussPost {

    @Id
    private int id;

    @Field(type = FieldType.Integer)
    private int userId;

    // 两种分词器
    @Field(type = FieldType.Text,analyzer = "ik_max_word",searchAnalyzer = "ik_smart")
    private String title;

    @Field(type = FieldType.Text,analyzer = "ik_max_word",searchAnalyzer = "ik_smart")
    private String content;

    @Field(type = FieldType.Integer)
    private int type;

    @Field(type = FieldType.Integer)
    private int status;

    @Field(type = FieldType.Date)
    private Date createTime;

    @Field(type = FieldType.Integer)
    private int commentCount;

    @Field(type = FieldType.Double)
    private double score;
    ...
}

```

在 ElasticsearchService 类中，用 ElasticsearchRestTemplate 类的 queryForPage() 进行搜索。

对于改动了 DiscussPost 的事件，需要 Elasticsearch 和 Kafka 事件配合，EventConsumer 类将发帖和评论保存到 Elasticsearch 服务器中。

## 其他

### 认证授权

废弃了之前采用拦截器实现的登录检查，使用 Spring Security，在 SecurityConfig 类中 configure() 配置了：

- 忽略对静态资源的访问

- 授权，分为管理员、版主和普通用户。版主能够置顶和加精，管理员能够删帖和进行项目管理相关的操作。

- 没有登录或未被授权时的处理

并在 LoginTicketInterceptor 中构建用户认证的结果，并存入 SecurityContext 中，方便 Spring Security 授权。

### 置顶、加精、删除

在获取到三个操作对应的权限后，实现这三个操作就是改变 DiscussPost 的属性 type 和 status。

置顶、加精和删除都会触发对应的事件，并且需要在 EventConsumer 类中消费。

### Redis 高级数据结构

网站需要对于独立访客 UV 和日活跃用户 DAU 进行统计。

#### HyperLogLog

采用基数算法进行统计，占用空间少，有较小误差。

用于统计 UV，根据 IP 进行统计，并且可以统计指定日期范围的 UV。

redisTemplate.opsForHyperLogLog()。

#### Bitmap

可以看成 byte[]，适合存储大量连续的布尔值。

用于统计 DAU，根据用户 ID 进行统计，同样可以统计指定日期范围的 DAU。

redisTemplate.opsForValue().setBit()。

### 任务执行和调度

任务调度器 Quartz 实现刷新帖子分数的任务，需要配置 RefreshJobDetail() 和 RefreshTrigger()。

**使用 Quartz 相比 JDK 的 ScheduledExecutorService 和 Spring 的 ThreadPoolTaskScheduler 的优势**

- Quartz 实现定时任务所依赖的参数是保存在数据库中，数据库只有一份，所以不会冲突。

- 而 ScheduledExecutorService 和 ThreadPoolTaskScheduler 是基于内存的，在分布式环境中，多台服务器会重复执行定时任务，产生冲突。

### 帖子热度排行

PostScoreRefreshJob 类的 refresh() 中加入计算帖子分数的公式：log(精华分+评论数*10+点赞数*2+收藏数*2)+(发布时间-纪元)。时间和分数对应的曲线如下图：

<img src="https://github.com/windwj000/community/blob/master/pics/score_time.png" width="50%">

可以看到随着帖子的热度下降的形势。

配合 Quartz 刷新帖子分数，我们可以通过 orderMode 的值，在网站首页按照最新和最热排序帖子。

### 分享长图

用到 wk 将 HTML 转换为 PDF 或 image 格式的图片。

由于使用 wk 转换图片实际是一条命令行的命令，我们用 Runtime.getRuntime().exec(command) 执行。

### 文件上传到云服务器

使用第三方平台，需要在配置文件中加入第三方授予的 access 和 secret。

对于之前的上传头像和分享的 wk 长图业务进行重构，使用云服务器存储。分别为客户端上传和服务器直传。

上传头像需要客户端（即浏览器的页面上）提交表单，表单数据再提交到云服务器上。

分享的 wk 长图中使用 UploadTask 任务监视，同时使用 Future 定时器返回执行结果，一旦生成长图就上传至云服务器，不经过客户端。

```java

class UploadTask implements Runnable {

        private String fileName;

        private String suffix;

        private Future future;

        // 保障任务能停止
        private long startTime;
        private int uploadTimes;

        public UploadTask(String fileName, String suffix) {
            this.fileName = fileName;
            this.suffix = suffix;
            this.startTime = System.currentTimeMillis();
        }

        public void setFuture(Future future) {
            this.future = future;
        }

        @Override
        public void run() {
            // 生成图片失败
            if (System.currentTimeMillis() - startTime > 30000) {
                logger.error("执行时间过长，终止任务：" + fileName);
                future.cancel(true);
                return;
            }
            // 上传云服务失败
            if (uploadTimes >= 3) {
                logger.error("上传次数太多，终止任务：" + fileName);
                future.cancel(true);
                return;
            }

            String path = wkImageStorage + "/" + fileName + suffix;
            File file = new File(path);
            if (file.exists()) {
                logger.info(String.format("开始第%d次上传[%s].", ++uploadTimes, fileName));
                // 设置响应信息
                StringMap policy = new StringMap();
                policy.put("returnBody", CommunityUtil.getJSONString(0));

                // 生成上传凭证
                Auth auth = Auth.create(accessKey, secretKey);
                String uploadToken = auth.uploadToken(shareBucketName, fileName, 3600, policy);

                // 指定上传机房，和客户端上传不一样
                UploadManager uploadManager = new UploadManager(new Configuration(Zone.zone0()));
                try {
                    // 开始上传图片
                    Response response = uploadManager.put(path, fileName, uploadToken, null, "image/" + suffix, false);
                    // 处理响应结果
                    JSONObject jsonObject = JSONObject.parseObject(response.bodyString());
                    if (jsonObject == null || jsonObject.get("code") == null || !jsonObject.get("code").toString().equals("0")) {
                        logger.info(String.format("第%d次上传失败[%s].", uploadTimes, fileName));
                    } else {
                        logger.info(String.format("第%d次上传成功[%s].", uploadTimes, fileName));
                        future.cancel(true);
                    }
                } catch (QiniuException e) {
                    logger.info(String.format("第%d次上传失败[%s].", uploadTimes, fileName));
                }
            } else {
                logger.info("等待图片生成[" + fileName + "].");
            }
        }
    }
}

```

### 多级缓存

1. 首先是本地缓存 Caffeine，缓存帖子列表和帖子总数。当访问网站首页时，可以直接从缓存中拿到帖子。

2. 再用分布式缓存 Redis 作为二级缓存。

进行一个小实验：造 30W 数据，JMeter 压测发现缓存提高了 10 倍的吞吐量。

### 项目部署

Docker

