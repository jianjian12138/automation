/*
 * Copyright (c) Zhongzao Software Co. LTD 2022-2062 All rights reserved
 */
package com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart.action;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.StopWatch;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.futurecraftsmen.pms.api.domain.ServiceErrorCode;
import com.futurecraftsmen.pms.api.dto.MultipartFileRpcDTO;
import com.futurecraftsmen.pms.api.dto.base.RpcPagingDTO;
import com.futurecraftsmen.pms.common.domain.excel.ParseExcelResult;
import com.futurecraftsmen.pms.common.domain.exception.ExcelException;
import com.futurecraftsmen.pms.common.excel.multi.ExcelMultiModelsProcessor;
import com.futurecraftsmen.pms.dm.api.service.base.staff.StaffService;
import com.futurecraftsmen.pms.dm.api.service.base.staff.dto.StaffRpcDTO;
import com.futurecraftsmen.pms.dp.api.domain.GlobalSerialNumberResponse;
import com.futurecraftsmen.pms.file.api.service.EnterpriseStorageSpaceFileService;
import com.futurecraftsmen.pms.file.api.service.dto.EnterpriseStorageSpaceFileDetailRpcDTO;
import com.futurecraftsmen.pms.file.api.service.dto.EnterpriseStorageSpaceFileDetailsRpcDTO;
import com.futurecraftsmen.pms.file.api.service.dto.StorageObjectRpcDTO;
import com.futurecraftsmen.pms.file.api.service.dto.StorageObjectRpcRequest;
import com.futurecraftsmen.pms.service.configuration.RequestTableHelper;
import com.futurecraftsmen.pms.service.configuration.xihang.QueryPartNo;
import com.futurecraftsmen.pms.service.domain.BaseExcelHeaderGenerator;
import com.futurecraftsmen.pms.service.domain.common.constant.CommonConstant;
import com.futurecraftsmen.pms.service.domain.extract.ExtractUtil;
import com.futurecraftsmen.pms.starter.domain.starter.PmsStarter;
import com.futurecraftsmen.pms.technical.api.domain.technical.items.ProductPartQualityItemsRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.items.ProductPartQualityItemsRpcRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.*;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.excel.xihang.part.PartExcelRpcModelXh;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.excel.xihang.product.ProductExcelRpcModelXh;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.xihang.ProductPartBatchAddRpcRequestXh;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.xihang.XiHangProductPartDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.xihang.comp.ProductPartChild;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.xihang.comp.ProductPartConsistRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.type.ProductPartTreeNodeRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.type.ProductPartTypeRpcRequest;
import com.futurecraftsmen.pms.technical.api.service.IPiecesService;
import com.futurecraftsmen.pms.technical.api.service.technical.IProductPartQualityItemsService;
import com.futurecraftsmen.pms.technical.service.common.enums.TechnicalErrorEnum;
import com.futurecraftsmen.pms.technical.service.dao.technical.IProcedurePartRelationshipMapper;
import com.futurecraftsmen.pms.technical.service.dao.technical.IProductPartCompMapper;
import com.futurecraftsmen.pms.technical.service.dao.technical.IProductPartMapper;
import com.futurecraftsmen.pms.technical.service.dao.technical.IProductPartRouteRelationshipMapper;
import com.futurecraftsmen.pms.technical.service.dao.warehouse.IPositionMapper;
import com.futurecraftsmen.pms.technical.service.dao.warehouse.IStockMapper;
import com.futurecraftsmen.pms.technical.service.domain.StandardEnum;
import com.futurecraftsmen.pms.technical.service.domain.StateEnum;
import com.futurecraftsmen.pms.technical.service.domain.technical.procedure.ProcedurePartRelationshipModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.process.ProcessRouteDataModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.productpart.ProductPartModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.productpart.ProductPartRouteRelationshipModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.productpart.comp.ProductPartConsistQueryResult;
import com.futurecraftsmen.pms.technical.service.domain.technical.productpart.xihang.PartExcelValidatorXh;
import com.futurecraftsmen.pms.technical.service.domain.technical.productpart.xihang.ProductExcelValidatorXh;
import com.futurecraftsmen.pms.technical.service.domain.technical.type.ProductPartTypeModel;
import com.futurecraftsmen.pms.technical.service.domain.warehouse.PositionModel;
import com.futurecraftsmen.pms.technical.service.domain.warehouse.StockModel;
import com.futurecraftsmen.pms.technical.service.impl.inner.TableNameFactory;
import com.futurecraftsmen.pms.technical.service.impl.technical.ProductPartTypeServiceImpl;
import com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart.abstracts.AbstractProductPart;
import com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart.action.unified.IProductPartCommonServiceImpl;
import com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart.action.unified.TechnicalUnifiedDataService;
import com.futurecraftsmen.pms.technical.service.impl.technical.route.ProcessRouteDataServiceImpl;
import com.futurecraftsmen.pms.technical.service.impl.warehouse.StockServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import org.aerie.forest.core.brick.domain.enums.PPAttributeEnum;
import org.aerie.forest.core.brick.domain.view.CodeMapName;
import org.aerie.forest.core.brick.exception.ExceptionMsg;
import org.aerie.forest.core.brick.exception.ExceptionPack;
import org.aerie.forest.core.brick.exception.assertprocess.AssertException;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.futurecraftsmen.pms.technical.service.util.CommonUtil.getEnterpriseCode;
import static com.futurecraftsmen.pms.technical.service.util.CommonUtil.getUserCode;

/**
 * @description 希航 策略实现
 *
 * @author qierkang
 * @organization futurecraftsmen
 * @date Created in 2025/4/25 00:06
 * @department: Product development
 */
@Slf4j
@Component
public class BZNProductPartAction extends AbstractProductPart {

	@Resource
	private TableNameFactory tableFactory;
	@Resource
	private ProductPartTypeServiceImpl productPartTypeService;
	@Resource
	private IProductPartMapper productPartMapper;
	@Resource
	private IProductPartCompMapper productPartCompMapper;
	@Resource
	private IPiecesService piecesService;
	@DubboReference(check = false, retries = 0)
	private StaffService staffService;
	@DubboReference(group = "pms", check = false, retries = 0)
	private EnterpriseStorageSpaceFileService storageSpaceFileService;
	@Resource
	private IProductPartQualityItemsService qualityItemsService;
	@Resource
	private IProductPartCommonServiceImpl productPartCommonService;
	@Resource
	private ProductExcelValidatorXh productExcelValidatorXh;
	@Resource
	private PartExcelValidatorXh partExcelValidatorXh;
	@Resource
	private TechnicalUnifiedDataService unifiedDataService;
	@Resource
	private IStockMapper stockMapper;
	@Resource
	private ProcessRouteDataServiceImpl processRouteDataService;
	@Resource
	private IProcedurePartRelationshipMapper procedurePartRelationshipMapper;
	@Resource
	private IProductPartRouteRelationshipMapper productPartRouteRelationshipMapper;

	@Resource
	private StockServiceImpl iStockService;
	@Resource
	private IPositionMapper iPositionMapper;

