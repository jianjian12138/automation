/*
 * Copyright (c) Zhongzao Software Co. LTD 2022-2062 All rights reserved
 */

package com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.StopWatch;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.futurecraftsmen.pms.api.domain.ServiceErrorCode;
import com.futurecraftsmen.pms.api.dto.MultipartFileRpcDTO;
import com.futurecraftsmen.pms.api.dto.base.RpcPagingDTO;
import com.futurecraftsmen.pms.common.domain.StaffOperationLogTypeEnum;
import com.futurecraftsmen.pms.common.domain.excel.ExcelHead;
import com.futurecraftsmen.pms.common.domain.excel.ParseExcelResult;
import com.futurecraftsmen.pms.common.domain.exception.ExcelException;
import com.futurecraftsmen.pms.common.excel.ExcelModelsProcessor;
import com.futurecraftsmen.pms.common.excel.multi.ExcelMultiModelsProcessor;
import com.futurecraftsmen.pms.dm.api.service.base.staff.StaffService;
import com.futurecraftsmen.pms.dm.api.service.base.staff.dto.StaffRpcDTO;
import com.futurecraftsmen.pms.dp.api.service.GlobalNumberServer;
import com.futurecraftsmen.pms.file.api.service.EnterpriseStorageSpaceFileService;
import com.futurecraftsmen.pms.file.api.service.dto.EnterpriseStorageSpaceFileDetailRpcDTO;
import com.futurecraftsmen.pms.file.api.service.dto.EnterpriseStorageSpaceFileDetailsRpcDTO;
import com.futurecraftsmen.pms.file.api.service.dto.StorageObjectRpcDTO;
import com.futurecraftsmen.pms.file.api.service.dto.StorageObjectRpcRequest;
import com.futurecraftsmen.pms.right.api.service.RightCharacteristicsJudgeService;
import com.futurecraftsmen.pms.service.configuration.MyBatisDynamicTableNameFactory;
import com.futurecraftsmen.pms.service.configuration.RequestTableHelper;
import com.futurecraftsmen.pms.service.domain.BaseExcelHeaderGenerator;
import com.futurecraftsmen.pms.service.domain.common.constant.CommonConstant;
import com.futurecraftsmen.pms.service.domain.extract.ExtractUtil;
import com.futurecraftsmen.pms.service.util.CommonUtil;
import com.futurecraftsmen.pms.starter.domain.starter.PmsStarter;
import com.futurecraftsmen.pms.technical.api.domain.product.ProductExcelModel;
import com.futurecraftsmen.pms.technical.api.domain.technical.procedure.ProductPartProcedureDetailRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.procedure.ProductPartProcedureRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.process.ProcessRouteDataDetailRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.process.ProcessRouteDataRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.process.ProcessRouteNodeModel;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.*;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.excel.product.PartDetailExcelRpcModel;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.excel.product.PartUpdateRpcModel;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.excel.product.ProductDetailExcelRpcModel;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.relationship.RelationshipRpcRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.xihang.ProductPartConsistDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.xihang.comp.ProductPartCompRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.type.ProductPartTreeNodeRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.type.ProductPartTypeRpcRequest;
import com.futurecraftsmen.pms.technical.api.service.IPiecesService;
import com.futurecraftsmen.pms.technical.api.service.technical.IProductPartService;
import com.futurecraftsmen.pms.technical.service.anno.OptRecord;
import com.futurecraftsmen.pms.technical.service.common.enums.TechnicalErrorEnum;
import com.futurecraftsmen.pms.technical.service.config.EnumCodeConfig;
import com.futurecraftsmen.pms.technical.service.dao.technical.IProductPartMapper;
import com.futurecraftsmen.pms.technical.service.dao.technical.IProductPartProcedureMapper;
import com.futurecraftsmen.pms.technical.service.dao.warehouse.IStockMapper;
import com.futurecraftsmen.pms.technical.service.domain.ProductBatchImportExcelValidator;
import com.futurecraftsmen.pms.technical.service.domain.technical.procedure.ProcedurePartRelationshipModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.procedure.ProcedureRouteRelationshipModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.procedure.ProductPartProcedureModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.process.ProcessRouteDataModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.productpart.ProductPartModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.productpart.ProductPartRouteRelationshipModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.productpart.detail.PartDetailExcelValidator;
import com.futurecraftsmen.pms.technical.service.domain.technical.productpart.detail.PartUpdateExcelValidator;
import com.futurecraftsmen.pms.technical.service.domain.technical.productpart.detail.ProductDetailExcelValidator;
import com.futurecraftsmen.pms.technical.service.domain.warehouse.StockModel;
import com.futurecraftsmen.pms.technical.service.impl.collaborate.base.CollaborateScheduleItemServiceImpl;
import com.futurecraftsmen.pms.technical.service.impl.inner.TableNameFactory;
import com.futurecraftsmen.pms.technical.service.impl.technical.ProductPartTypeServiceImpl;
import com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart.action.unified.IProductPartCommonServiceImpl;
import com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart.action.unified.TechnicalUnifiedDataService;
import com.futurecraftsmen.pms.technical.service.impl.technical.procedure.ProductPartProcedureServiceImpl;
import com.futurecraftsmen.pms.technical.service.impl.technical.relationship.ProcedurePartRelationshipServiceImpl;
import com.futurecraftsmen.pms.technical.service.impl.technical.relationship.ProcedureRouteRelationshipServiceImpl;
import com.futurecraftsmen.pms.technical.service.impl.technical.relationship.ProductPartRouteRelationshipServiceImpl;
import com.futurecraftsmen.pms.technical.service.impl.technical.route.ProcessRouteDataServiceImpl;
import com.futurecraftsmen.pms.technical.service.util.RouteActionDto;
import com.futurecraftsmen.pms.technical.service.util.RouteLink;
import com.futurecraftsmen.pms.technical.service.util.RouteLinkUtils;
import com.futurecraftsmen.pms.technical.service.util.StringUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.aerie.forest.core.brick.assertprocess.AssertInjecter;
import org.aerie.forest.core.brick.assertprocess.judge.judgement.shelf.ObjectJudgementShelf;
import org.aerie.forest.core.brick.domain.enums.PPAttributeEnum;
import org.aerie.forest.core.brick.exception.ExceptionMsg;
import org.aerie.forest.core.brick.exception.ExceptionPack;
import org.aerie.forest.core.brick.exception.assertprocess.AssertException;
import org.aerie.forest.core.brick.processor.datapenetrate.InfoPenetrateProcessor;
import org.aerie.forest.core.brick.rightcharacteristics.RightCharacteristics;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.aop.framework.AopContext;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.futurecraftsmen.pms.technical.service.util.CommonUtil.getEnterpriseCode;
import static org.aerie.forest.core.brick.processor.datapenetrate.GlobalTokenPenetrate.ENTERPRISE_CODE_THREAD_LOCAL;

/**
 * 产品零件服务
 *
 * @author qierkang
 * @date Created in 2024/11/16 下午3:33
 * @title IProductPartServiceImpl.java Department: IProductPartServiceImpl
 */
