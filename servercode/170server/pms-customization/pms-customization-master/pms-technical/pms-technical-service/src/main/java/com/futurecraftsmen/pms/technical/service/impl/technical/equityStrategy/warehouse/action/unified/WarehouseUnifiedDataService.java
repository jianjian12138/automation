/*
 * Copyright (c) Zhongzao Software Co. LTD 2022-2062 All rights reserved
 */

package com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.warehouse.action.unified;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.futurecraftsmen.pms.dm.api.service.enumvalue.EnumService;
import com.futurecraftsmen.pms.service.configuration.RequestTableHelper;
import com.futurecraftsmen.pms.service.domain.table.ModuleEnNameBaseConfig;
import com.futurecraftsmen.pms.starter.domain.starter.PmsStarter;
import com.futurecraftsmen.pms.technical.api.domain.warehouseinventory.WarehouseMergeImportRpcModel;
import com.futurecraftsmen.pms.technical.api.domain.warehouseinventory.WarehousePositionDetailModel;
import com.futurecraftsmen.pms.technical.service.config.EnumGroupCodeConfig;
import com.futurecraftsmen.pms.technical.service.dao.warehouse.IPositionMapper;
import com.futurecraftsmen.pms.technical.service.dao.warehouse.IPositionOperationMapper;
import com.futurecraftsmen.pms.technical.service.dao.warehouse.IPositionStockBatchMapper;
import com.futurecraftsmen.pms.technical.service.dao.warehouse.IStockMapper;
import com.futurecraftsmen.pms.technical.service.domain.technical.productpart.ProductPartModel;
import com.futurecraftsmen.pms.technical.service.domain.warehouse.*;
import com.futurecraftsmen.pms.technical.service.impl.inner.TableNameFactory;
import com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart.action.unified.TechnicalUnifiedDataService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.aerie.forest.core.brick.domain.view.CodeMapName;
import org.aerie.forest.core.brick.exception.ExceptionPack;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.futurecraftsmen.pms.service.domain.extract.ExtractUtil.streamMapToList;
import static com.futurecraftsmen.pms.technical.service.util.CommonUtil.getEnterpriseCode;

/**
 * @description 数据准备 中间服务
 *
 * @author qierkang
 * @organization futurecraftsmen
 * @date Created in 2025/5/29 22:14
 * @department: Product development
 */
@Slf4j
@Service
public class WarehouseUnifiedDataService {

	@Resource
	public ModuleEnNameBaseConfig moduleEnNameConfig;
	@Resource
	private IPositionMapper positionMapper;
	@Resource
	private IStockMapper stockMapper;
	@Resource
	private EnumService enumService;
	@Resource
	private EnumGroupCodeConfig enumGroupCodeConfig;
	@Resource
	private TechnicalUnifiedDataService technicalUnifiedDataService;
	@Resource
	private IPositionStockBatchMapper positionStockBatchMapper;
	@Resource
	private IPositionOperationMapper positionOperationMapper;
	@Resource
	private TableNameFactory tableFactory;

	/**
	 * @description 仓库仓位数据准备
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2024/12/13 16:15
	 */
	public Map<String, PositionTableModel> positionModelMapBz(
			List<WarehouseMergeImportRpcModel> allExcelModel,
			String positionTableName) {
		return processPositionModelMap(allExcelModel, positionTableName, WarehouseMergeImportRpcModel::getWarehousePositionList);
	}

	/**
	 * @description 仓库仓位数据准备
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/6/5 23:04
	 * @department: Product development
	 */
	public Map<String, PositionTableModel> positionModelMapHj(
			List<WarehouseMergeImportRpcModel> allExcelModel,
			String positionTableName) {
		return processPositionModelMap(allExcelModel, positionTableName, WarehouseMergeImportRpcModel::getWarehousePositionList);
	}