	@Override
	@QueryPartNo(partCodeField = "productPartCode", partNoField = "partUnityNo")
	public RpcPagingDTO<XiHangProductPartDTO> getPageList(ProductPartPageRequest requestData) throws ExceptionPack {
		try {
			// 新技术部-产品零件表
			String productPartTableName =
					tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
			// 产品零件-绑定组成表
			String productPartCompTableName =
					tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPartComp());

			// 新技术部-库存表
			String stockTableName =
					tableFactory.getTableName(tableFactory.module.getWarehouse(), tableFactory.table.getStock());
			//新-仓位表名
			String positionTableName =
					tableFactory.getTableName(tableFactory.module.getWarehouse(), tableFactory.table.getPosition());

			LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
			if (requestData.getProductPartCode() != null) {
				qw.eq(ProductPartModel::getProductPartCode, requestData.getProductPartCode());
			}
			if (requestData.getFileId() != null) {
				// 图纸ID数组字段查询，PostgreSQL array_position
				qw.apply("array_position(files, {0}) > 0", requestData.getFileId());
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
			if (ObjectUtil.isNotNull(requestData.getAttributeList())) {
				qw.in(ProductPartModel::getAttribute, requestData.getAttributeList());
			}
			if (ObjectUtil.isNotEmpty(requestData.getStandard())) {
				qw.eq(ProductPartModel::getStandard, requestData.getStandard());
			}
			// 编号不区分大小写模糊查询
			if (StrUtil.isNotBlank(requestData.getUnityNo())) {
				String cleanedUnityNo = requestData.getUnityNo().replaceAll("\\s+", "");
				String keyword = "%" + cleanedUnityNo + "%";
				qw.apply("lower(unity_no) like lower({0})", keyword);
			}
			// 普通-聚合搜索-名称+型号
			if (StrUtil.isNotBlank(requestData.getSearchName())) {
				qw.and(wp -> wp.like(ProductPartModel::getProductPartSign, requestData.getSearchName())
						.or().like(ProductPartModel::getModel, requestData.getSearchName()));
			}
			// 反查询-聚合搜索-名称+型号+编号
			if (StrUtil.isNotBlank(requestData.getReverseLookup())) {
				List<Long> resultCodeList = productPartCommonService.getReverseLookup(requestData.getReverseLookup());
				if (ObjectUtil.isEmpty(resultCodeList)) {
					return new RpcPagingDTO<>(Collections.emptyList(), CommonConstant.NUMBER_ZERO);
				}
				qw.in(ProductPartModel::getProductPartCode, resultCodeList);
			}

			if (StrUtil.isNotBlank(requestData.getPartUnityNo())) {
				String cleanedUnityNo = requestData.getPartUnityNo().replaceAll("\\s+", "");
				String keyword = "%" + cleanedUnityNo + "%";
				qw.apply("lower(unity_no) like lower({0})", keyword);
			}
			qw.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode()).orderByDesc(ProductPartModel::getId);
			Page<ProductPartModel> page = new Page<>(requestData.getCurrent(), requestData.getSize());
			RequestTableHelper.setTableName(productPartTableName);
			Page<ProductPartModel> pageList = productPartMapper.selectPage(page, qw);
			List<ProductPartModel> resultModelList = Convert.toList(ProductPartModel.class, pageList.getRecords());
			// 计量单位数据准备
			Set<Long> productPiecesCodes =
					new HashSet<>(ExtractUtil.streamMapToList(Long::valueOf, resultModelList, ProductPartModel::getPcs));
			// 获取产品与名称映射
			Map<Long, String> pieceCodeNameMapping =
					piecesService.pieceCodeChnNameMapping(productPiecesCodes, getEnterpriseCode());
			// 员工信息准备
			List<Long> staffCodes = ExtractUtil.streamMapToList(Long::valueOf, resultModelList,
					ProductPartModel::getContactPerson, ProductPartModel::getCreator, ProductPartModel::getWarehousePerson);

			// 获取员工与名称映射
			Map<Long, String> resultStaffMap = new HashMap<>();
			if (!staffCodes.isEmpty()) {
				resultStaffMap = staffService.queryStaffByStaffCodes(staffCodes, getEnterpriseCode()).stream()
						.collect(Collectors.toMap(StaffRpcDTO::getStaffCode, StaffRpcDTO::getStaffName,
								(existingValue, newValue) -> newValue));
			}

			// 检验准则信息准备
			List<Long> qualityCodesNeedQuery = resultModelList.stream()
					.filter(dto -> dto.getQualityCodeList() != null) // 过滤掉 qualityCodeList 为 null 的元素
					.flatMap(dto -> Arrays.stream(dto.getQualityCodeList())) // 展开 qualityCodeList 数组
					.filter(Objects::nonNull) // 过滤掉 qualityCodeList 中为 null 的元素
					.distinct() // 去重
					.collect(Collectors.toList());
			Map<Long, ProductPartQualityItemsRpcDTO> qualityItemMap = new HashMap<>();
			if (CollUtil.isNotEmpty(qualityCodesNeedQuery)) {
				ProductPartQualityItemsRpcRequest itemsRpcRequest = new ProductPartQualityItemsRpcRequest();
				itemsRpcRequest.setProductPartQualityCodeList(qualityCodesNeedQuery);
				List<ProductPartQualityItemsRpcDTO> itemsRpcDTOS = qualityItemsService.getProductPartQualityItemsList(itemsRpcRequest);
				if (CollUtil.isNotEmpty(itemsRpcDTOS)) {
					qualityItemMap = itemsRpcDTOS.stream()
							.collect(Collectors.toMap(ProductPartQualityItemsRpcDTO::getProductPartQualityCode, Function.identity(),
									(existingValue, newValue) -> newValue));
				}
			}

			// 获取文件ID列表
			List<Long> getFileIdList = resultModelList.stream().map(ProductPartModel::getFiles).filter(Objects::nonNull)
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
			// 转换为 ProductPartRpcDTO 列表
			List<XiHangProductPartDTO> data = Convert.toList(XiHangProductPartDTO.class, resultModelList);

			if (CollUtil.isNotEmpty(data)) {
				//处理对应产品库存仓位信息
				LambdaQueryWrapper<StockModel> queryStock = Wrappers.lambdaQuery();
				queryStock.in(StockModel::getProductPartCode, data.stream().map(XiHangProductPartDTO::getProductPartCode).distinct().toList());
				queryStock.eq(StockModel::getEnterpriseCode, getEnterpriseCode());
				queryStock.eq(StockModel::getDeleteFlag, Boolean.FALSE);
				RequestTableHelper.setTableName(stockTableName);
				List<StockModel> stockModelList = iStockService.list(queryStock);
				//stockModelList 切换成map key是productPartCode value是StockModel的stockCode
				Map<Long, Long> stockCodeMap = stockModelList.stream().collect(Collectors.toMap(StockModel::getProductPartCode, StockModel::getStockCode));
				Map<Long, BigDecimal> stockNumMap = stockModelList.stream().collect(Collectors.toMap(StockModel::getProductPartCode, StockModel::getTotalInventory));
				Map<Long, BigDecimal> stockLockNumMap = stockModelList.stream().collect(Collectors.toMap(StockModel::getProductPartCode, StockModel::getLockInInventor));
				//获取对应库存仓位信息
				List<Long> positionCodes = stockModelList.stream().filter(e -> ArrayUtil.isNotEmpty(e.getShippingSpace())).toList().stream().flatMap(e -> Arrays.stream(e.getShippingSpace())).distinct().toList();
				Map<Long, List<XiHangProductPartDTO.PositionPageModel>> productPartPositionInfoMap = new HashMap<>();
				Map<Long, PositionModel> positionModelMap = new HashMap<>();
				if (CollUtil.isNotEmpty(positionCodes)) {
					positionModelMap = iPositionMapper.getPositionsByCodeList(positionCodes, getEnterpriseCode(), positionTableName).stream()
							.collect(Collectors.toMap(PositionModel::getPositionCode, Function.identity()));
				}
				for (StockModel e : stockModelList) {
					if (ArrayUtil.isNotEmpty(e.getShippingSpace())) {
						List<XiHangProductPartDTO.PositionPageModel> positionPageModelList = new ArrayList<>();
						List<Long> longs = Arrays.asList(e.getShippingSpace());
						for (Long e1 : longs) {
							XiHangProductPartDTO.PositionPageModel positionPageModel = new XiHangProductPartDTO.PositionPageModel();
							PositionModel positionModel = positionModelMap.get(e1);
							if (ObjectUtil.isNotEmpty(positionModel)) {
								positionPageModel.setWarehorse(positionModel.getWarehorse());
								positionPageModel.setPositionSign(positionModel.getPositionName());
								positionPageModel.setPositionCode(positionModel.getPositionCode());
								positionPageModelList.add(positionPageModel);
							}
						}
						productPartPositionInfoMap.put(e.getProductPartCode(), positionPageModelList);
					} else {
						productPartPositionInfoMap.put(e.getProductPartCode(), List.of());
					}
				}
				data.forEach(e -> {
					e.setStockCode(stockCodeMap.get(e.getProductPartCode()));
					e.setTotalInventory(stockNumMap.getOrDefault(e.getProductPartCode(), BigDecimal.ZERO));
					e.setLockInInventor(stockLockNumMap.getOrDefault(e.getProductPartCode(), BigDecimal.ZERO));
					e.setPositionPageModelList(productPartPositionInfoMap.get(e.getProductPartCode()));
				});
			}

			// 批量收集查询条件并执行批量查询
			List<Map<String, Object>> queryConditions = new ArrayList<>();
			for (XiHangProductPartDTO rpcDTO : data) {
				Map<String, Object> condition = new HashMap<>();
				condition.put("parentCode", rpcDTO.getProductPartCode());
				condition.put("parentAttribute", rpcDTO.getAttribute());
				queryConditions.add(condition);
			}

			// 使用批量查询方法
			Map<String, Long> countMap = queryConditions.isEmpty() ? new HashMap<>() :
					productPartCompMapper.batchSelectCount(productPartCompTableName, getEnterpriseCode(), queryConditions).stream().collect(Collectors.toMap(ProductPartConsistQueryResult::getKey, ProductPartConsistQueryResult::getCount));
			if (countMap == null) {
				countMap = new HashMap<>();
			}

			// 根据 页面 code 获取对应编码 code
			GlobalSerialNumberResponse res = productPartCommonService.getGlobalSerialNumber(requestData.getAttribute());
			for (XiHangProductPartDTO rpcDTO : data) {
				rpcDTO.setName(rpcDTO.getProductPartSign());
				rpcDTO.setGenerateWay(res.getGenerateWay());
				rpcDTO.setPicSize(res.getPicSize());
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
				// 组装 文件信息
				List<EnterpriseStorageSpaceFileDetailRpcDTO> fileInfoList = Arrays.stream(rpcDTO.getFiles())
						.map(resultFileMap::get).filter(Objects::nonNull).collect(Collectors.toList());
				rpcDTO.setFileInfoList(fileInfoList);
				// 组装返回 检验准则 模型
				if (ObjectUtil.isNotEmpty(rpcDTO.getQualityCodeList())) {
					List<ProductPartQualityItemsRpcDTO> itemsRpcDTOS = Arrays.stream(rpcDTO.getQualityCodeList())
							.filter(Objects::nonNull)
							.map(qualityItemMap::get)
							.filter(Objects::nonNull)
							.collect(Collectors.toList());
					rpcDTO.setQualityItemsInfoList(itemsRpcDTOS);
				}
				// 查看详情，按钮是否显示 false 不显示 true 显示
				// 使用批量查询结果设置查看详情按钮状态
				String key = rpcDTO.getProductPartCode() + "_" + rpcDTO.getAttribute();
				Long compCount = countMap.getOrDefault(key, 0L);
				rpcDTO.setViewDetails(compCount > CommonConstant.NUMBER_ZERO);
				if (StrUtil.isNotBlank(rpcDTO.getExtra())) {
					rpcDTO.setExtraInfo(JSONUtil.toBean(rpcDTO.getExtra(), ProductPartExtraInfo.class));
				}
			}
			return new RpcPagingDTO<>(data, pageList.getTotal());
		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("query BZNProductPartAction.getPageList failed").build());
		}
	}

	//@Override
	//@QueryPartNo(partCodeField = "productPartCode", partNoField = "partUnityNo")
	//public RpcPagingDTO<XiHangProductPartDTO> getPageList(ProductPartPageRequest requestData) throws ExceptionPack {
	//	try {
	//		// 转换为 ProductPartRpcDTO 列表
	//		//List<ReqProductPartRpcDTO> partModels = new ArrayList<>();
	//		//ReqProductPartRpcDTO productPartRpcDTO = new ReqProductPartRpcDTO();
	//		//productPartRpcDTO.setAttribute(1);
	//		//productPartRpcDTO.setProductPartSign("测试 dubbo 序列化");
	//		//partModels.add(productPartRpcDTO);
	//		//List<ReqProductPartRpcDTO> data = new ArrayList<>();
	//		//for (ReqProductPartRpcDTO partModel : partModels) {
	//		//	ReqProductPartRpcDTO partRpcDTO = new ReqProductPartRpcDTO();
	//		//	BeanUtils.copyProperties(partModel, partRpcDTO);
	//		//	data.add(partRpcDTO);
	//		//}
	//		List<ProductPartModel> partModels = new ArrayList<>();
	//		List<XiHangProductPartDTO> data = Convert.toList(XiHangProductPartDTO.class, partModels);
	//		return new RpcPagingDTO<>(data, 200);
	//	} catch (Exception e) {
	//		throw new ExceptionPack(e, ExceptionMsg.builder("query BZNProductPartAction.getPageList failed").build());
	//	}
	//}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public ProductPartRpcDTO addProductPart(ProductPartAddRpcRequest requestData) throws AssertException {
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
		String productPartTypeTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPartType());
		// 生成产品、零件编号
		Long productPartCode = PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId();
		// 验证产品类型是否存在
		productPartCommonService.validateProductPartTypeAdd(requestData, productPartTypeTableName);
		// 验证编号是否存在
		productPartCommonService.validateUnityNo(null, requestData.getUnityNo());
		// 获取表代码-统一单号生成逻辑提取
		String unityNo = productPartCommonService.getGlobalSerialUnityNo(requestData.getAttribute(),
				requestData.getProductPartTypeCode(), requestData.getUnityNo(), Boolean.TRUE);
		ProductPartModel model = Convert.convert(ProductPartModel.class, requestData)
				.setProductPartCode(productPartCode)
				.setProductPartSign(requestData.getName()).setEnterpriseCode(getEnterpriseCode()).setCreator(getUserCode())
				.setSyncStatus(CommonConstant.NUMBER_ZERO).setUpdateCount(CommonConstant.NUMBER_ZERO)
				.setEnterTime(DateUtil.date()).setUnityNo(unityNo);
		RequestTableHelper.setTableName(productPartTableName);
		productPartMapper.insert(model);
		// 同步库存信息
		productPartCommonService.syncStock(model.getProductPartCode());
		// 日志记录
