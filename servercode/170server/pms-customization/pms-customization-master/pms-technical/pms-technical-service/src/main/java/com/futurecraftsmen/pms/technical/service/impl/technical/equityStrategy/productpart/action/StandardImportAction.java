/*
 * Copyright (c) Zhongzao Software Co. LTD 2022-2062 All rights reserved
 */
package com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart.action;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.StopWatch;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import java.math.BigDecimal;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.futurecraftsmen.pms.api.domain.ServiceErrorCode;
import com.futurecraftsmen.pms.api.dto.MultipartFileRpcDTO;
import com.futurecraftsmen.pms.common.domain.excel.ParseExcelResult;
import com.futurecraftsmen.pms.common.domain.exception.ExcelException;
import com.futurecraftsmen.pms.common.excel.multi.ExcelMultiModelsProcessor;
import com.futurecraftsmen.pms.file.api.service.EnterpriseStorageSpaceFileService;
import com.futurecraftsmen.pms.file.api.service.dto.StorageObjectRpcDTO;
import com.futurecraftsmen.pms.file.api.service.dto.StorageObjectRpcRequest;
import com.futurecraftsmen.pms.service.configuration.RequestTableHelper;
import com.futurecraftsmen.pms.service.domain.BaseExcelHeaderGenerator;
import com.futurecraftsmen.pms.service.domain.common.constant.CommonConstant;
import com.futurecraftsmen.pms.service.domain.extract.ExtractUtil;
import com.futurecraftsmen.pms.starter.domain.starter.PmsStarter;
import com.futurecraftsmen.pms.technical.api.domain.technical.items.ProductPartQualityItemsRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.items.ProductPartQualityItemsRpcRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.ProductPartExtraInfo;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.excel.standard.*;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.relationship.RelationshipRpcRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.standard.BatchAddMaterialDetailWorkRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.standard.BatchAddPartMaterialWorkRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.standard.BatchAddProductWorkRequest;
import com.futurecraftsmen.pms.technical.api.service.IPiecesService;
import com.futurecraftsmen.pms.technical.api.service.technical.IProductPartQualityItemsService;
import com.futurecraftsmen.pms.technical.service.common.enums.TechnicalErrorEnum;
import com.futurecraftsmen.pms.technical.service.dao.technical.IProductPartMapper;
import com.futurecraftsmen.pms.technical.service.domain.StateEnum;
import com.futurecraftsmen.pms.technical.service.domain.technical.procedure.ProcedurePartRelationshipModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.procedure.ProcedureRouteRelationshipModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.procedure.ProductPartProcedureModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.process.ProcessRouteDataModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.productpart.ProductPartModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.productpart.ProductPartRouteRelationshipModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.productpart.standard.*;
import com.futurecraftsmen.pms.technical.service.domain.technical.type.ProductPartTypeModel;
import com.futurecraftsmen.pms.technical.service.impl.inner.TableNameFactory;
import com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart.abstracts.AbstractImport;
import com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart.action.unified.IProductPartCommonServiceImpl;
import com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart.action.unified.TechnicalUnifiedDataService;
import com.futurecraftsmen.pms.technical.service.impl.technical.procedure.ProductPartProcedureServiceImpl;
import com.futurecraftsmen.pms.technical.service.impl.technical.relationship.ProcedurePartRelationshipServiceImpl;
import com.futurecraftsmen.pms.technical.service.impl.technical.relationship.ProcedureRouteRelationshipServiceImpl;
import com.futurecraftsmen.pms.technical.service.impl.technical.relationship.ProductPartRouteRelationshipServiceImpl;
import com.futurecraftsmen.pms.technical.service.impl.technical.route.ProcessRouteDataServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.aerie.forest.core.brick.domain.view.CodeMapName;
import org.aerie.forest.core.brick.exception.ExceptionMsg;
import org.aerie.forest.core.brick.exception.ExceptionPack;
import org.aerie.forest.core.brick.exception.assertprocess.AssertException;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.futurecraftsmen.pms.technical.service.util.CommonUtil.getEnterpriseCode;
import static com.futurecraftsmen.pms.technical.service.util.CommonUtil.getUserCode;

