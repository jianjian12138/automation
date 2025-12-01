package com.futurecraftsmen.pms.technical.service.domain.collaborate.update;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.futurecraftsmen.pms.common.domain.serializer.BigDecimalAutoStripTrailingZerosSerializer;
import lombok.*;
import org.aerie.forest.core.brick.domain.dto.AbstractDatabaseModel;

import java.math.BigDecimal;

/**
 * @author chengxinyu
 * @description 协作外发物料中转区 更新已领取量
 * @organization futurecraftsmen
 * @date 2025/01/24 14:01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class CollaborateOutSendMaterialStagingAreaUpdater extends AbstractDatabaseModel {

	/**
	 * 数据唯一编号
	 */
	private Long primaryKey;

	/***
	 * 需要增加到已领量的本次领取量
	 */
	@JsonSerialize(using = BigDecimalAutoStripTrailingZerosSerializer.class)
	private BigDecimal pickNumNeedAdd;

	/***
	 * 企业编号
	 */
	private Long enterpriseCode;


	@Override
	public long getPrimaryKeyValue() {
		return primaryKey;
	}

}

