/*
 * Copyright (c) Zhongzao Software Co. LTD 2022-2062 All rights reserved
 */
package com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.warehouse.action;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.futurecraftsmen.pms.api.domain.ServiceErrorCode;
import com.futurecraftsmen.pms.api.dto.MultipartFileRpcDTO;
import com.futurecraftsmen.pms.common.domain.excel.ParseExcelResult;
import com.futurecraftsmen.pms.common.domain.exception.ExcelException;
import com.futurecraftsmen.pms.common.excel.multi.ExcelMultiModelsProcessor;
import com.futurecraftsmen.pms.service.configuration.RequestTableHelper;
import com.futurecraftsmen.pms.service.domain.BaseExcelHeaderGenerator;
import com.futurecraftsmen.pms.starter.domain.starter.PmsStarter;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.standard.BatchAddWarehouseWorkRequest;
import com.futurecraftsmen.pms.technical.api.domain.warehouseinventory.WarehouseMergeImportRpcModel;
import com.futurecraftsmen.pms.technical.api.domain.warehouseinventory.WarehousePositionDetailModel;
import com.futurecraftsmen.pms.technical.service.dao.warehouse.IPositionOperationMapper;
import com.futurecraftsmen.pms.technical.service.dao.warehouse.IPositionStockMapper;
import com.futurecraftsmen.pms.technical.service.dao.warehouse.IStockMapper;
import com.futurecraftsmen.pms.technical.service.domain.warehouse.PositionOperationModel;
import com.futurecraftsmen.pms.technical.service.domain.warehouse.PositionStockBatchModel;
import com.futurecraftsmen.pms.technical.service.domain.warehouse.PositionStockModel;
import com.futurecraftsmen.pms.technical.service.domain.warehouse.StockModel;
import com.futurecraftsmen.pms.technical.service.domain.warehouse.excel.DynamicWarehouseExcelParser;
import com.futurecraftsmen.pms.technical.service.domain.warehouse.excel.WarehouseDataCleaner;
import com.futurecraftsmen.pms.technical.service.domain.warehouse.excel.WarehouseExcelAnalyzeValidatorBz;
import com.futurecraftsmen.pms.technical.service.impl.inner.TableNameFactory;
import com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.warehouse.abstracts.AbstractWarehouse;
import com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.warehouse.action.unified.WarehouseUnifiedDataService;
import com.futurecraftsmen.pms.technical.service.impl.warehouse.PositionStockBatchServiceImpl;

import org.aerie.forest.core.brick.exception.ExceptionMsg;
import org.aerie.forest.core.brick.exception.ExceptionPack;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import static com.futurecraftsmen.pms.technical.service.util.CommonUtil.getEnterpriseCode;
import static com.futurecraftsmen.pms.technical.service.util.CommonUtil.getUserCode;

/**
 * @description 仓库-标准版本-策略实现
 *
 * @author qierkang
 * @organization futurecraftsmen
 * @date Created in 2025/4/25 00:06
 * @department: Product development
 */
@Slf4j
@Component
public class BZYWarehouseAction extends AbstractWarehouse {

	@Resource
	private TableNameFactory tableFactory;
	@Resource
	private IStockMapper stockMapper;
	@Resource
	private IPositionStockMapper positionStockMapper;
	@Resource
	private PositionStockBatchServiceImpl positionStockBatchService;
	@Resource
	private WarehouseExcelAnalyzeValidatorBz warehouseExcelAnalyzeValidator;
	@Resource
	private WarehouseUnifiedDataService warehouseUnifiedDataService;
	@Resource
	private IPositionOperationMapper positionOperationMapper;