//		unifiedDataService.logProductPartAdd(model, unifiedDataService.resolveLogType(model.getAttribute()));
		return Convert.convert(ProductPartRpcDTO.class, model);
	}

	@Override
	public void updateProductPart(ProductPartUpdateRpcRequest requestData) throws AssertException {
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
		String productPartTypeTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPartType());
		// 验证产品类型是否存在
		productPartCommonService.validateProductPartTypeUpdate(requestData, productPartTypeTableName);
		// 查询当前修改次数
		RequestTableHelper.setTableName(productPartTableName);
		ProductPartModel productPartModel = productPartMapper.selectById(requestData.getProductPartCode());

		RequestTableHelper.setTableName(productPartTableName);
		ProductPartModel oldProductPartModel = productPartMapper.selectById(requestData.getProductPartCode());
//		BeanUtil.copyProperties(productPartModel, oldProductPartModel, "files");
		productPartModel.setSyncStatus(CommonConstant.NUMBER_TWO).setUpdateCount(productPartModel.getUpdateCount() + 1).setProductPartSign(requestData.getName());
		if (StrUtil.isNotBlank(productPartModel.getUnityNo()) &&
				!productPartModel.getUnityNo().equals(requestData.getUnityNo())) {
			// 验证编号是否存在
			productPartCommonService.validateUnityNo(requestData.getProductPartCode(), requestData.getUnityNo());
			// 获取表代码-统一单号生成逻辑提取
			String unityNo = productPartCommonService.getGlobalSerialUnityNo(requestData.getAttribute(),
					requestData.getProductPartTypeCode(), requestData.getUnityNo());
			productPartModel.setUnityNo(unityNo);
		}
		BeanUtil.copyProperties(requestData, productPartModel, "files");
		if (requestData.getSwitchRoute() == null || !requestData.getSwitchRoute()) {
			productPartModel.setFiles(requestData.getFiles());
		}

		// 校验产品零件是否允许停用
		if (Boolean.FALSE.equals(requestData.getState())) {
			ProductPartEditValidationResult req = checkEditProductPartBZN(requestData.getProductPartCode());
			if (!req.getCanEdit()) {
				throw new AssertException(ExceptionMsg.builder("productPartBatchUpdate This method failed")
						.msgView(req.getMessage()).build());
			}

		}

		RequestTableHelper.setTableName(productPartTableName);
		productPartMapper.updateById(productPartModel);
		RequestTableHelper.setTableName(productPartTableName);
		ProductPartModel newProductPartModel = productPartMapper.selectById(requestData.getProductPartCode());
		// 日志记录
		unifiedDataService.logProductPartUpdate(oldProductPartModel, newProductPartModel,
				unifiedDataService.resolveLogType(productPartModel.getAttribute()));
		
		String oldRouteName = "";
		if (oldProductPartModel.getDefaultRoute() != null) {
			ProcessRouteDataModel oldRoute = processRouteDataService.getById(oldProductPartModel.getDefaultRoute());
			if (oldRoute != null) {
				oldRouteName = oldRoute.getProcessRouteDataSign();
			}
		}
		String newRouteName = "";
		if (productPartModel.getDefaultRoute() != null) {
			ProcessRouteDataModel newRoute = processRouteDataService.getById(productPartModel.getDefaultRoute());
			if (newRoute != null) {
				newRouteName = newRoute.getProcessRouteDataSign();
			}
		}
		
		if (!oldRouteName.equals(newRouteName)) {
			unifiedDataService.logProductPartRouteUpdate(oldRouteName, newRouteName, productPartModel.getProductPartCode(),
					unifiedDataService.resolveLogType(productPartModel.getAttribute()));
		}
	}

	@Override
	public ParseExcelResult<?> productAnalyzeExcel(MultipartFileRpcDTO excelFile) throws ExceptionPack {
		try {
			return ExcelMultiModelsProcessor.INSTANCE.parseExcelModels(new ByteArrayInputStream(excelFile.getBytes()),
					ProductExcelRpcModelXh.class, productExcelValidatorXh, null, new BaseExcelHeaderGenerator());
		} catch (ExcelException e) {
			throw new ExceptionPack(e, ExceptionMsg.builder(e.getMessage())
					.msgViewAndResCode(e.getMsgView(), ServiceErrorCode.BATCH_IMPORT_FILE_ILLEGAL.getCode()).build());
		}
	}

	@Override
	public ParseExcelResult<?> partAnalyzeExcel(MultipartFileRpcDTO excelFile) throws ExceptionPack {
		try {
			return ExcelMultiModelsProcessor.INSTANCE.parseExcelModels(new ByteArrayInputStream(excelFile.getBytes()),
					PartExcelRpcModelXh.class, partExcelValidatorXh, null, new BaseExcelHeaderGenerator());
		} catch (ExcelException e) {
			throw new ExceptionPack(e, ExceptionMsg.builder(e.getMessage())
					.msgViewAndResCode(e.getMsgView(), ServiceErrorCode.BATCH_IMPORT_FILE_ILLEGAL.getCode()).build());
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void batchAddProductPart(List<?> requestData) throws ExceptionPack {
		List<ProductPartBatchAddRpcRequestXh> requestListData = Convert.toList(ProductPartBatchAddRpcRequestXh.class, requestData);
		// 新技术部-产品零件表
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
		// 新技术部-产品零件分类表
		String productPartTypeTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPartType());
		try {
			ProductPartBatchAddRpcRequestXh batchAddRpcRequest = requestListData.getFirst();
			if (null == batchAddRpcRequest.getAttribute()) {
				throw new AssertException(ExceptionMsg.builder("")
						.msgView(TechnicalErrorEnum.TECHNICAL_ATTRIBUTE_NOT_EXIST_MESSAGE.getMsg()).build());
			}
			StopWatch stopWatch = new StopWatch("batchAddProductPart");
			stopWatch.start(StrUtil.format("开始执行批量产品零件信息-处理数据大小 {}", requestListData.size()));
			// 获取统一数据 分类数据
			List<String> typeNameList = ExtractUtil.streamMapToList(String::valueOf, requestListData,
					ProductPartBatchAddRpcRequestXh::getProductPartTypeCodeName);
			Map<String, ProductPartTypeModel> typeNameToModelMap = unifiedDataService.prepareTypeData(typeNameList,
					productPartTypeTableName, batchAddRpcRequest.getAttribute());

			// 计量单位数据准备
			Set<String> productPiecesCodes = new HashSet<>(ExtractUtil.streamMapToList(String::valueOf, requestListData,
					ProductPartBatchAddRpcRequestXh::getPcsName));
			Map<String, Long> pieceCodeNameMapping =
					piecesService.pieceCodeNameMappingByName(productPiecesCodes, getEnterpriseCode());
			// 员工信息准备
			// 人员数据准备 技术对接人
			List<String> staffNameList = requestListData.stream().filter(Objects::nonNull) // 过滤掉 null 元素
					.flatMap(procedureModel -> Stream.of(procedureModel.getContactPersonName(),
							procedureModel.getWarehousePersonName()))
					.filter(Objects::nonNull).distinct().collect(Collectors.toList());

			Map<String, List<CodeMapName>> staffNameMap = unifiedDataService.prepareStaffModelData(staffNameList);
			//for (ProductPartBatchAddRpcRequestXh addRpcRequest : requestListData) {
			//ProductPartAddRpcRequest requestData = Convert.convert(ProductPartAddRpcRequest.class, addRpcRequest);
			// 如果是产品：产品名称可以重复、产品型号不可以重复
			// 如果是零件：零件名称+型号，唯一
			//validateProductOrPartAdd(requestData, productPartTableName);
			//}
			// 获取统一数据 图纸号数据
			List<String> numberData = requestListData.stream()
					.map(ProductPartBatchAddRpcRequestXh::getDrawingNumber)
					.filter(Objects::nonNull)
					.toList();
			StorageObjectRpcRequest objectRpcRequest = new StorageObjectRpcRequest();
			objectRpcRequest.setFileNumberList(numberData);
			Map<String, StorageObjectRpcDTO> fileNumberToMap = storageSpaceFileService.getFileNumberToMap(objectRpcRequest);
			stopWatch.stop();
			stopWatch.start(StrUtil.format("开始执行批量产品零件信息-初始化查询数据完成,进入业务循环，处理数据大小 {}", requestListData.size()));
			for (ProductPartBatchAddRpcRequestXh addRpcRequest : requestListData) {
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
				// 组装标准非标准
				StandardEnum standardEnum = StandardEnum.parse(addRpcRequest.getStandardName());
				addRpcRequest.setStandard(null != standardEnum ? standardEnum.isValue() : StandardEnum.STANDARD.isValue());
				// 零件型号数据准备 返回：零件名称_零件型号
				//Map<String, ProductPartModel> partToNameModelMap = new HashMap<>();
				//for (ProductPartBatchAddRpcRequestXh.PartInfoBatchAddModel partInfoBatchAddModel : addRpcRequest.getPartInfoBatchAddModels()) {
				//	partToNameModelMap.putAll(unifiedDataService.preparePartNameAndModelData(partInfoBatchAddModel.getPartName(),
				//			partInfoBatchAddModel.getModelSpecification(),
				//			CommonConstant.NUMBER_TWO, productPartTableName));
				//}
				List<String> unityNoListData = addRpcRequest.getPartInfoBatchAddModels().stream().filter(Objects::nonNull)
						.map(ProductPartBatchAddRpcRequestXh.PartInfoBatchAddModel::getPartUnityNo)
						.toList();
				Map<String, ProductPartModel> partUnityNoToModelMap = unifiedDataService.prepareUnityNoData(unityNoListData,
						productPartTableName,
						CommonConstant.NUMBER_TWO);

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
				List<ProductPartBatchAddRpcRequestXh.PartInfoBatchAddModel> batchAddModels =
						addRpcRequest.getPartInfoBatchAddModels();
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
				String unityNo = productPartCommonService.getGlobalSerialUnityNo(productPartModel.getAttribute(),
						productPartModel.getProductPartTypeCode(), addRpcRequest.getProductPartUnityNo());
				productPartModel.setUnityNo(unityNo);
				// 新增产品零件信息
				RequestTableHelper.setTableName(productPartTableName);
				productPartMapper.insert(productPartModel);
				if (!batchAddModels.isEmpty()) {
					ProductPartConsistRequest consistRequest = new ProductPartConsistRequest();
					consistRequest.setParentAttribute(addRpcRequest.getAttribute());
					consistRequest.setParentCode(productPartModel.getProductPartCode());
					List<ProductPartChild> productPartChildren = new ArrayList<>();
					for (ProductPartBatchAddRpcRequestXh.PartInfoBatchAddModel batchAddModel : batchAddModels) {
						ProductPartModel resultModel = partUnityNoToModelMap.get(batchAddModel.getPartUnityNo());
						if (resultModel == null) {
							log.warn("产品零件内容为空，不做任何处理 {}", JSONUtil.toJsonStr(resultModel));
							continue;
						}
					ProductPartChild productPartChild = new ProductPartChild();
					productPartChild.setChildCode(resultModel.getProductPartCode());
					productPartChild.setChildAttribute(resultModel.getAttribute());
					productPartChild.setChildNumber(StrUtil.isBlank(batchAddModel.getNumber()) ? null : new BigDecimal(batchAddModel.getNumber()));
					productPartChildren.add(productPartChild);
					}
					consistRequest.setProductPartChildList(productPartChildren);
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
			}
			stopWatch.stop();
			log.info("\r\n开始执行批量产品零件信息-请求执行耗时：{}", stopWatch.prettyPrint(TimeUnit.MILLISECONDS));
		} catch (Exception e) {
			throw new ExceptionPack(e,
					ExceptionMsg.builder("query batchAddProductPart failed").build());
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void productPartBatchUpdate(ProductPartUpdateRpcRequest requestData) throws ExceptionPack, AssertException {
		String tableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
		RequestTableHelper.setTableName(tableName);
		List<ProductPartModel> productPartModels = productPartMapper.selectBatchIds(requestData.getProductPartCodeList());
		for (ProductPartModel model : productPartModels) {
			// 记录旧数据
			ProductPartModel oldModel = new ProductPartModel();
			BeanUtil.copyProperties(model, oldModel);
			if (requestData.getAttribute().equals(CommonConstant.NUMBER_TWO)) {
				LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
				qw.eq(ProductPartModel::getProductPartSign, requestData.getName());
				qw.eq(ProductPartModel::getModel, model.getModel());
				qw.ne(ProductPartModel::getProductPartCode, model.getProductPartCode());
				qw.eq(ProductPartModel::getAttribute, requestData.getAttribute());
				qw.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE);
				RequestTableHelper.setTableName(tableName);
				if (productPartMapper.selectCount(qw) > CommonConstant.NUMBER_ZERO) {
					throw new AssertException(ExceptionMsg.builder("productPartBatchUpdate This method failed")
							.msgView(TechnicalErrorEnum.TECHNICAL_PRODUCT_PART_MODEL_EXIST_MESSAGE.getMsg()).build());
				}
			}
			if (StrUtil.isNotBlank(requestData.getName())
					&& !model.getProductPartSign().equals(requestData.getName())) {
				model.setProductPartSign(requestData.getName());
			}
			if (StrUtil.isNotBlank(requestData.getName())) {
				model.setProductPartSign(requestData.getName());
			}
			if (ObjectUtil.isNotEmpty(requestData.getPcs())) {
				model.setPcs(requestData.getPcs());
			}
			if (ObjectUtil.isNotEmpty(requestData.getStandard())) {
				model.setStandard(requestData.getStandard());
			}
			if (StrUtil.isNotEmpty(requestData.getVersion())) {
				model.setVersion(requestData.getVersion());
			}
			if (ObjectUtil.isNotEmpty(requestData.getState())) {
				model.setState(requestData.getState());
			}
			if (ObjectUtil.isNotEmpty(requestData.getContactPerson())) {
				model.setContactPerson(requestData.getContactPerson());
			}
			if (StrUtil.isNotBlank(requestData.getRemark())) {
				model.setRemark(requestData.getRemark());
			}
			if (requestData.getProductPartTypeCode() != null) {
				model.setProductPartTypeCode(requestData.getProductPartTypeCode());
			}
			if (ObjectUtil.isNotEmpty(requestData.getFiles()) &&
					requestData.getFiles().length > CommonConstant.NUMBER_ZERO) {
				model.setFiles(requestData.getFiles());
			}
			if (ObjectUtil.isNotEmpty(requestData.getQualityCodeList())) {
				model.setQualityCodeList(requestData.getQualityCodeList().toArray(new Long[0]));
			}
			if (Boolean.FALSE.equals(requestData.getState())) {
				ProductPartEditValidationResult req = checkEditProductPartBZN(model.getProductPartCode());
				if (!req.getCanEdit()) {
					throw new AssertException(ExceptionMsg.builder("productPartBatchUpdate This method failed")
							.msgView(req.getMessage()).build());
				}
			}
			RequestTableHelper.setTableName(tableName);
			productPartMapper.updateById(model);
			// 记录新数据
			ProductPartModel newModel = productPartMapper.selectById(model.getProductPartCode());
			unifiedDataService.logProductPartUpdate(oldModel, newModel, unifiedDataService.resolveLogType(model.getAttribute()));
		}
	}

	//@Override
	//@Transactional(rollbackFor = Exception.class)
	//public ProductPartRpcDTO productPartCopy(ProductPartRpcRequest requestData) throws ExceptionPack, AssertException {
	//	// 新技术部-产品零件表
	//	String productPartTableName =
	//			tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
	//	// 生成产品、零件编号
	//	Long productPartCode = PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId();
	//	RequestTableHelper.setTableName(productPartTableName);
	//	ProductPartModel productPartModel = productPartMapper.selectById(requestData.getProductPartCode());
	//	Long productPartCodeOld = productPartModel.getProductPartCode();
	//	ProductPartModel model =
	//			Convert.convert(ProductPartModel.class, productPartModel).setId(null)
	//					.setEnterTime(DateUtil.date()).setProductPartCode(productPartCode)
	//					.setProductPartSign(productPartModel.getProductPartSign()).setEnterpriseCode(getEnterpriseCode())
	//					.setCreator(getUserCode()).setSyncStatus(CommonConstant.NUMBER_ZERO)
	//					.setUpdateCount(CommonConstant.NUMBER_ZERO).setProductPartSign(productPartModel.getProductPartSign())
	//					.setModel(productPartCommonService.generateCopyModel(productPartModel.getModel(), productPartTableName));
	//	// 获取表代码-统一单号生成逻辑提取
	//	String unityNo = productPartCommonService.getGlobalSerialUnityNo(productPartModel.getAttribute(),
	//			productPartModel.getProductPartTypeCode(), productPartModel.getUnityNo());
	//	productPartModel.setUnityNo(unityNo);
	//	RequestTableHelper.setTableName(productPartTableName);
	//	productPartMapper.insert(model);
	//	// 全量复制产品零件绑定关系
	//	fullReplication(productPartCodeOld, model);
	//	// 同步库存信息
	//	productPartCommonService.syncStock(model.getProductPartCode());
	//	return Convert.convert(ProductPartRpcDTO.class, model);
	//}

	//@Override
	//@Transactional(rollbackFor = Exception.class)
	//public ProductPartRpcDTO partMaterialCopy(ProductPartRpcRequest requestData) throws ExceptionPack, AssertException {
	//	// 新技术部-产品零件表
	//	String productPartTableName =
	//			tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
	//	// 生成产品、零件编号
	//	Long productPartCode = PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId();
	//	RequestTableHelper.setTableName(productPartTableName);
	//	ProductPartModel productPartModel = productPartMapper.selectById(requestData.getProductPartCode());
	//	ProductPartModel model =
	//			Convert.convert(ProductPartModel.class, productPartModel).setId(null)
	//					.setEnterTime(DateUtil.date()).setProductPartCode(productPartCode)
	//					.setProductPartSign(productPartModel.getProductPartSign()).setEnterpriseCode(getEnterpriseCode())
	//					.setCreator(getUserCode()).setSyncStatus(CommonConstant.NUMBER_ZERO)
	//					.setUpdateCount(CommonConstant.NUMBER_ZERO).setProductPartSign(productPartModel.getProductPartSign())
	//					.setModel(productPartCommonService.generateCopyModel(productPartModel.getModel(), productPartTableName));
	//	// 获取表代码-统一单号生成逻辑提取
	//	String unityNo = productPartCommonService.getGlobalSerialUnityNo(productPartModel.getAttribute(),
	//			productPartModel.getProductPartTypeCode(), productPartModel.getUnityNo());
	//	productPartModel.setUnityNo(unityNo);
	//	RequestTableHelper.setTableName(productPartTableName);
	//	productPartMapper.insert(model);
	//	// 同步库存信息
	//	productPartCommonService.syncStock(model.getProductPartCode());
	//	return Convert.convert(ProductPartRpcDTO.class, model);
	//}

	@Override
	public void deleteProductPart(ProductPartRpcRequest requestData) throws ExceptionPack, AssertException {
		// 删除前校验（包含合同、调度、库存、关联关系等所有校验）
		ProductPartDeleteValidationResult validationResult = validateDeleteProductPart(requestData);
		if (!validationResult.getCanDelete()) {
			throw new AssertException(ExceptionMsg.builder("Delete validation failed")
					.msgView(validationResult.getMessage()).build());
		}

		// 如果存在关联关系，需要先自动解绑
		if ("RELATION".equals(validationResult.getValidationType()) &&
			validationResult.getRelatedItems() != null &&
			!validationResult.getRelatedItems().isEmpty()) {

			log.info("开始自动解绑产品部件关联关系，当前部件编码: {}, 关联数量: {}",
					requestData.getProductPartCode(), validationResult.getRelatedCount());

			autoUnbindRelatedProducts(requestData.getProductPartCode(), validationResult.getRelatedItems());
		}

		// 注意：所有校验（合同、调度、库存、关联关系、工序绑定等）已经在validateDeleteProductPart中完成

		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
		// 查询基本信息
		ProductPartModel productPartModel = productPartMapper.selectById(requestData.getProductPartCode());
		LambdaUpdateWrapper<ProductPartModel> uw = Wrappers.lambdaUpdate();
		uw.eq(ProductPartModel::getProductPartCode, requestData.getProductPartCode());
		uw.set(ProductPartModel::getDeleteFlag, Boolean.TRUE);
		RequestTableHelper.setTableName(productPartTableName);
		productPartMapper.update(uw);
		try {
			String stockTableName =
					tableFactory.getTableName(tableFactory.module.getWarehouse(), tableFactory.table.getStock());
			log.info("start 同步删除产品零件关联数据 {}", requestData.getProductPartCode());
			LambdaUpdateWrapper<StockModel> smQw = Wrappers.lambdaUpdate();
			smQw.set(StockModel::getDeleteFlag, Boolean.TRUE)
					.eq(StockModel::getProductPartCode, requestData.getProductPartCode());
			//新增同步库存数据
			RequestTableHelper.setTableName(stockTableName);
			stockMapper.update(smQw);
			// 删除关系数据
			productPartCommonService.safeUnbindConsistXh(new ProductPartConsistRequest().setParentCode(requestData.getProductPartCode()));
			// 日志记录
			unifiedDataService.logProductPartDelete(productPartModel,
					unifiedDataService.resolveLogType(productPartModel.getAttribute()));
		} catch (Exception e) {
			log.error("error 同步删除产品零件关联数据 执行失败 ", e);
		}
	}

	/**
	 * 自动解绑关联的产品部件BOM关系
	 * @param productPartCode 要删除的产品部件编码
	 * @param relatedItems 关联的产品部件信息
	 */
	private void autoUnbindRelatedProducts(Long productPartCode, List<ProductPartDeleteValidationResult.RelatedProductPartInfo> relatedItems) {
		try {
			String procedurePartRelationshipTableName =
					tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProcedurePartRelationship());
			String productPartRouteRelationshipTableName =
					tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPartRouteRelationship());

			Set<Long> relatedProductPartCodes = relatedItems.stream()
					.map(ProductPartDeleteValidationResult.RelatedProductPartInfo::getProductPartCode)
					.collect(Collectors.toSet());

			int totalUnbindCount = 0;

			// === 第一部分：解绑当前产品作为子项的关联关系 ===
			// 1. 查询当前产品部件在procedure_part_relationship表中作为子项的所有关联关系
			RequestTableHelper.setTableName(procedurePartRelationshipTableName);
			LambdaQueryWrapper<ProcedurePartRelationshipModel> procedureQuery = Wrappers.lambdaQuery();
			procedureQuery.eq(ProcedurePartRelationshipModel::getProductPartCode, productPartCode);
			procedureQuery.eq(ProcedurePartRelationshipModel::getEnterpriseCode, getEnterpriseCode());
			procedureQuery.eq(ProcedurePartRelationshipModel::getDeleteFlag, Boolean.FALSE);

			List<ProcedurePartRelationshipModel> procedureRelationships = procedurePartRelationshipMapper.selectList(procedureQuery);

			if (!procedureRelationships.isEmpty()) {
				// 2. 获取所有的uniqueId
				List<Long> uniqueIds = procedureRelationships.stream()
						.map(ProcedurePartRelationshipModel::getUniqueId)
						.distinct()
						.collect(Collectors.toList());

				// 3. 根据关联的产品列表，筛选出需要解绑的uniqueId
				RequestTableHelper.setTableName(productPartRouteRelationshipTableName);
				LambdaQueryWrapper<ProductPartRouteRelationshipModel> routeQuery = Wrappers.lambdaQuery();
				routeQuery.in(ProductPartRouteRelationshipModel::getUniqueId, uniqueIds);
				routeQuery.in(ProductPartRouteRelationshipModel::getProductPartCode, relatedProductPartCodes);
				routeQuery.eq(ProductPartRouteRelationshipModel::getEnterpriseCode, getEnterpriseCode());
				routeQuery.eq(ProductPartRouteRelationshipModel::getDeleteFlag, Boolean.FALSE);

				List<ProductPartRouteRelationshipModel> targetRouteRelationships = productPartRouteRelationshipMapper.selectList(routeQuery);

				if (!targetRouteRelationships.isEmpty()) {
					// 4. 获取需要解绑的uniqueId列表
					List<Long> targetUniqueIds = targetRouteRelationships.stream()
							.map(ProductPartRouteRelationshipModel::getUniqueId)
							.distinct()
							.collect(Collectors.toList());

					// 5. 软删除procedure_part_relationship表中的关联关系
					LambdaUpdateWrapper<ProcedurePartRelationshipModel> procedureUpdateWrapper = Wrappers.lambdaUpdate();
					procedureUpdateWrapper.eq(ProcedurePartRelationshipModel::getProductPartCode, productPartCode);
					procedureUpdateWrapper.in(ProcedurePartRelationshipModel::getUniqueId, targetUniqueIds);
					procedureUpdateWrapper.eq(ProcedurePartRelationshipModel::getEnterpriseCode, getEnterpriseCode());
					procedureUpdateWrapper.set(ProcedurePartRelationshipModel::getDeleteFlag, Boolean.TRUE);

					RequestTableHelper.setTableName(procedurePartRelationshipTableName);
					int procedureCount = procedurePartRelationshipMapper.update(procedureUpdateWrapper);
					totalUnbindCount += procedureCount;

					// 6. 检查每个uniqueId是否还有其他未删除的零件关联，如果没有则也删除路线关系
					for (Long uniqueId : targetUniqueIds) {
						RequestTableHelper.setTableName(procedurePartRelationshipTableName);
						LambdaQueryWrapper<ProcedurePartRelationshipModel> checkQuery = Wrappers.lambdaQuery();
						checkQuery.eq(ProcedurePartRelationshipModel::getUniqueId, uniqueId);
						checkQuery.eq(ProcedurePartRelationshipModel::getEnterpriseCode, getEnterpriseCode());
						checkQuery.eq(ProcedurePartRelationshipModel::getDeleteFlag, Boolean.FALSE);

						Long remainingCount = procedurePartRelationshipMapper.selectCount(checkQuery);

						// 如果该uniqueId下没有其他有效的零件关联，则软删除对应的路线关系
						if (remainingCount == 0) {
							LambdaUpdateWrapper<ProductPartRouteRelationshipModel> routeUpdateWrapper = Wrappers.lambdaUpdate();
							routeUpdateWrapper.eq(ProductPartRouteRelationshipModel::getUniqueId, uniqueId);
							routeUpdateWrapper.eq(ProductPartRouteRelationshipModel::getEnterpriseCode, getEnterpriseCode());
							routeUpdateWrapper.set(ProductPartRouteRelationshipModel::getDeleteFlag, Boolean.TRUE);

							RequestTableHelper.setTableName(productPartRouteRelationshipTableName);
							productPartRouteRelationshipMapper.update(routeUpdateWrapper);

							log.info("已自动删除空的路线关系，uniqueId: {}", uniqueId);
						}
					}

					log.info("解绑当前产品作为子项的关联关系完成，产品部件编码: {}, 解绑procedure关系数量: {}",
							productPartCode, procedureCount);
				}
			}

			// === 第二部分：解绑当前产品作为父项的关联关系 ===
			// 1. 查询当前产品部件作为父项的所有路线关系
			RequestTableHelper.setTableName(productPartRouteRelationshipTableName);
			LambdaQueryWrapper<ProductPartRouteRelationshipModel> currentAsParentQuery = Wrappers.lambdaQuery();
			currentAsParentQuery.eq(ProductPartRouteRelationshipModel::getProductPartCode, productPartCode);
			currentAsParentQuery.eq(ProductPartRouteRelationshipModel::getEnterpriseCode, getEnterpriseCode());
			currentAsParentQuery.eq(ProductPartRouteRelationshipModel::getDeleteFlag, Boolean.FALSE);

			List<ProductPartRouteRelationshipModel> currentAsParentRelationships = productPartRouteRelationshipMapper.selectList(currentAsParentQuery);

			if (!currentAsParentRelationships.isEmpty()) {
				// 2. 获取所有uniqueId
				List<Long> parentUniqueIds = currentAsParentRelationships.stream()
						.map(ProductPartRouteRelationshipModel::getUniqueId)
						.distinct()
						.collect(Collectors.toList());

				// 3. 查询这些uniqueId对应的子项，并筛选出关联列表中的子项
				RequestTableHelper.setTableName(procedurePartRelationshipTableName);
				LambdaQueryWrapper<ProcedurePartRelationshipModel> childProcedureQuery = Wrappers.lambdaQuery();
				childProcedureQuery.in(ProcedurePartRelationshipModel::getUniqueId, parentUniqueIds);
				childProcedureQuery.in(ProcedurePartRelationshipModel::getProductPartCode, relatedProductPartCodes);
				childProcedureQuery.eq(ProcedurePartRelationshipModel::getEnterpriseCode, getEnterpriseCode());
				childProcedureQuery.eq(ProcedurePartRelationshipModel::getDeleteFlag, Boolean.FALSE);

				List<ProcedurePartRelationshipModel> childProcedureRelationships = procedurePartRelationshipMapper.selectList(childProcedureQuery);

				if (!childProcedureRelationships.isEmpty()) {
					// 4. 获取需要解绑的uniqueId列表
					List<Long> childTargetUniqueIds = childProcedureRelationships.stream()
							.map(ProcedurePartRelationshipModel::getUniqueId)
							.distinct()
							.collect(Collectors.toList());

					// 5. 软删除procedure_part_relationship表中的子项关联关系
					LambdaUpdateWrapper<ProcedurePartRelationshipModel> childProcedureUpdateWrapper = Wrappers.lambdaUpdate();
					childProcedureUpdateWrapper.in(ProcedurePartRelationshipModel::getUniqueId, childTargetUniqueIds);
					childProcedureUpdateWrapper.in(ProcedurePartRelationshipModel::getProductPartCode, relatedProductPartCodes);
					childProcedureUpdateWrapper.eq(ProcedurePartRelationshipModel::getEnterpriseCode, getEnterpriseCode());
					childProcedureUpdateWrapper.set(ProcedurePartRelationshipModel::getDeleteFlag, Boolean.TRUE);

					RequestTableHelper.setTableName(procedurePartRelationshipTableName);
					int childProcedureCount = procedurePartRelationshipMapper.update(childProcedureUpdateWrapper);
					totalUnbindCount += childProcedureCount;

					// 6. 检查每个uniqueId是否还有其他未删除的零件关联，如果没有则也删除路线关系
					for (Long uniqueId : childTargetUniqueIds) {
						RequestTableHelper.setTableName(procedurePartRelationshipTableName);
						LambdaQueryWrapper<ProcedurePartRelationshipModel> checkQuery = Wrappers.lambdaQuery();
						checkQuery.eq(ProcedurePartRelationshipModel::getUniqueId, uniqueId);
						checkQuery.eq(ProcedurePartRelationshipModel::getEnterpriseCode, getEnterpriseCode());
						checkQuery.eq(ProcedurePartRelationshipModel::getDeleteFlag, Boolean.FALSE);

						Long remainingCount = procedurePartRelationshipMapper.selectCount(checkQuery);

						// 如果该uniqueId下没有其他有效的零件关联，则软删除对应的路线关系
						if (remainingCount == 0) {
							LambdaUpdateWrapper<ProductPartRouteRelationshipModel> routeUpdateWrapper = Wrappers.lambdaUpdate();
							routeUpdateWrapper.eq(ProductPartRouteRelationshipModel::getUniqueId, uniqueId);
							routeUpdateWrapper.eq(ProductPartRouteRelationshipModel::getEnterpriseCode, getEnterpriseCode());
							routeUpdateWrapper.set(ProductPartRouteRelationshipModel::getDeleteFlag, Boolean.TRUE);

							RequestTableHelper.setTableName(productPartRouteRelationshipTableName);
							productPartRouteRelationshipMapper.update(routeUpdateWrapper);

							log.info("已自动删除空的路线关系，uniqueId: {}", uniqueId);
						}
					}

					log.info("解绑当前产品作为父项的关联关系完成，产品部件编码: {}, 解绑procedure关系数量: {}",
							productPartCode, childProcedureCount);
				}
			}

			if (totalUnbindCount == 0) {
				log.info("未找到需要解绑的BOM关系，产品部件编码: {}", productPartCode);
				return;
			}

			log.info("自动解绑BOM关系完成，产品部件编码: {}, 总解绑关系数量: {}, 涉及关联产品数量: {}",
					productPartCode, totalUnbindCount, relatedItems.size());

			// 7. 记录解绑日志，对每个关联的产品记录解绑操作
			for (ProductPartDeleteValidationResult.RelatedProductPartInfo relatedItem : relatedItems) {
				log.info("已自动解绑BOM关系: 产品[{}:{}]与产品[{}]之间的关联",
						relatedItem.getProductPartCode(), relatedItem.getName(), productPartCode);
			}

		} catch (Exception e) {
			log.error("自动解绑BOM关系失败，产品部件编码: {}", productPartCode, e);
			throw new RuntimeException("自动解绑BOM关系失败: " + e.getMessage());
		}
	}
	public ProductPartEditValidationResult checkEditProductPartBZN(Long productPartCode) throws AssertException {
		// 校验产品部件编码是否为空
		if (productPartCode == null) {
			return new ProductPartEditValidationResult()
					.setCanEdit(false)
					.setMessage("产品部件编码不能为空")
					.setValidationType("ERROR");
		}
		// 查询当前产品部件信息（必须包含企业代码和删除标志的过滤条件）
		RequestTableHelper.setTableName(getProductPartTableName());
		LambdaQueryWrapper<ProductPartModel> queryWrapper = Wrappers.lambdaQuery();
		queryWrapper.eq(ProductPartModel::getProductPartCode, productPartCode)
				.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode())
				.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE);
		ProductPartModel currentProductPart = productPartMapper.selectOne(queryWrapper);
		// 校验产品部件是否存在
		if (currentProductPart == null)
			throw new AssertException(ExceptionMsg.builder("产品部件不存在")
					.msgView(TechnicalErrorEnum.TECHNICAL_PRODUCT_PART_NOT_EXIT.getMsg() + productPartCode).build());

		String prodName = PPAttributeEnum.getEnumByCode(currentProductPart.getAttribute()).getDescription();
		// 0. 校验是否被绑定BOM
		boolean hasInBom = !productPartCommonService.checkInBomBZN(productPartCode);
		if (hasInBom) {
			throw new AssertException(ExceptionMsg.builder("已经与其他物料的BOM绑定啦，如需停用，请先将相关的绑定关系清除～")
					.msgView("当前" + prodName + TechnicalErrorEnum.TECHNICAL_PRODUCT_PART_BUNDING_BOM_NO_STOP.getMsg() + "名称： " + currentProductPart.getProductPartSign()).build());
		}

		// 0. 校验是否绑定BOM
		boolean hasHasBom = !productPartCommonService.checkHasBomBZN(productPartCode);
		if (hasHasBom) {
			throw new AssertException(ExceptionMsg.builder("已经绑定配套零件啦，如需停用，请先将[详情]内的零件清除～")
					.msgView("当前" + prodName + TechnicalErrorEnum.TECHNICAL_PRODUCT_PART_HAS_BOM_TO_STOP.getMsg() + "名称： " + currentProductPart.getProductPartSign()).build());
		}

		// 1. 校验是否有已签章的合同
		boolean hasSignedContract = !productPartCommonService.checkDeleteProductPartNonSignedContract(productPartCode);
		if (hasSignedContract) {
			throw new AssertException(ExceptionMsg.builder("已经在签章合同的清单中啦，无法停用～")
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
		return new ProductPartEditValidationResult()
				.setCanEdit(true)
				.setMessage("SUCCESS")
				.setValidationType("RELATION");
	}
}
