/*
 * Copyright (c) Zhongzao Software Co. LTD 2022-2062 All rights reserved
 */

package com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart.action.unified;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;

import com.aliyun.core.utils.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.futurecraftsmen.pms.common.domain.StaffOperationLogTypeEnum;
import com.futurecraftsmen.pms.dm.api.service.base.staff.StaffService;
import com.futurecraftsmen.pms.dm.api.service.enumvalue.EnumService;
import com.futurecraftsmen.pms.service.configuration.RequestTableHelper;
import com.futurecraftsmen.pms.service.domain.common.constant.CommonConstant;
import com.futurecraftsmen.pms.service.domain.table.ModuleEnNameBaseConfig;
import com.futurecraftsmen.pms.technical.api.common.constants.ProductPartConstants;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.ProductPartCheckNameModelRpcRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.ProductPartResultRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.excel.part.PartMaterialModel;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.excel.product.ProductMaterialModel;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.excel.standard.WorkYStandardModel;
import com.futurecraftsmen.pms.technical.api.domain.technical.type.ProductPartTreeNodeRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.type.ProductPartTypeRpcRequest;
import com.futurecraftsmen.pms.technical.service.anno.OptRecord;
import com.futurecraftsmen.pms.technical.service.dao.technical.IProcessRouteDataMapper;
import com.futurecraftsmen.pms.technical.service.dao.technical.IProductPartMapper;
import com.futurecraftsmen.pms.technical.service.dao.technical.IProductPartProcedureMapper;
import com.futurecraftsmen.pms.technical.service.dao.technical.IProductPartTypeMapper;
import com.futurecraftsmen.pms.technical.service.domain.technical.procedure.ProductPartProcedureModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.process.ProcessRouteDataModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.productpart.MaterialModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.productpart.ProductPartModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.type.ProductPartTypeModel;
import jakarta.annotation.Resource;
import org.aerie.forest.core.brick.domain.enums.PPAttributeEnum;
import org.aerie.forest.core.brick.domain.view.CodeMapName;
import org.aerie.forest.core.brick.exception.ExceptionPack;
import org.apache.commons.compress.utils.Lists;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.futurecraftsmen.pms.technical.service.util.CommonUtil.getEnterpriseCode;

/**
 * @description 技术统一数据准备 中间服务
 *
 * @author qierkang
 * @organization futurecraftsmen
 * @date Created in 2024/12/13 16:29
 */
@Service
public class TechnicalUnifiedDataService {

	@Resource
	public ModuleEnNameBaseConfig moduleEnNameConfig;
	@DubboReference(check = false, retries = 0)
	private EnumService enumService;
	@Resource
	private IProcessRouteDataMapper processRouteDataMapper;
	@Resource
	private IProductPartTypeMapper productPartTypeMapper;
	@Resource
	private IProductPartMapper productPartMapper;
	@Resource
	private IProductPartProcedureMapper productPartProcedureMapper;
	@DubboReference(check = false, retries = 0)
	private StaffService staffService;

	/**
	 * @description 分类数据准备
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2024/12/13 16:15
	 */
	public ProductPartTreeNodeRpcDTO prepareTypeTree(String typeTableName, Integer attribute) {
		ProductPartTypeRpcRequest requestData = new ProductPartTypeRpcRequest()
				.setAttribute(attribute);
		List<ProductPartTypeModel> productPartTypeModels = productPartTypeMapper.findAll(getEnterpriseCode(), typeTableName, requestData);
		List<ProductPartTreeNodeRpcDTO> treeNodes = Lists.newArrayList();
		productPartTypeModels.forEach(entity -> treeNodes.add(new ProductPartTreeNodeRpcDTO()
				.setAttribute(entity.getAttribute())
				.setTypeName(entity.getTypeName())
				.setProductPartTypeCode(entity.getProductPartTypeCode())
				.setHasChild(entity.getHasChild())
				.setSuperior(entity.getSuperior())));
		return buildSimpleTree(treeNodes);
	}