/**
 * @description 产品、部件、零件、原料 导入校验策略实现
 *
 * @author qierkang
 * @organization futurecraftsmen
 * @date Created in 2025/4/27 23:12
 * @department: Product development
 */
@Slf4j
@Component
public class StandardImportAction extends AbstractImport {

	@Resource
	private TableNameFactory tableFactory;
	@Resource
	private ProductWorkYValidator productWorkYValidator;
	@Resource
	private ProductWorkNValidator productWorkNValidator;
	@Resource
	private ComponentWorkYValidator componentWorkYValidator;
	@Resource
	private ComponentWorkNValidator componentWorkNValidator;
	@Resource
	private PartValidator partValidator;
	@Resource
	private MaterialValidator materialValidator;
	@Resource
	private TechnicalUnifiedDataService unifiedDataService;
	@Resource
	private IPiecesService piecesService;
	@DubboReference(group = "pms", check = false, retries = 0)
	private EnterpriseStorageSpaceFileService storageSpaceFileService;
	@Resource
	private IProductPartQualityItemsService qualityItemsService;
	@Resource
	private IProductPartCommonServiceImpl productPartCommonService;
	@Resource
	private IProductPartMapper productPartMapper;
	@Resource
	private ProductPartRouteRelationshipServiceImpl productPartRouteRelationshipService;
	@Resource
	private ProductPartProcedureServiceImpl productPartProcedureService;
	@Resource
	private ProcedurePartRelationshipServiceImpl procedurePartRelationshipService;
	@Resource
	private ProcedureRouteRelationshipServiceImpl routeRelationshipService;
	@Resource
	private DetailWorkYValidator detailWorkYValidator;
	@Resource
	private ProcessRouteDataServiceImpl processRouteDataService;

	@Override
	public ParseExcelResult<?> productAnalyzeExcelWorkY(MultipartFileRpcDTO excelFile) throws ExceptionPack {
		try {
			return ExcelMultiModelsProcessor.INSTANCE.parseExcelModels(new ByteArrayInputStream(excelFile.getBytes()),
					ProductWorkYStandardModel.class, productWorkYValidator, null, new BaseExcelHeaderGenerator());
		} catch (ExcelException e) {
			throw new ExceptionPack(e, ExceptionMsg.builder(e.getMessage())
					.msgViewAndResCode(e.getMsgView(), ServiceErrorCode.BATCH_IMPORT_FILE_ILLEGAL.getCode()).build());
		}
	}

	@Override
	public ParseExcelResult<?> productAnalyzeExcelWorkN(MultipartFileRpcDTO excelFile) throws ExceptionPack {
		try {
			return ExcelMultiModelsProcessor.INSTANCE.parseExcelModels(new ByteArrayInputStream(excelFile.getBytes()),
					ProductWorkNStandardModel.class, productWorkNValidator, null, new BaseExcelHeaderGenerator());
		} catch (ExcelException e) {
			throw new ExceptionPack(e, ExceptionMsg.builder(e.getMessage())
					.msgViewAndResCode(e.getMsgView(), ServiceErrorCode.BATCH_IMPORT_FILE_ILLEGAL.getCode()).build());
		}
	}

	@Override
	public ParseExcelResult<?> componentAnalyzeExcelWorkY(MultipartFileRpcDTO excelFile) throws ExceptionPack {
		try {
			return ExcelMultiModelsProcessor.INSTANCE.parseExcelModels(new ByteArrayInputStream(excelFile.getBytes()),
					ComponentWorkYStandardModel.class, componentWorkYValidator, null, new BaseExcelHeaderGenerator());
		} catch (ExcelException e) {
			throw new ExceptionPack(e, ExceptionMsg.builder(e.getMessage())
					.msgViewAndResCode(e.getMsgView(), ServiceErrorCode.BATCH_IMPORT_FILE_ILLEGAL.getCode()).build());
		}
	}