	/**
	 * @description 统一提取仓位信息Map
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/5/29 23:26
	 * @department: Product development
	 */
	public <T> Map<String, PositionTableModel> processPositionModelMap(
			List<T> dataList,
			String positionTableName,
			Function<T, List<WarehousePositionDetailModel>> detailExtractor) {
		Set<String> warehouseNames = new HashSet<>();
		Set<String> positionNames = new HashSet<>();
		if (CollUtil.isEmpty(dataList)) {
			return Collections.emptyMap();
		}
		for (T item : dataList) {
			List<WarehousePositionDetailModel> details = detailExtractor.apply(item);
			if (CollUtil.isNotEmpty(details)) {
				details.forEach(detail -> {
					if (StrUtil.isNotBlank(detail.getPositionWarehouseName())) {
						warehouseNames.add(detail.getPositionWarehouseName());
					}
					if (StrUtil.isNotBlank(detail.getPositionName())) {
						positionNames.add(detail.getPositionName());
					}
				});
			}
		}
		if (warehouseNames.isEmpty() || positionNames.isEmpty()) {
			return Collections.emptyMap();
		}
		// 构建查询条件
		LambdaQueryWrapper<PositionTableModel> qw = Wrappers.lambdaQuery();
		qw.in(PositionTableModel::getWarehorse, warehouseNames);
		qw.in(PositionTableModel::getPositionSign, positionNames);
		qw.eq(PositionTableModel::getEnterpriseCode, getEnterpriseCode());
		qw.eq(PositionTableModel::getDeleteFlag, Boolean.FALSE);
		RequestTableHelper.setTableName(positionTableName);
		return positionMapper.selectList(qw).stream()
				.collect(Collectors.toMap(
						PositionTableModel::getPositionSign,
						Function.identity(),
						(existing, replacement) -> existing));
	}

	/**
	 * 初始化仓库批次库存模型
	 *
	 * @param stockModel           库存模型
	 * @param positionCode         仓位编码
	 * @param enterpriseCode       企业编码
	 * @param productPartCode      物料/零件编码
	 * @param positionDetailModel  仓位详情模型
	 * @return 新建的仓库批次库存模型
	 */
	public PositionStockBatchModel createPositionStockBatchModel(
			StockModel stockModel,
			Long positionCode,
			Long enterpriseCode,
			Long productPartCode,
			WarehousePositionDetailModel positionDetailModel) {
		Long batchCode = PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId();
		Long primaryKey = PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId();
		PositionStockBatchModel model = new PositionStockBatchModel();
		model.setStockCode(stockModel.getPrimaryKeyValue());
		model.setPositionCode(positionCode);
		model.setEnterpriseCode(enterpriseCode);
		model.setProductOrPart(productPartCode);
		model.setBatchCode(batchCode);
		model.setInStorageType(0L);
		model.setBatch(StrUtil.isBlank(positionDetailModel.getPositionBatch())
				? "系统导入"
				: positionDetailModel.getPositionBatch());
		model.setPositionStockBatchNum(positionDetailModel.getPositionBatchNum() == null
				? BigDecimal.ZERO :
				positionDetailModel.getPositionBatchNum());
		model.setPrimaryKey(primaryKey);
		return model;
	}

	/**
	 * @description 更新库存总库存数
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/6/15 17:42
	 * @department: Product development
	 */
	public void updateStockInventory(BigDecimal total, Long stockCode, String tableName) {
		LambdaUpdateWrapper<StockModel> stockUw = Wrappers.lambdaUpdate();
		stockUw.set(StockModel::getTotalInventory, total)
				.eq(StockModel::getStockCode, stockCode)
				.eq(StockModel::getDeleteFlag, Boolean.FALSE);
		RequestTableHelper.setTableName(tableName);
		stockMapper.update(stockUw);
	}

	public Map<String, Long> getStocktakingCycleMap() {
		try {
			return enumService.getEnumByEnumGroupCode(enumGroupCodeConfig.getStocktakingCycle(), true, true).stream()
					.map(p -> new CodeMapName(p.getEnumCode(), p.getEnumName())).toList().stream()
					.collect(Collectors.toMap(CodeMapName::getName, CodeMapName::getCode));
		} catch (Exception e) {
			log.error("查询 盘点周期失败，不做任何处理 ", e);
		}
		return new HashMap<>();
	}