	/**
	 * 构建产品部件树结构
	 */
	private ProductPartTreeNodeRpcDTO buildSimpleTree(List<ProductPartTreeNodeRpcDTO> treeNodes) {
		Map<Long, ProductPartTreeNodeRpcDTO> nodeMap = treeNodes.stream().collect(
				Collectors.toMap(ProductPartTreeNodeRpcDTO::getProductPartTypeCode, Function.identity()));
		// 构建一个虚拟的根节点
		ProductPartTreeNodeRpcDTO virtualRootSeries = new ProductPartTreeNodeRpcDTO()
				.setProductPartTypeCode(ProductPartConstants.VIRTUAL_PRODUCT_SERIES_CODE)
				.setTypeName(ProductPartConstants.VIRTUAL_PRODUCT_SERIES_NAME);

		// 构建树结构
		for (ProductPartTreeNodeRpcDTO node : treeNodes) {
			ProductPartTreeNodeRpcDTO superior = nodeMap.get(node.getSuperior());
			if (superior == null) {
				virtualRootSeries.addChild(node);
			} else {
				superior.addChild(node);
			}
		}
		return virtualRootSeries;
	}

	/**
	 * @description 分类数据准备
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2024/12/13 16:15
	 */
	public Map<String, ProductPartTypeModel> prepareTypeData(List<String> listData,
	                                                         String typeTableName, Integer attribute) {
		if (listData.isEmpty()) {
			return Collections.emptyMap();
		}
		LambdaQueryWrapper<ProductPartTypeModel> qw = Wrappers.lambdaQuery();
		qw.in(ProductPartTypeModel::getTypeName, listData);
		if (null != attribute) {
			qw.eq(ProductPartTypeModel::getAttribute, attribute);
		}
		qw.eq(ProductPartTypeModel::getEnterpriseCode, getEnterpriseCode());
		qw.eq(ProductPartTypeModel::getDeleteFlag, Boolean.FALSE);
		RequestTableHelper.setTableName(typeTableName);
		return productPartTypeMapper.selectList(qw).stream()
				.collect(Collectors.toMap(ProductPartTypeModel::getTypeName, Function.identity(),
						(existing, replacement) -> existing));
	}

	public Map<String, ProductPartTypeModel> prepareTypeData(List<String> listData,
	                                                         String typeTableName) {
		if (listData.isEmpty()) {
			return Collections.emptyMap();
		}
		LambdaQueryWrapper<ProductPartTypeModel> qw = Wrappers.lambdaQuery();
		qw.in(ProductPartTypeModel::getTypeName, listData);
		qw.in(ProductPartTypeModel::getAttribute, Arrays.asList(2, 3, 4));
		qw.eq(ProductPartTypeModel::getEnterpriseCode, getEnterpriseCode());
		qw.eq(ProductPartTypeModel::getDeleteFlag, Boolean.FALSE);
		RequestTableHelper.setTableName(typeTableName);
		return productPartTypeMapper.selectList(qw).stream()
				.collect(Collectors.toMap(ProductPartTypeModel::getTypeName, Function.identity(),
						(existing, replacement) -> existing));
	}

