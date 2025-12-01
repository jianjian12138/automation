package com.futurecraftsmen.pms.technical.service.domain.technical.productpart.detail;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.futurecraftsmen.pms.common.domain.excel.BatchImportSelectionTypeEnum;
import com.futurecraftsmen.pms.common.domain.excel.BatchUserImportSelection;
import com.futurecraftsmen.pms.common.domain.exception.ExcelRuntimeException;
import com.futurecraftsmen.pms.common.excel.multi.BaseExcelMultiValidator;
import com.futurecraftsmen.pms.dm.api.service.base.staff.StaffService;
import com.futurecraftsmen.pms.service.configuration.RequestTableHelper;
import com.futurecraftsmen.pms.service.domain.common.constant.CommonConstant;
import com.futurecraftsmen.pms.technical.api.domain.technical.process.ProcessRouteNodeModel;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.excel.product.ProductDetailExcelRpcModel;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.excel.product.ProductExcelRpcModel;
import com.futurecraftsmen.pms.technical.service.common.enums.ExcelValidatorEnum;
import com.futurecraftsmen.pms.technical.service.config.EnumCodeConfig;
import com.futurecraftsmen.pms.technical.service.dao.IPiecesMapper;
import com.futurecraftsmen.pms.technical.service.dao.technical.IProcessRouteDataMapper;
import com.futurecraftsmen.pms.technical.service.dao.technical.IProductPartMapper;
import com.futurecraftsmen.pms.technical.service.dao.technical.IProductPartProcedureMapper;
import com.futurecraftsmen.pms.technical.service.dao.technical.IProductPartTypeMapper;
import com.futurecraftsmen.pms.technical.service.domain.technical.procedure.ProductPartProcedureModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.process.ProcessRouteDataModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.productpart.ProductPartModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.type.ProductPartTypeModel;
import com.futurecraftsmen.pms.technical.service.impl.inner.TableNameFactory;
import com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart.action.unified.TechnicalUnifiedDataService;
import com.futurecraftsmen.pms.technical.service.impl.technical.relationship.ProcedureRouteRelationshipServiceImpl;

import org.aerie.forest.core.brick.domain.view.CodeMapName;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import static com.futurecraftsmen.pms.service.domain.extract.ExtractUtil.streamMapToList;
import static com.futurecraftsmen.pms.technical.service.util.CommonUtil.getEnterpriseCode;

/**
 * @description 产品详情配套零件导入校验
 *
 * @author qierkang
 * @organization futurecraftsmen
 * @date Created in 2024/12/10 下午8:56
 */
@Slf4j
@Component
public class ProductDetailExcelValidator extends BaseExcelMultiValidator<ProductDetailExcelRpcModel> {

	@Resource
	private TableNameFactory tableFactory;
	@Resource
	private EnumCodeConfig enumCodeConfig;
	@Resource
	private IProductPartProcedureMapper productPartProcedureMapper;
	@Resource
	private IProductPartMapper productPartMapper;
	@Resource
	private IProcessRouteDataMapper processRouteDataMapper;
	@Resource
	private IProductPartTypeMapper productPartTypeMapper;
	@DubboReference(check = false, retries = 0)
	private StaffService staffService;
	@Resource
	private IPiecesMapper piecesMapper;
	@Resource
	private TechnicalUnifiedDataService unifiedDataService;
	@Resource
	private ProcedureRouteRelationshipServiceImpl routeRelationshipService;

