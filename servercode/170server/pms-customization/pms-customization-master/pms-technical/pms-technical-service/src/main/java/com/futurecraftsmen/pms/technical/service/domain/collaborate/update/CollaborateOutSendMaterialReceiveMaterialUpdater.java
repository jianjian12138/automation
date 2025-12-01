package com.futurecraftsmen.pms.technical.service.domain.collaborate.update;

import lombok.*;
import org.aerie.forest.core.brick.domain.dto.AbstractDatabaseModel;

/**
 * @author chengxinyu
 * @description 协作外发物料数据 更新收货型号
 * @organization futurecraftsmen
 * @date 2025/01/24 14:01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class CollaborateOutSendMaterialReceiveMaterialUpdater extends AbstractDatabaseModel {

	/**
	 * 数据唯一编号
	 */
	private Long outSendCode;

	/***
	 * 收货物流型号
	 */
	private Long receiveMaterial;

	/***
	 * 企业编号
	 */
	private Long enterpriseCode;


	@Override
	public long getPrimaryKeyValue() {
		return outSendCode;
	}

}

