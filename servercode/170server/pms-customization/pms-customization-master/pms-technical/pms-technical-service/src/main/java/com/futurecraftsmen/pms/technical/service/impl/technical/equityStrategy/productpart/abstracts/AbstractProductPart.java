/*
 * Copyright (c) Zhongzao Software Co. LTD 2022-2062 All rights reserved
 */
package com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart.abstracts;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.futurecraftsmen.pms.service.configuration.RequestTableHelper;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.ProductPartBatchDeleteRpcRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.ProductPartBatchDeleteValidationResult;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.ProductPartBatchEditRpcRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.ProductPartBatchEditValidationResult;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.ProductPartCheckNameModelRpcRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.ProductPartDeleteValidationResult;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.ProductPartEditValidationResult;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.ProductPartResultRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.ProductPartRpcRequest;
import com.futurecraftsmen.pms.technical.service.common.enums.TechnicalErrorEnum;
import com.futurecraftsmen.pms.technical.service.dao.technical.IProductPartMapper;
import com.futurecraftsmen.pms.technical.service.dao.technical.IProcedurePartRelationshipMapper;
import com.futurecraftsmen.pms.technical.service.dao.technical.IProductPartRouteRelationshipMapper;
import com.futurecraftsmen.pms.technical.service.domain.technical.procedure.ProcedurePartRelationshipModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.productpart.ProductPartModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.productpart.ProductPartRouteRelationshipModel;
import com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart.action.unified.IProductPartCommonServiceImpl;
import com.futurecraftsmen.pms.technical.service.impl.inner.TableNameFactory;
import com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart.strategy.ProductPartStrategy;

import org.aerie.forest.core.brick.domain.enums.PPAttributeEnum;
import org.aerie.forest.core.brick.exception.ExceptionMsg;
import org.aerie.forest.core.brick.exception.ExceptionPack;
import org.aerie.forest.core.brick.exception.assertprocess.AssertException;
import org.apache.commons.lang3.StringUtils;

import java.util.stream.Collectors;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.futurecraftsmen.pms.technical.service.util.CommonUtil.getEnterpriseCode;

@Slf4j
public abstract class AbstractProductPart implements ProductPartStrategy {

	@Resource
	private TableNameFactory tableFactory;

	@Resource
	private IProductPartMapper productPartMapper;

	@Resource
	private IProcedurePartRelationshipMapper procedurePartRelationshipMapper;

	@Resource
	private IProductPartRouteRelationshipMapper productPartRouteRelationshipMapper;

	@Resource
	private IProductPartCommonServiceImpl productPartCommonService;

	private String productPartTableName;
	private String productPartRouteRelationshipTableName;
	private String processRouteDataTableName;
	private String procedurePartRelationshipTableName;

