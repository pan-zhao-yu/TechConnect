package com.zhaoyu.utils;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Data
@Component
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
