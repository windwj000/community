package com.jieb.community.dao;

import com.jieb.community.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@Mapper
public interface UserMapper {

    User selectById(int id);

    User selectByName(String username);

    User selectByEmail(String email);

    int insertUser(User user);

    int updatePassword(int id, String password);

    int updateStatus(int id, int status);

    int updateHeader(int id, String headerUrl);

}
