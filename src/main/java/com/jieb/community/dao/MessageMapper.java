package com.jieb.community.dao;

import com.jieb.community.entity.Message;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MessageMapper {

    // 查询当前用户的会话列表，每个会话只返回最新的一条消息
    List<Message> selectConversation(int userId, int offset, int limit);

    // 查询当前用户的会话数量
    int selectConversationCount(int userId);

    // 查询一个会话的私信列表
    List<Message> selectLetter(String conversationId, int offset, int limit);

    // 查询一个会话的私信数量
    int selectLetterCount(String conversationId);

    // 查询未读私信的数量
    int selectLetterUnreadCount(int userId,String conversationId);

    // 新增消息
    int insertMessage(Message message);

    // 更改消息状态
    int updateStatus(List<Integer> ids, int status);

    // 查询某个主题下最新的通知
    Message selectLatestNotice(int userId, String topic);

    // 查询某个主题包含通知的数量
    int selectNoticeCount(int userId, String topic);

    // 查询未读通知的数量
    int selectUnreadNoticeCount(int userId, String topic);

    // 查询某个主题所包含的通知列表
    List<Message> selectNotices(int userId, String topic, int offset, int limit);
}
