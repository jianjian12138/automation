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
import com.futurecraftsmen.pms.technical.api.domain.technical.items.ProductPartQualityItemsRpcRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.ProductMaterialRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.ProductPartExtraInfo;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.ProductPartPageRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.ProductPartRpcRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.standard.BatchAddComponentWorkRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.standard.BatchAddProductWorkRequest;
import com.futurecraftsmen.pms.technical.api.service.IPiecesService;
import com.futurecraftsmen.pms.technical.api.service.technical.IProductPartQualityItemsService;
import com.futurecraftsmen.pms.technical.service.common.enums.TechnicalErrorEnum;
import com.futurecraftsmen.pms.technical.service.dao.technical.IProductPartMapper;
import com.futurecraftsmen.pms.technical.service.dao.warehouse.IStockMapper;
import com.futurecraftsmen.pms.technical.service.domain.StandardEnum;
import com.futurecraftsmen.pms.technical.service.domain.StateEnum;
import com.futurecraftsmen.pms.technical.service.domain.technical.procedure.ProcedurePartRelationshipModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.procedure.ProcedureRouteRelationshipModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.procedure.ProductPartProcedureModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.process.ProcessRouteDataModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.productpart.ProductPartModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.productpart.ProductPartRouteRelationshipModel;
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
public class WorkmanshipYAction implements WorkmanshipYNStrategy {

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
		// 新技术部-产品零件表
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
		// 新技术部-产品零件分类表
		String productPartTypeTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPartType());
		// 工艺路线管理表
		String processRouteDataTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProcessRouteData());
		// 产品零件与工艺路线关系表
		String productPartRouteRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProductPartRouteRelationship());
		// 工序表与工艺路线关系表
		String procedureRouteRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProcedureRouteRelationship());
		// 工序表与零件关系表
		String procedurePartRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProcedurePartRelationship());
		// 产品零件工序表
		String productPartProcedure = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProductPartProcedure());
		try {
			//starthere
			// 对requestListData中的每个请求对象的inspectionItemNames进行规范化去重处理
			for (BatchAddProductWorkRequest request : requestListData) {
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
			// 获取统一数据 工艺路线数据
			List<String> listData = ExtractUtil.streamMapToList(String::valueOf, requestListData,
					BatchAddProductWorkRequest::getProcessRouteData);
			Map<String, ProcessRouteDataModel> routeDataSignToModelMap =
					unifiedDataService.routeDataSignToModelMap(listData, processRouteDataTableName);
			stopWatch.stop();
			stopWatch.start(StrUtil.format("开始执行批量产品零件信息-初始化查询数据完成,进入业务循环，处理数据大小 {}", requestListData.size()));

			Set<String> itemNames = requestListData.stream().map(BatchAddProductWorkRequest::getInspectionItemNames).flatMap(Collection::stream).collect(Collectors.toSet());
			Map<String, ProductPartQualityItemsRpcDTO> qualityItemsToMap = qualityItemsService.getMapByNames(itemNames);

			List<String> unityNoListData = requestListData.stream().map(BatchAddProductWorkRequest::getMaterialModels).flatMap(Collection::stream).filter(Objects::nonNull)
					.map(BatchAddProductWorkRequest.MaterialModels::getMaterialUnityNo)
					.toList();

			Map<String, ProductPartModel> partUnityNoToModelMap = unifiedDataService.materialUnityNoData(unityNoListData, productPartTableName);

			List<ProcedureRouteRelationshipModel> procedureRouteRelModels = new ArrayList<>();
			List<ProductPartModel> productPartModels = new ArrayList<>();
			for (BatchAddProductWorkRequest addRpcRequest : requestListData) {
				// 生成产品、零件编号
				Long productPartCode = PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId();
				// 生成关系唯一 ID
				Long uniqueId = PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId();
				// 组装检验项名称数据
				ProductPartQualityItemsRpcRequest itemsRpcRequest =
						new ProductPartQualityItemsRpcRequest().setProductPartQualityItemsSignList(addRpcRequest.getInspectionItemNames());
//				Map<String, ProductPartQualityItemsRpcDTO> qualityItemsToMap = qualityItemsService.getInspectionItemToMap(itemsRpcRequest);

				// 组装分类信息
				addRpcRequest.setProductPartTypeCode(typeNameToModelMap
						.getOrDefault(addRpcRequest.getProductPartTypeCodeName(), null).getProductPartTypeCode());
				// 组装计量单位
				addRpcRequest.setPcs(pieceCodeNameMapping.getOrDefault(addRpcRequest.getPcsName(), null));
				// 组装标准非标准
				StandardEnum standardEnum = StandardEnum.parse(addRpcRequest.getStandardName());
				addRpcRequest.setStandard(null != standardEnum ? standardEnum.isValue() : StandardEnum.STANDARD.isValue());
//				List<String> unityNoListData = addRpcRequest.getMaterialModels().stream().filter(Objects::nonNull)
//						.map(BatchAddProductWorkRequest.MaterialModels::getMaterialUnityNo)
//						.toList();
//				Map<String, ProductPartModel> partUnityNoToModelMap = unifiedDataService.materialUnityNoData(unityNoListData, productPartTableName);
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
				ProcessRouteDataModel processRouteDataModel =
						routeDataSignToModelMap.get(addRpcRequest.getProcessRouteData());
				// 组装产品零件与工艺路线关系数据
				if (processRouteDataModel != null) {
					productPartModel.setDefaultRoute(processRouteDataModel.getProcessRouteDataCode());
					ProductPartRouteRelationshipModel routeRelationshipModel = new ProductPartRouteRelationshipModel()
							.setEnterpriseCode(getEnterpriseCode()).setProductPartCode(productPartCode)
							.setProcessRouteDataCode(processRouteDataModel.getProcessRouteDataCode())
							.setState(Boolean.TRUE).setUniqueId(uniqueId);
					RequestTableHelper.setTableName(productPartRouteRelationshipTableName);
					productPartRouteRelationshipService.save(routeRelationshipModel);
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
				productPartModels.add(productPartModel);
				if (!batchAddModels.isEmpty()) {
					for (BatchAddProductWorkRequest.MaterialModels batchAddModel : batchAddModels) {
						ProductPartModel resultModel = partUnityNoToModelMap.get(batchAddModel.getMaterialUnityNo());
						if (processRouteDataModel == null || resultModel == null) {
							log.info("processRouteDataModel 数据查询空 不进行关联数据保存");
							continue;
						}
						// 组装工艺路线与工序关系数据
						LambdaQueryWrapper<ProductPartProcedureModel> queryProcedure = Wrappers.lambdaQuery();
						queryProcedure.eq(ProductPartProcedureModel::getNumber, batchAddModel.getProcedureNumber());
						queryProcedure.eq(ProductPartProcedureModel::getEnterpriseCode, getEnterpriseCode());
						queryProcedure.eq(ProductPartProcedureModel::getDeleteFlag, Boolean.FALSE);
						// 使用 stream 进行倒序排序并获取第一条记录
						RequestTableHelper.setTableName(productPartProcedure);
						Optional<ProductPartProcedureModel> productPartProcedureModel = productPartProcedureService
								.list(queryProcedure).stream().max(Comparator.comparing(ProductPartProcedureModel::getId));
						productPartProcedureModel.ifPresent(procedureModel -> {
							Long productPartProcedureCode = procedureModel.getProductPartProcedureCode();
							if (!processRouteDataModel.getProcedureData().contains(String.valueOf(productPartProcedureCode))) {
								log.warn("工序号不存在当前工艺路线中，不进行绑定操作,跳过关系数据绑定,工序号 {} 工艺路线号 {} 工艺路线绑定工序号 {} ",
										productPartProcedureCode, processRouteDataModel.getProcessRouteDataCode(),
										processRouteDataModel.getProcedureData());
								return;
							}
							ProcedureRouteRelationshipModel procedureRouteRelModel =
									new ProcedureRouteRelationshipModel().setEnterpriseCode(getEnterpriseCode())
											.setProcedureCode(productPartProcedureCode)
											.setProcessRouteDataCode(processRouteDataModel.getProcessRouteDataCode())
											.setSequence(CommonConstant.NUMBER_ZERO)
											.setProductPartCode(resultModel.getProductPartCode()).setUniqueId(uniqueId);
							procedureRouteRelModels.add(procedureRouteRelModel);
						});
						// 分为 2 中情况，第一种 挂了工序号
						if (StrUtil.isNotBlank(batchAddModel.getProcedureNumber())) {
							// 组装工序和零件绑定关系数据
							LambdaQueryWrapper<ProductPartProcedureModel> qw = Wrappers.lambdaQuery();
							qw.eq(ProductPartProcedureModel::getNumber, batchAddModel.getProcedureNumber());
							qw.eq(ProductPartProcedureModel::getEnterpriseCode, getEnterpriseCode());
							qw.eq(ProductPartProcedureModel::getDeleteFlag, Boolean.FALSE);
							// 使用 stream 进行倒序排序并获取第一条记录
							RequestTableHelper.setTableName(productPartProcedure);
							Optional<ProductPartProcedureModel> optionalProductPartProcedureModel =
									productPartProcedureService.list(qw).stream()
											.max(Comparator.comparing(ProductPartProcedureModel::getId));
							optionalProductPartProcedureModel.ifPresent(procedureModel -> {
								Long productPartProcedureCode = procedureModel.getProductPartProcedureCode();
								if (!processRouteDataModel.getProcedureData().contains(String.valueOf(productPartProcedureCode))) {
									log.warn("工序号不存在当前工艺路线中，不进行绑定操作,跳过关系数据绑定,工序号 {} 工艺路线号 {} 工艺路线绑定工序号 {} ",
											productPartProcedureCode, processRouteDataModel.getProcessRouteDataCode(),
											processRouteDataModel.getProcedureData());
									return;
								}
								ProcedurePartRelationshipModel procedurePartRelationshipModel =
										new ProcedurePartRelationshipModel()
												.setProductPartCode(resultModel.getProductPartCode())
												.setEnterpriseCode(getEnterpriseCode())
												.setProcedureCode(procedureModel.getProductPartProcedureCode())
												.setProcessRouteDataCode(processRouteDataModel.getProcessRouteDataCode())
												.setUniqueId(uniqueId);
								BigDecimal number = batchAddModel.getNumber();
								if (number == null || number.compareTo(BigDecimal.ZERO) < 0) {
									number = BigDecimal.ZERO;
								}
								procedurePartRelationshipModel.setQuantity(number);
								RequestTableHelper.setTableName(procedurePartRelationshipTableName);
								procedurePartRelationshipService.save(procedurePartRelationshipModel);

							});
						} else {
							BigDecimal number = batchAddModel.getNumber();
							if (number == null || number.compareTo(BigDecimal.ZERO) < 0) {
								number = BigDecimal.ZERO;
							}
							ProcedurePartRelationshipModel procedurePartRelationshipModel =
									new ProcedurePartRelationshipModel()
											.setProductPartCode(resultModel.getProductPartCode())
											.setEnterpriseCode(getEnterpriseCode()).setProcedureCode(0L)
											.setProcessRouteDataCode(processRouteDataModel.getProcessRouteDataCode())
											.setQuantity(number)
											.setUniqueId(uniqueId);
							RequestTableHelper.setTableName(procedurePartRelationshipTableName);
							procedurePartRelationshipService.save(procedurePartRelationshipModel);
						}
					}
				}
			}

			// 组装，产品零件后续续操作
			handleBatchProductPartRelationAndStockSync(procedureRouteRelModels, productPartModels, stopWatch);
			stopWatch.stop();
			log.info("\r\n开始执行批量产品零件信息-请求执行耗时：{}", stopWatch.prettyPrint(TimeUnit.MILLISECONDS));
		} catch (Exception e) {
			throw new ExceptionPack(e,
					ExceptionMsg.builder("query WorkmanshipYAction.batchAddProductWork failed").build());
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void batchAddComponentWork(List<BatchAddComponentWorkRequest> requestListData) throws ExceptionPack {
		// 新技术部-产品零件表
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
		// 新技术部-产品零件分类表
		String productPartTypeTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPartType());
		// 工艺路线管理表
		String processRouteDataTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProcessRouteData());
		// 产品零件与工艺路线关系表
		String productPartRouteRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProductPartRouteRelationship());
		// 工序表与工艺路线关系表
		String procedureRouteRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProcedureRouteRelationship());
		// 工序表与零件关系表
		String procedurePartRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProcedurePartRelationship());
		// 产品零件工序表
		String productPartProcedure = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProductPartProcedure());
		try {

			//starthere
			// 对requestListData中的每个请求对象的inspectionItemNames进行规范化去重处理
			for (BatchAddComponentWorkRequest request : requestListData) {
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
			// 获取统一数据 工艺路线数据
			List<String> listData = ExtractUtil.streamMapToList(String::valueOf, requestListData,
					BatchAddComponentWorkRequest::getProcessRouteData);
			Map<String, ProcessRouteDataModel> routeDataSignToModelMap =
					unifiedDataService.routeDataSignToModelMap(listData, processRouteDataTableName);
			// 获取统一数据 图纸号数据
			List<String> numberData = requestListData.stream()
					.map(BatchAddComponentWorkRequest::getDrawingNumber)
					.filter(Objects::nonNull)
					.toList();
			StorageObjectRpcRequest objectRpcRequest = new StorageObjectRpcRequest();
			objectRpcRequest.setFileNumberList(numberData);
					Map<String, StorageObjectRpcDTO> fileNumberToMap = storageSpaceFileService.getFileNumberToMap(objectRpcRequest);
		stopWatch.stop();
		stopWatch.start(StrUtil.format("开始执行批量产品零件信息-初始化查询数据完成,进入业务循环，处理数据大小 {}", requestListData.size()));

		
		// 批量预处理检验项目数据 - 收集所有规范化后的检验项名称
		Set<String> componentItemNames = requestListData.stream()
				.map(BatchAddComponentWorkRequest::getInspectionItemNames)
				.filter(Objects::nonNull)
				.flatMap(Collection::stream)
				.collect(Collectors.toSet());
		Map<String, ProductPartQualityItemsRpcDTO> componentQualityItemsToMap = qualityItemsService.getMapByNames(componentItemNames);

		// 批量预处理物料统一编号数据
		List<String> componentUnityNoListData = requestListData.stream().map(BatchAddComponentWorkRequest::getMaterialModels).flatMap(Collection::stream).filter(Objects::nonNull)
				.map(BatchAddComponentWorkRequest.MaterialModels::getMaterialUnityNo)
				.toList();
		Map<String, ProductPartModel> componentPartUnityNoToModelMap = unifiedDataService.materialUnityNoData(componentUnityNoListData, productPartTableName);

		List<ProcedureRouteRelationshipModel> procedureRouteRelModels = new ArrayList<>();
		List<ProductPartModel> productPartModels = new ArrayList<>();
		for (BatchAddComponentWorkRequest addRpcRequest : requestListData) {
			// 生成产品、零件编号
			Long productPartCode = PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId();
			// 生成关系唯一 ID
			Long uniqueId = PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId();
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
				ProcessRouteDataModel processRouteDataModel =
						routeDataSignToModelMap.get(addRpcRequest.getProcessRouteData());
				// 组装产品零件与工艺路线关系数据
				if (processRouteDataModel != null) {
					productPartModel.setDefaultRoute(processRouteDataModel.getProcessRouteDataCode());
					ProductPartRouteRelationshipModel routeRelationshipModel = new ProductPartRouteRelationshipModel()
							.setEnterpriseCode(getEnterpriseCode()).setProductPartCode(productPartCode)
							.setProcessRouteDataCode(processRouteDataModel.getProcessRouteDataCode())
							.setState(Boolean.TRUE).setUniqueId(uniqueId);
					RequestTableHelper.setTableName(productPartRouteRelationshipTableName);
					productPartRouteRelationshipService.save(routeRelationshipModel);
				}
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
			String unityNo = addRpcRequest.getUnityNo();
			if (addRpcRequest.isGenerate()) {
				// 获取表代码-统一单号生成逻辑提取
				unityNo = productPartCommonService.getGlobalSerialUnityNo(productPartModel.getAttribute(),
						productPartModel.getProductPartTypeCode(),
						addRpcRequest.getUnityNo(), Boolean.FALSE);
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
			productPartModels.add(productPartModel);
			if (!batchAddModels.isEmpty()) {
				for (BatchAddComponentWorkRequest.MaterialModels batchAddModel : batchAddModels) {
					ProductPartModel resultModel = componentPartUnityNoToModelMap.get(batchAddModel.getMaterialUnityNo());
						if (processRouteDataModel == null || resultModel == null) {
							log.info("processRouteDataModel 数据查询空 不进行关联数据保存");
							continue;
						}
						// 组装工艺路线与工序关系数据
						LambdaQueryWrapper<ProductPartProcedureModel> queryProcedure = Wrappers.lambdaQuery();
						queryProcedure.eq(ProductPartProcedureModel::getNumber, batchAddModel.getProcedureNumber());
						queryProcedure.eq(ProductPartProcedureModel::getEnterpriseCode, getEnterpriseCode());
						queryProcedure.eq(ProductPartProcedureModel::getDeleteFlag, Boolean.FALSE);
						// 使用 stream 进行倒序排序并获取第一条记录
						RequestTableHelper.setTableName(productPartProcedure);
						Optional<ProductPartProcedureModel> productPartProcedureModel = productPartProcedureService
								.list(queryProcedure).stream().max(Comparator.comparing(ProductPartProcedureModel::getId));
						productPartProcedureModel.ifPresent(procedureModel -> {
							Long productPartProcedureCode = procedureModel.getProductPartProcedureCode();
							ProcedureRouteRelationshipModel procedureRouteRelModel =
									new ProcedureRouteRelationshipModel().setEnterpriseCode(getEnterpriseCode())
											.setProcedureCode(productPartProcedureCode)
											.setProcessRouteDataCode(processRouteDataModel.getProcessRouteDataCode())
											.setSequence(CommonConstant.NUMBER_ZERO)
											.setProductPartCode(resultModel.getProductPartCode()).setUniqueId(uniqueId);
							procedureRouteRelModels.add(procedureRouteRelModel);
						});
						// 分为 2 中情况，第一种 挂了工序号
						if (StrUtil.isNotBlank(batchAddModel.getProcedureNumber())) {
							// 组装工序和零件绑定关系数据
							LambdaQueryWrapper<ProductPartProcedureModel> qw = Wrappers.lambdaQuery();
							qw.eq(ProductPartProcedureModel::getNumber, batchAddModel.getProcedureNumber());
							qw.eq(ProductPartProcedureModel::getEnterpriseCode, getEnterpriseCode());
							qw.eq(ProductPartProcedureModel::getDeleteFlag, Boolean.FALSE);
							// 使用 stream 进行倒序排序并获取第一条记录
							RequestTableHelper.setTableName(productPartProcedure);
							Optional<ProductPartProcedureModel> optionalProductPartProcedureModel =
									productPartProcedureService.list(qw).stream()
											.max(Comparator.comparing(ProductPartProcedureModel::getId));
						optionalProductPartProcedureModel.ifPresent(procedureModel -> {
							ProcedurePartRelationshipModel procedurePartRelationshipModel =
									new ProcedurePartRelationshipModel()
											.setProductPartCode(resultModel.getProductPartCode())
											.setEnterpriseCode(getEnterpriseCode())
											.setProcedureCode(procedureModel.getProductPartProcedureCode())
											.setProcessRouteDataCode(processRouteDataModel.getProcessRouteDataCode())
											.setUniqueId(uniqueId);
							BigDecimal number = batchAddModel.getNumber();
							if (number == null || number.compareTo(BigDecimal.ZERO) < 0) {
								number = BigDecimal.ZERO;
							}
							procedurePartRelationshipModel.setQuantity(number);
							RequestTableHelper.setTableName(procedurePartRelationshipTableName);
							procedurePartRelationshipService.save(procedurePartRelationshipModel);

						});
					} else {
						BigDecimal number = batchAddModel.getNumber();
						if (number == null || number.compareTo(BigDecimal.ZERO) < 0) {
							number = BigDecimal.ZERO;
						}
						ProcedurePartRelationshipModel procedurePartRelationshipModel =
								new ProcedurePartRelationshipModel()
										.setProductPartCode(resultModel.getProductPartCode())
										.setEnterpriseCode(getEnterpriseCode()).setProcedureCode(0L)
										.setProcessRouteDataCode(processRouteDataModel.getProcessRouteDataCode())
										.setQuantity(number)
										.setUniqueId(uniqueId);
						RequestTableHelper.setTableName(procedurePartRelationshipTableName);
						procedurePartRelationshipService.save(procedurePartRelationshipModel);
					}
					}
				}
			}

			// 组装，产品零件后续续操作
			handleBatchProductPartRelationAndStockSync(procedureRouteRelModels, productPartModels, stopWatch);
			stopWatch.stop();
			log.info("\r\n开始执行批量产品零件信息-请求执行耗时：{}", stopWatch.prettyPrint(TimeUnit.MILLISECONDS));
		} catch (Exception e) {
			throw new ExceptionPack(e,
					ExceptionMsg.builder("query WorkmanshipYAction.batchAddComponentWork failed").build());
		}
	}

	/**
	 * @description 组装，产品零件后续续操作
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/29 14:52
	 * @department: Product development
	 */
	private void handleProductPartRelationAndStockSync(List<ProcedureRouteRelationshipModel> procedureRouteRelModels,
	                                                   ProductPartModel productPartModel,
	                                                   StopWatch stopWatch) {
		// 工序表与工艺路线关系表
		String procedureRouteRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProcedureRouteRelationship());
		try {
			StopWatch stockWatch = new StopWatch("stockMapper.insert");
			stockWatch.start("stockMapper.insert Start");
			log.warn("start 同步产品零件数据到仓库表 ");
			// 同步库存信息
			productPartCommonService.syncStock(productPartModel.getProductPartCode());
			stockWatch.stop();
			log.info("\r\n开始同步产品零件数据到仓库表-结束, 请求执行耗时：{} 请求入参 {}", stopWatch.prettyPrint(TimeUnit.MILLISECONDS),
					productPartModel.getProductPartCode());
		} catch (Exception e) {
			log.error("start 同步产品零件数据到仓库表 执行失败 ", e);
		}
		try (AutoCloseable ignored = RequestTableHelper::clearBatchTableName) {
			RequestTableHelper.setBatchTableName(procedureRouteRelationshipTableName);
			routeRelationshipService.saveBatch(procedureRouteRelModels);
		} catch (Exception e) {
			log.error("批量操作失败: 表名={}, 数据大小={}", procedureRouteRelationshipTableName, procedureRouteRelModels.size(), e);
		}
	}

	private void handleBatchProductPartRelationAndStockSync(List<ProcedureRouteRelationshipModel> procedureRouteRelModels,
	                                                   List<ProductPartModel> productPartModels,
	                                                   StopWatch stopWatch) {
		try {
			StopWatch stockWatch = new StopWatch("stockMapper.insert");
			stockWatch.start("stockMapper.insert Start");
			log.warn("start 同步产品零件数据到仓库表 ");
			// 同步库存信息
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
			log.info("\r\n开始同步产品零件数据到仓库表-结束, 请求执行耗时：{} 请求入参 {}", stopWatch.prettyPrint(TimeUnit.MILLISECONDS),
					productPartModels.stream().map(ProductPartModel::getProductPartCode).toList());
		} catch (Exception e) {
			log.error("start 同步产品零件数据到仓库表 执行失败 ", e);
		}

		// 工序表与工艺路线关系表
		String procedureRouteRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProcedureRouteRelationship());
		try (AutoCloseable ignored = RequestTableHelper::clearBatchTableName) {
			RequestTableHelper.setBatchTableName(procedureRouteRelationshipTableName);
			routeRelationshipService.saveBatch(procedureRouteRelModels);
		} catch (Exception e) {
			log.error("批量操作失败: 表名={}, 数据大小={}", procedureRouteRelationshipTableName, procedureRouteRelModels.size(), e);
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
		model.setUnityNo(unityNo);
		RequestTableHelper.setTableName(productPartTableName);
		productPartMapper.insert(model);
		// 深度复制-复制工艺路线和BOM明细数据
		productPartCommonService.routeProcedureCopy(model, requestData.getProductPartCode());
		// 同步库存信息
		productPartCommonService.syncStock(model.getProductPartCode());
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
		log.info("start WorkmanshipYAction.getProductMaterialList requestData {} ", JSONUtil.toJsonStr(requestData));
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
		//if (requestData.getReverseLookup() != null) {
		//	qw.and(wp -> wp.eq(ProductPartModel::getUnityNo, requestData.getReverseLookup())
		//			.or().eq(ProductPartModel::getModel, requestData.getReverseLookup()));
		//}
		// 查询不区分大小写模糊查询
		if (StrUtil.isNotBlank(requestData.getReverseLookup())) {
			String keyword = "%" + requestData.getReverseLookup().trim() + "%";
			qw.and(wp -> wp.apply("lower(unity_no) like lower({0})", keyword)
					.or().apply("lower(model) like lower({0})", keyword));
		}
		if (requestData.getProductPartCode() != null) {
			qw.eq(ProductPartModel::getProductPartCode, requestData.getProductPartCode());
		}
		// 目前只查询分类1-5的数据
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
			// 判断 下层是否有绑定数据
			LambdaQueryWrapper<ProductPartRouteRelationshipModel> queryRoute = Wrappers.lambdaQuery();
			queryRoute.eq(ProductPartRouteRelationshipModel::getProductPartCode, rpcDTO.getProductPartCode());
			queryRoute.eq(ProductPartRouteRelationshipModel::getProcessRouteDataCode, rpcDTO.getDefaultRoute());
			queryRoute.eq(ProductPartRouteRelationshipModel::getEnterpriseCode, getEnterpriseCode());
			queryRoute.eq(ProductPartRouteRelationshipModel::getDeleteFlag, Boolean.FALSE);
			RequestTableHelper.setTableName(productPartRouteRelationshipTableName);
			ProductPartRouteRelationshipModel routeRelationship =
					productPartRouteRelationshipService.list(queryRoute).stream()
							.findFirst().orElse(null);
			if (routeRelationship != null) {
				// 生成关系唯一ID
				Long uniqueId = routeRelationship.getUniqueId();
				LambdaQueryWrapper<ProcedurePartRelationshipModel> queryProcedurePart = Wrappers.lambdaQuery();
				queryProcedurePart.eq(ProcedurePartRelationshipModel::getUniqueId, uniqueId);
				queryProcedurePart.eq(ProcedurePartRelationshipModel::getEnterpriseCode, getEnterpriseCode());
				queryProcedurePart.eq(ProcedurePartRelationshipModel::getDeleteFlag, Boolean.FALSE);
				RequestTableHelper.setTableName(procedurePartRelationshipTableName);
				List<ProcedurePartRelationshipModel> resultProductPartRouteList =
						procedurePartRelationshipService.list(queryProcedurePart);
				rpcDTO.setBindData(!resultProductPartRouteList.isEmpty());
			}

			if (StringUtils.isEmpty(rpcDTO.getQuantity())) {
				rpcDTO.setQuantity("0");
			}
		}
		return new RpcPagingDTO<>(resultList, pageList.getTotal());
	}

	@Override
	public List<?> getProductMaterialRecursive(ProductPartPageRequest requestData) throws ExceptionPack {
		log.info("start WorkmanshipYAction.getProductMaterialRecursive requestData {} ", JSONUtil.toJsonStr(requestData));
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
		StopWatch stopWatch = new StopWatch("WorkmanshipYAction.getProductMaterialRecursive");
		stopWatch.start("WorkmanshipYAction.getProductMaterialRecursive Start");
		RequestTableHelper.setTableName(productPartTableName);
		ProductPartModel productPartModel = productPartMapper.selectById(requestData.getProductPartCode());
		if (productPartModel == null || productPartModel.getDefaultRoute() == null) {
			return Collections.emptyList();
		}
		LambdaQueryWrapper<ProductPartRouteRelationshipModel> queryRoute = Wrappers.lambdaQuery();
		queryRoute.eq(ProductPartRouteRelationshipModel::getProductPartCode, productPartModel.getProductPartCode());
		queryRoute.eq(ProductPartRouteRelationshipModel::getProcessRouteDataCode, productPartModel.getDefaultRoute());
		queryRoute.eq(ProductPartRouteRelationshipModel::getEnterpriseCode, getEnterpriseCode());
		queryRoute.eq(ProductPartRouteRelationshipModel::getDeleteFlag, Boolean.FALSE);
		RequestTableHelper.setTableName(productPartRouteRelationshipTableName);
		ProductPartRouteRelationshipModel routeRelationship =
				productPartRouteRelationshipService.list(queryRoute).stream().findFirst().orElse(null);
		if (routeRelationship == null) {
			return Collections.emptyList();
		}
		List<ProductMaterialRpcDTO> resultList = new ArrayList<>();
		// 生成关系唯一 ID
		Long uniqueId = routeRelationship.getUniqueId();
		LambdaQueryWrapper<ProcedurePartRelationshipModel> queryProcedurePart = Wrappers.lambdaQuery();
		queryProcedurePart.eq(ProcedurePartRelationshipModel::getUniqueId, uniqueId);
		queryProcedurePart.eq(ProcedurePartRelationshipModel::getEnterpriseCode, getEnterpriseCode());
		queryProcedurePart.eq(ProcedurePartRelationshipModel::getDeleteFlag, Boolean.FALSE);
		RequestTableHelper.setTableName(procedurePartRelationshipTableName);
		List<ProcedurePartRelationshipModel> resultProductPartRouteList =
				procedurePartRelationshipService.list(queryProcedurePart);
		for (ProcedurePartRelationshipModel relationshipModel : resultProductPartRouteList) {
		// 生成产品、零件编号
		RequestTableHelper.setTableName(productPartTableName);
		ProductPartModel resultModel = productPartMapper.selectById(relationshipModel.getProductPartCode());
		ProductMaterialRpcDTO materialRpcDTO = Convert.convert(ProductMaterialRpcDTO.class, resultModel);
		materialRpcDTO.setQuantity(relationshipModel.getQuantity() != null ? relationshipModel.getQuantity().toPlainString() : null);
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
		stopWatch.start(StrUtil.format("开始查询生产明细数据,处理数据大小 {}", productPartCodeList.size()));
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
		// 查询库存数据
		Map<Long, StockModel> stockModelMap = stockMapper.getStockByProductPartCodeInNew(productPartCodeList, getEnterpriseCode(),
						stockTableName)
				.stream().collect(Collectors.toMap(StockModel::getProductPartCode, Function.identity()));
		for (ProductMaterialRpcDTO rpcDTO : resultList) {
			rpcDTO.setOnWayNum(productPartCodeToOnWayNumMap.getOrDefault(rpcDTO.getProductPartCode(), BigDecimal.ZERO));
			rpcDTO.setProduceNum(productPartCodeToTaskNumMap.getOrDefault(rpcDTO.getProductPartCode(), BigDecimal.ZERO));
			StockModel stockModel = stockModelMap.get(rpcDTO.getProductPartCode());
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
//			}

			if (StringUtils.isEmpty(rpcDTO.getQuantity())) {
				rpcDTO.setQuantity("0");
			}
			// 组装 计量单位名称
			if (rpcDTO.getPcs() != null) {
				rpcDTO.setPcsName(pieceCodeNameMapping.get(rpcDTO.getPcs()));
			}
			if (rpcDTO.getDefaultRoute() != null) {
				LambdaQueryWrapper<ProductPartRouteRelationshipModel> queryRouteNext = Wrappers.lambdaQuery();
				queryRouteNext.eq(ProductPartRouteRelationshipModel::getProductPartCode, rpcDTO.getProductPartCode())
						.eq(ProductPartRouteRelationshipModel::getProcessRouteDataCode, rpcDTO.getDefaultRoute())
						.eq(ProductPartRouteRelationshipModel::getEnterpriseCode, getEnterpriseCode())
						.eq(ProductPartRouteRelationshipModel::getDeleteFlag, Boolean.FALSE);
				RequestTableHelper.setTableName(productPartRouteRelationshipTableName);
				ProductPartRouteRelationshipModel routeRelationshipNext =
						productPartRouteRelationshipService.list(queryRouteNext).stream()
								.findFirst().orElse(null);
				if (routeRelationshipNext != null) {
					// 生成关系唯一 ID
					Long uniqueIdNext = routeRelationshipNext.getUniqueId();
					LambdaQueryWrapper<ProcedurePartRelationshipModel> nextQw = Wrappers.lambdaQuery();
					nextQw.eq(ProcedurePartRelationshipModel::getUniqueId, uniqueIdNext);
					nextQw.eq(ProcedurePartRelationshipModel::getEnterpriseCode, getEnterpriseCode());
					nextQw.eq(ProcedurePartRelationshipModel::getDeleteFlag, Boolean.FALSE);
					RequestTableHelper.setTableName(procedurePartRelationshipTableName);
					List<ProcedurePartRelationshipModel> resultRouteListNext =
							procedurePartRelationshipService.list(nextQw);
					rpcDTO.setBindData(!resultRouteListNext.isEmpty());
				}
			}
		}
		stopWatch.stop();
		log.info("\r\n开始产品查询-结束, 请求执行耗时：{} 请求入参 {}", stopWatch.prettyPrint(TimeUnit.MILLISECONDS),
				productPartModel.getProductPartCode());
		return resultList;
	}
}
