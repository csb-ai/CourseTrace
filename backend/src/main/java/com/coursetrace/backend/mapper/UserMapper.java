package com.coursetrace.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.coursetrace.backend.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    //MyBatis-Plus自动搞定增删改查
}