	/**
	 * @description 型号数据准备
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2024/12/13 16:44
	 */
	public Map<String, ProductPartModel> prepareModelData(List<String> listData,
	                                                      List<String> nameList,
	                                                      String productPartTableName,
	                                                      Integer attribute) {
		if (listData.isEmpty() || nameList.isEmpty()) {
			return Collections.emptyMap();
		}
		LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
		qw.in(ProductPartModel::getModel, listData);
		if (attribute.equals(CommonConstant.NUMBER_TWO)) {
			qw.in(ProductPartModel::getProductPartSign, nameList);
		}
		qw.eq(ProductPartModel::getAttribute, attribute);
		qw.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode());
		qw.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE);
		RequestTableHelper.setTableName(productPartTableName);
		if (attribute.equals(CommonConstant.NUMBER_ONE)) {
			return productPartMapper.selectList(qw).stream()
					.collect(Collectors.toMap(ProductPartModel::getModel, Function.identity(),
							(existing, replacement) -> existing));
		}
		// 如果是零件维度 需要返回 零件名称-零件型号 格式
		return productPartMapper.selectList(qw).stream()
				.collect(Collectors.toMap(model -> StrUtil.format("{}{}", model.getProductPartSign(), model.getModel()), Function.identity(),
						(existing, replacement) -> existing));
	}

	public Map<String, ProductPartModel> prepareModelData(List<String> unityNo,
	                                                      String productPartTableName) {
		if (unityNo.isEmpty()) {
			return Collections.emptyMap();
		}
		LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
		qw.in(ProductPartModel::getUnityNo, unityNo);
		qw.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode());
		qw.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE);
		RequestTableHelper.setTableName(productPartTableName);
		// 如果是零件维度 需要返回 零件名称-零件型号 格式
		return productPartMapper.selectList(qw).stream()
				.collect(Collectors.toMap(model -> StrUtil.format("{}{}", model.getProductPartSign(), model.getModel()), Function.identity(),
						(existing, replacement) -> existing));
	}

	/**
	 * @description 编号数据准备
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2024/12/13 16:44
	 */
	public Map<String, ProductPartModel> prepareUnityNoData(List<String> listData,
	                                                        String productPartTableName,
	                                                        Integer attribute) {
		if (listData.isEmpty()) {
			return Collections.emptyMap();
		}
		LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
		qw.in(ProductPartModel::getUnityNo, listData);
		qw.eq(ProductPartModel::getAttribute, attribute);
		qw.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode());
		qw.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE);
		RequestTableHelper.setTableName(productPartTableName);
		return productPartMapper.selectList(qw).stream()
				.collect(Collectors.toMap(ProductPartModel::getUnityNo, Function.identity(),
						(existing, replacement) -> existing));
	}

	public Map<String, ProductPartModel> prepareUnityNoData(List<String> listData,
	                                                        String productPartTableName) {
		if (listData.isEmpty()) {
			return Collections.emptyMap();
		}
		LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
		qw.in(ProductPartModel::getUnityNo, listData);
		qw.in(ProductPartModel::getAttribute, Arrays.asList(1, 2, 3, 4));
		qw.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode());
		qw.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE);
		RequestTableHelper.setTableName(productPartTableName);
		return productPartMapper.selectList(qw).stream()
				.collect(Collectors.toMap(ProductPartModel::getUnityNo, Function.identity(),
						(existing, replacement) -> existing));
	}

	/**
	 * @description 物料查询 物料包含 2,3,4
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/28 20:21
	 * @department: Product development
	 */
	public Map<String, ProductPartModel> materialUnityNoData(List<String> listData,
	                                                         String productPartTableName) {
		if (listData.isEmpty()) {
			return Collections.emptyMap();
		}
		LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
		qw.in(ProductPartModel::getUnityNo, listData);
		qw.in(ProductPartModel::getAttribute, Arrays.asList(1, 2, 3, 4));
		qw.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode());
		qw.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE);
		RequestTableHelper.setTableName(productPartTableName);
		return productPartMapper.selectList(qw).stream()
				.collect(Collectors.toMap(ProductPartModel::getUnityNo, Function.identity(),
						(existing, replacement) -> existing));
	}

	public Map<Long, ProductPartModel> prepareCodeData(List<Long> listData,
	                                                      String productPartTableName) {
		if (listData.isEmpty()) {
			return Collections.emptyMap();
		}
		LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
		qw.in(ProductPartModel::getProductPartCode, listData);
		qw.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode());
		RequestTableHelper.setTableName(productPartTableName);
		return productPartMapper.selectList(qw).stream()
				.collect(Collectors.toMap(ProductPartModel::getProductPartCode, Function.identity(),
						(existing, replacement) -> existing));
	}

	/**
	 * @description 工艺路线数据准备
	 * 根据工艺路线名称做为 key
	 * 根据工艺路号做为 key
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2024/12/13 16:15
	 */
	public Map<String, ProcessRouteDataModel> routeDataSignToModelMap(List<String> listData,
	                                                                  String processRouteDataTableName) {
		if (listData.isEmpty()) {
			return Collections.emptyMap();
		}
		// 工艺路线数据准备
		LambdaQueryWrapper<ProcessRouteDataModel> qw = Wrappers.lambdaQuery();
		qw.in(ProcessRouteDataModel::getProcessRouteDataSign, listData).or()
				.in(ProcessRouteDataModel::getRouteNumber, listData);
		qw.eq(ProcessRouteDataModel::getEnterpriseCode, getEnterpriseCode());
		qw.eq(ProcessRouteDataModel::getDeleteFlag, Boolean.FALSE);
		RequestTableHelper.setTableName(processRouteDataTableName);
		return processRouteDataMapper.selectList(qw).stream()
				.flatMap(model -> {
					List<Map.Entry<String, ProcessRouteDataModel>> entries = new ArrayList<>();
					// 始终添加ProcessRouteDataSign作为key
					entries.add(new AbstractMap.SimpleEntry<>(model.getProcessRouteDataSign(), model));
					// 如果RouteNumber存在且非空，也添加为key
					String routeNumber = model.getRouteNumber();
					if (routeNumber != null && !routeNumber.isEmpty()) {
						entries.add(new AbstractMap.SimpleEntry<>(routeNumber, model));
					}
					return entries.stream();
				})
				.collect(Collectors.toMap(
						Map.Entry::getKey,
						Map.Entry::getValue,
						(existing, replacement) -> existing // 冲突时保留第一个
				));
	}

	public Map<Long, ProcessRouteDataModel> routeDataCodeToModelMap(List<Long> listData,
	                                                                  String processRouteDataTableName) {
		if (listData.isEmpty()) {
			return Collections.emptyMap();
		}
		// 工艺路线数据准备
		LambdaQueryWrapper<ProcessRouteDataModel> qw = Wrappers.lambdaQuery();
		qw.in(ProcessRouteDataModel::getProcessRouteDataCode, listData);
		qw.eq(ProcessRouteDataModel::getEnterpriseCode, getEnterpriseCode());
		qw.eq(ProcessRouteDataModel::getDeleteFlag, Boolean.FALSE);
		RequestTableHelper.setTableName(processRouteDataTableName);
		return processRouteDataMapper.selectList(qw).stream()
				.collect(Collectors.toMap(ProcessRouteDataModel::getProcessRouteDataCode, Function.identity(),
						(existing, replacement) -> existing));
	}

	/**
	 * @description 零件型号数据准备 返回：零件名称_零件型号
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/3 19:40
	 */
	public Map<String, ProductPartModel> preparePartNameAndModelData(String partName, String modelSpecification, Integer attribute,
	                                                                 String productPartTableName) {
		if (StrUtil.isAllBlank(partName, modelSpecification)) {
			return Collections.emptyMap();
		}
		LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
		qw.eq(ProductPartModel::getProductPartSign, partName);
		qw.eq(ProductPartModel::getModel, modelSpecification);
		qw.eq(ProductPartModel::getAttribute, attribute);
		qw.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE);
		RequestTableHelper.setTableName(productPartTableName);
		return productPartMapper.selectList(qw).stream()
				.collect(Collectors.toMap(model -> model.getProductPartSign() + "_" + model.getModel(),
						Function.identity(),
						(existing, replacement) -> existing));
	}

	/**
	 * @description 零件型号数据准备 返回零件名称Map
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2024/12/13 16:15
	 */
	public Map<String, ProductPartModel> preparePartNameData(List<String> nameListData, Integer attribute,
	                                                         String productPartTableName) {
		List<ProductPartModel> productPartModels = preparePartMapData(null, nameListData, attribute, productPartTableName);
		return productPartModels.stream()
				.collect(Collectors.toMap(ProductPartModel::getProductPartSign, Function.identity(),
						(existing, replacement) -> existing));
	}

	/**
	 * @description 零件型号数据准备 返回型号Map
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2024/12/13 16:15
	 */
	public Map<String, ProductPartModel> preparePartModelData(List<String> modelListData, Integer attribute,
	                                                                  String productPartTableName) {
		List<ProductPartModel> productPartModels = preparePartMapData(modelListData, null, attribute, productPartTableName);
		return productPartModels.stream()
				.collect(Collectors.toMap(ProductPartModel::getModel, Function.identity(),
						(existing, replacement) -> existing));
	}

	/**
	 * @description 根据零件型号或零件名称进行查询 返回 Map
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/3 18:01
	 */
	public List<ProductPartModel> preparePartMapData(List<String> modelListData, List<String> nameListData, Integer attribute,
	                                                        String productPartTableName) {
		if (ObjectUtil.isEmpty(modelListData) && ObjectUtil.isEmpty(nameListData)) {
			return Collections.emptyList();
		}
		LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
		// 根据型号查询
		if (ObjectUtil.isNotEmpty(modelListData)) {
			qw.in(ProductPartModel::getModel, modelListData);
		}
		// 根据名称查询
		if (ObjectUtil.isNotEmpty(nameListData)) {
			qw.in(ProductPartModel::getProductPartSign, nameListData);
		}
		qw.eq(ProductPartModel::getAttribute, attribute);
		qw.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode());
		RequestTableHelper.setTableName(productPartTableName);
		return productPartMapper.selectList(qw);
	}

	/**
	 * @description 零件和型号数据 key 准备
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2024/12/26 17:20
	 */
	public Map<String, ProductPartModel> preparePartModelDataMap(List<String> nameListData, List<String> modelListData, Integer attribute,
	                                                             String productPartTableName) {
		if (modelListData.isEmpty()) {
			return Collections.emptyMap();
		}
		LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
		qw.in(ProductPartModel::getProductPartSign, nameListData);
		//qw.in(ProductPartModel::getModel, modelListData);
		qw.eq(ProductPartModel::getAttribute, attribute);
		qw.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode());
		qw.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE);
		RequestTableHelper.setTableName(productPartTableName);
		return productPartMapper.selectList(qw).stream()
				.collect(Collectors.toMap(m -> m.getProductPartSign() + m.getModel(),
						Function.identity(),
						(existing, replacement) -> existing));
	}

	/**
	 * @description TODO
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2024/12/16 19:00
	 */
	public Map<String, List<CodeMapName>> prepareStaffModelData(List<String> listData) throws ExceptionPack {
		// 人员数据准备
		Map<String, List<CodeMapName>> staffNameMapShowInformation = new HashMap<>();
		if (ObjectUtil.isNotEmpty(listData)) {
			staffNameMapShowInformation =
					staffService.queryStaffNameShowForBatchImport(listData, getEnterpriseCode());
		}
		return staffNameMapShowInformation;
	}

	public Map<String, List<CodeMapName>> prepareStaffModel(List<String> listData) throws ExceptionPack {
		// 人员数据准备
		Map<String, List<CodeMapName>> staffNameMapShowInformation = new HashMap<>();
		if (ObjectUtil.isNotEmpty(listData)) {
			staffNameMapShowInformation =
					staffService.queryStaffNameShowForBatchImport(listData, getEnterpriseCode());
		}
		return staffNameMapShowInformation;
	}

	/**
	 * @description 工序号数据准备
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2024/12/13 16:15
	 */
	public Map<String, ProductPartProcedureModel> numberDataSignToModelMap(List<String> listData,
	                                                                       String processRouteDataTableName) {
		if (listData.isEmpty()) {
			return Collections.emptyMap();
		}
		// 工序号数据准备
		LambdaQueryWrapper<ProductPartProcedureModel> qw = Wrappers.lambdaQuery();
		qw.in(ProductPartProcedureModel::getNumber, listData);
		qw.eq(ProductPartProcedureModel::getEnterpriseCode, getEnterpriseCode());
		qw.eq(ProductPartProcedureModel::getDeleteFlag, Boolean.FALSE);
		RequestTableHelper.setTableName(processRouteDataTableName);
		return productPartProcedureMapper.selectList(qw).stream()
				.collect(Collectors.toMap(model -> String.valueOf(model.getNumber()), Function.identity(),
						(existing, replacement) -> existing));
	}

	/**
	 * @description 校验集合对象 里面是否 是 null
	 *{
	 * "partName": null,
	 * "modelSpecification": null,
	 * "number": null,
	 * "procedureNumber": null,
	 * "checkFailList": []
	 *}
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2024/12/22 17:19
	 */
	private boolean hasNonNullMaterialModels(List<? extends MaterialModel> materialModels) {
		if (ObjectUtil.isEmpty(materialModels)) {
			return false;
		}
		for (MaterialModel materialModel : materialModels) {
			if (materialModel != null &&
					(materialModel.getMaterialUnityNo() != null)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @description 判断零件 小列表集合对象是否是 空
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2024/12/22 17:35
	 */
	public boolean hasNonNullPartMaterialModels(List<PartMaterialModel> partMaterialModels) {
		List<MaterialModel> materialModels = new ArrayList<>();
		for (PartMaterialModel partMaterialModel : partMaterialModels) {
			MaterialModel materialModel = new MaterialModel();
			BeanUtil.copyProperties(partMaterialModel, materialModel);
			materialModels.add(materialModel);
		}
		return hasNonNullMaterialModels(materialModels);
	}

	/**
	 * @description 判断产品 小列表集合对象是否是 空
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2024/12/22 17:35
	 */
	public boolean hasNonNullProductMaterialModels(List<ProductMaterialModel> productMaterialModels) {
		List<MaterialModel> materialModels = new ArrayList<>();
		for (ProductMaterialModel partMaterialModel : productMaterialModels) {
			MaterialModel materialModel = new MaterialModel();
			BeanUtil.copyProperties(partMaterialModel, materialModel);
			materialModels.add(materialModel);
		}
		return hasNonNullMaterialModels(materialModels);
	}

	public boolean hasNonNullWorkYStandardModelModels(List<WorkYStandardModel> productMaterialModels) {
		List<MaterialModel> materialModels = new ArrayList<>();
		for (WorkYStandardModel partMaterialModel : productMaterialModels) {
			MaterialModel materialModel = new MaterialModel();
			BeanUtil.copyProperties(partMaterialModel, materialModel);
			materialModels.add(materialModel);
		}
		return hasNonNullMaterialModels(materialModels);
	}

	public ProductPartModel prepareModelData(String name, String model,
	                                         Integer attribute, String productPartTableName) {
		LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
		qw.eq(ProductPartModel::getProductPartSign, name);
		qw.eq(ProductPartModel::getModel, model);
		qw.eq(ProductPartModel::getAttribute, attribute);
		qw.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode());
		qw.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE);
		RequestTableHelper.setTableName(productPartTableName);
		return productPartMapper.selectList(qw).stream().findFirst().orElse(null);
	}

	public ProductPartModel prepareModelData(String unityNo, String productPartTableName) {
		LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
		qw.in(ProductPartModel::getUnityNo, unityNo);
		qw.in(ProductPartModel::getAttribute, Arrays.asList(2, 3, 4));
		qw.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode());
		qw.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE);
		RequestTableHelper.setTableName(productPartTableName);
		return productPartMapper.selectList(qw).stream().findFirst().orElse(null);
	}

	public Map<Long, ProductPartModel> preparePartModelCode(List<Long> modelListData,
	                                                          String productPartTableName) {
		if (modelListData.isEmpty()) {
			return Collections.emptyMap();
		}
		LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
		qw.in(ProductPartModel::getProductPartCode, modelListData);
		qw.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode());
		qw.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE);
		RequestTableHelper.setTableName(productPartTableName);
		return productPartMapper.selectList(qw).stream()
				.collect(Collectors.toMap(ProductPartModel::getProductPartCode, Function.identity(),
						(existing, replacement) -> existing));
	}