@Slf4j
@DubboService(group = "pms")
public class IProductPartServiceImpl extends ServiceImpl<IProductPartMapper, ProductPartModel>
		implements IProductPartService {

	@Resource
	private TableNameFactory tableFactory;
	@Resource
	private IProductPartMapper productPartMapper;
	@Resource
	private IPiecesService piecesService;
	@Resource
	private ProductDetailExcelValidator productDetailExcelValidator;
	@Resource
	private PartDetailExcelValidator partDetailExcelValidator;
	@Resource
	private PartUpdateExcelValidator partUpdateExcelValidator;
	@Resource
	private TechnicalUnifiedDataService unifiedDataService;
	@Resource
	private ProductBatchImportExcelValidator productBatchImportExcelValidator;
	@Resource
	private ProductPartRouteRelationshipServiceImpl productPartRouteRelationshipService;
	@Resource
	private ProcedurePartRelationshipServiceImpl procedurePartRelationshipService;
	@Resource
	private ProcedureRouteRelationshipServiceImpl routeRelationshipService;
	@Resource
	private ProcessRouteDataServiceImpl processRouteDataService;
	@Resource
	private ProductPartProcedureServiceImpl productPartProcedureService;
	// 员工业务
	@DubboReference(check = false, retries = 0)
	private StaffService staffService;
	@Resource
	private IStockMapper stockMapper;
	@Resource
	private IProductPartProcedureMapper productPartProcedureMapper;
	@Resource
	private ThreadPoolExecutor asyncTaskExecutor;

	@Resource
	private EnumCodeConfig enumCodeConfig;
	@Resource
	private IProductPartCommonServiceImpl productPartCommonService;
	@DubboReference(check = false)
	private RightCharacteristicsJudgeService rightCharacteristicsJudgeService;
	@Resource
	private MyBatisDynamicTableNameFactory myBatisDynamicTableNameFactory;
	@Resource
	private ProductPartTypeServiceImpl productPartTypeService;
	@DubboReference(group = "pms", check = false, retries = 0)
	private EnterpriseStorageSpaceFileService storageSpaceFileService;
	@DubboReference(check = false, retries = 0)
	private GlobalNumberServer globalNumberServer;
	@Resource
	private CollaborateScheduleItemServiceImpl collaborateScheduleItemService;

	//@Override
	//public RpcPagingDTO<ProductPartRpcDTO> getPageList(ProductPartPageRequest requestData) throws ExceptionPack {
	//	try {
	//		// 新技术部-产品零件表
	//		String productPartTableName =
	//				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
	//		// 产品零件与工艺路线关系表
	//		String productPartRouteRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
	//				tableFactory.table.getProductPartRouteRelationship());
	//		// 工艺路线管理表
	//		String processRouteDataTableName =
	//				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProcessRouteData());
	//		LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
	//		if (requestData.getProductPartCode() != null) {
	//			qw.eq(ProductPartModel::getProductPartCode, requestData.getProductPartCode());
	//		}
	//		if (requestData.getProductPartTypeCode() != null) {
	//			ProductPartTypeRpcRequest typeRpcRequest =
	//					new ProductPartTypeRpcRequest().setProductPartTypeCode(requestData.getProductPartTypeCode());
	//			List<ProductPartTreeNodeRpcDTO> productPartTreeNodeRpcDTOS = new ArrayList<>();
	//			if (ObjectUtil.isNotEmpty(typeRpcRequest)) {
	//				productPartTreeNodeRpcDTOS = productPartTypeService.findProductPartTypeChildren(typeRpcRequest);
	//			}
	//			if (ObjectUtil.isNotEmpty(productPartTreeNodeRpcDTOS)) {
	//				List<Long> productPartTypeList =
	//						productPartTreeNodeRpcDTOS.stream().map(ProductPartTreeNodeRpcDTO::getProductPartTypeCode).toList();
	//				qw.in(ProductPartModel::getProductPartTypeCode, productPartTypeList);
	//			} else {
	//				qw.eq(ProductPartModel::getProductPartTypeCode, requestData.getProductPartTypeCode());
	//			}
	//		}
	//		if (StrUtil.isNotBlank(requestData.getName())) {
	//			qw.like(ProductPartModel::getProductPartSign, requestData.getName());
	//		}
	//		if (StrUtil.isNotBlank(requestData.getModel())) {
	//			qw.like(ProductPartModel::getModel, requestData.getModel());
	//		}
	//		if (requestData.getState() != null) {
	//			qw.eq(ProductPartModel::getState, requestData.getState());
	//		}
	//		if (requestData.getAttribute() != null) {
	//			qw.eq(ProductPartModel::getAttribute, requestData.getAttribute());
	//		}
	//		if (ObjectUtil.isNotEmpty(requestData.getStandard())) {
	//			qw.eq(ProductPartModel::getStandard, requestData.getStandard());
	//		}
	//		// 普通-聚合搜索-名称+型号
	//		if (StrUtil.isNotBlank(requestData.getSearchName())) {
	//			qw.and(wp -> wp.like(ProductPartModel::getProductPartSign, requestData.getSearchName())
	//					.or().like(ProductPartModel::getModel, requestData.getSearchName()));
	//		}
	//
	//		if (StringUtils.isNotBlank(requestData.getPartUnityNo())) {
	//			qw.like(ProductPartModel::getUnityNo, requestData.getPartUnityNo());
	//		}
	//
	//		if (StrUtil.isNotBlank(requestData.getSearchName())) {
	//			qw.and(wp -> wp.like(ProductPartModel::getProductPartSign, requestData.getSearchName())
	//					.or().like(ProductPartModel::getModel, requestData.getSearchName()));
	//		}
	//		qw.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode());
	//		qw.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE).orderByDesc(ProductPartModel::getId);
	//		Page<ProductPartModel> page = new Page<>(requestData.getCurrent(), requestData.getSize());
	//		RequestTableHelper.setTableName(productPartTableName);
	//		Page<ProductPartModel> pageList = this.page(page, qw);
	//		List<ProductPartModel> resultModelList = Convert.toList(ProductPartModel.class, pageList.getRecords());
	//
	//		// 计量单位数据准备
	//		Set<Long> productPiecesCodes =
	//				new HashSet<>(ExtractUtil.streamMapToList(Long::valueOf, resultModelList, ProductPartModel::getPcs));
	//		// 获取产品与名称映射
	//		Map<Long, String> pieceCodeNameMapping =
	//				piecesService.pieceCodeChnNameMapping(productPiecesCodes, getEnterpriseCode());
	//		// 员工信息准备
	//		List<Long> staffCodes = ExtractUtil.streamMapToList(Long::valueOf, resultModelList,
	//				ProductPartModel::getContactPerson, ProductPartModel::getCreator, ProductPartModel::getWarehousePerson);
	//
	//		// 获取员工与名称映射
	//		Map<Long, String> resultStaffMap = new HashMap<>();
	//		if (!staffCodes.isEmpty()) {
	//			resultStaffMap = staffService.queryStaffByStaffCodes(staffCodes, getEnterpriseCode()).stream()
	//					.collect(Collectors.toMap(StaffRpcDTO::getStaffCode, StaffRpcDTO::getStaffName,
	//							(existingValue, newValue) -> newValue));
	//		}
	//
	//		// 获取文件ID列表
	//		List<Long> getFileIdList = resultModelList.stream().map(ProductPartModel::getFiles).filter(Objects::nonNull)
	//				.flatMap(Arrays::stream).collect(Collectors.toList());
	//		Map<Long, EnterpriseStorageSpaceFileDetailRpcDTO> resultFileMap = new HashMap<>();
	//		if (!getFileIdList.isEmpty()) {
	//			// 获取文件详细信息
	//			EnterpriseStorageSpaceFileDetailsRpcDTO getFileInfoResult =
	//					enterpriseStorageSpaceFileService.details(getFileIdList, getEnterpriseCode());
	//			// 使用 Optional 处理可能的空值情况
	//			Optional.ofNullable(getFileInfoResult)
	//					.map(EnterpriseStorageSpaceFileDetailsRpcDTO::getDetails)
	//					.ifPresent(details ->
	//							resultFileMap.putAll(details.stream()
	//									.collect(Collectors.toMap(EnterpriseStorageSpaceFileDetailRpcDTO::getFileId, Function.identity())))
	//					);
	//		}
	//		// 转换为 ProductPartRpcDTO 列表
	//		List<ProductPartRpcDTO> data = Convert.toList(ProductPartRpcDTO.class, resultModelList);
	//
	//		GlobalSerialNumberRequest gr = new GlobalSerialNumberRequest();
	//
	//		if (requestData.getAttribute() != null && requestData.getAttribute() == 1) {
	//			gr.setTableCode(enCodePropertiesConfig.getProductCode());
	//		} else {
	//			gr.setTableCode(enCodePropertiesConfig.getPartCode());
	//		}
	//
	//		GlobalSerialNumberResponse res = globalNumberServer.serialNumberQuery(gr);
	//
	//		for (ProductPartRpcDTO rpcDTO : data) {
	//			rpcDTO.setPartUnityNo(rpcDTO.getUnityNo());
	//			rpcDTO.setName(rpcDTO.getProductPartSign());
	//			rpcDTO.setGenerateWay(res.getGenerateWay());
	//			rpcDTO.setPicSize(res.getPicSize());
	//			// 组装 计量单位名称
	//			if (rpcDTO.getPcs() != null) {
	//				rpcDTO.setPcsName(pieceCodeNameMapping.get(rpcDTO.getPcs()));
	//			}
	//			// 组装 技术对接人
	//			if (rpcDTO.getContactPerson() != null) {
	//				rpcDTO.setContactPersonName(resultStaffMap.get(rpcDTO.getContactPerson()));
	//			}
	//			// 组装 创建人
	//			if (rpcDTO.getCreator() != null) {
	//				rpcDTO.setCreatorName(resultStaffMap.get(rpcDTO.getCreator()));
	//			}
	//			// 组装 文件信息
	//			List<EnterpriseStorageSpaceFileDetailRpcDTO> fileInfoList = Arrays.stream(rpcDTO.getFiles())
	//					.map(resultFileMap::get).filter(Objects::nonNull).collect(Collectors.toList());
	//			rpcDTO.setFileInfoList(fileInfoList);
	//			// 组装 仓库负责人
	//			if (rpcDTO.getWarehousePerson() != null) {
	//				rpcDTO.setWarehousePersonName(resultStaffMap.get(rpcDTO.getWarehousePerson()));
	//			}
	//			// 组装工艺路线名称
	//			if (rpcDTO.getDefaultRoute() != null) {
	//				RequestTableHelper.setTableName(processRouteDataTableName);
	//				ProcessRouteDataModel processRouteDataModel = processRouteDataService.getById(rpcDTO.getDefaultRoute());
	//				if (processRouteDataModel != null) {
	//					rpcDTO.setProcessRouteDataCode(rpcDTO.getDefaultRoute());
	//					rpcDTO.setProcessRouteName(processRouteDataModel.getProcessRouteDataSign());
	//				}
	//				LambdaQueryWrapper<ProductPartRouteRelationshipModel> queryRoute = Wrappers.lambdaQuery();
	//				queryRoute.eq(ProductPartRouteRelationshipModel::getProductPartCode, rpcDTO.getProductPartCode());
	//				queryRoute.eq(ProductPartRouteRelationshipModel::getProcessRouteDataCode, rpcDTO.getDefaultRoute());
	//				queryRoute.eq(ProductPartRouteRelationshipModel::getEnterpriseCode, getEnterpriseCode());
	//				queryRoute.eq(ProductPartRouteRelationshipModel::getDeleteFlag, Boolean.FALSE);
	//				RequestTableHelper.setTableName(productPartRouteRelationshipTableName);
	//				ProductPartRouteRelationshipModel routeRelationship =
	//						productPartRouteRelationshipService.list(queryRoute).stream()
	//								.findFirst().orElse(null);
	//				if (routeRelationship != null) {
	//					ProductPartProcedureRpcRequest requestProcedureData = new ProductPartProcedureRpcRequest();
	//					requestProcedureData.setProcessRouteDataCode(rpcDTO.getDefaultRoute());
	//					requestProcedureData.setUniqueId(routeRelationship.getUniqueId());
	//					List<ProcedurePartDTO> procedurePartList = productPartProcedureService.getProcedurePartList(requestProcedureData);
	//					// 组装工艺路线当前绑定的零件信息
	//					rpcDTO.setProcedurePartList(procedurePartList);
	//					rpcDTO.setProcessRouteStatus(routeRelationship.getState() == null ? Boolean.FALSE : routeRelationship.getState());
	//				}
	//			}
	//			// 组装返回 检验准则 模型
	//			if (ObjectUtil.isNotEmpty(rpcDTO.getQualityCodeList())) {
	//				ProductPartQualityItemsRpcRequest itemsRpcRequest = new ProductPartQualityItemsRpcRequest();
	//				itemsRpcRequest.setProductPartQualityCodeList(Arrays.stream(rpcDTO.getQualityCodeList()).toList());
	//				List<ProductPartQualityItemsRpcDTO> itemsRpcDTOS = qualityItemsService.getProductPartQualityItemsList(itemsRpcRequest);
	//				rpcDTO.setQualityItemsInfoList(itemsRpcDTOS);
	//			}
	//			rpcDTO.setExtraInfo(JSONUtil.toBean(rpcDTO.getExtra(), ProductPartExtraInfo.class));
	//		}
	//		return new RpcPagingDTO<>(data, pageList.getTotal());
	//	} catch (Exception e) {
	//		throw new ExceptionPack(e, ExceptionMsg.builder("query IProductPartService.getPageList failed").build());
	//	}
	//}
	//
	//@Override
	//@Transactional(rollbackFor = Exception.class)
	//public ProductPartRpcDTO addProductPart(ProductPartAddRpcRequest requestData) throws AssertException {
	//	// 新技术部-产品零件表
	//	String productPartTableName =
	//			tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
	//	// 新技术部-产品零件分类表
	//	String productPartTypeTableName =
	//			tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPartType());
	//	// 生成产品、零件编号
	//	Long productPartCode = PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId();
	//	// 如果是产品：产品名称可以重复、产品型号不可以重复
	//	// 如果是零件：零件名称+型号，唯一
	//	productPartCommonService.validateProductOrPartAdd(requestData, productPartTableName);
	//	// 验证产品类型是否存在
	//	productPartCommonService.validateProductPartTypeAdd(requestData, productPartTypeTableName);
	//	// 获取表代码-统一单号生成逻辑提取
	//	String unityNo = productPartCommonService.getGlobalSerialUnityNo(requestData.getAttribute(),
	//			requestData.getProductPartTypeCode(),
	//			requestData.getUnityNo());
	//	ProductPartModel model = Convert.convert(ProductPartModel.class, requestData)
	//			.setProductPartCode(productPartCode)
	//			.setProductPartSign(requestData.getName()).setEnterpriseCode(getEnterpriseCode()).setCreator(getUserCode())
	//			.setSyncStatus(CommonConstant.NUMBER_ZERO).setUpdateCount(CommonConstant.NUMBER_ZERO)
	//			.setEnterTime(DateUtil.date()).setUnityNo(unityNo);
	//	RequestTableHelper.setTableName(productPartTableName);
	//	productPartMapper.insert(model);
	//	// 同步库存信息
	//	productPartCommonService.syncStock(model.getProductPartCode());
	//	return Convert.convert(ProductPartRpcDTO.class, model);
	//}
	//
	//@Override
	//@Transactional(rollbackFor = Exception.class)
	//public void updateProductPart(ProductPartUpdateRpcRequest requestData) throws AssertException {
	//	String productPartTableName =
	//			tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
	//	String productPartTypeTableName =
	//			tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPartType());
	//	// 产品零件与工艺路线关系表
	//	String productPartRouteRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
	//			tableFactory.table.getProductPartRouteRelationship());
	//	// 验证产品类型是否存在
	//	productPartCommonService.validateProductPartTypeUpdate(requestData, productPartTypeTableName);
	//	// 查询当前修改次数
	//	RequestTableHelper.setTableName(productPartTableName);
	//	ProductPartModel productPartModel = productPartMapper.selectById(requestData.getProductPartCode());
	//	// 如果是产品：产品名称可以重复、产品型号不可以重复
	//	// 如果是零件：零件名称+型号，唯一
	//	productPartCommonService.validateProductOrPartUpdate(productPartModel, requestData, productPartTableName);
	//	productPartModel.setSyncStatus(CommonConstant.NUMBER_TWO).setUpdateCount(productPartModel.getUpdateCount() + 1).setProductPartSign
	//	(requestData.getName());
	//	BeanUtil.copyProperties(requestData, productPartModel, "files");
	//	if (requestData.getSwitchRoute() == null || Boolean.FALSE.equals(requestData.getSwitchRoute())) {
	//		productPartModel.setFiles(requestData.getFiles());
	//	}
	//	RequestTableHelper.setTableName(productPartTableName);
	//	productPartMapper.updateById(productPartModel);
	//	if (requestData.getDefaultRoute() != null) {
	//		LambdaUpdateWrapper<ProductPartRouteRelationshipModel> relationshipUw = Wrappers.lambdaUpdate();
	//		relationshipUw.set(ProductPartRouteRelationshipModel::getState, Boolean.TRUE);
	//		relationshipUw.eq(ProductPartRouteRelationshipModel::getProductPartCode, requestData.getProductPartCode());
	//		relationshipUw.eq(ProductPartRouteRelationshipModel::getProcessRouteDataCode, requestData.getDefaultRoute());
	//		relationshipUw.eq(ProductPartRouteRelationshipModel::getDeleteFlag, Boolean.FALSE);
	//		relationshipUw.eq(ProductPartRouteRelationshipModel::getEnterpriseCode, getEnterpriseCode());
	//		RequestTableHelper.setTableName(productPartRouteRelationshipTableName);
	//		productPartRouteRelationshipService.update(relationshipUw);
	//	}
	//}
	//
	//@Override
	//public void deleteProductPart(ProductPartRpcRequest requestData) throws AssertException {
	//	// 校验产品零件是否被调度安排过
	//	AssertInjecter.inject(productPartCommonService.checkDeleteProductPartDispatch(requestData.getProductPartCode()),
	//					"checkDeleteProductPartDispatch").and(BooleanJudgementShelf.IS_TRUE)
	//			.judge(ExceptionMsg.builder("This data has already been bound to the process route")
	//					.msgView("已经被调度安排啦，无法删除～").build());
	//	// 校验产品是否有已签章的销售合同/采购合同涉及到
	//	// 检查零件是否有已签章的采购合同涉及到
	//	AssertInjecter.inject(productPartCommonService.checkDeleteProductPartNonSignedContract(requestData.getProductPartCode()),
	//					"checkDeleteProductPartNonSignedContract").and(BooleanJudgementShelf.IS_TRUE)
	//			.judge(ExceptionMsg.builder("This data has already using in signed contract")
	//					.msgView("已经在签章合同的清单中啦，无法删除～").build());
	//
	//	// 校验产品零件是否被工序绑定
	//	AssertInjecter.inject(productPartCommonService.checkDeleteProductPartRelationship(requestData.getProductPartCode()),
	//					"deleteProductPart").and(BooleanJudgementShelf.IS_FALSE)
	//			.judge(ExceptionMsg.builder("The matching parts have already been bound. If you need to delete them, please clear the parts in " +
	//							"[details] first")
	//					.msgView("已经绑定配套零件啦，如需删除，请先将[详情]内的零件清除～").build());
	//
	//	// 查询工艺路线下面工序是否空
	//	AssertInjecter.inject(productPartCommonService.detailProcessEmpty(new ProductPartProcedureRpcRequest().setProductPartCode(requestData
	//	.getProductPartCode())),
	//					"deleteProductPart").and(BooleanJudgementShelf.IS_FALSE)
	//			.judge(ExceptionMsg.builder("The matching parts have already been bound. If you need to delete them, please clear the parts in " +
	//							"[details] first")
	//					.msgView("已经被工序绑定啦，如需删除，请先将相关的绑定关系清除～").build());
	//
	//	// 校验产品零件是否有库存
	//	AssertInjecter.inject(productPartCommonService.checkDeleteStockModel(requestData.getProductPartCode()),
	//					"deleteProductPart").and(BooleanJudgementShelf.IS_FALSE)
	//			.judge(ExceptionMsg.builder("The inventory level has already been set in the warehouse and cannot be deleted")
	//					.msgView("已经在仓库设置过库存量啦，无法删除～").build());
	//	String productPartTableName =
	//			tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
	//	LambdaUpdateWrapper<ProductPartModel> uw = Wrappers.lambdaUpdate();
	//	uw.eq(ProductPartModel::getProductPartCode, requestData.getProductPartCode());
	//	uw.set(ProductPartModel::getDeleteFlag, Boolean.TRUE);
	//	RequestTableHelper.setTableName(productPartTableName);
	//	productPartMapper.update(uw);
	//	try {
	//		// 手动设置企业代码到当前线程
	//		String stockTableName =
	//				tableFactory.getTableName(tableFactory.module.getWarehouse(), tableFactory.table.getStock());
	//		log.info("start 同步删除产品零件关联数据 {}", requestData.getProductPartCode());
	//		LambdaUpdateWrapper<StockModel> smQw = Wrappers.lambdaUpdate();
	//		smQw.set(StockModel::getDeleteFlag, Boolean.TRUE)
	//				.eq(StockModel::getProductPartCode, requestData.getProductPartCode());
	//		//新增同步库存数据
	//		RequestTableHelper.setTableName(stockTableName);
	//		stockMapper.update(smQw);
	//		// 删除关系数据
	//		productPartCommonService.deleteRelationship(requestData.getProductPartCode());
	//	} catch (Exception e) {
	//		log.error("error 同步删除产品零件关联数据 执行失败 ", e);
	//	}
	//}

	@Override
	public ParseExcelResult<ProductExcelModel> parseExcel(MultipartFileRpcDTO excelFile) throws ExceptionPack {
		try {
			return ExcelModelsProcessor.INSTANCE.parseExcelModels(new ByteArrayInputStream(excelFile.getBytes()),
					ProductExcelModel.class, productBatchImportExcelValidator, null, new BaseExcelHeaderGenerator());
		} catch (ExcelException e) {
			throw new ExceptionPack(e, ExceptionMsg.builder(e.getMessage())
					.msgViewAndResCode(e.getMsgView(), ServiceErrorCode.BATCH_IMPORT_FILE_ILLEGAL.getCode()).build());
		}
	}

	@Override
	public void deleteProductPartFiles(Long productPartCode) throws ExceptionPack {
		try {
			asyncTaskExecutor.execute(() -> {
				try {
					StopWatch stopWatch = new StopWatch("deleteProductPartFiles");
					stopWatch.start("deleteProductPartFiles Start");
					log.warn("start 删除产品零件文件 ");
					String productPartTableName =
							tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
					RequestTableHelper.setTableName(productPartTableName);
					ProductPartModel productPartModel =
							AssertInjecter.inject(productPartMapper.selectById(productPartCode), "productModel")
									.and(ObjectJudgementShelf.NOT_NULL)
									.judgeAndGet(ExceptionMsg.builder("product not found").msgView("产品不存在").build());
					ProductPartFilesRpcDTO files = new ProductPartFilesRpcDTO();
					if (ObjectUtil.isNotEmpty(productPartModel.getFiles()) &&
							productPartModel.getFiles().length > CommonConstant.NUMBER_ZERO) {
						files.setFileCodes(Arrays.stream(productPartModel.getFiles()).toList());
					}
					stopWatch.stop();
					log.info("start 删除产品零件文件 结束, 请求执行耗时：{}", stopWatch.prettyPrint());
				} catch (Exception e) {
					log.error("start 删除产品零件文件 执行失败 ", e);
				}
			});
		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("fail to query product files").build());
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void bindingQualityItems(ProductPartUpdateRpcRequest requestData) {
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
		LambdaUpdateWrapper<ProductPartModel> uw = Wrappers.lambdaUpdate();
		uw.eq(ProductPartModel::getProductPartCode, requestData.getProductPartCode());
		uw.set(ProductPartModel::getQualityCodeList, requestData.getQualityCodeList().toArray(new Long[0]));
		RequestTableHelper.setTableName(productPartTableName);
		this.update(uw);
	}

	@Override
	public ProductPartDetailRpcDTO productPartDetail(ProductPartRpcRequest requestData) throws AssertException {
		// 新技术部-产品零件表
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
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
		String productPartProcedureTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProductPartProcedure());
		// 查询产品、零件信息
		RequestTableHelper.setTableName(productPartTableName);
		ProductPartModel productPartModel = productPartMapper.selectById(requestData.getProductPartCode());
		// 查询的信息不存在，请检查
		if (null == productPartModel) {
			throw new AssertException(ExceptionMsg.builder("")
					.msgView(TechnicalErrorEnum.TECHNICAL_PRODUCT_PART_EXIST_MESSAGE.getMsg()).build());
		}
		// 查询产品、零件 code
		ProductPartDetailRpcDTO detailRpcDTO = Convert.convert(ProductPartDetailRpcDTO.class, productPartModel);
		LambdaQueryWrapper<ProductPartRouteRelationshipModel> queryProductPartRoute = Wrappers.lambdaQuery();
		queryProductPartRoute.eq(ProductPartRouteRelationshipModel::getProductPartCode,
				detailRpcDTO.getProductPartCode());
		detailRpcDTO.setDefaultRoute(productPartModel.getDefaultRoute());
		queryProductPartRoute.eq(ProductPartRouteRelationshipModel::getEnterpriseCode, getEnterpriseCode());
		queryProductPartRoute.eq(ProductPartRouteRelationshipModel::getDeleteFlag, Boolean.FALSE);
		// 查询产品、零件和工艺路线绑定关系
		RequestTableHelper.setTableName(productPartRouteRelationshipTableName);
		List<ProductPartRouteRelationshipModel> resultProductPartRouteList =
				productPartRouteRelationshipService.list(queryProductPartRoute);
		List<ProcessRouteDataDetailRpcDTO> processRouteDataLists = new ArrayList<>();
		List<Long> uniqueIdList =
				resultProductPartRouteList.stream().distinct().map(ProductPartRouteRelationshipModel::getUniqueId).toList();
		for (Long uniqueId : uniqueIdList) {
			// 查询工艺路线和工序绑定关系
			LambdaQueryWrapper<ProductPartRouteRelationshipModel> queryProcedureRoute = Wrappers.lambdaQuery();
			queryProcedureRoute.eq(ProductPartRouteRelationshipModel::getUniqueId, uniqueId);
			queryProcedureRoute.eq(ProductPartRouteRelationshipModel::getEnterpriseCode, getEnterpriseCode());
			queryProcedureRoute.eq(ProductPartRouteRelationshipModel::getDeleteFlag, Boolean.FALSE);
			RequestTableHelper.setTableName(productPartRouteRelationshipTableName);
			ProductPartRouteRelationshipModel routeRelationship =
					productPartRouteRelationshipService.list(queryProcedureRoute).stream().findFirst().orElse(null);
			ProcessRouteDataDetailRpcDTO dataDetailRpcDTO = new ProcessRouteDataDetailRpcDTO();
			if (routeRelationship != null) {
				RequestTableHelper.setTableName(processRouteDataTableName);
				ProcessRouteDataModel processRouteDataModel =
						processRouteDataService.getById(routeRelationship.getProcessRouteDataCode());
				ProcessRouteDataDetailRpcDTO processRouteDataDetailRpcDTO =
						Convert.convert(ProcessRouteDataDetailRpcDTO.class, processRouteDataModel);
				BeanUtil.copyProperties(processRouteDataDetailRpcDTO, dataDetailRpcDTO);
				// 解析组装工序编号信息
				dataDetailRpcDTO.setRouteNodeResult(productPartCommonService.routeNodeModels(processRouteDataDetailRpcDTO.getRouteNode()));
				// 根据工艺路线查询工序ID
				LambdaQueryWrapper<ProcedureRouteRelationshipModel> queryProcedure = Wrappers.lambdaQuery();
				queryProcedure.eq(ProcedureRouteRelationshipModel::getProcessRouteDataCode,
						processRouteDataDetailRpcDTO.getProcessRouteDataCode());
				queryProcedure.eq(ProcedureRouteRelationshipModel::getUniqueId, uniqueId);
				queryProcedure.eq(ProcedureRouteRelationshipModel::getEnterpriseCode, getEnterpriseCode());
				queryProcedure.eq(ProcedureRouteRelationshipModel::getDeleteFlag, Boolean.FALSE);
				RequestTableHelper.setTableName(procedureRouteRelationshipTableName);
				List<ProcedureRouteRelationshipModel> resultProcedureList =
						routeRelationshipService.list(queryProcedure);
				List<ProductPartProcedureDetailRpcDTO> procedureDataList = new ArrayList<>();
				for (ProcedureRouteRelationshipModel routeRelationshipModel : resultProcedureList) {
					if (routeRelationshipModel == null || routeRelationshipModel.getProcedureCode() == null) {
						continue;
					}
					RequestTableHelper.setTableName(productPartProcedureTableName);
					ProductPartProcedureModel procedureModel =
							productPartProcedureService.getById(routeRelationshipModel.getProcedureCode());
					ProductPartProcedureDetailRpcDTO procedureDetailRpcDTO = new ProductPartProcedureDetailRpcDTO();
					if (procedureModel != null) {
						procedureDetailRpcDTO.setProductPartProcedureCode(procedureModel.getProductPartProcedureCode());
						procedureDetailRpcDTO.setState(procedureModel.getState());
						procedureDetailRpcDTO.setProductPartProcedureSign(procedureModel.getProductPartProcedureSign());
						procedureDetailRpcDTO.setNumber(procedureModel.getNumber());
					}
					procedureDataList.add(procedureDetailRpcDTO);
				}
				// 查询产品、零件 code
				LambdaQueryWrapper<ProductPartRouteRelationshipModel> queryProductPartRouteQw = Wrappers.lambdaQuery();
				queryProductPartRouteQw.eq(ProductPartRouteRelationshipModel::getUniqueId, uniqueId);
				queryProductPartRouteQw.eq(ProductPartRouteRelationshipModel::getEnterpriseCode, getEnterpriseCode());
				queryProductPartRouteQw.eq(ProductPartRouteRelationshipModel::getDeleteFlag, Boolean.FALSE);
				// 查询产品、零件和工艺路线绑定关系
				RequestTableHelper.setTableName(productPartRouteRelationshipTableName);
				List<ProductPartRouteRelationshipModel> getProductPartRouteRelationship =
						productPartRouteRelationshipService.list(queryProductPartRouteQw);
				ProductPartRouteRelationshipModel partRouteRelationshipModel = getProductPartRouteRelationship.stream()
						.findFirst().orElse(new ProductPartRouteRelationshipModel());
				dataDetailRpcDTO.setState(partRouteRelationshipModel.getState() == null ? Boolean.FALSE
						: partRouteRelationshipModel.getState());
				dataDetailRpcDTO.setProcedureDataList(procedureDataList);
				dataDetailRpcDTO.setUniqueId(uniqueId);
				//processRouteDataLists.add(dataDetailRpcDTO);
				//detailRpcDTO.setProcessRouteDataList(processRouteDataLists);
			} else {
				RequestTableHelper.setTableName(procedurePartRelationshipTableName);
				// 根据唯一 ID查询绑定零件 没有工序 ID场景
				LambdaQueryWrapper<ProcedurePartRelationshipModel> queryProcedure = Wrappers.lambdaQuery();
				queryProcedure.eq(ProcedurePartRelationshipModel::getUniqueId, uniqueId);
				queryProcedure.eq(ProcedurePartRelationshipModel::getEnterpriseCode, getEnterpriseCode());
				queryProcedure.eq(ProcedurePartRelationshipModel::getDeleteFlag, Boolean.FALSE);
				List<ProcedurePartRelationshipModel> resultProcedureList =
						procedurePartRelationshipService.list(queryProcedure);
				// 获取统一数据 工艺路线数据
				List<Long> routeData = resultProcedureList.stream()
						.map(ProcedurePartRelationshipModel::getProcessRouteDataCode).filter(Objects::nonNull).toList();
				Map<Long, ProcessRouteDataModel> routeDataCodeToModelMap = new HashMap<>();
				if (!routeData.isEmpty()) {
					routeDataCodeToModelMap =
							unifiedDataService.routeDataCodeToModelMap(routeData, processRouteDataTableName);
				}
				for (ProcedurePartRelationshipModel relationshipModel : resultProcedureList) {
					dataDetailRpcDTO.setProcessRouteDataCode(relationshipModel.getProcessRouteDataCode());
					ProcessRouteDataModel processRouteDataModel = routeDataCodeToModelMap
							.getOrDefault(relationshipModel.getProcessRouteDataCode(), new ProcessRouteDataModel());
					dataDetailRpcDTO.setProcessRouteDataSign(processRouteDataModel.getProcessRouteDataSign());
					dataDetailRpcDTO.setUniqueId(uniqueId);
					//processRouteDataLists.add(dataDetailRpcDTO);
					//detailRpcDTO.setProcessRouteDataList(processRouteDataLists);
				}
			}
			// 添加到列表中
			processRouteDataLists.add(dataDetailRpcDTO);
		}
		// 使用流 API 进行倒序排序
		List<ProcessRouteDataDetailRpcDTO> routeDataDetailRpcDTOS = processRouteDataLists.stream()
				.sorted(Comparator.comparing(ProcessRouteDataDetailRpcDTO::getId).reversed())
				.toList();
		detailRpcDTO.setProcessRouteDataList(routeDataDetailRpcDTOS);
		return detailRpcDTO;
	}

	@Override
	public ParseExcelResult<ProductDetailExcelRpcModel> productDetailAnalyzeExcel(MultipartFileRpcDTO excelFile,
	                                                                              String processRouteDataCode) throws ExceptionPack {
		try {
			return ExcelMultiModelsProcessor.INSTANCE.parseExcelModels(new ByteArrayInputStream(excelFile.getBytes()),
					ProductDetailExcelRpcModel.class, productDetailExcelValidator, processRouteDataCode,
					new BaseExcelHeaderGenerator());
		} catch (ExcelException e) {
			throw new ExceptionPack(e, ExceptionMsg.builder(e.getMessage())
					.msgViewAndResCode(e.getMsgView(), ServiceErrorCode.BATCH_IMPORT_FILE_ILLEGAL.getCode()).build());
		}
	}

	@Override
	public ParseExcelResult<PartDetailExcelRpcModel> partDetailAnalyzeExcel(MultipartFileRpcDTO excelFile,
	                                                                        String processRouteDataCode) throws ExceptionPack {
		try {
			return ExcelMultiModelsProcessor.INSTANCE.parseExcelModels(new ByteArrayInputStream(excelFile.getBytes()),
					PartDetailExcelRpcModel.class, partDetailExcelValidator, processRouteDataCode,
					new BaseExcelHeaderGenerator());
		} catch (ExcelException e) {
			throw new ExceptionPack(e, ExceptionMsg.builder(e.getMessage())
					.msgViewAndResCode(e.getMsgView(), ServiceErrorCode.BATCH_IMPORT_FILE_ILLEGAL.getCode()).build());
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void batchDetailAddProductPart(ProductPartBatchDetailAddRpcRequest requestData) throws ExceptionPack {
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
			List<ProductPartBatchDetailAddRpcRequest.PartBatchAddModel> requestListData =
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
					ProductPartBatchDetailAddRpcRequest.PartBatchAddModel::getProductPartCode);
			Map<Long, ProductPartModel> partToModelMap =
					unifiedDataService.prepareCodeData(modelListData, productPartTableName);
			// 生成关系唯一 ID
			Long uniqueId = PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId();
			// 组装最外层 产品零件与工艺路线关系数据
			ProductPartRouteRelationshipModel productPartRouteRelationshipModel =
					new ProductPartRouteRelationshipModel().setEnterpriseCode(getEnterpriseCode())
							.setProductPartCode(productPartModel.getProductPartCode()).setState(Boolean.TRUE)
							.setProcessRouteDataCode(processRouteDataModel.getProcessRouteDataCode()).setUniqueId(uniqueId);
			LambdaQueryWrapper<ProductPartRouteRelationshipModel> qw = Wrappers.lambdaQuery();
			qw.eq(ProductPartRouteRelationshipModel::getProductPartCode, productPartModel.getProductPartCode());
			qw.eq(ProductPartRouteRelationshipModel::getProcessRouteDataCode, processRouteDataModel.getProcessRouteDataCode());
			qw.eq(ProductPartRouteRelationshipModel::getEnterpriseCode, getEnterpriseCode());
			qw.eq(ProductPartRouteRelationshipModel::getDeleteFlag, Boolean.FALSE);
			RequestTableHelper.setTableName(productPartRouteRelationshipTableName);
			if (productPartRouteRelationshipService.count(qw) == CommonConstant.NUMBER_ZERO) {
				RequestTableHelper.setTableName(productPartRouteRelationshipTableName);
				productPartRouteRelationshipService.save(productPartRouteRelationshipModel);
			}
			// 调用公共方法处理批次添加模型
			processBatchAddModels(requestData, partToModelMap, processRouteDataModel, uniqueId, productPartModel.getProductPartCode(), productPartModel.getAttribute());
			// 判断是否有默认工艺路线如果没有 当前这个路线是默认的
			if (productPartModel.getDefaultRoute() == null) {
				LambdaUpdateWrapper<ProductPartModel> updateDef = Wrappers.lambdaUpdate();
				updateDef.set(ProductPartModel::getDefaultRoute, requestData.getProcessRouteDataCode());
				updateDef.eq(ProductPartModel::getProductPartCode, productPartModel.getProductPartCode());
				updateDef.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode());
				updateDef.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE);
				RequestTableHelper.setTableName(productPartTableName);
				this.update(updateDef);
			}
		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("batchDetailAddProductPart 新增出错 failed")
					.msgView(TechnicalErrorEnum.TECHNICAL_EXCEL_IMPORT.getMsg()).build());
		}
	}

	@Override
	public ParseExcelResult<PartUpdateRpcModel> partUpdateAnalyzeExcel(MultipartFileRpcDTO excelFile,
	                                                                   Integer attribute) {
        try {
	        // 统一使用 PartUpdateRpcModel 解析，包含备注和图纸字段
	        ParseExcelResult<PartUpdateRpcModel> result = ExcelModelsProcessor.INSTANCE.parseExcelModels(
			        new ByteArrayInputStream(excelFile.getBytes()),
			        PartUpdateRpcModel.class,
			        partUpdateExcelValidator,
				new BaseExcelHeaderGenerator(), 0, 0);

	        // 根据类型处理数据：产品(1)和原料(4)需要修正字段
	        if (attribute != null && (attribute == 1 || attribute == 4)) {
		        // 产品和原料不显示图纸字段（从headList中移除）
		        if (result.getHeadList() != null) {
			        List<ExcelHead> headList = result.getHeadList();
			        List<ExcelHead> headListIgnoreFileName = headList.stream()
					        .filter(p -> !Objects.equals(p.getColumn(), "fileName"))
					        .toList();
			        result.setHeadList(headListIgnoreFileName);
		        }

		        if (result.getDatas() != null) {
			        result.getDatas().forEach(p -> {
				        p.setRemark(p.getFileName());
				        p.setFileName(null);
			        });
		        }
	        }

	        return result;
		} catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


	@Override
	@Transactional(rollbackFor = Exception.class)
	public void batchUpdateProductPart(ProductPartBatchUpdateRpcRequest requestData) {
		String productPartTableName =
			tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());

		// 判断是否需要处理图纸字段：产品(1)和原料(4)不处理图纸
		boolean shouldProcessDrawing = requestData.getAttribute() == null
				|| (requestData.getAttribute() != 1 && requestData.getAttribute() != 4);

		// 查询图片唯一标识 - 仅在需要处理图纸时查询
		Map<String, StorageObjectRpcDTO> fileNumberToMap = null;
		if (shouldProcessDrawing) {
			List<String> numberData = StringUtil.split(requestData.getPartInfoBatchUpdateModels().stream()
					.map(ProductPartBatchUpdateRpcRequest.PartBatchUpdateModel::getFileName)
					.filter(StringUtils::isNotBlank)
					.toList());
			if (!numberData.isEmpty()) {
				StorageObjectRpcRequest objectRpcRequest = new StorageObjectRpcRequest();
				objectRpcRequest.setFileNumberList(numberData);
				try {
					fileNumberToMap = storageSpaceFileService.getFileNumberToMap(objectRpcRequest);
				} catch (Exception e) {
					log.warn("获取图纸文件映射失败", e);
				}
			}
		}

		try {
			final Set<String> unityNos = requestData.getPartInfoBatchUpdateModels().stream()
				.map(ProductPartBatchUpdateRpcRequest.PartBatchUpdateModel::getUnityNo).collect(Collectors.toSet());
			LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
			qw.in(ProductPartModel::getUnityNo, unityNos);
			qw.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode());
			qw.orderByDesc(ProductPartModel::getId);
			final Map<String, Long> idMap = productPartMapper.selectList(qw).stream().collect(
				Collectors.toMap(ProductPartModel::getUnityNo, ProductPartModel::getProductPartCode,
					(existing, replacement) -> existing));
			if (idMap.isEmpty()) {
				log.info("未查到主键，不更新");
				return;
			}

			List<ProductPartModel> updateList = new ArrayList<>();
			for (ProductPartBatchUpdateRpcRequest.PartBatchUpdateModel partInfoBatchUpdateModel :
				requestData.getPartInfoBatchUpdateModels()) {
				Long productPartCode = idMap.get(partInfoBatchUpdateModel.getUnityNo());
				// 记录旧数据
				ProductPartModel oldModel = productPartMapper.selectById(productPartCode);
				ProductPartModel m = new ProductPartModel();
				m.setProductPartCode(productPartCode);
				m.setProductPartSign(StringUtils.defaultIfEmpty(partInfoBatchUpdateModel.getName(),null));
				m.setModel(StringUtils.defaultIfEmpty(partInfoBatchUpdateModel.getModel(), null));
				m.setRemark(StringUtils.defaultIfEmpty(partInfoBatchUpdateModel.getRemark(), null));
				m.setFiles(null);
				m.setQualityCodeList(null);

				// ✅ 只有部件(2)和零件(3)才处理图纸字段
				if (shouldProcessDrawing && StringUtils.isNotBlank(partInfoBatchUpdateModel.getFileName()) && fileNumberToMap != null) {
					final List<String> fileNames = StringUtil.split(partInfoBatchUpdateModel.getFileName());
					List<Long> fileIds = new ArrayList<>();
					for (String fileName : fileNames) {
						StorageObjectRpcDTO storageObjectRpcDTO = fileNumberToMap.get(fileName);
						if (storageObjectRpcDTO != null) {
							fileIds.add(storageObjectRpcDTO.getFileId());
						}
					}

					// 组装图纸号
					if (CollectionUtils.isNotEmpty(fileIds)) {
						m.setFiles(fileIds.toArray(Long[]::new));
					} else {
						m.setFiles(new Long[0]);
					}

					ProductPartExtraInfo extraInfo = new ProductPartExtraInfo();
					extraInfo.setDrawingNumberName(String.join(",", fileNames));
					m.setExtra(JSONUtil.toJsonStr(extraInfo));
				}

				updateList.add(m);
				// 先更新
				productPartMapper.updateById(m);
				// 记录新数据
				ProductPartModel newModel = productPartMapper.selectById(productPartCode);
				unifiedDataService.logProductPartUpdate(oldModel, newModel, unifiedDataService.resolveLogType(newModel.getAttribute()));
			}
	        log.info(" batchUpdateProductPart 开始更新 updateList 数量 {} 目标表是：{} ", updateList.size(), productPartTableName);
			productPartMapper.updateById(updateList);
		} catch (Exception e) {
			log.error("操作异常", e);
			throw new RuntimeException(e);
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void batchDetailUpdateProductPart(ProductPartBatchDetailAddRpcRequest requestData) throws ExceptionPack {
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
		// 工艺路线管理表
		String processRouteDataTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProcessRouteData());
		// 产品零件与工艺路线关系表
		String productPartRouteRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProductPartRouteRelationship());

		try {
			// starthere
			// 校验：不允许请求中出现 productPartCode 与 productPartProcedureCode 完全相同的重复数据
			List<ProductPartBatchDetailAddRpcRequest.PartBatchAddModel> partBatchModels =
					Optional.ofNullable(requestData.getPartInfoBatchAddModels()).orElse(Collections.emptyList());
			Map<String, List<ProductPartBatchDetailAddRpcRequest.PartBatchAddModel>> duplicatedMap = partBatchModels.stream()
					.filter(Objects::nonNull)
					.filter(m -> m.getProductPartCode() != null && m.getProductPartProcedureCode() != null)
					.collect(Collectors.groupingBy(m -> m.getProductPartCode() + "|" + m.getProductPartProcedureCode()));
			List<String> duplicatedPairs = duplicatedMap.entrySet().stream()
					.filter(e -> e.getValue().size() > 1)
					.map(e -> {
						String[] arr = e.getKey().split("\\|");
						return StrUtil.format("(productPartCode:{} , productPartProcedureCode:{})", arr[0], arr[1]);
					})
					.collect(Collectors.toList());
			if (!duplicatedPairs.isEmpty()) {
				String msg = "存在重复数据：同一 物料名称 与 工序号 的组合在请求中出现多次 "
						+ String.join("; ", duplicatedPairs);
				throw new AssertException(ExceptionMsg
						.builder(msg)
						.msgView(TechnicalErrorEnum.TECHNICAL_PROCESS_ROUTE_DATA_EXIST_MESSAGE.getMsg()).build());
			}
			// 协作安排的企业，产品修改工艺路线时，如果产品还有 安排中的数据，需要提示 不能修改
			collaborateScheduleItemService.judgeHasSchedulingProducts(List.of(requestData.getProductPartCode()));
		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder(e.getMessage())
					.msgView(e.getMessage()).build());
		}

		try {
			// 查询产品、零件信息
			RequestTableHelper.setTableName(productPartTableName);
			ProductPartModel productPartModel = productPartMapper.selectById(requestData.getProductPartCode());
			// 查询的信息不存在，请检查
			if (null == productPartModel) {
				throw new AssertException(ExceptionMsg
						.builder("This batchDetailUpdateProductPart method failed to execute null == productPartModel")
						.msgView(TechnicalErrorEnum.TECHNICAL_PRODUCT_PART_EXIST_MESSAGE.getMsg()).build());
			}
			List<ProductPartBatchDetailAddRpcRequest.PartBatchAddModel> requestListData =
					requestData.getPartInfoBatchAddModels();
			// 获取统一数据 工艺路线数据
			RequestTableHelper.setTableName(processRouteDataTableName);
			ProcessRouteDataModel processRouteDataModel =
					processRouteDataService.getById(requestData.getProcessRouteDataCode());
			if (null == processRouteDataModel) {
				throw new AssertException(ExceptionMsg
						.builder(
								"This batchDetailUpdateProductPart method failed to execute null == " + "processRouteDataModel")
						.msgView(TechnicalErrorEnum.TECHNICAL_PROCESS_ROUTE_DATA_EXIST_MESSAGE.getMsg()).build());
			}
			List<Long> modelListData = ExtractUtil.streamMapToList(Long::valueOf, requestListData,
					ProductPartBatchDetailAddRpcRequest.PartBatchAddModel::getProductPartCode);
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
			if (routeRelationship == null) {
				log.info("查询关系数据失败，没查询到数据 ，getProductPartCode {} getProcessRouteDataCode{}",
						requestData.getProductPartCode(), requestData.getProcessRouteDataCode());
				return;
			}
			// 获取已存在的唯一标识 ID
			Long uniqueId = routeRelationship.getUniqueId();
			// 调用公共方法处理批次添加模型
			processBatchAddModels(requestData, partToModelMap, processRouteDataModel, uniqueId, productPartModel.getProductPartCode(), productPartModel.getAttribute());
		} catch (Exception e) {
			// 检查是否是自定义的业务异常消息
			String errorMsg = e.getMessage();
			if (errorMsg != null) {
				// 如果是我们自定义的校验错误，保持原有错误信息
				throw new ExceptionPack(e, ExceptionMsg.builder(errorMsg).msgView(errorMsg).build());
			} else {
				// 其他情况使用通用错误信息
				throw new ExceptionPack(e, ExceptionMsg.builder("batchDetailUpdateProductPart 更新出错 failed")
						.msgView(TechnicalErrorEnum.TECHNICAL_EXCEL_IMPORT.getMsg()).build());
			}
		}
	}

	private void processBatchAddModels(ProductPartBatchDetailAddRpcRequest requestData, Map<Long, ProductPartModel> partToModelMap,
	                                   ProcessRouteDataModel processRouteDataModel, Long uniqueId, Long parentProductPartCode, Integer parentAttribute) throws ExceptionPack {
		try {
			String productPartTableName = tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
			// 工序表与零件关系表
			String procedurePartRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
					tableFactory.table.getProcedurePartRelationship());
			// 产品零件工序表
			String productPartProcedure = tableFactory.getTableName(tableFactory.module.getTechnology(),
					tableFactory.table.getProductPartProcedure());
			// 工序表与工艺路线关系表
			String procedureRouteRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
					tableFactory.table.getProcedureRouteRelationship());

			// 添加校验逻辑：检查productPartProcedureCode是否为空
			List<String> invalidItems = new ArrayList<>();
			for (ProductPartBatchDetailAddRpcRequest.PartBatchAddModel batchAddModel : requestData.getPartInfoBatchAddModels()) {
				if (batchAddModel.getProductPartProcedureCode() == null) {
					invalidItems.add("物料名称: " + batchAddModel.getPartName());
				}
			}

			if (!invalidItems.isEmpty()) {
				String errorMsg = "以下物料缺少工序信息: " + String.join("; ", invalidItems);
				throw new AssertException(ExceptionMsg.builder(errorMsg).build());
			}

			// --- Log Start ---
			// 1. 获取旧的物料关系
			LambdaQueryWrapper<ProcedurePartRelationshipModel> oldRelationsQw = Wrappers.lambdaQuery();
			oldRelationsQw.eq(ProcedurePartRelationshipModel::getUniqueId, uniqueId);
			oldRelationsQw.eq(ProcedurePartRelationshipModel::getEnterpriseCode, getEnterpriseCode());
			oldRelationsQw.eq(ProcedurePartRelationshipModel::getDeleteFlag, Boolean.FALSE);
			RequestTableHelper.setTableName(procedurePartRelationshipTableName);
			List<ProcedurePartRelationshipModel> oldRelations = procedurePartRelationshipService.list(oldRelationsQw);
			List<Long> oldMaterialCodes = oldRelations.stream()
					.map(ProcedurePartRelationshipModel::getProductPartCode)
					.filter(Objects::nonNull)
					.toList();
			// --- Log End ---

			List<ProcedureRouteRelationshipModel> procedureRouteRelModels = new ArrayList<>();
			// 工序与工艺路线关系查询
			LambdaQueryWrapper<ProductPartRouteRelationshipModel> qw = Wrappers.lambdaQuery();
			qw.eq(ProductPartRouteRelationshipModel::getUniqueId, uniqueId);
			qw.eq(ProductPartRouteRelationshipModel::getEnterpriseCode, getEnterpriseCode())
					.eq(ProductPartRouteRelationshipModel::getDeleteFlag, Boolean.FALSE);
			// 先清空原来绑定的工序表与零件关系表，在新增
			RelationshipRpcRequest deleteRelationship = new RelationshipRpcRequest().setUniqueId(uniqueId);
			procedurePartRelationshipService.deleteRelationship(deleteRelationship);
			for (ProductPartBatchDetailAddRpcRequest.PartBatchAddModel batchAddModel : requestData.getPartInfoBatchAddModels()) {
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
			BigDecimal quantity = batchAddModel.getNumber();
			// 如果数量为null或为负数，设置为0
			if (quantity == null || quantity.compareTo(BigDecimal.ZERO) < 0) {
				quantity = BigDecimal.ZERO;
			}
			procedurePartRelationshipModel.setQuantity(quantity).setUniqueId(uniqueId);
			// 新增工序表与零件关系
			RequestTableHelper.setTableName(procedurePartRelationshipTableName);
			procedurePartRelationshipService.save(procedurePartRelationshipModel);
			}

			// --- Log Start ---
			// 2. 对比新旧物料并记录日志
			List<Long> newMaterialCodes = requestData.getPartInfoBatchAddModels().stream()
					.map(ProductPartBatchDetailAddRpcRequest.PartBatchAddModel::getProductPartCode)
					.toList();

			// 2.1 记录删除日志
			List<Long> deletedCodes = oldMaterialCodes.stream()
					.filter(code -> !newMaterialCodes.contains(code))
					.toList();
			if (CollectionUtils.isNotEmpty(deletedCodes)) {
				LambdaQueryWrapper<ProductPartModel> nameQw = Wrappers.lambdaQuery();
				nameQw.in(ProductPartModel::getProductPartCode, deletedCodes);
				nameQw.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode());
				nameQw.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE);
				RequestTableHelper.setTableName(productPartTableName);
				String deletedNames = this.list(nameQw).stream()
						.map(ProductPartModel::getProductPartSign)
						.filter(Objects::nonNull)
						.collect(Collectors.joining("、"));
				if (StrUtil.isNotBlank(deletedNames)) {
					StaffOperationLogTypeEnum type = unifiedDataService.resolveLogType(parentAttribute);
					((IProductPartServiceImpl) AopContext.currentProxy()).logDeleteMaterial(deletedNames, parentProductPartCode, type);
				}
			}

			// 2.2 记录新增日志
			List<Long> addedCodes = newMaterialCodes.stream()
					.filter(code -> !oldMaterialCodes.contains(code))
					.toList();
			if (CollectionUtils.isNotEmpty(addedCodes)) {
				String addedNames = addedCodes.stream()
						.map(partToModelMap::get)
						.filter(Objects::nonNull)
						.map(ProductPartModel::getProductPartSign)
						.collect(Collectors.joining("、"));
				if (StrUtil.isNotBlank(addedNames)) {
					StaffOperationLogTypeEnum type = unifiedDataService.resolveLogType(parentAttribute);
					((IProductPartServiceImpl) AopContext.currentProxy()).logAddMaterial(addedNames, parentProductPartCode, type);
				}
			}
			// --- Log End ---

			// 对未变物料，判断工序是否变化，记录工序变更日志
			Map<Long, ProcedurePartRelationshipModel> oldMaterialMap = oldRelations.stream()
				.collect(Collectors.toMap(ProcedurePartRelationshipModel::getProductPartCode, Function.identity(), (a, b) -> a));
			Map<Long, ProductPartBatchDetailAddRpcRequest.PartBatchAddModel> newMaterialMap = requestData.getPartInfoBatchAddModels().stream()
					.collect(Collectors.toMap(ProductPartBatchDetailAddRpcRequest.PartBatchAddModel::getProductPartCode, Function.identity(),
							(a, b) -> a));
			for (Long code : oldMaterialMap.keySet()) {
				if (newMaterialMap.containsKey(code)) {
					Long oldProcedure = oldMaterialMap.get(code).getProcedureCode();
					Long newProcedure = null;
					try {
						newProcedure = partToModelMap.get(code) != null
								? newMaterialMap.get(code).getProductPartProcedureCode()
							: null;
					} catch (Exception ignore) {}
					String materialName = partToModelMap.get(code) != null ? partToModelMap.get(code).getProductPartSign() : "";
					StaffOperationLogTypeEnum type = unifiedDataService.resolveLogType(parentAttribute);
					// 获取工序名称
					String oldProcedureName = "";
					if (oldProcedure != null) {
						ProductPartProcedureModel oldProcModel = productPartProcedureService.getById(oldProcedure);
						if (oldProcModel != null) {
							oldProcedureName = oldProcModel.getProductPartProcedureSign();
						}
					}
					String newProcedureName = "";
					if (newProcedure != null) {
						ProductPartProcedureModel newProcModel = productPartProcedureService.getById(newProcedure);
						if (newProcModel != null) {
							newProcedureName = newProcModel.getProductPartProcedureSign();
						}
					}
					((IProductPartServiceImpl) AopContext.currentProxy()).logUpdateProcedure(
						materialName,
						oldProcedureName,
						newProcedureName,
						parentProductPartCode,
						type
					);
				}
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
			// 对于其他异常，保留原始异常信息
			String errorMsg = e.getMessage();
			if (errorMsg != null) {
				throw new ExceptionPack(e, ExceptionMsg.builder(errorMsg).build());
			} else {
				throw new ExceptionPack(e, ExceptionMsg.builder(TechnicalErrorEnum.TECHNICAL_EXCEL_IMPORT.getMsg()).build());
			}
		}
	}


	@Override
	@Transactional(rollbackFor = Exception.class)
	public void bindMatchingParts(ProductPartBatchDetailAddRpcRequest requestData) throws ExceptionPack {
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
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
			// 查询产品、零件信息
			RequestTableHelper.setTableName(productPartTableName);
			ProductPartModel productPartModel = productPartMapper.selectById(requestData.getProductPartCode());
			// 查询的信息不存在，请检查
			if (null == productPartModel) {
				throw new AssertException(ExceptionMsg
						.builder("This bindMatchingParts method failed to execute null == productPartModel")
						.msgView(TechnicalErrorEnum.TECHNICAL_PRODUCT_PART_EXIST_MESSAGE.getMsg()).build());
			}
			List<ProductPartBatchDetailAddRpcRequest.PartBatchAddModel> requestListData =
					requestData.getPartInfoBatchAddModels();

			// 获取统一数据 工艺路线数据
			RequestTableHelper.setTableName(processRouteDataTableName);
			ProcessRouteDataModel processRouteDataModel =
					processRouteDataService.getById(requestData.getProcessRouteDataCode());
			if (null == processRouteDataModel) {
				throw new AssertException(ExceptionMsg
						.builder(
								"This bindMatchingParts method failed to execute null == " + "processRouteDataModel")
						.msgView(TechnicalErrorEnum.TECHNICAL_PROCESS_ROUTE_DATA_EXIST_MESSAGE.getMsg()).build());
			}
			List<String> nameListData = ExtractUtil.streamMapToList(String::valueOf, requestListData,
					ProductPartBatchDetailAddRpcRequest.PartBatchAddModel::getPartName);
			List<String> modelListData = ExtractUtil.streamMapToList(String::valueOf, requestListData,
					ProductPartBatchDetailAddRpcRequest.PartBatchAddModel::getModelSpecification);
			Map<String, ProductPartModel> partToModelMap = unifiedDataService.preparePartModelDataMap(nameListData,
					modelListData, CommonConstant.NUMBER_TWO, productPartTableName);

			// 获取关系 ID 组装关系数据
			LambdaQueryWrapper<ProductPartRouteRelationshipModel> queryRoute = Wrappers.lambdaQuery();
			queryRoute.eq(ProductPartRouteRelationshipModel::getProductPartCode, requestData.getProductPartCode());
			queryRoute.eq(ProductPartRouteRelationshipModel::getProcessRouteDataCode,
					requestData.getProcessRouteDataCode());
			queryRoute.eq(ProductPartRouteRelationshipModel::getEnterpriseCode, getEnterpriseCode());
			queryRoute.eq(ProductPartRouteRelationshipModel::getDeleteFlag, Boolean.FALSE);
			RequestTableHelper.setTableName(productPartRouteRelationshipTableName);
			ProductPartRouteRelationshipModel routeRelationship =
					productPartRouteRelationshipService.list(queryRoute).stream().findFirst().orElse(null);
			if (routeRelationship == null) {
				log.info("查询关系数据失败，没查询到数据 ，getProductPartCode {} getProcessRouteDataCode{}",
						requestData.getProductPartCode(), requestData.getProcessRouteDataCode());
				return;
			}
			// 生成关系唯一 ID
			Long uniqueId = routeRelationship.getUniqueId();
			List<ProcedureRouteRelationshipModel> procedureRouteRelModels = new ArrayList<>();
			for (ProductPartBatchDetailAddRpcRequest.PartBatchAddModel batchAddModel : requestListData) {
				String partMap = batchAddModel.getPartName() + batchAddModel.getModelSpecification();
				ProductPartModel resultModel = partToModelMap.get(partMap);
				// 导入零件校验
				if (ObjectUtil.isEmpty(partToModelMap.get(partMap))) {
					throw new AssertException(
							ExceptionMsg.builder("导入零件校验 This bindMatchingParts method failed to execute ")
									.msgView(TechnicalErrorEnum.TECHNICAL_PART_MODEL_NOT_EXIST_MESSAGE.getMsg()).build());
				}
				// Long productPartCode =
				// partToModelMap.get(batchAddModel.getModelSpecification()).getProductPartCode();
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
					ProcedureRouteRelationshipModel procedureRouteRelModel = new ProcedureRouteRelationshipModel()
							.setEnterpriseCode(getEnterpriseCode()).setProcedureCode(productPartProcedureCode)
							.setProcessRouteDataCode(processRouteDataModel.getProcessRouteDataCode()).setUniqueId(uniqueId)
							.setSequence(CommonConstant.NUMBER_ZERO).setProductPartCode(resultModel.getProductPartCode());
					procedureRouteRelModels.add(procedureRouteRelModel);
				});
				// 组装工序和零件绑定关系数据
				LambdaQueryWrapper<ProductPartProcedureModel> qw = Wrappers.lambdaQuery();
				qw.eq(ProductPartProcedureModel::getNumber, batchAddModel.getProcedureNumber());
				qw.eq(ProductPartProcedureModel::getEnterpriseCode, getEnterpriseCode());
				qw.eq(ProductPartProcedureModel::getDeleteFlag, Boolean.FALSE);
				// 使用 stream 进行倒序排序并获取第一条记录
				RequestTableHelper.setTableName(productPartProcedure);
				Optional<ProductPartProcedureModel> optionalProductPartProcedureModel = productPartProcedureService
						.list(qw).stream().max(Comparator.comparing(ProductPartProcedureModel::getId));
			optionalProductPartProcedureModel.ifPresent(procedureModel -> {
				ProcedurePartRelationshipModel procedurePartRelationshipModel = new ProcedurePartRelationshipModel()
						.setProductPartCode(resultModel.getProductPartCode()).setEnterpriseCode(getEnterpriseCode())
						.setProcedureCode(procedureModel.getProductPartProcedureCode())
						.setProcessRouteDataCode(processRouteDataModel.getProcessRouteDataCode());
				BigDecimal quantity = batchAddModel.getNumber();
				// 如果数量为null或为负数，设置为0
				if (quantity == null || quantity.compareTo(BigDecimal.ZERO) < 0) {
					quantity = BigDecimal.ZERO;
				}
				procedurePartRelationshipModel.setQuantity(quantity).setUniqueId(uniqueId);
				RequestTableHelper.setTableName(procedurePartRelationshipTableName);
				procedurePartRelationshipService.save(procedurePartRelationshipModel);
			});
			}
			try (AutoCloseable ignored = RequestTableHelper::clearBatchTableName) {
				RequestTableHelper.setBatchTableName(procedureRouteRelationshipTableName);
				routeRelationshipService.saveBatch(procedureRouteRelModels);
			} catch (Exception e) {
				log.error("批量操作失败: 表名={}, 数据大小={}", procedureRouteRelationshipTableName, procedureRouteRelModels.size(), e);
			}
		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("bindMatchingParts 模版导入出错 failed")
					.msgView(TechnicalErrorEnum.TECHNICAL_EXCEL_IMPORT.getMsg()).build());
		}
	}

	@Override
	public List<ProductPartRpcDTO> queryByCodes(List<Long> productPartCodes, Long enterpriseCode, boolean needPcsName,
	                                            boolean needStaffName) throws ExceptionPack {
		try {
			if (productPartCodes == null || productPartCodes.isEmpty()) {
				return List.of();
			}
			String productPartTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
					tableFactory.table.getProductPart(), enterpriseCode);
//			LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
//			qw.in(ProductPartModel::getProductPartCode, productPartCodes);
//			qw.eq(ProductPartModel::getEnterpriseCode, enterpriseCode);
////			qw.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE);
//			RequestTableHelper.setTableName(productPartTableName);
			List<ProductPartModel> productPartModels = productPartMapper.queryByCodes(productPartCodes, enterpriseCode, productPartTableName);

			// 获取单位编号与单位名称的映射
			Map<Long, String> pieceCodeNameMapping = new HashMap<>();
			if (needPcsName) {
				// 计量单位数据准备
				Set<Long> piecesCodes = productPartModels.stream().map(ProductPartModel::getPcs)
						.filter(Objects::nonNull).collect(Collectors.toSet());
				// 获取单位编号与单位名称的映射
				pieceCodeNameMapping = piecesService.pieceCodeChnNameMapping(piecesCodes, enterpriseCode);
			}

			// 获取员工与名称映射
			Map<Long, String> resultStaffMap = new HashMap<>();
			if (needStaffName) {
				// 员工编号
				List<Long> staffCodes = ExtractUtil
						.streamMapToList(Long::valueOf, productPartModels, ProductPartModel::getContactPerson,
								ProductPartModel::getCreator, ProductPartModel::getWarehousePerson)
						.stream().filter(Objects::nonNull).distinct().toList();

				if (!staffCodes.isEmpty()) {
					resultStaffMap = staffService.queryStaffByStaffCodes(staffCodes, enterpriseCode).stream()
							.collect(Collectors.toMap(StaffRpcDTO::getStaffCode, StaffRpcDTO::getStaffName,
									(existingValue, newValue) -> newValue));
				}
			}

			// 转换为 ProductPartRpcDTO 列表
			List<ProductPartRpcDTO> data = Convert.toList(ProductPartRpcDTO.class, productPartModels);
//
//			RightCharacteristics rightCharacteristics = rightCharacteristicsJudgeService.judge(enterpriseCode);
//			boolean hasUnityNo = !rightCharacteristics.getCharacteristic().equals(RightCharacteristicsEnum.PREMIUM_PACKAGE_HANGJIAN);

			for (ProductPartRpcDTO rpcDTO : data) {
				//只有真的有编码，才设置编码 hasUnityNo
//				if (!hasUnityNo) {
//					rpcDTO.setUnityNo(null);
//				}

				rpcDTO.setPartUnityNo(rpcDTO.getUnityNo());
				rpcDTO.setName(rpcDTO.getProductPartSign());
				// 组装 计量单位名称
				if (rpcDTO.getPcs() != null) {
					rpcDTO.setPcsName(pieceCodeNameMapping.get(rpcDTO.getPcs()));
				}
				// 组装 技术对接人
				if (rpcDTO.getContactPerson() != null) {
					rpcDTO.setContactPersonName(resultStaffMap.get(rpcDTO.getContactPerson()));
				}
				// 组装 创建人
				if (rpcDTO.getCreator() != null) {
					rpcDTO.setCreatorName(resultStaffMap.get(rpcDTO.getCreator()));
				}
				// 组装 仓库负责人
				if (rpcDTO.getWarehousePerson() != null) {
					rpcDTO.setWarehousePersonName(resultStaffMap.get(rpcDTO.getWarehousePerson()));
				}
			}
			return data;
		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("query IProductPartService.queryByCodes failed").build());
		}
	}

	@Override
	public ProductPartRpcDTO queryByCode(Long productPartCode, Long enterpriseCode, boolean needPcsName, boolean needStaffName) throws ExceptionPack {
		try {
			if (productPartCode == null || enterpriseCode == null) {
				return null;
			}
			String productPartTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
					tableFactory.table.getProductPart(), enterpriseCode);
			LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
			qw.eq(ProductPartModel::getProductPartCode, productPartCode);
			qw.eq(ProductPartModel::getEnterpriseCode, enterpriseCode);
			qw.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE);
			RequestTableHelper.setTableName(productPartTableName);
			ProductPartModel productPartModel = this.getOne(qw);

			if (productPartModel == null) {
				return null;
			}

			// 获取单位编号与单位名称的映射
			Map<Long, String> pieceCodeNameMapping = new HashMap<>();
			if (needPcsName) {
				// 计量单位数据准备
				Set<Long> piecesCodes = new HashSet<>();
				if (productPartModel.getPcs() != null) {
					piecesCodes.add(productPartModel.getPcs());
				}
				piecesCodes.add(productPartModel.getPcs());

				// 获取单位编号与单位名称的映射
				pieceCodeNameMapping = piecesService.pieceCodeChnNameMapping(piecesCodes, enterpriseCode);
			}

			// 获取员工与名称映射
			Map<Long, String> resultStaffMap = new HashMap<>();
			if (needStaffName) {
				// 员工编号
				List<Long> staffCodes = ExtractUtil
						.streamMapToList(Long::valueOf, List.of(productPartModel), ProductPartModel::getContactPerson,
								ProductPartModel::getCreator, ProductPartModel::getWarehousePerson)
						.stream().filter(Objects::nonNull).distinct().toList();

				if (!staffCodes.isEmpty()) {
					resultStaffMap = staffService.queryStaffByStaffCodes(staffCodes, enterpriseCode).stream()
							.collect(Collectors.toMap(StaffRpcDTO::getStaffCode, StaffRpcDTO::getStaffName,
									(existingValue, newValue) -> newValue));
				}
			}

			// 转换为 ProductPartRpcDTO
			ProductPartRpcDTO rpcDTO = Convert.convert(ProductPartRpcDTO.class, productPartModel);


			rpcDTO.setPartUnityNo(rpcDTO.getUnityNo());
			rpcDTO.setName(rpcDTO.getProductPartSign());
			// 组装 计量单位名称
			if (rpcDTO.getPcs() != null) {
				rpcDTO.setPcsName(pieceCodeNameMapping.get(rpcDTO.getPcs()));
			}
			// 组装 技术对接人
			if (rpcDTO.getContactPerson() != null) {
				rpcDTO.setContactPersonName(resultStaffMap.get(rpcDTO.getContactPerson()));
			}
			// 组装 创建人
			if (rpcDTO.getCreator() != null) {
				rpcDTO.setCreatorName(resultStaffMap.get(rpcDTO.getCreator()));
			}
			// 组装 仓库负责人
			if (rpcDTO.getWarehousePerson() != null) {
				rpcDTO.setWarehousePersonName(resultStaffMap.get(rpcDTO.getWarehousePerson()));
			}
			return rpcDTO;
		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("query IProductPartService.queryByCodes failed").build());
		}
	}

	@Override
	public List<ProductPartRpcDTO> matchQueryByModelOrUnityNos(List<String> materialKeyWordsNeedQuery, Long enterpriseCode,
	                                                           boolean needPcsName, boolean needStaffName, Boolean modelOrUnityNo, boolean allowOtherAttributeMaterial) throws ExceptionPack {
		try {
			if (materialKeyWordsNeedQuery == null || materialKeyWordsNeedQuery.isEmpty()) {
				return List.of();
			}
			String productPartTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
					tableFactory.table.getProductPart(), enterpriseCode);
			LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();


			if (Boolean.FALSE.equals(modelOrUnityNo)) {
//				false:根据型号查
				qw.in(ProductPartModel::getModel, materialKeyWordsNeedQuery);

			} else if (Boolean.TRUE.equals(modelOrUnityNo)) {
//				true: 根据唯一编号查
				qw.in(ProductPartModel::getUnityNo, materialKeyWordsNeedQuery);

			} else {
//				null : 根据型号/唯一编码 查询
				qw.and(wrapper -> wrapper.in(ProductPartModel::getModel, materialKeyWordsNeedQuery).or().in(ProductPartModel::getUnityNo, materialKeyWordsNeedQuery));
			}
			qw.eq(ProductPartModel::getEnterpriseCode, enterpriseCode);
			qw.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE);


			RightCharacteristics rightCharacteristicsForEnterprise = rightCharacteristicsJudgeService.judge(enterpriseCode);

			List<Integer> attributeList = rightCharacteristicsForEnterprise.getTechnicalMaterialTypes().stream()
					.map(PPAttributeEnum::getValue)
					.distinct()
					.collect(Collectors.toList());

			attributeList.add(PPAttributeEnum.PRODUCT.getValue());

			if (allowOtherAttributeMaterial) {
				if (!attributeList.contains(PPAttributeEnum.OTHER.getValue())) {
					attributeList.add(PPAttributeEnum.OTHER.getValue());
				}
			} else {
				attributeList.remove(PPAttributeEnum.OTHER.getValue());
			}

			qw.in(ProductPartModel::getAttribute, attributeList);
			RequestTableHelper.setTableName(productPartTableName);
			List<ProductPartModel> productPartModels = this.list(qw);

			// 获取单位编号与单位名称的映射
			Map<Long, String> pieceCodeNameMapping = new HashMap<>();
			if (needPcsName) {
				// 计量单位数据准备
				Set<Long> piecesCodes = productPartModels.stream().map(ProductPartModel::getPcs)
						.filter(Objects::nonNull).collect(Collectors.toSet());
				// 获取单位编号与单位名称的映射
				pieceCodeNameMapping = piecesService.pieceCodeChnNameMapping(piecesCodes, enterpriseCode);
			}

			// 获取员工与名称映射
			Map<Long, String> resultStaffMap = new HashMap<>();
			if (needStaffName) {
				// 员工编号
				List<Long> staffCodes = ExtractUtil
						.streamMapToList(Long::valueOf, productPartModels, ProductPartModel::getContactPerson,
								ProductPartModel::getCreator, ProductPartModel::getWarehousePerson)
						.stream().filter(Objects::nonNull).distinct().toList();

				if (!staffCodes.isEmpty()) {
					resultStaffMap = staffService.queryStaffByStaffCodes(staffCodes, enterpriseCode).stream()
							.collect(Collectors.toMap(StaffRpcDTO::getStaffCode, StaffRpcDTO::getStaffName,
									(existingValue, newValue) -> newValue));
				}
			}

			// 转换为 ProductPartRpcDTO 列表
			List<ProductPartRpcDTO> data = Convert.toList(ProductPartRpcDTO.class, productPartModels);


			for (ProductPartRpcDTO rpcDTO : data) {

				rpcDTO.setPartUnityNo(rpcDTO.getUnityNo());
				rpcDTO.setName(rpcDTO.getProductPartSign());
				// 组装 计量单位名称
				if (rpcDTO.getPcs() != null) {
					rpcDTO.setPcsName(pieceCodeNameMapping.get(rpcDTO.getPcs()));
				}
				// 组装 技术对接人
				if (rpcDTO.getContactPerson() != null) {
					rpcDTO.setContactPersonName(resultStaffMap.get(rpcDTO.getContactPerson()));
				}
				// 组装 创建人
				if (rpcDTO.getCreator() != null) {
					rpcDTO.setCreatorName(resultStaffMap.get(rpcDTO.getCreator()));
				}
				// 组装 仓库负责人
				if (rpcDTO.getWarehousePerson() != null) {
					rpcDTO.setWarehousePersonName(resultStaffMap.get(rpcDTO.getWarehousePerson()));
				}
			}
			return data;
		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("query IProductPartService.queryByCodes failed").build());
		}
	}

	@Override
	public ProductPartRpcDTO matchQueryByName(String name, Long enterpriseCode, boolean needPcsName, boolean needStaffName, boolean allowOtherAttributeMaterial) throws ExceptionPack {
		try {
			if (enterpriseCode == null || StrUtil.isBlank(name)) {
				return null;
			}
			String productPartTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
					tableFactory.table.getProductPart(), enterpriseCode);
			LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
			qw.eq(ProductPartModel::getProductPartSign, name);
			qw.eq(ProductPartModel::getEnterpriseCode, enterpriseCode);
			qw.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE);


			RightCharacteristics rightCharacteristicsForEnterprise = rightCharacteristicsJudgeService.judge(enterpriseCode);

			List<Integer> attributeList = rightCharacteristicsForEnterprise.getTechnicalMaterialTypes().stream()
					.map(PPAttributeEnum::getValue)
					.distinct()
					.collect(Collectors.toList());

			attributeList.add(PPAttributeEnum.PRODUCT.getValue());

			if (allowOtherAttributeMaterial) {
				if (!attributeList.contains(PPAttributeEnum.OTHER.getValue())) {
					attributeList.add(PPAttributeEnum.OTHER.getValue());
				}
			} else {
				attributeList.remove(PPAttributeEnum.OTHER.getValue());
			}

			qw.in(ProductPartModel::getAttribute, attributeList);
			RequestTableHelper.setTableName(productPartTableName);
			List<ProductPartModel> productPartModels = this.list(qw);

			if (productPartModels == null || productPartModels.isEmpty()) {
				return null;
			}

			if (productPartModels.size() > 1) {
				return null;
			}


			// 获取单位编号与单位名称的映射
			Map<Long, String> pieceCodeNameMapping = new HashMap<>();
			if (needPcsName) {
				// 计量单位数据准备
				Set<Long> piecesCodes = productPartModels.stream().map(ProductPartModel::getPcs)
						.filter(Objects::nonNull).collect(Collectors.toSet());
				// 获取单位编号与单位名称的映射
				pieceCodeNameMapping = piecesService.pieceCodeChnNameMapping(piecesCodes, enterpriseCode);
			}

			// 获取员工与名称映射
			Map<Long, String> resultStaffMap = new HashMap<>();
			if (needStaffName) {
				// 员工编号
				List<Long> staffCodes = ExtractUtil
						.streamMapToList(Long::valueOf, productPartModels, ProductPartModel::getContactPerson,
								ProductPartModel::getCreator, ProductPartModel::getWarehousePerson)
						.stream().filter(Objects::nonNull).distinct().toList();

				if (!staffCodes.isEmpty()) {
					resultStaffMap = staffService.queryStaffByStaffCodes(staffCodes, enterpriseCode).stream()
							.collect(Collectors.toMap(StaffRpcDTO::getStaffCode, StaffRpcDTO::getStaffName,
									(existingValue, newValue) -> newValue));
				}
			}

			// 转换为 ProductPartRpcDTO 列表
			List<ProductPartRpcDTO> data = Convert.toList(ProductPartRpcDTO.class, productPartModels);

			//20250701:所有企业都有编号
