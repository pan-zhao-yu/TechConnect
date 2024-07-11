package com.zhaoyu.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhaoyu.controller.VoucherOrderController;
import com.zhaoyu.dto.Result;
import com.zhaoyu.entity.VoucherOrder;

public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    Result creatvoucherOrder(Long voucherId);
}
