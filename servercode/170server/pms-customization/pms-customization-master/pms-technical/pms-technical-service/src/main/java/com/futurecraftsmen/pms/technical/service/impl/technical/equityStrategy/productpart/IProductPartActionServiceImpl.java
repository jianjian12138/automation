/*
 * Copyright (c) Zhongzao Software Co. LTD 2022-2062 All rights reserved
 */
package com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart;

import cn.hutool.core.collection.CollUtil;
import com.futurecraftsmen.pms.api.dto.MultipartFileRpcDTO;
import com.futurecraftsmen.pms.api.dto.base.RpcPagingDTO;
import com.futurecraftsmen.pms.common.domain.excel.ParseExcelResult;
import com.futurecraftsmen.pms.technical.api.domain.technical.procedure.BomPartSimpleDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.*;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.standard.BatchAddComponentWorkRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.standard.BatchAddMaterialDetailWorkRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.standard.BatchAddProductWorkRequest;
import com.futurecraftsmen.pms.technical.api.service.collaborate.ProductBomQueryService;
import com.futurecraftsmen.pms.technical.api.service.technical.IProductPartActionService;
import com.futurecraftsmen.pms.technical.api.service.technical.IProductPartService;
import com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.factory.AbstractFactory;
import com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart.strategy.ImportStrategy;
import com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart.strategy.ProductPartStrategy;
import com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart.strategy.WorkmanshipYNStrategy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.aerie.forest.core.brick.exception.ExceptionMsg;
import org.aerie.forest.core.brick.exception.ExceptionPack;
import org.aerie.forest.core.brick.exception.assertprocess.AssertException;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.futurecraftsmen.pms.technical.service.util.CommonUtil.getEnterpriseCode;

@Slf4j
@DubboService(group = "pms")
public class IProductPartActionServiceImpl implements IProductPartActionService {

	@Autowired
	private ProductBomQueryService productBomQueryService;

	@Resource
	private IProductPartService productPartService;

	@Override
	public RpcPagingDTO<?> getPageList(ProductPartPageRequest requestData) throws ExceptionPack {
		ProductPartStrategy action = AbstractFactory.getStrategy(AbstractFactory.StrategyType.PRODUCT_PART_ACTION);
		return action.getPageList(requestData);
	}

	@Override
	public ProductPartRpcDTO addProductPart(ProductPartAddRpcRequest requestData) throws AssertException {
		ProductPartStrategy action = AbstractFactory.getStrategy(AbstractFactory.StrategyType.PRODUCT_PART_ACTION);
		return action.addProductPart(requestData);
	}

	@Override
	public void updateProductPart(ProductPartUpdateRpcRequest requestData) throws AssertException {
		ProductPartStrategy action = AbstractFactory.getStrategy(AbstractFactory.StrategyType.PRODUCT_PART_ACTION);
		action.updateProductPart(requestData);
	}

	@Override
	public ParseExcelResult<?> productAnalyzeExcel(MultipartFileRpcDTO excelFile) throws ExceptionPack {
		ProductPartStrategy action = AbstractFactory.getStrategy(AbstractFactory.StrategyType.PRODUCT_PART_ACTION);
		return action.productAnalyzeExcel(excelFile);
	}

	@Override
	public ParseExcelResult<?> partAnalyzeExcel(MultipartFileRpcDTO excelFile) throws ExceptionPack {
		ProductPartStrategy action = AbstractFactory.getStrategy(AbstractFactory.StrategyType.PRODUCT_PART_ACTION);
		return action.partAnalyzeExcel(excelFile);
	}

	@Override
	public void batchAddProductPart(List<?> requestData) throws ExceptionPack {
		ProductPartStrategy action = AbstractFactory.getStrategy(AbstractFactory.StrategyType.PRODUCT_PART_ACTION);
		action.batchAddProductPart(requestData);
	}

	@Override
	public void productPartBatchUpdate(ProductPartUpdateRpcRequest requestData) throws ExceptionPack, AssertException {
		ProductPartStrategy action = AbstractFactory.getStrategy(AbstractFactory.StrategyType.PRODUCT_PART_ACTION);
		action.productPartBatchUpdate(requestData);
	}

