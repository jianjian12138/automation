/*
 * Copyright (c) Zhongzao Software Co. LTD 2022-2062 All rights reserved
 */

package com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart.action.unified;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.futurecraftsmen.pms.common.domain.StaffOperationLogTypeEnum;
import com.futurecraftsmen.pms.common.utils.ExtractMaxNumberUtil;
import com.futurecraftsmen.pms.dp.api.domain.GlobalSerialNumberRequest;
import com.futurecraftsmen.pms.dp.api.domain.GlobalSerialNumberResponse;
import com.futurecraftsmen.pms.dp.api.service.GlobalNumberServer;
import com.futurecraftsmen.pms.service.configuration.EnCodePropertiesConfig;
import com.futurecraftsmen.pms.service.configuration.RequestTableHelper;
import com.futurecraftsmen.pms.service.domain.common.constant.CommonConstant;
import com.futurecraftsmen.pms.service.domain.extract.ExtractUtil;
import com.futurecraftsmen.pms.starter.domain.starter.PmsStarter;
import com.futurecraftsmen.pms.technical.api.domain.technical.procedure.ProductPartProcedureDetailRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.procedure.ProductPartProcedureRpcRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.process.ProcessRouteDataDetailRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.process.ProcessRouteNodeModel;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.ProductPartAddRpcRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.ProductPartRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.ProductPartUpdateRpcRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.common.ProductPartSearchObjRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.xihang.ProductPartConsistDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.xihang.ProductPartConsistTreeDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.xihang.comp.ProductPartChild;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.xihang.comp.ProductPartCompRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.xihang.comp.ProductPartConsistRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.type.ProductPartTreeNodeRpcDTO;
import com.futurecraftsmen.pms.technical.api.service.IPiecesService;
import com.futurecraftsmen.pms.technical.api.service.technical.IProductPartCommonService;
import com.futurecraftsmen.pms.technical.service.anno.OptRecord;
import com.futurecraftsmen.pms.technical.service.common.enums.TechnicalErrorEnum;
import com.futurecraftsmen.pms.technical.service.dao.HistoryPriceMapper;
import com.futurecraftsmen.pms.technical.service.dao.technical.IProductPartCompMapper;
import com.futurecraftsmen.pms.technical.service.dao.technical.IProductPartMapper;
import com.futurecraftsmen.pms.technical.service.dao.technical.IProductPartProcedureMapper;
import com.futurecraftsmen.pms.technical.service.dao.technical.IProductPartTypeMapper;
import com.futurecraftsmen.pms.technical.service.dao.warehouse.IOutboundOrderDetailMapper;
import com.futurecraftsmen.pms.technical.service.dao.warehouse.IStockMapper;
import com.futurecraftsmen.pms.technical.service.domain.baseModule.HistoryPriceModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.procedure.ProcedurePartRelationshipModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.procedure.ProcedureRouteRelationshipModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.procedure.ProductPartProcedureModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.process.ProcessRouteDataModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.productpart.ProductPartModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.productpart.ProductPartRouteRelationshipModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.productpart.comp.ProductPartCompModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.productpart.comp.ProductPartConsistModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.type.ProductPartTypeModel;
import com.futurecraftsmen.pms.technical.service.domain.warehouse.OutboundOrderDetailModel;
import com.futurecraftsmen.pms.technical.service.domain.warehouse.StockModel;
import com.futurecraftsmen.pms.technical.service.impl.inner.PurchaseReceivingInnerService;
import com.futurecraftsmen.pms.technical.service.impl.inner.TableNameFactory;
import com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart.IProductPartCompServiceImpl;
import com.futurecraftsmen.pms.technical.service.impl.technical.procedure.ProductPartProcedureServiceImpl;
import com.futurecraftsmen.pms.technical.service.impl.technical.relationship.ProcedurePartRelationshipServiceImpl;
import com.futurecraftsmen.pms.technical.service.impl.technical.relationship.ProcedureRouteRelationshipServiceImpl;
import com.futurecraftsmen.pms.technical.service.impl.technical.relationship.ProductPartRouteRelationshipServiceImpl;
import com.futurecraftsmen.pms.technical.service.impl.technical.route.ProcessRouteDataServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.aerie.forest.core.brick.exception.ExceptionMsg;
import org.aerie.forest.core.brick.exception.ExceptionPack;
import org.aerie.forest.core.brick.exception.assertprocess.AssertException;
import org.aerie.forest.core.brick.processor.datapenetrate.GlobalTokenPenetrate;
import org.aerie.forest.core.brick.processor.datapenetrate.InfoPenetrateProcessor;
import org.apache.commons.collections4.MapUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.futurecraftsmen.pms.technical.service.util.CommonUtil.getEnterpriseCode;

@Slf4j
@DubboService(group = "pms")
public class IProductPartCommonServiceImpl extends ServiceImpl<IProductPartMapper, ProductPartModel> implements IProductPartCommonService {

	@Resource
	private TableNameFactory tableFactory;
	@Resource
	private IProductPartTypeMapper productPartTypeMapper;
	@Resource
	private IOutboundOrderDetailMapper outboundOrderDetailMapper;
	@Resource
	private HistoryPriceMapper historyPriceMapper;
	@Resource
	private ProductPartRouteRelationshipServiceImpl productPartRouteRelationshipService;
	@Resource
	private ProcedurePartRelationshipServiceImpl procedurePartRelationshipService;
	@Resource
	private IStockMapper stockMapper;
	@Resource
	private ProcedureRouteRelationshipServiceImpl procedureRouteRelationshipService;
	@Resource
	private IProductPartCompMapper productPartCompMapper;
	@Resource
	private ProductPartProcedureServiceImpl productPartProcedureService;
	@DubboReference(check = false, retries = 0, timeout = 600000)
	private GlobalNumberServer globalNumberServer;
	@Resource
	private EnCodePropertiesConfig enCodePropertiesConfig;
	@Resource
	private IPiecesService piecesService;
	@Resource
	private IProductPartCompServiceImpl productPartCompService;
	@Resource
	private ProcessRouteDataServiceImpl processRouteDataService;
	@Resource
	private IProductPartProcedureMapper productPartProcedureMapper;
	@Resource
	private IProductPartMapper productPartMapper;
	@Resource
	private TechnicalUnifiedDataService unifiedDataService;

	@Autowired
	private PurchaseReceivingInnerService purchaseReceivingInnerService;
	@Resource
	private ProcedureRouteRelationshipServiceImpl routeRelationshipService;

	/**
	 * @description 验证产品类型是否存在
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/13 16:26
	 */
	public void validateProductPartTypeUpdate(ProductPartUpdateRpcRequest requestData, String productPartTypeTableName)
			throws AssertException {
		if (requestData.getProductPartTypeCode() != null) {
			LambdaQueryWrapper<ProductPartTypeModel> typeQw = Wrappers.lambdaQuery();
			typeQw.eq(ProductPartTypeModel::getProductPartTypeCode, requestData.getProductPartTypeCode());
			typeQw.eq(ProductPartTypeModel::getAttribute, requestData.getAttribute());
			typeQw.eq(ProductPartTypeModel::getEnterpriseCode, getEnterpriseCode());
			typeQw.eq(ProductPartTypeModel::getDeleteFlag, Boolean.FALSE);
			RequestTableHelper.setTableName(productPartTypeTableName);
			if (productPartTypeMapper.selectCount(typeQw) == CommonConstant.NUMBER_ZERO) {
				throw new AssertException(ExceptionMsg.builder("validateProductPartTypeUpdate This method failed")
						.msgView(TechnicalErrorEnum.TECHNICAL_TYPE_NOT_MODEL_EXIST.getMsg()).build());
			}
		}
	}