	public String getProductPartTableName() {
//		if (productPartTableName == null) {
//			productPartTableName = tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
//		}
//		return productPartTableName;
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
//		return processRouteDataTableName;
		return tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProcessRouteData());
	}

	public String getProcedurePartRelationshipTableName() {
//		if (procedurePartRelationshipTableName == null) {
//			procedurePartRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
//					tableFactory.table.getProcedurePartRelationship());
//		}
//		return procedurePartRelationshipTableName;
		return tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProcedurePartRelationship());
	}

	@Override
	public ProductPartDeleteValidationResult validateDeleteProductPart(ProductPartRpcRequest requestData) throws AssertException {
		Long productPartCode = requestData.getProductPartCode();
		if (productPartCode == null) {
			return new ProductPartDeleteValidationResult()
					.setCanDelete(false)
					.setMessage("产品部件编码不能为空")
					.setValidationType("ERROR")
					.setRelatedCount(0)
					.setRelatedItems(new ArrayList<>());
		}

		// 查询当前产品部件信息（必须包含企业代码和删除标志的过滤条件）
		RequestTableHelper.setTableName(getProductPartTableName());
		LambdaQueryWrapper<ProductPartModel> queryWrapper = Wrappers.lambdaQuery();
		queryWrapper.eq(ProductPartModel::getProductPartCode, productPartCode)
				.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode())
				.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE);
		ProductPartModel currentProductPart = productPartMapper.selectOne(queryWrapper);
		if (currentProductPart == null) {
			throw new AssertException(ExceptionMsg.builder("产品部件不存在")
					.msgView(TechnicalErrorEnum.TECHNICAL_PRODUCT_PART_NOT_EXIT.getMsg() + productPartCode).build());
		}
		String prodName = PPAttributeEnum.getEnumByCode(currentProductPart.getAttribute()).getDescription();

		// 1. 校验是否有已签章的合同
		boolean hasSignedContract = !productPartCommonService.checkDeleteProductPartNonSignedContract(productPartCode);
		if (hasSignedContract) {
			throw new AssertException(ExceptionMsg.builder("当前部件存在已生效的合同哦，无法删除～")
					.msgView("当前" + prodName + TechnicalErrorEnum.TECHNICAL_PRODUCT_PART_HAS_CONTRACT_TO_DELETE.getMsg() + "名称： " + currentProductPart.getProductPartSign()).build());
		}

		// 2. 校验是否被调度安排过
		boolean hasDispatch = !productPartCommonService.checkDeleteProductPartDispatch(productPartCode);
		if (hasDispatch) {
			throw new AssertException(ExceptionMsg.builder("已经被调度安排啦，无法删除～")
					.msgView("当前" + prodName + TechnicalErrorEnum.TECHNICAL_PRODUCT_PART_HAS_TASK_TO_DELETE.getMsg() + "名称： " + currentProductPart.getProductPartSign()).build());
		}

		// 3. 校验是否还有库存
		boolean hasStock = productPartCommonService.checkDeleteStockModel(productPartCode);
		if (hasStock) {
			throw new AssertException(ExceptionMsg.builder("当前部件还有库存哦，无法删除～")
					.msgView("当前" + prodName + TechnicalErrorEnum.TECHNICAL_PRODUCT_PART_BUNDING_PRODUCTING.getMsg() + "名称： " + currentProductPart.getProductPartSign()).build());
		}

		List<ProductPartDeleteValidationResult.RelatedProductPartInfo> allRelatedItems = new ArrayList<>();

		// 如果没有任何关联关系，可以直接删除
		return new ProductPartDeleteValidationResult()
				.setCanDelete(true)
				.setMessage("可以删除")
				.setValidationType("SUCCESS")
				.setRelatedCount(0)
				.setRelatedItems(new ArrayList<>());
	}

	/**
	 * 获取属性类型文本
	 */
	private String getAttributeText(Integer attribute) {
		if (attribute == null) {
			return "项目";
		}
		switch (attribute) {
			case 1:
				return "产品";
			case 2:
				return "部件";
			case 3:
				return "零件";
			case 4:
				return "原料";
			default:
				return "项目";
		}
	}

	/**
	 * 获取关联项的类型描述
	 */
	private String getRelatedItemsTypeText(List<ProductPartDeleteValidationResult.RelatedProductPartInfo> relatedItems) {
		if (relatedItems == null || relatedItems.isEmpty()) {
			return "项目";
		}

		// 统计各种类型的数量
		Map<Integer, Long> attributeCount = relatedItems.stream()
				.collect(Collectors.groupingBy(
						ProductPartDeleteValidationResult.RelatedProductPartInfo::getAttribute,
						Collectors.counting()));

		// 如果只有一种类型，返回该类型的名称
		if (attributeCount.size() == 1) {
			Integer attribute = attributeCount.keySet().iterator().next();
			return getAttributeText(attribute);
		}

		// 如果有多种类型，按优先级返回最主要的类型描述
		// 优先级：产品 > 部件 > 零件 > 原料
		if (attributeCount.containsKey(1)) {
			return "产品";
		} else if (attributeCount.containsKey(2)) {
			return "部件";
		} else if (attributeCount.containsKey(3)) {
			return "零件";
		} else if (attributeCount.containsKey(4)) {
			return "原料";
		} else {
			return "项目";
		}
	}

	/**
	 * 生成批量删除关联关系提示消息
	 * @param productPartCodes 要删除的产品部件编码列表
	 * @param detailResults 详细校验结果
	 * @return 格式化的提示消息
	 */
	private String generateBatchRelationMessage(List<Long> productPartCodes, 
			Map<Long, ProductPartDeleteValidationResult> detailResults) {
		
		// 统计选中的产品部件类型
		Map<Integer, Integer> selectedAttributeCount = new HashMap<>();
		// 统计所有关联的产品部件类型和数量
		Map<Integer, Integer> relatedAttributeCount = new HashMap<>();
		int totalRelatedCount = 0;

		try {
			// 查询选中的产品部件信息
			RequestTableHelper.setTableName(getProductPartTableName());
			List<ProductPartModel> selectedProductParts = productPartMapper.selectBatchIds(productPartCodes);
			
			// 统计选中项的类型分布
			for (ProductPartModel productPart : selectedProductParts) {
				Integer attribute = productPart.getAttribute();
				selectedAttributeCount.put(attribute, selectedAttributeCount.getOrDefault(attribute, 0) + 1);
			}
			
			// 统计关联项的类型分布
			for (ProductPartDeleteValidationResult result : detailResults.values()) {
				if ("RELATION".equals(result.getValidationType()) && result.getRelatedItems() != null) {
					for (ProductPartDeleteValidationResult.RelatedProductPartInfo relatedItem : result.getRelatedItems()) {
						Integer attribute = relatedItem.getAttribute();
						relatedAttributeCount.put(attribute, relatedAttributeCount.getOrDefault(attribute, 0) + 1);
						totalRelatedCount++;
					}
				}
			}
			
		} catch (Exception e) {
			log.warn("查询产品部件信息失败，使用默认描述", e);
		}

		// 生成选中项的类型描述
		String selectedTypeText = getSelectedItemsTypeText(selectedAttributeCount, productPartCodes.size());
		
		// 生成关联项的类型描述
		String relatedTypeText = getRelatedItemsTypeTextFromCount(relatedAttributeCount);
		
		// 生成最终消息
		return String.format("选中的 [%d] 个%s共关联 [%d] 件%s，提交后系统将自动解绑，是否确定删除？", 
				productPartCodes.size(), selectedTypeText, totalRelatedCount, relatedTypeText);
	}

	/**
	 * 根据选中项的属性统计获取类型描述
	 */
	private String getSelectedItemsTypeText(Map<Integer, Integer> attributeCount, int totalCount) {
		if (attributeCount.isEmpty()) {
			return "项目";
		}

		// 如果只有一种类型
		if (attributeCount.size() == 1) {
			Integer attribute = attributeCount.keySet().iterator().next();
			return getAttributeText(attribute);
		}

		// 多种类型的情况，按优先级返回主要类型
		if (attributeCount.containsKey(1)) {
			return "产品";
		} else if (attributeCount.containsKey(2)) {
			return "部件";
		} else if (attributeCount.containsKey(3)) {
			return "零件";
		} else if (attributeCount.containsKey(4)) {
			return "原料";
		} else {
			return "项目";
		}
	}

	/**
	 * 根据关联项的属性统计获取类型描述
	 */
	private String getRelatedItemsTypeTextFromCount(Map<Integer, Integer> attributeCount) {
		if (attributeCount.isEmpty()) {
			return "项目";
		}

		// 如果只有一种类型
		if (attributeCount.size() == 1) {
			Integer attribute = attributeCount.keySet().iterator().next();
			return getAttributeText(attribute);
		}

		// 多种类型的情况，按优先级返回主要类型
		if (attributeCount.containsKey(1)) {
			return "产品";
		} else if (attributeCount.containsKey(2)) {
			return "部件";
		} else if (attributeCount.containsKey(3)) {
			return "零件";
		} else if (attributeCount.containsKey(4)) {
			return "原料";
		} else {
			return "项目";
		}
	}

	/**
	 * 生成批量删除警告提示消息
	 * @param productPartCodes 要删除的产品部件编码列表
	 * @param detailResults 详细校验结果
	 * @param statistics 统计信息
	 * @return 格式化的警告消息
	 */
	private String generateBatchWarningMessage(List<Long> productPartCodes, 
			Map<Long, ProductPartDeleteValidationResult> detailResults,
			ProductPartBatchDeleteValidationResult.Statistics statistics) {
		
		// 统计各种无法删除的原因对应的产品部件类型
		Map<Integer, Integer> contractAttributeCount = new HashMap<>();
		Map<Integer, Integer> dispatchAttributeCount = new HashMap<>();
		Map<Integer, Integer> stockAttributeCount = new HashMap<>();

		try {
			// 查询所有产品部件信息
			RequestTableHelper.setTableName(getProductPartTableName());
			List<ProductPartModel> allProductParts = productPartMapper.selectBatchIds(productPartCodes);
			Map<Long, Integer> codeToAttributeMap = allProductParts.stream()
					.collect(Collectors.toMap(
							ProductPartModel::getProductPartCode,
							ProductPartModel::getAttribute,
							(existing, replacement) -> existing
					));
			
			// 统计各种无法删除原因对应的类型分布
			for (Map.Entry<Long, ProductPartDeleteValidationResult> entry : detailResults.entrySet()) {
				Long productPartCode = entry.getKey();
				ProductPartDeleteValidationResult result = entry.getValue();
				
				if (!result.getCanDelete()) {
					Integer attribute = codeToAttributeMap.get(productPartCode);
					if (attribute != null) {
						switch (result.getValidationType()) {
							case "CONTRACT":
								contractAttributeCount.put(attribute, contractAttributeCount.getOrDefault(attribute, 0) + 1);
								break;
							case "DISPATCH":
								dispatchAttributeCount.put(attribute, dispatchAttributeCount.getOrDefault(attribute, 0) + 1);
								break;
							case "STOCK":
								stockAttributeCount.put(attribute, stockAttributeCount.getOrDefault(attribute, 0) + 1);
								break;
						}
					}
				}
			}
			
		} catch (Exception e) {
			log.warn("查询产品部件信息失败，使用默认描述", e);
		}

		// 生成警告消息，按优先级：合同 > 调度 > 库存
		List<String> warningMessages = new ArrayList<>();
		
		// 1. 合同警告
		if (statistics.getContractCount() > 0) {
			String contractTypeText = getSelectedItemsTypeTextFromCount(contractAttributeCount);
			warningMessages.add(String.format("[%d]个%s存在已生效的合同哦，无法删除～", 
					statistics.getContractCount(), contractTypeText));
		}
		
		// 2. 调度警告
		if (statistics.getDispatchCount() > 0) {
			String dispatchTypeText = getSelectedItemsTypeTextFromCount(dispatchAttributeCount);
			warningMessages.add(String.format("[%d]个%s已经被安排工作啦，无法删除～", 
					statistics.getDispatchCount(), dispatchTypeText));
		}
		
		// 3. 库存警告
		if (statistics.getStockCount() > 0) {
			String stockTypeText = getSelectedItemsTypeTextFromCount(stockAttributeCount);
			warningMessages.add(String.format("[%d]个%s还有库存哦，无法删除～", 
					statistics.getStockCount(), stockTypeText));
		}
		
		// 返回第一个警告消息（最高优先级）
		return warningMessages.isEmpty() ? "存在无法删除的项目" : warningMessages.get(0);
	}

	/**
	 * 根据属性统计获取类型描述（用于警告消息）
	 */
	private String getSelectedItemsTypeTextFromCount(Map<Integer, Integer> attributeCount) {
		if (attributeCount.isEmpty()) {
			return "项目";
		}

		// 如果只有一种类型
		if (attributeCount.size() == 1) {
			Integer attribute = attributeCount.keySet().iterator().next();
			return getAttributeText(attribute);
		}

		// 多种类型的情况，按优先级返回主要类型
		if (attributeCount.containsKey(1)) {
			return "产品";
		} else if (attributeCount.containsKey(2)) {
			return "部件";
		} else if (attributeCount.containsKey(3)) {
			return "零件";
		} else if (attributeCount.containsKey(4)) {
			return "原料";
		} else {
			return "项目";
		}
	}

	@Override
	public ProductPartBatchDeleteValidationResult validateBatchDeleteProductPart(ProductPartBatchDeleteRpcRequest requestData) throws AssertException {
		ProductPartBatchDeleteValidationResult result = new ProductPartBatchDeleteValidationResult();
		result.setDetailResults(new HashMap<>());
		result.setDeletableProductPartCodes(new ArrayList<>());
		result.setUndeletableProductPartCodes(new ArrayList<>());
		
		// 统计信息
		ProductPartBatchDeleteValidationResult.Statistics statistics = new ProductPartBatchDeleteValidationResult.Statistics();
		statistics.setTotalCount(requestData.getProductPartCodes().size());
		statistics.setDeletableCount(0);
		statistics.setUndeletableCount(0);
		statistics.setContractCount(0);
		statistics.setDispatchCount(0);
		statistics.setStockCount(0);
		statistics.setRelationCount(0);

		boolean allCanDelete = true;
		StringBuilder messageBuilder = new StringBuilder();

		// 逐个校验每个产品部件
		for (Long productPartCode : requestData.getProductPartCodes()) {
			ProductPartRpcRequest singleRequest = new ProductPartRpcRequest();
			singleRequest.setProductPartCode(productPartCode);
			ProductPartDeleteValidationResult singleResult = validateDeleteProductPart(singleRequest);
			result.getDetailResults().put(productPartCode, singleResult);

			if (singleResult.getCanDelete()) {
				result.getDeletableProductPartCodes().add(productPartCode);
				statistics.setDeletableCount(statistics.getDeletableCount() + 1);

				if ("RELATION".equals(singleResult.getValidationType())) {
					statistics.setRelationCount(statistics.getRelationCount() + 1);
				}
			} else {
				result.getUndeletableProductPartCodes().add(productPartCode);
				statistics.setUndeletableCount(statistics.getUndeletableCount() + 1);
				allCanDelete = false;
				// 统计不同类型的阻止原因
				switch (singleResult.getValidationType()) {
					case "CONTRACT":
						statistics.setContractCount(statistics.getContractCount() + 1);
						break;
					case "DISPATCH":
						statistics.setDispatchCount(statistics.getDispatchCount() + 1);
						break;
					case "STOCK":
						statistics.setStockCount(statistics.getStockCount() + 1);
						break;
				}
			}
		}

		result.setStatistics(statistics);
		result.setCanDelete(allCanDelete || requestData.getForceDelete());

		// 生成总体消息
		if (allCanDelete) {
			if (statistics.getRelationCount() > 0) {
				// 统计所有关联关系信息
				String batchRelationMessage = generateBatchRelationMessage(requestData.getProductPartCodes(), result.getDetailResults());
				messageBuilder.append(batchRelationMessage);
			} else {
				// 无关联关系，可以直接删除，原来显示"可以删除"的场景，现在返回空字符串，方便前端根据message信息来判断是否进行二次提示
				messageBuilder.append("是否确定删除？");
			}
		} else {
			// 有无法删除的项目，生成警告提示
			String warningMessage = generateBatchWarningMessage(requestData.getProductPartCodes(), result.getDetailResults(), statistics);
			messageBuilder.append(warningMessage);
		}

		if (null != result.getDetailResults() && result.getDetailResults().size() == 1){
			result.setMessage(result.getDetailResults().values().iterator().next().getMessage());
		}else{
			result.setMessage(messageBuilder.toString());
		}
		return result;
	}

	@Override
	public void batchDeleteProductPart(ProductPartBatchDeleteRpcRequest requestData) throws ExceptionPack, AssertException {
		// 先进行批量校验
		ProductPartBatchDeleteValidationResult validationResult = validateBatchDeleteProductPart(requestData);
		
		if (!validationResult.getCanDelete()) {
			throw new AssertException(ExceptionMsg.builder("Batch delete validation failed")
					.msgView(validationResult.getMessage()).build());
		}

		List<Long> productPartsToDelete;
		if (requestData.getForceDelete()) {
			// 强制删除模式：删除所有可删除的项目
			productPartsToDelete = validationResult.getDeletableProductPartCodes();
		} else {
			// 正常模式：全部校验通过才能删除
			productPartsToDelete = requestData.getProductPartCodes();
		}

		if (productPartsToDelete.isEmpty()) {
			log.warn("没有可删除的产品部件");
			return;
		}

		log.info("开始批量删除产品部件，共{}个", productPartsToDelete.size());

		// 逐个删除
		int successCount = 0;
		int failCount = 0;
		List<String> failedItems = new ArrayList<>();

		for (Long productPartCode : productPartsToDelete) {
			try {
				ProductPartRpcRequest singleRequest = new ProductPartRpcRequest();
				singleRequest.setProductPartCode(productPartCode);
				
				// 调用具体策略实现类的单个删除方法
				deleteProductPart(singleRequest);
				successCount++;
				
				log.debug("成功删除产品部件: {}", productPartCode);
			} catch (Exception e) {
				failCount++;
				String errorMsg = String.format("删除产品部件[%d]失败: %s", productPartCode, e.getMessage());
				failedItems.add(errorMsg);
				log.error(errorMsg, e);
				
				// 根据配置决定是否继续删除其他项目
				// 这里选择继续删除，记录错误但不中断整个批量操作
			}
		}

		String resultMessage = String.format("批量删除完成，成功: %d，失败: %d", successCount, failCount);
		if (failCount > 0) {
			resultMessage += "，失败详情: " + String.join("; ", failedItems);
		}
		
		log.info(resultMessage);

		// 如果有失败的情况，抛出异常
		if (failCount > 0) {
			throw new AssertException(ExceptionMsg.builder("Batch delete partially failed")
					.msgView(resultMessage).build());
		}
	}

	/**
	 * 验证产品部件编辑的合法性
	 * @param productPartCode 产品部件唯一编号
	 * @return ProductPartEditValidationResult 验证结果，包含是否可编辑、消息和验证类型
	 */
	public ProductPartEditValidationResult validateEditProductPart(Long productPartCode) throws AssertException {
		// 校验产品部件编码是否为空
		if (productPartCode == null) {
			throw new AssertException(ExceptionMsg.builder("产品部件编码不能为空")
					.msgView(TechnicalErrorEnum.TECHNICAL_PRODUCT_PART_STATE_IS_TRUE.getMsg()).build());
		}

		// 查询当前产品部件信息（必须包含企业代码和删除标志的过滤条件）
		RequestTableHelper.setTableName(getProductPartTableName());
		LambdaQueryWrapper<ProductPartModel> queryWrapper = Wrappers.lambdaQuery();
		queryWrapper.eq(ProductPartModel::getProductPartCode, productPartCode)
				.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode())
				.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE);
		ProductPartModel currentProductPart = productPartMapper.selectOne(queryWrapper);
		// 校验产品部件是否存在
		if (currentProductPart == null) {
			throw new AssertException(ExceptionMsg.builder("产品部件不存在")
					.msgView(TechnicalErrorEnum.TECHNICAL_PRODUCT_PART_NOT_EXIT.getMsg() + productPartCode).build());
		}
		String prodName = PPAttributeEnum.getEnumByCode(currentProductPart.getAttribute()).getDescription();

		// 0. 校验是否绑定BOM
		boolean hasHasBom = !productPartCommonService.checkHasBom(currentProductPart);
		if (hasHasBom) {
			throw new AssertException(ExceptionMsg.builder("当前部件已经绑定BOM啦，无法停用～")
					.msgView("当前" + prodName + TechnicalErrorEnum.TECHNICAL_PRODUCT_PART_HAS_BOM_TO_STOP.getMsg() + "名称： " + currentProductPart.getProductPartSign()).build());
		}
		// 0. 校验是否绑定BOM wuluxian
		boolean hasHasBomBZN = !productPartCommonService.checkHasBomBZN(productPartCode);
		if (hasHasBomBZN) {
			throw new AssertException(ExceptionMsg.builder("当前部件已经绑定BOM啦，无法删除～")
					.msgView("当前" + prodName + TechnicalErrorEnum.TECHNICAL_PRODUCT_PART_HAS_BOM_TO_DELETE.getMsg() + "名称： " + currentProductPart.getProductPartSign()).build());
		}
		// 0. 校验是否绑定BOM
		boolean hasInBom = !productPartCommonService.checkInBomBZN(productPartCode);
		if (hasInBom) {
			throw new AssertException(ExceptionMsg.builder("当前部件已经挂载在其他产品或部件的BOM中啦，无法删除～")
					.msgView("当前" + prodName + TechnicalErrorEnum.TECHNICAL_PRODUCT_PART_BUNDING_BOM_DELETE.getMsg() + "名称： " + currentProductPart.getProductPartSign()).build());
		}

		// 1. 校验是否有已签章的合同
		boolean hasSignedContract = !productPartCommonService.checkDeleteProductPartNonSignedContract(productPartCode);
		if (hasSignedContract) {
			throw new AssertException(ExceptionMsg.builder("当前部件存在已生效的合同哦，无法停用～")
					.msgView("当前" + prodName + TechnicalErrorEnum.TECHNICAL_PRODUCT_PART_HAS_CONTRACT_TO_STOP.getMsg() + "名称： " + currentProductPart.getProductPartSign()).build());
		}

		// 2. 校验是否被调度安排过
		boolean hasDispatch = !productPartCommonService.checkDeleteProductPartDispatch(productPartCode);
		if (hasDispatch) {
			throw new AssertException(ExceptionMsg.builder("已经被调度安排啦，无法停用～")
					.msgView("当前" + prodName + TechnicalErrorEnum.TECHNICAL_PRODUCT_PART_HAS_TASK_TO_STOP.getMsg() + "名称： " + currentProductPart.getProductPartSign()).build());
		}

		// 3. 校验是否还有库存
		boolean hasStock = productPartCommonService.checkDeleteStockModel(productPartCode);
		if (hasStock) {
			throw new AssertException(ExceptionMsg.builder("当前部件还有库存哦，无法停用～")
					.msgView("当前" + prodName + TechnicalErrorEnum.TECHNICAL_PRODUCT_PART_HAS_STOCKING_NO_STOP.getMsg() + "名称： " + currentProductPart.getProductPartSign()).build());
		}
		// 4. 校验是否绑定BOM
		boolean hasHasBomed = !productPartCommonService.checkHasBomed(currentProductPart);
		if (hasHasBomed) {
			throw new AssertException(ExceptionMsg.builder("当前部件已经绑定BOM啦，无法停用～")
					.msgView("当前" + prodName + TechnicalErrorEnum.TECHNICAL_PRODUCT_PART_BUNDING_BOM_NO_STOP.getMsg() + "名称： " + currentProductPart.getProductPartSign()).build());
		}

		return new ProductPartEditValidationResult()
				.setCanEdit(true)
				.setMessage("SUCCESS")
				.setValidationType("RELATION");
	}

	@Override
	public ProductPartBatchEditValidationResult validateBatchEditProductPart(ProductPartBatchEditRpcRequest requestData) throws AssertException {
		ProductPartBatchEditValidationResult result = new ProductPartBatchEditValidationResult();
		result.setDetailResults(new HashMap<>());
		result.setEditableProductPartCodes(new ArrayList<>());
		result.setUneditableProductPartCodes(new ArrayList<>());

		// 统计信息
		ProductPartBatchEditValidationResult.Statistics statistics = new ProductPartBatchEditValidationResult.Statistics();
		statistics.setTotalCount(requestData.getProductPartCodes().size());
		statistics.setEditableCount(0);
		statistics.setUneditableCount(0);
		statistics.setContractCount(0);
		statistics.setDispatchCount(0);
		statistics.setStockCount(0);
		statistics.setRelationCount(0);

		boolean allCanDelete = true;
		StringBuilder messageBuilder = new StringBuilder();

		// 逐个校验每个产品部件
		for (Long productPartCode : requestData.getProductPartCodes()) {
			ProductPartEditValidationResult singleResult = validateEditProductPart(productPartCode);
			result.getDetailResults().put(productPartCode, singleResult);

			if (singleResult.getCanEdit()) {
				result.getEditableProductPartCodes().add(productPartCode);
				statistics.setEditableCount(statistics.getEditableCount() + 1);

				if ("RELATION".equals(singleResult.getValidationType())) {
					statistics.setRelationCount(statistics.getRelationCount() + 1);
				}
			} else {
				result.getUneditableProductPartCodes().add(productPartCode);
				statistics.setUneditableCount(statistics.getUneditableCount() + 1);
				allCanDelete = false;

				// 统计不同类型的阻止原因
				switch (singleResult.getValidationType()) {
					case "CONTRACT":
						statistics.setContractCount(statistics.getContractCount() + 1);
						break;
					case "DISPATCH":
						statistics.setDispatchCount(statistics.getDispatchCount() + 1);
						break;
					case "STOCK":
						statistics.setStockCount(statistics.getStockCount() + 1);
						break;
				}
			}
		}

		result.setStatistics(statistics);
		result.setCanEdit(allCanDelete);

		// 生成总体消息
		if (allCanDelete) {
			if (statistics.getRelationCount() > 0) {
				// 统计所有关联关系信息
				String batchRelationMessage = generateBatchRelationMessage4Edit(requestData.getProductPartCodes(), result.getDetailResults());
				messageBuilder.append(batchRelationMessage);
			} else {
				// 无关联关系，可以直接删除，原来显示"可以删除"的场景，现在返回空字符串，方便前端根据message信息来判断是否进行二次提示
				messageBuilder.append("是否确定删除？");
			}
		} else {
			// 有无法删除的项目，生成警告提示
			String warningMessage = generateBatchWarningMessage4Edit(requestData.getProductPartCodes(), result.getDetailResults(), statistics);
			messageBuilder.append(warningMessage);
		}

		result.setMessage(messageBuilder.toString());
		return result;
	}


	/**
	 * 生成批量关联关系消息
	 * @param productPartCodes 产品部件代码列表
	 * @param detailResults 产品部件编辑验证结果映射
	 * @return 格式化后的删除确认消息
	 */
	private String generateBatchRelationMessage4Edit(List<Long> productPartCodes,
	                                            Map<Long, ProductPartEditValidationResult> detailResults) {

		// 统计选中的产品部件类型
		Map<Integer, Integer> selectedAttributeCount = new HashMap<>();
		// 统计所有关联的产品部件类型和数量
		Map<Integer, Integer> relatedAttributeCount = new HashMap<>();
		int totalRelatedCount = 0;

		try {
			// 查询选中的产品部件信息
			RequestTableHelper.setTableName(getProductPartTableName());
			List<ProductPartModel> selectedProductParts = productPartMapper.selectBatchIds(productPartCodes);

			// 统计选中项的类型分布
			for (ProductPartModel productPart : selectedProductParts) {
				Integer attribute = productPart.getAttribute();
				selectedAttributeCount.put(attribute, selectedAttributeCount.getOrDefault(attribute, 0) + 1);
			}

			// 统计关联项的类型分布
			for (ProductPartEditValidationResult result : detailResults.values()) {
				if ("RELATION".equals(result.getValidationType()) && result.getRelatedItems() != null) {
					for (ProductPartEditValidationResult.RelatedProductPartInfo relatedItem : result.getRelatedItems()) {
						Integer attribute = relatedItem.getAttribute();
						relatedAttributeCount.put(attribute, relatedAttributeCount.getOrDefault(attribute, 0) + 1);
						totalRelatedCount++;
					}
				}
			}

		} catch (Exception e) {
			log.warn("查询产品部件信息失败，使用默认描述", e); // 记录查询失败日志
		}

		// 生成选中项的类型描述
		String selectedTypeText = getSelectedItemsTypeText(selectedAttributeCount, productPartCodes.size());

		// 生成关联项的类型描述
		String relatedTypeText = getRelatedItemsTypeTextFromCount(relatedAttributeCount);

		// 生成最终消息
		return String.format("选中的 [%d] 个%s共关联 [%d] 件%s，提交后系统将自动解绑，是否确定删除？",
				productPartCodes.size(), selectedTypeText, totalRelatedCount, relatedTypeText);
	}

	/**
	 * 生成编辑时的批量警告消息
	 * @param productPartCodes 产品部件代码列表
	 * @param detailResults 产品部件删除验证结果详情映射
	 * @param statistics 统计信息
	 * @return 警告消息字符串
	 */
	private String generateBatchWarningMessage4Edit(List<Long> productPartCodes,
	                                           Map<Long, ProductPartEditValidationResult> detailResults,
	                                           ProductPartBatchEditValidationResult.Statistics statistics) {

		// 统计各种无法删除的原因对应的产品部件类型
		Map<Integer, Integer> contractAttributeCount = new HashMap<>(); // 合同属性计数映射
		Map<Integer, Integer> dispatchAttributeCount = new HashMap<>(); // 调度属性计数映射
		Map<Integer, Integer> stockAttributeCount = new HashMap<>(); // 库存属性计数映射

		try {
			// 查询所有产品部件信息
			RequestTableHelper.setTableName(getProductPartTableName()); // 设置产品部件表名
			List<ProductPartModel> allProductParts = productPartMapper.selectBatchIds(productPartCodes); // 批量查询产品部件
			Map<Long, Integer> codeToAttributeMap = allProductParts.stream()
					.collect(Collectors.toMap(
							ProductPartModel::getProductPartCode, // 产品部件代码
							ProductPartModel::getAttribute,       // 产品部件属性
							(existing, replacement) -> existing   // 合并函数，保留现有值
					));

			// 统计各种无法删除原因对应的类型分布
			for (Map.Entry<Long, ProductPartEditValidationResult> entry : detailResults.entrySet()) {
				Long productPartCode = entry.getKey(); // 产品部件代码
				ProductPartEditValidationResult result = entry.getValue(); // 验证结果

				if (!result.getCanEdit()) { // 如果不能删除
					Integer attribute = codeToAttributeMap.get(productPartCode);
					if (attribute != null) { // 获取产品部件属性
						switch (result.getValidationType()) {
							case "CONTRACT": // 根据验证类型进行分类统计
								contractAttributeCount.put(attribute, contractAttributeCount.getOrDefault(attribute, 0) + 1); // 合同类型
								break;
							case "DISPATCH":
								dispatchAttributeCount.put(attribute, dispatchAttributeCount.getOrDefault(attribute, 0) + 1); // 调度类型
								break;
							case "STOCK":
								stockAttributeCount.put(attribute, stockAttributeCount.getOrDefault(attribute, 0) + 1); // 库存类型
								break;
						}
					}
				}
			}

		} catch (Exception e) {
			log.warn("查询产品部件信息失败，使用默认描述", e);
		} // 记录警告日志

		// 生成警告消息，按优先级：合同 > 调度 > 库存
		List<String> warningMessages = new ArrayList<>();
 // 警告消息列表
		// 1. 合同警告
		if (statistics.getContractCount() > 0) {
			String contractTypeText = getSelectedItemsTypeTextFromCount(contractAttributeCount); // 如果存在合同限制
			warningMessages.add(String.format("[%d]个%s存在已生效的合同哦，无法删除～", // 获取合同类型文本
					statistics.getContractCount(), contractTypeText));
		} // 添加合同警告消息

		// 2. 调度警告
		if (statistics.getDispatchCount() > 0) {
			String dispatchTypeText = getSelectedItemsTypeTextFromCount(dispatchAttributeCount); // 如果存在调度限制
			warningMessages.add(String.format("[%d]个%s已经被安排工作啦，无法删除～", // 获取调度类型文本
					statistics.getDispatchCount(), dispatchTypeText));
		} // 添加调度警告消息

		// 3. 库存警告
		if (statistics.getStockCount() > 0) {
			String stockTypeText = getSelectedItemsTypeTextFromCount(stockAttributeCount); // 如果存在库存限制
			warningMessages.add(String.format("[%d]个%s还有库存哦，无法删除～", // 获取库存类型文本
					statistics.getStockCount(), stockTypeText));
		} // 添加库存警告消息

		// 返回第一个警告消息（最高优先级）
		return warningMessages.isEmpty() ? "存在无法删除的项目" : warningMessages.get(0);
	} // 返回最高优先级的警告消息


	@Override
	public ProductPartResultRpcDTO checkNameAndModel(ProductPartCheckNameModelRpcRequest requestData) {
		ProductPartResultRpcDTO result = new ProductPartResultRpcDTO();
		boolean isAllow = false;
		String msg = "提示信息：";
		String code ="-1";
		try {
			String productPartTableName =
					tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
			LambdaUpdateWrapper<ProductPartModel> queryWp = Wrappers.lambdaUpdate();
			queryWp.eq(ProductPartModel::getAttribute, requestData.getAttribute())
					.eq(ProductPartModel::getProductPartTypeCode, requestData.getProductPartTypeCode())
					.eq(ProductPartModel::getState, Boolean.TRUE)
					.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE)
					.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode());
			RequestTableHelper.setTableName(productPartTableName);
			List<ProductPartModel> productPartList = productPartMapper.selectList(queryWp);
			if (null != requestData.getProductPartCode() && 0L != requestData.getProductPartCode()) {
				// productPartList中过滤掉当前数据对象
				productPartList = productPartList.stream()
						.filter(productPart -> !requestData.getProductPartCode().equals(productPart.getProductPartCode()))
						.collect(Collectors.toList());
			}
			// 筛选 productPartList 中 ProductPartModel 对象内 productPartSign=requestData 中的 name 的数据，作为 nameList
			List<ProductPartModel> nameList = productPartList.stream()
					.filter(productPart -> StringUtils.equals(productPart.getProductPartSign(), requestData.getName()))
					.collect(Collectors.toList());
			List<ProductPartModel> modelList = productPartList.stream()
					.filter(productPart -> StringUtils.equals(productPart.getModel(), requestData.getModel()))
					.collect(Collectors.toList());

			if (nameList.size() > 0 || modelList.size() > 0) {
				if (nameList.size() > 0){
					msg += " 产品部件名称已存在";
				}
				if (nameList.size() > 0){
					msg += " 产品部件型号已存在";
				}
			} else {
				code = "0";
				msg += "产品部件名称和型号都不存在";
				isAllow = true;
			}
			result.setReturnCode(code);
			result.setReturnMsg(msg);
			result.setIsAllow(isAllow);
		} catch (Exception e) {
			log.error("查询产品部件名称和型号失败", e);
		}
		return result;
	}

}