	@Override
	public void productPartCopy(ProductPartRpcRequest requestData) throws AssertException {
		WorkmanshipYNStrategy action = AbstractFactory.getStrategy(AbstractFactory.StrategyType.WORKMANSHIP_ACTION);
		action.productPartCopy(requestData);
	}

	@Override
	public void partMaterialCopy(ProductPartRpcRequest requestData) throws AssertException {
		WorkmanshipYNStrategy action = AbstractFactory.getStrategy(AbstractFactory.StrategyType.WORKMANSHIP_ACTION);
		action.partMaterialCopy(requestData);
	}

	@Override
	public void deleteProductPart(ProductPartRpcRequest requestData) throws ExceptionPack, AssertException {
		ProductPartStrategy action = AbstractFactory.getStrategy(AbstractFactory.StrategyType.PRODUCT_PART_ACTION);
		action.deleteProductPart(requestData);
	}

	@Override
	public ParseExcelResult<?> productAnalyzeExcelWorkY(MultipartFileRpcDTO excelFile) throws ExceptionPack {
		ImportStrategy action = AbstractFactory.getStrategy(AbstractFactory.StrategyType.IMPORT_ACTION);
		return action.productAnalyzeExcelWorkY(excelFile);
	}

	@Override
	public ParseExcelResult<?> productAnalyzeExcelWorkN(MultipartFileRpcDTO excelFile) throws ExceptionPack {
		ImportStrategy action = AbstractFactory.getStrategy(AbstractFactory.StrategyType.IMPORT_ACTION);
		return action.productAnalyzeExcelWorkN(excelFile);
	}

	@Override
	public ParseExcelResult<?> componentAnalyzeExcelWorkY(MultipartFileRpcDTO excelFile) throws ExceptionPack {
		ImportStrategy action = AbstractFactory.getStrategy(AbstractFactory.StrategyType.IMPORT_ACTION);
		return action.componentAnalyzeExcelWorkY(excelFile);
	}

	@Override
	public ParseExcelResult<?> componentAnalyzeExcelWorkN(MultipartFileRpcDTO excelFile) throws ExceptionPack {
		ImportStrategy action = AbstractFactory.getStrategy(AbstractFactory.StrategyType.IMPORT_ACTION);
		return action.componentAnalyzeExcelWorkN(excelFile);
	}

	@Override
	public ParseExcelResult<?> partAnalyzeExcelWorkN(MultipartFileRpcDTO excelFile) throws ExceptionPack {
		ImportStrategy action = AbstractFactory.getStrategy(AbstractFactory.StrategyType.IMPORT_ACTION);
		return action.partAnalyzeExcelWorkN(excelFile);
	}

	@Override
	public ParseExcelResult<?> materialAnalyzeExcelWorkN(MultipartFileRpcDTO excelFile) throws ExceptionPack {
		ImportStrategy action = AbstractFactory.getStrategy(AbstractFactory.StrategyType.IMPORT_ACTION);
		return action.materialAnalyzeExcelWorkN(excelFile);
	}

	@Override
	public void batchAddProductWork(List<BatchAddProductWorkRequest> requestData) throws ExceptionPack {
		WorkmanshipYNStrategy action = AbstractFactory.getStrategy(AbstractFactory.StrategyType.WORKMANSHIP_ACTION);
		action.batchAddProductWork(requestData);
	}

	@Override
	public void batchAddComponentWork(List<BatchAddComponentWorkRequest> requestData) throws ExceptionPack {
		WorkmanshipYNStrategy action = AbstractFactory.getStrategy(AbstractFactory.StrategyType.WORKMANSHIP_ACTION);
		action.batchAddComponentWork(requestData);
	}

	@Override
	public void batchAddPartMaterialWork(List<?> requestData) throws ExceptionPack {
		ImportStrategy action = AbstractFactory.getStrategy(AbstractFactory.StrategyType.IMPORT_ACTION);
		action.batchAddPartMaterialWork(requestData);
	}

	@Override
	public ParseExcelResult<?> materialDetailAnalyzeExcelWorkY(MultipartFileRpcDTO excelFile, String processRouteDataCode, Long productPartCode) throws ExceptionPack {
		ImportStrategy action = AbstractFactory.getStrategy(AbstractFactory.StrategyType.IMPORT_ACTION);
		return action.materialDetailAnalyzeExcelWorkY(excelFile, processRouteDataCode, productPartCode);
	}

