package com.jieb.community.util;

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

    /**
     * 默认状态的登录凭证超时时间
     */
    int DEFAULT_EXPIRED_SECONDS=3600*12;

    /**
     * 记住状态的登录凭证超时时间
     */
    int REMENBER_EXPIRED_SECONDS=3600*24*100;

    /**
     * 帖子的实体类型
     */
    int ENTITY_TYPE_POST=1;

    /**
     * 评论的实体类型
     */
    int ENTITY_TYPE_COMMENT=2;

    /**
     * 用户的实体类型
     */
    int ENTITY_TYPE_USER=3;

    /**
     * 主题：评论
     */
    String TOPIC_COMMENT = "comment";

    /**
     * 主题：点赞
     */
    String TOPIC_LIKE = "like";

    /**
     * 主题：关注
     */
    String TOPIC_FOLLOW = "follow";

    /**
     * 系统用户 ID
     */
    int SYSTEM_USER_ID=1;
}
