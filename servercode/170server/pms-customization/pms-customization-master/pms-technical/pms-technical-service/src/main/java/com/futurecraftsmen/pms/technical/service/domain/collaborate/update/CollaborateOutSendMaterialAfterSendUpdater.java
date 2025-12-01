package com.futurecraftsmen.pms.technical.service.domain.collaborate.update;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.futurecraftsmen.pms.common.domain.serializer.BigDecimalAutoStripTrailingZerosSerializer;
import lombok.*;
import org.aerie.forest.core.brick.domain.dto.AbstractDatabaseModel;

import java.math.BigDecimal;

/**
 * @author chengxinyu
 * @description 协作外发物料数据 更新历史已外发量
 * @organization futurecraftsmen
 * @date 2025/01/24 14:01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class CollaborateOutSendMaterialAfterSendUpdater extends AbstractDatabaseModel {

	/**
	 * 数据唯一编号
	 */
	private Long outSendCode;

	/***
	 * 需要增加到历史已外发量的外发量
	 */
	@JsonSerialize(using = BigDecimalAutoStripTrailingZerosSerializer.class)
	private BigDecimal sendSumNeedAdd;


	/***
	 * 最近一次发料时的需求量
	 */
	@JsonSerialize(using = BigDecimalAutoStripTrailingZerosSerializer.class)
	private BigDecimal demandQuantityLastSend;


	/***
	 * 企业编号
	 */
	private Long enterpriseCode;


	@Override
	public long getPrimaryKeyValue() {
		return outSendCode;
	}

}

