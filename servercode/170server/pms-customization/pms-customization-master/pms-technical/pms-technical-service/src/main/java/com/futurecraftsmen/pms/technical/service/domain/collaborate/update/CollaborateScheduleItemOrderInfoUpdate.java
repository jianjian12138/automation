package com.futurecraftsmen.pms.technical.service.domain.collaborate.update;


import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.futurecraftsmen.pms.common.domain.serializer.BigDecimalAutoStripTrailingZerosSerializer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.base.MaterialSimpleInfo;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
public class CollaborateScheduleItemOrderInfoUpdate {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	static {
		OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}


	/**
	 * @description 产品物料编号
	 */
	private Long collaborateMaterial;

	/**
	 * @description 交期
	 */
	private Date delivery;

	/**
	 * @description 交期显示值
	 */
	private String displayDelivery;

	/**
	 * @description 需求量（订单量）：合并后的订单量：初始是 订单量（合并后的）,后续也是生成的地方会更新
	 */
	@JsonSerialize(using = BigDecimalAutoStripTrailingZerosSerializer.class)
	private BigDecimal orderQuantity;

	/**
	 * @description 物料需求详情
	 */
	private String materialDemandInfo;


	public void settingMaterialDemandInfo(List<MaterialSimpleInfo> materialDemandInfo) {
		if (materialDemandInfo != null) {
			try {
				this.materialDemandInfo = OBJECT_MAPPER.writeValueAsString(materialDemandInfo);
			} catch (JsonProcessingException e) {
			}
		}
	}
}
