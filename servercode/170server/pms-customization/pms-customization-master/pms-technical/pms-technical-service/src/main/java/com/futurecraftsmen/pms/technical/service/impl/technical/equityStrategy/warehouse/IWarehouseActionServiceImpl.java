/*
 * Copyright (c) Zhongzao Software Co. LTD 2022-2062 All rights reserved
 */
package com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.warehouse;

import com.futurecraftsmen.pms.api.dto.MultipartFileRpcDTO;
import com.futurecraftsmen.pms.common.domain.excel.ParseExcelResult;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.standard.BatchAddWarehouseWorkRequest;
import com.futurecraftsmen.pms.technical.api.service.warehouse.IWarehouseActionService;
import com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.factory.AbstractFactory;
import com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.warehouse.strategy.WarehouseStrategy;

import org.aerie.forest.core.brick.exception.ExceptionPack;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@DubboService(group = "pms")
public class IWarehouseActionServiceImpl implements IWarehouseActionService {

	@Override
	public ParseExcelResult<?> warehouseMergeExportAnalyze(MultipartFileRpcDTO excelFile) throws ExceptionPack {
		WarehouseStrategy action = AbstractFactory.getStrategy(AbstractFactory.StrategyType.WAREHOUS_ACTION);
		return action.warehouseMergeExportAnalyze(excelFile);
	}

	@Override
	public void batchSaveWarehouseMergeExcel(List<BatchAddWarehouseWorkRequest> requestData) throws ExceptionPack {
		WarehouseStrategy action = AbstractFactory.getStrategy(AbstractFactory.StrategyType.WAREHOUS_ACTION);
		action.batchSaveWarehouseMergeExcel(requestData);
	}
}
