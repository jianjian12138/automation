package com.futurecraftsmen.pms.technical.api.domain.technical.productpart.relationship;

import com.futurecraftsmen.pms.api.dto.base.AbstractRpcDTO;

import java.io.Serial;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @description 产品零件与工艺路线关系
 *
 * @author qierkang
 * @organization futurecraftsmen
 * @date Created in 2024/12/13 13:41
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductPartRouteRelationshipDTO extends AbstractRpcDTO {

	@Serial
	private static final long serialVersionUID = 2767614657964399170L;

	/**
	 * 产品零件表主键编号
	 */
	private Long productPartCode;

	/**
	 * 主键编号,全局唯一主键
	 */
	private Long primaryKey;

	/**
	 * 删除标识符, false: 未删除 , true: 已删除
	 */
	private Boolean deleteFlag;

	/**
	 * 关联关系唯一ID
	 */
	private Long uniqueId;

	/**
	 * 状态 true 启用，false 停用
	 */
	private Boolean state;
}