	@Override
	public ParseExcelResult<?> componentAnalyzeExcelWorkN(MultipartFileRpcDTO excelFile) throws ExceptionPack {
		try {
			return ExcelMultiModelsProcessor.INSTANCE.parseExcelModels(new ByteArrayInputStream(excelFile.getBytes()),
					ComponentWorkNStandardModel.class, componentWorkNValidator, null, new BaseExcelHeaderGenerator());
		} catch (ExcelException e) {
			throw new ExceptionPack(e, ExceptionMsg.builder(e.getMessage())
					.msgViewAndResCode(e.getMsgView(), ServiceErrorCode.BATCH_IMPORT_FILE_ILLEGAL.getCode()).build());
		}
	}

	@Override
	public ParseExcelResult<?> partAnalyzeExcelWorkN(MultipartFileRpcDTO excelFile) throws ExceptionPack {
		try {
			return ExcelMultiModelsProcessor.INSTANCE.parseExcelModels(new ByteArrayInputStream(excelFile.getBytes()),
					PartStandardModel.class, partValidator, null, new BaseExcelHeaderGenerator());
		} catch (ExcelException e) {
			throw new ExceptionPack(e, ExceptionMsg.builder(e.getMessage())
					.msgViewAndResCode(e.getMsgView(), ServiceErrorCode.BATCH_IMPORT_FILE_ILLEGAL.getCode()).build());
		}
	}

	@Override
	public ParseExcelResult<?> materialAnalyzeExcelWorkN(MultipartFileRpcDTO excelFile) throws ExceptionPack {
		try {
			return ExcelMultiModelsProcessor.INSTANCE.parseExcelModels(new ByteArrayInputStream(excelFile.getBytes()),
					MaterialStandardModel.class, materialValidator, null, new BaseExcelHeaderGenerator());
		} catch (ExcelException e) {
			throw new ExceptionPack(e, ExceptionMsg.builder(e.getMessage())
					.msgViewAndResCode(e.getMsgView(), ServiceErrorCode.BATCH_IMPORT_FILE_ILLEGAL.getCode()).build());
		}
	}

