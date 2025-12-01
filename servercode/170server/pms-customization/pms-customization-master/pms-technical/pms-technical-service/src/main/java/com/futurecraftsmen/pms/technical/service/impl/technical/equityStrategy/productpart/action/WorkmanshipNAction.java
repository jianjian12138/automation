/*
 * Copyright (c) Zhongzao Software Co. LTD 2022-2062 All rights reserved
 */
package com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart.action;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.StopWatch;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.futurecraftsmen.pms.api.dto.base.RpcPagingDTO;
import com.futurecraftsmen.pms.file.api.service.EnterpriseStorageSpaceFileService;
import com.futurecraftsmen.pms.file.api.service.dto.StorageObjectRpcDTO;
import com.futurecraftsmen.pms.file.api.service.dto.StorageObjectRpcRequest;
import com.futurecraftsmen.pms.pas.api.rpc.receiving.ProductMaterialRpcRequest;
import com.futurecraftsmen.pms.pas.api.rpc.receiving.ProductProductionRpcDTO;
import com.futurecraftsmen.pms.pas.api.rpc.receiving.ProductTransitRpcDTO;
import com.futurecraftsmen.pms.pas.api.rpc.sellorder.ProduceDetailConditionDTO;
import com.futurecraftsmen.pms.pas.api.service.produce.ProducePageDetailService;
import com.futurecraftsmen.pms.pas.api.service.receiving.PurchaseSelfReceivingService;
import com.futurecraftsmen.pms.service.configuration.MyBatisDynamicTableNameFactory;
import com.futurecraftsmen.pms.service.configuration.RequestTableHelper;
import com.futurecraftsmen.pms.service.domain.common.constant.CommonConstant;
import com.futurecraftsmen.pms.service.domain.extract.ExtractUtil;
import com.futurecraftsmen.pms.starter.domain.starter.PmsStarter;
import com.futurecraftsmen.pms.technical.api.domain.technical.items.ProductPartQualityItemsRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.ProductMaterialRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.ProductPartExtraInfo;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.ProductPartPageRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.ProductPartRpcRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.standard.BatchAddComponentWorkRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.standard.BatchAddProductWorkRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.xihang.ProductPartConsistDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.xihang.ProductPartConsistTreeDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.xihang.comp.ProductPartChild;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.xihang.comp.ProductPartCompRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.xihang.comp.ProductPartConsistRequest;
import com.futurecraftsmen.pms.technical.api.service.IPiecesService;
import com.futurecraftsmen.pms.technical.api.service.technical.IProductPartQualityItemsService;
import com.futurecraftsmen.pms.technical.service.common.enums.TechnicalErrorEnum;
import com.futurecraftsmen.pms.technical.service.dao.technical.IProductPartMapper;
import com.futurecraftsmen.pms.technical.service.dao.warehouse.IStockMapper;
import com.futurecraftsmen.pms.technical.service.domain.StandardEnum;
import com.futurecraftsmen.pms.technical.service.domain.StateEnum;
import com.futurecraftsmen.pms.technical.service.domain.technical.productpart.ProductPartModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.type.ProductPartTypeModel;
import com.futurecraftsmen.pms.technical.service.domain.warehouse.StockModel;
import com.futurecraftsmen.pms.technical.service.impl.inner.TableNameFactory;
import com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart.action.unified.IProductPartCommonServiceImpl;
import com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart.action.unified.TechnicalUnifiedDataService;
import com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart.strategy.WorkmanshipYNStrategy;
import com.futurecraftsmen.pms.technical.service.impl.technical.procedure.ProductPartProcedureServiceImpl;
import com.futurecraftsmen.pms.technical.service.impl.technical.relationship.ProcedurePartRelationshipServiceImpl;
import com.futurecraftsmen.pms.technical.service.impl.technical.relationship.ProcedureRouteRelationshipServiceImpl;
import com.futurecraftsmen.pms.technical.service.impl.technical.relationship.ProductPartRouteRelationshipServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.aerie.forest.core.brick.domain.view.CodeMapName;
import org.aerie.forest.core.brick.exception.ExceptionMsg;
import org.aerie.forest.core.brick.exception.ExceptionPack;
import org.aerie.forest.core.brick.exception.assertprocess.AssertException;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.futurecraftsmen.pms.technical.service.util.CommonUtil.getEnterpriseCode;
import static com.futurecraftsmen.pms.technical.service.util.CommonUtil.getUserCode;

/**
 * @description 标准-产品、部件批量新增 策略实现
 *
 * @author qierkang
 * @organization futurecraftsmen
 * @date Created in 2025/4/24 23:06
 * @department: Product development
 */
@Slf4j
@Component
@SuppressWarnings("all")
public class WorkmanshipNAction implements WorkmanshipYNStrategy {

