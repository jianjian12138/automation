/*
 * Copyright (c) Zhongzao Software Co. LTD 2022-2062 All rights reserved
 */

package com.futurecraftsmen.pms.technical.service.domain.collaborate.update;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.futurecraftsmen.pms.common.domain.serializer.BigDecimalAutoStripTrailingZerosSerializer;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.futurecraftsmen.pms.api.mybatis.MyBatisMark;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * @author chengxinyu
 * @description 销售方协作安排数据表, 已安排量减少
 * @organization futurecraftsmen
 * @date 2025-07-03 15:27
 */
@Data
@TableName(autoResultMap = true, value = "${dynamicTableNameProxy.collaborateScheduleItem}")
public class CollaborateScheduleItemScheduleQuantityDecreaseUpdate implements Serializable, MyBatisMark {

	@Serial
	private static final long serialVersionUID = -260407439774866191L;


	/**
	 * @description 数据唯一编号
	 */
	@TableId(value = "primary_key")
	private long collaborateCode;

	/**
	 * @description 已安排量：初始是 0 ,后续会修改
	 */
	@TableField(value = "schedule_quantity")
	@JsonSerialize(using = BigDecimalAutoStripTrailingZerosSerializer.class)
	private BigDecimal scheduleQuantity;


	/**
	 * @description 物料数据的 scheduleQuantity 的详情
	 */
	@TableField(value = "material_schedule_detail")
	private String latestMaterialScheduleDetail;
}
