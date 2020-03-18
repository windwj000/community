package com.jieb.community.controller;

import com.jieb.community.entity.Comment;
import com.jieb.community.entity.DiscussPost;
import com.jieb.community.entity.Page;
import com.jieb.community.entity.User;
import com.jieb.community.service.CommentService;
import com.jieb.community.service.DiscussPostService;
import com.jieb.community.service.LikeService;
import com.jieb.community.service.UserService;
import com.jieb.community.util.CommunityConstant;
import com.jieb.community.util.CommunityUtil;
import com.jieb.community.util.HostHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.*;

@Controller
@RequestMapping("/discuss")
public class DiscussPostController implements CommunityConstant {

    @Autowired
    private DiscussPostService discussPostService;

    @Autowired
    private HostHolder hostHolder;

    @Autowired
    private UserService userService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private LikeService likeService;

    @RequestMapping(path = "/add", method = RequestMethod.POST)
    @ResponseBody
    public String addDiscussPost(String title, String content) {
        User user = hostHolder.getUser();
        if (user == null)
            return CommunityUtil.getJSONString(403, "你还没有登录！");
        DiscussPost discussPost = new DiscussPost();
        discussPost.setUserId(user.getId());
        discussPost.setTitle(title);
        discussPost.setContent(content);
        discussPost.setCreateTime(new Date());
        discussPostService.addDiscussPost(discussPost);

        return CommunityUtil.getJSONString(0, "发布成功！");
    }

    @RequestMapping(path = "/detail/{discussPostId}", method = RequestMethod.GET)
    public String getDiscussPost(@PathVariable("discussPostId") int discussPostId, Model model, Page page) {
        DiscussPost post = discussPostService.findDiscussPostById(discussPostId);
        model.addAttribute("post", post);

        User user = userService.findUserById(post.getUserId());
        model.addAttribute("user", user);

        // 点赞数量和状态
        long likeCount = likeService.fingEntityLikeCount(ENTITY_TYPE_POST, discussPostId);
        model.addAttribute("likeCount", likeCount);

        // 考虑用户是否登录
        int likeStatus = hostHolder.getUser()==null?0:
                likeService.fingEntityLikeStatus(hostHolder.getUser().getId(), ENTITY_TYPE_POST, discussPostId);
        model.addAttribute("likeStatus", likeStatus);

        // 评论分页
        page.setLimit(5);
        page.setPath("/discuss/detail/"+discussPostId);
        page.setRows(post.getCommentCount());

        // 给帖子的评论
        List<Comment> commentList = commentService.findCommentByEntity(ENTITY_TYPE_POST, post.getId(), page.getOffset(), page.getLimit());
        List<Map<String, Object>> commentViewObjectList = new ArrayList<>();
        if (commentList != null) {
            for (Comment comment : commentList) {
                Map<String, Object> commentViewObject = new HashMap<>();
                commentViewObject.put("comment", comment);
                commentViewObject.put("user", userService.findUserById(comment.getUserId()));

                // 点赞数量和状态
                likeCount = likeService.fingEntityLikeCount(ENTITY_TYPE_COMMENT, comment.getId());
                commentViewObject.put("likeCount", likeCount);

                // 考虑用户是否登录
                likeStatus = hostHolder.getUser()==null?0:
                        likeService.fingEntityLikeStatus(hostHolder.getUser().getId(), ENTITY_TYPE_COMMENT, comment.getId());
                commentViewObject.put("likeStatus", likeStatus);

                // 给评论的回复
                List<Comment> replyList = commentService.findCommentByEntity(ENTITY_TYPE_COMMENT, comment.getId(), 0, Integer.MAX_VALUE);
                List<Map<String, Object>> replyViewObjectList = new ArrayList<>();
                if (replyList != null) {
                    for (Comment reply : replyList) {
                        Map<String, Object> replyViewObject = new HashMap<>();
                        replyViewObject.put("reply", reply);
                        replyViewObject.put("user", userService.findUserById(reply.getUserId()));
                        User target = reply.getTargetId() == 0 ? null : userService.findUserById(reply.getTargetId());
                        replyViewObject.put("target", target);

                        // 点赞数量和状态
                        likeCount = likeService.fingEntityLikeCount(ENTITY_TYPE_COMMENT, reply.getId());
                        replyViewObject.put("likeCount", likeCount);

                        // 考虑用户是否登录
                        likeStatus = hostHolder.getUser()==null?0:
                                likeService.fingEntityLikeStatus(hostHolder.getUser().getId(), ENTITY_TYPE_COMMENT, reply.getId());
                        replyViewObject.put("likeStatus", likeStatus);

                        replyViewObjectList.add(replyViewObject);
                    }
                }
                commentViewObject.put("replys",replyViewObjectList);

                // 回复数量
                int replyCount = commentService.findCommentCount(ENTITY_TYPE_COMMENT, comment.getId());
                commentViewObject.put("replyCount",replyCount);

                commentViewObjectList.add(commentViewObject);
            }
        }

        model.addAttribute("comments", commentViewObjectList);

        return "/site/discuss-detail";
    }
}
