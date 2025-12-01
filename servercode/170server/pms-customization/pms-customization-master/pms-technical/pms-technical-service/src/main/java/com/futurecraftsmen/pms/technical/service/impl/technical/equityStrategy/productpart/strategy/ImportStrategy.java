/*
 * Copyright (c) Zhongzao Software Co. LTD 2022-2062 All rights reserved
 */
package com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart.strategy;

import com.futurecraftsmen.pms.api.dto.MultipartFileRpcDTO;
import com.futurecraftsmen.pms.common.domain.excel.ParseExcelResult;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.standard.BatchAddMaterialDetailWorkRequest;

import org.aerie.forest.core.brick.exception.ExceptionPack;

import java.util.List;

/**
 * @description 标准-产品、部件、零件、原料 策略类
 *
 * @author qierkang
 * @organization futurecraftsmen
 * @date Created in 2025/4/24 23:06
 * @department: Product development
 */
public interface ImportStrategy {

	/**
	 * @description 标准-导入校验 产品-有工艺路线
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/25 11:44
	 * @department: Product development
	 */
	ParseExcelResult<?> productAnalyzeExcelWorkY(MultipartFileRpcDTO excelFile) throws ExceptionPack;

	/**
	 * @description 标准-导入校验 产品-无工艺路线
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/25 11:44
	 * @department: Product development
	 */
	ParseExcelResult<?> productAnalyzeExcelWorkN(MultipartFileRpcDTO excelFile) throws ExceptionPack;

	/**
	 * @description 标准-导入校验 部件-有工艺路线
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/25 11:49
	 * @department: Product development
	 */
	ParseExcelResult<?> componentAnalyzeExcelWorkY(MultipartFileRpcDTO excelFile) throws ExceptionPack;

	/**
	 * @description 标准-导入校验 部件-无工艺路线
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/25 11:49
	 * @department: Product development
	 */
	ParseExcelResult<?> componentAnalyzeExcelWorkN(MultipartFileRpcDTO excelFile) throws ExceptionPack;

	/**
	 * @description 标准-导入校验 零件
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/25 11:44
	 * @department: Product development
	 */
	ParseExcelResult<?> partAnalyzeExcelWorkN(MultipartFileRpcDTO excelFile) throws ExceptionPack;

	/**
	 * @description 标准-导入校验 原料
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/25 11:49
	 * @department: Product development
	 */
	ParseExcelResult<?> materialAnalyzeExcelWorkN(MultipartFileRpcDTO excelFile) throws ExceptionPack;

	/**
	 * @description 标准-零件、原料批量导入
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/28 00:18
	 * @department: Product development
	 */
	void batchAddPartMaterialWork(List<?> requestData) throws ExceptionPack;

	/**
	 * @description 详情导入校验-物料
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/28 18:26
	 * @department: Product development
	 */
	ParseExcelResult<?> materialDetailAnalyzeExcelWorkY(MultipartFileRpcDTO excelFile, String processRouteDataCode, Long productPartCode) throws ExceptionPack;

	/**
	 * @description 详情物料批量导入
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/28 21:53
	 * @department: Product development
	 */
	void batchAddMaterialDetailWorkY(BatchAddMaterialDetailWorkRequest requestData) throws ExceptionPack;
}