	@Override
	public ParseExcelResult<?> warehouseMergeExportAnalyze(MultipartFileRpcDTO excelFile) throws ExceptionPack {
		try {
			// 使用动态解析工具类，支持真正的动态导入：根据Excel实际表头动态映射字段
			// 所有字段范围均在 WarehouseMergeImportRpcModel 中定义
			// 必填字段：xh, typeName, unityNo, name, model, pcsChn, canUseInventory, stockLimit, positionWarehouseName, positionName
			log.info("开始使用动态解析工具解析Excel文件，支持列名动态匹配");

			// 使用动态解析工具类
			ParseExcelResult<WarehouseMergeImportRpcModel> parseExcelResult =
					DynamicWarehouseExcelParser.parseDynamicExcel(new ByteArrayInputStream(excelFile.getBytes()));

			List<WarehouseMergeImportRpcModel> importRpcModels = parseExcelResult.getDatas();
			log.info("动态解析完成，处理数据大小 {}", importRpcModels == null ? 0 : importRpcModels.size());

			if (importRpcModels == null || importRpcModels.isEmpty()) {
				log.warn("解析后的数据为空，请检查Excel文件格式和列名是否匹配");
				return new ParseExcelResult<>(new ArrayList<>(), parseExcelResult != null ? parseExcelResult.getHeadList() : new ArrayList<>());
			}

			// 执行数据校验（包含必填字段校验）
			warehouseExcelAnalyzeValidator.checkAll(importRpcModels, "动态导入数据校验");

			// 执行数据初始化清洗
			List<String> errorMessages = new ArrayList<>();
			List<WarehouseMergeImportRpcModel> cleanedList =
					WarehouseDataCleaner.cleanAndMerge(importRpcModels, errorMessages);

			// 添加排序逻辑：按照 xh 字段从小到大排序
			cleanedList.sort((o1, o2) -> {
				if (o1.getXh() == null) return -1;
				if (o2.getXh() == null) return 1;
				return o1.getXh().compareTo(o2.getXh());
			});

			// === 修复：将warehousePositionList展开，每个仓位信息一行，确保6条数据返回6条记录 ===
			// 如果数据被合并成1条记录（warehousePositionList有多个元素），需要展开成多条记录
			List<WarehouseMergeImportRpcModel> flattenedList = new ArrayList<>();
			for (WarehouseMergeImportRpcModel model : cleanedList) {
				List<WarehousePositionDetailModel> positionList = model.getWarehousePositionList();
				
				// 如果warehousePositionList为空或只有一个元素，直接添加
				if (positionList == null || positionList.isEmpty()) {
					// 如果warehousePositionList为空，但模型本身有仓位字段，创建一个仓位信息
					if (StrUtil.isNotBlank(model.getPositionWarehouseName()) || StrUtil.isNotBlank(model.getPositionName())) {
						WarehousePositionDetailModel positionDetail = new WarehousePositionDetailModel();
						positionDetail.setPositionWarehouseName(model.getPositionWarehouseName());
						positionDetail.setPositionName(model.getPositionName());
						positionDetail.setPositionBatch(model.getPositionBatch());
						positionDetail.setPositionBatchNum(model.getPositionBatchNum());
						positionDetail.setPositionCode(model.getPositionCode());
						positionDetail.setProductPartCode(model.getProductPartCode());
						
						positionList = new ArrayList<>();
						positionList.add(positionDetail);
						model.setWarehousePositionList(positionList);
					} else {
						// 如果仍然没有仓位信息，至少创建一行空记录（用于显示物料基本信息）
						positionList = new ArrayList<>();
						positionList.add(new WarehousePositionDetailModel());
						model.setWarehousePositionList(positionList);
					}
				}
				
				// 为每个仓位信息创建一条扁平化的记录
				for (WarehousePositionDetailModel positionDetail : positionList) {
					WarehouseMergeImportRpcModel flattenedModel = new WarehouseMergeImportRpcModel();
					
					// 复制物料基本信息（序号、编号、分类、名称等）
					BeanUtils.copyProperties(model, flattenedModel);
					
					// 复制仓位信息到主模型字段（用于前端显示）
					if (positionDetail != null) {
						flattenedModel.setPositionWarehouseName(positionDetail.getPositionWarehouseName());
						flattenedModel.setPositionName(positionDetail.getPositionName());
						flattenedModel.setPositionBatch(positionDetail.getPositionBatch());
						flattenedModel.setPositionBatchNum(positionDetail.getPositionBatchNum());
						flattenedModel.setPositionCode(positionDetail.getPositionCode());
						
						// 复制错误信息
						if (positionDetail.getCheckFailList() != null && !positionDetail.getCheckFailList().isEmpty()) {
							flattenedModel.setCheckFailList(new ArrayList<>(positionDetail.getCheckFailList()));
						}
						if (positionDetail.getCheckPartReminderList() != null && !positionDetail.getCheckPartReminderList().isEmpty()) {
							flattenedModel.setCheckReminderList(new ArrayList<>(positionDetail.getCheckPartReminderList()));
						}
					}
					
					// 合并物料级别的错误信息
					if (model.getCheckFailList() != null && !model.getCheckFailList().isEmpty()) {
						if (flattenedModel.getCheckFailList() == null) {
							flattenedModel.setCheckFailList(new ArrayList<>());
						}
						flattenedModel.getCheckFailList().addAll(model.getCheckFailList());
					}
					if (model.getCheckReminderList() != null && !model.getCheckReminderList().isEmpty()) {
						if (flattenedModel.getCheckReminderList() == null) {
							flattenedModel.setCheckReminderList(new ArrayList<>());
						}
						flattenedModel.getCheckReminderList().addAll(model.getCheckReminderList());
					}
					
					// 设置warehousePositionList为单元素列表（保持结构一致性）
					List<WarehousePositionDetailModel> singlePositionList = new ArrayList<>();
					if (positionDetail != null) {
						singlePositionList.add(positionDetail);
					}
					flattenedModel.setWarehousePositionList(singlePositionList);
					
					flattenedList.add(flattenedModel);
				}
			}
			
			log.info("数据扁平化完成，原始记录数: {}, 扁平化后记录数: {}", cleanedList.size(), flattenedList.size());
			
			// 构建新的 ParseExcelResult（扁平化后的数据，确保6条数据返回6条记录）
			return new ParseExcelResult<>(flattenedList, parseExcelResult.getHeadList());
		} catch (Exception e) {
			log.error("Excel导入处理异常: {}", e.getMessage(), e);
			throw new ExceptionPack(e, ExceptionMsg.builder("Excel文件解析失败: " + e.getMessage())
					.msgViewAndResCode("模版导入出错啦~不要着急,请联系技术排查哦!", ServiceErrorCode.BATCH_IMPORT_FILE_ILLEGAL.getCode()).build());
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void batchSaveWarehouseMergeExcel(List<BatchAddWarehouseWorkRequest> requestData) throws ExceptionPack {
		// 企业编号
		Long enterpriseCode = getEnterpriseCode();
		// 获取用户CODE
		Long operator = getUserCode();
		// 新-库存表名
		String stockTableName =
				tableFactory.getTableName(tableFactory.module.getWarehouse(), tableFactory.table.getStock());
		// 新-仓位库存表名
		String positionStockTableName =
				tableFactory.getTableName(tableFactory.module.getWarehouse(), tableFactory.table.getPositionStock());
		// 新仓位
		String positionTableName =
				tableFactory.getTableName(tableFactory.module.getWarehouse(), tableFactory.table.getPosition());
		// 仓库模块-仓位库存批次表
		String positionStockBatchTableName = tableFactory.getTableName(tableFactory.module.getWarehouse(),
				tableFactory.table.getPositionStockBatch());
		// 新-仓位操作记录表
		String positionOperationTableName = tableFactory.getTableName(tableFactory.module.getWarehouse(),
				tableFactory.table.getPositionOperation());
		try {
			// 获取库存数据
			for (BatchAddWarehouseWorkRequest request : requestData) {
				List<WarehousePositionDetailModel> positionList = request.getWarehousePositionList();
				Set<Long> positionCodeSet = new HashSet<>();
				if (positionList != null) {
					for (WarehousePositionDetailModel model : positionList) {
						if (model != null && model.getPositionCode() != null) {
							positionCodeSet.add(model.getPositionCode());
						}
					}
				}
				Long[] positionCodeArray = positionCodeSet.toArray(new Long[0]);
				request.setPositionCodes(positionCodeArray);
			}
			// 数据方向处理
			requestData.forEach(request -> {
				if (request.getWarehousePositionList() != null && !request.getWarehousePositionList().isEmpty()) {
					request.getWarehousePositionList().forEach(detail ->
							request.setProductPartCode(detail.getProductPartCode())
					);
				}
			});
			// 库存code数据准备
			List<Long> productPartCodeList = requestData.stream().map(BatchAddWarehouseWorkRequest::getProductPartCode).toList();
			List<StockModel> stockModelList = stockMapper.getStockByProductPartCodeInNew(productPartCodeList, enterpriseCode, stockTableName);
			Map<Long, StockModel> stockModelMap = stockModelList.stream()
					.collect(Collectors.toMap(StockModel::getProductPartCode, Function.identity()));
			// 查询盘点周期数据
			Map<String, Long> stocktakingCycleMap = warehouseUnifiedDataService.getStocktakingCycleMap();
			for (BatchAddWarehouseWorkRequest requestModel : requestData) {
				List<PositionStockBatchModel> batchAddList = new ArrayList<>();
				List<PositionStockBatchModel> batchUpdateList = new ArrayList<>();
				if (requestModel.getProductPartCode() == null) {
					continue;
				}
				StockModel stockModel = stockModelMap.get(requestModel.getProductPartCode());
				// 仓库仓位数据准备
				List<Long> positionCodeList =
						requestModel.getWarehousePositionList().stream()
								.map(WarehousePositionDetailModel::getPositionCode).toList();
				Map<String, PositionStockModel> positionStockModels = positionStockMapper.getProductByPositionList(
						positionCodeList,
						productPartCodeList,
						enterpriseCode,
						positionStockTableName
				).stream().collect(Collectors.toMap(model -> StrUtil.format("{}-{}", model.getPositionCode(), model.getMaterial()),
						Function.identity(),
						(existing, replacement) -> existing));
				List<PositionStockModel> positionStockModelAddList = new ArrayList<>();
				Map<Long, Set<Long>> addedPositionMaterials = new HashMap<>();
				Map<Long, BigDecimal> positionCodeToBatchNumSum = new HashMap<>();
				// 操作记录-入库操作
				List<PositionOperationModel> storePositionOperationModelList = new ArrayList<>();
				// 操作记录-出库操作
				List<PositionOperationModel> outPositionOperationModelList = new ArrayList<>();
				for (WarehousePositionDetailModel positionDetailModel : requestModel.getWarehousePositionList()) {
					Long positionCode = positionDetailModel.getPositionCode();
					if (positionCode != null) {
						BigDecimal batchNum = positionDetailModel.getPositionBatchNum();
						if (batchNum == null) {
							batchNum = BigDecimal.ZERO;
						}
						positionCodeToBatchNumSum.merge(positionCode, batchNum, BigDecimal::add);
					}
				}

				// 执行后续业务
				for (WarehousePositionDetailModel positionDetailModel : requestModel.getWarehousePositionList()) {
					String positionAndProductCode = StrUtil.format("{}-{}",
							positionDetailModel.getPositionCode(), positionDetailModel.getProductPartCode());
					// 仓库仓位信息不存在，新增仓位信息
					PositionStockModel positionStockModel = positionStockModels.get(positionAndProductCode);
					Long positionStockCode = PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId();
					Long productPartCode = positionDetailModel.getProductPartCode();
					Long positionCode = positionDetailModel.getPositionCode();
					BigDecimal positionSum = positionCodeToBatchNumSum.getOrDefault(positionCode, BigDecimal.ZERO);
					BigDecimal positionBatchNum = ObjectUtil.defaultIfNull(positionDetailModel.getPositionBatchNum(), BigDecimal.ZERO);
					String positionBatch = ObjectUtil.defaultIfBlank(positionDetailModel.getPositionBatch(), "系统导入");
					positionDetailModel.setPositionBatchNum(positionBatchNum);
					positionDetailModel.setPositionBatch(positionBatch);
					if (positionStockModel == null) {
						warehouseUnifiedDataService.handlePositionStockNotExists(
								positionDetailModel,
								stockModel,
								operator,
								enterpriseCode,
								productPartCode,
								positionSum,
								positionStockCode,
								addedPositionMaterials,
								positionStockModelAddList,
								batchAddList,
								storePositionOperationModelList
						);
					} else {
						warehouseUnifiedDataService.handleBatchStockIfNeeded(
								stockModel,
								positionStockModel,
								operator,
								enterpriseCode,
								productPartCode,
								positionStockBatchTableName,
								positionDetailModel,
								batchAddList,
								batchUpdateList,
								storePositionOperationModelList
						);
					}
				}
				// 更新库存批次数据-并且根据 条件 去重复
				if (ObjectUtil.isNotEmpty(batchUpdateList)) {
					List<PositionStockBatchModel> uniqueBatchUpdateList = batchUpdateList.stream()
							.collect(Collectors.collectingAndThen(
									Collectors.toMap(model ->
													new AbstractMap.SimpleEntry<>
															(model.getBatch(), model.getPositionStockBatchNum()),
											Function.identity(),
											(existing, replacement) -> existing
									),
									map -> new ArrayList<>(map.values())
							));

					if (!uniqueBatchUpdateList.isEmpty()) {
						try (AutoCloseable ignored = RequestTableHelper::clearBatchTableName) {
							RequestTableHelper.setBatchTableName(positionStockBatchTableName);
							positionStockBatchService.updateBatchById(uniqueBatchUpdateList);
						} catch (Exception e) {
							throw new RuntimeException(e);
						}
					}
					// 后置处理：更新仓库仓位数据
					for (WarehousePositionDetailModel positionDetailModel : requestModel.getWarehousePositionList()) {
						Long positionCode = positionDetailModel.getPositionCode();
						BigDecimal positionSum = positionCodeToBatchNumSum.getOrDefault(positionCode, BigDecimal.ZERO);
						positionStockMapper.updateById(positionDetailModel.getPositionCode(), positionDetailModel.getProductPartCode(), positionSum,
								positionStockTableName);
					}
				}

				// 出库操作记录
				if (ObjectUtil.isNotEmpty(outPositionOperationModelList)) {
					log.info("开始执行 出库操作记录 数据如下：{}", JSONUtil.toJsonStr(outPositionOperationModelList));
					positionOperationMapper.insertBatch(outPositionOperationModelList, positionOperationTableName);
				}

				// 入库操作记录
				if (ObjectUtil.isNotEmpty(storePositionOperationModelList)) {
					log.info("开始执行 入库操作记录 数据如下：{}", JSONUtil.toJsonStr(storePositionOperationModelList));
					positionOperationMapper.insertBatch(storePositionOperationModelList, positionOperationTableName);
				}

				if (ObjectUtil.isNotEmpty(positionStockModelAddList)) {
					// 新增仓库仓位数据
					positionStockMapper.insertBatch(positionStockModelAddList, positionStockTableName);
					List<PositionOperationModel> storeAddPositionOperationModelList = new ArrayList<>();
					for (PositionOperationModel positionOperationModel : storePositionOperationModelList) {
						long count =
								positionOperationMapper.countByPositionOperation
										(positionOperationModel.getPositionOperationCode(), enterpriseCode, positionOperationTableName);
						if (count == 0) {
							storeAddPositionOperationModelList.add(positionOperationModel);
						}
					}
					// 入库操作记录
					if (ObjectUtil.isNotEmpty(storeAddPositionOperationModelList)) {
						log.info("开始执行 新增场景，入库操作记录 数据如下：{}", JSONUtil.toJsonStr(storeAddPositionOperationModelList));
						positionOperationMapper.insertBatch(storeAddPositionOperationModelList, positionOperationTableName);
					}
				}
				// 新增批次数据
				if (ObjectUtil.isNotEmpty(batchAddList)) {
					try (AutoCloseable ignored = RequestTableHelper::clearBatchTableName) {
						RequestTableHelper.setBatchTableName(positionStockBatchTableName);
						positionStockBatchService.saveBatch(batchAddList);
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}
				if (stockModel != null) {
					stockModel.setRemark(requestModel.getRemark());
					stockModel.setSafeInventory(requestModel.getSafeInventory());
					stockModel.setCountCycle(stocktakingCycleMap.get(requestModel.getCountCycleChn()));
					stockModel.setStockLimit(requestModel.getStockLimit());
					stockMapper.updateById(stockModel);
				}
			}
			for (StockModel model : stockModelList) {
				//List<PositionStockModel> stocksByProductPartCode = positionStockMapper
				//		.getStocksByProductPartCode(model.getProductPartCode(), enterpriseCode, positionStockTableName);
				LambdaQueryWrapper<PositionStockBatchModel> qw = Wrappers.lambdaQuery();
				qw.eq(PositionStockBatchModel::getProductOrPart, model.getProductPartCode())
						.eq(PositionStockBatchModel::getEnterpriseCode, enterpriseCode)
						.eq(PositionStockBatchModel::getDeleteFlag, false);
				RequestTableHelper.setTableName(positionStockBatchTableName);
				List<PositionStockBatchModel> models = positionStockBatchService.list(qw);
				BigDecimal positionNum = models.stream()
						.map(PositionStockBatchModel::getPositionStockBatchNum)
						.filter(Objects::nonNull)
						.reduce(BigDecimal.ZERO, BigDecimal::add);
				List<Long> positionCodes = models.stream()
						.map(PositionStockBatchModel::getPositionCode)
						.distinct()
						.toList();
				LambdaUpdateWrapper<StockModel> stockUw = Wrappers.lambdaUpdate();
				stockUw.set(StockModel::getTotalInventory, positionNum)
						.set(StockModel::getShippingSpace, positionCodes.toArray(new Long[0]))
						.set(StockModel::getStockInitState, Boolean.TRUE)
						.eq(StockModel::getStockCode, model.getStockCode())
						.eq(StockModel::getDeleteFlag, Boolean.FALSE);
				RequestTableHelper.setTableName(stockTableName);
				stockMapper.update(stockUw);
			}
		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("fail to changeStockBingPositionCode").build());
		}
	}

}
