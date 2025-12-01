/*
 * Copyright (c) Zhongzao Software Co. LTD 2022-2062 All rights reserved
 */

package com.futurecraftsmen.pms.technical.service.domain.collaborate.update;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.futurecraftsmen.pms.api.mybatis.MyBatisMark;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * @author chengxinyu
 * @description 销售方协作安排数据表, 产品已出完 更新
 * @organization futurecraftsmen
 * @date 2025-07-03 15:27
 */
@Data
@TableName(autoResultMap = true, value = "${dynamicTableNameProxy.collaborateScheduleItem}")
public class CollaborateScheduleItemProductOutFinishUpdate implements Serializable, MyBatisMark {

	@Serial
	private static final long serialVersionUID = -260407439774866191L;


	/**
	 * @description 数据唯一编号
	 */
	@TableId(value = "primary_key")
	private long collaborateCode;


	/**
	 * @description 产品是否出完
	 */
	@TableField(value = "product_out_finish")
	private boolean productOutFinish;

}