//	@OptRecord(
//			desc = {
//					"新增了：#{#p0.productPartSign}"
//			},
//			dynamicType = "#{#p1}",
//			primaryKey = "#{#p0.productPartCode}"
//	)
//	public void logProductPartAdd(ProductPartModel model, StaffOperationLogTypeEnum typeEnum) {}

	@OptRecord(
			desc = {
					"修改编号：#{#p0.unityNo} 变更为 #{#p1.unityNo}",
					"修改名称：#{#p0.productPartSign} 变更为 #{#p1.productPartSign}",
					"修改型号：#{#p0.model} 变更为 #{#p1.model}",
					"修改状态：#{#p0.state ? '启用' : '停用'} 变更为 #{#p1.state ? '启用' : '停用'}"
			},
			primaryKey = "#{#p1.productPartCode}",
			dynamicType = "#{#p2}",
			recordIfChanged = true
	)
	public void logProductPartUpdate(ProductPartModel oldModel, ProductPartModel newModel, StaffOperationLogTypeEnum typeEnum) {}

	@OptRecord(
			desc = {
					"修改默认工艺路线：#{#p0} 变更为 #{#p1}"
			},
			primaryKey = "#{#p2}",
			dynamicType = "#{#p3}",
			recordIfChanged = true
	)
	public void logProductPartRouteUpdate(String oldRouteName, String newRouteName, Long productPartCode, StaffOperationLogTypeEnum typeEnum) {}

	@OptRecord(
			desc = {
					"删除了：#{#p0.productPartSign}"
			},
			dynamicType = "#{#p1}",
			primaryKey = "#{#p0.productPartCode}"
	)
	public void logProductPartDelete(ProductPartModel model, StaffOperationLogTypeEnum typeEnum) {}

	public StaffOperationLogTypeEnum resolveLogType(Integer attribute) {
		PPAttributeEnum attrEnum = PPAttributeEnum.getEnumByCode(attribute);
		return switch (attrEnum) {
			case PRODUCT -> StaffOperationLogTypeEnum.PRODUCT_CHANGE;
			case COMPONENT -> StaffOperationLogTypeEnum.COMPONENT_CHANGE;
			case PART -> StaffOperationLogTypeEnum.PART_CHANGE;
			case MATERIAL -> StaffOperationLogTypeEnum.MATERIAL_CHANGE;
			default -> StaffOperationLogTypeEnum.PRODUCT_CHANGE;
		};
	}

	/**
	 * 准备产品部件类型数据
	 * @param listData 部件类型名称列表
	 * @param typeTableName 表名
	 * @param isAllAttribute 是否获取所有属性
	 * @return 返回部件类型名称到部件类型模型的映射
	 */
	public Map<String, ProductPartTypeModel> prepareTypeData(List<String> listData,
	                                                         String typeTableName,Boolean isAllAttribute) {
    // 如果传入的列表为空，直接返回空Map
		if (listData.isEmpty()) {
			return Collections.emptyMap();
		}
    // 创建Lambda查询包装器，用于构建查询条件
		LambdaQueryWrapper<ProductPartTypeModel> qw = Wrappers.lambdaQuery();
    // 添加查询条件：部件类型名称在传入的列表中
		qw.in(ProductPartTypeModel::getTypeName, listData);
    // 添加查询条件：属性值为1、2、3或4
		qw.in(ProductPartTypeModel::getAttribute, Arrays.asList(1,2, 3, 4));
    // 添加查询条件：企业代码匹配当前企业
		qw.eq(ProductPartTypeModel::getEnterpriseCode, getEnterpriseCode());
    // 添加查询条件：删除标记为false（未删除）
		qw.eq(ProductPartTypeModel::getDeleteFlag, Boolean.FALSE);
    // 设置动态表名
		RequestTableHelper.setTableName(typeTableName);
    // 执行查询并将结果转换为Map，以部件类型名为键，部件类型模型为值
		return productPartTypeMapper.selectList(qw).stream()
				.collect(Collectors.toMap(ProductPartTypeModel::getTypeName, Function.identity(),
						(existing, replacement) -> existing));
	}