	@DubboReference(group = "pms", check = false, retries = 0)
	protected PurchaseSelfReceivingService selfReceivingService;
	@DubboReference(group = "pms", check = false, retries = 0)
	protected ProducePageDetailService producePageDetailService;
	@Resource
	private TableNameFactory tableFactory;
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
	private IStockMapper stockMapper;
	@Resource
	private MyBatisDynamicTableNameFactory batisDynamicTableNameFactory;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void batchAddProductWork(List<BatchAddProductWorkRequest> requestListData) throws ExceptionPack {
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
		// 新技术部-产品零件分类表
		String productPartTypeTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPartType());
		try {
			BatchAddProductWorkRequest batchAddRpcRequest = requestListData.getFirst();
			if (null == batchAddRpcRequest.getAttribute()) {
				throw new AssertException(ExceptionMsg.builder("")
						.msgView(TechnicalErrorEnum.TECHNICAL_ATTRIBUTE_NOT_EXIST_MESSAGE.getMsg()).build());
			}
			StopWatch stopWatch = new StopWatch("batchAddProductPart");
			stopWatch.start(StrUtil.format("开始执行批量产品零件信息-处理数据大小 {}", requestListData.size()));
			// 获取统一数据 分类数据
			List<String> typeNameList = ExtractUtil.streamMapToList(String::valueOf, requestListData,
					BatchAddProductWorkRequest::getProductPartTypeCodeName);
			Map<String, ProductPartTypeModel> typeNameToModelMap = unifiedDataService.prepareTypeData(typeNameList,
					productPartTypeTableName, batchAddRpcRequest.getAttribute());

			// 计量单位数据准备
			Set<String> productPiecesCodes = new HashSet<>(ExtractUtil.streamMapToList(String::valueOf, requestListData,
					BatchAddProductWorkRequest::getPcsName));
			Map<String, Long> pieceCodeNameMapping =
					piecesService.pieceCodeNameMappingByName(productPiecesCodes, getEnterpriseCode());
			// 员工信息准备
			// 人员数据准备 技术对接人
			List<String> staffNameList = requestListData.stream().filter(Objects::nonNull) // 过滤掉 null 元素
					.flatMap(procedureModel -> Stream.of(procedureModel.getContactPersonName()))
					.filter(Objects::nonNull).distinct().collect(Collectors.toList());

					Map<String, List<CodeMapName>> staffNameMap = unifiedDataService.prepareStaffModelData(staffNameList);
		stopWatch.stop();
		stopWatch.start(StrUtil.format("开始执行批量产品零件信息-初始化查询数据完成,进入业务循环，处理数据大小 {}", requestListData.size()));

		// 批量预处理检验项目数据
		Set<String> itemNames = requestListData.stream().map(BatchAddProductWorkRequest::getInspectionItemNames).flatMap(Collection::stream).collect(Collectors.toSet());
		Map<String, ProductPartQualityItemsRpcDTO> qualityItemsToMap = qualityItemsService.getMapByNames(itemNames);

		// 批量预处理物料统一编号数据
		List<String> unityNoListData = requestListData.stream().map(BatchAddProductWorkRequest::getMaterialModels).flatMap(Collection::stream).filter(Objects::nonNull)
				.map(BatchAddProductWorkRequest.MaterialModels::getMaterialUnityNo)
				.toList();
		Map<String, ProductPartModel> partUnityNoToModelMap = unifiedDataService.prepareUnityNoData(unityNoListData, productPartTableName);

		// 收集批量处理的数据
		List<ProductPartConsistRequest> consistRequests = new ArrayList<>();
		List<ProductPartModel> productPartModels = new ArrayList<>();
		for (BatchAddProductWorkRequest addRpcRequest : requestListData) {
			// 生成产品、零件编号
			Long productPartCode = PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId();
			// 组装检验项名称数据 - 使用预处理的数据
//			ProductPartQualityItemsRpcRequest itemsRpcRequest =
//					new ProductPartQualityItemsRpcRequest().setProductPartQualityItemsSignList(addRpcRequest.getInspectionItemNames());
//			Map<String, ProductPartQualityItemsRpcDTO> qualityItemsToMap = qualityItemsService.getInspectionItemToMap(itemsRpcRequest);
			
			// 组装分类信息
			addRpcRequest.setProductPartTypeCode(typeNameToModelMap
					.getOrDefault(addRpcRequest.getProductPartTypeCodeName(), null).getProductPartTypeCode());
			// 组装计量单位
			addRpcRequest.setPcs(pieceCodeNameMapping.getOrDefault(addRpcRequest.getPcsName(), null));
			// 组装标准非标准
			StandardEnum standardEnum = StandardEnum.parse(addRpcRequest.getStandardName());
			addRpcRequest.setStandard(null != standardEnum ? standardEnum.isValue() : StandardEnum.STANDARD.isValue());
//			List<String> unityNoListData = addRpcRequest.getMaterialModels().stream().filter(Objects::nonNull)
//					.map(BatchAddProductWorkRequest.MaterialModels::getMaterialUnityNo)
//					.toList();
//			Map<String, ProductPartModel> partUnityNoToModelMap = unifiedDataService.materialUnityNoData(unityNoListData, productPartTableName);

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
				RequestTableHelper.setTableName(productPartTableName);
				List<BatchAddProductWorkRequest.MaterialModels> batchAddModels =
						addRpcRequest.getMaterialModels();
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

			String unityNo = addRpcRequest.getUnityNo();
			if (addRpcRequest.isGenerate()) {
				// 获取表代码-统一单号生成逻辑提取
				unityNo = productPartCommonService.getGlobalSerialUnityNo(productPartModel.getAttribute(),
						productPartModel.getProductPartTypeCode(),
						productPartModel.getUnityNo(), Boolean.FALSE);
				productPartModel.setUnityNo(unityNo);
			}
				// 检查生成的unityNo是否已存在
				LambdaQueryWrapper<ProductPartModel> checkQuery = Wrappers.lambdaQuery();
				checkQuery.eq(ProductPartModel::getUnityNo, unityNo)
						.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode())
						.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE);
				RequestTableHelper.setTableName(productPartTableName);
				if (productPartMapper.selectCount(checkQuery) > 0) {
					throw new AssertException(ExceptionMsg.builder("")
							.msgView("编码已存在，请刷新后再试").build());
				}
							// 新增产品零件信息
			RequestTableHelper.setTableName(productPartTableName);
			productPartMapper.insert(productPartModel);
			
