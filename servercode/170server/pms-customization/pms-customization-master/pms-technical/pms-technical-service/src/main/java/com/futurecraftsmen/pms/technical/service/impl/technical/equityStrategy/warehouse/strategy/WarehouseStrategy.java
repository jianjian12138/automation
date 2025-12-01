/*
 * Copyright (c) Zhongzao Software Co. LTD 2022-2062 All rights reserved
 */
package com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.warehouse.strategy;

import com.futurecraftsmen.pms.api.dto.MultipartFileRpcDTO;
import com.futurecraftsmen.pms.common.domain.excel.ParseExcelResult;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.standard.BatchAddWarehouseWorkRequest;

import org.aerie.forest.core.brick.exception.ExceptionPack;

import java.util.List;

/**
 * @description 仓库-航舰、标准-策略实现
 *
 * @author qierkang
 * @organization futurecraftsmen
 * @date Created in 2025/4/24 23:06
 * @department: Product development
 */
public interface WarehouseStrategy {

	/**
	 * @description 库存批量校验接口
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/6/5 19:59
	 * @department: Product development
	 */
	ParseExcelResult<?> warehouseMergeExportAnalyze(MultipartFileRpcDTO excelFile) throws ExceptionPack;

	/**
	 * @description 库存批量保存接口
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/5/29 20:14
	 * @department: Product development
	 */
	void batchSaveWarehouseMergeExcel(List<BatchAddWarehouseWorkRequest> requestData) throws ExceptionPack;
}
