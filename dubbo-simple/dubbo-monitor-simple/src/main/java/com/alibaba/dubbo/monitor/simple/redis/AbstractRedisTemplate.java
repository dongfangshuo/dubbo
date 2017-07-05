package com.alibaba.dubbo.monitor.simple.redis;

import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.monitor.simple.SimpleMonitorService;
import redis.clients.jedis.Jedis;
import redis.clients.util.Pool;

/**
 * Created by gaopengju on 2017/7/5.
 */
public abstract class AbstractRedisTemplate {
    private static final Logger logger = LoggerFactory.getLogger(AbstractRedisTemplate.class);

    private Pool<Jedis> pool;

    public Pool<Jedis> getPool() {
        return pool;
    }

    public void setPool(Pool<Jedis> pool) {
        this.pool = pool;
    }

    protected <T> T exec(Callback<T> callback){
        Jedis jedis = pool.getResource();
        try {
           return callback.exec(jedis);
        } catch (Exception e) {
            logger.error(e);
        } finally {
            pool.returnResource(jedis);
        }
        return null;
    }

    protected interface Callback<T>{
        public T exec(Jedis jedis);
    }
}