	@Override
	public void batchAddMaterialDetailWorkY(BatchAddMaterialDetailWorkRequest requestData) throws ExceptionPack {
		ImportStrategy action = AbstractFactory.getStrategy(AbstractFactory.StrategyType.IMPORT_ACTION);
		action.batchAddMaterialDetailWorkY(requestData);
	}

	@Override
	public RpcPagingDTO<ProductMaterialRpcDTO> getProductMaterialList(ProductPartPageRequest requestData) throws ExceptionPack {
		WorkmanshipYNStrategy action = AbstractFactory.getStrategy(AbstractFactory.StrategyType.WORKMANSHIP_ACTION);
		return action.getProductMaterialList(requestData);
	}

	@Override
	public List<?> getProductMaterialRecursive(ProductPartPageRequest requestData) throws ExceptionPack {
		WorkmanshipYNStrategy action = AbstractFactory.getStrategy(AbstractFactory.StrategyType.WORKMANSHIP_ACTION);
		return action.getProductMaterialRecursive(requestData);
	}

	@Override
	public ProductPartDeleteValidationResult validateDeleteProductPart(ProductPartRpcRequest requestData) throws AssertException  {
		ProductPartStrategy action = AbstractFactory.getStrategy(AbstractFactory.StrategyType.PRODUCT_PART_ACTION);
		return action.validateDeleteProductPart(requestData);
	}

	@Override
	public ProductPartBatchDeleteValidationResult validateBatchDeleteProductPart(ProductPartBatchDeleteRpcRequest requestData) throws AssertException {
		ProductPartStrategy action = AbstractFactory.getStrategy(AbstractFactory.StrategyType.PRODUCT_PART_ACTION);
		return action.validateBatchDeleteProductPart(requestData);
	}

	@Override
	public void batchDeleteProductPart(ProductPartBatchDeleteRpcRequest requestData) throws ExceptionPack, AssertException {
		ProductPartStrategy action = AbstractFactory.getStrategy(AbstractFactory.StrategyType.PRODUCT_PART_ACTION);
		action.batchDeleteProductPart(requestData);
	}

	@Override
	public ProductPartBatchEditValidationResult validateEditProductPart(ProductPartBatchEditRpcRequest requestData) throws AssertException {
		ProductPartStrategy action = AbstractFactory.getStrategy(AbstractFactory.StrategyType.PRODUCT_PART_ACTION);
		return action.validateBatchEditProductPart(requestData);
	}

	@Override
	public ProductPartResultRpcDTO checkNameAndModel(ProductPartCheckNameModelRpcRequest requestData) throws AssertException {
		ProductPartStrategy action = AbstractFactory.getStrategy(AbstractFactory.StrategyType.PRODUCT_PART_ACTION);
		return action.checkNameAndModel(requestData);
	}

	@Override
	public List<BomPartSimpleDTO> queryMaterialLevelBomMaterialsY(Long productPartCode) throws ExceptionPack {
		try {
			List<BomPartSimpleDTO> materials = productBomQueryService.materialLevelBomMaterialsSimple(productPartCode, 2);

			if (CollUtil.isEmpty(materials)) {
				return new ArrayList<>();
			}

			List<Long> materialCodesNeedQuery = new ArrayList<>();
			for (BomPartSimpleDTO material : materials) {
				materialCodesNeedQuery.addAll(material.materialCodesNeedQuery());
			}

			// 批量查询物料详细信息
			Map<Long, ProductPartRpcDTO> materialDetailMap = new HashMap<>();
			if (CollUtil.isNotEmpty(materialCodesNeedQuery)) {
				List<ProductPartRpcDTO> productPartList = productPartService.queryByCodes(
						materialCodesNeedQuery.stream().filter(Objects::nonNull).distinct().toList(), getEnterpriseCode(), true, false);
				if (CollUtil.isNotEmpty(productPartList)) {
					materialDetailMap = productPartList.stream().collect(Collectors.toMap(ProductPartRpcDTO::getProductPartCode,
							Function.identity(), (existing, replacement) -> existing));
				}
			}
			for (BomPartSimpleDTO material : materials) {
				material.setMaterialInfo(materialDetailMap);
			}

			return materials;
		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("fail to queryMaterialLevelBomMaterialsY").build());
		}
	}
}
