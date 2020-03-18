package com.jieb.community.util;

import com.jieb.community.entity.User;
import org.springframework.stereotype.Component;

/**
 * 持有用户信息，用于代替 Session
 */
@Component
public class HostHolder {
    private ThreadLocal<User> users = new ThreadLocal<>();

    public void setUser(User user) {
        users.set(user);
    }

    public User getUser() {
        return users.get();
    }

    public void clear() {
        users.remove();
    }
}
