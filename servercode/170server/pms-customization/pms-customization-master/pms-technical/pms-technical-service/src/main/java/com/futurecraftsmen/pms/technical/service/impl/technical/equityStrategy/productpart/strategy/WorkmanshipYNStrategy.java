/*
 * Copyright (c) Zhongzao Software Co. LTD 2022-2062 All rights reserved
 */
package com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart.strategy;

import com.futurecraftsmen.pms.api.dto.base.RpcPagingDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.ProductMaterialRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.ProductPartPageRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.ProductPartRpcRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.standard.BatchAddComponentWorkRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.standard.BatchAddProductWorkRequest;
import org.aerie.forest.core.brick.exception.ExceptionPack;
import org.aerie.forest.core.brick.exception.assertprocess.AssertException;

import java.util.List;

/**
 * @description 标准-产品、部件、零件、原料 策略类
 *
 * @author qierkang
 * @organization futurecraftsmen
 * @date Created in 2025/4/24 23:06
 * @department: Product development
 */
public interface WorkmanshipYNStrategy {

	/**
	 * @description 标准-产品批量导入
	 * 有路线和无路线实现
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/28 00:18
	 * @department: Product development
	 */
	void batchAddProductWork(List<BatchAddProductWorkRequest> requestData) throws ExceptionPack;

	/**
	 * @description 标准-部件批量导入
	 * 有路线和无路线实现
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/28 00:18
	 * @department: Product development
	 */
	void batchAddComponentWork(List<BatchAddComponentWorkRequest> requestData) throws ExceptionPack;

	/**
	 * @description 标准-产品、部件复制
	 * 有路线和无路线实现
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/30 17:01
	 * @department: Product development
	 */
	void productPartCopy(ProductPartRpcRequest requestData) throws AssertException;

	/**
	 * @description 复制 零件、原料
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/30 17:23
	 * @department: Product development
	 */
	void partMaterialCopy(ProductPartRpcRequest requestData) throws AssertException;

	/**
	 * @description 采购部-产品查询
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/5/23 15:17
	 * @department: Product development
	 */
	RpcPagingDTO<ProductMaterialRpcDTO> getProductMaterialList(ProductPartPageRequest requestData) throws ExceptionPack;

	/**
	 * @description 采购部-递归产品查询
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/5/26 16:46
	 * @department: Product development
	 */
	List<?> getProductMaterialRecursive(ProductPartPageRequest requestData) throws ExceptionPack;
}
