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
 * @description 销售方协作安排数据表, 已安排量,是否安排过标识更新
 * @organization futurecraftsmen
 * @date 2025-07-03 15:27
 */
@Data
@TableName(autoResultMap = true, value = "${dynamicTableNameProxy.collaborateScheduleItem}")
public class CollaborateScheduleItemScheduleQuantityUpdate implements Serializable, MyBatisMark {

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
	 * @description 已安排量 增量 (暂时只有出库的时候会传递)
	 */
	@TableField(exist = false)
	@JsonSerialize(using = BigDecimalAutoStripTrailingZerosSerializer.class)
	private BigDecimal scheduleQuantityNeedAdd;



	/**
	 * @description 是否安排过标识 ， 初始是 false
	 */
	@TableField(value = "schedule_flag")
	private boolean scheduleFlag;


	/**
	 * @description 物料数据的 scheduleQuantity 的详情
	 */
	@TableField(value = "material_schedule_detail")
	private String latestMaterialScheduleDetail;

}
