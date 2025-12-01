/*
 * Copyright (c) Zhongzao Software Co. LTD 2022-2062 All rights reserved
 */
package com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart.strategy;

import com.futurecraftsmen.pms.api.dto.MultipartFileRpcDTO;
import com.futurecraftsmen.pms.api.dto.base.RpcPagingDTO;
import com.futurecraftsmen.pms.common.domain.excel.ParseExcelResult;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.*;

import org.aerie.forest.core.brick.exception.ExceptionPack;
import org.aerie.forest.core.brick.exception.assertprocess.AssertException;

import java.util.List;

/**
 * @description 产品、部件、零件、原料 策略类
 *
 * @author qierkang
 * @organization futurecraftsmen
 * @date Created in 2025/4/24 23:06
 * @department: Product development
 */
public interface ProductPartStrategy {

	/**
	 * @description 查询产品、部件、零件、原料列表
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/24 23:06
	 * @department: Product development
	 */
	RpcPagingDTO<?> getPageList(ProductPartPageRequest requestData) throws ExceptionPack;

	/**
	 * @description 新增产品、部件、零件、原料
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/25 09:55
	 * @department: Product development
	 */
	ProductPartRpcDTO addProductPart(ProductPartAddRpcRequest requestData) throws AssertException;

	/**
	 * @description 更新产品、部件、零件、原料
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/25 09:54
	 * @department: Product development
	 */
	void updateProductPart(ProductPartUpdateRpcRequest requestData) throws AssertException;

	/**
	 * @description 导入校验 产品
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/25 11:44
	 * @department: Product development
	 */
	ParseExcelResult<?> productAnalyzeExcel(MultipartFileRpcDTO excelFile) throws ExceptionPack;

	/**
	 * @description 导入校验 零件
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/25 11:49
	 * @department: Product development
	 */
	ParseExcelResult<?> partAnalyzeExcel(MultipartFileRpcDTO excelFile) throws ExceptionPack;

	/**
	 * @description 批量新增 产品、部件、零件、原料
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/25 11:56
	 * @department: Product development
	 */
	void batchAddProductPart(List<?> requestData) throws ExceptionPack;

	/**
	 * @description 批量修改 产品、部件、零件、原料
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/25 17:09
	 * @department: Product development
	 */
	void productPartBatchUpdate(ProductPartUpdateRpcRequest requestData) throws ExceptionPack, AssertException;

	/**
	 * @description 删除 产品、部件、零件、原料
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/25 17:17
	 * @department: Product development
	 */
	void deleteProductPart(ProductPartRpcRequest requestData) throws ExceptionPack, AssertException;

	/**
	 * @description 删除前校验产品部件关联关系
	 *
	 */
	ProductPartDeleteValidationResult validateDeleteProductPart(ProductPartRpcRequest requestData) throws AssertException ;

	/**
	 * @description 批量删除前校验产品部件关联关系
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/12/28
	 * @department: Product development
	 */
	ProductPartBatchDeleteValidationResult validateBatchDeleteProductPart(ProductPartBatchDeleteRpcRequest requestData) throws AssertException;

	/**
	 * @description 批量删除产品部件
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/12/28
	 * @department: Product development
	 */
	void batchDeleteProductPart(ProductPartBatchDeleteRpcRequest requestData) throws ExceptionPack, AssertException;

	/**
	 * 验证产品部件编辑的方法
	 * 该方法用于验证产品部件更新的请求数据是否有效
	 *
	 * @param requestData 产品部件更新请求的RPC请求数据对象，包含需要更新的产品部件信息
	 * @return ProductPartEditValidationResult 验证结果对象，包含验证是否通过及相关的验证信息
	 * @throws ExceptionPack 如果验证过程中发生异常，将抛出异常包，包含可能出现的各类异常信息
	 */
	ProductPartBatchEditValidationResult validateBatchEditProductPart(ProductPartBatchEditRpcRequest requestData) throws AssertException;

	/**
	 * 检查产品部件的名称和型号
	 *
	 * @param requestData 包含需要检查的产品部件名称和型号信息的请求对象
	 * @return ProductPartResultRpcDTO 返回检查结果的数据传输对象
	 * @throws ExceptionPack 如果检查过程中发生异常，将抛出异常包
	 */
	ProductPartResultRpcDTO checkNameAndModel(ProductPartCheckNameModelRpcRequest requestData);

}
