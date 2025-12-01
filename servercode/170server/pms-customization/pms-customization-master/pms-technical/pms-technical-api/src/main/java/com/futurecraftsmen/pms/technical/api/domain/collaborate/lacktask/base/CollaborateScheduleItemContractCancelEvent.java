package com.futurecraftsmen.pms.technical.api.domain.collaborate.lacktask.base;


import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.futurecraftsmen.pms.common.domain.serializer.BigDecimalAutoStripTrailingZerosSerializer;
import com.futurecraftsmen.pms.api.dto.base.AbstractRpcDTO;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.Accessors;

import java.math.BigDecimal;


/**
 * @author chengxinyu
 * @description 协作安排数据-询价后，采购合同作废
 * @organization futurecraftsmen
 * @date 2025-07-18 10:16
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class CollaborateScheduleItemContractCancelEvent extends AbstractRpcDTO {


	/**
	 * @description 数据唯一编号
	 */
	@NotNull(message = "协作安排数据编号必填")
	private Long collaborateCode;

	/**
	 * @description 作废量 =  已安排量 需要减少的值
	 */
	@JsonSerialize(using = BigDecimalAutoStripTrailingZerosSerializer.class)
	private BigDecimal scheduleNumNeedDecrease;


	/**
	 * @description 操作数量
	 */
	@JsonSerialize(using = BigDecimalAutoStripTrailingZerosSerializer.class)
	private BigDecimal collaborateOperationNum;


}
