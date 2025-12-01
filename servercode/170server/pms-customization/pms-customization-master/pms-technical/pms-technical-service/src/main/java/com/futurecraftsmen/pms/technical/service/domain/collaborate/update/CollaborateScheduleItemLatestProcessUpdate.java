/*
 * Copyright (c) Zhongzao Software Co. LTD 2022-2062 All rights reserved
 */

package com.futurecraftsmen.pms.technical.service.domain.collaborate.update;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.futurecraftsmen.pms.api.mybatis.MyBatisMark;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.ProcessEnum;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * @author chengxinyu
 * @description 销售方协作安排数据表, 进度最新更新
 * @organization futurecraftsmen
 * @date 2025-07-03 15:27
 */
@Data
@TableName(autoResultMap = true, value = "${dynamicTableNameProxy.collaborateScheduleItem}")
public class CollaborateScheduleItemLatestProcessUpdate implements Serializable, MyBatisMark {

	@Serial
	private static final long serialVersionUID = -260407439774866191L;


	/**
	 * @description 数据唯一编号
	 */
	@TableId(value = "primary_key")
	private long collaborateCode;


	/**
	 * @description 最新生产进度
	 */
	@TableField(value = "latest_progress")
	private ProcessEnum latestProgress;


}
