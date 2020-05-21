package com.jieb.community.quartz;

import com.jieb.community.entity.DiscussPost;
import com.jieb.community.service.DiscussPostService;
import com.jieb.community.service.ElasticsearchService;
import com.jieb.community.service.LikeService;
import com.jieb.community.util.CommunityConstant;
import com.jieb.community.util.RedisKeyUtil;
import io.lettuce.core.RedisURI;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

// 这里选择将定时任务的数据入库，避免数据直接存在内存中，因应用重启造成的数据丢失和做集群控制。
public class PostScoreRefreshJob implements Job, CommunityConstant {
    private static final Logger logger = LoggerFactory.getLogger(PostScoreRefreshJob.class);

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private DiscussPostService discussPostService;

    @Autowired
    private LikeService likeService;

    @Autowired
    private ElasticsearchService elasticsearchService;

    // 牛客纪元
    private static final Date epoch;

    static{
        try {
            epoch = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2014-08-01 00:00:00");
        } catch (ParseException e) {
            throw new RuntimeException("初始化牛客纪元失败！", e);
        }
    }

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        String redisKey = RedisKeyUtil.getPostScoreKey();
        BoundSetOperations boundSetOperations = redisTemplate.boundSetOps(redisKey);

        if (boundSetOperations.size() == 0) {
            logger.info("任务取消！没有需要刷新的帖子！");
            return;
        }

        logger.info("任务开始！正在刷新帖子分数："+boundSetOperations.size());
        while (boundSetOperations.size() > 0) {
            this.refresh((Integer) boundSetOperations.pop());
        }
        logger.info("任务结束！帖子分数刷新完毕！");
    }

    private void refresh(int postId) {
        DiscussPost discussPost = discussPostService.findDiscussPostById(postId);

        if (discussPost == null) {
            logger.error("该帖子不存在：id="+postId);
            return;
        }

        // 是否为精华
        boolean wonderful=discussPost.getStatus()==1;
        // 评论数量
        int commentCount = discussPost.getCommentCount();
        // 点赞数量
        long likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_POST, postId);

        // 帖子分数 log(精华分+评论数*10+点赞数*2+收藏数*2)+(发布时间-纪元)
        double score = Math.log10(Math.max((wonderful ? 75 : 0) + commentCount * 10 + likeCount * 2, 1)) +
                (discussPost.getCreateTime().getTime() - epoch.getTime()) / (1000 * 3600 * 24);

        // 更新帖子分数
        discussPostService.updateScore(postId, score);

        // 同步搜索数据
        discussPost.setScore(score);
        elasticsearchService.saveDiscussPost(discussPost);
    }
}