	@Override
	public void batchAddPartMaterialWork(List<?> requestData) throws ExceptionPack {
		List<BatchAddPartMaterialWorkRequest> requestListData = Convert.toList(BatchAddPartMaterialWorkRequest.class, requestData);
		// 新技术部-产品零件表
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
		// 新技术部-产品零件分类表
		String productPartTypeTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPartType());
		try {
			//starthere
			// 对requestListData中的每个请求对象的inspectionItemNames进行规范化去重处理
			for (BatchAddPartMaterialWorkRequest request : requestListData) {
				if (request.getInspectionItemNames() != null && !request.getInspectionItemNames().isEmpty()) {
					// 使用LinkedHashSet保持原有顺序并去重
					// 同时对每个检验项名称进行规范化处理，去除空白字符
					LinkedHashSet<String> normalizedItemNames = new LinkedHashSet<>();
					for (String itemName : request.getInspectionItemNames()) {
						if (StringUtils.isNotBlank(itemName)) {
							// 使用cleanBlank去除所有空白字符，包括首尾和中间多余的空格
							String normalizedName = StrUtil.cleanBlank(itemName);
							if (StringUtils.isNotBlank(normalizedName)) {
								normalizedItemNames.add(normalizedName);
							}
						}
					}
					request.setInspectionItemNames(new ArrayList<>(normalizedItemNames));
				}
			}


			BatchAddPartMaterialWorkRequest batchAddRpcRequest = requestListData.getFirst();
			if (null == batchAddRpcRequest.getAttribute()) {
				throw new AssertException(ExceptionMsg.builder("")
						.msgView(TechnicalErrorEnum.TECHNICAL_ATTRIBUTE_NOT_EXIST_MESSAGE.getMsg()).build());
			}
			StopWatch stopWatch = new StopWatch("batchAddPartMaterialWork");
			stopWatch.start(StrUtil.format("开始执行批量零件原料信息-处理数据大小 {}", requestListData.size()));
			// 获取统一数据 分类数据
			List<String> typeNameList = ExtractUtil.streamMapToList(String::valueOf, requestListData,
					BatchAddPartMaterialWorkRequest::getProductPartTypeCodeName);
			Map<String, ProductPartTypeModel> typeNameToModelMap = unifiedDataService.prepareTypeData(typeNameList,
					productPartTypeTableName, batchAddRpcRequest.getAttribute());

			// 计量单位数据准备
			Set<String> productPiecesCodes = new HashSet<>(ExtractUtil.streamMapToList(String::valueOf, requestListData,
					BatchAddPartMaterialWorkRequest::getPcsName));
			Map<String, Long> pieceCodeNameMapping =
					piecesService.pieceCodeNameMappingByName(productPiecesCodes, getEnterpriseCode());
			// 员工信息准备
			// 人员数据准备 技术对接人
			List<String> staffNameList = requestListData.stream().filter(Objects::nonNull) // 过滤掉 null 元素
					.flatMap(procedureModel -> Stream.of(procedureModel.getContactPersonName()))
					.filter(Objects::nonNull).distinct().collect(Collectors.toList());

			Map<String, List<CodeMapName>> staffNameMap = unifiedDataService.prepareStaffModelData(staffNameList);
			// 获取统一数据 图纸号数据
			List<String> numberData = requestListData.stream()
					.map(BatchAddPartMaterialWorkRequest::getDrawingNumber)
					.filter(Objects::nonNull)
					.toList();
			StorageObjectRpcRequest objectRpcRequest = new StorageObjectRpcRequest();
			objectRpcRequest.setFileNumberList(numberData);
			Map<String, StorageObjectRpcDTO> fileNumberToMap = storageSpaceFileService.getFileNumberToMap(objectRpcRequest);
			stopWatch.stop();
			stopWatch.start(StrUtil.format("开始执行批量零件原料信息-初始化查询数据完成,进入业务循环，处理数据大小 {}", requestListData.size()));
			for (BatchAddPartMaterialWorkRequest addRpcRequest : requestListData) {
				// 生成产品、零件编号
				Long productPartCode = PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId();
				// 组装检验项名称数据
				ProductPartQualityItemsRpcRequest itemsRpcRequest =
						new ProductPartQualityItemsRpcRequest().setProductPartQualityItemsSignList(addRpcRequest.getInspectionItemNames());
				Map<String, ProductPartQualityItemsRpcDTO> qualityItemsToMap = qualityItemsService.getInspectionItemToMap(itemsRpcRequest);
				// 组装分类信息
				addRpcRequest.setProductPartTypeCode(typeNameToModelMap
						.getOrDefault(addRpcRequest.getProductPartTypeCodeName(), null).getProductPartTypeCode());
				// 组装计量单位
				addRpcRequest.setPcs(pieceCodeNameMapping.getOrDefault(addRpcRequest.getPcsName(), null));
				// 组装技术对接人
				Long contactPerson =
						staffNameMap.getOrDefault(addRpcRequest.getContactPersonName(), Collections.emptyList()).stream()
								.findFirst().map(CodeMapName::getCode).orElse(null);
				addRpcRequest.setContactPerson(contactPerson);
				ProductPartModel productPartModel = Convert.convert(ProductPartModel.class, addRpcRequest)
						.setProductPartSign(addRpcRequest.getName()).setProductPartCode(productPartCode)
						.setEnterpriseCode(getEnterpriseCode()).setCreator(getUserCode()).setEnterTime(DateUtil.date());
				// 组装状态 状态启用禁用准备 默认启用
				StateEnum stateEnum = StateEnum.parse(addRpcRequest.getStateName());
				productPartModel.setState(stateEnum == null ? Boolean.TRUE : stateEnum.isValue());
				StorageObjectRpcDTO storageObjectRpcDTO = fileNumberToMap.get(addRpcRequest.getDrawingNumber());
				// 组装图纸号
				if (storageObjectRpcDTO != null) {
					productPartModel.setFiles(new Long[]{storageObjectRpcDTO.getFileId()});
				} else {
					ProductPartExtraInfo extraInfo = new ProductPartExtraInfo();
					extraInfo.setDrawingNumberName(addRpcRequest.getDrawingNumber());
					productPartModel.setExtra(JSONUtil.toJsonStr(extraInfo));
				}
				// 组装检验项目数据
				if (ObjectUtil.isNotEmpty(addRpcRequest.getInspectionItemNames())) {
					productPartModel.setQualityCodeList(
							addRpcRequest.getInspectionItemNames().stream()
									.map(qualityItemsToMap::get)
									.filter(Objects::nonNull)
									.map(ProductPartQualityItemsRpcDTO::getProductPartQualityCode)
									.toArray(Long[]::new)
					);
				}
				// 获取表代码-统一单号生成逻辑提取
				String unityNo = addRpcRequest.getUnityNo();
				if (addRpcRequest.isGenerate()) {
					// 获取表代码-统一单号生成逻辑提取
					unityNo = productPartCommonService.getGlobalSerialUnityNo(productPartModel.getAttribute(),
							productPartModel.getProductPartTypeCode(),
							addRpcRequest.getUnityNo(), Boolean.FALSE);
				}
				productPartModel.setUnityNo(unityNo);
				// 新增零件原料信息
				RequestTableHelper.setTableName(productPartTableName);
				productPartMapper.insert(productPartModel);
				try {
					StopWatch stockWatch = new StopWatch("stockMapper.insert");
					stockWatch.start("stockMapper.insert Start");
					// 同步库存信息
					productPartCommonService.syncStock(productPartModel.getProductPartCode());
					stockWatch.stop();
					log.info("\r\n开始同步零件原料数据到仓库表-结束, 请求执行耗时：{} 请求入参 {}", stopWatch.prettyPrint(TimeUnit.MILLISECONDS),
							productPartModel.getProductPartCode());
				} catch (Exception e) {
					log.error("error 同步零件原料数据到仓库表 执行失败 ", e);
				}
			}
			stopWatch.stop();
			log.info("\r\n开始执行批量零件原料信息-请求执行耗时：{}", stopWatch.prettyPrint(TimeUnit.MILLISECONDS));
		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("batchAddPartMaterialWork failed").build());
		}
	}

	@Override
	public ParseExcelResult<?> materialDetailAnalyzeExcelWorkY(MultipartFileRpcDTO excelFile, String processRouteDataCode, Long productPartCode) throws ExceptionPack {
		try {
			processRouteDataCode = processRouteDataCode + "|" + productPartCode;
		return ExcelMultiModelsProcessor.INSTANCE.parseExcelModels(new ByteArrayInputStream(excelFile.getBytes()),
					DetailWorkYStandardModel.class, detailWorkYValidator, processRouteDataCode, new BaseExcelHeaderGenerator());
		} catch (ExcelException e) {
			throw new ExceptionPack(e, ExceptionMsg.builder(e.getMessage())
					.msgViewAndResCode(e.getMsgView(), ServiceErrorCode.BATCH_IMPORT_FILE_ILLEGAL.getCode()).build());
		}
	}

	@Override
	public void batchAddMaterialDetailWorkY(BatchAddMaterialDetailWorkRequest requestData) throws ExceptionPack {
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
		tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPartType());
		// 工艺路线管理表
		String processRouteDataTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProcessRouteData());
		// 产品零件与工艺路线关系表
		String productPartRouteRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProductPartRouteRelationship());
		try {
			// 查询产品、零件信息
			RequestTableHelper.setTableName(productPartTableName);
			ProductPartModel productPartModel = productPartMapper.selectById(requestData.getProductPartCode());
			// 查询的信息不存在，请检查
			if (null == productPartModel) {
				throw new AssertException(ExceptionMsg
						.builder("This batchDetailAddProductPart method failed to execute null == productPartModel")
						.msgView(TechnicalErrorEnum.TECHNICAL_PRODUCT_PART_EXIST_MESSAGE.getMsg()).build());
			}
			List<BatchAddMaterialDetailWorkRequest.PartBatchAddModel> requestListData =
					requestData.getPartInfoBatchAddModels();
			// 获取统一数据 工艺路线数据
			RequestTableHelper.setTableName(processRouteDataTableName);
			ProcessRouteDataModel processRouteDataModel =
					processRouteDataService.getById(requestData.getProcessRouteDataCode());
			if (null == processRouteDataModel) {
				throw new AssertException(ExceptionMsg
						.builder(
								"This batchDetailAddProductPart method failed to execute null == processRouteDataModel")
						.msgView(TechnicalErrorEnum.TECHNICAL_PROCESS_ROUTE_DATA_EXIST_MESSAGE.getMsg()).build());
			}
			List<Long> modelListData = ExtractUtil.streamMapToList(Long::valueOf, requestListData,
					BatchAddMaterialDetailWorkRequest.PartBatchAddModel::getProductPartCode);
			Map<Long, ProductPartModel> partToModelMap =
					unifiedDataService.prepareCodeData(modelListData, productPartTableName);
			// 组装最外层 产品零件与工艺路线关系数据
			LambdaQueryWrapper<ProductPartRouteRelationshipModel> qw = Wrappers.lambdaQuery();
			qw.eq(ProductPartRouteRelationshipModel::getProductPartCode, productPartModel.getProductPartCode());
			qw.eq(ProductPartRouteRelationshipModel::getProcessRouteDataCode, processRouteDataModel.getProcessRouteDataCode());
			qw.eq(ProductPartRouteRelationshipModel::getEnterpriseCode, getEnterpriseCode());
			qw.eq(ProductPartRouteRelationshipModel::getDeleteFlag, Boolean.FALSE);
			RequestTableHelper.setTableName(productPartRouteRelationshipTableName);
			ProductPartRouteRelationshipModel routeRelationship =
					productPartRouteRelationshipService.list(qw).stream().findFirst().orElse(null);
			Long uniqueId;
			if (routeRelationship == null) {
				// 生成关系唯一 ID
				uniqueId = PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId();
				ProductPartRouteRelationshipModel productPartRouteRelationshipModel =
						new ProductPartRouteRelationshipModel().setEnterpriseCode(getEnterpriseCode())
								.setProductPartCode(productPartModel.getProductPartCode()).setState(Boolean.TRUE)
								.setProcessRouteDataCode(processRouteDataModel.getProcessRouteDataCode()).setUniqueId(uniqueId);
				RequestTableHelper.setTableName(productPartRouteRelationshipTableName);
				productPartRouteRelationshipService.save(productPartRouteRelationshipModel);
			} else {
				// 获取已存在的唯一标识 ID
				uniqueId = routeRelationship.getUniqueId();
			}
			// 调用公共方法处理批次添加模型
			processBatchAddModels(requestData, partToModelMap, processRouteDataModel, uniqueId);
			// 判断是否有默认工艺路线如果没有 当前这个路线是默认的
			if (productPartModel.getDefaultRoute() == null) {
				LambdaUpdateWrapper<ProductPartModel> updateDef = Wrappers.lambdaUpdate();
				updateDef.set(ProductPartModel::getDefaultRoute, requestData.getProcessRouteDataCode());
				updateDef.eq(ProductPartModel::getProductPartCode, productPartModel.getProductPartCode());
				updateDef.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode());
				updateDef.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE);
				RequestTableHelper.setTableName(productPartTableName);
				productPartMapper.update(updateDef);
			}
		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("batchDetailAddProductPart 新增出错 failed")
					.msgView(TechnicalErrorEnum.TECHNICAL_EXCEL_IMPORT.getMsg()).build());
		}
	}

	private void processBatchAddModels(BatchAddMaterialDetailWorkRequest requestData, Map<Long, ProductPartModel> partToModelMap,
	                                   ProcessRouteDataModel processRouteDataModel, Long uniqueId) throws ExceptionPack {
		try {
			// 工序表与零件关系表
			String procedurePartRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
					tableFactory.table.getProcedurePartRelationship());
			// 产品零件工序表
			String productPartProcedure = tableFactory.getTableName(tableFactory.module.getTechnology(),
					tableFactory.table.getProductPartProcedure());
			// 工序表与工艺路线关系表
			String procedureRouteRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
					tableFactory.table.getProcedureRouteRelationship());
			List<ProcedureRouteRelationshipModel> procedureRouteRelModels = new ArrayList<>();
			// 工序与工艺路线关系查询
			LambdaQueryWrapper<ProductPartRouteRelationshipModel> qw = Wrappers.lambdaQuery();
			qw.eq(ProductPartRouteRelationshipModel::getUniqueId, uniqueId);
			qw.eq(ProductPartRouteRelationshipModel::getEnterpriseCode, getEnterpriseCode())
					.eq(ProductPartRouteRelationshipModel::getDeleteFlag, Boolean.FALSE);
			// 先清空原来绑定的工序表与零件关系表，在新增
			RelationshipRpcRequest deleteRelationship = new RelationshipRpcRequest().setUniqueId(uniqueId);
			procedurePartRelationshipService.deleteRelationship(deleteRelationship);
			for (BatchAddMaterialDetailWorkRequest.PartBatchAddModel batchAddModel : requestData.getPartInfoBatchAddModels()) {
				ProductPartModel resultModel = partToModelMap.get(batchAddModel.getProductPartCode());
				// 导入零件校验
				if (ObjectUtil.isEmpty(resultModel)) {
					throw new AssertException(
							ExceptionMsg.builder("导入零件校验 This processBatchAddModels method failed to execute ")
									.msgView(TechnicalErrorEnum.TECHNICAL_PART_MODEL_NOT_EXIST_MESSAGE.getMsg()).build());
				}
				// 组装工艺路线与工序关系数据
				RequestTableHelper.setTableName(productPartProcedure);
				ProductPartProcedureModel partProcedureModel = productPartProcedureService.getById(batchAddModel.getProductPartProcedureCode());

				Long productPartProcedureCode = partProcedureModel != null ? partProcedureModel.getProductPartProcedureCode() : null;
				ProcedureRouteRelationshipModel procedureRouteRelModel = new ProcedureRouteRelationshipModel()
						.setEnterpriseCode(getEnterpriseCode())
						.setProcedureCode(productPartProcedureCode)
						.setProcessRouteDataCode(processRouteDataModel.getProcessRouteDataCode())
						.setUniqueId(uniqueId)
						.setSequence(CommonConstant.NUMBER_ZERO)
						.setProductPartCode(resultModel.getProductPartCode());
				procedureRouteRelModels.add(procedureRouteRelModel);
				// 组装工序和零件绑定关系数据
				ProcedurePartRelationshipModel procedurePartRelationshipModel = new ProcedurePartRelationshipModel()
						.setProductPartCode(resultModel.getProductPartCode())
						.setEnterpriseCode(getEnterpriseCode())
						.setProcedureCode(productPartProcedureCode)
						.setProcessRouteDataCode(processRouteDataModel.getProcessRouteDataCode());
				String number;
				try {
					number = batchAddModel.getNumber();
					if (NumberUtil.isNumber(number) && (Integer.parseInt(number) < 0 || Double.parseDouble(number) < 0)) {
						number = String.valueOf(0);
					}
				} catch (NumberFormatException e) {
				// 处理解析错误的情况，设置默认值
				number = batchAddModel.getNumber();
			}
			BigDecimal quantity = StrUtil.isBlank(batchAddModel.getNumber()) ? BigDecimal.ZERO : new BigDecimal(number);
			procedurePartRelationshipModel.setQuantity(quantity).setUniqueId(uniqueId);
			// 新增工序表与零件关系
			RequestTableHelper.setTableName(procedurePartRelationshipTableName);
			procedurePartRelationshipService.save(procedurePartRelationshipModel);
			}
			// 先清空原来绑定的，在新增
			routeRelationshipService.deleteRelationship(deleteRelationship);
			try (AutoCloseable ignored = RequestTableHelper::clearBatchTableName) {
				RequestTableHelper.setBatchTableName(procedureRouteRelationshipTableName);
				routeRelationshipService.saveBatch(procedureRouteRelModels);
			} catch (Exception e) {
				log.error("批量操作失败: 表名={}, 数据大小={}", procedureRouteRelationshipTableName, procedureRouteRelModels.size(), e);
			}
		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("processBatchAddModels 新增出错 failed")
					.msgView(TechnicalErrorEnum.TECHNICAL_EXCEL_IMPORT.getMsg()).build());
		}
	}
}
