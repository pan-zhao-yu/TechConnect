package com.zhaoyu.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhaoyu.config.RedissionConfig;
import com.zhaoyu.dto.Result;
import com.zhaoyu.entity.SeckillVoucher;
import com.zhaoyu.entity.VoucherOrder;
import com.zhaoyu.mapper.VoucherOrderMapper;
import com.zhaoyu.service.ISeckillVoucherService;
import com.zhaoyu.service.IVoucherOrderService;
import com.zhaoyu.utils.RedisIdWorker;
import com.zhaoyu.utils.SimpleRedisLock;
import com.zhaoyu.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;


@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {


    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    RedissonClient redissonClient;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Transactional
    @Override
    public Result seckillVoucher(Long voucherId){

        //search voucher by id
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        //if begin time before curr && expire time after curr
        // if not return error
        if(seckillVoucher.getBeginTime().isAfter(LocalDateTime.now()) || seckillVoucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("voucher time not valid");
        }
        //if stock > 1
        // if not, return error
        if(seckillVoucher.getStock() < 1){
            return Result.fail("stock not available");
        }


        Long userId = UserHolder.getUser().getId();
//        SimpleRedisLock lock = new SimpleRedisLock("order" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
        if(!isLock){
            return Result.fail("no more order");
        }

        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.creatvoucherOrder(voucherId);
        }finally {
            lock.unlock();
        }

    }

    @Transactional
    public Result creatvoucherOrder(Long voucherId) {
        //one person one seckillVoucher
        int count = query().eq("user_id", UserHolder.getUser().getId()).eq("voucher_id", voucherId).count();

        if (count > 0) {
            return Result.fail("you can only buy seckill voucher once");
        }

        //update new stock
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            return Result.fail("stock not available");
        }
        //create new order
        VoucherOrder voucherOrder = new VoucherOrder();
        //set order id
        Long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //set user id
        voucherOrder.setUserId(UserHolder.getUser().getId());
        //set voucher id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        //return order id
        return Result.ok(orderId);
    }
}