			if (!batchAddModels.isEmpty()) {
				ProductPartConsistRequest consistRequest = new ProductPartConsistRequest();
				consistRequest.setParentAttribute(addRpcRequest.getAttribute());
				consistRequest.setParentCode(productPartModel.getProductPartCode());
				List<ProductPartChild> productPartChildren = new ArrayList<>();
				for (BatchAddProductWorkRequest.MaterialModels batchAddModel : batchAddModels) {
					ProductPartModel resultModel = partUnityNoToModelMap.get(batchAddModel.getMaterialUnityNo());
					if (resultModel == null) {
						log.warn("产品零件内容为空，不做任何处理 {}", JSONUtil.toJsonStr(resultModel));
						continue;
					}
					ProductPartChild productPartChild = new ProductPartChild();
					productPartChild.setChildCode(resultModel.getProductPartCode());
					productPartChild.setChildAttribute(resultModel.getAttribute());
					productPartChild.setChildNumber(batchAddModel.getNumber());
					productPartChildren.add(productPartChild);
				}
				consistRequest.setProductPartChildList(productPartChildren);
				// 收集关系请求，稍后批量处理
				consistRequests.add(consistRequest);
				productPartModels.add(productPartModel);
			}
		}

		// 批量处理产品零件关系和库存同步
		if (!consistRequests.isEmpty()) {
			handleBatchProductPartRelationAndStockSync(consistRequests, productPartModels, stopWatch);
		}
			stopWatch.stop();
			log.info("\r\n开始执行批量产品零件信息-请求执行耗时：{}", stopWatch.prettyPrint(TimeUnit.MILLISECONDS));
		} catch (Exception e) {
			throw new ExceptionPack(e,
					ExceptionMsg.builder("query WorkmanshipNAction.batchAddProductWork failed").build());
		}
	}

	/**
	 * @description 组装，产品零件后续续操作
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/29 14:51
	 * @department: Product development
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void batchAddComponentWork(List<BatchAddComponentWorkRequest> requestListData) throws ExceptionPack {
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
		// 新技术部-产品零件分类表
		String productPartTypeTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPartType());
		try {
			BatchAddComponentWorkRequest batchAddRpcRequest = requestListData.getFirst();
			if (null == batchAddRpcRequest.getAttribute()) {
				throw new AssertException(ExceptionMsg.builder("")
						.msgView(TechnicalErrorEnum.TECHNICAL_ATTRIBUTE_NOT_EXIST_MESSAGE.getMsg()).build());
			}
			StopWatch stopWatch = new StopWatch("batchAddProductPart");
			stopWatch.start(StrUtil.format("开始执行批量产品零件信息-处理数据大小 {}", requestListData.size()));
			// 获取统一数据 分类数据
			List<String> typeNameList = ExtractUtil.streamMapToList(String::valueOf, requestListData,
					BatchAddComponentWorkRequest::getProductPartTypeCodeName);
			Map<String, ProductPartTypeModel> typeNameToModelMap = unifiedDataService.prepareTypeData(typeNameList,
					productPartTypeTableName, batchAddRpcRequest.getAttribute());

			// 计量单位数据准备
			Set<String> productPiecesCodes = new HashSet<>(ExtractUtil.streamMapToList(String::valueOf, requestListData,
					BatchAddComponentWorkRequest::getPcsName));
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
					.map(BatchAddComponentWorkRequest::getDrawingNumber)
					.filter(Objects::nonNull)
					.toList();
			StorageObjectRpcRequest objectRpcRequest = new StorageObjectRpcRequest();
			objectRpcRequest.setFileNumberList(numberData);
					Map<String, StorageObjectRpcDTO> fileNumberToMap = storageSpaceFileService.getFileNumberToMap(objectRpcRequest);
		
		// 批量预处理检验项目数据
		Set<String> componentItemNames = requestListData.stream().map(BatchAddComponentWorkRequest::getInspectionItemNames).flatMap(Collection::stream).collect(Collectors.toSet());
		Map<String, ProductPartQualityItemsRpcDTO> componentQualityItemsToMap = qualityItemsService.getMapByNames(componentItemNames);

		// 批量预处理物料统一编号数据
		List<String> componentUnityNoListData = requestListData.stream().map(BatchAddComponentWorkRequest::getMaterialModels).flatMap(Collection::stream).filter(Objects::nonNull)
				.map(BatchAddComponentWorkRequest.MaterialModels::getMaterialUnityNo)
				.toList();
		Map<String, ProductPartModel> componentPartUnityNoToModelMap = unifiedDataService.prepareUnityNoData(componentUnityNoListData, productPartTableName);
		
		stopWatch.stop();
		stopWatch.start(StrUtil.format("开始执行批量产品零件信息-初始化查询数据完成,进入业务循环，处理数据大小 {}", requestListData.size()));
		

		for (BatchAddComponentWorkRequest addRpcRequest : requestListData) {
			// 生成产品、零件编号
			Long productPartCode = PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId();
			// 组装检验项名称数据 - 使用预处理的数据
//			ProductPartQualityItemsRpcRequest itemsRpcRequest =
//					new ProductPartQualityItemsRpcRequest().setProductPartQualityItemsSignList(addRpcRequest.getInspectionItemNames());
//			Map<String, ProductPartQualityItemsRpcDTO> qualityItemsToMap = qualityItemsService.getInspectionItemToMap(itemsRpcRequest);
			
			// 组装分类信息
			addRpcRequest.setProductPartTypeCode(typeNameToModelMap
					.getOrDefault(addRpcRequest.getProductPartTypeCodeName(), null).getProductPartTypeCode());
			// 组装计量单位
			addRpcRequest.setPcs(pieceCodeNameMapping.getOrDefault(addRpcRequest.getPcsName(), null));
			// 组装标准非标准
			StandardEnum standardEnum = StandardEnum.parse(addRpcRequest.getStandardName());
			addRpcRequest.setStandard(null != standardEnum ? standardEnum.isValue() : StandardEnum.STANDARD.isValue());
//			List<String> unityNoListData = addRpcRequest.getMaterialModels().stream().filter(Objects::nonNull)
//					.map(BatchAddComponentWorkRequest.MaterialModels::getMaterialUnityNo)
//					.toList();
//			Map<String, ProductPartModel> partUnityNoToModelMap = unifiedDataService.materialUnityNoData(unityNoListData, productPartTableName);

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
				RequestTableHelper.setTableName(productPartTableName);
				List<BatchAddComponentWorkRequest.MaterialModels> batchAddModels =
						addRpcRequest.getMaterialModels();
				StorageObjectRpcDTO storageObjectRpcDTO = fileNumberToMap.get(addRpcRequest.getDrawingNumber());
				// 组装图纸号
				if (storageObjectRpcDTO != null) {
					productPartModel.setFiles(new Long[]{storageObjectRpcDTO.getFileId()});
				} else {
					ProductPartExtraInfo extraInfo = new ProductPartExtraInfo();
					extraInfo.setDrawingNumberName(addRpcRequest.getDrawingNumber());
									productPartModel.setExtra(JSONUtil.toJsonStr(extraInfo));
			}
			// 组装检验项目数据 - 使用预处理的数据
			if (ObjectUtil.isNotEmpty(addRpcRequest.getInspectionItemNames())) {
				productPartModel.setQualityCodeList(
						addRpcRequest.getInspectionItemNames().stream()
								.map(componentQualityItemsToMap::get)
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
						productPartModel.getUnityNo(), Boolean.FALSE);
				productPartModel.setUnityNo(unityNo);
			}
				// 检查生成的unityNo是否已存在
				LambdaQueryWrapper<ProductPartModel> checkQuery = Wrappers.lambdaQuery();
				checkQuery.eq(ProductPartModel::getUnityNo, unityNo)
						.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode())
						.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE);
				RequestTableHelper.setTableName(productPartTableName);
				if (productPartMapper.selectCount(checkQuery) > 0) {
					throw new AssertException(ExceptionMsg.builder("")
							.msgView("编码已存在，请刷新后再试").build());
				}
							// 新增产品零件信息
			RequestTableHelper.setTableName(productPartTableName);
			productPartMapper.insert(productPartModel);
			if (!batchAddModels.isEmpty()) {
				ProductPartConsistRequest consistRequest = new ProductPartConsistRequest();
				consistRequest.setParentAttribute(addRpcRequest.getAttribute());
				consistRequest.setParentCode(productPartModel.getProductPartCode());
				List<ProductPartChild> productPartChildren = new ArrayList<>();
				for (BatchAddComponentWorkRequest.MaterialModels batchAddModel : batchAddModels) {
					ProductPartModel resultModel = componentPartUnityNoToModelMap.get(batchAddModel.getMaterialUnityNo());
					if (resultModel == null) {
						log.warn("产品零件内容为空，不做任何处理 {}", JSONUtil.toJsonStr(resultModel));
						continue;
					}
					ProductPartChild productPartChild = new ProductPartChild();
					productPartChild.setChildCode(resultModel.getProductPartCode());
					productPartChild.setChildAttribute(resultModel.getAttribute());
					productPartChild.setChildNumber(batchAddModel.getNumber());
					productPartChildren.add(productPartChild);
				}
				consistRequest.setProductPartChildList(productPartChildren);
				// 组装，产品零件后续续操作 - 保持原有业务逻辑
				handleProductPartRelationAndStockSync(consistRequest, productPartModel, stopWatch);
			}
		}
			stopWatch.stop();
			log.info("\r\n开始执行批量产品零件信息-请求执行耗时：{}", stopWatch.prettyPrint(TimeUnit.MILLISECONDS));
		} catch (Exception e) {
			throw new ExceptionPack(e,
					ExceptionMsg.builder("query WorkmanshipNAction.batchAddComponentWork failed").build());
		}
	}

	/**
	 * @description 组装，产品零件后续续操作
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/29 14:51
	 * @department: Product development
	 */
	private void handleProductPartRelationAndStockSync(ProductPartConsistRequest consistRequest, ProductPartModel productPartModel,
	                                                   StopWatch stopWatch) {
		// 组装，产品零件绑定关系
		productPartCommonService.productPartConsist(consistRequest);
		try {
			StopWatch stockWatch = new StopWatch("stockMapper.insert");
			stockWatch.start("stockMapper.insert Start");
			// 同步库存信息
			productPartCommonService.syncStock(productPartModel.getProductPartCode());
			stockWatch.stop();
			log.info("\r\n开始同步产品零件数据到仓库表-结束, 请求执行耗时：{} 请求入参 {}", stopWatch.prettyPrint(TimeUnit.MILLISECONDS),
					productPartModel.getProductPartCode());
		} catch (Exception e) {
			log.error("error 同步产品零件数据到仓库表 执行失败 ", e);
		}
	}

	/**
	 * @description 批量组装，产品零件后续续操作
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/1/14 11:00
	 * @department: Product development
	 */
	private void handleBatchProductPartRelationAndStockSync(List<ProductPartConsistRequest> consistRequests,
	                                                        List<ProductPartModel> productPartModels,
	                                                        StopWatch stopWatch) {
		// 批量处理产品零件绑定关系
		for (ProductPartConsistRequest consistRequest : consistRequests) {
			productPartCommonService.productPartConsist(consistRequest);
		}
		
		try {
			StopWatch stockWatch = new StopWatch("stockMapper.insert");
			stockWatch.start("stockMapper.insert Start");
			log.warn("start 批量同步产品零件数据到仓库表 ");
			// 批量同步库存信息
			// 新技术部-库存表
			String stockTableName =
					tableFactory.getTableName(tableFactory.module.getWarehouse(), tableFactory.table.getStock());
			List<StockModel> list = new ArrayList<>(productPartModels.size());
			for (ProductPartModel productPartModel : productPartModels) {
				//新增同步库存数据
				StockModel stockModel = new StockModel();
				stockModel.setProductPartCode(productPartModel.getProductPartCode())
						.setUnqualifiedInventory(BigDecimal.ZERO)
						.setLockInInventor(BigDecimal.ZERO)
						.setTotalInventory(BigDecimal.ZERO)
						// 状态 true：启用
						.setState(Boolean.TRUE)
						// 库存初始化状态 默认 False
						.setStockInitState(Boolean.FALSE)
						.setEnterpriseCode(getEnterpriseCode());
				list.add(stockModel);
			}
			try (AutoCloseable ignored = RequestTableHelper::clearBatchTableName) {
				RequestTableHelper.setBatchTableName(stockTableName);
				stockMapper.insert(list);
			}

			stockWatch.stop();
			log.info("\r\n开始批量同步产品零件数据到仓库表-结束, 请求执行耗时：{} 请求入参 {}", stopWatch.prettyPrint(TimeUnit.MILLISECONDS),
					productPartModels.stream().map(ProductPartModel::getProductPartCode).toList());
		} catch (Exception e) {
			log.error("start 批量同步产品零件数据到仓库表 执行失败 ", e);
		}
	}



	@Override
	public void productPartCopy(ProductPartRpcRequest requestData) throws AssertException {
		// 新技术部-产品零件表
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
		// 生成产品、零件编号
		Long productPartCode = PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId();
		RequestTableHelper.setTableName(productPartTableName);
		ProductPartModel productPartModel = productPartMapper.selectById(requestData.getProductPartCode());
		Long productPartCodeOld = productPartModel.getProductPartCode();
		ProductPartModel model =
				Convert.convert(ProductPartModel.class, productPartModel).setId(null)
						.setEnterTime(DateUtil.date()).setProductPartCode(productPartCode)
						.setProductPartSign(productPartModel.getProductPartSign()).setEnterpriseCode(getEnterpriseCode())
						.setCreator(getUserCode()).setSyncStatus(CommonConstant.NUMBER_ZERO)
						.setUpdateCount(CommonConstant.NUMBER_ZERO).setProductPartSign(productPartModel.getProductPartSign())
						.setModel(productPartCommonService.generateCopyModel(productPartModel.getModel(), productPartTableName));
		// 获取表代码-统一单号生成逻辑提取
		String unityNo = productPartCommonService.getGlobalSerialUnityNo(productPartModel.getAttribute(),
				productPartModel.getProductPartTypeCode(), productPartModel.getUnityNo());
		productPartModel.setUnityNo(unityNo);
		RequestTableHelper.setTableName(productPartTableName);
		productPartMapper.insert(model);
		// 全量复制产品零件绑定关系
		fullReplication(productPartCodeOld, model);
		// 同步库存信息
		productPartCommonService.syncStock(model.getProductPartCode());
	}

	@Transactional(rollbackFor = Exception.class)
	public void fullReplication(Long productPartCodeOld, ProductPartModel productPartModelNew) {
		try {
			// 简化复制逻辑：只复制关系数据，将原来的所有子节点重新映射到新的根节点
			simpleReplicationWithCorrectHierarchy(productPartCodeOld, productPartModelNew.getProductPartCode(), productPartModelNew.getAttribute());
		} catch (Exception e) {
			log.error(" 复制-深度组装零件错误 fullReplication ", e);
		}
	}

	/**
	 * 简化复制逻辑：重新构建层级关系
	 * @param originalRootCode 原始根节点code
	 * @param newRootCode 新的根节点code  
	 * @param rootAttribute 根节点属性
	 */
	private void simpleReplicationWithCorrectHierarchy(Long originalRootCode, Long newRootCode, Integer rootAttribute) {
		try {
			// 获取原始根节点的所有子节点（包括所有层级）
			ProductPartCompRequest requestData = new ProductPartCompRequest();
			requestData.setParentCode(originalRootCode);
			List<ProductPartConsistDTO> allChildren = productPartCommonService.getProductConsistList(requestData);
			
			if (allChildren.isEmpty()) {
				return; // 没有子节点，结束操作
			}
			
			// 过滤出真正的直接子节点
			List<ProductPartConsistDTO> directChildren = filterDirectChildren(allChildren, originalRootCode);
			
			List<ProductPartChild> newChildren = new ArrayList<>();
			Set<Long> processedChildCodes = new LinkedHashSet<>(); // 去重
			
			for (ProductPartConsistDTO child : directChildren) {
				// 去重逻辑
				if (!processedChildCodes.add(child.getChildCode())) {
					log.debug("检测到重复的childCode，已过滤: {}", child.getChildCode());
					continue;
				}
				
				ProductPartChild newChild = new ProductPartChild();
				// 直接使用原来的code，不创建新的产品零件
				newChild.setChildCode(child.getChildCode());
				newChild.setChildAttribute(child.getChildAttribute());
				newChild.setChildNumber(child.getChildNumber());
				
				newChildren.add(newChild);
			}
			
			if (!newChildren.isEmpty()) {
				// 建立新的父子关系
				ProductPartConsistRequest consistRequest = new ProductPartConsistRequest();
				consistRequest.setParentAttribute(rootAttribute);
				consistRequest.setParentCode(newRootCode);
				consistRequest.setProductPartChildList(newChildren);
				productPartCommonService.productPartConsist(consistRequest);
			}
			
			log.info("复制完成：原根节点 {} -> 新根节点 {}，复制了 {} 个直接子节点", 
					originalRootCode, newRootCode, newChildren.size());
			
		} catch (Exception e) {
			log.error("简化复制产品零件层级结构错误，originalRootCode: {}, newRootCode: {}", 
					originalRootCode, newRootCode, e);
			throw new RuntimeException(e);
		}
	}

	/**
	 * 从所有子节点中过滤出直接子节点
	 * @param allChildren 所有子节点列表
	 * @param parentCode 父节点code
	 * @return 直接子节点列表
	 */
	private List<ProductPartConsistDTO> filterDirectChildren(List<ProductPartConsistDTO> allChildren, Long parentCode) {
		if (allChildren.isEmpty()) {
			return Collections.emptyList();
		}

		List<ProductPartConsistDTO> directChildren = new ArrayList<>();
		Set<Long> allChildCodes = allChildren.stream()
				.map(ProductPartConsistDTO::getChildCode)
				.collect(Collectors.toSet());

		// 对于每个子节点，检查它是否是其他子节点的子节点
		for (ProductPartConsistDTO child : allChildren) {
			boolean isDirectChild = true;

			// 检查是否存在中间节点包含这个子节点
			for (ProductPartConsistDTO potentialParent : allChildren) {
				if (!potentialParent.getChildCode().equals(child.getChildCode())) {
					// 检查 potentialParent 是否包含 child
					ProductPartCompRequest checkRequest = new ProductPartCompRequest();
					checkRequest.setParentCode(potentialParent.getChildCode());
					try {
						List<ProductPartConsistDTO> subChildren = productPartCommonService.getProductConsistList(checkRequest);
						boolean containsChild = subChildren.stream()
								.anyMatch(sub -> sub.getChildCode().equals(child.getChildCode()));
						if (containsChild) {
							isDirectChild = false;
							break;
						}
					} catch (Exception e) {
						log.warn("检查子节点关系时出错，parent: {}, child: {}", potentialParent.getChildCode(), child.getChildCode(), e);
					}
				}
			}

			if (isDirectChild) {
				directChildren.add(child);
			}
		}

		log.debug("父节点 {} 的所有子节点数: {}, 直接子节点数: {}",
				parentCode, allChildren.size(), directChildren.size());

		return directChildren;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void partMaterialCopy(ProductPartRpcRequest requestData) throws AssertException {
		// 新技术部-产品零件表
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
		// 生成产品、零件编号
		Long productPartCode = PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId();
		RequestTableHelper.setTableName(productPartTableName);
		ProductPartModel productPartModel = productPartMapper.selectById(requestData.getProductPartCode());
		ProductPartModel model =
				Convert.convert(ProductPartModel.class, productPartModel).setId(null)
						.setEnterTime(DateUtil.date()).setProductPartCode(productPartCode)
						.setProductPartSign(productPartModel.getProductPartSign()).setEnterpriseCode(getEnterpriseCode())
						.setCreator(getUserCode()).setSyncStatus(CommonConstant.NUMBER_ZERO)
						.setUpdateCount(CommonConstant.NUMBER_ZERO).setProductPartSign(productPartModel.getProductPartSign())
						.setModel(productPartCommonService.generateCopyModel(productPartModel.getModel(), productPartTableName));
		// 获取表代码-统一单号生成逻辑提取
		String unityNo = productPartCommonService.getGlobalSerialUnityNo(productPartModel.getAttribute(),
				productPartModel.getProductPartTypeCode(), productPartModel.getUnityNo());
		productPartModel.setUnityNo(unityNo);
		RequestTableHelper.setTableName(productPartTableName);
		productPartMapper.insert(model);
		// 同步库存信息
		productPartCommonService.syncStock(model.getProductPartCode());
	}

	@Override
	public RpcPagingDTO<ProductMaterialRpcDTO> getProductMaterialList(ProductPartPageRequest requestData) throws ExceptionPack {
		log.info("start WorkmanshipNAction.getProductMaterialList requestData {} ", JSONUtil.toJsonStr(requestData));
		if (StrUtil.isBlank(requestData.getReverseLookup()) && requestData.getProductPartCode() == null) {
			return new RpcPagingDTO<>(Collections.emptyList(), CommonConstant.NUMBER_ZERO);
		}
		// 新技术部-产品零件表
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
		// 新技术部-库存表
		String stockTableName =
				tableFactory.getTableName(tableFactory.module.getWarehouse(), tableFactory.table.getStock());
		// 产品零件与工艺路线关系表
		String productPartRouteRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProductPartRouteRelationship());
		// 工序表与零件关系表
		String procedurePartRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProcedurePartRelationship());
		// 生成产品、零件编号
		LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
		if (StrUtil.isNotBlank(requestData.getReverseLookup())) {
			String keyword = "%" + requestData.getReverseLookup().trim() + "%";
			qw.and(wp -> wp.apply("lower(unity_no) like lower({0})", keyword)
					.or().apply("lower(model) like lower({0})", keyword));
		}
		if (requestData.getProductPartCode() != null) {
			qw.eq(ProductPartModel::getProductPartCode, requestData.getProductPartCode());
		}
		// 目前只查询分类1-4的数据
		List<Integer> partAttributeList = batisDynamicTableNameFactory.getProductPartAttributeList();
		qw.in(ProductPartModel::getAttribute, partAttributeList);
		qw.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode());
		qw.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE).orderByDesc(ProductPartModel::getId);
		Page<ProductPartModel> page = new Page<>(requestData.getCurrent(), requestData.getSize());
		RequestTableHelper.setTableName(productPartTableName);
		Page<ProductPartModel> pageList = productPartMapper.selectPage(page, qw);
		List<ProductMaterialRpcDTO> resultList = Convert.toList(ProductMaterialRpcDTO.class, pageList.getRecords());
		if (ObjectUtil.isEmpty(resultList)) {
			return new RpcPagingDTO<>(Collections.emptyList(), CommonConstant.NUMBER_ZERO);
		}
		// 计量单位数据准备
		Set<Long> productPiecesCodes =
				new HashSet<>(ExtractUtil.streamMapToList(Long::valueOf, resultList, ProductMaterialRpcDTO::getPcs));
		// 获取产品与名称映射
		Map<Long, String> pieceCodeNameMapping =
				piecesService.pieceCodeChnNameMapping(productPiecesCodes, getEnterpriseCode());
		// 产品零件数据准备
		Set<Long> productPartCodeSet =
				new HashSet<>(ExtractUtil.streamMapToList(Long::valueOf, resultList, ProductMaterialRpcDTO::getProductPartCode));
		List<Long> productPartCodeList = new ArrayList<>(productPartCodeSet);
		// 查询在途明细数据
		ProductMaterialRpcRequest materialRpcRequest = new ProductMaterialRpcRequest();
		materialRpcRequest.setProductPartCodeList(productPartCodeList);
		List<ProductTransitRpcDTO> transitRpcDTOS = selfReceivingService.getPurchaseDetailList(materialRpcRequest);
		// 计算在途数量 contract_quantity-received_quantity & >0
		Map<Long, BigDecimal> productPartCodeToOnWayNumMap = new HashMap<>();
		for (ProductTransitRpcDTO dto : transitRpcDTOS) {
			productPartCodeToOnWayNumMap.merge(dto.getProductPartCode(),
					dto.getOnWayNum(),
					BigDecimal::add);
		}
		// 查询生产明细数据
		ProduceDetailConditionDTO conditionDTO = new ProduceDetailConditionDTO();
		conditionDTO.setProductPartCodeList(productPartCodeList);
		List<ProductProductionRpcDTO> productProductionRpcDTOS = producePageDetailService.getProductionDetailList(conditionDTO).getPageDetails();
		// 计算生产量
		Map<Long, BigDecimal> productPartCodeToTaskNumMap = new HashMap<>();
		for (ProductProductionRpcDTO dto : productProductionRpcDTOS) {
			if (dto.getTaskNum() != null && dto.getTaskNum().compareTo(BigDecimal.ZERO) > 0) {
				productPartCodeToTaskNumMap.merge(dto.getProductPartCode(),
						dto.getTaskNum(),
						BigDecimal::add);
			}
		}
		// 查询库存数据
		Map<Long, StockModel> stockModelMap = stockMapper.getStockByProductPartCodeInNew(productPartCodeList, getEnterpriseCode(), stockTableName)
				.stream().collect(Collectors.toMap(StockModel::getProductPartCode, Function.identity()));
		for (ProductMaterialRpcDTO rpcDTO : resultList) {
			rpcDTO.setOnWayNum(productPartCodeToOnWayNumMap.getOrDefault(rpcDTO.getProductPartCode(), BigDecimal.ZERO));
			rpcDTO.setProduceNum(productPartCodeToTaskNumMap.getOrDefault(rpcDTO.getProductPartCode(), BigDecimal.ZERO));
			StockModel stockModel = stockModelMap.get(rpcDTO.getProductPartCode());
			rpcDTO.setTotalInventory(stockModel.getTotalInventory());
			rpcDTO.setCanUseInventory(ObjectUtil.isNotEmpty(stockModel.getTotalInventory()) ?
					stockModel.getTotalInventory().subtract(Optional.ofNullable(stockModel.getLockInInventor()).orElse(BigDecimal.ZERO)) :
					BigDecimal.ZERO);
			rpcDTO.setTotalDemand(String.valueOf(CommonConstant.NUMBER_ONE));
			// 组装 计量单位名称
			if (rpcDTO.getPcs() != null) {
				rpcDTO.setPcsName(pieceCodeNameMapping.get(rpcDTO.getPcs()));
			}
			ProductPartCompRequest compRequest = new ProductPartCompRequest();
			compRequest.setParentCode(rpcDTO.getProductPartCode());
			List<ProductPartConsistDTO> consistDTOList = productPartCommonService.getProductConsistList(compRequest);
			rpcDTO.setBindData(!consistDTOList.isEmpty());
			if (StringUtils.isEmpty(rpcDTO.getQuantity())) {
				rpcDTO.setQuantity("0");
			}
		}
		return new RpcPagingDTO<>(resultList, pageList.getTotal());
	}

	@Override
	public List<?> getProductMaterialRecursive(ProductPartPageRequest requestData) throws ExceptionPack {
		log.info("start WorkmanshipNAction.getProductMaterialRecursive requestData {} ", JSONUtil.toJsonStr(requestData));
		if (StrUtil.isBlank(requestData.getReverseLookup()) && requestData.getProductPartCode() == null) {
			return Collections.emptyList();
		}
		// 新技术部-产品零件表
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
		// 新技术部-库存表
		String stockTableName =
				tableFactory.getTableName(tableFactory.module.getWarehouse(), tableFactory.table.getStock());
		// 产品零件与工艺路线关系表
		String productPartRouteRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProductPartRouteRelationship());
		// 工序表与零件关系表
		String procedurePartRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProcedurePartRelationship());
		StopWatch stopWatch = new StopWatch("WorkmanshipNAction.getProductMaterialRecursive");
		stopWatch.start("WorkmanshipNAction.getProductMaterialRecursive Start");
		RequestTableHelper.setTableName(productPartTableName);
		ProductPartModel productPartModel = productPartMapper.selectById(requestData.getProductPartCode());
		ProductPartCompRequest compRequest = new ProductPartCompRequest();
		compRequest.setParentCode(productPartModel.getProductPartCode());
		List<ProductPartConsistTreeDTO> consistDTOList = productPartCommonService.productPartConsistTreeForm(compRequest);
		List<ProductMaterialRpcDTO> resultList = new ArrayList<>();
		for (ProductPartConsistTreeDTO consistDTO : consistDTOList) {
			// 生成产品、零件编号
			RequestTableHelper.setTableName(productPartTableName);
			ProductPartModel resultModel = productPartMapper.selectById(consistDTO.getChildCode());
			ProductMaterialRpcDTO materialRpcDTO = Convert.convert(ProductMaterialRpcDTO.class, resultModel);
			materialRpcDTO.setChildNumber(consistDTO.getChildNumber());
			resultList.add(materialRpcDTO);
		}
		// 计量单位数据准备
		Set<Long> productPiecesCodes =
				new HashSet<>(ExtractUtil.streamMapToList(Long::valueOf, resultList, ProductMaterialRpcDTO::getPcs));
		// 获取产品与名称映射
		Map<Long, String> pieceCodeNameMapping =
				piecesService.pieceCodeChnNameMapping(productPiecesCodes, getEnterpriseCode());
		// 产品零件数据准备
		Set<Long> productPartCodeSet =
				new HashSet<>(ExtractUtil.streamMapToList(Long::valueOf, resultList, ProductMaterialRpcDTO::getProductPartCode));
		List<Long> productPartCodeList = new ArrayList<>(productPartCodeSet);
		// 查询在途明细数据
		ProductMaterialRpcRequest materialRpcRequest = new ProductMaterialRpcRequest();
		materialRpcRequest.setProductPartCodeList(productPartCodeList);
		stopWatch.stop();
		stopWatch.start(StrUtil.format("开始查询在途明细数据,处理数据大小 {}", productPartCodeList.size()));
		List<ProductTransitRpcDTO> transitRpcDTOS = selfReceivingService.getPurchaseDetailList(materialRpcRequest);
		// 计算在途数量 contract_quantity-received_quantity & >0
		Map<Long, BigDecimal> productPartCodeToOnWayNumMap = new HashMap<>();
		for (ProductTransitRpcDTO dto : transitRpcDTOS) {
			productPartCodeToOnWayNumMap.merge(dto.getProductPartCode(),
					dto.getOnWayNum(),
					BigDecimal::add);
		}
		// 查询生产明细数据
		ProduceDetailConditionDTO conditionDTO = new ProduceDetailConditionDTO();
		conditionDTO.setProductPartCodeList(productPartCodeList);
		stopWatch.stop();
		stopWatch.start(StrUtil.format("开始查询在途明细数据,处理数据大小 {}", productPartCodeList.size()));
		List<ProductProductionRpcDTO> productProductionRpcDTOS =
				producePageDetailService.getProductionDetailList(conditionDTO).getPageDetails();
		// 计算生产量
		Map<Long, BigDecimal> productPartCodeToTaskNumMap = new HashMap<>();
		for (ProductProductionRpcDTO dto : productProductionRpcDTOS) {
			if (dto.getTaskNum() != null && dto.getTaskNum().compareTo(BigDecimal.ZERO) > 0) {
				productPartCodeToTaskNumMap.merge(dto.getProductPartCode(),
						dto.getTaskNum(),
						BigDecimal::add);
			}
		}
		stopWatch.stop();
		stopWatch.start(StrUtil.format("开始查询库存数据数据,处理数据大小 {}", productPartCodeList.size()));
		Map<Long, StockModel> stockModelMap = new HashMap<>();
		if (ObjectUtil.isNotEmpty(productPartCodeList)) {
			// 查询库存数据
			stockModelMap = stockMapper.getStockByProductPartCodeInNew(productPartCodeList, getEnterpriseCode(),
							stockTableName)
					.stream().collect(Collectors.toMap(StockModel::getProductPartCode, Function.identity()));
		}
		for (ProductMaterialRpcDTO rpcDTO : resultList) {
			rpcDTO.setOnWayNum(productPartCodeToOnWayNumMap.getOrDefault(rpcDTO.getProductPartCode(), BigDecimal.ZERO));
			rpcDTO.setProduceNum(productPartCodeToTaskNumMap.getOrDefault(rpcDTO.getProductPartCode(), BigDecimal.ZERO));
			StockModel stockModel = stockModelMap.getOrDefault(rpcDTO.getProductPartCode(), new StockModel());
			rpcDTO.setTotalInventory(stockModel.getTotalInventory());
			rpcDTO.setCanUseInventory(ObjectUtil.isNotEmpty(stockModel.getTotalInventory()) ?
					stockModel.getTotalInventory().subtract(Optional.ofNullable(stockModel.getLockInInventor()).orElse(BigDecimal.ZERO)) :
					BigDecimal.ZERO);
//			rpcDTO.setReqDemandQuantity(requestData.getDemandQuantity());
//			if (StringUtils.isNotEmpty(requestData.getDemandQuantity()) && StringUtils.isNotEmpty(rpcDTO.getQuantity())) {
//				rpcDTO.setTotalDemand(new BigDecimal(requestData.getDemandQuantity())
//						.multiply(new BigDecimal(rpcDTO.getQuantity())).setScale(2, RoundingMode.HALF_UP)
//						.stripTrailingZeros()
//						.toPlainString());
//			} else if (requestData.getDemandQuantity() != null && !requestData.getDemandQuantity().isEmpty()) {
//				rpcDTO.setTotalDemand(new BigDecimal(requestData.getDemandQuantity())
//						.multiply(new BigDecimal(rpcDTO.getChildNumber()))
//						.setScale(2, RoundingMode.HALF_UP)
//						.stripTrailingZeros()
//						.toPlainString());
//			} else {
//				rpcDTO.setTotalDemand(new BigDecimal(rpcDTO.getChildNumber()).setScale(2, RoundingMode.HALF_UP)
//						.stripTrailingZeros()
//						.toPlainString());
//			}

		if (StringUtils.isNotEmpty(rpcDTO.getQuantity())) {
			rpcDTO.setQuantity(rpcDTO.getQuantity());
		} else if (rpcDTO.getChildNumber() != null) {
			rpcDTO.setQuantity(rpcDTO.getChildNumber().toPlainString());
		}

		if (StringUtils.isEmpty(rpcDTO.getQuantity())) {
			rpcDTO.setQuantity("0");
		}
			// 组装 计量单位名称
			if (rpcDTO.getPcs() != null) {
				rpcDTO.setPcsName(pieceCodeNameMapping.get(rpcDTO.getPcs()));
			}
			ProductPartCompRequest compRequestNext = new ProductPartCompRequest();
			compRequestNext.setParentCode(rpcDTO.getProductPartCode());
			List<ProductPartConsistDTO> consistDTOListNext = productPartCommonService.getProductConsistList(compRequestNext);
			rpcDTO.setBindData(!consistDTOListNext.isEmpty());

		}
		stopWatch.stop();
		log.info("\r\n开始产品查询-结束, 请求执行耗时：{} 请求入参 {}", stopWatch.prettyPrint(TimeUnit.MILLISECONDS),
				productPartModel.getProductPartCode());
		return resultList;
	}

}
