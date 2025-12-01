/*
 * Copyright (c) Zhongzao Software Co. LTD 2022-2062 All rights reserved
 */
package com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart.abstracts;

import com.futurecraftsmen.pms.technical.service.impl.inner.TableNameFactory;
import com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart.strategy.ImportStrategy;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractImport implements ImportStrategy {

	@Resource
	private TableNameFactory tableFactory;
	private String productPartTableName;
	private String productPartRouteRelationshipTableName;
	private String processRouteDataTableName;

	public String getProductPartTableName() {
//		if (productPartTableName == null) {
//			productPartTableName = tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
//		}
		return tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
	}

	public String getProductPartRouteRelationshipTableName() {
//		if (productPartRouteRelationshipTableName == null) {
//			productPartRouteRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
//					tableFactory.table.getProductPartRouteRelationship());
//		}
		return tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProductPartRouteRelationship());
	}

	public String getProcessRouteDataTableName() {
//		if (processRouteDataTableName == null) {
//			processRouteDataTableName = tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProcessRouteData());
//		}
		return tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProcessRouteData());
	}
}