/**
 * 准备模型数据
 * @param unityNo 统一编号
 * @param productPartTableName 产品部件表名
 * @param isAllAttribute 是否获取所有属性
 * @return ProductPartModel 产品部件模型
 */
	public ProductPartModel prepareModelData(String unityNo, String productPartTableName, Boolean isAllAttribute) {
	// 创建Lambda查询包装器
		LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
	// 添加查询条件：统一编号在指定列表中
		qw.in(ProductPartModel::getUnityNo, unityNo);
	// 添加查询条件：属性值为2、3或4
		qw.in(ProductPartModel::getAttribute, Arrays.asList(1, 2, 3, 4));
	// 添加查询条件：企业代码匹配当前企业代码
		qw.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode());
	// 添加查询条件：删除标记为false
		qw.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE);
	// 设置动态表名
		RequestTableHelper.setTableName(productPartTableName);
	// 执行查询并返回第一个结果，如果没有则返回null
		return productPartMapper.selectList(qw).stream().findFirst().orElse(null);
	}

	/**
	 * 检查产品部件的名称和型号是否已存在
	 * @param requestData 包含要检查的产品部件属性和名称、型号信息的请求对象
	 * @param productPartTableName 产品部件表名
	 * @return ProductPartResultRpcDTO 包含检查结果、返回代码、提示信息和是否允许操作的信息
	 */
	public ProductPartResultRpcDTO checkNameAndModelTool(ProductPartCheckNameModelRpcRequest requestData,String productPartTableName) {
    // 创建返回结果对象
		ProductPartResultRpcDTO result = new ProductPartResultRpcDTO();
    // 初始化是否允许操作的标志为false
		boolean isAllow = true;
    // 初始化提示信息
		String msg = "提示信息：";
    // 初始化返回代码为-1（表示检查失败）
		String code = "0";
    // 创建Lambda查询条件构造器
		LambdaUpdateWrapper<ProductPartModel> queryWp = Wrappers.lambdaUpdate();
    // 设置查询条件：属性、产品部件类型代码、状态（有效）、删除标志（未删除）、企业代码
		queryWp.eq(ProductPartModel::getAttribute, requestData.getAttribute())
				.eq(ProductPartModel::getProductPartTypeCode, requestData.getProductPartTypeCode())
				.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE)
				.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode())
				.eq(ProductPartModel::getProductPartSign, requestData.getName())
				.eq(ProductPartModel::getModel, requestData.getModel());
    // 设置动态表名
		RequestTableHelper.setTableName(productPartTableName);
    // 根据条件查询产品部件列表
		List<ProductPartModel> productPartList = productPartMapper.selectList(queryWp);
    // 如果请求中包含产品部件代码且不为0，则从查询结果中过滤掉当前数据对象
		if (null != productPartList && productPartList.size() > 0) {
			if (!StringUtils.isEmpty(requestData.getUnityNo())) {
				if (1 == productPartList.size() && requestData.getUnityNo().equals(productPartList.get(0).getUnityNo())) {
					result.setReturnCode(code);
					result.setReturnMsg(msg);
					result.setIsAllow(isAllow);
					return result;
				}
			}
			msg += " 当前分类下产品部件名称型号已存在";
			isAllow = false;
			code = "-1";
		}
		result.setReturnCode(code);
		result.setReturnMsg(msg);
		result.setIsAllow(isAllow);
		return result;
	}
}
