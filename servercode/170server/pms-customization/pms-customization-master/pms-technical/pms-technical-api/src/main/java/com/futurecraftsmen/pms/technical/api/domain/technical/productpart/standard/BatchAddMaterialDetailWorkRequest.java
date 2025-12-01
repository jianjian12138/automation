/*
 * Copyright (c) Zhongzao Software Co. LTD 2022-2062 All rights reserved
 */

package com.futurecraftsmen.pms.technical.api.domain.technical.productpart.standard;

import com.futurecraftsmen.pms.api.dto.base.AbstractRpcDTO;

import java.io.Serial;
import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @description 详情物料批量导入
 *
 * @author qierkang
 * @organization futurecraftsmen
 * @date Created in 2025/4/28 22:02
 * @department: Product development
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BatchAddMaterialDetailWorkRequest extends AbstractRpcDTO {

	@Serial
	private static final long serialVersionUID = 604611812137885738L;

	/**
	 * 产品零件编号 主键编号,全局唯一主键
	 */
	private Long productPartCode;

	/**
	 * 工艺路线编号 主键编号,全局唯一主键
	 */
	private Long processRouteDataCode;

	/**
	 * 属性类型, 1：产品，2：部件（希航叫部件，航舰是零件） 3：零件 4：原料，5：其他
	 * 参考枚举：{@link org.aerie.forest.core.brick.domain.enums.PPAttributeEnum}
	 */
	private Integer attribute;

	/**
	 * 导入零件，规格数据模型
	 */
	private List<PartBatchAddModel> partInfoBatchAddModels;

	@Data
	@EqualsAndHashCode(callSuper = true)
	public static class PartBatchAddModel extends AbstractRpcDTO {

		@Serial
		private static final long serialVersionUID = 8205461636982902003L;

		/**
		 * 零件分类
		 */
		private Long productPartTypeCode;
		private String productPartTypeCodeName;

		/**
		 * 物料编号
		 */
		private String unityNo;

		/**
		 * 物料名称
		 */
		private Long productPartCode;
		private String partName;

		/**
		 * 数量
		 */
		private String number;

		/**
		 * 工序号
		 */
		private Long productPartProcedureCode;
		private String procedureNumber;
	}
}