	/**
	 * @description 验证编号是否存在
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/29 22:40
	 * @department: Product development
	 */
	public void validateUnityNo(Long productPartCode, String unityNo)
			throws AssertException {
		if (StrUtil.isBlank(unityNo)) {
			return;
		}
		// 新技术部-产品零件表
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
		LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
		qw.eq(ProductPartModel::getUnityNo, unityNo);
		qw.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode());
		RequestTableHelper.setTableName(productPartTableName);
		ProductPartModel productPartModel = this.list(qw).stream().findFirst().orElse(null);
		if (productPartModel != null && !productPartModel.getProductPartCode().equals(productPartCode)) {
			throw new AssertException(ExceptionMsg.builder("")
					.msgView(TechnicalErrorEnum.TECHNICAL_PRODUCT_PART_UNITY_NO_EXIST_MESSAGE.getMsg())
					.build());
		}
	}

	/**
	 * @description 验证产品类型是否存在
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/13 16:27
	 */
	public void validateProductPartTypeAdd(ProductPartAddRpcRequest requestData, String productPartTypeTableName)
			throws AssertException {
		if (requestData.getProductPartTypeCode() != null) {
			LambdaQueryWrapper<ProductPartTypeModel> typeQw = Wrappers.lambdaQuery();
			typeQw.eq(ProductPartTypeModel::getProductPartTypeCode, requestData.getProductPartTypeCode());
			typeQw.eq(ProductPartTypeModel::getAttribute, requestData.getAttribute());
			typeQw.eq(ProductPartTypeModel::getEnterpriseCode, getEnterpriseCode());
			typeQw.eq(ProductPartTypeModel::getDeleteFlag, Boolean.FALSE);
			RequestTableHelper.setTableName(productPartTypeTableName);
			if (productPartTypeMapper.selectCount(typeQw) == CommonConstant.NUMBER_ZERO) {
				throw new AssertException(ExceptionMsg.builder("validateProductPartTypeAdd This method failed")
						.msgView(TechnicalErrorEnum.TECHNICAL_TYPE_NOT_MODEL_EXIST.getMsg()).build());
			}
		}
	}

	/**
	 * @description
	 * 如果是产品：产品名称可以重复、产品型号不可以重复
	 * 如果是零件：零件名称+型号，唯一
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/13 16:29
	 */
	public void validateProductOrPartAdd(ProductPartAddRpcRequest requestData, String tableName)
			throws AssertException {
		Integer attribute = requestData.getAttribute();
		LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
		if (StrUtil.length(requestData.getName()) > 64) {
			throw new AssertException(ExceptionMsg.builder("TECHNICAL_NAME_EXTRA_LONG_MESSAGE This method failed")
					.msgView(TechnicalErrorEnum.TECHNICAL_NAME_EXTRA_LONG_MESSAGE.getMsg()).build());
		}
		if (attribute.equals(CommonConstant.NUMBER_ONE)) {
			qw.eq(ProductPartModel::getModel, requestData.getModel());
		} else {
			qw.eq(ProductPartModel::getProductPartSign, requestData.getName());
			qw.eq(ProductPartModel::getModel, requestData.getModel());
		}
		qw.eq(ProductPartModel::getAttribute, attribute);
		TechnicalErrorEnum mesEnum = attribute.equals(CommonConstant.NUMBER_ONE)
				? TechnicalErrorEnum.TECHNICAL_PRODUCT_MODEL_EXIST : TechnicalErrorEnum.TECHNICAL_PRAT_MODEL_EXIST;
		RequestTableHelper.setTableName(tableName);
		long count = this.count(qw);
		if (count > CommonConstant.NUMBER_ZERO) {
			throw new AssertException(
					ExceptionMsg.builder("导入零件校验 This batchDetailAddProductPart method failed to execute count(qw) > "
							+ "CommonConstant.NUMBER_ZERO").msgView(mesEnum.getMsg()).build());
		}
	}

	/**
	 * @description
	 * 产品名称可以重复、产品型号不可以重复
	 * 零件名称+型号，唯一
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/13 16:22
	 */
	public void validateProductOrPartUpdate(ProductPartModel productPartModel, ProductPartUpdateRpcRequest requestData,
	                                        String tableName) throws AssertException {
		Integer attribute = requestData.getAttribute();
		LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
		if (StrUtil.length(productPartModel.getProductPartSign()) > 64) {
			throw new AssertException(ExceptionMsg.builder("TECHNICAL_NAME_EXTRA_LONG_MESSAGE This method failed")
					.msgView(TechnicalErrorEnum.TECHNICAL_NAME_EXTRA_LONG_MESSAGE.getMsg()).build());
		}
		if (attribute.equals(CommonConstant.NUMBER_ONE)) {
			qw.eq(ProductPartModel::getModel, requestData.getModel());
		}
		if (attribute.equals(CommonConstant.NUMBER_TWO)) {
			qw.eq(ProductPartModel::getProductPartSign, requestData.getName());
			qw.eq(ProductPartModel::getModel, requestData.getModel());
		}
		qw.eq(ProductPartModel::getAttribute, attribute);
		qw.eq(ProductPartModel::getDeleteFlag, Boolean.FALSE);
		RequestTableHelper.setTableName(tableName);
		String mes =
				attribute.equals(CommonConstant.NUMBER_ONE) ? TechnicalErrorEnum.TECHNICAL_PRODUCT_MODEL_EXIST.getMsg()
						: TechnicalErrorEnum.TECHNICAL_PRODUCT_PART_MODEL_EXIST_MESSAGE.getMsg();
		boolean count = count(qw) > CommonConstant.NUMBER_ZERO;
		boolean shouldThrowException = ObjectUtil.notEqual(productPartModel.getProductPartSign(), requestData.getName())
				|| ObjectUtil.notEqual(productPartModel.getModel(), requestData.getModel());
		if (count && shouldThrowException) {
			// 如果是产品&&产品名称不一致，产品型号一致
			if (attribute.equals(CommonConstant.NUMBER_ONE) && ObjectUtil.notEqual(productPartModel.getProductPartSign(), requestData.getName()) &&
					ObjectUtil.equal(productPartModel.getModel(), requestData.getModel())) {
				return;
			}
			throw new AssertException(
					ExceptionMsg.builder("validateProductOrPartUpdate This method failed").msgView(mes).build());
		}
	}

	/**
	 * @description 校验产品零件是否被调度安排过
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/2/19 15:14
	 */
	public boolean checkDeleteProductPartDispatch(Long productPartCode) {
		String outboundOrderDetailTableName = tableFactory.getTableName(tableFactory.module.getWarehouse(),
				tableFactory.table.getOutboundOrderDetail());
		LambdaQueryWrapper<OutboundOrderDetailModel> query = Wrappers.lambdaQuery();
		query.eq(OutboundOrderDetailModel::getProductOrPart, productPartCode);
		query.eq(OutboundOrderDetailModel::getEnterpriseCode, getEnterpriseCode());
		RequestTableHelper.setTableName(outboundOrderDetailTableName);
		return outboundOrderDetailMapper.selectCount(query) == CommonConstant.NUMBER_ZERO;
	}

	/**
	 * @description 校验产品是否有已签章的销售合同/采购合同涉及到
	 *              检查零件是否有已签章的采购合同涉及到
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/2/19 15:14
	 */
	public boolean checkDeleteProductPartNonSignedContract(Long productPartCode) {
		String historyPriceTableName = tableFactory.getTableName(tableFactory.module.getBaseModule(),
				tableFactory.table.getHistoryPrice());
		LambdaQueryWrapper<HistoryPriceModel> query = Wrappers.lambdaQuery();
		query.eq(HistoryPriceModel::getMaterialCode, productPartCode);
		query.in(HistoryPriceModel::getType, List.of(1, 3));
		query.eq(HistoryPriceModel::getEnterpriseCode, getEnterpriseCode());
		RequestTableHelper.setTableName(historyPriceTableName);
		boolean hasAnyHistoryPrice = historyPriceMapper.selectCount(query) > CommonConstant.NUMBER_ZERO;
		if (hasAnyHistoryPrice) {
			return false;
		}
		//查看是否有收货详情
		return purchaseReceivingInnerService.hasNonPurchaseReceivingDetail(productPartCode, getEnterpriseCode());

	}

	/**
	 * @description 检查工序是否绑定了零件信息
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/2/19 15:14
	 */
	public boolean checkDeleteProductPartRelationship(Long productPartCode) {
		// 产品零件与工艺路线关系表
		String productPartRouteRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProductPartRouteRelationship());
		// 校验产品零件是否被工序绑定
		LambdaQueryWrapper<ProductPartRouteRelationshipModel> queryRoute = Wrappers.lambdaQuery();
		queryRoute.eq(ProductPartRouteRelationshipModel::getProductPartCode, productPartCode);
		queryRoute.eq(ProductPartRouteRelationshipModel::getEnterpriseCode, getEnterpriseCode());
		queryRoute.eq(ProductPartRouteRelationshipModel::getDeleteFlag, Boolean.FALSE);
		RequestTableHelper.setTableName(productPartRouteRelationshipTableName);
		return productPartRouteRelationshipService.list(queryRoute).stream()
				.anyMatch(p -> {
					ProductPartProcedureRpcRequest procedureData = new ProductPartProcedureRpcRequest()
							.setProcessRouteDataCode(p.getProcessRouteDataCode())
							.setUniqueId(p.getUniqueId());
					return detailProcessEmpty(procedureData);
				});
	}

	/**
	 * @description 查询工艺路线下面工序是否空
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/14 09:55
	 */
	public boolean detailProcessEmpty(ProductPartProcedureRpcRequest requestData) {
		// 工序表与零件关系表
		String procedurePartRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProcedurePartRelationship());
		// 查询当前工艺路线下面工序是否有零件信息
		LambdaQueryWrapper<ProcedurePartRelationshipModel> queryProcedurePart = Wrappers.lambdaQuery();
		if (requestData.getProductPartCode() != null) {
			queryProcedurePart.eq(ProcedurePartRelationshipModel::getProductPartCode, requestData.getProductPartCode());
		}
		if (requestData.getProcessRouteDataCode() != null) {
			queryProcedurePart.eq(ProcedurePartRelationshipModel::getProcessRouteDataCode, requestData.getProcessRouteDataCode());
		}
		if (requestData.getUniqueId() != null) {
			queryProcedurePart.eq(ProcedurePartRelationshipModel::getUniqueId, requestData.getUniqueId());
		}
		queryProcedurePart.eq(ProcedurePartRelationshipModel::getEnterpriseCode, getEnterpriseCode());
		queryProcedurePart.eq(ProcedurePartRelationshipModel::getDeleteFlag, Boolean.FALSE);
		RequestTableHelper.setTableName(procedurePartRelationshipTableName);
		return procedurePartRelationshipService.count(queryProcedurePart) > 0;
	}

	/**
	 * @description 检查是否有库存数据
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/2/19 15:14
	 */
	public boolean checkDeleteStockModel(Long productPartCode) {
		// 库存表
		String stockTableName =
				tableFactory.getTableName(tableFactory.module.getWarehouse(), tableFactory.table.getStock());
		StockModel stockModel = stockMapper.getStockByProductPartCode(productPartCode, getEnterpriseCode(), stockTableName);
		if (stockModel == null) {
			return false;
		}
		return stockModel.getTotalInventory().compareTo(BigDecimal.ZERO) > CommonConstant.NUMBER_ZERO;
	}

	/**
	 * @description 深度复制-复制工艺路线和BOM明细数据
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/3/11 20:49
	 */
	public void routeProcedureCopy(ProductPartModel model, Long oldCode) {
		// 产品零件与工艺路线关系表
		String productPartRouteRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProductPartRouteRelationship());
		// 工序表与工艺路线关系表
		String procedureRouteRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProcedureRouteRelationship());
		// 工序表与零件关系表
		String procedurePartRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProcedurePartRelationship());
		log.info("start 复制产品零件关系数据 oldCode {} model {}", oldCode, JSONUtil.toJsonStr(model));
		// 复制产品零件与工艺路线关系
		LambdaQueryWrapper<ProductPartRouteRelationshipModel> queryRoute = Wrappers.lambdaQuery();
		queryRoute.eq(ProductPartRouteRelationshipModel::getProductPartCode, oldCode);
		queryRoute.eq(ProductPartRouteRelationshipModel::getEnterpriseCode, getEnterpriseCode());
		queryRoute.eq(ProductPartRouteRelationshipModel::getDeleteFlag, Boolean.FALSE)
				.orderByDesc(ProductPartRouteRelationshipModel::getId);
		RequestTableHelper.setTableName(productPartRouteRelationshipTableName);
		List<ProductPartRouteRelationshipModel> routeRelationshipModelList = productPartRouteRelationshipService.list(queryRoute);
		if (ObjectUtil.isEmpty(routeRelationshipModelList)) {
			log.info("复制产品零件与工艺路线关系不存在,不进行复制 oldCode {}", oldCode);
			return;
		}
		for (ProductPartRouteRelationshipModel routeRelationshipModel : routeRelationshipModelList) {
			// 暂存旧关系唯一 ID
			Long oldUniqueId = routeRelationshipModel.getUniqueId();
			// 生成新关系唯一 ID
			Long uniqueId = PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId();
			routeRelationshipModel.setProductPartCode(model.getProductPartCode());
			routeRelationshipModel.setUniqueId(uniqueId).setId(null).setPrimaryKey(null);
			RequestTableHelper.setTableName(productPartRouteRelationshipTableName);
			productPartRouteRelationshipService.save(routeRelationshipModel);
			log.info("复制产品零件与工艺路线关系,完成 {}", JSONUtil.toJsonStr(routeRelationshipModel));
			// 工序表与工艺路线关系
			LambdaQueryWrapper<ProcedureRouteRelationshipModel> queryProcedureRoute = Wrappers.lambdaQuery();
			queryProcedureRoute.eq(ProcedureRouteRelationshipModel::getUniqueId, oldUniqueId);
			queryProcedureRoute.eq(ProcedureRouteRelationshipModel::getEnterpriseCode, getEnterpriseCode());
			queryProcedureRoute.eq(ProcedureRouteRelationshipModel::getDeleteFlag, Boolean.FALSE);
			RequestTableHelper.setTableName(procedureRouteRelationshipTableName);
			List<ProcedureRouteRelationshipModel> procedureRouteList =
					procedureRouteRelationshipService.list(queryProcedureRoute);
			for (ProcedureRouteRelationshipModel relationshipModel : procedureRouteList) {
				relationshipModel.setProcedureCode(model.getProcedureCode());
				relationshipModel.setUniqueId(uniqueId).setId(null).setPrimaryKey(null);
			}
			try (AutoCloseable ignored = RequestTableHelper::clearBatchTableName) {
				RequestTableHelper.setBatchTableName(procedureRouteRelationshipTableName);
				procedureRouteRelationshipService.saveBatch(procedureRouteList);
				log.info("复制工序表与工艺路线关系,完成 {}", JSONUtil.toJsonStr(procedureRouteList));
			} catch (Exception e) {
				log.error("批量操作失败: 表名={}, 数据大小={}", procedureRouteRelationshipTableName, procedureRouteList.size(), e);
			}
			// 工序表与零件关系
			LambdaQueryWrapper<ProcedurePartRelationshipModel> queryProcedurePart = Wrappers.lambdaQuery();
			queryProcedurePart.eq(ProcedurePartRelationshipModel::getUniqueId, oldUniqueId);
			queryProcedurePart.eq(ProcedurePartRelationshipModel::getEnterpriseCode, getEnterpriseCode());
			queryProcedurePart.eq(ProcedurePartRelationshipModel::getDeleteFlag, Boolean.FALSE);
			RequestTableHelper.setTableName(procedurePartRelationshipTableName);
			List<ProcedurePartRelationshipModel> procedurePartList =
					procedurePartRelationshipService.list(queryProcedurePart);
			for (ProcedurePartRelationshipModel relationshipModel : procedurePartList) {
				relationshipModel.setProcedureCode(relationshipModel.getProcedureCode());
				relationshipModel.setUniqueId(uniqueId).setId(null).setPrimaryKey(null);
			}
			try (AutoCloseable ignored = RequestTableHelper::clearBatchTableName) {
				RequestTableHelper.setBatchTableName(procedurePartRelationshipTableName);
				procedurePartRelationshipService.saveBatch(procedurePartList);
				log.info("复制工序表与零件关系,完成 {}", JSONUtil.toJsonStr(procedureRouteList));
			} catch (Exception e) {
				log.error("批量操作失败: 表名={}, 数据大小={}", procedureRouteRelationshipTableName, procedureRouteList.size(), e);
			}
		}
	}

	/**
	 * @description 名称复制
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/3/12 11:01
	 */
	public String generateCopyModel(String name, String tableName) {
		try {
			if (StrUtil.isBlank(name)) {
				return "";
			}
			String baseName = "复制";
			String newName = baseName;
			// 检查是否有重复的型号名称
			while (true) {
				// 如果name已经包含"复制"，则不需要再添加
				if (name.contains("复制")) {
					newName = baseName + "_" + name.substring("复制".length());
				} else {
					newName = baseName + "_" + name;
				}
				// 生成随机字符串
				String randomPart = RandomUtil.randomString(2);
				newName = newName.replaceFirst("复制_", "复制" + randomPart + "_");
				LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
				qw.eq(ProductPartModel::getModel, newName);
				RequestTableHelper.setTableName(tableName);
				List<ProductPartModel> existingModels = list(qw);
				if (existingModels.isEmpty()) {
					return newName;
				}
			}
		} catch (Exception e) {
			log.error("名称复制 生成失败 {} ", name, e);
		}
		return "";
	}

	/**
	 * @description 同步库存信息
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/3/12 09:28
	 */
	public void syncStock(Long productPartCode) {
		// 新技术部-库存表
		String stockTableName =
				tableFactory.getTableName(tableFactory.module.getWarehouse(), tableFactory.table.getStock());
		//新增同步库存数据
		StockModel stockModel = new StockModel();
		stockModel.setProductPartCode(productPartCode)
				.setUnqualifiedInventory(BigDecimal.ZERO)
				.setLockInInventor(BigDecimal.ZERO)
				.setTotalInventory(BigDecimal.ZERO)
				// 状态 true：启用
				.setState(Boolean.TRUE)
				// 库存初始化状态 默认 False
				.setStockInitState(Boolean.FALSE)
				.setEnterpriseCode(getEnterpriseCode());
		RequestTableHelper.setTableName(stockTableName);
		stockMapper.insert(stockModel);
	}

	/**
	 * @description 删除产品、零件关系数据
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/3/12 17:25
	 */

	public void deleteRelationship(Long productPartCode) {
		// 产品零件与工艺路线关系表
		String productPartRouteRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProductPartRouteRelationship());
		// 工序表与工艺路线关系表
		String procedureRouteRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProcedureRouteRelationship());
		// 工序表与零件关系表
		String procedurePartRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProcedurePartRelationship());
		log.info("start 产品零件关系 productPartCode {}", productPartCode);
		LambdaQueryWrapper<ProductPartRouteRelationshipModel> queryRoute = Wrappers.lambdaQuery();
		queryRoute.eq(ProductPartRouteRelationshipModel::getProductPartCode, productPartCode);
		queryRoute.eq(ProductPartRouteRelationshipModel::getEnterpriseCode, getEnterpriseCode());
		queryRoute.orderByDesc(ProductPartRouteRelationshipModel::getId);
		RequestTableHelper.setTableName(productPartRouteRelationshipTableName);
		ProductPartRouteRelationshipModel routeRelationship =
				productPartRouteRelationshipService.list(queryRoute).stream().findFirst().orElse(null);
		RequestTableHelper.setTableName(productPartRouteRelationshipTableName);
		productPartRouteRelationshipService.removeById(routeRelationship);
		log.info("info 删除 产品零件与工艺路线关系表 productPartCode {}", JSONUtil.toJsonStr(routeRelationship));
		if (routeRelationship == null) {
			log.warn("查询关系数据失败，没查询到数据 ，getProductPartCode {}", productPartCode);
			return;
		}
		// 关系唯一 ID
		Long uniqueId = routeRelationship.getUniqueId();
		LambdaQueryWrapper<ProcedureRouteRelationshipModel> queryProcedureRoute = Wrappers.lambdaQuery();
		queryProcedureRoute.eq(ProcedureRouteRelationshipModel::getUniqueId, uniqueId);
		RequestTableHelper.setTableName(procedureRouteRelationshipTableName);
		procedureRouteRelationshipService.remove(queryProcedureRoute);
		log.info("info 删除 工序表与工艺路线关系表 productPartCode {}", JSONUtil.toJsonStr(routeRelationship));
		LambdaQueryWrapper<ProcedurePartRelationshipModel> queryProcedurePart = Wrappers.lambdaQuery();
		queryProcedurePart.eq(ProcedurePartRelationshipModel::getUniqueId, uniqueId);
		RequestTableHelper.setTableName(procedurePartRelationshipTableName);
		procedurePartRelationshipService.remove(queryProcedurePart);
		log.info("info 删除 工序表与零件关系表 productPartCode {}", JSONUtil.toJsonStr(routeRelationship));
	}

	/**
	 * @description 解绑，产品与零件关系
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/24 17:03
	 * @department: Product development
	 */
	@Transactional(rollbackFor = Exception.class)
	public void safeUnbindConsistXh(ProductPartConsistRequest requestData) {
		// 产品零件-绑定组成表
		String productPartCompTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPartComp());
		// 精确解绑
		LambdaUpdateWrapper<ProductPartCompModel> uw = new LambdaUpdateWrapper<>();
		uw.set(ProductPartCompModel::getDeleteFlag, true)
				.eq(ProductPartCompModel::getParentCode, requestData.getParentCode())
				.eq(ProductPartCompModel::getDeleteFlag, false); // 幂等性保障
		RequestTableHelper.setTableName(productPartCompTableName);
		int rows = productPartCompMapper.update(uw);
		log.info("产品与零件关系,已正确解绑 {} requestData {}", rows, JSONUtil.toJsonStr(requestData));
	}

	/**
	 * @description 构建产品零件组成树结构
	 * @param allNodes      包含所有节点的列表，每个节点包含子节点和父节点的关系
	 * @param rootParentCode 根节点的父代码，用于确定树的起始点
	 * @return 返回构建好的树结构，根节点为指定的父代码对应的节点
	 *  通过构建全量节点注册表和父级索引，递归地构建产品零件的组成树结构。
	 * 根节点由 rootParentCode 确定，递归地处理每个节点的子节点，直到所有节点都被处理完毕。
	 * 如果检测到循环路径，会终止展开并记录警告日志。
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/15 15:52
	 * @department: Product development
	 */
	public List<ProductPartConsistTreeDTO> buildConsistTree(List<ProductPartConsistTreeDTO> allNodes, Long rootParentCode) {
		// 构建全量节点注册表（childCode → 节点模板）
		Map<Long, ProductPartConsistTreeDTO> globalNodeRegistry = allNodes.stream()
				.collect(Collectors.toMap(
						ProductPartConsistTreeDTO::getChildCode,
						Function.identity(),
						(existing, replacement) -> existing // 保留首次出现的节点定义
				));

		// 构建父级索引（parentCode → 子节点模板集合）
		Map<Long, List<ProductPartConsistTreeDTO>> parentMap = allNodes.stream()
				.collect(Collectors.groupingBy(ProductPartConsistTreeDTO::getParentCode));

		// 处理根节点 - 只处理直接子节点（treeDepth = 1）
		return Optional.ofNullable(parentMap.get(rootParentCode))
				.orElse(Collections.emptyList())
				.stream()
				.filter(node -> node.getTreeDepth() != null && node.getTreeDepth() == 1) // 只处理第一层节点
				.map(root -> buildTreeBranchOptimized(root, parentMap, globalNodeRegistry, new HashSet<>()))
				.collect(Collectors.toList());
	}

	/**
	 * @description 递归构建树的分支（优化版本）
	 * @param current      当前处理的节点
	 * @param parentMap    父级索引，映射父节点代码到子节点列表
	 * @param globalRegistry 全量节点注册表，映射子节点代码到节点模板
	 * @param branchPath   当前路径跟踪器，用于检测循环路径
	 * 基于数据库返回的层级信息构建树，避免重复计算层级
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/24 17:03
	 * @department: Product development
	 */
	private ProductPartConsistTreeDTO buildTreeBranchOptimized(ProductPartConsistTreeDTO current,
	                                                           Map<Long, List<ProductPartConsistTreeDTO>> parentMap,
	                                                           Map<Long, ProductPartConsistTreeDTO> globalRegistry,
	                                                           Set<Long> branchPath) {
		// 深拷贝节点模板
		ProductPartConsistTreeDTO node = new ProductPartConsistTreeDTO();
		BeanUtil.copyProperties(current, node);
		node.setUniqueId(UUID.fastUUID().toString().replace("-", ""));

		// 循环检测
		if (branchPath.contains(node.getChildCode())) {
			log.warn("检测到循环路径，终止展开：{}", node.getChildCode());
			return node;
		}

		// 准备新路径跟踪器
		Set<Long> newBranchPath = new HashSet<>(branchPath);
		newBranchPath.add(node.getChildCode());

		// 获取当前节点的所有子节点，基于数据库返回的层级信息
		List<ProductPartConsistTreeDTO> children = Optional.ofNullable(parentMap.get(node.getChildCode()))
				.orElse(Collections.emptyList())
				.stream()
				.filter(childTemplate -> {
					// 有效性检查
					if (childTemplate == null || childTemplate.getChildCode() == null) return false;
					// 子节点必须存在于全局注册表
					if (!globalRegistry.containsKey(childTemplate.getChildCode())) return false;
					// 确保子节点的层级是当前节点层级+1
					return childTemplate.getTreeDepth() != null &&
							childTemplate.getTreeDepth() == (node.getTreeDepth() != null ? node.getTreeDepth() + 1 : 1);
				})
				.collect(Collectors.collectingAndThen(
						Collectors.toMap(
								ProductPartConsistTreeDTO::getChildCode,
								childTemplate -> buildTreeBranchOptimized(
										globalRegistry.get(childTemplate.getChildCode()),
										parentMap,
										globalRegistry,
										newBranchPath
								),
								(existing, replacement) -> existing // 同父节点下去重
						),
						map -> new ArrayList<>(map.values())
				));

		node.setChildren(children);
		return node;
	}

	/**
	 * @description 递归构建树的分支（原版本，保留作为备用）
	 * @param current      当前处理的节点
	 * @param parentMap    父级索引，映射父节点代码到子节点列表
	 * @param globalRegistry 全量节点注册表，映射子节点代码到节点模板
	 * @param branchPath   当前路径跟踪器，用于检测循环路径
	 * 首先深拷贝当前节点，然后检查是否存在循环路径。
	 * 如果存在循环路径，记录警告日志并终止展开。否则，继续处理子节点，递归地构建子树。
	 * 子节点通过 parentMap 获取，并通过 globalRegistry 确认其有效性。
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/24 17:03
	 * @department: Product development
	 */
	private ProductPartConsistTreeDTO buildTreeBranch(ProductPartConsistTreeDTO current,
	                                                  Map<Long, List<ProductPartConsistTreeDTO>> parentMap,
	                                                  Map<Long, ProductPartConsistTreeDTO> globalRegistry,
	                                                  Set<Long> branchPath) {
		// 深拷贝节点模板
		ProductPartConsistTreeDTO node = new ProductPartConsistTreeDTO();
		BeanUtil.copyProperties(current, node);
		node.setUniqueId(UUID.fastUUID().toString().replace("-", ""));
		// 循环检测
		if (branchPath.contains(node.getChildCode())) {
			log.warn("检测到循环路径，终止展开：{}", node.getChildCode());
			return node;
		}
		// 准备新路径跟踪器
		Set<Long> newBranchPath = new HashSet<>(branchPath);
		newBranchPath.add(node.getChildCode());
		// 处理子节点（带同父节点下去重）
		List<ProductPartConsistTreeDTO> children = Optional.ofNullable(parentMap.get(node.getChildCode()))
				.orElse(Collections.emptyList())
				.stream()
				.filter(childTemplate -> {
					// 有效性检查
					if (childTemplate == null || childTemplate.getChildCode() == null) return false;
					// 子节点必须存在于全局注册表
					return globalRegistry.containsKey(childTemplate.getChildCode());
				})
				.collect(Collectors.collectingAndThen(
						Collectors.toMap(
								ProductPartConsistTreeDTO::getChildCode,
								childTemplate -> buildTreeBranch(
										globalRegistry.get(childTemplate.getChildCode()),
										parentMap,
										globalRegistry,
										newBranchPath
								),
								(existing, replacement) -> existing // 同父节点下去重
						),
						map -> new ArrayList<>(map.values())
				));
		node.setChildren(children);
		return node;
	}

	/**
	 * @description 解析工艺路线里面 路线绑定关系
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/16 21:46
	 * @department: Product development
	 */
	public List<Map<String, Object>> routeNodeModels(String routeNode) {
		if (StrUtil.isBlank(routeNode)) {
			return List.of();
		}
		// 产品零件工序表
		String productPartProcedureTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProductPartProcedure());
		List<ProcessRouteNodeModel> routeNodeModels = JSONUtil.toList(routeNode,
				ProcessRouteNodeModel.class);
		List<Map<String, Object>> routeNodeResult = new ArrayList<>();
		for (ProcessRouteNodeModel routeNodeModel : routeNodeModels) {
			if (StrUtil.isBlank(routeNodeModel.getProductPartProcedureCode())) {
				log.warn("查询工序信息，工序主键编号 空 不进后续行操作 {}", JSONUtil.toJsonStr(routeNodeModel));
				continue;
			}
			if (!NumberUtil.isNumber(routeNodeModel.getProductPartProcedureCode())) {
				log.warn("查询工序信息，工序主键编号 不是数字类型 不进后续行操作 {}", JSONUtil.toJsonStr(routeNodeModel));
				continue;
			}
			RequestTableHelper.setTableName(productPartProcedureTableName);
			ProductPartProcedureModel procedureModel =
					productPartProcedureService.getById(Long.parseLong(routeNodeModel.getProductPartProcedureCode()));
			if (procedureModel != null) {
				Map<String, Object> processMap = new HashMap<>();
				processMap.put("productPartProcedureCode", procedureModel.getProductPartProcedureCode());
				processMap.put("state", procedureModel.getState());
				routeNodeResult.add(processMap);
			}
		}
		return routeNodeResult;
	}

	@Override
	public List<Long> getProductPartSearchObj(ProductPartSearchObjRequest requestListData) {
		String searchName = requestListData.getSearchName();
		if (StrUtil.isBlank(searchName)) {
			log.warn("查询产品名称和型号入参空，不做任何处理，不进后续行操作 {}", searchName);
			return List.of();
		}
		// 新技术部-产品零件表
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
		LambdaQueryWrapper<ProductPartModel> query = Wrappers.lambdaQuery();
		query.and(wp -> wp.like(ProductPartModel::getProductPartSign, searchName)
				.or().like(ProductPartModel::getModel, searchName));
		query.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode()).orderByDesc(ProductPartModel::getId);
		RequestTableHelper.setTableName(productPartTableName);
		List<ProductPartModel> resultModelList = this.list(query);
		return resultModelList.stream().map(ProductPartModel::getProductPartCode).toList();
	}

	/**
	 * @description 获取统一编号（根据已有编号生成下一个编号）
	 * @param  attribute {@link org.aerie.forest.core.brick.domain.enums.PPAttributeEnum}
	 * @param attribute           产品或零件属性
	 * @param productPartTypeCode 分类代码
	 * @param unityNo             指定编号
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/29 15:34
	 * @department: Product development
	 */
	public String getGlobalSerialUnityNo(Integer attribute, Long productPartTypeCode, String unityNo) throws AssertException {
		return getGlobalUnityNo(attribute, productPartTypeCode, unityNo, null, Boolean.FALSE, Boolean.FALSE);
	}

	/**
	 * @description 获取统一编号（例如获取下一个编号，带预览模式）
	 * @param  attribute {@link org.aerie.forest.core.brick.domain.enums.PPAttributeEnum}
	 * @param attribute           产品或零件属性
	 * @param productPartTypeCode 分类代码
	 * @param previewFlag         是否启用预览模式
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/29 15:34
	 * @department: Product development
	 */
	public String getGlobalSerialUnityNo(Integer attribute, Long productPartTypeCode, Boolean previewFlag) throws AssertException {
		return getGlobalUnityNo(attribute, productPartTypeCode, null, null, Boolean.FALSE, previewFlag);
	}

	/**
	 * @param attribute               {@link org.aerie.forest.core.brick.domain.enums.PPAttributeEnum}
	 * @param attribute               产品或零件属性
	 * @param allProductPartTypeCodes 分类代码
	 * @description 获取规则相同的编号
	 * @author chengxinyu
	 * @organization futurecraftsmen
	 * @date Created in 2025/8/19 15:34
	 * @department: Product development
	 */
	public CheckBeforeGlobalSerialUnityNoBatchResult checkBeforeGlobalSerialUnityNoBatch(Integer attribute, Set<Long> allConcernedTypeCodes, ProductPartTreeNodeRpcDTO productPartTypeTree) throws AssertException {
		CheckBeforeGlobalSerialUnityNoBatchResult result = new CheckBeforeGlobalSerialUnityNoBatchResult(allConcernedTypeCodes, productPartTypeTree);
		try {

			if (attribute == null || CollUtil.isEmpty(allConcernedTypeCodes) || productPartTypeTree == null) {
				return result;
			}

			long tableCode = enCodePropertiesConfig.getTableCodeByAttribute(attribute);

			//路径上所有相关的分类编号
			List<Long> relatedTypeCodes = result.relatedTypeCodes();

			//1.查询有规则的分类编号
			Set<Long> withRuleQueryKeyValues = globalNumberServer.getWithRuleQueryKeyValues(tableCode, relatedTypeCodes);

			//子级到父级的映射关系
			Map<Long, Long> childToParentMap = result.getChildToParentMap();

			//遍历待处理的分类编号
			for (Long concernedTypeCode : allConcernedTypeCodes) {
				if (withRuleQueryKeyValues.contains(concernedTypeCode)) { //如果有规则，直接加入
					result.recordQueryKeyAndTypeCode(concernedTypeCode, concernedTypeCode);
					continue;
				}
				//自己没有规则，但父级或父级的父级有规则,向上直到找到有规则的父级
				Long parentCode = childToParentMap.get(concernedTypeCode);
				Long sameQueryKey = 0L; // 默认使用顶级父级作为sameQueryKey
				
				// 向上查找有规则的父级
				while (parentCode != null) {
					if (withRuleQueryKeyValues.contains(parentCode)) {
						sameQueryKey = parentCode;
						break;
					}
					parentCode = childToParentMap.get(parentCode);
				}

				result.recordQueryKeyAndTypeCode(sameQueryKey, concernedTypeCode);
			}
			return result;
		} catch (Exception e) {
			return result;
		}
	}




	/**
	 * @description 批量获取统一编号（预览模式，支持指定数量）
	 * @param  attribute {@link org.aerie.forest.core.brick.domain.enums.PPAttributeEnum}
	 * @param attribute           产品或零件属性
	 * @param productPartTypeCode 分类代码
	 * @param count               需要预览的编号数量
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/8/19 15:34
	 * @department: Product development
	 */
	public List<String> getGlobalSerialUnityNoBatch(Integer attribute, Long productPartTypeCode, Integer count) throws AssertException {
		try {
			if (attribute == null || productPartTypeCode == null || count == null || count <= 0) {
				log.warn("批量预览编号参数错误，attribute={}, productPartTypeCode={}, count={}", attribute, productPartTypeCode, count);
				return Collections.emptyList();
			}
			// 构建请求对象
			GlobalSerialNumberRequest requestData = new GlobalSerialNumberRequest();
			long tableCode = enCodePropertiesConfig.getTableCodeByAttribute(attribute);
			requestData.setTableCode(tableCode);
			requestData.setQueryKey(String.valueOf(productPartTypeCode));
			// 设置预览标志为true，确保不会更新数据库序号
			requestData.getContext().put("previewFlag", Boolean.TRUE);
			// 调用GlobalNumberServer的批量生成方法
			return globalNumberServer.serialNumberGenerateWay(requestData, count);
		} catch (Exception e) {
			String defNo = StrUtil.format("batch-ee-{}", RandomUtil.randomStringLowerWithoutStr(6, ""));
			log.error("批量预览统一编号出错，返回默认兜底编号 {}", defNo, e);
			// 返回指定数量的兜底编号
			List<String> result = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				result.add(defNo + "-" + (i + 1));
			}
			return result;
		}
	}

	/**
	 * @description 获取统一编号（根据当前编号获取下一个编号）
	 * @param  attribute {@link org.aerie.forest.core.brick.domain.enums.PPAttributeEnum}
	 * @param attribute           产品或零件属性
	 * @param productPartTypeCode 分类代码
	 * @param unityNo             指定编号
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/6/25 15:15
	 * @department: Product development
	 */
	public String getGlobalSerialUnityNo(Integer attribute, Long productPartTypeCode, String unityNo, Boolean batchAdd) throws AssertException {
		return getGlobalUnityNo(attribute, productPartTypeCode, unityNo, null, batchAdd, Boolean.FALSE);
	}

	/**
	 * @description 获取统一编号（支持预览模式）
	 * @param attribute           产品或零件属性
	 * @param productPartTypeCode 分类代码
	 * @param unityNo             指定编号（可为空）
	 * @param maxUnityNo          最大编号（用于更新序列号生成器）
	 * @param batchAdd            是否为批量添加
	 * @param previewFlag         是否启用预览模式
	 * @return 返回生成的统一编号
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/6/25 14:05
	 * @department: Product development
	 */
	public String getGlobalUnityNo(Integer attribute, Long productPartTypeCode, String unityNo, Long maxUnityNo, Boolean batchAdd,
	                               Boolean previewFlag) {
		try {
			if (attribute == null && productPartTypeCode == null) {
				log.warn("产品和零件单号生成空，不做任何处理，不进后续行操作");
				return unityNo;
			}
			// 获取产品零件表名
			String productPartTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
					tableFactory.table.getProductPart());
			// 如果指定了编号，则检查是否已存在
			if (StrUtil.isNotBlank(unityNo)) {
				LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
				qw.eq(ProductPartModel::getAttribute, attribute)
						.eq(ProductPartModel::getUnityNo, unityNo);
				qw.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode());
				RequestTableHelper.setTableName(productPartTableName);
				// 如果编号已存在，更新最大值
				if (this.count(qw) > 0) {
					if (maxUnityNo == null) {
						maxUnityNo = ExtractMaxNumberUtil.extractMaxNumber(List.of(unityNo));
					}
					updateGlobalSerialUnityNo(maxUnityNo, attribute, productPartTypeCode);
				}
				// 如果是批量添加，直接返回指定编号
				if (batchAdd) {
					return unityNo;
				}
			}
			// 构建请求对象
			GlobalSerialNumberRequest requestData = new GlobalSerialNumberRequest();
			long tableCode = enCodePropertiesConfig.getTableCodeByAttribute(attribute);
			requestData.setTableCode(tableCode);
			requestData.setQueryKey(String.valueOf(productPartTypeCode));
			// 设置预览标志
			requestData.getContext().put("previewFlag", previewFlag);
			// 获取表代码-统一单号生成逻辑提取
			return generateUniqueUnityNo(productPartTableName, attribute, requestData);
		} catch (Exception e) {
			String defNo = StrUtil.format("ee-{}", RandomUtil.randomStringLowerWithoutStr(6, ""));
			log.error("查询统一编号数据出错，返回默认兜底编号 {}", defNo, e);
			return defNo;
		}
	}

	/**
	 * @description 生成唯一编号
	 * 如果出现重复编号 会继续获取下一次编号，直到获取数据库不存在为准。
	 * 目前限制，循环 20次
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/5/13 16:21
	 * @department: Product development
	 */
	private String generateUniqueUnityNo(String tableName, Integer attribute, GlobalSerialNumberRequest requestData) {
		int retryCount = 0;
		final int MAX_RETRY = 20;
		while (retryCount < MAX_RETRY) {
			String unityNoRes = globalNumberServer.serialNumberGenerateWay(requestData);
			// 如果生成失败，直接返回兜底编号
			if (StrUtil.isBlank(unityNoRes)) {
				String defNo = StrUtil.format("def-{}", RandomUtil.randomStringLowerWithoutStr(6, ""));
				log.warn("生成统一编号为空，使用兜底方案 {}", defNo);
				return defNo;
			}
			// 检查编号是否存在
			if (!isUnityNoExists(tableName, attribute, unityNoRes)) {
				return unityNoRes;
			}

			/**
			 * 失败强制走更新
			 */
			if (MapUtils.getBoolean(requestData.getContext(), "previewFlag", true)) {
				requestData.getContext().put("previewFlag", false);
			} else {
				requestData.getContext().put("previewFlag", true);
			}

			retryCount++;
			log.info("生成统一编号冲突，继续调用 retryCount {} MAX_RETRY {} 新编号 {}", retryCount, MAX_RETRY, unityNoRes);
		}
		// 达到最大重试次数，返回兜底编号
		String defNo = StrUtil.format("rle-{}", RandomUtil.randomStringLowerWithoutStr(6, ""));
		log.warn("统一编号重复超过最大重试次数({})，使用兜底编号 {}", MAX_RETRY, defNo);
		return defNo;
	}

	/**
	 * @description 检查编号是否已存在
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/5/13 16:22
	 * @department: Product development
	 */
	private boolean isUnityNoExists(String tableName, Integer attribute, String unityNo) {
		LambdaQueryWrapper<ProductPartModel> qw = Wrappers.lambdaQuery();
		qw.eq(ProductPartModel::getAttribute, attribute)
				.eq(ProductPartModel::getUnityNo, unityNo)
				.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode());
		RequestTableHelper.setTableName(tableName);
		return this.count(qw) > 0;
	}


	/**
	 * @description 更新编号
	 *
	 * @author zhanglijia
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/29 10:03
	 * @department: Product development
	 */
	public void updateGlobalSerialUnityNo(Long num, Integer attribute, Long productPartTypeCode) {
		// 统一单号的生成
		GlobalSerialNumberRequest requestData = new GlobalSerialNumberRequest();
		// 获取表代码-统一单号生成逻辑提取
		long tableCode = 0L;
		try {
			tableCode = enCodePropertiesConfig.getTableCodeByAttribute(attribute);
		} catch (Exception e) {
			log.error("编码设置-获取属性获取表代码错误 不做任何处理 ", e);
		}
		requestData.setTableCode(tableCode);
		requestData.setQueryKey(String.valueOf(productPartTypeCode));
		Map<String, Object> param = new HashMap<>();
		param.put("RandomParseNumberUpdate", num);
		requestData.setContext(param);
		globalNumberServer.serialNumberGenerateWay(requestData);
	}

	@Override
	public List<ProductPartConsistTreeDTO> productPartConsistTreeForm(ProductPartCompRequest requestData) throws ExceptionPack {
		// 产品零件-绑定组成表
		String productPartCompTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPartComp());
		// 新技术部-产品零件表
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
		List<ProductPartConsistModel> productPartConsistModels = productPartCompMapper.getProductConsistList(productPartCompTableName,
				productPartTableName, getEnterpriseCode(), requestData);
		// 转换为DTO并构建树
		List<ProductPartConsistTreeDTO> allNodes = Convert.toList(ProductPartConsistTreeDTO.class, productPartConsistModels);
		// 计量单位数据准备
		Set<Long> productPiecesCodes =
				new HashSet<>(ExtractUtil.streamMapToList(Long::valueOf, allNodes, ProductPartConsistTreeDTO::getChildPcs));
		// 获取产品与名称映射
		Map<Long, String> pieceCodeNameMapping =
				piecesService.pieceCodeChnNameMapping(productPiecesCodes, getEnterpriseCode());
		for (ProductPartConsistTreeDTO allNode : allNodes) {
			// 组装 计量单位名称
			if (allNode.getChildPcs() != null) {
				allNode.setChildPcsName(pieceCodeNameMapping.get(allNode.getChildPcs()));
			}
		}

		// 使用优化后的树构建方法
		return buildConsistTree(allNodes, requestData.getParentCode());
	}

	@Override
	public List<ProductPartConsistDTO> getProductConsistList(ProductPartCompRequest requestData) throws ExceptionPack {
		// 产品零件-绑定组成表
		String productPartCompTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPartComp());
		// 新技术部-产品零件表
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
		List<ProductPartConsistModel> productPartConsistModels = productPartCompMapper.getProductConsistList(productPartCompTableName,
				productPartTableName, getEnterpriseCode(), requestData);
		List<ProductPartConsistDTO> resultList = Convert.toList(ProductPartConsistDTO.class, productPartConsistModels);
		// 计量单位数据准备
		Set<Long> productPiecesCodes =
				new HashSet<>(ExtractUtil.streamMapToList(Long::valueOf, resultList, ProductPartConsistDTO::getChildPcs,
						ProductPartConsistDTO::getParentPcs));
		// 获取产品与名称映射
		Map<Long, String> pieceCodeNameMapping =
				piecesService.pieceCodeChnNameMapping(productPiecesCodes, getEnterpriseCode());
		for (ProductPartConsistDTO consistDTO : resultList) {
			// 组装 计量单位名称
			if (consistDTO.getChildPcs() != null) {
				consistDTO.setChildPcsName(pieceCodeNameMapping.get(consistDTO.getChildPcs()));
			}
			// 组装 计量单位名称
			if (consistDTO.getParentPcs() != null) {
				consistDTO.setParentPcsName(pieceCodeNameMapping.get(consistDTO.getParentPcs()));
			}
		}
		return resultList;
	}

	@Override
	public List<ProductPartConsistDTO> getPartConsistList(ProductPartCompRequest requestData) {
		// 产品零件-绑定组成表
		String productPartCompTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPartComp());
		// 新技术部-产品零件表
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
		List<ProductPartConsistModel> productPartConsistModels = productPartCompMapper.getPartConsistList(productPartCompTableName,
				productPartTableName, getEnterpriseCode(), requestData);
		return Convert.toList(ProductPartConsistDTO.class, productPartConsistModels);
	}

	@Override
	public String queryBodyDescByCode(Long productPartCode, Long enterpriseCode) throws ExceptionPack {
		try {
			InfoPenetrateProcessor.INSTANCE.setPenetrateInfo(GlobalTokenPenetrate.ENTERPRISE_CODE_THREAD_LOCAL, enterpriseCode);

			// 新技术部-产品零件表
			String productPartTableName =
					tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart(), enterpriseCode);
			ProductPartModel productPartModel = productPartMapper.queryBodyDescByCode(productPartCode, enterpriseCode, productPartTableName);
			if (productPartModel == null) {
				return "";
			}
			return StrUtil.format("{}-{}-{}", productPartModel.getUnityNo(), productPartModel.getProductPartSign(),
					StrUtil.isBlank(productPartModel.getModel()) ? "" : productPartModel.getModel());
		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("fail to queryBodyDescByCode").build());
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void productPartConsist(ProductPartConsistRequest requestData) {
		// 产品零件-绑定组成表
		String productPartCompTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPartComp());
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());

		// --- Log Start ---
		// 1. 获取旧的物料关系
		LambdaQueryWrapper<ProductPartCompModel> oldConsistQw = new LambdaQueryWrapper<>();
		oldConsistQw.eq(ProductPartCompModel::getParentCode, requestData.getParentCode());
		oldConsistQw.eq(ProductPartCompModel::getDeleteFlag, false);
		RequestTableHelper.setTableName(productPartCompTableName);
		List<ProductPartCompModel> oldConsistList = productPartCompService.list(oldConsistQw);
		List<Long> oldMaterialCodes = oldConsistList.stream()
				.map(ProductPartCompModel::getChildCode)
				.filter(Objects::nonNull)
				.toList();
		// --- Log End ---

		// 解绑关系
		safeUnbindConsistXh(requestData);
		// 过滤掉空对象
		List<ProductPartChild> filteredChildList = requestData.getProductPartChildList().stream()
				.filter(child -> child != null && (child.getChildCode() != null || child.getChildAttribute() != null))
				.collect(Collectors.toList());
		if (ObjectUtil.isNotEmpty(filteredChildList)) {
			List<ProductPartCompModel> procedurePartList = requestData.getProductPartChildList().stream().map(child -> {
				// 深度计算（需查询父节点当前最大深度）
				Integer parentDepth = productPartCompMapper.selectMaxDepth(productPartCompTableName, requestData.getParentCode(), getEnterpriseCode());
				return new ProductPartCompModel()
						.setParentCode(requestData.getParentCode())
						.setParentAttribute(requestData.getParentAttribute())
						.setEnterpriseCode(getEnterpriseCode())
						.setChildCode(child.getChildCode())
						.setChildAttribute(child.getChildAttribute())
						.setChildNumber(child.getChildNumber())
						.setDepth(parentDepth != null ? parentDepth + 1 : 1);
			}).toList();
			try (AutoCloseable ignored = RequestTableHelper::clearBatchTableName) {
				RequestTableHelper.setBatchTableName(productPartCompTableName);
				productPartCompService.saveBatch(procedurePartList);
				log.info("产品与零件绑定关系,完成 {}", JSONUtil.toJsonStr(procedurePartList));
			} catch (Exception e) {
				log.error("批量操作失败: 表名={}, 数据大小={}", productPartCompTableName, procedurePartList.size(), e);
			}
		}

		// --- Log Start ---
		List<Long> newMaterialCodes = requestData.getProductPartChildList().stream()
				.map(ProductPartChild::getChildCode)
				.filter(Objects::nonNull)
				.toList();

		// 删除日志
		List<Long> deletedCodes = oldMaterialCodes.stream()
				.filter(code -> !newMaterialCodes.contains(code))
				.toList();
		if (CollUtil.isNotEmpty(deletedCodes)) {
			LambdaQueryWrapper<ProductPartModel> nameQw = Wrappers.lambdaQuery();
			nameQw.in(ProductPartModel::getProductPartCode, deletedCodes);
			RequestTableHelper.setTableName(productPartTableName);
			String deletedNames = productPartMapper.selectList(nameQw).stream()
					.map(ProductPartModel::getProductPartSign)
					.filter(Objects::nonNull)
					.collect(Collectors.joining("、"));
			if (StrUtil.isNotBlank(deletedNames)) {
				((IProductPartCommonServiceImpl) AopContext.currentProxy()).logDeleteConsist(deletedNames, requestData.getParentCode(),
						unifiedDataService.resolveLogType(requestData.getParentAttribute()));
			}
		}

		// 新增日志
		List<Long> addedCodes = newMaterialCodes.stream()
				.filter(code -> !oldMaterialCodes.contains(code))
				.toList();
		if (CollUtil.isNotEmpty(addedCodes)) {
			LambdaQueryWrapper<ProductPartModel> nameQw = Wrappers.lambdaQuery();
			nameQw.in(ProductPartModel::getProductPartCode, addedCodes);
			RequestTableHelper.setTableName(productPartTableName);
			String addedNames = productPartMapper.selectList(nameQw).stream()
					.map(ProductPartModel::getProductPartSign)
					.filter(Objects::nonNull)
					.collect(Collectors.joining("、"));
			if (StrUtil.isNotBlank(addedNames)) {
				((IProductPartCommonServiceImpl) AopContext.currentProxy()).logAddConsist(addedNames, requestData.getParentCode(),
						unifiedDataService.resolveLogType(requestData.getParentAttribute()));
			}
		}
		// --- Log End ---
	}

	/**
	 * 记录删除配套物料日志
	 */
	@OptRecord(
			desc = {"配套物料删除了: #{#p0}"},
			primaryKey = "#{#p1}",
			dynamicType = "#{#p2}"
	)
	public void logDeleteConsist(String materialNames, Long parentCode, StaffOperationLogTypeEnum type) {
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
	public void logAddConsist(String materialNames, Long parentCode, StaffOperationLogTypeEnum type) {
		// AOP切面自动记录日志
	}

	/**
	 * @description 根据 页面 code 获取对应编码 code
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/30 15:12
	 * @department: Product development
	 */
	public GlobalSerialNumberResponse getGlobalSerialNumber(Integer attribute) {
		GlobalSerialNumberResponse res = new GlobalSerialNumberResponse();
		try {
			GlobalSerialNumberRequest gr = new GlobalSerialNumberRequest();
			gr.setTableCode(enCodePropertiesConfig.getTableCodeByAttribute(attribute));
			GlobalSerialNumberResponse response = globalNumberServer.serialNumberQuery(gr);
			if (response != null) {
				res = response;
			}
		} catch (Exception e) {
			log.error("编码设置-发生未知错误 不做任何处理 ", e);
		}
		return res;
	}

	/**
	 * @description 反查询-聚合搜索-名称+型号+编号
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/4/30 15:48
	 * @department: Product development
	 */
	public List<Long> getReverseLookup(String reverseLookup) {
		if (StrUtil.isBlank(reverseLookup)) {
			return Collections.emptyList();
		}
		// 新技术部-产品零件表
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
		// 产品零件-绑定组成表
		String productPartCompTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPartComp());

		LambdaQueryWrapper<ProductPartModel> query = Wrappers.lambdaQuery();
		query.and(wp -> wp.like(ProductPartModel::getProductPartSign, reverseLookup)
				.or().like(ProductPartModel::getModel, reverseLookup)
				.or().like(ProductPartModel::getUnityNo, reverseLookup));
		query.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode()).orderByDesc(ProductPartModel::getId);
		RequestTableHelper.setTableName(productPartTableName);
		List<ProductPartModel> resultModelList = this.list(query);
		List<Long> resultIdList = resultModelList.stream().map(ProductPartModel::getProductPartCode).toList();
		if (ObjectUtil.isEmpty(resultIdList)) {
			return Collections.emptyList();
		}
		ProductPartCompRequest data = new ProductPartCompRequest();
		data.setChildCodeList(resultIdList);
		List<ProductPartConsistModel> productPartConsistModels = productPartCompMapper.getPartConsistList(productPartCompTableName,
				productPartTableName, getEnterpriseCode(), data);
		List<Long> resultCodeList = productPartConsistModels.stream().map(ProductPartConsistModel::getParentCode).toList();
		if (ObjectUtil.isEmpty(resultCodeList)) {
			return Collections.emptyList();
		}
		return resultCodeList;
	}

	/**
	 * @description 反查询-查询这个工艺路线在哪些产品、部件使用
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/6/10 22:00
	 * @department: Product development
	 */
	public List<Long> getReverseProcessRoute(String reverseProcessRoute) {
		if (StrUtil.isBlank(reverseProcessRoute)) {
			return Collections.emptyList();
		}
		// 工艺路线管理表
		String processRouteDataTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProcessRouteData());
		// 新技术部-产品零件表
		// 产品零件与工艺路线关系表
		String productPartRouteRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProductPartRouteRelationship());
		LambdaQueryWrapper<ProcessRouteDataModel> route = Wrappers.lambdaQuery();
		route.like(ProcessRouteDataModel::getProcessRouteDataSign, reverseProcessRoute);
		route.eq(ProcessRouteDataModel::getEnterpriseCode, getEnterpriseCode());
		RequestTableHelper.setTableName(processRouteDataTableName);
		List<Long> resultCodeList =
				processRouteDataService.list(route)
						.stream().distinct().map(ProcessRouteDataModel::getProcessRouteDataCode).toList();
		if (ObjectUtil.isEmpty(resultCodeList)) {
			return Collections.emptyList();
		}
		LambdaQueryWrapper<ProductPartRouteRelationshipModel> queryRoute = Wrappers.lambdaQuery();
		queryRoute.in(ProductPartRouteRelationshipModel::getProcessRouteDataCode, resultCodeList);
		queryRoute.eq(ProductPartRouteRelationshipModel::getEnterpriseCode, getEnterpriseCode());
		queryRoute.eq(ProductPartRouteRelationshipModel::getDeleteFlag, Boolean.FALSE);
		RequestTableHelper.setTableName(productPartRouteRelationshipTableName);
		return productPartRouteRelationshipService.list(queryRoute)
				.stream().distinct()
				.map(ProductPartRouteRelationshipModel::getProductPartCode).toList();
	}

	/**
	 * @description 反查询-根据产品、部件、零件、原料 查询所在BOM列表
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/6/10 22:00
	 * @department: Product development
	 */
	public List<Long> getReverseName(String reverseName) {
		if (StrUtil.isBlank(reverseName)) {
			return Collections.emptyList();
		}
		// 新技术部-产品零件表
		String productPartTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPart());
		// 产品零件与工艺路线关系表
		String productPartRouteRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProductPartRouteRelationship());
		// 工序表与零件关系表
		String procedurePartRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProcedurePartRelationship());
		LambdaQueryWrapper<ProductPartModel> queryProductPart = Wrappers.lambdaQuery();
		queryProductPart.like(ProductPartModel::getProductPartSign, reverseName);
		queryProductPart.eq(ProductPartModel::getEnterpriseCode, getEnterpriseCode());
		RequestTableHelper.setTableName(productPartTableName);
		List<Long> productCodeList = this.list(queryProductPart).stream().distinct()
				.map(ProductPartModel::getProductPartCode).toList();
		if (ObjectUtil.isEmpty(productCodeList)) {
			return Collections.emptyList();
		}
		LambdaQueryWrapper<ProcedurePartRelationshipModel> queryProcedurePart = Wrappers.lambdaQuery();
		queryProcedurePart.in(ProcedurePartRelationshipModel::getProductPartCode, productCodeList);
		queryProcedurePart.eq(ProcedurePartRelationshipModel::getEnterpriseCode, getEnterpriseCode());
		queryProcedurePart.eq(ProcedurePartRelationshipModel::getDeleteFlag, Boolean.FALSE);
		RequestTableHelper.setTableName(procedurePartRelationshipTableName);
		List<ProcedurePartRelationshipModel> resultProductPartRouteList =
				procedurePartRelationshipService.list(queryProcedurePart);
		if (ObjectUtil.isEmpty(resultProductPartRouteList)) {
			return Collections.emptyList();
		}
		List<Long> getUniqueIdList =
				resultProductPartRouteList.stream().distinct()
						.map(ProcedurePartRelationshipModel::getUniqueId).toList();
		LambdaQueryWrapper<ProductPartRouteRelationshipModel> queryRoute = Wrappers.lambdaQuery();
		queryRoute.in(ProductPartRouteRelationshipModel::getUniqueId, getUniqueIdList);
		queryRoute.eq(ProductPartRouteRelationshipModel::getEnterpriseCode, getEnterpriseCode());
		queryRoute.eq(ProductPartRouteRelationshipModel::getDeleteFlag, Boolean.FALSE);
		RequestTableHelper.setTableName(productPartRouteRelationshipTableName);
		return productPartRouteRelationshipService.list(queryRoute).stream().distinct().map(ProductPartRouteRelationshipModel::getProductPartCode).toList();
	}

	/**
	 * @description 判断改工艺路线，BOM下面是否有被删除的工序
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/6/11 16:43
	 * @department: Product development
	 */
	public Boolean getIsDeleteProcess(ProductPartProcedureRpcRequest requestProcedureData, ProductPartRpcDTO rpcDTO) throws ExceptionPack {
		// 工序表与零件关系表
		String procedurePartRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProcedurePartRelationship());
		// 新技术部-工艺路线管理
		String processRouteDataTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProcessRouteData());
		LambdaQueryWrapper<ProcedurePartRelationshipModel> queryProcedurePart = Wrappers.lambdaQuery();
		if (requestProcedureData.getProductPartProcedureCode() != null) {
			queryProcedurePart.eq(ProcedurePartRelationshipModel::getProcedureCode,
					requestProcedureData.getProductPartProcedureCode());
		}
		queryProcedurePart.eq(ProcedurePartRelationshipModel::getProcessRouteDataCode,
				requestProcedureData.getProcessRouteDataCode());
		queryProcedurePart.eq(ProcedurePartRelationshipModel::getUniqueId, requestProcedureData.getUniqueId());
		queryProcedurePart.eq(ProcedurePartRelationshipModel::getEnterpriseCode, getEnterpriseCode());
		queryProcedurePart.gt(ProcedurePartRelationshipModel::getProcedureCode, CommonConstant.NUMBER_ZERO);
		queryProcedurePart.eq(ProcedurePartRelationshipModel::getDeleteFlag, Boolean.FALSE);
		RequestTableHelper.setTableName(procedurePartRelationshipTableName);
		List<ProcedurePartRelationshipModel> resultProductPartRouteList =
				procedurePartRelationshipService.list(queryProcedurePart);
		if (ObjectUtil.isNotEmpty(resultProductPartRouteList)) {
			// 工序信息准备
			List<Long> procedureCodeList =
					resultProductPartRouteList.stream().map(ProcedurePartRelationshipModel::getProcedureCode)
							.filter(Objects::nonNull)
							.toList();
			// 中所有元素是否都为 null
			if (procedureCodeList.isEmpty()) {
				return Boolean.FALSE;
			}
			RequestTableHelper.setTableName(processRouteDataTableName);
			ProcessRouteDataModel processRouteDataModel =
					processRouteDataService.getById(requestProcedureData.getProcessRouteDataCode());
			Set<Long> existingProcedureCodes = Optional.ofNullable(
							JSONUtil.toList(processRouteDataModel.getProcedureData(), Long.class))
					.orElse(Collections.emptyList())
					.stream()
					.filter(Objects::nonNull)
					.collect(Collectors.toSet());
			if (ObjectUtil.isEmpty(procedureCodeList)) {
				return Boolean.FALSE;
			}
			log.info("判断返回值：getIsDeleteProcess existingProcedureCodes: {}, procedureCodeList: {}",
					existingProcedureCodes, JSONUtil.toJsonStr(procedureCodeList));
			List<Long> missingProcedureCodes = procedureCodeList.stream()
					.filter(code -> !existingProcedureCodes.contains(code))
					.collect(Collectors.toList());
			boolean isMissing = !missingProcedureCodes.isEmpty();
			if (isMissing) {
				rpcDTO.setDeleteProcessInfoList(productPartProcedureService.queryListByProcedureIdIn(missingProcedureCodes));
			}
			Boolean resultMissing = isMissing ? Boolean.TRUE : Boolean.FALSE;
			log.info("判断返回值：missingProcedureCodes: {},resultMissing  {} ", JSONUtil.toJsonStr(missingProcedureCodes), resultMissing);
			return resultMissing;
		}
		return Boolean.FALSE;
	}

	/**
	 * @description TODO
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/7/1 15:53
	 * @department: Product development
	 */
	public Map<String, Boolean> getIsDeleteProcessBatch(List<ProductPartProcedureRpcRequest> requestData) {
		log.info("start getIsDeleteProcessBatch requestData {}", JSONUtil.toJsonStr(requestData));
		// 表名初始化
		String procedurePartRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProcedurePartRelationship());
		String processRouteDataTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProcessRouteData());
		// 构建结果 map
		Map<String, Boolean> result = new HashMap<>();
		if (CollUtil.isEmpty(requestData)) {
			return result;
		}

		// 提取所有 processRouteDataCode 和 uniqueId
		Set<String> requestKeys = requestData.stream()
				.map(req -> req.getProcessRouteDataCode() + ":" + req.getUniqueId())
				.collect(Collectors.toSet());

		// 查询所有关系数据
		LambdaQueryWrapper<ProcedurePartRelationshipModel> query = Wrappers.lambdaQuery();
		query.in(ProcedurePartRelationshipModel::getProcessRouteDataCode,
				requestData.stream().map(ProductPartProcedureRpcRequest::getProcessRouteDataCode).distinct().toList());
		query.in(ProcedurePartRelationshipModel::getUniqueId,
				requestData.stream().map(ProductPartProcedureRpcRequest::getUniqueId).distinct().toList());
		query.eq(ProcedurePartRelationshipModel::getEnterpriseCode, getEnterpriseCode());
		query.eq(ProcedurePartRelationshipModel::getDeleteFlag, Boolean.FALSE);

		RequestTableHelper.setTableName(procedurePartRelationshipTableName);
		List<ProcedurePartRelationshipModel> relationshipModels = procedurePartRelationshipService.list(query);

		// 按 processRouteDataCode + uniqueId 分组
		Map<String, List<ProcedurePartRelationshipModel>> groupedRelationships = relationshipModels.stream()
				.collect(Collectors.groupingBy(model ->
						model.getProcessRouteDataCode() + ":" + model.getUniqueId()));

		// 获取所有 processRouteDataCode
		Set<Long> processRouteDataCodes = relationshipModels.stream()
				.map(ProcedurePartRelationshipModel::getProcessRouteDataCode)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());

		// 查询所有工艺路线数据
		Map<Long, ProcessRouteDataModel> processRouteDataMap = new HashMap<>();
		if (CollUtil.isNotEmpty(processRouteDataCodes)) {
			RequestTableHelper.setTableName(processRouteDataTableName);
			List<ProcessRouteDataModel> processRouteDataModels = processRouteDataService.listByIds(processRouteDataCodes);
			processRouteDataMap = processRouteDataModels.stream()
					.collect(Collectors.toMap(ProcessRouteDataModel::getProcessRouteDataCode, Function.identity()));
		}

		// 遍历每个请求，组装结果
		for (ProductPartProcedureRpcRequest request : requestData) {
			String key = request.getProcessRouteDataCode() + ":" + request.getUniqueId();
			List<ProcedurePartRelationshipModel> models = groupedRelationships.getOrDefault(key, Collections.emptyList());

			List<Long> procedureCodeList = models.stream()
					.map(ProcedurePartRelationshipModel::getProcedureCode)
					.filter(Objects::nonNull)
					.toList();

			if (procedureCodeList.isEmpty()) {
				result.put(key, Boolean.FALSE);
				continue;
			}

			ProcessRouteDataModel processRouteDataModel = processRouteDataMap.get(request.getProcessRouteDataCode());
			if (processRouteDataModel == null) {
				result.put(key, Boolean.FALSE);
				continue;
			}

			Set<Long> existingProcedureCodes = Optional.ofNullable(
							JSONUtil.toList(processRouteDataModel.getProcedureData(), Long.class))
					.orElse(Collections.emptyList())
					.stream()
					.filter(Objects::nonNull)
					.collect(Collectors.toSet());

			List<Long> missingProcedureCodes = procedureCodeList.stream()
					.filter(code -> !existingProcedureCodes.contains(code))
					.toList();
			boolean isMissing = !missingProcedureCodes.isEmpty();
			result.put(key, isMissing);
		}
		log.info("end getIsDeleteProcessBatch result {}", JSONUtil.toJsonStr(result));
		return result;
	}

	/**
	 * @description 产品零件-根据表名分类，批量获取编号
	 * @param attribute           产品或零件属性
	 * @param productPartTypeCode 分类代码
	 * @return 返回生成的统一编号
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/6/25 14:05
	 * @department: Product development
	 */
	public List<String> getGlobalUnityNoList(Integer attribute, Long productPartTypeCode,
	                                         List<ProductPartModel> queryNotExistsSchedule) {
		try {
			int size = queryNotExistsSchedule.size();
			List<String> nameList = queryNotExistsSchedule.stream().map(ProductPartModel::getProductPartSign).filter(Objects::nonNull).toList();
			List<String> modelList = queryNotExistsSchedule.stream().map(ProductPartModel::getModel).filter(Objects::nonNull).toList();
			log.info("start getGlobalUnityNoList attribute {},productPartTypeCode {},size {}", attribute, productPartTypeCode, size);
			if (attribute == null && productPartTypeCode == null) {
				log.warn("产品和零件单号生成空，不做任何处理，不进后续行操作");
				return null;
			}
			// 构建请求对象
			GlobalSerialNumberRequest requestData = new GlobalSerialNumberRequest();
			long tableCode = enCodePropertiesConfig.getTableCodeByAttribute(attribute);
			requestData.setTableCode(tableCode);
			requestData.setQueryKey(String.valueOf(productPartTypeCode));
			// 设置预览标志
			requestData.getContext().put("previewFlag", Boolean.FALSE);
			requestData.getContext().put("productPartNameConfig", nameList);
			requestData.getContext().put("productPartModelConfig", modelList);
			log.info("start 开始请求编码获取code入参 requestData {} size {}", JSONUtil.toJsonStr(requestData), size);
			// 获取表代码-统一单号生成逻辑提取
			List<String> resultUnityNo = globalNumberServer.serialNumberGenerateWay(requestData, size);
			log.info("start 开始请求编码获取code返回 resultUnityNo {}", JSONUtil.toJsonStr(resultUnityNo));
			return resultUnityNo;
		} catch (Exception e) {
			String defNo = StrUtil.format("ee-{}", RandomUtil.randomStringLowerWithoutStr(6, ""));
			log.error("查询统一编号数据出错，返回默认兜底编号 {}", defNo, e);
			return List.of(defNo);
		}
	}

	public List<Map<String, Object>> getGlobalUnityNoByList(Integer attribute, Long productPartTypeCode,
	                                                        List<ProductPartModel> queryNotExistsSchedule) {
		try {
			log.info("start getGlobalUnityNoByList attribute {},productPartTypeCode {},size {}",
					attribute, productPartTypeCode,
					queryNotExistsSchedule.size());
			if (attribute == null && productPartTypeCode == null) {
				log.warn("产品和零件单号生成空，不做任何处理，不进后续行操作");
				return List.of();
			}

			// 在批量生成编号之前，先重置序列号
			// 这是规则配置更新场景，需要重置序列号从1开始
			resetSequenceForConfigUpdate(attribute, productPartTypeCode);

			List<GlobalSerialNumberRequest> requestDataList = new ArrayList<>();

			for (ProductPartModel productPartModel : queryNotExistsSchedule) {
				// 构建请求对象
				GlobalSerialNumberRequest requestData = new GlobalSerialNumberRequest();
				long tableCode = enCodePropertiesConfig.getTableCodeByAttribute(attribute);
				requestData.setTableCode(tableCode);
				requestData.setQueryKey(String.valueOf(productPartTypeCode));
				// 设置预览标志
				requestData.getContext().put("previewFlag", Boolean.FALSE);
				requestData.getContext().put("unityNo", productPartModel.getUnityNo());
				requestData.getContext().put("productPartNameConfig", productPartModel.getProductPartSign());
				requestData.getContext().put("productPartModelConfig", productPartModel.getModel());
				requestDataList.add(requestData);
			}
			// 获取表代码-统一单号生成逻辑提取
			List<Map<String, Object>> resultUnityNo = globalNumberServer.serialNumberGenerateWay(requestDataList);
			log.info("start 开始请求编码获取code返回 resultUnityNo {}", JSONUtil.toJsonStr(resultUnityNo));
			return resultUnityNo;
		} catch (Exception e) {
			String defNo = StrUtil.format("ee-{}", RandomUtil.randomStringLowerWithoutStr(6, ""));
			log.error("查询统一编号数据出错，返回默认兜底编号 {}", defNo, e);
			List<Map<String, Object>> resultListMap = new ArrayList<>();
			for (ProductPartModel productPartModel : queryNotExistsSchedule) {
				Map<String, Object> resultMap = new HashMap<>();
				resultMap.put("unityNo", productPartModel.getUnityNo());
				resultMap.put("resultUnityNo", defNo);
				resultListMap.add(resultMap);
			}
			return resultListMap;
		}
	}

	/**
	 * @description 为规则配置更新场景重置序列号
	 * @param attribute 产品或零件属性
	 * @param productPartTypeCode 分类代码
	 */
	private void resetSequenceForConfigUpdate(Integer attribute, Long productPartTypeCode) {
		try {
			// 构建重置请求对象
			GlobalSerialNumberRequest resetRequest = new GlobalSerialNumberRequest();
			long tableCode = enCodePropertiesConfig.getTableCodeByAttribute(attribute);
			resetRequest.setTableCode(tableCode);
			resetRequest.setQueryKey(String.valueOf(productPartTypeCode));

			// 设置重置序列号的标识
			Map<String, Object> resetContext = new HashMap<>();
			resetContext.put("isUnityNoRefresh", Boolean.TRUE);
			resetRequest.setContext(resetContext);

			// 调用一次编号生成来触发序列号重置
			// 这里不需要实际的编号，只需要重置序列号
			globalNumberServer.serialNumberGenerateWay(resetRequest);

			log.info("规则配置更新场景，已重置序列号 - attribute: {}, productPartTypeCode: {}", attribute, productPartTypeCode);
		} catch (Exception e) {
			log.warn("重置序列号失败，但不影响后续编号生成 - attribute: {}, productPartTypeCode: {}", attribute, productPartTypeCode, e);
		}
	}

	/**
	 * @description 校验产品零件是否绑定工艺路线
	 * true : 未绑定 false: 已绑定
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/2/19 15:14
	 */
	public boolean checkProductPartRouteShipDispatch(Long productPartCode) {
		String procedureRouteRelationshipTableName = tableFactory.getTableName(tableFactory.module.getWarehouse(),
				tableFactory.table.getProcedureRouteRelationship());
		LambdaQueryWrapper<ProcedureRouteRelationshipModel> query = Wrappers.lambdaQuery();
		query.eq(ProcedureRouteRelationshipModel::getProductPartCode, productPartCode);
		query.eq(ProcedureRouteRelationshipModel::getEnterpriseCode, getEnterpriseCode());
		query.eq(ProcedureRouteRelationshipModel::getDeleteFlag, Boolean.FALSE);
		RequestTableHelper.setTableName(procedureRouteRelationshipTableName);
		return procedureRouteRelationshipService.count(query) == CommonConstant.NUMBER_ZERO;
	}

	/**
	 * @description 校验产品零件是下是否有bom
	 * true : 未绑定 false: 已绑定
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/2/19 15:14
	 */
	public boolean checkHasBom(ProductPartModel productPartModel) {
		// 工艺路线管理表
		String processRouteDataTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProcessRouteData());
		// 产品零件与工艺路线关系表
		String productPartRouteRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProductPartRouteRelationship());
		// 工序表与工艺路线关系表
		String procedureRouteRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProcedureRouteRelationship());
		// 产品零件工序表
		String productPartProcedureTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProductPartProcedure());

		LambdaQueryWrapper<ProductPartRouteRelationshipModel> queryProductPartRoute = Wrappers.lambdaQuery();
		queryProductPartRoute.eq(ProductPartRouteRelationshipModel::getProductPartCode,
				productPartModel.getProductPartCode());
		productPartModel.setDefaultRoute(productPartModel.getDefaultRoute());
		queryProductPartRoute.eq(ProductPartRouteRelationshipModel::getEnterpriseCode, getEnterpriseCode());
		queryProductPartRoute.eq(ProductPartRouteRelationshipModel::getDeleteFlag, Boolean.FALSE);
		// 查询产品、零件和工艺路线绑定关系
		RequestTableHelper.setTableName(productPartRouteRelationshipTableName);
		List<ProductPartRouteRelationshipModel> resultProductPartRouteList =
				productPartRouteRelationshipService.list(queryProductPartRoute);
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
				dataDetailRpcDTO.setRouteNodeResult(routeNodeModels(processRouteDataDetailRpcDTO.getRouteNode()));
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
				if (null != resultProcedureList && 0 < resultProcedureList.size()) {
					// 确认绑定信息
					return false;
				}
			}
		}
				return true;
	}
	/**
	 * @description 校验产品零件是否被其他bom绑定(有路线)
	 * true : 未绑定 false: 已绑定
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/2/19 15:14
	 */
	public boolean checkHasBomed(ProductPartModel productPartModel) {
		// 工序表与零件关系表
		String procedurePartRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProcedurePartRelationship());

		RequestTableHelper.setTableName(procedurePartRelationshipTableName);
		// 根据唯一 ID查询绑定零件 没有工序 ID场景
		LambdaQueryWrapper<ProcedurePartRelationshipModel> queryProcedure = Wrappers.lambdaQuery();
		queryProcedure.eq(ProcedurePartRelationshipModel::getProductPartCode, productPartModel.getProductPartCode());
		queryProcedure.eq(ProcedurePartRelationshipModel::getEnterpriseCode, getEnterpriseCode());
		queryProcedure.eq(ProcedurePartRelationshipModel::getDeleteFlag, Boolean.FALSE);
		List<ProcedurePartRelationshipModel> resultProcedureList =
				procedurePartRelationshipService.list(queryProcedure);
		if(null != resultProcedureList && 0 < resultProcedureList.size()) {
			// 确认绑定信息
			return false;
		}
		return true;
	}

	/**
	 * 检查产品零件是否绑定组成表 无路线
	 * @param productPartCode 产品零件代码
	 * @return 如果没有绑定组成则返回true，否则返回false
	 */
	public boolean checkHasBomBZN(Long productPartCode) {    // 产品零件-绑定组成表
		// 获取产品零件组成表的表名
		String productPartCompTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPartComp());
		// 创建Lambda查询包装器，用于构建查询条件
		LambdaQueryWrapper<ProductPartCompModel> queryComp = Wrappers.lambdaQuery();
		// 设置查询条件：子零件代码等于传入的产品零件代码
		queryComp.eq(ProductPartCompModel::getParentCode, productPartCode);
		// 设置查询条件：企业代码等于当前企业代码
		queryComp.eq(ProductPartCompModel::getEnterpriseCode, getEnterpriseCode());
		// 设置查询条件：删除标志为false（即未删除）
		queryComp.eq(ProductPartCompModel::getDeleteFlag, Boolean.FALSE);
		// 设置查询的表名
		RequestTableHelper.setTableName(productPartCompTableName);
		// 查询满足条件的记录数，如果为0则返回true，否则返回false
		return productPartCompMapper.selectCount(queryComp) == CommonConstant.NUMBER_ZERO;

	}

	/**
	 * 检查产品零件是否在其他产品的BOM（物料清单）中绑定组成
	 * @param productPartCode 产品零件代码
	 * @return 如果查询结果为空则返回true，否则返回false
	 */
	public boolean checkInBomBZN(Long productPartCode) {    // 产品零件-绑定组成表
		// 获取产品零件组成表的表名
		String productPartCompTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPartComp());
    // 创建Lambda查询包装器，用于构建查询条件
		LambdaQueryWrapper<ProductPartCompModel> queryComp = Wrappers.lambdaQuery();
    // 设置查询条件：子零件代码等于传入的产品零件代码
		queryComp.eq(ProductPartCompModel::getChildCode, productPartCode);
    // 设置查询条件：企业代码等于当前企业代码
		queryComp.eq(ProductPartCompModel::getEnterpriseCode, getEnterpriseCode());
    // 设置查询条件：删除标志为false（即未删除）
		queryComp.eq(ProductPartCompModel::getDeleteFlag, Boolean.FALSE);
    // 设置查询的表名
		RequestTableHelper.setTableName(productPartCompTableName);
    // 查询满足条件的记录数，如果为0则返回true，否则返回false
		return productPartCompMapper.selectCount(queryComp) == CommonConstant.NUMBER_ZERO;
	}
}
