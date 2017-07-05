package com.alibaba.dubbo.monitor.simple.redis;

import redis.clients.jedis.Jedis;
import redis.clients.util.Pool;

/**
 * Created by gaopengju on 2017/7/5.
 */
public class RedisTemplate extends AbstractRedisTemplate{

    public void set(final String key, final String value){
        exec(new Callback<String>() {
            public String exec(Jedis jedis) {
                return  jedis.set(key,value);
            }
        });
    }

    public void rpush(final String key, final String... value){
        exec(new Callback<Long>() {
            public Long exec(Jedis jedis) {
                return  jedis.rpush(key,value);
            }
        });
    }

}
