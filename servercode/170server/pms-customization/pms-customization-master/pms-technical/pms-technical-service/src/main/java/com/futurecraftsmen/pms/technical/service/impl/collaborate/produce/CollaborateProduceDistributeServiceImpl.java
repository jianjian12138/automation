/*
 * @(#)com.futurecraftsmen.pms.technical.service.impl.collaborate.produce 2025/7/3 17:09
 * @Author <a href="mailto:xyqierkang@gmail.com">ErKang Qi</a>
 * @Blog：https://www.qekang.com
 * Copyright (c) 2019-2025 Shanghai
 * All rights reserved.

 * This software is the confidential and proprietary information of
 * You shall not disclose such Confidential Information and shall use
 * it only in accordance with the terms of the license agreement
 */
package com.futurecraftsmen.pms.technical.service.impl.collaborate.produce;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.futurecraftsmen.pms.common.utils.PrecisionUtils;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.futurecraftsmen.pms.api.dto.base.RpcPagingDTO;
import com.futurecraftsmen.pms.api.dto.scope.PostPageFunctionScopeRpcDTO;
import com.futurecraftsmen.pms.api.service.FunctionScopeService;
import com.futurecraftsmen.pms.dm.api.service.base.staff.StaffService;
import com.futurecraftsmen.pms.pas.api.rpc.receiving.ProductMaterialRpcRequest;
import com.futurecraftsmen.pms.pas.api.rpc.receiving.ProductTransitRpcDTO;
import com.futurecraftsmen.pms.pas.api.service.receiving.PurchaseSelfReceivingService;
import com.futurecraftsmen.pms.right.api.domain.CustomPageEnum;
import com.futurecraftsmen.pms.starter.domain.starter.PmsStarter;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.MaterialNatureEnum;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.OutStockApply;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.ProcessEnum;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.StagingArea;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.base.*;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.inbound.CollaborateInboundPageRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.outbound.CollaboratePurchaseContractInfoRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.outbound.CollaborateStockInfoRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.outbound.CollaborateStockInfoRpcRequest;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.process.CollaborateCheckMaterialStatusDTO;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.process.CollaborateProcessRecordRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.produce.*;
import com.futurecraftsmen.pms.technical.api.domain.production.ScrapDataAddOrUpdateRequest;
import com.futurecraftsmen.pms.technical.api.domain.production.ScrapDataDTO;
import com.futurecraftsmen.pms.technical.api.domain.sellorder.SellOrderProductRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.sellorder.SellOrderProductScheduleDetailRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.procedure.ProductPartProcedureRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.procedure.ScrapTypeEnum;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.ProductPartRpcDTO;
import com.futurecraftsmen.pms.technical.api.service.ISellOrderService;
import com.futurecraftsmen.pms.technical.api.service.collaborate.CollaborateOutboundService;
import com.futurecraftsmen.pms.technical.api.service.collaborate.CollaborateProcessService;
import com.futurecraftsmen.pms.technical.api.service.collaborate.CollaborateProduceDistributeService;
import com.futurecraftsmen.pms.technical.api.service.collaborate.ProductBomQueryService;
import com.futurecraftsmen.pms.technical.api.service.production.IScrapDataService;
import com.futurecraftsmen.pms.technical.api.service.technical.IProductPartProcedureService;
import com.futurecraftsmen.pms.technical.api.service.technical.IProductPartService;
import com.futurecraftsmen.pms.technical.api.service.warehouse.awaiting.IAwaitingDetailsService;
import com.futurecraftsmen.pms.technical.service.common.enums.MaterialType;
import com.futurecraftsmen.pms.technical.service.config.TechnicalTableNameProxyImpl;
import com.futurecraftsmen.pms.technical.service.dao.ISellOrderMapper;
import com.futurecraftsmen.pms.technical.service.dao.collaborate.*;
import com.futurecraftsmen.pms.technical.service.dao.collaborate.produce.CollaborateProduceBatchInfoMapper;
import com.futurecraftsmen.pms.technical.service.dao.collaborate.produce.CollaborateProduceDistributeMapper;
import com.futurecraftsmen.pms.technical.service.dao.collaborate.produce.CollaborateProduceExtendMapper;
import com.futurecraftsmen.pms.technical.service.dao.collaborate.produce.CollaborateProduceHandleMapper;
import com.futurecraftsmen.pms.technical.service.dao.technical.IProduceMainMapper;
import com.futurecraftsmen.pms.technical.service.dao.technical.IProduceSubDispatcherMapper;
import com.futurecraftsmen.pms.technical.service.dao.technical.IProduceSubPartInfoMapper;
import com.futurecraftsmen.pms.technical.service.domain.collaborate.CollaborateBatchProduceMaterialModel;
import com.futurecraftsmen.pms.technical.service.domain.collaborate.CollaborateInboundItemModel;
import com.futurecraftsmen.pms.technical.service.domain.collaborate.CollaborateProduceBatchInfoModel;
import com.futurecraftsmen.pms.technical.service.domain.collaborate.CollaborateScheduleItemModel;
import com.futurecraftsmen.pms.technical.service.domain.collaborate.base.CollaborateContractReceiveMaterial;
import com.futurecraftsmen.pms.technical.service.domain.collaborate.base.CollaborateContractReceiveMaterials;
import com.futurecraftsmen.pms.technical.service.domain.sellorder.SellOrderModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.produce.ProduceMainModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.produce.ProduceSubDispatcherModel;
import com.futurecraftsmen.pms.technical.service.domain.technical.produce.ProduceSubPartInfoModel;
import com.futurecraftsmen.pms.technical.service.impl.collaborate.lacktask.CollaborateReceiveMaterialInnerService;
import com.futurecraftsmen.pms.technical.service.impl.inner.TableNameFactory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.aerie.forest.core.brick.domain.view.CodeMapName;
import org.aerie.forest.core.brick.exception.ExceptionMsg;
import org.aerie.forest.core.brick.exception.ExceptionPack;
import org.aerie.forest.core.brick.processor.datapenetrate.GlobalTokenPenetrate;
import org.aerie.forest.core.brick.processor.datapenetrate.InfoPenetrateProcessor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.futurecraftsmen.pms.technical.service.util.CommonUtil.getEnterpriseCode;
import static org.aerie.forest.core.brick.processor.datapenetrate.GlobalTokenPenetrate.ENTERPRISE_CODE_THREAD_LOCAL;

/**
 * @description 主任分发
 *
 * @author zhanglijia
 * @organization futurecraftsmen
 * @date Created in 2025/7/3 17:09
 * @department: Product development
 */
@DubboService
@Slf4j
public class CollaborateProduceDistributeServiceImpl implements CollaborateProduceDistributeService {

	@Resource
	private CollaborateProduceDistributeMapper collaborateProduceDistributeMapper;

	@Resource
	private CollaborateScheduleItemMapper collaborateScheduleItemMapper;

	@Resource
	private IProductPartService iProductPartService;

	@Resource
	private TechnicalTableNameProxyImpl technicalTableNameProxyImpl;

	@Resource
	private IProduceMainMapper iProduceMainMapper;

	@Resource
	private IProduceSubDispatcherMapper iProduceSubDispatcherMapper;

	@Resource
	private IProduceSubPartInfoMapper iProduceSubPartInfoMapper;

	@Resource
	private ProductBomQueryService productBomQueryService;

	@Resource
	private OutStockApplyMapper outStockApplyMapper;

	@DubboReference
	private FunctionScopeService functionScopeService;

	@Resource
	private CollaborateProduceBatchInfoMapper collaborateProduceBatchInfoMapper;

	@Resource
	private ISellOrderService iSellOrderService;

	@Resource
	private IAwaitingDetailsService awaitingDetailsService;

	@Resource
	private ISellOrderMapper iSellOrderMapper;

	@Resource
	private CollaborateBatchProduceMaterialMapper collaborateBatchProduceMaterialMapper;

	@Resource
	@Lazy
	private CollaborateOutboundService collaborateOutboundService;

	@Resource
	private IProductPartProcedureService iProductPartProcedureService;

	@Resource
	private StagingAreaMapper stagingAreaMapper;

	@Resource
	private CollaborateProducePickMapper collaborateProducePickMapper;

	@DubboReference(group = "pms", check = false, retries = 0)
	protected PurchaseSelfReceivingService selfReceivingService;

	@Resource
	private CollaborateProduceExtendMapper collaborateProduceExtendMapper;

	@Resource
	@Lazy
	private CollaborateProcessService collaborateProcessService;

	@DubboReference(check = false, retries = 0)
	private StaffService staffService;

	@Resource
	private CollaborateProduceHandleMapper collaborateProduceHandleMapper;

	@Resource
	private IScrapDataService scrapDataService;

	@Resource
	private CollaborateProcessReadInfoMapper collaborateProcessReadInfoMapper;

	@Resource
	private TableNameFactory tableFactory;

	@Resource
	private CollaborateInboundItemMapper collaborateInboundItemMapper;

	@Autowired
	private CollaborateReceiveMaterialInnerService collaborateReceiveMaterialInnerService;