	/**
	 * @description 转换：产品code转换成产品编号 Map<String,StockModel>
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/6/4 18:33
	 * @department: Product development
	 */
	public Map<String, StockModel> buildStockMapByUnityNo(
			List<WarehouseMergeImportRpcModel> allExcelModel,
			String productPartTableName,
			Long enterpriseCode,
			String stockTableName) {
		List<String> unityNoList = streamMapToList(String::valueOf, allExcelModel, WarehouseMergeImportRpcModel::getUnityNo);
		Map<String, ProductPartModel> unityNoToMap = technicalUnifiedDataService.prepareUnityNoData(unityNoList, productPartTableName);
		Map<String, Long> unityNoToProductPartCode = unityNoToMap.entrySet().stream()
				.filter(e -> e.getValue() != null)
				.collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getProductPartCode()));
		List<Long> productPartCodeList = unityNoToProductPartCode.values().stream()
				.distinct()
				.toList();
		Map<String, StockModel> result = new HashMap<>();
		if (CollUtil.isNotEmpty(productPartCodeList)) {
			List<StockModel> stockModelList = stockMapper.getStockByProductPartCodeInNew(
					productPartCodeList, enterpriseCode, stockTableName
			);
			Map<Long, List<StockModel>> stockMapByProductPartCode = stockModelList.stream()
					.collect(Collectors.groupingBy(StockModel::getProductPartCode));
			for (Map.Entry<String, Long> entry : unityNoToProductPartCode.entrySet()) {
				if (stockMapByProductPartCode.containsKey(entry.getValue())) {
					List<StockModel> stocks = stockMapByProductPartCode.get(entry.getValue());
					result.put(entry.getKey(), stocks.getFirst());
				}
			}
		}
		return result;
	}

	/**
	 * @description 新增仓库仓位信息并同步新增仓库批次库存
	 *
	 * @param positionDetailModel 仓位详情模型
	 * @param stockModel 库存模型
	 * @param enterpriseCode 企业编号
	 * @param productPartCode 零件编码
	 * @param positionSum 总数量
	 * @param positionStockCode 仓位库存编号
	 * @param addedPositionMaterials 已添加的 (positionCode, productPartCode) 集合
	 * @param positionStockModelAddList 要新增的仓位库存列表
	 * @param batchAddList 批次库存新增列表
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/6/22 13:55
	 * @department: Product development
	 */
	public void handlePositionStockNotExists(WarehousePositionDetailModel positionDetailModel,
	                                         StockModel stockModel,
	                                         Long operator,
	                                         Long enterpriseCode,
	                                         Long productPartCode,
	                                         BigDecimal positionSum,
	                                         Long positionStockCode,
	                                         Map<Long, Set<Long>> addedPositionMaterials,
	                                         List<PositionStockModel> positionStockModelAddList,
	                                         List<PositionStockBatchModel> batchAddList,
	                                         List<PositionOperationModel> storePositionOperationModelList) {
		PositionStockModel savePositionStockModel = new PositionStockModel();
		savePositionStockModel.setPositionCode(positionDetailModel.getPositionCode());
		savePositionStockModel.setMaterial(productPartCode);
		savePositionStockModel.setStock(stockModel.getPrimaryKeyValue());
		savePositionStockModel.setPositionStockNum(positionSum);
		savePositionStockModel.setPositionStockCode(positionStockCode);
		savePositionStockModel.setEnterpriseCode(enterpriseCode);
		// 判断是否已添加过相同 (positionCode, productPartCode)
		addedPositionMaterials.computeIfAbsent(positionDetailModel.getPositionCode(), k -> new HashSet<>());
		if (!addedPositionMaterials.get(positionDetailModel.getPositionCode()).contains(productPartCode)) {
			positionStockModelAddList.add(savePositionStockModel);
			addedPositionMaterials.get(positionDetailModel.getPositionCode()).add(productPartCode);
		}
		// 同步新增仓库批次库存
		PositionStockBatchModel positionStockBatchModel = createPositionStockBatchModel(
				stockModel,
				positionDetailModel.getPositionCode(),
				enterpriseCode,
				productPartCode,
				positionDetailModel
		);
		batchAddList.add(positionStockBatchModel);
		// 库存记录 入库的情况
		createAndAddPositionOperation(stockModel, positionDetailModel.getPositionBatchNum(), enterpriseCode, operator,
				positionDetailModel.getPositionCode(),
				positionDetailModel.getPositionBatch(),
				Boolean.FALSE, storePositionOperationModelList);
	}

	/**
	 * @description 检查并处理批次库存信息（新增或更新）
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/6/22 13:57
	 * @department: Product development
	 */
	public void handleBatchStockIfNeeded(StockModel stockModel,
	                                     PositionStockModel positionStockModel,
	                                     Long operator,
	                                     Long enterpriseCode,
	                                     Long productPartCode,
	                                     String positionStockBatchTableName,
	                                     WarehousePositionDetailModel positionDetailModel,
	                                     List<PositionStockBatchModel> batchAddList,
	                                     List<PositionStockBatchModel> batchUpdateList,
	                                     List<PositionOperationModel> storePositionOperationModelList
	) {
		// 新-仓位操作记录表
		String positionOperationTableName = tableFactory.getTableName(tableFactory.module.getWarehouse(),
				tableFactory.table.getPositionOperation());
		LambdaQueryWrapper<PositionStockBatchModel> qw = Wrappers.lambdaQuery();
		qw.eq(PositionStockBatchModel::getStockCode, stockModel.getPrimaryKeyValue());
		qw.eq(PositionStockBatchModel::getPositionCode, positionStockModel.getPositionCode());
		qw.eq(PositionStockBatchModel::getProductOrPart, productPartCode);
		qw.eq(PositionStockBatchModel::getEnterpriseCode, enterpriseCode);
		qw.eq(PositionStockBatchModel::getDeleteFlag, false);
		RequestTableHelper.setTableName(positionStockBatchTableName);
		List<PositionStockBatchModel> positionStockBatchModels = positionStockBatchMapper.selectList(qw);
		if (ObjectUtil.isEmpty(positionStockBatchModels)) {
			// 同步新增仓库批次库存
			PositionStockBatchModel positionStockBatchModel = createPositionStockBatchModel(
					stockModel,
					positionDetailModel.getPositionCode(),
					enterpriseCode,
					productPartCode,
					positionDetailModel
			);
			batchAddList.add(positionStockBatchModel);
			// 库存记录 入库的情况
			createAndAddPositionOperation(stockModel, positionDetailModel.getPositionBatchNum(), enterpriseCode, operator,
					positionDetailModel.getPositionCode(),
					positionDetailModel.getPositionBatch(),
					Boolean.FALSE, storePositionOperationModelList);
		} else {
			// 记录库存批次数据
			for (PositionStockBatchModel positionStockBatchModel : positionStockBatchModels) {
				// 先清空仓位批次数据，库存记录 出库的情况
				LambdaUpdateWrapper<PositionStockBatchModel> psUw = Wrappers.lambdaUpdate();
				psUw.set(PositionStockBatchModel::getDeleteFlag, Boolean.TRUE);
				psUw.eq(PositionStockBatchModel::getDeleteFlag, Boolean.FALSE);
				psUw.eq(PositionStockBatchModel::getStockCode, stockModel.getPrimaryKeyValue());
				psUw.eq(PositionStockBatchModel::getPositionCode, positionStockBatchModel.getPositionCode());
				psUw.eq(PositionStockBatchModel::getProductOrPart, positionStockBatchModel.getProductOrPart());
				RequestTableHelper.setTableName(positionStockBatchTableName);
				positionStockBatchMapper.update(psUw);
				positionStockBatchModel.setBatch(positionDetailModel.getPositionBatch());
				positionStockBatchModel.setPositionStockBatchNum(positionDetailModel.getPositionBatchNum());
				batchUpdateList.add(positionStockBatchModel);
			}
			LambdaUpdateWrapper<PositionOperationModel> psUw = Wrappers.lambdaUpdate();
			psUw.eq(PositionOperationModel::getMaterial, positionDetailModel.getProductPartCode());
			psUw.eq(PositionOperationModel::getPosition, positionDetailModel.getPositionCode());
			psUw.eq(PositionOperationModel::getEnterpriseCode, enterpriseCode);
			psUw.eq(PositionOperationModel::getDeleteFlag, Boolean.FALSE);
			RequestTableHelper.setTableName(positionOperationTableName);
			List<PositionOperationModel> operationModels = positionOperationMapper.selectList(psUw);
			for (PositionOperationModel operationModel : operationModels) {
				operationModel.setOperationType(Boolean.TRUE);
				RequestTableHelper.setTableName(positionOperationTableName);
				positionOperationMapper.updateById(operationModel);
			}
			// 库存记录 入库的情况
			createAndAddPositionOperation(stockModel, positionDetailModel.getPositionBatchNum(), enterpriseCode, operator,
					positionDetailModel.getPositionCode(),
					positionDetailModel.getPositionBatch(),
					Boolean.FALSE, storePositionOperationModelList);
		}
	}

	/**
	 * @description 记录库存操作日志
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/6/22 14:11
	 * @department: Product development
	 */
	public void createAndAddPositionOperation(StockModel stockModel,
	                                          BigDecimal operationNum,
	                                          Long enterpriseCode,
	                                          Long operator,
	                                          Long positionCode,
	                                          String checkBatchNumber,
	                                          boolean operationType,
	                                          List<PositionOperationModel> storePositionOperationModelList) {
		Long operationVersion = PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId();
		PositionOperationModel positionOperationModel = new PositionOperationModel();
		positionOperationModel.setPosition(positionCode);
		positionOperationModel.setStock(stockModel.getPrimaryKeyValue());
		positionOperationModel.setOperationNum(operationNum);
		positionOperationModel.setCheckBatchNumber(checkBatchNumber);
		positionOperationModel.setOperator(operator);
		positionOperationModel.setOperationType(operationType);
		positionOperationModel.setOperationTime(DateUtil.date());
		positionOperationModel.setMaterial(stockModel.getProductPartCode());
		positionOperationModel.setPositionOperationCode(PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId());
		positionOperationModel.setEnterpriseCode(enterpriseCode);
		positionOperationModel.setApplyNum(stockModel.getTotalInventory());
		positionOperationModel.setOperationVersion(operationVersion);
		storePositionOperationModelList.add(positionOperationModel);
	}
}