	@Override
	public void checkAll(List<ProductDetailExcelRpcModel> allExcelModel, String dataInfo) {
		if (allExcelModel.isEmpty()) {
			return;
		}
		try {
			String productPartTypeTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
					tableFactory.table.getProductPartType());
			String productPartTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
					tableFactory.table.getProductPart());
			String piecesTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
					tableFactory.table.getPieces());
			String processRouteDataTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
					tableFactory.table.getProcessRouteData());
			String productPartProcedureTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
					tableFactory.table.getProductPartProcedure());
			// 工序表与工艺路线关系表
			String procedureRouteRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
					tableFactory.table.getProcedureRouteRelationship());
			String processRouteDataCode = dataInfo;
			// 获取统一数据 分类数据
			List<String> typeNameList = streamMapToList(String::valueOf, allExcelModel, ProductDetailExcelRpcModel::getProductPartTypeName);
			Map<String, ProductPartTypeModel> typeNameToModelMap = unifiedDataService.prepareTypeData(typeNameList,
					productPartTypeTableName, CommonConstant.NUMBER_TWO);
			// 零件名称+零件型号数据准备
			//List<String> modelListData = streamMapToList(String::valueOf, allExcelModel,
			//		ProductDetailExcelRpcModel::getModel)
			//		.stream().map(String::toLowerCase)
			//		.collect(Collectors.toList());
			//Map<String, ProductPartModel> partToModelMap = unifiedDataService.preparePartModelData(modelListData, CommonConstant.NUMBER_TWO,
			//		productPartTableName);

			// 工序号数据准备
			List<String> procedureNumberData = streamMapToList(String::valueOf, allExcelModel,
							ProductDetailExcelRpcModel::getProcedureNumber);
			Map<String, ProductPartProcedureModel> numberToModelMap;
			if (!procedureNumberData.isEmpty()) {
				List<String> numberListData = streamMapToList(String::valueOf, allExcelModel,
						ProductDetailExcelRpcModel::getProcedureNumber);
				if (!numberListData.isEmpty()) {
					numberToModelMap = unifiedDataService.numberDataSignToModelMap(numberListData,
							productPartProcedureTableName);
				} else {numberToModelMap = new HashMap<>();}
			} else {numberToModelMap = new HashMap<>();}
			Map<String, ProductPartProcedureModel> numberToExistMap = Map.of();
			// 工序号不在当前工艺路线里面 数据准备
			if (processRouteDataCode != null) {
				RequestTableHelper.setTableName(processRouteDataTableName);
				ProcessRouteDataModel routeDataModel = processRouteDataMapper.selectById(Long.parseLong(processRouteDataCode));
				List<ProcessRouteNodeModel> routeNodeModels = JSONUtil.toList(routeDataModel.getRouteNode(), ProcessRouteNodeModel.class);
				List<Long> numberListData = streamMapToList(String::valueOf, routeNodeModels,
						ProcessRouteNodeModel::getProductPartProcedureCode)
						.stream().map(Long::valueOf)
						.collect(Collectors.toList());
				if (!numberListData.isEmpty()) {
					// 工序号数据准备
					LambdaQueryWrapper<ProductPartProcedureModel> qw = Wrappers.lambdaQuery();
					qw.in(ProductPartProcedureModel::getProductPartProcedureCode, numberListData);
					qw.eq(ProductPartProcedureModel::getEnterpriseCode, getEnterpriseCode());
					qw.eq(ProductPartProcedureModel::getDeleteFlag, Boolean.FALSE);
					RequestTableHelper.setTableName(productPartProcedureTableName);
					numberToExistMap = productPartProcedureMapper.selectList(qw).stream()
							.collect(Collectors.toMap(model -> String.valueOf(model.getNumber()), Function.identity()));
				}
			}
			for (ProductDetailExcelRpcModel excelModel : allExcelModel) {
				// 错误信息集合
				List<String> checkFailList = excelModel.getCheckFailList();
				// 提示信息集合
				List<String> checkReminderList = excelModel.getCheckReminderList();

				// 零件维度：分类空 错误校验
				String typeStr = excelModel.getProductPartTypeName();
				if (StrUtil.isEmpty(typeStr)) {
					checkFailList.add(ExcelValidatorEnum.TYPE_NOT_EXIST.getMsg());
				}

				// 零件维度：名称空 错误校验
				String nameStr = excelModel.getName();
				if (StrUtil.isEmpty(nameStr)) {
					checkFailList.add(ExcelValidatorEnum.NAME_MODEL_EXIST.getMsg());
				}

				// 零件维度：型号空 错误校验
				String modelStr = excelModel.getModel();
				if (ObjectUtil.isEmpty(modelStr)) {
					checkFailList.add(ExcelValidatorEnum.MODEL_NOT_EXIST.getMsg());
				}

				// 零件维度：分类错误校验
				String seriesName = excelModel.getProductPartTypeName();
				ProductPartTypeModel productTypeModel = typeNameToModelMap.get(seriesName);
				if (ObjectUtil.isEmpty(typeNameToModelMap.get(seriesName))) {
					checkFailList.add(StrUtil.format(ExcelValidatorEnum.PART_TYPE_NOT_EXIST.getMsg(), seriesName));
				} else {
					excelModel.setProductPartTypeCode(productTypeModel.getProductPartTypeCode());
				}

				ProductPartModel productPartModel = unifiedDataService.prepareModelData(excelModel.getName(), excelModel.getModel(),
						CommonConstant.NUMBER_TWO, productPartTableName);
				if (productPartModel != null) {
					excelModel.setProductPartCode(String.valueOf(productPartModel.getProductPartCode()));
				}
				ProductPartProcedureModel procedureModel = numberToModelMap.get(excelModel.getProcedureNumber());
				if (procedureModel != null) {
					excelModel.setProductPartProcedureCode(String.valueOf(procedureModel.getProductPartProcedureCode()));
				}

				// 零件维度：零件规格错误校验|校验零件是否存在
				String model = excelModel.getModel();
				if (StrUtil.isNotBlank(model) &&
						ObjectUtil.isEmpty(productPartModel)) {
					checkFailList.add(StrUtil.format(ExcelValidatorEnum.PART_NAME_MODEL_NOT_EXIST.getMsg(), model));
				}

				// 零件维度：校验零件状态是否启用
				if (productPartModel != null && StrUtil.isNotBlank(excelModel.getModel())
						&& Boolean.FALSE.equals(productPartModel.getState())) {
					checkFailList.add(StrUtil.format(ExcelValidatorEnum.PART_STATE_ENABLE_NOT_EXIST.getMsg()));
				}

				// 零件维度：如果有零件信息没有填写工序号信息
				if (StrUtil.isNotBlank(excelModel.getName()) &&
						StrUtil.isNotBlank(excelModel.getModel()) &&
						StrUtil.isBlank(excelModel.getProcedureNumber())) {
					checkReminderList.add(StrUtil.format(ExcelValidatorEnum.PART_PROCEDURE_NOT_EXIST.getMsg(),
							excelModel.getProcedureNumber()));
				}

				// 零件维度：数量校验，如果getNumber=空且有配套零件信息时 赋值默认值=1
				if (StrUtil.isBlank(excelModel.getNumber()) 
					&& (StrUtil.isNotBlank(excelModel.getName()) 
						|| StrUtil.isNotBlank(excelModel.getModel()))) {
					excelModel.setNumber(String.valueOf(CommonConstant.NUMBER_ONE));
				}

				// 零件维度：数量校验，如果<=0进行错误提示
				if (NumberUtil.isNumber(excelModel.getNumber())) {
					int number = Integer.parseInt(excelModel.getNumber());
					if (number < 0) {
						checkFailList.add(StrUtil.format(ExcelValidatorEnum.QUANTITY_ERROR.getMsg(),
								excelModel.getProcedureNumber()));
					}
				}

				// 零件维度：工序号 错误校验
				if (StrUtil.isNotBlank(excelModel.getProcedureNumber())
						&& ObjectUtil.isEmpty(procedureModel)) {
					checkFailList.add(StrUtil.format(ExcelValidatorEnum.PROCEDURE_NUMBER_NOT_EXIST.getMsg(),
							excelModel.getProcedureNumber()));
				}

				// 零件维度：工序号不在当前工艺路线里面 错误校验
				if (StrUtil.isNotBlank(excelModel.getProcedureNumber())
						&& ObjectUtil.isEmpty(numberToExistMap.get(excelModel.getProcedureNumber()))) {
					checkFailList.add(StrUtil.format(ExcelValidatorEnum.PROCEDURE_NUMBER_ROUTE_DATA_NOT_EXIST.getMsg(),
							excelModel.getProcedureNumber()));
				}
				excelModel.setCheckFailList(checkFailList);
				excelModel.setCheckPartReminderList(checkReminderList);
			}
		} catch (Exception e) {
			log.error("Error during checkAll: ", e);
			throw new ExcelRuntimeException(e, "", "Unable to complete full verification");
		}
	}

	/**
	 * @description 检查指定人员信息是否符合要求
	 * 此方法主要用于检查在程序Excel导入过程中，指定的人员信息是否存在于给定的映射中
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2024/12/8 下午5:52
	 */
	private void checkPerson(Map<String, List<CodeMapName>> staffNameMapShowInformation, ProductExcelRpcModel model,
	                         List<String> checkFailList, List<String> checkReminderList, List<BatchUserImportSelection> userSelections,
	                         String notExistMessage, String duplicateMessage, BatchImportSelectionTypeEnum type, Function<ProductExcelRpcModel,
					String> personGetter) {
		String person = personGetter.apply(model);
		if (StrUtil.isNotBlank(person) && ObjectUtil.isNotEmpty(staffNameMapShowInformation)) {
			checkUserSelection(staffNameMapShowInformation, person, checkFailList, notExistMessage, type, checkReminderList, duplicateMessage,
					userSelections);
		}
	}

	@Override
	protected Class<ProductDetailExcelRpcModel> setExcelModelClassForUserSelection() {
		return ProductDetailExcelRpcModel.class;
	}
}