	@Override
	public List<CollaborateScheduleItemDTO> orderList(CollaborateProduceRequest request) throws ExceptionPack {

		LambdaQueryWrapper<CollaborateScheduleItemModel> wrapper = Wrappers.lambdaQuery();
		wrapper.eq(CollaborateScheduleItemModel::getContractCode, request.getContractCode());
		wrapper.eq(CollaborateScheduleItemModel::getCollaborateType, 0);
		wrapper.eq(CollaborateScheduleItemModel::getDeleteFlag, false);
		wrapper.eq(CollaborateScheduleItemModel::getEnterpriseCode, technicalTableNameProxyImpl.getEnterpriseCode());
		wrapper.eq(CollaborateScheduleItemModel::isCanProduce, true);
		List<CollaborateScheduleItemModel> itemModels = collaborateScheduleItemMapper.selectList(wrapper);
		if (CollectionUtils.isEmpty(itemModels)) {
			return Collections.emptyList();
		}

		Map<Long, List<SellOrderProductRpcDTO>> batchQuery =
				iSellOrderService.batchQuery(itemModels.stream().map(a -> a.getSellOrderCode()).toList());

		List<CollaborateScheduleItemDTO> list = Convert.toList(CollaborateScheduleItemDTO.class, itemModels);

		Map<Long, CollaboratePurchaseContractInfoRpcDTO> wayNumMap = getOnWayNumMap(list.stream().map(a -> a.getCollaborateMaterial()).toList());

		for (CollaborateScheduleItemDTO dto : list) {
			dto.setPartInfo(iProductPartService.queryPartsById(dto.getCollaborateMaterial()));

			for (SellOrderProductRpcDTO productRpcDTO :
					batchQuery.getOrDefault(dto.getSellOrderCode(), new ArrayList<>()).stream()
							.filter(a -> a.getProductPartCode().equals(dto.getCollaborateMaterial()))
							.toList()) {
				dto.setDelivery(productRpcDTO.getProductDeliverDate());
				dto.setDisplayDelivery(productRpcDTO.getProductDeliver());
				List<String> model = new ArrayList<>(Optional.ofNullable(dto.getPublicModel()).orElse(new ArrayList<>()));
				model.add(productRpcDTO.getPublicModel());
				dto.setPublicModel(model.stream().filter(StringUtils::isNotBlank).distinct().toList());
				dto.setRemark(productRpcDTO.getProductRemark());

				//合同的 产品名称
				dto.setContractProductName(productRpcDTO.getProductName());
				dto.setProductModel(productRpcDTO.getProductModel());

				List<String> cacheList = new ArrayList<>();
				cacheList.add(dto.getProductModel().getUnityNo());
				cacheList.add(dto.getProductModel().getName());
				cacheList.add(dto.getProductModel().getModel());
				cacheList = cacheList.stream().filter(StringUtils::isNotBlank).toList();

				dto.setProductModelName(String.join("-", cacheList));
			}

			if(ObjectUtil.isNotEmpty(wayNumMap.getOrDefault(dto.getCollaborateMaterial(), null))){
				dto.setInWayNum(wayNumMap.getOrDefault(dto.getCollaborateMaterial(), null).getOnWayNum());
				dto.setReceivingNoIntoStockNum(wayNumMap.getOrDefault(dto.getCollaborateMaterial(), null).getReceivingNoIntoStockNum());
			}else{
				dto.setInWayNum(BigDecimal.ZERO);
				dto.setReceivingNoIntoStockNum(BigDecimal.ZERO);
			}
		}

		CollaborateStockInfoRpcRequest requestData = new CollaborateStockInfoRpcRequest();
		requestData.setContractCode(request.getContractCode());
		requestData.setProductPartCodes(list.stream().map(a -> a.getCollaborateMaterial()).toList());
		Map<Long, CollaborateStockInfoRpcDTO> decimalMap = collaborateOutboundService.getStockInfo(requestData).stream()
				.collect(Collectors
						.toMap(a -> a.getProductPartCode(), a -> a,
								(a, b) -> a));

		for (CollaborateScheduleItemDTO dto : list) {

			dto.setStockNum(decimalMap.getOrDefault(dto.getCollaborateMaterial(), new CollaborateStockInfoRpcDTO()).getCanUseNum());
			dto.setHasOtherContractNeed(decimalMap.getOrDefault(dto.getCollaborateMaterial(), new CollaborateStockInfoRpcDTO()).getHasOtherContractNeed());
		}
		return list;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void pickBatch(List<CollaborateDistributeRequest> request) throws ExceptionPack {

		for (CollaborateDistributeRequest distributeRequest : request) {
			pick(distributeRequest);
		}
	}

	@Override
	public void pick(CollaborateDistributeRequest request) throws ExceptionPack {

		if (BigDecimal.ZERO.compareTo(Optional.ofNullable(request.getPickDataNum()).orElse(BigDecimal.ZERO)) >= 0) {
			return;
		}

		LambdaQueryWrapper<StagingArea> areaWrapper = Wrappers.lambdaQuery();
		areaWrapper.eq(StagingArea::getContractCode, request.getContractCode());
		areaWrapper.eq(StagingArea::getDeleteFlag, false);
		areaWrapper.in(StagingArea::getAreaType, List.of(0, 3));
		areaWrapper.eq(StagingArea::getEnterpriseCode, technicalTableNameProxyImpl.getEnterpriseCode());
		areaWrapper.eq(StagingArea::getProductPartCode, request.getPartCode());
		List<StagingArea> stagingAreas = stagingAreaMapper.selectList(areaWrapper).stream().filter(a -> {

			if (request.getParentPartCode() == null || request.getParentPartCode() == 0) {
				return a.getParentMaterial() == null || a.getParentMaterial() == 0;
			} else {
				return request.getPartCode().equals(a.getProductPartCode());
			}

		}).toList();

		LambdaQueryWrapper<CollaborateProducePick> pickWrapper = Wrappers.lambdaQuery();
		pickWrapper.eq(CollaborateProducePick::getContractCode, request.getContractCode());
		pickWrapper.eq(CollaborateProducePick::getDeleteFlag, false);
		pickWrapper.eq(CollaborateProducePick::getEnterpriseCode, technicalTableNameProxyImpl.getEnterpriseCode());
		pickWrapper.eq(CollaborateProducePick::getPartCode, request.getPartCode());
		List<CollaborateProducePick> picks = collaborateProducePickMapper.selectList(pickWrapper).stream().filter(a -> {
			if (request.getParentPartCode() == null || request.getParentPartCode() == 0) {
				return a.getParenPartCode() == null || a.getParenPartCode() == 0;
			} else {
				return request.getPartCode().equals(a.getPartCode());
			}
		}).toList();

		BigDecimal areaNum = stagingAreas.stream().map(a -> a.getOutNum())
				.reduce(BigDecimal.ZERO, (a, b) -> Optional.ofNullable(a).orElse(BigDecimal.ZERO)
						.add(Optional.ofNullable(b).orElse(BigDecimal.ZERO)));

		BigDecimal pickNum = picks.stream().map(a -> a.getPickNum())
				.reduce(BigDecimal.ZERO, (a, b) -> Optional.ofNullable(a).orElse(BigDecimal.ZERO)
						.add(Optional.ofNullable(b).orElse(BigDecimal.ZERO)));

		if (pickNum.add(request.getPickDataNum()).compareTo(areaNum) > 0) {
			String msg = "已超出可领取数量哦~";
			throw new ExceptionPack(new Exception(msg), ExceptionMsg.builder(msg).build());
		}

		savePickInfo(request.getContractCode(), request.getPartCode(), request.getParentPartCode(), request.getRemark(), request.getPickDataNum());
	}

	private void savePickInfo(Long contractCode, Long partCode, Long parentPartCode, String remark, BigDecimal pickNum) {
		CollaborateProducePick pick = new CollaborateProducePick();
		pick.setEnterpriseCode(technicalTableNameProxyImpl.getEnterpriseCode());
		pick.setPickNum(pickNum);
		pick.setRemark(remark);
		pick.setPrimaryKey(technicalTableNameProxyImpl.getNextPrimaryKey());
		pick.setPartCode(partCode);
		pick.setParenPartCode(parentPartCode);
		pick.setContractCode(contractCode);
		pick.setOpUserCode(technicalTableNameProxyImpl.getCurrentCode());
		collaborateProducePickMapper.insert(pick);
	}

	@Override
	public void pickOne(CollaborateDistributeRequest request) {

		/**
		 * 外发不进行扣减
		 */
		LambdaQueryWrapper<CollaborateBatchProduceMaterialModel> queryWrapper = Wrappers.lambdaQuery();
		queryWrapper.eq(CollaborateBatchProduceMaterialModel::getContractCode, request.getContractCode());
		queryWrapper.eq(CollaborateBatchProduceMaterialModel::getDeleteFlag, false);
		queryWrapper.eq(CollaborateBatchProduceMaterialModel::getEnterpriseCode, technicalTableNameProxyImpl.getEnterpriseCode());
		List<CollaborateBatchProduceMaterialModel> materialModels = collaborateBatchProduceMaterialMapper.selectList(queryWrapper);
		List<CollaborateBatchProduceMaterialModel> removeModel = materialModels.stream()
				.filter(a -> a.getNature() != null)
				.filter(a -> a.getNature() == MaterialNatureEnum.OUT_SEND).collect(Collectors.toList());

		List<CollaborateBatchProduceMaterialModel> saveModel = materialModels.stream()
				.filter(a -> a.getNature() != null || a.getNature() == null)
				.filter(a -> a.getNature() == MaterialNatureEnum.PRODUCE).toList();

		for (CollaborateBatchProduceMaterialModel collaborateBatchProduceMaterialModel : new ArrayList<>(removeModel)) {

			for (CollaborateBatchProduceMaterialModel model : saveModel) {
				if (model.getProduceMaterial().equals(collaborateBatchProduceMaterialModel.getProduceMaterial())) {

					if (model.getParentMaterial() == null || model.getParentMaterial() == 0) {
						if (collaborateBatchProduceMaterialModel.getParentMaterial() == null || collaborateBatchProduceMaterialModel.getParentMaterial() == 0) {
							removeModel.remove(collaborateBatchProduceMaterialModel);
							break;
						}
					}

					if (model.getParentMaterial().equals(collaborateBatchProduceMaterialModel.getParentMaterial())) {
						removeModel.remove(collaborateBatchProduceMaterialModel);
						break;
					}
				}
			}

		}

		Map<Long, CollaborateBatchProduceMaterialModel> removeProduct = removeModel.stream()
				.filter(a -> a.getParentMaterial() == null || a.getParentMaterial().equals(0))
				.collect(Collectors.toMap(a -> a.getProduceMaterial(),
						a -> a, (a, b) -> a));

		Map<String, CollaborateBatchProduceMaterialModel> removePart = removeModel.stream()
				.filter(a -> !(a.getParentMaterial() == null || a.getParentMaterial().equals(0)))
				.collect(Collectors.toMap(a -> a.getProduceMaterial() + "-" + a.getParentMaterial(),
						a -> a, (a, b) -> a));

		/**
		 * 中转区
		 */
		LambdaQueryWrapper<StagingArea> areaWrapper = Wrappers.lambdaQuery();
		areaWrapper.eq(StagingArea::getContractCode, request.getContractCode());
		areaWrapper.eq(StagingArea::getDeleteFlag, false);
		areaWrapper.in(StagingArea::getAreaType, List.of(0, 3));
		areaWrapper.eq(StagingArea::getEnterpriseCode, technicalTableNameProxyImpl.getEnterpriseCode());

		List<StagingArea> stagingAreas = stagingAreaMapper.selectList(areaWrapper);
		CurrentAndParent<StagingArea> currentAndParent = wrapperCurrentAndParentInfo(stagingAreas, a -> a.getParentMaterial(),
				a -> a.getProductPartCode());

		/**
		 * 已领量
		 */
		LambdaQueryWrapper<CollaborateProducePick> pickWrapper = Wrappers.lambdaQuery();
		pickWrapper.eq(CollaborateProducePick::getContractCode, request.getContractCode());
		pickWrapper.eq(CollaborateProducePick::getDeleteFlag, false);
		pickWrapper.eq(CollaborateProducePick::getEnterpriseCode, technicalTableNameProxyImpl.getEnterpriseCode());
		List<CollaborateProducePick> picks = collaborateProducePickMapper.selectList(pickWrapper);
		CurrentAndParent<CollaborateProducePick> pickCP = wrapperCurrentAndParentInfo(picks, a -> a.getParenPartCode(),
				a -> a.getPartCode());

		for (Map.Entry<Long, List<StagingArea>> entry : currentAndParent.parent.entrySet()) {

			if (removeProduct.containsKey(entry.getKey())) {
				continue;
			}

			BigDecimal areaNum = currentAndParent.getParentSum(entry.getKey(), a -> a.getOutNum());
			BigDecimal pickNum = pickCP.getParentSum(entry.getKey(), a -> a.getPickNum());
			if (areaNum.compareTo(pickNum) > 0) {
				savePickInfo(request.getContractCode(), entry.getKey(), null, null,
						areaNum.subtract(pickNum));
			}
		}

		for (Map.Entry<Long, Map<Long, List<StagingArea>>> mapEntry : currentAndParent.child.entrySet()) {
			for (Map.Entry<Long, List<StagingArea>> entry : mapEntry.getValue().entrySet()) {
				if (removePart.containsKey(entry.getKey() + "-" + mapEntry.getKey())) {
					continue;
				}

				BigDecimal areaNum = currentAndParent.getChildListSum(mapEntry.getKey(), entry.getKey(), a -> a.getOutNum());
				BigDecimal pickNum = pickCP.getChildListSum(mapEntry.getKey(), entry.getKey(), a -> a.getPickNum());
				if (areaNum.compareTo(pickNum) > 0) {
					savePickInfo(request.getContractCode(), entry.getKey(), mapEntry.getKey(), null,
							areaNum.subtract(pickNum));
				}
			}
		}
		collaborateProduceExtendMapper.updateStatus(request.getContractCode());

	}

	@Override
	public List<CollaborateProduceExtend> complementList(CollaborateDistributeRequest request) throws ExceptionPack {

		LambdaQueryWrapper<CollaborateProduceExtend> queryWrapper = Wrappers.lambdaQuery();
		queryWrapper.eq(CollaborateProduceExtend::getContractCode, request.getContractCode());
		queryWrapper.eq(CollaborateProduceExtend::getDeleteFlag, false);
		queryWrapper.eq(CollaborateProduceExtend::getEnterpriseCode, technicalTableNameProxyImpl.getEnterpriseCode());
		queryWrapper.eq(CollaborateProduceExtend::getPartCode, request.getPartCode());
		List<CollaborateProduceExtend> produceExtends = collaborateProduceExtendMapper.selectList(queryWrapper);
		produceExtends = produceExtends.stream().filter(a -> {
			if (request.getParentPartCode() == null || request.getParentPartCode() == 0) {
				return a.getParentPartCode() == null || a.getParentPartCode() == 0;
			} else {
				return request.getParentPartCode().equals(a.getParentPartCode());
			}
		}).toList();

		for (CollaborateProduceExtend produceExtend : produceExtends) {
			produceExtend.setOpUserName(staffService.getStaffCode(produceExtend.getOpUserCode()).getStaffName());
		}

		return produceExtends;
	}

	@Override
	public void complementMaterial(CollaborateDistributeRequest request) throws ExceptionPack {
		CollaborateProduceExtend extend = new CollaborateProduceExtend();
		extend.setPrimaryKey(technicalTableNameProxyImpl.getNextPrimaryKey());
		extend.setEnterpriseCode(technicalTableNameProxyImpl.getEnterpriseCode());
		extend.setOpUserCode(technicalTableNameProxyImpl.getCurrentCode());
		extend.setContractCode(request.getContractCode());
		extend.setPartCode(request.getPartCode());
		extend.setApplyNum(request.getComplementNum());
		extend.setRemark(request.getRemark());
		extend.setScrapNum(request.getScrapNum());
		extend.setParentPartCode(request.getParentPartCode());
		collaborateProduceExtendMapper.insert(extend);

		LambdaQueryWrapper<CollaborateScheduleItemModel> wrapper = Wrappers.lambdaQuery();
		wrapper.eq(CollaborateScheduleItemModel::getContractCode, request.getContractCode());
		wrapper.eq(CollaborateScheduleItemModel::getEnterpriseCode, technicalTableNameProxyImpl.getEnterpriseCode());
		wrapper.eq(CollaborateScheduleItemModel::getDeleteFlag, false);
		wrapper.eq(CollaborateScheduleItemModel::getCollaborateType, 1);
		wrapper.eq(CollaborateScheduleItemModel::getCollaborateMaterial, request.getPartCode());
		List<CollaborateScheduleItemModel> models = collaborateScheduleItemMapper.selectList(wrapper);
		OutStockApply apply = new OutStockApply();
		apply.setApplyNum(request.getComplementNum());
		apply.setPrimaryKey(PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId());
		apply.setEnterpriseCode(technicalTableNameProxyImpl.getEnterpriseCode());
		apply.setApplyStaff(technicalTableNameProxyImpl.getCurrentCode());
		apply.setContractCode(models.getFirst().getContractCode());
		apply.setDataKey(models.getFirst().getCollaborateCode());
		apply.setUnityNo(request.getBatchNum());
		apply.setContractNumber(models.getFirst().getContractNumber());
		apply.setProductPartCode(request.getPartCode());
		apply.setType(MaterialType.SCBL.getType());

		if (Optional.ofNullable(request.getScrapNum()).orElse(BigDecimal.ZERO).compareTo(BigDecimal.ZERO) > 0) {
			ScrapDataAddOrUpdateRequest scrapDataRequest = new ScrapDataAddOrUpdateRequest();
			ProductPartRpcDTO partRpcDTO = iProductPartService.queryPartsById(request.getPartCode());
			scrapDataRequest.setContractCode(models.getFirst().getContractCode());
			scrapDataRequest.setScrapType(ScrapTypeEnum.produce_add_material.getCode());
			scrapDataRequest.setPcs(partRpcDTO.getPcs());
			scrapDataRequest.setScrapRemark(request.getRemark());
			scrapDataRequest.setCreator(technicalTableNameProxyImpl.getCurrentCode());
			scrapDataRequest.setScrapDate(new Date());
			scrapDataRequest.setEnterpriseCode(getEnterpriseCode());
			scrapDataRequest.setScrapDataCode(technicalTableNameProxyImpl.getNextPrimaryKey());
			ScrapDataDTO scrapDataDTO = scrapDataService.getScrapDataLastId();
			String scrapNumber = StrUtil.format("BF{}0{}", DateUtil.format(DateUtil.date(), "yyyyMMdd"), scrapDataDTO == null ? 0 :
					scrapDataDTO.getId());
			scrapDataRequest.setScrapNumber(scrapNumber);
			scrapDataRequest.setProductPartCode(request.getPartCode());
			scrapDataRequest.setBussNumber(models.getFirst().getContractNumber());
			scrapDataRequest.setScrapNum(request.getScrapNum());
			scrapDataService.scrapDataSave(scrapDataRequest);
		}

		outStockApplyMapper.insert(apply);

	}

	/**
	 * @description 在途量
	 *
	 * @author zhanglijia
	 * @organization futurecraftsmen
	 * @date Created in 2025/7/16 10:47
	 * @department: Product development
	 */
	public Map<Long, CollaboratePurchaseContractInfoRpcDTO> getOnWayNumMap(List<Long> productPartCodeList) throws ExceptionPack {
		if (productPartCodeList == null || productPartCodeList.isEmpty()) {
			return new HashMap<>();
		}

		ProductMaterialRpcRequest materialRpcRequest = new ProductMaterialRpcRequest();
		materialRpcRequest.setProductPartCodeList(productPartCodeList);


		List<ProductTransitRpcDTO> newTransitRpcDTOS = selfReceivingService.getNewPurchaseDetailList(materialRpcRequest);

		// 避免 null 引发 NPE
		if (newTransitRpcDTOS == null || newTransitRpcDTOS.isEmpty()) {
			return new HashMap<>();
		}

		// 初始化已入库量映射，预设容量以提高性能
		Map<Long, BigDecimal> inboundQuantityNumMap = new HashMap<>((int) (productPartCodeList.size() / 0.75f) + 1);

		if(CollUtil.isNotEmpty(newTransitRpcDTOS)){
			// 根据采购合同查询对应的已入库量
			List<Long> purchaseContractCodeList = newTransitRpcDTOS.stream().map(ProductTransitRpcDTO::getPurchaseContractCode).distinct().toList();
			List<Long> productPartCodeLists = newTransitRpcDTOS.stream().map(ProductTransitRpcDTO::getProductPartCode).distinct().toList();
			List<CollaborateInboundPageRpcDTO> purchaseContractProductInQualified = getPurchaseContractProductInQualified(purchaseContractCodeList, productPartCodeLists);
			// 将purchaseContractProductInQualified转换成Map，每个产品零件code的数据作为key，sum对应数据的已入库量
			for (CollaborateInboundPageRpcDTO dto : purchaseContractProductInQualified) {
				BigDecimal inboundQuantity = dto.getInboundQuantity() != null ? dto.getInboundQuantity() : BigDecimal.ZERO;
				inboundQuantityNumMap.merge(dto.getCollaborateMaterial(), inboundQuantity, BigDecimal::add);
			}
		}


		// 初始化在途量映射，预设容量以提高性能
		Map<Long, BigDecimal> productPartCodeToOnWayNumMap = new HashMap<>((int) (productPartCodeList.size() / 0.75f) + 1);
		// 提取在途量数据
		for (ProductTransitRpcDTO dto : newTransitRpcDTOS) {
			// 如果onWayNum为null，则设置为BigDecimal.ZERO
			BigDecimal onWayNum = dto.getOnWayNum() != null ? dto.getOnWayNum() : BigDecimal.ZERO;
			// 移除了大于0的限制，处理所有值
			productPartCodeToOnWayNumMap.merge(dto.getProductPartCode(), onWayNum, BigDecimal::add);
		}

		// 初始化签收量映射，预设容量以提高性能
		Map<Long, BigDecimal> receivingNumMap = new HashMap<>((int) (productPartCodeList.size() / 0.75f) + 1);
		// 提取签收量数据
		for (ProductTransitRpcDTO dto : newTransitRpcDTOS) {
			// 如果receivedQuantityNum为null，则设置为BigDecimal.ZERO
			BigDecimal receivedQuantityNum = dto.getReceivedQuantity() != null ? dto.getReceivedQuantity() : BigDecimal.ZERO;
			// 移除了大于0的限制，处理所有值
			receivingNumMap.merge(dto.getProductPartCode(), receivedQuantityNum, BigDecimal::add);
		}

		// 构建最终结果映射
		Map<Long, CollaboratePurchaseContractInfoRpcDTO> resultMap = new HashMap<>((int) (productPartCodeList.size() / 0.75f) + 1);
		productPartCodeToOnWayNumMap.forEach((productPartCode, onWayNum) -> {
			CollaboratePurchaseContractInfoRpcDTO rpcDTO = new CollaboratePurchaseContractInfoRpcDTO();
			rpcDTO.setOnWayNum(onWayNum);
			//通过已签收量 - 已入库量
			BigDecimal receivingNum = receivingNumMap.getOrDefault(productPartCode, BigDecimal.ZERO);
			BigDecimal inboundQuantityNum = inboundQuantityNumMap.getOrDefault(productPartCode, BigDecimal.ZERO);
			if(receivingNum.compareTo(inboundQuantityNum) > 0){
				rpcDTO.setReceivingNoIntoStockNum(receivingNum.subtract(inboundQuantityNum));
			}else {
				rpcDTO.setReceivingNoIntoStockNum(BigDecimal.ZERO);
			}
			resultMap.put(productPartCode, rpcDTO);
		});
		return resultMap;
	}

	private List<CollaborateInboundPageRpcDTO> getPurchaseContractProductInQualified(List<Long> purchaseContractCodes, List<Long> productCodes) throws ExceptionPack {
		try {
			//企业编号
			Long enterpriseCode = InfoPenetrateProcessor.INSTANCE
					.getPenetrateInfoNotNull(GlobalTokenPenetrate.ENTERPRISE_CODE_THREAD_LOCAL);
			LambdaQueryWrapper<CollaborateInboundItemModel> queryWrapper = new LambdaQueryWrapper<>();
			queryWrapper.eq(CollaborateInboundItemModel::getEnterpriseCode,enterpriseCode);
			queryWrapper.in(CollaborateInboundItemModel::getContractCode,purchaseContractCodes);
			queryWrapper.in(CollaborateInboundItemModel::getCollaborateMaterial,productCodes);
			queryWrapper.eq(CollaborateInboundItemModel::getContractType, 1);
			List<CollaborateInboundItemModel> list = collaborateInboundItemMapper.selectList(queryWrapper);
			if(CollUtil.isEmpty(list)){
				return List.of();
			}
			return Convert.toList(CollaborateInboundPageRpcDTO.class, list);

		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("fail to getPurchaseContractProductInQualified").build());
		}
	}