//			RightCharacteristics rightCharacteristics = rightCharacteristicsJudgeService.judge(enterpriseCode);
//			boolean hasUnityNo = !rightCharacteristics.getCharacteristic().equals(RightCharacteristicsEnum.PREMIUM_PACKAGE_HANGJIAN);

			for (ProductPartRpcDTO rpcDTO : data) {
				//只有真的有编码，才设置编码 20250701:所有企业都有编号
//				if (!hasUnityNo) {
//					rpcDTO.setUnityNo(null);
//				}
				rpcDTO.setPartUnityNo(rpcDTO.getUnityNo());
				rpcDTO.setName(rpcDTO.getProductPartSign());
				// 组装 计量单位名称
				if (rpcDTO.getPcs() != null) {
					rpcDTO.setPcsName(pieceCodeNameMapping.get(rpcDTO.getPcs()));
				}
				// 组装 技术对接人
				if (rpcDTO.getContactPerson() != null) {
					rpcDTO.setContactPersonName(resultStaffMap.get(rpcDTO.getContactPerson()));
				}
				// 组装 创建人
				if (rpcDTO.getCreator() != null) {
					rpcDTO.setCreatorName(resultStaffMap.get(rpcDTO.getCreator()));
				}
				// 组装 仓库负责人
				if (rpcDTO.getWarehousePerson() != null) {
					rpcDTO.setWarehousePersonName(resultStaffMap.get(rpcDTO.getWarehousePerson()));
				}
			}
			return data.getFirst();
		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("query IProductPartService.queryByCodes failed").build());
		}
	}

	@Override
	public List<ProductPartRpcDTO> likeQueryByModelOrUnityNo(String materialKeyWordNeedQuery, Long enterpriseCode,
	                                                         boolean needPcsName, boolean needStaffName, Boolean modelOrUnityNo) throws ExceptionPack {
		try {
			if (StrUtil.isBlank(materialKeyWordNeedQuery)) {
				return List.of();
			}
			String productPartTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
					tableFactory.table.getProductPart(), enterpriseCode);
			LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();


			if (Boolean.FALSE.equals(modelOrUnityNo)) {
//				false:根据型号 /名称 查
				qw.and(wrapper -> wrapper.like(ProductPartModel::getModel, materialKeyWordNeedQuery).or().like(ProductPartModel::getProductPartSign, materialKeyWordNeedQuery));

			} else if (Boolean.TRUE.equals(modelOrUnityNo)) {
//				true: 根据唯一编号 /名称 查
				qw.and(wrapper -> wrapper.like(ProductPartModel::getUnityNo, materialKeyWordNeedQuery).or().like(ProductPartModel::getProductPartSign, materialKeyWordNeedQuery));

			} else {
//				null : 根据型号/唯一编码 /名称 查询
				qw.and(wrapper -> wrapper.like(ProductPartModel::getModel, materialKeyWordNeedQuery).or().like(ProductPartModel::getUnityNo, materialKeyWordNeedQuery)
						.or().like(ProductPartModel::getProductPartSign, materialKeyWordNeedQuery));
			}
			qw.eq(ProductPartModel::getEnterpriseCode, enterpriseCode);
			qw.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE);
			RequestTableHelper.setTableName(productPartTableName);
			List<ProductPartModel> productPartModels = this.list(qw);

			// 获取单位编号与单位名称的映射
			Map<Long, String> pieceCodeNameMapping = new HashMap<>();
			if (needPcsName) {
				// 计量单位数据准备
				Set<Long> piecesCodes = productPartModels.stream().map(ProductPartModel::getPcs)
						.filter(Objects::nonNull).collect(Collectors.toSet());
				// 获取单位编号与单位名称的映射
				pieceCodeNameMapping = piecesService.pieceCodeChnNameMapping(piecesCodes, enterpriseCode);
			}

			// 获取员工与名称映射
			Map<Long, String> resultStaffMap = new HashMap<>();
			if (needStaffName) {
				// 员工编号
				List<Long> staffCodes = ExtractUtil
						.streamMapToList(Long::valueOf, productPartModels, ProductPartModel::getContactPerson,
								ProductPartModel::getCreator, ProductPartModel::getWarehousePerson)
						.stream().filter(Objects::nonNull).distinct().toList();

				if (!staffCodes.isEmpty()) {
					resultStaffMap = staffService.queryStaffByStaffCodes(staffCodes, enterpriseCode).stream()
							.collect(Collectors.toMap(StaffRpcDTO::getStaffCode, StaffRpcDTO::getStaffName,
									(existingValue, newValue) -> newValue));
				}
			}

			// 转换为 ProductPartRpcDTO 列表
			List<ProductPartRpcDTO> data = Convert.toList(ProductPartRpcDTO.class, productPartModels);

			for (ProductPartRpcDTO rpcDTO : data) {
				rpcDTO.setPartUnityNo(rpcDTO.getUnityNo());
				rpcDTO.setName(rpcDTO.getProductPartSign());
				// 组装 计量单位名称
				if (rpcDTO.getPcs() != null) {
					rpcDTO.setPcsName(pieceCodeNameMapping.get(rpcDTO.getPcs()));
				}
				// 组装 技术对接人
				if (rpcDTO.getContactPerson() != null) {
					rpcDTO.setContactPersonName(resultStaffMap.get(rpcDTO.getContactPerson()));
				}
				// 组装 创建人
				if (rpcDTO.getCreator() != null) {
					rpcDTO.setCreatorName(resultStaffMap.get(rpcDTO.getCreator()));
				}
				// 组装 仓库负责人
				if (rpcDTO.getWarehousePerson() != null) {
					rpcDTO.setWarehousePersonName(resultStaffMap.get(rpcDTO.getWarehousePerson()));
				}
			}
			return data;
		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("query IProductPartService.queryByCodes failed").build());
		}
	}

	@Override
	public Map<Long, Long> defaultRoute(List<Long> productPartCodes, Integer attribute, Long enterpriseCode)
			throws ExceptionPack {
		try {
			if (productPartCodes == null || productPartCodes.isEmpty()) {
				return new HashMap<>();
			}
			String productPartTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
					tableFactory.table.getProductPart(), enterpriseCode);
			LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
			qw.in(ProductPartModel::getProductPartCode, productPartCodes);
			qw.eq(ProductPartModel::getEnterpriseCode, enterpriseCode);
			qw.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE);
			qw.isNotNull(ProductPartModel::getDefaultRoute);

			if (attribute != null) {
				qw.eq(ProductPartModel::getAttribute, attribute);
			}

			RequestTableHelper.setTableName(productPartTableName);
			List<ProductPartModel> productPartModels = this.list(qw);

			return productPartModels.stream().filter(p -> p.getDefaultRoute() != null)
					.collect(Collectors.toMap(ProductPartModel::getProductPartCode, ProductPartModel::getDefaultRoute));
		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("query IProductPartService.defaultRoute failed").build());
		}
	}

	@Override
	public ProductPartRpcDTO queryPartsById(Long id) throws ExceptionPack {
		// 新技术部-产品零件表
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
		LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
		qw.eq(ProductPartModel::getProductPartCode, id);

		RequestTableHelper.setTableName(productPartTableName);
		ProductPartModel data = productPartMapper.queryById(id);
		if (data == null) {
			return null;
		}
		data.setProductPartCode(id);
		return transRes(Collections.singletonList(data)).getFirst();

	}

	@Override
	public List<ProductPartRpcDTO> queryPartsByIds(List<Long> ids) throws ExceptionPack {
		// 新技术部-产品零件表
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
		LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
		qw.in(ProductPartModel::getProductPartCode, ids);
		RequestTableHelper.setTableName(productPartTableName);
		List<ProductPartModel> data = this.list(qw);
		return transRes(data);
	}

	@Override
	public List<ProductPartRpcDTO> queryListByProductPartProcedure(List<ProductPartProcedureRpcDTO> queryIds)
			throws ExceptionPack {
		// 新技术部-工序表与零件关系表
		String procedurePartRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProcedurePartRelationship());
		// 新技术部-产品零件表
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());

		long enterpriseCode = InfoPenetrateProcessor.INSTANCE.getPenetrateInfoNotNull(ENTERPRISE_CODE_THREAD_LOCAL);

		List<ProductPartModel> list = productPartMapper.queryListByProductPartProcedure(queryIds, enterpriseCode,
				productPartTableName, procedurePartRelationshipTableName);

		list = list.stream().filter(a -> a != null).toList();
		if (CollectionUtils.isEmpty(list)) {
			return Collections.emptyList();
		}
		return transRes(list);
	}

	private List<ProductPartRpcDTO> transRes(List<ProductPartModel> list) throws ExceptionPack {
		// 计量单位数据准备
		Set<Long> productPiecesCodes =
				list.stream().filter(a -> a != null && a.getPcs() != null).map(ProductPartModel::getPcs).collect(Collectors.toSet());
		List<ProductPartRpcDTO> result = list.stream().map(a -> {
			ProductPartRpcDTO dto = Convert.convert(ProductPartRpcDTO.class, a);
			if (dto != null) {
				if (a != null && a.getQuantity() != null) {
					dto.setQuantity(a.getQuantity());
				} else {
					dto.setQuantity(BigDecimal.ONE);
				}
			}
			return dto;
		}).collect(Collectors.toList());

		if (CollectionUtils.isNotEmpty(productPiecesCodes)) {
			// 获取产品与名称映射
			Map<Long, String> pieceCodeNameMapping =
					piecesService.pieceCodeChnNameMapping(productPiecesCodes, getEnterpriseCode());
			result.forEach(a -> a.setPcsName(pieceCodeNameMapping.get(a.getPcs())));
		}
		return result;
	}

	@Override
	public RpcPagingDTO<ProductPartRpcDTO> queryPartsByCondition(Map<String, Object> conditionMap, Integer current,
	                                                             Integer size) throws ExceptionPack {
		long enterpriseCode = InfoPenetrateProcessor.INSTANCE.getPenetrateInfoNotNull(ENTERPRISE_CODE_THREAD_LOCAL);
		// 新技术部-产品零件表
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());

		LambdaQueryWrapper<ProductPartModel> wrapper = Wrappers.lambdaQuery();

		wrapper.eq(ProductPartModel::getEnterpriseCode, enterpriseCode);
		// 只查询零件
		wrapper.in(ProductPartModel::getAttribute, myBatisDynamicTableNameFactory.getPartAttributeRightJPA());
		wrapper.eq(ProductPartModel::getDeleteFlag, false);
		wrapper.eq(ProductPartModel::getState, true);

		if (MapUtils.getLong(conditionMap, "id") != null) {
			wrapper.eq(ProductPartModel::getProductPartCode, MapUtils.getLong(conditionMap, "id"));
		}

		if (StringUtils.isNotBlank(MapUtils.getString(conditionMap, "keyWord"))) {
			wrapper.like(ProductPartModel::getProductPartSign, MapUtils.getString(conditionMap, "keyWord"));
		}
		Page<ProductPartModel> page = new Page<>(current, size);
		RequestTableHelper.setTableName(productPartTableName);
		Page<ProductPartModel> pageList = this.page(page, wrapper);

		return new RpcPagingDTO(transRes(pageList.getRecords()), pageList.getTotal());
	}

	@Override
	public List<ProductPartRpcDTO> queryListByProcessRoutesAndPartCode(List<Long> processRoutes, Long partCode)
			throws ExceptionPack {
		// 新技术部-工序表与零件关系表
		String procedurePartRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProcedurePartRelationship());
		// 新技术部-产品零件表
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());

		long enterpriseCode = InfoPenetrateProcessor.INSTANCE.getPenetrateInfoNotNull(ENTERPRISE_CODE_THREAD_LOCAL);

		List<ProductPartModel> list = productPartMapper.queryListByProcessRoutesAndPartCode(processRoutes, partCode,
				enterpriseCode, productPartTableName, procedurePartRelationshipTableName);

		return transRes(list);
	}

	@Override
	public SimpleStockInfoDto queryStockNumByProductPart(Long productPartCode) {
		// 新技术部-库存表
		String stockTableName =
				tableFactory.getTableName(tableFactory.module.getWarehouse(), tableFactory.table.getStock());

		StockModel modle = stockMapper.getStockByProductPartCode(productPartCode,
				InfoPenetrateProcessor.INSTANCE.getPenetrateInfoNotNull(ENTERPRISE_CODE_THREAD_LOCAL), stockTableName);
		if (modle == null) {
			return new SimpleStockInfoDto();
		}

		SimpleStockInfoDto info = new SimpleStockInfoDto();

		info.setStockCode(modle.getPrimaryKeyValue());
		info.setStockNum(Optional.ofNullable(modle.getTotalInventory()).orElse(BigDecimal.ZERO)
				.subtract(Optional.ofNullable(modle.getLockInInventor()).orElse(BigDecimal.ZERO)));

		if (info.getStockNum().compareTo(BigDecimal.ZERO) < 0) {
			info.setStockNum(BigDecimal.ZERO);
		}
		if (!Objects.equals(modle.getStockInitState(), Boolean.TRUE) ||
				!Objects.equals(modle.getState(), Boolean.TRUE)) {
			info.setStockNum(BigDecimal.ZERO);
		}
		return info;
	}

	@Override
	public List<ProductPartRpcDTO> queryByProductIn(List<Long> products) throws ExceptionPack {
		long enterpriseCode = InfoPenetrateProcessor.INSTANCE.getPenetrateInfoNotNull(ENTERPRISE_CODE_THREAD_LOCAL);
		// 新技术部-产品零件表
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());

		LambdaQueryWrapper<ProductPartModel> wrapper = Wrappers.lambdaQuery();

		wrapper.eq(ProductPartModel::getEnterpriseCode, enterpriseCode);
		wrapper.in(ProductPartModel::getProductPartCode, products);
		RequestTableHelper.setTableName(productPartTableName);

		List<ProductPartModel> list = this.list(wrapper);
		return transRes(list);
	}

	@Override
	public int updateStockNum(Long stockInfoCode, BigDecimal useStockNum) {
		String stockTableName =
				tableFactory.getTableName(tableFactory.module.getWarehouse(), tableFactory.table.getStock());

		return stockMapper.updateStockNum(stockInfoCode, useStockNum, stockTableName);
	}

	@Override
	public List<SimpleStockInfoDto> queryStockNumByProductParts(List<Long> productPartCodes) {
		// 新技术部-库存表
		String stockTableName =
				tableFactory.getTableName(tableFactory.module.getWarehouse(), tableFactory.table.getStock());

		List<StockModel> modles = stockMapper.getStockByProductPartCodeInNew(productPartCodes,
				InfoPenetrateProcessor.INSTANCE.getPenetrateInfoNotNull(ENTERPRISE_CODE_THREAD_LOCAL), stockTableName);

		return modles.stream().map(modle -> {
			SimpleStockInfoDto info = new SimpleStockInfoDto();
			info.setStockCode(modle.getPrimaryKeyValue());
			info.setStockNum(Optional.ofNullable(modle.getTotalInventory()).orElse(BigDecimal.ZERO)
					.subtract(Optional.ofNullable(modle.getLockInInventor()).orElse(BigDecimal.ZERO)));

			if (info.getStockNum().compareTo(BigDecimal.ZERO) < 0) {
				info.setStockNum(BigDecimal.ZERO);
			}
			if (!Objects.equals(modle.getStockInitState(), Boolean.TRUE) ||
					!Objects.equals(modle.getState(), Boolean.TRUE)) {
				info.setStockNum(BigDecimal.ZERO);
			}

			info.setProductPartCode(modle.getProductPartCode());
			return info;
		}).toList();

	}

	@Override
	public void updateProductPartDetailRouteState(ProductPartRpcRequest requestData) throws AssertException {
		log.info("start 开始更新BOM详情工艺路线信息 {}", JSONUtil.toJsonStr(requestData));
		if (ObjectUtil.isEmpty(requestData.getUniqueId())) {
			return;
		}
		if (ObjectUtil.isAllEmpty(requestData.getState(), requestData.getDeleteFlag())) {
			return;
		}
		// 新技术部-产品零件表
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
		// 工艺路线管理表
		String processRouteDataTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProcessRouteData());
		// 获取统一数据 工艺路线数据
		//RequestTableHelper.setTableName(processRouteDataTableName);
		//ProcessRouteDataModel processRouteDataModel = processRouteDataService.getById(requestData.getProcessRouteDataCode());
		//if (Boolean.FALSE.equals(processRouteDataModel.getState())) {
		//	throw new AssertException(ExceptionMsg.builder("").
		//			msgView(TechnicalErrorEnum.ROUTE_DATA_STATE_NOT_EXIST.getMsg()).build());
		//}
		// 产品零件与工艺路线关系表
		String productPartRouteRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProductPartRouteRelationship());
		LambdaUpdateWrapper<ProductPartRouteRelationshipModel> uw = Wrappers.lambdaUpdate();
		uw.eq(ProductPartRouteRelationshipModel::getUniqueId, requestData.getUniqueId());
		uw.eq(ProductPartRouteRelationshipModel::getEnterpriseCode, getEnterpriseCode());
		if (requestData.getState() != null) {
			uw.set(ProductPartRouteRelationshipModel::getState, requestData.getState());
		}
		if (requestData.getDeleteFlag() != null) {
			uw.set(ProductPartRouteRelationshipModel::getDeleteFlag, requestData.getDeleteFlag());
		}
		RequestTableHelper.setTableName(productPartRouteRelationshipTableName);
		productPartRouteRelationshipService.update(uw);
		RequestTableHelper.setTableName(productPartRouteRelationshipTableName);
		List<ProductPartRouteRelationshipModel> relationshipModels = productPartRouteRelationshipService.list(uw);
		ProductPartRouteRelationshipModel relationshipModel = relationshipModels.stream().findFirst().orElse(null);
		if (relationshipModel != null) {
			RequestTableHelper.setTableName(productPartTableName);
			ProductPartModel productPartModel = getById(relationshipModel.getProductPartCode());
			// 如果是停用路线，判断当前已停用路线是否是默认，如果是默认 默认值需要更新成启用的code
			if (Boolean.FALSE.equals(requestData.getState()) || Boolean.TRUE.equals(requestData.getDeleteFlag())) {
				LambdaQueryWrapper<ProductPartRouteRelationshipModel> productPartQw = Wrappers.lambdaQuery();
				productPartQw.eq(ProductPartRouteRelationshipModel::getProductPartCode, relationshipModel.getProductPartCode());
				productPartQw.eq(ProductPartRouteRelationshipModel::getEnterpriseCode, getEnterpriseCode());
				RequestTableHelper.setTableName(productPartRouteRelationshipTableName);
				List<ProductPartRouteRelationshipModel> getProductPartList = productPartRouteRelationshipService.list(productPartQw).stream()
						.sorted(Comparator.comparing(ProductPartRouteRelationshipModel::getId).reversed())
						.toList();
				ProductPartRouteRelationshipModel routeRelationship = getProductPartList.stream()
						.filter(f -> Boolean.TRUE.equals(f.getState()) && Boolean.FALSE.equals(f.getDeleteFlag()))
						.findFirst().orElse(null);
				// 如果没有启用的 那么默认值也不需要了
				LambdaUpdateWrapper<ProductPartModel> updateDef = Wrappers.lambdaUpdate();
				updateDef.eq(ProductPartModel::getProductPartCode, relationshipModel.getProductPartCode());
				updateDef.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode());
				updateDef.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE);
				if (routeRelationship != null && productPartModel.getDefaultRoute().equals(requestData.getProcessRouteDataCode())) {
					updateDef.set(ProductPartModel::getDefaultRoute, routeRelationship.getProcessRouteDataCode());
					RequestTableHelper.setTableName(productPartTableName);
					this.update(updateDef);
				}
				if (routeRelationship == null) {
					updateDef.set(ProductPartModel::getDefaultRoute, null);
					RequestTableHelper.setTableName(productPartTableName);
					this.update(updateDef);
				}
			}
			// 如果是启用路线，判断当前是否有默认路线如果没有 默认当前
			if (Boolean.TRUE.equals(requestData.getState())) {
				if (productPartModel.getDefaultRoute() == null) {
					LambdaUpdateWrapper<ProductPartModel> updateDef = Wrappers.lambdaUpdate();
					updateDef.set(ProductPartModel::getDefaultRoute, requestData.getProcessRouteDataCode());
					updateDef.eq(ProductPartModel::getProductPartCode, relationshipModel.getProductPartCode());
					updateDef.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode());
					updateDef.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE);
					RequestTableHelper.setTableName(productPartTableName);
					this.update(updateDef);
				}
			}
		}
	}

	@Override
	public List<PartAllRelationship> queryAllRelationshipByIdAndRoute(Long processRoute, Long productId)
			throws ExceptionPack {

		if (productId == null) {
			return Collections.emptyList();
		}

		/**
		 * 希航的特殊处理
		 */
		if (!CommonUtil.getEnterpriseRightCharacteristics().isProcedureProcessRoute()) {
			return queryAllXiHangRelationshipById(productId);
		}

		/**
		 * 工艺路线和工序的关系表
		 */
		String procedureRouteRelationship = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProcedureRouteRelationship());

		/**
		 * 工艺路线和工序的关系表
		 */
		String productPartRouteRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProductPartRouteRelationship());

		String procedurePartRelationship = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProcedurePartRelationship());
		/**
		 * 工序表
		 */
		String productPartProcedure = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProductPartProcedure());

		/**
		 * 工艺路线
		 */
		String processRouteData =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProcessRouteData());

		/**
		 * 零件表
		 */
		String productPart =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());

		List<Integer> attributes = myBatisDynamicTableNameFactory.getPartAttributeRightJPA();
		//if (!RightCharacteristicsEnum.PREMIUM_PACKAGE_HANGJIAN.equals(CommonUtil.getEnterpriseRightCharacteristics().getCharacteristic())) {
			attributes.add(PPAttributeEnum.PRODUCT.getValue());
		//}

		List<PartAllRelationship> result = productPartMapper.queryAllRelationshipByIdAndRoute(productId, processRoute,
				InfoPenetrateProcessor.INSTANCE.getPenetrateInfoNotNull(ENTERPRISE_CODE_THREAD_LOCAL), productPartProcedure,
				procedureRouteRelationship, productPartRouteRelationshipTableName, procedurePartRelationship, productPart,
				processRouteData, attributes);

		try {
			transdata(result, productPartProcedure);
		} catch (Exception e) {
			log.error("转化出错", e);
			throw new RuntimeException(e);
		}

		lastProduceWrapper(result);
		return result;
	}

	/**
	 * @description 最后工序的封装
	 *
	 * @author zhanglijia
	 * @organization futurecraftsmen
	 * @date Created in 2025/6/9 11:17
	 * @department: Product development
	 */
	private void lastProduceWrapper(List<PartAllRelationship> list) {
		if (CollectionUtils.isEmpty(list)) {
			return;
		}
		Map<Long, List<PartAllRelationship>> map = list.stream().collect(Collectors.groupingBy(a -> a.getRouteCode()));
		for (Map.Entry<Long, List<PartAllRelationship>> entry : map.entrySet()) {
			plusLastProduce(entry.getValue());
		}
	}

	/**
	 * @description 补充是否最后1道工序
	 *
	 * @author zhanglijia
	 * @organization futurecraftsmen
	 * @date Created in 2025/6/9 11:13
	 * @department: Product development
	 */
	private void plusLastProduce(List<PartAllRelationship> list) {

		Long routeCode = list.getFirst().getRouteCode();

		String html = "";
		for (PartAllRelationship relationship : list) {
			if (routeCode.equals(relationship.getRouteCode()) && StringUtils.isNotBlank(relationship.getRouteData())) {
				html = relationship.getRouteData();
				break;
			}
		}

		String last = null;
		if (StringUtils.isNotBlank(html)) {
			RouteActionDto routeAction = RouteLinkUtils.parseRouteAction(html);
			for (PartAllRelationship datum : list) {

				if (datum.getProcedureCode() == null || datum.getProcedureCode() == 0) {
					continue;
				}

				RouteLink link = routeAction.getIdCache().getOrDefault(datum.getProcedureActivity(), new RouteLink());
				if (CollectionUtils.isEmpty(link.getNextIDList())) {
					last = datum.getProcedureActivity();
					break;
				}
			}
		}

		for (PartAllRelationship saveDatum : list) {
			saveDatum.setLastProcedure(false);
			if (StringUtils.isNotBlank(saveDatum.getProcedureActivity())) {
				if (saveDatum.getProcedureActivity().equals(last)) {
					saveDatum.setLastProcedure(true);
				}
			}

		}

		/**
		 * 兼容无工艺路线模式
		 */
		boolean flag = true;
		for (PartAllRelationship datum : list) {
			if (datum.getLastProcedure()) {
				flag = false;
			}
		}

		if (flag) {
			for (PartAllRelationship saveDatum : list) {
				if (saveDatum.getProcedureCode() != null && saveDatum.getProcedureCode() != 0) {
					saveDatum.setLastProcedure(true);
					break;
				}
			}
		}
	}

	/**
	 *
	 * @description 希航虚拟的工艺路线和工序
	 * @param productId
	 * @return
	 * @throws ExceptionPack
	 *
	 */
	private List<PartAllRelationship> queryAllXiHangRelationshipById(Long productId) throws ExceptionPack {
		ProductPartCompRequest request = new ProductPartCompRequest();
		request.setParentCode(productId);
		request.setTreeDepth(1);
		List<ProductPartConsistDTO> result = productPartCommonService.getProductConsistList(request);
		if (CollectionUtils.isEmpty(result)) {
			return Collections.emptyList();
		}

		List<ProductPartRpcDTO> checkPartInfo = queryPartsByIds(result.stream().map(a -> a.getChildCode()).toList());

		/**
		 * 读取有权限的产品零件。由于限制了1层，故不用考虑断环的情况
		 */
		Set<Integer> chmod = new HashSet<>(myBatisDynamicTableNameFactory.getPartAttributeRightAllJPA());
		checkPartInfo = checkPartInfo.stream().filter(a -> chmod.contains(a.getAttribute())).toList();

		Map<Long, ProductPartRpcDTO> partCode = checkPartInfo.stream().collect(Collectors.toMap(a -> a.getProductPartCode(),
				a -> a, (a, b) -> a));

		List<ProductPartConsistDTO> filter = new ArrayList<>(result.size());

		result.forEach(a -> {
			if (partCode.containsKey(a.getChildCode())) {
				a.setChildPcsName(partCode.get(a.getChildCode()).getPcsName());
				filter.add(a);
			}
		});
		if (CollectionUtils.isEmpty(filter)) {
			return Collections.emptyList();
		}
		ProductPartRpcDTO dto = queryPartsById(productId);
		if (dto == null) {
			return Collections.emptyList();
		}
		for (ProductPartConsistDTO partConsistDTO : filter) {
			partConsistDTO.setParentPcsName(dto.getPcsName());
		}

		return analysisXiHang(filter);
	}

	/**
	 * @description 希航数据虚拟化
	 *
	 * @author zhanglijia
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/27 14:33
	 * @department: Product development
	 */
	private List<PartAllRelationship> analysisXiHang(List<ProductPartConsistDTO> data) {
		List<PartAllRelationship> list = new ArrayList<>();

		if (CollectionUtils.isEmpty(data)) {
			return list;
		}

		Set<Long> partCode = new HashSet<>();

		for (ProductPartConsistDTO detailDTO : data) {

			if (partCode.contains(detailDTO.getChildCode())) {
				continue;
			}
			partCode.add(detailDTO.getChildCode());

			PartAllRelationship gx = new PartAllRelationship();
			gx.setProductCode(detailDTO.getParentCode());
			gx.setProductName(detailDTO.getParentName());

			gx.setProductModel(detailDTO.getParentModel());
			gx.setProductPcs(detailDTO.getParentPcs());
			gx.setProductType(detailDTO.getParentType());
			gx.setProductPcsName(detailDTO.getParentPcsName());
			gx.setRouteCode(detailDTO.getParentCode());
			gx.setRouteState(true);
			gx.setPartCode(detailDTO.getChildCode());
			gx.setPartName(detailDTO.getChildName());
			gx.setPartType(detailDTO.getChildType());
		gx.setPartModel(detailDTO.getChildModel());
		gx.setPartPcs(detailDTO.getChildPcs());
		gx.setPartPcsName(detailDTO.getChildPcsName());
		BigDecimal childNumber = detailDTO.getChildNumber();
		gx.setQuantity(childNumber != null ? childNumber : BigDecimal.ONE);

			list.add(gx);
		}

		ProductPartConsistDTO ppc = data.getFirst();
		PartAllRelationship gx = new PartAllRelationship();
		gx.setProcedureCode(ppc.getParentCode());
		gx.setProductName(ppc.getParentName());
		gx.setProcedureNature(enumCodeConfig.getProcedureNatureInner());
		gx.setProductCode(ppc.getParentCode());
		gx.setProductName(ppc.getParentName());
		gx.setRouteState(true);
		gx.setProductModel(ppc.getParentModel());
		gx.setProductPcs(ppc.getParentPcs());
		gx.setProductType(ppc.getParentType());
		gx.setProductPcsName(ppc.getParentPcsName());
		gx.setRouteCode(ppc.getParentCode());
		list.add(gx);
		return list;
	}

	/**
	 *
	 * @description 工艺路线补充工序
	 * @param result
	 * @param productPartProcedure
	 * @throws ExceptionPack
	 *
	 * TODO - methodRedactInfo[enter]
	 */
	private void transdata(List<PartAllRelationship> result, String productPartProcedure) throws ExceptionPack {
		Map<Long, Set<Long>> routeToProcedure = new HashMap<>();

		/**
		 * 单位的补充
		 */
		Set<Long> productPiecesCodes = new HashSet<>();
		result.forEach(a -> {
			if (CollectionUtils.isNotEmpty(a.getRouteBindProcedureCodes())) {
				routeToProcedure.computeIfAbsent(a.getRouteCode(), b -> new HashSet<>()).addAll(a.getRouteBindProcedureCodes());
			}
			productPiecesCodes.add(a.getProductPcs());
			productPiecesCodes.add(a.getPartPcs());
		});

		Map<Long, String> pieceCodeNameMapping =
				piecesService.pieceCodeChnNameMapping(productPiecesCodes, getEnterpriseCode());

		result.forEach(a -> {
			a.setPartPcsName(Optional.ofNullable(pieceCodeNameMapping.get(a.getPartPcs())).orElse(null));
			a.setProductPcsName(Optional.ofNullable(pieceCodeNameMapping.get(a.getProductPcs())).orElse(null));
		});

		/**
		 * 补充  工序
		 */
		Map<Long, PartAllRelationship> map = new HashMap<>();
		result.forEach(a -> {
			if (CollectionUtils.isNotEmpty(a.getRouteBindProcedureCodes())) {
				routeToProcedure.computeIfAbsent(a.getRouteCode(), b -> new HashSet<>()).remove(a.getProcedureCode());
			}
			PartAllRelationship obj = new PartAllRelationship();
			obj.setProductPcsName(a.getProductPcsName());
			obj.setProductPcs(a.getProductPcs());
			obj.setProductCode(a.getProductCode());
			obj.setProductRemark(a.getProductRemark());
			obj.setProductName(a.getProductName());
			obj.setProductModel(a.getProductModel());
			obj.setProductSwitchRoute(a.getProductSwitchRoute());
			obj.setProductType(a.getProductType());

			obj.setRouteState(a.getRouteState());
			obj.setRouteCode(a.getRouteCode());
			obj.setRouteBindProcedureCodes(a.getRouteBindProcedureCodes());

			map.put(a.getRouteCode(), obj);
		});

		Set<Long> sets = routeToProcedure.entrySet().stream().flatMap(a -> a.getValue().stream()).collect(Collectors.toSet());

		Map<Long, ProductPartProcedureModel> procedureMap = new HashMap<>();
		if (CollectionUtils.isNotEmpty(sets)) {
			List<ProductPartProcedureModel> procedureInfos = productPartProcedureMapper.queryListByIds(productPartProcedure, sets);
			procedureInfos.forEach(a -> procedureMap.put(a.getProductPartProcedureCode(), a));
		}

		routeToProcedure.entrySet().forEach(a -> {

			PartAllRelationship obj = map.get(a.getKey());
			if (obj == null) {
				return;
			}

			a.getValue().forEach(b -> {
				ProductPartProcedureModel model = procedureMap.get(b);
				if (model == null) {
					return;
				}
				PartAllRelationship ship = JSON.parseObject(JSON.toJSONString(obj), PartAllRelationship.class);
				ship.setProcedureCode(model.getProductPartProcedureCode());
				ship.setProcedureName(model.getProductPartProcedureSign());
				ship.setProcedureNature(model.getNature());
				ship.setProcedureRemark(model.getRemark());
				result.add(ship);
			});
		});

		/**
		 * 补充复制的工序
		 */
		Set<Long> routeNode = result.stream().map(a -> a.getRouteCode()).collect(Collectors.toSet());

		if (CollectionUtils.isEmpty(routeNode)) {
			return;
		}

		List<ProcessRouteDataRpcDTO> routeList = processRouteDataService.queryByIds(new ArrayList<>(routeNode));

		Map<Long, Map<Long, String>> routeProduce = new HashMap<>();

		/**
		 *  依据工艺路线，获取复制的工序
		 */
		Map<Long, List<ProcessRouteNodeModel>> copyMap = new HashMap<>();
		Set<String> repeat = new HashSet<>();

		Map<Long, String> routeCache = new HashMap<>();

		for (ProcessRouteDataRpcDTO route : routeList) {

			routeCache.put(route.getProcessRouteDataCode(), route.getRouteData());

			for (ProcessRouteNodeModel model : JSON.parseArray(route.getRouteNode(), ProcessRouteNodeModel.class)) {
				if ("true".equals(model.getIsCopy())) {
					if (repeat.contains(model.getProductPartProcedureCode())) {
						continue;
					}
					repeat.add(model.getProductPartProcedureCode());
					copyMap.computeIfAbsent(route.getProcessRouteDataCode(), a -> new ArrayList<>()).add(model);
				} else if (NumberUtil.isNumber(model.getProductPartProcedureCode())) {
					routeProduce.computeIfAbsent(route.getProcessRouteDataCode(),
									a -> new HashMap<>()).
							put(Long.valueOf(model.getProductPartProcedureCode()),
									model.getActivityId());
				}
			}
		}

		for (PartAllRelationship relationship : result) {
			relationship.setRouteData(routeCache.get(relationship.getRouteCode()));
			String activity = routeProduce.getOrDefault(relationship.getRouteCode(), Map.of())
					.getOrDefault(relationship.getProcedureCode(), "");
			if (StringUtils.isBlank(relationship.getProcedureActivity())) {
				relationship.setProcedureActivity(activity);
			}
		}

		for (Map.Entry<Long, PartAllRelationship> entry : map.entrySet()) {
			PartAllRelationship value = entry.getValue();
			if (StringUtils.isBlank(value.getRouteData())) {
				value.setRouteData(routeCache.get(value.getRouteCode()));
			}

		}

		if (MapUtils.isEmpty(copyMap)) {
			return;
		}

		List<ProductPartProcedureModel> procedureInfos = productPartProcedureMapper.queryListByIds(productPartProcedure,
				copyMap.entrySet().stream().
						flatMap(a -> a.getValue().stream()).
						map(a -> Long.valueOf(a.getCopyCode()))
						.collect(Collectors.toSet()));

		Map<String, ProductPartProcedureModel> copySourceProcedureMap = procedureInfos.stream()
				.collect(Collectors.toMap(a -> a.getProductPartProcedureCode() + ""
						, a -> a, (a, b) -> a));

		for (Map.Entry<Long, List<ProcessRouteNodeModel>> entry : copyMap.entrySet()) {

			long routeCode = entry.getKey();
			List<ProcessRouteNodeModel> saveProduce = entry.getValue();

			PartAllRelationship obj = map.get(routeCode);
			if (obj == null) {
				continue;
			}

			saveProduce.forEach(b -> {
				ProductPartProcedureModel model = copySourceProcedureMap.get(b.getCopyCode());
				if (model == null) {
					return;
				}
				PartAllRelationship ship = JSON.parseObject(JSON.toJSONString(obj), PartAllRelationship.class);
				ship.setProcedureCode(model.getProductPartProcedureCode());
				ship.setProcedureName(b.getName());
				ship.setProcedureNature(model.getNature());
				ship.setProcedureRemark(model.getRemark());
				ship.setProcedureActivity(b.getActivityId());
				ship.setProcedureCopy(true);
				result.add(ship);
			});
		}

	}

	@Override
	public RpcPagingDTO<ProductPartRpcDTO> getPageListByKeyword(int current, int size, List<Long> excludedPartCodes, Boolean productOrPart,
	                                                            String keyword, boolean allowOtherAttributeMaterial, long enterpriseCode) throws ExceptionPack {
		try {

			RightCharacteristics rightCharacteristics = rightCharacteristicsJudgeService.judge(enterpriseCode);

			String productPartTableName =
					tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart(), enterpriseCode);

			LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
			qw.eq(ProductPartModel::getState, true);
			if (StrUtil.isNotBlank(keyword)) {
//				qw.and(wrapper -> wrapper.like(ProductPartModel::getProductPartSign, keyword).or().like(ProductPartModel::getModel, keyword).or().like(ProductPartModel::getUnityNo, keyword));
				qw.and(wrapper -> wrapper
						.apply("LOWER(product_part_sign) LIKE LOWER({0})", "%" + keyword + "%")
						.or()
						.apply("LOWER(model) LIKE LOWER({0})", "%" + keyword + "%")
						.or()
						.apply("LOWER(unity_no) LIKE LOWER({0})", "%" + keyword + "%")
				);
			}
			List<Integer> attributeList = rightCharacteristics.getTechnicalMaterialTypes().stream()
					.map(PPAttributeEnum::getValue)
					.distinct()
					.collect(Collectors.toList());

			if (allowOtherAttributeMaterial) {
				if (!attributeList.contains(PPAttributeEnum.OTHER.getValue())) {
					attributeList.add(PPAttributeEnum.OTHER.getValue());
				}
			} else {
				attributeList.remove(PPAttributeEnum.OTHER.getValue());
			}

			if (productOrPart != null) {
				qw.in(ProductPartModel::getAttribute, productOrPart ? attributeList : List.of(1));
			}else {
				attributeList.add(1);
				qw.in(ProductPartModel::getAttribute, attributeList);
			}
			if (excludedPartCodes != null && !excludedPartCodes.isEmpty()) {
				qw.notIn(ProductPartModel::getProductPartCode, excludedPartCodes);
			}
			qw.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode());
			qw.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE).orderByAsc(ProductPartModel::getAttribute).orderByDesc(ProductPartModel::getCreateTime);
			Page<ProductPartModel> page = new Page<>(current, size);
			RequestTableHelper.setTableName(productPartTableName);
			Page<ProductPartModel> pageList = this.page(page, qw);
			List<ProductPartModel> resultModelList = Convert.toList(ProductPartModel.class, pageList.getRecords());
			// 转换为 ProductPartRpcDTO 列表
			List<ProductPartRpcDTO> datas = Convert.toList(ProductPartRpcDTO.class, resultModelList);
			for (ProductPartRpcDTO data : datas) {
				data.setPartUnityNo(data.getUnityNo());
				data.setName(data.getProductPartSign());
			}
			return new RpcPagingDTO<>(datas, pageList.getTotal());
		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("query IProductPartService.getPageList failed").build());
		}
	}


	@Override
	@Transactional(rollbackFor = Exception.class)
	public void productPartDrawingDelete(ProductPartRpcRequest requestData) {
		// 新技术部-产品零件表
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
		Set<Long> deleteSet = new HashSet<>(requestData.getFileIdList());
		String fileIds = deleteSet.stream()
				.map(String::valueOf)
				.collect(Collectors.joining(","));
		LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
		qw.eq(ProductPartModel::getState, Boolean.TRUE).eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode());
		// 修改查询条件
		qw.apply("files && ARRAY[" + fileIds + "]");
		// 查询产品、零件信息
		RequestTableHelper.setTableName(productPartTableName);
		List<ProductPartModel> productPartModelList = productPartMapper.selectList(qw);
		for (ProductPartModel productPartModel : productPartModelList) {
			Long[] originalFiles = productPartModel.getFiles();
			if (originalFiles == null) continue; // 空值处理
			// 过滤掉需要删除的文件ID
			List<Long> filteredFiles = Arrays.stream(originalFiles)
					.filter(file -> !deleteSet.contains(file))
					.toList();
			// 转换为数组并设置回对象
			productPartModel.setFiles(filteredFiles.toArray(new Long[0]));
		}
		RequestTableHelper.setBatchTableName(productPartTableName);
		try (AutoCloseable ignored = RequestTableHelper::clearBatchTableName) {
			RequestTableHelper.setBatchTableName(productPartTableName);
			this.updateBatchById(productPartModelList);
		} catch (Exception e) {
			log.error("批量操作失败: 表名={}, 数据大小={}", productPartTableName, productPartModelList.size(), e);
		}
	}

	@Override
	public String getNextUnityNo(ProductPartRpcRequest requestData) throws AssertException {
		// 获取表代码-统一单号生成逻辑提取
		return productPartCommonService.getGlobalSerialUnityNo(requestData.getAttribute(),
				requestData.getProductPartTypeCode(), Boolean.TRUE);
	}

	@Override
	public void unityNoRefreshUpdate(ProductPartRpcRequest requestData) throws ExceptionPack {
		// 新技术部-产品零件表
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
		// 新-出库详情
		String outboundOrderDetailTableName = tableFactory.getTableName(tableFactory.module.getWarehouse(),
				tableFactory.table.getOutboundOrderDetail());
		if (ObjectUtil.isEmpty(requestData.getProductPartTypeCodeList())) {
			log.warn(" start unityNoRefreshUpdate 没有可更新的产品零件 typeCode: {}", JSONUtil.toJsonStr(requestData));
			return;
		}
		try {
			for (Long typeCode : requestData.getProductPartTypeCodeList()) {
				// 可以进行更新的对象
				List<ProductPartModel> queryNotExistsSchedule = productPartMapper.queryNotExistsSchedule(typeCode, getEnterpriseCode(),
						productPartTableName, outboundOrderDetailTableName);
				// 不可以进行更新的对象
				List<ProductPartModel> queryExistsSchedule = productPartMapper.queryExistsSchedule(typeCode, getEnterpriseCode(),
						productPartTableName, outboundOrderDetailTableName);
				log.info(" \n log existsSchedule：\n ✅可以更新的编号是 {} \n ❌不可以更新的编号是 {}",
						JSONUtil.toJsonStr(queryNotExistsSchedule),
						JSONUtil.toJsonStr(queryExistsSchedule));
				if (ObjectUtil.isEmpty(queryNotExistsSchedule)) {
					continue;
				}
				Integer attribute = extractAttributeFromFirstModel(queryNotExistsSchedule);
				log.info(" log unityNoRefreshUpdate 开始批量更新编号 分类 code typeCode ：{} \n 本次更新数量 {}", typeCode, queryNotExistsSchedule.size());
				List<Map<String, Object>> unityNoList = productPartCommonService.getGlobalUnityNoByList(attribute, typeCode, queryNotExistsSchedule);
				// 校验数量是否一致
				if (queryNotExistsSchedule.size() != unityNoList.size()) {
					log.error(" \n log unityNoRefreshUpdate ❌ 产品零件数量与生成编号数量不一致，错误请检查 productPartModels {} unityNoList {}",
							JSONUtil.toJsonStr(queryNotExistsSchedule), JSONUtil.toJsonStr(unityNoList));
					return;
				}
				// 更新每个模型的 unityNo
				for (int i = 0; i < unityNoList.size(); i++) {
					Map<String, Object> result = unityNoList.get(i);
					String unityNo = String.valueOf(result.getOrDefault("unityNo", ""));
					ProductPartModel productPartModel = queryNotExistsSchedule.get(i);
					if (StrUtil.isNotBlank(unityNo) && unityNo.equals(productPartModel.getUnityNo())) {
						String resultUnityNo = String.valueOf(result.get("resultUnityNo"));
						productPartModel.setUnityNo(resultUnityNo);
					}
				}
				// 批量更新数据库
				try (AutoCloseable ignored = RequestTableHelper::clearBatchTableName) {
					RequestTableHelper.setBatchTableName(productPartTableName);
					this.updateBatchById(queryNotExistsSchedule);
				} catch (Exception e) {
					log.error("批量操作失败: 表名={}", queryNotExistsSchedule, e);
					throw new ExceptionPack(e, ExceptionMsg.builder("fail to save sellContractProductChanges").build());
				}
			}
		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("unityNoRefreshUpdate fail").build());
		}
	}

	@Override
	public List<ProductPartExportExcelDTO> getProductPartExportList(ProductPartRpcRequest requestData) throws ExceptionPack {
		// 新技术部-产品零件表
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
		LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
		if (requestData.getProductPartCode() != null) {
			qw.eq(ProductPartModel::getProductPartCode, requestData.getProductPartCode());
		}
		if (requestData.getProductPartTypeCode() != null) {
			ProductPartTypeRpcRequest typeRpcRequest =
					new ProductPartTypeRpcRequest().setProductPartTypeCode(requestData.getProductPartTypeCode());
			List<ProductPartTreeNodeRpcDTO> productPartTreeNodeRpcDTOS = new ArrayList<>();
			if (ObjectUtil.isNotEmpty(typeRpcRequest)) {
				productPartTreeNodeRpcDTOS = productPartTypeService.findProductPartTypeChildren(typeRpcRequest);
			}
			if (ObjectUtil.isNotEmpty(productPartTreeNodeRpcDTOS)) {
				List<Long> productPartTypeList =
						productPartTreeNodeRpcDTOS.stream().map(ProductPartTreeNodeRpcDTO::getProductPartTypeCode).toList();
				qw.in(ProductPartModel::getProductPartTypeCode, productPartTypeList);
			} else {
				qw.eq(ProductPartModel::getProductPartTypeCode, requestData.getProductPartTypeCode());
			}
		}
		if (StrUtil.isNotBlank(requestData.getName())) {
			qw.like(ProductPartModel::getProductPartSign, requestData.getName());
		}
		if (StrUtil.isNotBlank(requestData.getModel())) {
			qw.like(ProductPartModel::getModel, requestData.getModel());
		}
		if (requestData.getState() != null) {
			qw.eq(ProductPartModel::getState, requestData.getState());
		}
		if (requestData.getAttribute() != null) {
			qw.eq(ProductPartModel::getAttribute, requestData.getAttribute());
		}
		if (ObjectUtil.isNotEmpty(requestData.getStandard())) {
			qw.eq(ProductPartModel::getStandard, requestData.getStandard());
		}
		// 普通-聚合搜索-名称+型号
		if (StrUtil.isNotBlank(requestData.getSearchName())) {
			qw.and(wp -> wp.like(ProductPartModel::getProductPartSign, requestData.getSearchName())
					.or().like(ProductPartModel::getModel, requestData.getSearchName()));
		}
		// 编号不区分大小写模糊查询
		if (StrUtil.isNotBlank(requestData.getUnityNo())) {
			String keyword = "%" + requestData.getUnityNo() + "%";
			qw.apply("lower(unity_no) like lower({0})", keyword);
		}
		// 反查询-查询这个工艺路线在哪些产品、部件使用
		if (StrUtil.isNotBlank(requestData.getReverseProcessRoute())) {
			List<Long> productCodeResultList = productPartCommonService.getReverseProcessRoute(requestData.getReverseProcessRoute());
			if (ObjectUtil.isEmpty(productCodeResultList)) {
				return List.of();
			}
			qw.in(ProductPartModel::getProductPartCode, productCodeResultList);
		}
		// 反查询-根据产品、部件、零件、原料 查询所在BOM列表
		if (StrUtil.isNotBlank(requestData.getReverseName())) {
			List<Long> productCodeResultList = productPartCommonService.getReverseName(requestData.getReverseName());
			if (ObjectUtil.isEmpty(productCodeResultList)) {
				return List.of();
			}
			qw.in(ProductPartModel::getProductPartCode, productCodeResultList);
		}

		if (ObjectUtil.isNotEmpty(requestData.getCodes())) {
			qw.in(ProductPartModel::getProductPartCode, requestData.getCodes());
		}
		// 分页处理 支持传入数量查询范围
		if (requestData.getOffset() != null && requestData.getPageSize() != null) {
			int offset = requestData.getOffset();
			int pageSize = requestData.getPageSize();
			qw.last("LIMIT " + pageSize + " OFFSET " + offset);
		}
		qw.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode());
		qw.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE).orderByDesc(ProductPartModel::getId);
		RequestTableHelper.setTableName(productPartTableName);
		List<ProductPartExportExcelDTO> resultModelList = Convert.toList(ProductPartExportExcelDTO.class, productPartMapper.selectList(qw));
		// 获取文件ID列表
		List<Long> getFileIdList = resultModelList.stream().map(ProductPartExportExcelDTO::getFiles).filter(Objects::nonNull)
				.flatMap(Arrays::stream).collect(Collectors.toList());
		Map<Long, EnterpriseStorageSpaceFileDetailRpcDTO> resultFileMap = new HashMap<>();
		if (!getFileIdList.isEmpty()) {
			// 获取文件详细信息
			EnterpriseStorageSpaceFileDetailsRpcDTO getFileInfoResult =
					storageSpaceFileService.details(getFileIdList, getEnterpriseCode());
			// 使用 Optional 处理可能的空值情况
			Optional.ofNullable(getFileInfoResult)
					.map(EnterpriseStorageSpaceFileDetailsRpcDTO::getDetails)
					.ifPresent(details ->
							resultFileMap.putAll(details.stream()
									.collect(Collectors.toMap(EnterpriseStorageSpaceFileDetailRpcDTO::getFileId, Function.identity())))
					);
		}
		for (ProductPartExportExcelDTO rpcDTO : resultModelList) {
			// 组装 文件信息
			List<EnterpriseStorageSpaceFileDetailRpcDTO> fileInfoList = Arrays.stream(rpcDTO.getFiles())
					.map(resultFileMap::get).filter(Objects::nonNull).toList();
			String originalFileName = fileInfoList.stream()
					.map(EnterpriseStorageSpaceFileDetailRpcDTO::getOriginalFileName) // 假设 getOriginalFileName() 是获取原始文件名的方法
					.collect(Collectors.joining(",")); // 使用逗号拼接
			rpcDTO.setFileName(originalFileName);
		}
		return resultModelList;
	}

	/**
	 * @description 提取第一个 model 的 attribute 字段，用于生成 unityNo
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/6/25 17:55
	 * @department: Product development
	 */
	private Integer extractAttributeFromFirstModel(List<ProductPartModel> productPartModels) throws AssertException {
		if (productPartModels == null || productPartModels.isEmpty()) {
			throw new AssertException(ExceptionMsg.builder("attribute 为空")
					.msgView("无法获取 attribute 字段，请检查数据").build());
		}
		return Optional.ofNullable(productPartModels.getFirst())
				.map(ProductPartModel::getAttribute)
				.orElseThrow(() -> new AssertException(ExceptionMsg.builder("attribute 为空")
						.msgView("无法获取 attribute 字段，请检查数据").build()));
	}

	@Override
	public void processPartDataRecovery() throws ExceptionPack {
		StopWatch stopWatch = new StopWatch("processPartDataRecovery");
		stopWatch.start("processPartDataRecovery Start");
		long lastId = 0;
		int pageSize = 100;
		int syncCount = 0;
		int processingCount = 0;// 真实处理数量
		log.info("开始执行工序零件数据校对修复，企业编码: {}, 每页数量: {}", getEnterpriseCode(), pageSize);
		List<ProcedurePartRelationshipModel> batchUpdateList = new ArrayList<>();
		// 基于ID的游标分页 数据
		while (true) {
			LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
			qw.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode())
					//.eq(ProductPartModel::getProductPartCode, 424370054186532864L)
					.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE)
					.gt(ProductPartModel::getId, lastId) // 基于上一次的 ID 查询下一页
					.orderByAsc(ProductPartModel::getId)
					.last("LIMIT " + pageSize);
			List<ProductPartModel> partModels = productPartMapper.selectList(qw);
			if (ObjectUtil.isEmpty(partModels)) {
				break;
			}
			List<Long> modelListData = ExtractUtil.streamMapToList(Long::valueOf, partModels,
					ProductPartModel::getProductPartCode);
			LambdaQueryWrapper<ProductPartRouteRelationshipModel> query = Wrappers.lambdaQuery();
			query.in(ProductPartRouteRelationshipModel::getProductPartCode, modelListData);
			query.eq(ProductPartRouteRelationshipModel::getEnterpriseCode, getEnterpriseCode());
			query.eq(ProductPartRouteRelationshipModel::getDeleteFlag, Boolean.FALSE);
			// 查询
			List<ProductPartRouteRelationshipModel> getProductPartRouteRelationship = productPartRouteRelationshipService.list(query);
			if (ObjectUtil.isEmpty(getProductPartRouteRelationship)) {
				lastId = partModels.getLast().getId();
				syncCount += partModels.size();
				continue;
			}
			log.info("getProductPartRouteRelationship {}", JSONUtil.toJsonStr(getProductPartRouteRelationship));
			for (ProductPartRouteRelationshipModel routeRelationship : getProductPartRouteRelationship) {
				ProcessRouteDataModel processRouteDataModel =
						processRouteDataService.getById(routeRelationship.getProcessRouteDataCode());
				ProcessRouteDataDetailRpcDTO processRouteDataDetailRpcDTO =
						Convert.convert(ProcessRouteDataDetailRpcDTO.class, processRouteDataModel);
				List<Map<String, Object>> result = productPartCommonService.routeNodeModels(processRouteDataDetailRpcDTO.getRouteNode());
				if (result.size() > 1) {
					continue;
				}
				for (Map<String, Object> stringObjectMap : result) {
					if (Boolean.TRUE.equals(stringObjectMap.get("state"))) {
						Object procedureCode = stringObjectMap.get("productPartProcedureCode");
						LambdaQueryWrapper<ProcedurePartRelationshipModel> qwRel = Wrappers.lambdaQuery();
						qwRel.eq(ProcedurePartRelationshipModel::getUniqueId, routeRelationship.getUniqueId())
								.eq(ProcedurePartRelationshipModel::getUniqueId, routeRelationship.getUniqueId())
								.isNull(ProcedurePartRelationshipModel::getProcedureCode)
								.eq(ProcedurePartRelationshipModel::getEnterpriseCode, getEnterpriseCode())
								.eq(ProcedurePartRelationshipModel::getDeleteFlag, Boolean.FALSE);
						for (ProcedurePartRelationshipModel procedurePartRelationshipModel : procedurePartRelationshipService.list(qwRel)) {
							procedurePartRelationshipModel.setProcedureCode(Long.parseLong(String.valueOf(procedureCode)));
							batchUpdateList.add(procedurePartRelationshipModel); // 加入批量更新列表
							processingCount++;
						}
					}
				}
			}
			// 批量更新
			if (!batchUpdateList.isEmpty()) {
				procedurePartRelationshipService.updateBatchById(batchUpdateList);
				batchUpdateList.clear();
			}
			lastId = partModels.getLast().getId(); // 更新 lastId 为最后一条记录的 ID
			syncCount += partModels.size();
		}
		stopWatch.stop();
		log.info("初始化-工序零件数据校对修复 结束, 总处理条数 count:{} 真实处理数量:{} 请求执行耗时：{}", syncCount, processingCount, stopWatch.prettyPrint(TimeUnit.MILLISECONDS));
	}

	/**
	 * 记录删除配套物料日志
	 */
	@OptRecord(
			desc = {"配套物料删除了: #{#p0}"},
			primaryKey = "#{#p1}",
			dynamicType = "#{#p2}"
	)
	public void logDeleteMaterial(String materialNames, Long productPartCode, StaffOperationLogTypeEnum type) {
		// AOP切面自动记录日志
	}

	/**
	 * 记录新增配套物料日志
	 */
	@OptRecord(
			desc = {"配套物料添加了: #{#p0}"},
			primaryKey = "#{#p1}",
			dynamicType = "#{#p2}"
	)
	public void logAddMaterial(String materialNames, Long productPartCode, StaffOperationLogTypeEnum type) {
		// AOP切面自动记录日志
	}

	/**
	 * 记录工序变更日志
	 */
	@OptRecord(
			desc = {"#{#p0}物料的工序由【#{#p1}】变更为【#{#p2}】"},
			primaryKey = "#{#p3}",
			dynamicType = "#{#p4}",
			recordIfChanged = true,
			compareFields = {"p1", "p2"}
	)
	public void logUpdateProcedure(String materialName, String oldProcedure, String newProcedure, Long productPartCode, StaffOperationLogTypeEnum type) {
		// AOP切面自动记录日志
	}
}
