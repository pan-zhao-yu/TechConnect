package com.zhaoyu.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhaoyu.dto.Result;
import com.zhaoyu.entity.Shop;
import com.zhaoyu.mapper.ShopMapper;
import com.zhaoyu.service.IShopService;
import com.zhaoyu.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.zhaoyu.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedisData redisData;


    public Result queryById(Long id){
        // solve Cache Penetration by using cache null value
        //Shop shop = queryWithPassThrough(id);

        // solve Cache Breakdown by using cache Mutex lock
        Shop shop = queryWithMutex(id);

        // solve Cache Breakdown by using cache Logical Expire
        //Shop shop = queryWithLogicalExpire(id);

        if(shop == null){
            return Result.fail("shop not exist");
        }
        return Result.ok(shop);
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public Shop queryWithLogicalExpire(Long id){
        //search shop from redis cache
        String shopKey = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        //check if exist
        if(StrUtil.isBlank(shopJson)){
            //if not in redis, return null(not hot key)
            return null;
        }
        //deserilisation json to object
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //check if expire
        //if not, return
        if(expireTime.isAfter(LocalDateTime.now())){
            return shop;
        }
        //expire, try to get lock
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //if lock exist
        //return old info
        if(isLock){
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.saveShopToRedis(id, 60L);
                }catch(Exception e){
                    throw new RuntimeException(e);
                }finally{
                    unlock(lockKey);
                }
            });
        }
        //if no lock exist
        //new thread to update info, current thread return old info

        //new thread after update, release lock
        return shop;
    }


    public Shop queryWithMutex(Long id){
        //search shop from redis cache
        String shopKey = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        //check if exist
        if(StrUtil.isNotBlank(shopJson)){
            //if exist, return
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        if(shopJson != null){
            return null;
        }

        //rebuild cache
        //get mutex lok
        String lockKey = "lock:shop:" + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            //if get success
            if(isLock){
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //if fail, sleep and retry

            //if not exist, search shop by id from database
            shop = getById(id);
            //if shop not exist, return 404
            if(shop == null){
                stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //if shop exist, save to redis, return shop
            stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop));
            stringRedisTemplate.expire(shopKey, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            unlock(lockKey);
        }
        return shop;
    }

    public Shop queryWithPassThrough(Long id){
        //search shop from redis cache
        String shopKey = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        //check if exist
        if(StrUtil.isNotBlank(shopJson)){
            //if exist, return
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        if(shopJson != null){
            return null;
        }
        //if not exist, search shop by id from database
        Shop shop = getById(id);
        //if shop not exist, return 404
        if(shop == null){
            stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //if shop exist, save to redis, return shop
        stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop));
        stringRedisTemplate.expire(shopKey, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return null;
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }


    public void saveShopToRedis(Long id, Long expireSecpnds){
        Shop shop = getById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSecpnds));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    @Override
    @Transactional
    public Result update(Shop shop){
        //when modify database
        //update database first
        updateById(shop);
        //then delete cache from redis for better data integrity
        Long id = shop.getId();
        if(id == null){
            return Result.fail("update failed, shop id cannot be null");
        }
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

}