	@Override
	public List<CollaborateBatchProduceMaterialDTO> materialList(CollaborateProduceRequest request) throws ExceptionPack {
		try {
			LambdaQueryWrapper<CollaborateBatchProduceMaterialModel> queryWrapper = Wrappers.lambdaQuery();
			queryWrapper.eq(CollaborateBatchProduceMaterialModel::getContractCode, request.getContractCode());
			queryWrapper.eq(CollaborateBatchProduceMaterialModel::getDeleteFlag, false);
			queryWrapper.eq(CollaborateBatchProduceMaterialModel::getEnterpriseCode, technicalTableNameProxyImpl.getEnterpriseCode());
			List<CollaborateBatchProduceMaterialModel> materialModels = collaborateBatchProduceMaterialMapper.selectList(queryWrapper);

			//防止垃圾数据导致接口出错
			materialModels = materialModels.stream().filter(a -> a.getProduceMaterial() != null).collect(Collectors.toList());

			if (CollectionUtils.isEmpty(materialModels)) {
				return Collections.emptyList();
			}

			List<CollaborateBatchProduceMaterialDTO> list = Convert.toList(CollaborateBatchProduceMaterialDTO.class, materialModels);

			for (CollaborateBatchProduceMaterialDTO materialDTO : list) {
				if (materialDTO.getNature() == null) {
					materialDTO.setNature(MaterialNatureEnum.PRODUCE);
				}
			}

			Map<Long, CollaboratePurchaseContractInfoRpcDTO> wayNumMap = getOnWayNumMap(list.stream().map(a -> a.getProduceMaterial()).toList());
			for (CollaborateBatchProduceMaterialDTO dto : list) {
				dto.setPartInfo(iProductPartService.queryPartsById(dto.getProduceMaterial()));
				try {
					List<ProcedureSimpleInfo> infos = JSON.parseArray(dto.getProduceInfo(), ProcedureSimpleInfo.class);
					List<Long> produceCode = infos.stream().map(a -> a.getProduceCode()).toList();
					List<ProductPartProcedureRpcDTO> rpcDTOS = iProductPartProcedureService.queryListByProcedureIdIn(produceCode);
					dto.setProduceNames(rpcDTOS.stream().map(a -> a.getProductPartProcedureSign()).toList());
				} catch (Exception e) {
					log.error(dto.getProduceInfo() + "数据存在问题", e);
				}
				if (ObjectUtil.isNotEmpty(wayNumMap.getOrDefault(dto.getProduceMaterial(), null))) {
					dto.setInWayNum(wayNumMap.getOrDefault(dto.getProduceMaterial(), null).getOnWayNum());
					dto.setReceivingNoIntoStockNum(wayNumMap.getOrDefault(dto.getProduceMaterial(), null).getReceivingNoIntoStockNum());
				} else {
					dto.setInWayNum(BigDecimal.ZERO);
					dto.setReceivingNoIntoStockNum(BigDecimal.ZERO);
				}
			}

			CollaborateStockInfoRpcRequest requestData = new CollaborateStockInfoRpcRequest();
			requestData.setContractCode(request.getContractCode());
			requestData.setProductPartCodes(list.stream().map(a -> a.getProduceMaterial()).filter(a -> a != null).distinct().toList());

			Map<Long, CollaborateStockInfoRpcDTO> decimalMap = collaborateOutboundService.getStockInfo(requestData).stream()
					.collect(Collectors
							.toMap(a -> a.getProductPartCode(), a -> a,
									(a, b) -> a));

			for (CollaborateBatchProduceMaterialDTO dto : list) {
				dto.setStockNum(decimalMap.getOrDefault(dto.getProduceMaterial(), new CollaborateStockInfoRpcDTO()).getCanUseNum());
				dto.setHasOtherContractNeed(decimalMap.getOrDefault(dto.getProduceMaterial(), new CollaborateStockInfoRpcDTO()).getHasOtherContractNeed());
			}

			/**
			 * 形成树形结构
			 */
			Map<Long, List<CollaborateBatchProduceMaterialDTO>> parentCode =
					list.stream().collect(Collectors.groupingBy(a -> Optional.ofNullable(a.getParentMaterial()).orElse(0l)));
			Map<Long, List<CollaborateBatchProduceMaterialDTO>> currentCode = list.stream().collect(Collectors.groupingBy(a -> a.getProduceMaterial()));

			for (Map.Entry<Long, List<CollaborateBatchProduceMaterialDTO>> entry : currentCode.entrySet()) {
				CollaborateBatchProduceMaterialDTO materialDTO = entry.getValue().stream()
						.filter(a -> a.getParentMaterial() == null || a.getParentMaterial() == 0)
						.findFirst()
						.orElse(entry.getValue().getFirst());
				entry.getValue().clear();
				entry.getValue().add(materialDTO);
			}

			list = parentCode.entrySet().stream().filter(a -> a.getKey() == null || a.getKey() == 0).flatMap(a -> a.getValue().stream()).toList();
			parentCode.entrySet().stream().filter(a -> a.getKey() != null && a.getKey() > 0).flatMap(a -> a.getValue().stream())
					.forEach(a -> {

						List<CollaborateBatchProduceMaterialDTO> codeOrDefault = currentCode.getOrDefault(a.getParentMaterial(), new ArrayList<>());
						for (CollaborateBatchProduceMaterialDTO materialDTO : codeOrDefault) {
							if (CollectionUtils.isEmpty(materialDTO.getChildren())) {
								materialDTO.setChildren(new ArrayList<>());
							}
							materialDTO.getChildren().add(a);
						}
					});

			/**
			 * 需求量查询
			 */
			LambdaQueryWrapper<CollaborateScheduleItemModel> queryItemWrapper = Wrappers.lambdaQuery();
			queryItemWrapper.eq(CollaborateScheduleItemModel::getContractCode, request.getContractCode());
			queryItemWrapper.eq(CollaborateScheduleItemModel::getDeleteFlag, false);
			queryItemWrapper.eq(CollaborateScheduleItemModel::getEnterpriseCode, technicalTableNameProxyImpl.getEnterpriseCode());
			queryItemWrapper.eq(CollaborateScheduleItemModel::getCollaborateType, 1);
			Map<String, CollaborateScheduleItemModel> needMap = collaborateScheduleItemMapper.selectList(queryItemWrapper).stream()
					.collect(Collectors.toMap(a -> a.mergeKey(), a -> a,
							(a, b) -> a));

			//收货物料需求量
			CollaborateContractReceiveMaterials collaborateContractReceiveMaterials = collaborateReceiveMaterialInnerService.queryContractReceiveMaterials(request.getContractCode());


			for (CollaborateBatchProduceMaterialDTO materialDTO : list) {
				CollaborateScheduleItemModel model = needMap.getOrDefault(materialDTO.mergeKeyForScheduleItem(), new CollaborateScheduleItemModel());
				materialDTO.setNeedNum(Optional.ofNullable(model.getOrderQuantity()).orElse(BigDecimal.ZERO)
						.subtract(Optional.ofNullable(model.getScheduleQuantity()).orElse(BigDecimal.ZERO)));

				if (materialDTO.getNeedNum().compareTo(BigDecimal.ZERO) < 0) {
					materialDTO.setNeedNum(BigDecimal.ZERO);
				}

				//收货物料的需求量单独设置
				if (materialDTO.getNature() == MaterialNatureEnum.PRODUCE_RECEIVE_MATERIAL) {
					CollaborateContractReceiveMaterial collaborateContractReceiveMaterial =
							collaborateContractReceiveMaterials.findReceiveMaterial(materialDTO.getProduceMaterial(), false);
					if (collaborateContractReceiveMaterial != null) {
						materialDTO.setNeedNum(collaborateContractReceiveMaterial.receiveMaterialDemand());
					}

				}

				materialDTO.setContractNumber(model.getContractNumber());

			}

			/**
			 * 中转区
			 */
			LambdaQueryWrapper<StagingArea> areaWrapper = Wrappers.lambdaQuery();
			areaWrapper.eq(StagingArea::getContractCode, request.getContractCode());
			areaWrapper.eq(StagingArea::getDeleteFlag, false);
			areaWrapper.in(StagingArea::getAreaType, List.of(0, 1, 3));
			areaWrapper.eq(StagingArea::getEnterpriseCode, technicalTableNameProxyImpl.getEnterpriseCode());

			List<StagingArea> stagingAreas = stagingAreaMapper.selectList(areaWrapper);

			for (StagingArea area : stagingAreas) {
				area.setOperatorName(staffService.getStaffCode(area.getOperator()).getStaffName());
			}


			//分为 生产用物料 以及 外发用物料 以及 收货物料

			List<CollaborateBatchProduceMaterialDTO> materialsForProduce =
					list.stream().filter(p -> p.getNature() == null || p.getNature() == MaterialNatureEnum.PRODUCE).collect(Collectors.toList());


			List<CollaborateBatchProduceMaterialDTO> materialsForOutSend = list.stream().filter(p -> p.getNature() == MaterialNatureEnum.OUT_SEND).collect(Collectors.toList());

			List<CollaborateBatchProduceMaterialDTO> materialsForProduceReceive = list.stream().filter(p -> p.getNature() == MaterialNatureEnum.PRODUCE_RECEIVE_MATERIAL).collect(Collectors.toList());


			//收货物料相关的外发物料编号
			Set<Long> outSendMaterialsForReceiveMaterials = collaborateContractReceiveMaterials.outSendMaterials(materialsForProduceReceive.stream().map(CollaborateBatchProduceMaterialDTO::getProduceMaterial).distinct().toList());

			/**
			 * 已领量
			 */
			LambdaQueryWrapper<CollaborateProducePick> pickWrapper = Wrappers.lambdaQuery();
			pickWrapper.eq(CollaborateProducePick::getContractCode, request.getContractCode());
			pickWrapper.eq(CollaborateProducePick::getDeleteFlag, false);
			pickWrapper.eq(CollaborateProducePick::getEnterpriseCode, technicalTableNameProxyImpl.getEnterpriseCode());
			List<CollaborateProducePick> picks = collaborateProducePickMapper.selectList(pickWrapper);

			for (CollaborateProducePick pick : picks) {
				pick.setOpUserName(staffService.getStaffCode(pick.getOpUserCode()).getStaffName());
			}

			//批次是否完成
			CollaborateScheduleItemModel batchFinish = collaborateScheduleItemMapper.batchFinish(request.getContractCode());


			// 生产用物料
			if (!materialsForProduce.isEmpty()) {
				List<StagingArea> stagingAreaForProduce = stagingAreas.stream().filter(p -> Objects.equals(p.getAreaType(), 0)).toList();

				CurrentAndParent<StagingArea> currentAndParent = wrapperCurrentAndParentInfo(stagingAreaForProduce, a -> a.getParentMaterial(),
						a -> a.getProductPartCode());


				CurrentAndParent<CollaborateProducePick> pickCP = wrapperCurrentAndParentInfo(picks, a -> a.getParenPartCode(),
						a -> a.getPartCode());

				/**
				 * 补料
				 */
				List<CollaborateProduceExtend> anExtends = collaborateProduceExtendMapper.selectCountByPart(request.getContractCode());

				Map<Long, CollaborateProduceExtend> parentExtend =
						anExtends.stream()
								.filter(a -> a.getParentPartCode() == null || a.getParentPartCode() == 0)
								.collect(Collectors.toMap(a -> a.getPartCode(), a -> a));

				Map<Long, CollaborateProduceExtend> childExtend = anExtends.stream()
						.filter(a -> a.getParentPartCode() != null && a.getParentPartCode() > 0)
						.collect(Collectors.toMap(a -> a.getPartCode(), a -> a));


				AtomicInteger count = new AtomicInteger();
				count.set(0);

				for (CollaborateBatchProduceMaterialDTO materialDTO : materialsForProduce) {
					materialDTO.setAreaList(currentAndParent.getParentList(materialDTO.getProduceMaterial()));
					materialDTO.setAreaNum(currentAndParent.getParentSum(materialDTO.getProduceMaterial(), b -> b.getOutNum()));


					materialDTO.setPickList(pickCP.getParentList(materialDTO.getProduceMaterial()));
					materialDTO.setPickNum(pickCP.getParentSum(materialDTO.getProduceMaterial(), a -> a.getPickNum()));
					materialDTO.setExtendNum(parentExtend.getOrDefault(materialDTO.getProduceMaterial(), new CollaborateProduceExtend()).getApplyNum());

					if (batchFinish == null) {
						materialDTO.setStatusName("-");
					} else {
						if (CollectionUtils.isNotEmpty(materialDTO.getChildren())) {
							materialDTO.setStatusName("待装配");
						} else if (materialDTO.getAreaNum().compareTo(BigDecimal.ZERO) > 0) {
							materialDTO.setStatusName("已出库");
						} else {
							materialDTO.setStatusName("待配料");
						}
					}

					if (materialDTO.getAreaNum().compareTo(BigDecimal.ZERO) > 0) {

						if (materialDTO.getAreaNum().compareTo(materialDTO.getPickNum()) > 0) {
							materialDTO.setPickButton(true);
							count.incrementAndGet();
						} else {
							materialDTO.setExtendButton(true);
						}
					}

					transMaterialList(materialDTO.getChildren(), currentAndParent, pickCP, childExtend, batchFinish, materialDTO.getPartInfo(), count);

				}

				for (CollaborateBatchProduceMaterialDTO materialDTO : materialsForProduce) {
					if (count.get() > 0) {
						materialDTO.setOnePickButton(true);
					}
				}
			}

			//外发用物料
			if (!materialsForOutSend.isEmpty()) {

				// 协作安排-装配-物料状态-收货物料出库后，相关外发物料不展示
				materialsForOutSend = materialsForOutSend.stream().filter(p -> !outSendMaterialsForReceiveMaterials.contains(p.getProduceMaterial())).toList();

				List<StagingArea> stagingAreaForOutSend = stagingAreas.stream().filter(p -> Objects.equals(p.getAreaType(), 1)).toList();

				CurrentAndParent<StagingArea> currentAndParent = wrapperCurrentAndParentInfo(stagingAreaForOutSend, a -> a.getParentMaterial(),
						a -> a.getProductPartCode());


				for (CollaborateBatchProduceMaterialDTO materialDTO : materialsForOutSend) {
					materialDTO.setAreaList(currentAndParent.getParentList(materialDTO.getProduceMaterial()));
					materialDTO.setAreaNum(currentAndParent.getParentSum(materialDTO.getProduceMaterial(), b -> b.getOutNum()));

					materialDTO.setStatusName("委外加工");

					materialDTO.setPickButton(false);
					materialDTO.setExtendButton(false);

					transMaterialForOutSendList(materialDTO.getChildren(), currentAndParent, materialDTO.getPartInfo());

				}

//			for (CollaborateBatchProduceMaterialDTO materialDTO : materialsForProduce) {
//				if (count.get() > 0) {
//					materialDTO.setOnePickButton(true); //todo 一键领料
//				}
//			}
			}


			// 收货物料
			if (!materialsForProduceReceive.isEmpty()) {
				List<StagingArea> stagingAreaForProduceReceive = stagingAreas.stream().filter(p -> Objects.equals(p.getAreaType(), 3)).toList();

				CurrentAndParent<StagingArea> currentAndParent = wrapperCurrentAndParentInfo(stagingAreaForProduceReceive, a -> a.getParentMaterial(),
						a -> a.getProductPartCode());


				CurrentAndParent<CollaborateProducePick> pickCP = wrapperCurrentAndParentInfo(picks, a -> a.getParenPartCode(),
						a -> a.getPartCode());

//				/**
//				 * 补料
//				 */
//				List<CollaborateProduceExtend> anExtends = collaborateProduceExtendMapper.selectCountByPart(request.getContractCode());

				Map<Long, CollaborateProduceExtend> parentExtend = new HashMap<>();

				Map<Long, CollaborateProduceExtend> childExtend = new HashMap<>();


				AtomicInteger count = new AtomicInteger();
				count.set(0);

				for (CollaborateBatchProduceMaterialDTO materialDTO : materialsForProduceReceive) {
					materialDTO.setAreaList(currentAndParent.getParentList(materialDTO.getProduceMaterial()));
					materialDTO.setAreaNum(currentAndParent.getParentSum(materialDTO.getProduceMaterial(), b -> b.getOutNum()));


					materialDTO.setPickList(pickCP.getParentList(materialDTO.getProduceMaterial()));
					materialDTO.setPickNum(pickCP.getParentSum(materialDTO.getProduceMaterial(), a -> a.getPickNum()));
					materialDTO.setExtendNum(parentExtend.getOrDefault(materialDTO.getProduceMaterial(), new CollaborateProduceExtend()).getApplyNum());

					if (batchFinish == null) {
						materialDTO.setStatusName("-");
					} else {
						if (CollectionUtils.isNotEmpty(materialDTO.getChildren())) {
							materialDTO.setStatusName("待装配");
						} else if (materialDTO.getAreaNum().compareTo(BigDecimal.ZERO) > 0) {
							materialDTO.setStatusName("已出库");
						} else {
							materialDTO.setStatusName("待配料");
						}
					}

					if (materialDTO.getAreaNum().compareTo(BigDecimal.ZERO) > 0) {

						if (materialDTO.getAreaNum().compareTo(materialDTO.getPickNum()) > 0) {
							materialDTO.setPickButton(true);
							count.incrementAndGet();
						} else {
							materialDTO.setExtendButton(true);
						}
					}

					transMaterialList(materialDTO.getChildren(), currentAndParent, pickCP, childExtend, batchFinish, materialDTO.getPartInfo(), count);

				}

				for (CollaborateBatchProduceMaterialDTO materialDTO : materialsForProduceReceive) {
					if (count.get() > 0) {
						materialDTO.setOnePickButton(true);
					}
				}
			}

			List<CollaborateBatchProduceMaterialDTO> result = new ArrayList<>();
			result.addAll(materialsForProduce);
			result.addAll(materialsForOutSend);
			result.addAll(materialsForProduceReceive);


			for (CollaborateBatchProduceMaterialDTO materialDTO : result) {
				childNeedNum(materialDTO);
			}

			return result;
		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("fail to materialList").build());
		}
	}

	private void childNeedNum(CollaborateBatchProduceMaterialDTO materialDTO) throws ExceptionPack {

		if (CollectionUtils.isEmpty(materialDTO.getChildren())) {
			return;
		}

		MaterialBomMaterialRecorder materialBomMaterialRecorder =
				productBomQueryService.materialBomMaterials(materialDTO.getProduceMaterial());
		if (materialBomMaterialRecorder == null || CollectionUtils.isEmpty(materialBomMaterialRecorder.getMaterialInfos())) {
			return;
		}
		List<MaterialSimpleInfo> subMaterialInfos = materialBomMaterialRecorder.getMaterialInfos();

		Map<Long, List<MaterialSimpleInfo>> listMap = subMaterialInfos.stream().collect(Collectors.groupingBy(a -> a.getMaterialCode()));

		Map<Long, BigDecimal> peiBi = new HashMap<>();
		for (Map.Entry<Long, List<MaterialSimpleInfo>> entry : listMap.entrySet()) {
			peiBi.put(entry.getKey(),
					entry.getValue().stream().map(a -> a.getFactor())
							.filter(a -> a != null)
							.reduce(BigDecimal::add).orElse(BigDecimal.ZERO));
		}

		for (CollaborateBatchProduceMaterialDTO child : materialDTO.getChildren()) {
			child.setNeedNum(PrecisionUtils.multiplyNumber(
					peiBi.getOrDefault(child.getProduceMaterial(), BigDecimal.ONE),
					materialDTO.getNeedNum()));
			childNeedNum(child);
		}
	}

	/**
	 * @description 递归处理
	 *
	 * @author zhanglijia
	 * @organization futurecraftsmen
	 * @date Created in 2025/7/15 16:31
	 * @department: Product development
	 */
	private void transMaterialList(List<CollaborateBatchProduceMaterialDTO> list,
	                               CurrentAndParent<StagingArea> currentAndParent, CurrentAndParent<CollaborateProducePick> pickCP,
	                               Map<Long, CollaborateProduceExtend> childExtend, CollaborateScheduleItemModel itemModel,
	                               ProductPartRpcDTO partInfo, AtomicInteger count) {

		if (CollectionUtils.isNotEmpty(list)) {
			for (CollaborateBatchProduceMaterialDTO child : list) {

				child.setAreaList(currentAndParent.getChildList(child.getParentMaterial(), child.getProduceMaterial()));
				child.setAreaNum(currentAndParent.getChildListSum(child.getParentMaterial(), child.getProduceMaterial(), a -> a.getOutNum()));
				child.setParentPart(partInfo.getProductPartCode());

				if (child.getNature() == MaterialNatureEnum.OUT_SEND) {
					child.setPickList(new ArrayList<>());
					child.setPickNum(null);
					child.setExtendNum(null);
					child.setStatusName("委外加工");
					child.setPickButton(false);
					child.setExtendButton(false);
					continue;
				}

				child.setPickList(pickCP.getChildList(child.getParentMaterial(), child.getProduceMaterial()));
				child.setPickNum(pickCP.getChildListSum(child.getParentMaterial(), child.getProduceMaterial(), a -> a.getPickNum()));
				child.setExtendNum(childExtend.getOrDefault(child.getProduceMaterial(), new CollaborateProduceExtend()).getApplyNum());

				if (itemModel == null) {
					child.setStatusName("-");
				} else {
					if (CollectionUtils.isNotEmpty(child.getChildren())) {
						child.setStatusName("待装配");
					} else {
						child.setStatusName("已出库");
					}
				}

				if (child.getAreaNum().compareTo(child.getPickNum()) > 0) {
					child.setPickButton(true);
					count.incrementAndGet();
				} else {
					child.setExtendButton(true);
				}

				transMaterialList(child.getChildren(), currentAndParent, pickCP, childExtend, itemModel, child.getPartInfo(), count);
			}
		}
	}


	/**
	 * @description 递归处理外发用物料
	 * @author zhanglijia
	 * @organization futurecraftsmen
	 * @date Created in 2025/7/15 16:31
	 * @department: Product development
	 */
	private void transMaterialForOutSendList(List<CollaborateBatchProduceMaterialDTO> list,
	                                         CurrentAndParent<StagingArea> currentAndParent,
	                                         ProductPartRpcDTO partInfo) {

		if (CollectionUtils.isNotEmpty(list)) {
			for (CollaborateBatchProduceMaterialDTO child : list) {

				child.setAreaList(currentAndParent.getChildList(child.getParentMaterial(), child.getProduceMaterial()));
				child.setAreaNum(currentAndParent.getChildListSum(child.getParentMaterial(), child.getProduceMaterial(), a -> a.getOutNum()));
				child.setParentPart(partInfo.getProductPartCode());
				child.setStatusName("委外加工");

				child.setPickButton(false);
				child.setExtendButton(false);

				transMaterialForOutSendList(child.getChildren(), currentAndParent, child.getPartInfo());
			}
		}
	}


	/**
	 * @description 父子引用包装
	 *
	 * @author zhanglijia
	 * @organization futurecraftsmen
	 * @date Created in 2025/7/15 15:07
	 * @department: Product development
	 */
	private <T> CurrentAndParent<T> wrapperCurrentAndParentInfo(List<T> list, Function<T, Long> parent, Function<T, Long> child) {

		Map<Long, List<T>> parentArea = list.stream()
				.filter(a -> parent.apply(a) == null || parent.apply(a) == 0)
				.collect(Collectors.groupingBy(a -> child.apply(a)));
		Map<Long, Map<Long, List<T>>> childArea = new HashMap<>();
		list.stream().filter(a -> parent.apply(a) != null && parent.apply(a) > 0).forEach(a -> {
			childArea.computeIfAbsent(parent.apply(a), b -> new HashMap<>()).computeIfAbsent(child.apply(a), b -> new ArrayList<>()).add(a);
		});
		CurrentAndParent<T> cap = new CurrentAndParent<>();
		cap.parent = parentArea;
		cap.child = childArea;
		return cap;
	}

	/**
	 * @description 父子包装类
	 *
	 * @author zhanglijia
	 * @organization futurecraftsmen
	 * @date Created in 2025/7/15 15:07
	 * @department: Product development
	 */
	private class CurrentAndParent<ITEM> {

		private Map<Long, List<ITEM>> parent;

		private Map<Long, Map<Long, List<ITEM>>> child;

		public List<ITEM> getParentList(Long key) {
			return parent.getOrDefault(key, new ArrayList<>());
		}

		public BigDecimal getParentSum(Long key, Function<ITEM, BigDecimal> numF) {
			return parent.getOrDefault(key, new ArrayList<>()).stream().map(a -> numF.apply(a))
					.reduce(BigDecimal.ZERO,
							(a, b) -> Optional.ofNullable(a).orElse(BigDecimal.ZERO).add(Optional.ofNullable(b).orElse(BigDecimal.ZERO)));
		}

		public List<ITEM> getChildList(Long keyParent, Long key) {
			return child.getOrDefault(keyParent, new HashMap<>()).getOrDefault(key, new ArrayList<>());
		}

		public BigDecimal getChildListSum(Long keyParent, Long key, Function<ITEM, BigDecimal> numF) {
			return getChildList(keyParent, key).stream().map(a -> numF.apply(a))
					.reduce(BigDecimal.ZERO,
							(a, b) -> Optional.ofNullable(a).orElse(BigDecimal.ZERO).add(Optional.ofNullable(b).orElse(BigDecimal.ZERO)));
		}
	}

	@Override
	public RpcPagingDTO<CollaborateScheduleItemDTO> batchList(CollaborateProduceRequest request) throws ExceptionPack {

		Page<?> page = new Page<>(request.getCurrent(), request.getSize());

		PostPageFunctionScopeRpcDTO functionScopeRpcDTO =
				functionScopeService.currentStaffPageFunctionScope(CustomPageEnum.PRODUCE_MANAGE_NEW.getPageCode(), 620111L);

		if (functionScopeRpcDTO == null || functionScopeRpcDTO.isTempDeptManager()) {
			request.setDisRight(true);
		}

		if (functionScopeRpcDTO == null || !functionScopeRpcDTO.getFunctions().stream().filter(a -> "dis".equals(a.getFunctionKey())).findFirst().isEmpty()) {
			request.setDis(true);
			request.setDisRight(true);
		}

		boolean flagDis = false;

		if (functionScopeRpcDTO == null || !functionScopeRpcDTO.getFunctions().stream()
				.filter(a -> "disAndReport".equals(a.getFunctionKey()))
				.findFirst().isEmpty()) {

			flagDis = true;
		}

		if (request.getDataProcess() != null && !request.getDataProcess().isEmpty()) {
			List<ProcessEnum> processConditions = new ArrayList<>();
			for (ProcessEnum processEnum : request.getDataProcess()) {
				processConditions.add(processEnum);
				if (processEnum.getBindProcess() != null) {
					processConditions.add(processEnum.getBindProcess());
				}
			}
			request.setDataProcess(processConditions);
		}


		List<CollaborateScheduleItemModel> list = collaborateScheduleItemMapper.selectBatchList(page, request);
		List<CollaborateScheduleItemDTO> result = Convert.toList(CollaborateScheduleItemDTO.class, list);

		Map<Long, List<CollaborateScheduleItemDTO>> cache = new HashMap<>();

		for (CollaborateScheduleItemDTO dto : result) {
			LambdaQueryWrapper<CollaborateScheduleItemModel> wrapper = Wrappers.lambdaQuery();
			wrapper.eq(CollaborateScheduleItemModel::getContractCode, dto.getContractCode());
			wrapper.eq(CollaborateScheduleItemModel::getCollaborateType, 0);
			wrapper.eq(CollaborateScheduleItemModel::getDeleteFlag, false);
			wrapper.eq(CollaborateScheduleItemModel::getEnterpriseCode, technicalTableNameProxyImpl.getEnterpriseCode());
			wrapper.eq(CollaborateScheduleItemModel::isCanProduce, true);
			List<CollaborateScheduleItemModel> itemModels = collaborateScheduleItemMapper.selectList(wrapper);

			boolean empty = itemModels.stream().filter(a -> a.isScheduleFlag()).findAny().isEmpty();
			if (empty) {
				dto.setBackButton(true);
			}

			List<CanReturnInfo> infos = collaborateScheduleItemMapper.canReturnInfo(Collections.singletonList(dto.getContractCode()),
					technicalTableNameProxyImpl.getEnterpriseCode());

			if (CollectionUtils.isNotEmpty(infos)) {
				dto.setBackButton(infos.getFirst().isCanReturn());
			}

			for (CollaborateScheduleItemModel model : itemModels) {
				if (request.isDis()) {
					if (Optional.ofNullable(model.getOrderQuantity()).orElse(BigDecimal.ZERO).compareTo(Optional.ofNullable(model.getScheduleQuantity()).orElse(BigDecimal.ZERO)) > 0) {
						dto.setDisButton(true);
						dto.setDisAndReport(true);
						break;
					}
				}
			}

			for (CollaborateScheduleItemModel model : itemModels) {
				if (flagDis) {
					if (Optional.ofNullable(model.getOrderQuantity()).orElse(BigDecimal.ZERO).compareTo(Optional.ofNullable(model.getScheduleQuantity()).orElse(BigDecimal.ZERO)) > 0) {
						dto.setDisAndReport(true);
						break;
					}
				}
			}

			if (flagDis && !dto.isDisAndReport()) {
				List<CollaborateProduceHandle> handles = collaborateProduceHandleMapper.queryFinishCount(dto.getContractCode());
				if (CollectionUtils.isNotEmpty(handles)) {
					dto.setDisAndReport(true);
				}

			}

			LambdaQueryWrapper<CollaborateProduceDistribute> disWrapper = Wrappers.lambdaQuery();
			disWrapper.eq(CollaborateProduceDistribute::getBussNumber, dto.getContractNumber());
			disWrapper.eq(CollaborateProduceDistribute::getDeleteFlag, false);
			disWrapper.eq(CollaborateProduceDistribute::getEnterpriseCode, technicalTableNameProxyImpl.getEnterpriseCode());

			List<CollaborateProduceDistribute> distributes = collaborateProduceDistributeMapper.selectList(disWrapper);
			empty = distributes.stream().filter(a -> technicalTableNameProxyImpl.getCurrentCode().equals(a.getManageUserCode())).findAny().isEmpty();
			if (!empty) {
				List<CollaborateProduceHandle> handles = collaborateProduceHandleMapper.checkReportNum(null, dto.getContractCode());
				if (CollectionUtils.isEmpty(handles)) {
					dto.setReportButton(true);
				}

				for (CollaborateProduceHandle handle : handles) {
					if (handle.getTaskNum().compareTo(handle.getFinishNum()) > 0) {
						dto.setReportButton(true);
					}

					if (handle.getReportShow() != null && handle.getReportShow()) {
						dto.setReportButton(true);
					}
				}

			}

			SellOrderModel sellOrderModel = iSellOrderMapper.queryBySellOrderCode(dto.getSellOrderCode(),
					technicalTableNameProxyImpl.getEnterpriseCode(), technicalTableNameProxyImpl.getSellOrder());
			dto.setProcessEnum(sellOrderModel.getCollaborateLatestProcess());
			dto.setRemark(sellOrderModel.getContractRemark());
			cache.computeIfAbsent(dto.getSellOrderCode(), a -> new ArrayList<>()).add(dto);

			/**
			 * 中转区
			 */
			LambdaQueryWrapper<StagingArea> areaWrapper = Wrappers.lambdaQuery();
			areaWrapper.eq(StagingArea::getContractCode, dto.getContractCode());
			areaWrapper.eq(StagingArea::getDeleteFlag, false);
			areaWrapper.eq(StagingArea::getAreaType, 2);
			areaWrapper.eq(StagingArea::getEnterpriseCode, technicalTableNameProxyImpl.getEnterpriseCode());
			Map<Long, List<StagingArea>> productMap =
					stagingAreaMapper.selectList(areaWrapper).stream().collect(Collectors.groupingBy(a -> a.getProductPartCode()));

			boolean closeAll = true;

			for (CollaborateScheduleItemModel itemModel : itemModels) {
				BigDecimal bigDecimal = productMap.getOrDefault(itemModel.getCollaborateMaterial(), new ArrayList<>()).stream()
						.map(a -> a.getOutNum())
						.filter(a -> a != null)
						.reduce((a, b) -> a.add(b)).orElse(BigDecimal.ZERO);

				if (itemModel.getOrderQuantity().compareTo(bigDecimal) > 0) {
					closeAll = false;
					break;
				}
			}

			if (closeAll) {
				dto.setReportButton(false);
				dto.setDisAndReport(false);
				dto.setDisButton(false);
			}

		}

		Map<Long, List<SellOrderProductRpcDTO>> batchQuery = iSellOrderService.batchQuery(new ArrayList<>(cache.keySet()));

		for (Map.Entry<Long, List<SellOrderProductRpcDTO>> entry : batchQuery.entrySet()) {
			String deliveryOverview = ISellOrderService.sellOrderDeliveryOverview(entry.getValue());
			cache.getOrDefault(entry.getKey(), new ArrayList<>()).forEach(a -> {
				a.setDeliveryOverview(deliveryOverview);
				a.setDetails(entry.getValue());
				//a.setRemark(String.join(",", entry.getValue().stream().map(b -> b.getProductRemark()).filter(StringUtils::isNotBlank).toList()));
			});

		}
		/**
		 * 物料状态
		 */
		Map<Long, List<CollaborateCheckMaterialStatusDTO>> cmsMap =
				collaborateProcessService.checkMaterialStatus(result.stream()
								.map(a -> a.getSellOrderCode()).toList(), true)
						.stream()
						.collect(Collectors.groupingBy(a -> a.getBussNumber()));
		for (CollaborateScheduleItemDTO dto : result) {
			dto.setSatisfyState(cmsMap.getOrDefault(dto.getSellOrderCode(), new ArrayList<>())
					.stream().findFirst()
					.orElseGet(CollaborateCheckMaterialStatusDTO::new).getStatus());

			if (Objects.equals(dto.getProcessEnum(), ProcessEnum.ORDER_FINISH)) {
				dto.setSatisfyState(null);
			}
		}

		/**
		 * 气泡写入
		 */
		Map<Long, Boolean> longBooleanMap = bubbleList(result.stream().map(a -> a.getSellOrderCode()).toList(), CollaborateProcessReadInfo.PRODUCE);
		for (CollaborateScheduleItemDTO dto : result) {
			dto.setBubble(longBooleanMap.getOrDefault(dto.getSellOrderCode(), false));
		}



		return new RpcPagingDTO<>(result, page.getTotal());
	}

	@Override
	public List<CollaborateScheduleItemDTO> distributeList(CollaborateProduceRequest request) throws ExceptionPack {

		LambdaQueryWrapper<CollaborateScheduleItemModel> wrapper = Wrappers.lambdaQuery();
		wrapper.eq(CollaborateScheduleItemModel::getContractCode, request.getContractCode());
		wrapper.eq(CollaborateScheduleItemModel::getCollaborateType, 0);
		wrapper.eq(CollaborateScheduleItemModel::getDeleteFlag, false);
		wrapper.eq(CollaborateScheduleItemModel::getEnterpriseCode, technicalTableNameProxyImpl.getEnterpriseCode());
		wrapper.eq(CollaborateScheduleItemModel::isCanProduce, true);
		List<CollaborateScheduleItemModel> itemModels = collaborateScheduleItemMapper.selectList(wrapper);
		List<CollaborateScheduleItemDTO> resultList = Convert.toList(CollaborateScheduleItemDTO.class, itemModels);

		if (CollectionUtils.isEmpty(resultList)) {
			return Collections.emptyList();
		}

		if (!request.isAdmin()) {
			resultList =
					resultList.stream().filter(a -> a.getOrderQuantity()
							.compareTo(Optional.ofNullable(a.getScheduleQuantity())
									.orElse(BigDecimal.ZERO)) > 0).toList();

		}

		for (CollaborateScheduleItemDTO dto : resultList) {
			ProductPartRpcDTO partRpcDTO = iProductPartService.queryPartsById(dto.getCollaborateMaterial());
			dto.setPartInfo(partRpcDTO);
		}

		String batchNo = new SimpleDateFormat("yyMMdd").format(new Date());

		LambdaQueryWrapper<CollaborateProduceBatchInfoModel> batchNoWrapper = Wrappers.lambdaQuery();
		batchNoWrapper.eq(CollaborateProduceBatchInfoModel::getDeleteFlag, false);
		batchNoWrapper.eq(CollaborateProduceBatchInfoModel::getEnterpriseCode, technicalTableNameProxyImpl.getEnterpriseCode());
		batchNoWrapper.eq(CollaborateProduceBatchInfoModel::getContractCode, resultList.getFirst().getContractCode());
		List<CollaborateProduceBatchInfoModel> infoModels = collaborateProduceBatchInfoMapper.selectList(batchNoWrapper);

		boolean flag = true;
		if (CollectionUtils.isNotEmpty(infoModels)) {
			batchNo = infoModels.getFirst().getProduceBatchNumber();
			flag = false;
		}

		for (CollaborateScheduleItemDTO dto : resultList) {
			dto.setProduceBatchAllowUpdate(flag);
			dto.setProduceBatchNumber(batchNo);
		}

		CollaborateScheduleItemDTO dto = resultList.stream().findAny().get();

		Map<Long, List<SellOrderProductScheduleDetailRpcDTO>> listMap = iSellOrderService.querySellOrderProducts(dto.getSellOrderCode(),
				resultList.stream().map(a -> a.getCollaborateMaterial()).toList(), true);

		if (MapUtils.isEmpty(listMap)) {
			listMap = new HashMap<>();
		}

		for (CollaborateScheduleItemDTO itemDTO : resultList) {

			List<CodeMapName> codeMapNames = listMap.getOrDefault(itemDTO.getCollaborateMaterial(), new ArrayList<>()).stream()
					.collect(
							Collectors.toMap(a -> a.getProcessRoute(), a -> a.getProcessRouteName(), (a, b) -> a))
					.entrySet().stream()
					.map(a -> new CodeMapName(a.getKey(), a.getValue())).toList();
			itemDTO.setRouteInfos(codeMapNames);

			itemDTO.setPublicModel(listMap.getOrDefault(itemDTO.getCollaborateMaterial(), new ArrayList<>())
					.stream().map(a -> a.getPublicModel()).filter(a -> StringUtils.isNotBlank(a)).distinct().toList());

			itemDTO.setAwaitQuantity(itemDTO.getOrderQuantity().subtract(Optional.ofNullable(itemDTO.getScheduleQuantity()).orElse(BigDecimal.ZERO)));
		}

		return resultList;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void distributeData(CollaborateDistributeRequest request) throws Exception {

		for (CollaborateDistributeRequest.DistributeDetail detail : new ArrayList<>(request.getDistributeDetailList())) {
			if (!request.isAdmin() && Optional.ofNullable(detail.getNum()).orElse(BigDecimal.ZERO).compareTo(BigDecimal.ZERO) <= 0) {
				request.getDistributeDetailList().remove(detail);
			}
		}

		if (CollectionUtils.isEmpty(request.getDistributeDetailList())) {
			return;
		}

		LambdaQueryWrapper<CollaborateScheduleItemModel> wrapper = Wrappers.lambdaQuery();
		wrapper.in(CollaborateScheduleItemModel::getCollaborateMaterial, request.getDistributeDetailList().
				stream().map(a -> a.getCid()).toList());
		wrapper.in(CollaborateScheduleItemModel::getCollaborateType, 0);
		wrapper.eq(CollaborateScheduleItemModel::getContractCode, request.getContractCode());
		wrapper.eq(CollaborateScheduleItemModel::getEnterpriseCode, technicalTableNameProxyImpl.getEnterpriseCode());
		wrapper.eq(CollaborateScheduleItemModel::getDeleteFlag, false);

		Map<Long, List<CollaborateScheduleItemModel>> productMap = collaborateScheduleItemMapper.selectList(wrapper).stream()
				.collect(Collectors.groupingBy(a -> a.getCollaborateMaterial()));
		//资源扣减
		for (CollaborateDistributeRequest.DistributeDetail detail : request.getDistributeDetailList()) {

			collaborateScheduleItemMapper.useDeductionAwaitQuantity(collaborateScheduleItemMapper,
					productMap.get(detail.getCid()).getFirst().getCollaborateCode(), detail.getNum());

			/**
			 * 进度通知
			 */
			CollaborateProcessRecordRpcDTO collaborateProcessRecordRpcDTO = new CollaborateProcessRecordRpcDTO();
			collaborateProcessRecordRpcDTO.setCollaborateCode(productMap.get(detail.getCid()).getFirst().getCollaborateCode());
			collaborateProcessRecordRpcDTO.setProcess(ProcessEnum.WAIT_PRODUCE);
			collaborateProcessRecordRpcDTO.setQuantity(detail.getNum());
			collaborateProcessRecordRpcDTO.setScheduleQuantity(detail.getNum());
			collaborateProcessRecordRpcDTO.setStaff(technicalTableNameProxyImpl.getCurrentCode());
			collaborateProcessRecordRpcDTO.setRemark(request.getRemark());
			collaborateProcessService.record(collaborateProcessRecordRpcDTO);

		}
		List<CollaborateProduceDistribute> saveList = new ArrayList<>();

		Long batchGlobalCode = PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId();
		Long contractCode = null;
		Long sellOrderCode = null;
		//分发
		for (Long userCode : request.getUserCodeList()) {
			for (CollaborateDistributeRequest.DistributeDetail detail : request.getDistributeDetailList()) {

				CollaborateProduceDistribute cpd = new CollaborateProduceDistribute();
				cpd.setPrimaryKey(PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId());
				cpd.setEnterpriseCode(technicalTableNameProxyImpl.getEnterpriseCode());

				cpd.setFinishDate(request.getNeedFinishDate());
				cpd.setBatchNum(request.getBatchNum());
				CollaborateScheduleItemModel model = productMap.get(detail.getCid()).getFirst();
				cpd.setContractCode(model.getContractCode());
				cpd.setBussNumber(model.getContractNumber());
				cpd.setProductCode(model.getCollaborateMaterial());
				cpd.setBindCode(model.getCollaborateCode());
				cpd.setBatchGlobalCode(batchGlobalCode);
				cpd.setManageUserCode(userCode);
				cpd.setLeaderUserCode(technicalTableNameProxyImpl.getCurrentCode());
				cpd.setSellOrderCode(model.getSellOrderCode());
				cpd.setTaskNum(detail.getNum());
				cpd.setReportShow(true);
				saveList.add(cpd);

				contractCode = model.getContractCode();
				sellOrderCode = model.getSellOrderCode();
			}
		}


		collaborateProduceDistributeMapper.insert(saveList);
		wrapper = Wrappers.lambdaQuery();
		CollaborateScheduleItemModel partQuery = productMap.entrySet().stream().findFirst().get().getValue().getFirst();
		wrapper.eq(CollaborateScheduleItemModel::getContractCode, partQuery.getContractCode());
		wrapper.eq(CollaborateScheduleItemModel::getEnterpriseCode, partQuery.getEnterpriseCode());
		wrapper.eq(CollaborateScheduleItemModel::getDeleteFlag, false);
		wrapper.eq(CollaborateScheduleItemModel::getCollaborateType, 1);
		wrapper.eq(CollaborateScheduleItemModel::getMaterialNature, MaterialNatureEnum.PRODUCE);

		Map<Long, List<CollaborateScheduleItemModel>> partMap = collaborateScheduleItemMapper.selectList(wrapper).stream()
				.collect(Collectors.groupingBy(a -> a.getCollaborateMaterial()));

		List<CollaborateScheduleItemModel> materialList = new ArrayList<>();

		for (CollaborateDistributeRequest.DistributeDetail detail : request.getDistributeDetailList()) {
			CollaborateScheduleItemModel itemModel = productMap.get(detail.getCid()).getFirst();

			ProductSimpleInfo info = new ProductSimpleInfo();
			info.setProductCode(itemModel.getCollaborateMaterial());
			info.setRouteCode(detail.getRouteId());
			info.setOrderQuantity(detail.getNum());
			ProductAndBomMaterialRecorder bomMaterialRecorder = productBomQueryService.productAndBomMaterials(Collections.singletonList(info));

			// 出库
			io.vavr.collection.HashMap.ofAll(bomMaterialRecorder.getMaterialForProduce2OrderQuantity()).forEach(a -> {
				OutStockApply apply = new OutStockApply();
				apply.setApplyNum(a._2);
				apply.setPrimaryKey(PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId());
				apply.setEnterpriseCode(technicalTableNameProxyImpl.getEnterpriseCode());
				apply.setApplyStaff(technicalTableNameProxyImpl.getCurrentCode());
				CollaborateScheduleItemModel partModel = partMap.get(a._1).getFirst();
				apply.setContractCode(partModel.getContractCode());
				apply.setDataKey(partModel.getCollaborateCode());
				apply.setUnityNo(request.getBatchNum());
				apply.setContractNumber(partModel.getContractNumber());
				apply.setProductPartCode(a._1);
				apply.setType(MaterialType.SCLL.getType());

				partModel.setMaterialProduceQuantity(a._2.add(Optional.ofNullable(partModel.getMaterialProduceQuantity()).orElse(BigDecimal.ZERO)));
				materialList.add(partModel);

				/**
				 * 进度通知
				 */
				if (Optional.ofNullable(partModel.getOutboundQuantity()).orElse(BigDecimal.ZERO).compareTo(BigDecimal.ZERO) == 0) {
					CollaborateProcessRecordRpcDTO collaborateProcessRecordRpcDTO = new CollaborateProcessRecordRpcDTO();
					collaborateProcessRecordRpcDTO.setCollaborateCode(partModel.getContractCode());
					collaborateProcessRecordRpcDTO.setProcess(ProcessEnum.OUT_STOCK_WAIT);
					collaborateProcessRecordRpcDTO.setQuantity(a._2);
					collaborateProcessRecordRpcDTO.setStaff(technicalTableNameProxyImpl.getCurrentCode());
					collaborateProcessRecordRpcDTO.setRemark(request.getRemark());
					try {
						collaborateProcessService.record(collaborateProcessRecordRpcDTO);
					} catch (ExceptionPack e) {
						log.error("进度通知失败", e);
					}
				}
				outStockApplyMapper.insert(apply);
			});
		}
		//出库申请
		if (CollectionUtils.isNotEmpty(materialList)) {
			collaborateScheduleItemMapper.updateById(materialList);
		}
		// 原始生产
		compliantProduceMain(productMap, request, batchGlobalCode);
		//待发任务
		awaitingDetailsService.batchSynchronization(sellOrderCode);
		//批次号
		collaborateProduceBatchInfoMapper.createOneData(contractCode, request.getBatchNum());

	}



	/**
	 * @description 兼容现有的生产数据
	 *
	 * @author zhanglijia
	 * @organization futurecraftsmen
	 * @date Created in 2025/7/7 17:36
	 * @department: Product development
	 */
	private void compliantProduceMain(Map<Long, List<CollaborateScheduleItemModel>> cacheMap, CollaborateDistributeRequest request,
	                                  Long batchGlobalCode) throws ExceptionPack {

		for (CollaborateDistributeRequest.DistributeDetail detail : request.getDistributeDetailList()) {

			ProduceMainModel model = new ProduceMainModel();
			CollaborateScheduleItemModel itemModel = cacheMap.get(detail.getCid()).getFirst();
			ProductPartRpcDTO partRpcDTO = iProductPartService.queryPartsById(itemModel.getCollaborateMaterial());
			model.setEnterpriseCode(technicalTableNameProxyImpl.getEnterpriseCode());
			model.setProductOrPart(partRpcDTO.getProductPartCode());
			model.setTaskNum(detail.getNum());
			model.setAllocationNum(BigDecimal.ZERO);
			model.setFinishNum(BigDecimal.ZERO);
			model.setStatus(ProduceMainModel.Status.INIT.getCode());
			model.setStockType(1);
			model.setNeedType(1);
			/**
			 * 调度信息
			 */
			model.setBatchNum(request.getBatchNum());
			model.setBatchGlobalCode(batchGlobalCode);
			/**
			 * 产品信息
			 */
			model.setProductOrPartName(partRpcDTO.getProductPartSign());
			model.setDeliveryDate(itemModel.getDelivery());
			model.setRouteCode(detail.getRouteId());
			model.setPcsName(partRpcDTO.getPcsName());
			model.setPcsCode(Optional.ofNullable(partRpcDTO.getPcs()).orElse(0l));
			model.setProductFlag(true);
			model.setLevel(1);
			model.setContractCode(itemModel.getContractCode());
			model.setBussNumber(itemModel.getContractNumber());
			model.setPrimaryKeyValue(PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId());

			model.setProcedureCode(partRpcDTO.getProductPartCode());
			model.setProcedureName(partRpcDTO.getProductPartSign());
			model.setStatus(0);
			model.setProcedureFlag(1);

			model.setProduceUnityNo(request.getBatchNum());
			model.setUnityNo(request.getBatchNum());

			iProduceMainMapper.insertProduceMain(model, technicalTableNameProxyImpl.getProduceMain());

			List<ProduceSubPartInfoModel> partList =
					iProductPartService.queryAllRelationshipByIdAndRoute(detail.getRouteId(),
									partRpcDTO.getProductPartCode()).stream().filter(a -> a.getPartCode() != null)
							.map(a -> {
								ProduceSubPartInfoModel infoModel = new ProduceSubPartInfoModel();
								infoModel.setEnterpriseCode(technicalTableNameProxyImpl.getEnterpriseCode());
								infoModel.setPrimaryKeyValue(PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId());
								infoModel.setMainId(model.getPrimaryKeyValue());
								infoModel.setRouteCode(detail.getRouteId());
								infoModel.setMatchingNum(PrecisionUtils.multiplyNumber(
										Optional.of(a.getQuantity()).orElse(BigDecimal.ONE),
										detail.getNum()));
								infoModel.setPartCode(a.getPartCode());
								infoModel.setProcedureCode(a.getProcedureCode());
								infoModel.setPartName(a.getPartName());
								infoModel.setProductCode(partRpcDTO.getProductPartCode());
								return infoModel;
							}).toList();

			if (CollectionUtils.isNotEmpty(partList)) {
				iProduceSubPartInfoMapper.insertProduceSubPart(partList, technicalTableNameProxyImpl.getProduceSubPartInfo());
			}

			for (Long userCode : request.getUserCodeList()) {

				ProduceSubDispatcherModel produceSubModel = new ProduceSubDispatcherModel();
				produceSubModel.setOperator(userCode);
				produceSubModel.setEnterpriseCode(technicalTableNameProxyImpl.getEnterpriseCode());
				produceSubModel.setFlowNo(PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId());
				produceSubModel.setStatus(30);

				produceSubModel.setFinishDate(request.getNeedFinishDate());
				produceSubModel.setTaskNum(detail.getNum());
				produceSubModel.setMainId(model.getPrimaryKeyValue());
				produceSubModel.setCreator(technicalTableNameProxyImpl.getCurrentCode());
				produceSubModel.setAllocationNum(detail.getNum());
				produceSubModel.setPrimaryKeyValue(PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId());
				produceSubModel.setProduceUnityNo(request.getBatchNum());
				iProduceSubDispatcherMapper.add(produceSubModel, technicalTableNameProxyImpl.getProduceSubDispatcher());

			}

		}

	}

	@Override
	public Map<Long, Boolean> bubbleList(List<Long> sellOrderIds, int type) throws ExceptionPack {

		if (CollectionUtils.isEmpty(sellOrderIds)) {
			return Map.of();
		}

		LambdaQueryWrapper<CollaborateProcessReadInfo> queryWrapper = Wrappers.lambdaQuery();
		queryWrapper.eq(CollaborateProcessReadInfo::getEnterpriseCode, technicalTableNameProxyImpl.getEnterpriseCode());
		queryWrapper.in(CollaborateProcessReadInfo::getSellOrderCode, sellOrderIds);
		queryWrapper.eq(CollaborateProcessReadInfo::getDeleteFlag, false);
		List<CollaborateProcessReadInfo> readInfos = collaborateProcessReadInfoMapper.selectList(queryWrapper);
		return readInfos.stream().filter(a -> a.getReadFlag() != null).collect(Collectors
				.toMap(a -> a.getSellOrderCode(),
						a -> a.bubbleIng(type), (a, b) -> a));
	}

	@Override
	public void bubbleRead(Long sellOrderId, Integer type) throws ExceptionPack {
		if (sellOrderId == null) {
			return;
		}

		if (!CollaborateProcessReadInfo.allowOperation(type)) {
			return;
		}
		LambdaQueryWrapper<CollaborateProcessReadInfo> queryWrapper = Wrappers.lambdaQuery();
		queryWrapper.eq(CollaborateProcessReadInfo::getEnterpriseCode, technicalTableNameProxyImpl.getEnterpriseCode());
		queryWrapper.eq(CollaborateProcessReadInfo::getSellOrderCode, sellOrderId);
		queryWrapper.eq(CollaborateProcessReadInfo::getDeleteFlag, false);
		List<CollaborateProcessReadInfo> readInfos = collaborateProcessReadInfoMapper.selectList(queryWrapper);
		for (CollaborateProcessReadInfo readInfo : readInfos) {
			readInfo.bubbleEd(type);
		}
		collaborateProcessReadInfoMapper.updateById(readInfos);
	}

	@Override
	public void bubbleReset(Long sellOrderId, Long enterpriseCode) throws ExceptionPack {

		if (sellOrderId == null || enterpriseCode == null) {
			return;
		}

		Long currentEnterpriseCode = InfoPenetrateProcessor.INSTANCE.getPenetrateInfoNotNull(ENTERPRISE_CODE_THREAD_LOCAL);

		InfoPenetrateProcessor.INSTANCE.setPenetrateInfo(ENTERPRISE_CODE_THREAD_LOCAL, enterpriseCode);


		LambdaQueryWrapper<CollaborateProcessReadInfo> queryWrapper = Wrappers.lambdaQuery();
		queryWrapper.eq(CollaborateProcessReadInfo::getEnterpriseCode, enterpriseCode);
		queryWrapper.eq(CollaborateProcessReadInfo::getSellOrderCode, sellOrderId);
		queryWrapper.eq(CollaborateProcessReadInfo::getDeleteFlag, false);
		List<CollaborateProcessReadInfo> readInfos = collaborateProcessReadInfoMapper.selectList(queryWrapper);
		if (CollectionUtils.isNotEmpty(readInfos)) {
			for (CollaborateProcessReadInfo readInfo : readInfos) {
				readInfo.setReadFlag(0);
			}
			collaborateProcessReadInfoMapper.updateById(readInfos);
		} else {
			CollaborateProcessReadInfo info = new CollaborateProcessReadInfo();
			info.setEnterpriseCode(enterpriseCode);
			info.setSellOrderCode(sellOrderId);
			info.setReadFlag(0);
			info.setPrimaryKey(technicalTableNameProxyImpl.getNextPrimaryKey());
			collaborateProcessReadInfoMapper.insert(info);
		}
		InfoPenetrateProcessor.INSTANCE.setPenetrateInfo(ENTERPRISE_CODE_THREAD_LOCAL, currentEnterpriseCode);
	}
}
