package com.futurecraftsmen.pms.technical.service.impl.collaborate.lacktask;


import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.futurecraftsmen.pms.common.utils.PrecisionUtils;
import com.futurecraftsmen.pms.service.configuration.RequestTableHelper;
import com.futurecraftsmen.pms.service.util.CommonUtil;
import com.futurecraftsmen.pms.starter.domain.starter.PmsStarter;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.ProcessEnum;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.base.MaterialScheduleQuantityDetail;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.base.MaterialSimpleInfo;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.lacktask.*;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.process.CollaborateBatchProcessRecordRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.process.CollaborateProcessRecordRpcDTO;
import com.futurecraftsmen.pms.technical.api.service.collaborate.CollaborateProcessService;
import com.futurecraftsmen.pms.technical.api.service.collaborate.lacktask.CollaborateLackTaskEventHandlerService;
import com.futurecraftsmen.pms.technical.service.dao.collaborate.CollaborateScheduleItemMapper;
import com.futurecraftsmen.pms.technical.service.dao.collaborate.CollaborateScheduleItemMaterialPurchaseUpdateMapper;
import com.futurecraftsmen.pms.technical.service.dao.collaborate.CollaborateScheduleItemScheduleQuantityDecreaseUpdateMapper;
import com.futurecraftsmen.pms.technical.service.dao.collaborate.CollaborateScheduleItemScheduleQuantityUpdateMapper;
import com.futurecraftsmen.pms.technical.service.domain.collaborate.CollaborateScheduleItemModel;
import com.futurecraftsmen.pms.technical.service.domain.collaborate.update.CollaborateScheduleItemMaterialPurchaseUpdate;
import com.futurecraftsmen.pms.technical.service.domain.collaborate.update.CollaborateScheduleItemScheduleQuantityDecreaseUpdate;
import com.futurecraftsmen.pms.technical.service.domain.collaborate.update.CollaborateScheduleItemScheduleQuantityUpdate;
import com.futurecraftsmen.pms.technical.service.impl.inner.TableNameFactory;
import jakarta.annotation.Resource;
import org.aerie.forest.core.brick.exception.ExceptionMsg;
import org.aerie.forest.core.brick.exception.ExceptionPack;
import org.aerie.forest.core.brick.log.GlobalLogger;
import org.aerie.forest.core.brick.processor.datapenetrate.InfoPenetrateProcessor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.aerie.forest.core.brick.processor.datapenetrate.GlobalTokenPenetrate.ENTERPRISE_CODE_THREAD_LOCAL;


@DubboService(group = "pms")
public class CollaborateLackTaskEventHandlerServiceImpl implements CollaborateLackTaskEventHandlerService, GlobalLogger {

	@Resource
	private TableNameFactory tableFactory;

	@Autowired
	private CollaborateScheduleItemMapper collaborateScheduleItemMapper;

	@Resource
	private CollaborateProcessService collaborateProcessService;

	@Autowired
	private CollaborateScheduleItemScheduleQuantityUpdateMapper collaborateScheduleItemScheduleQuantityUpdateMapper;

	@Autowired
	private CollaborateScheduleItemScheduleQuantityDecreaseUpdateMapper collaborateScheduleItemScheduleQuantityDecreaseUpdateMapper;

	@Autowired
	private CollaborateScheduleItemMaterialPurchaseUpdateMapper collaborateScheduleItemMaterialPurchaseUpdateMapper;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void handlePurchaseEnquiryDeleteEvent(CollaborateScheduleItemPurchaseEnquiryDeleteEvents events) throws ExceptionPack {
		try {
			Long enterpriseCode = CommonUtil.getEnterpriseCode();


			List<Long> collaborateCodes = events.collaborateCodes();

			if (collaborateCodes == null || collaborateCodes.isEmpty()) {
				return;
			}

			// 选择的协作安排数据编号 ->删除数量
			Map<Long, BigDecimal> collaborateCode2DeleteNum = events.collaborateCode2DeleteNum();


			// 1.查询协作安排数据详情
			String collaborateScheduleItemTableName = tableFactory.getTableName(tableFactory.module.getProduction(),
					tableFactory.table.getCollaborateScheduleItem());

			LambdaQueryWrapper<CollaborateScheduleItemModel> scheduleItemWrapper = Wrappers.lambdaQuery();
			scheduleItemWrapper.in(CollaborateScheduleItemModel::getCollaborateCode, collaborateCodes);
			scheduleItemWrapper.eq(CollaborateScheduleItemModel::getEnterpriseCode, enterpriseCode);
			scheduleItemWrapper.eq(CollaborateScheduleItemModel::getDeleteFlag, false);
			RequestTableHelper.setTableName(collaborateScheduleItemTableName);
			List<CollaborateScheduleItemModel> scheduleItemModels = collaborateScheduleItemMapper.selectList(scheduleItemWrapper);

			if (CollectionUtil.isEmpty(scheduleItemModels)) {
				return;
			}

			Map<Long, Long> materialCode2MergeFlag = new HashMap<>();
			for (CollaborateScheduleItemModel collaborateScheduleItemModel : scheduleItemModels) {
				materialCode2MergeFlag.putIfAbsent(collaborateScheduleItemModel.getCollaborateMaterial(), PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId());
			}

			//4.按照销售合同分组，批量写入进度
			Map<Long, List<CollaborateScheduleItemModel>> sellOrderCode2ScheduleItemModels = scheduleItemModels.stream().collect(Collectors.groupingBy(
					CollaborateScheduleItemModel::getSellOrderCode));

			for (Map.Entry<Long, List<CollaborateScheduleItemModel>> entry : sellOrderCode2ScheduleItemModels.entrySet()) {
				Long sellOrderCode = entry.getKey();

				List<CollaborateScheduleItemModel> sellOrderScheduleItemModels = entry.getValue();
				CollaborateBatchProcessRecordRpcDTO collaborateBatchProcessRecordRpcDTO = new CollaborateBatchProcessRecordRpcDTO();
				collaborateBatchProcessRecordRpcDTO.setSellOrderCode(sellOrderCode);
				List<CollaborateProcessRecordRpcDTO> itemProcessRecords = new ArrayList<>();
				for (CollaborateScheduleItemModel sellOrderScheduleItemModel : sellOrderScheduleItemModels) {
					BigDecimal deleteNum = collaborateCode2DeleteNum.get(sellOrderScheduleItemModel.getCollaborateCode());
					if (deleteNum == null) {
						continue;
					}
					CollaborateProcessRecordRpcDTO collaborateProcessRecordRpcDTO = new CollaborateProcessRecordRpcDTO();
					collaborateProcessRecordRpcDTO.setCollaborateCode(sellOrderScheduleItemModel.getCollaborateCode());
					collaborateProcessRecordRpcDTO.setProcess(ProcessEnum.ENQUIRED_DELETE);
					collaborateProcessRecordRpcDTO.setQuantity(deleteNum);
					collaborateProcessRecordRpcDTO.setScheduleQuantity(null);
					collaborateProcessRecordRpcDTO.setStaff(events.getOpStaff());
					collaborateProcessRecordRpcDTO.setRemark(null);
					itemProcessRecords.add(collaborateProcessRecordRpcDTO);
				}
				collaborateBatchProcessRecordRpcDTO.setItemProcessRecords(itemProcessRecords);
				collaborateBatchProcessRecordRpcDTO.setMaterialCode2MergeFlag(materialCode2MergeFlag);
				collaborateProcessService.batchRecordForBatch(collaborateBatchProcessRecordRpcDTO);
			}


		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("fail to handlePurchaseEnquiryDeleteEvent").build());
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public List<Long> handleContractSignEvent(CollaborateScheduleItemContractSignEvents events) throws ExceptionPack {
		try {
			Long enterpriseCode = CommonUtil.getEnterpriseCode();


			List<Long> collaborateCodes = events.collaborateCodes();

			if (collaborateCodes == null || collaborateCodes.isEmpty()) {
				return Collections.emptyList();
			}

			// 选择的协作安排数据编号 ->对应的采购签章数量
			Map<Long, BigDecimal> collaborateCode2ScheduleNumNeedAdd = events.collaborateCode2ScheduleNumNeedAdd();

			// 选择的协作安排数据编号 ->对应的操作数量
			Map<Long, BigDecimal> collaborateCode2OperationNum = events.collaborateCode2OperationNum();


			// 1.查询协作安排数据详情
			String collaborateScheduleItemTableName = tableFactory.getTableName(tableFactory.module.getProduction(),
					tableFactory.table.getCollaborateScheduleItem());

			LambdaQueryWrapper<CollaborateScheduleItemModel> scheduleItemWrapper = Wrappers.lambdaQuery();
			scheduleItemWrapper.in(CollaborateScheduleItemModel::getCollaborateCode, collaborateCodes);
			scheduleItemWrapper.eq(CollaborateScheduleItemModel::getEnterpriseCode, enterpriseCode);
			scheduleItemWrapper.eq(CollaborateScheduleItemModel::getDeleteFlag, false);
			RequestTableHelper.setTableName(collaborateScheduleItemTableName);
			List<CollaborateScheduleItemModel> scheduleItemModels = collaborateScheduleItemMapper.selectList(scheduleItemWrapper);

			if (CollectionUtil.isEmpty(scheduleItemModels)) {
				return Collections.emptyList();
			}

			Set<Long> affectProcessPercentCollaborates = new HashSet<>();

			//2.批量更新 产品已安排量,schedule_flag
			//  批量更新 物料计划采购已安排量
			List<CollaborateScheduleItemScheduleQuantityUpdate> scheduleQuantityUpdates = new ArrayList<>();
			List<CollaborateScheduleItemMaterialPurchaseUpdate> materialPurchaseQuantityUpdates = new ArrayList<>();
			for (CollaborateScheduleItemModel scheduleItemModel : scheduleItemModels) {
				BigDecimal scheduleNumNeedAdd = collaborateCode2ScheduleNumNeedAdd.get(scheduleItemModel.getCollaborateCode());
				if (scheduleNumNeedAdd == null) {
					continue;
				}

				//采购产品：增加 已安排量，修改是否安排过标识；
				if (scheduleItemModel.getCollaborateType() == 0) {

//					boolean productScheduleFinish = scheduleItemModel.getOrderQuantity().compareTo(scheduleItemModel.scheduleQuantityRealValue()) <= 0;
//					boolean productOutFinish = scheduleItemModel.getOutboundQuantity().compareTo(scheduleItemModel.getOrderQuantity()) >= 0;
//
//					if (productOutFinish) {
//						continue;
//					}
					affectProcessPercentCollaborates.add(scheduleItemModel.getCollaborateCode());
					CollaborateScheduleItemScheduleQuantityUpdate scheduleQuantityUpdate = new CollaborateScheduleItemScheduleQuantityUpdate();
					scheduleQuantityUpdate.setCollaborateCode(scheduleItemModel.getCollaborateCode());
					scheduleQuantityUpdate.setScheduleQuantity(scheduleItemModel.scheduleQuantityRealValue().add(scheduleNumNeedAdd));
					scheduleQuantityUpdate.setScheduleFlag(true);
					scheduleQuantityUpdate.setLatestMaterialScheduleDetail(scheduleItemModel.getMaterialScheduleDetail());//保持原样
					scheduleQuantityUpdates.add(scheduleQuantityUpdate);
				}
				//采购物料：增加 物料计划采购已安排量；
				if (scheduleItemModel.getCollaborateType() == 1) {


					affectProcessPercentCollaborates.add(scheduleItemModel.getCollaborateCode());

					CollaborateScheduleItemMaterialPurchaseUpdate materialPurchaseQuantityUpdate = new CollaborateScheduleItemMaterialPurchaseUpdate();
					materialPurchaseQuantityUpdate.setCollaborateCode(scheduleItemModel.getCollaborateCode());
					materialPurchaseQuantityUpdate.setMaterialPurchaseQuantity(scheduleItemModel.getMaterialPurchaseQuantity().add(scheduleNumNeedAdd));
					materialPurchaseQuantityUpdates.add(materialPurchaseQuantityUpdate);
				}
			}


			//3.对于 scheduleItemModels 中产品数据的相关物料， 相关物料按配比增加已安排量
			List<CollaborateScheduleItemModel> scheduleProductItemModels = scheduleItemModels.stream().filter(p -> p.getCollaborateType() == 0).collect(Collectors.toList());

			// 产品编号 ->对应的采购签章数量
			Map<Long, BigDecimal> productCode2SignNum = new HashMap<>();
			for (CollaborateScheduleItemModel scheduleProductItemModel : scheduleProductItemModels) {
				BigDecimal scheduleNumNeedAdd = collaborateCode2ScheduleNumNeedAdd.get(scheduleProductItemModel.getCollaborateCode());
				if (scheduleNumNeedAdd == null) {
					continue;
				}
				boolean productScheduleFinish = scheduleProductItemModel.getOrderQuantity().compareTo(scheduleProductItemModel.scheduleQuantityRealValue()) <= 0;
				if (scheduleProductItemModel.getOutboundQuantity().compareTo(scheduleProductItemModel.getOrderQuantity()) >= 0) {
					productScheduleFinish = true;
				}
				if (productScheduleFinish) {
					continue;
				}
				productCode2SignNum.merge(scheduleProductItemModel.getCollaborateMaterial(), scheduleNumNeedAdd, BigDecimal::add);
			}

			if (!scheduleProductItemModels.isEmpty()) {

				Map<Long, List<CollaborateScheduleItemModel>> sellOrderCode2ScheduleProductItemModels = scheduleProductItemModels.stream().collect(Collectors.groupingBy(
						CollaborateScheduleItemModel::getSellOrderCode));

				List<Long> sellOrderCodes = sellOrderCode2ScheduleProductItemModels.keySet().stream().toList();

				//查询产品数据所在订单，所有物料信息
				LambdaQueryWrapper<CollaborateScheduleItemModel> allMaterialItemQueryWrapper = Wrappers.lambdaQuery();
				allMaterialItemQueryWrapper.in(CollaborateScheduleItemModel::getSellOrderCode, sellOrderCodes);
				allMaterialItemQueryWrapper.eq(CollaborateScheduleItemModel::getEnterpriseCode, enterpriseCode);
				allMaterialItemQueryWrapper.eq(CollaborateScheduleItemModel::getDeleteFlag, false);
				allMaterialItemQueryWrapper.eq(CollaborateScheduleItemModel::getCollaborateType, 1);
				RequestTableHelper.setTableName(collaborateScheduleItemTableName);
				List<CollaborateScheduleItemModel> allMaterialItemModels = collaborateScheduleItemMapper.selectList(allMaterialItemQueryWrapper);

				if (allMaterialItemModels != null) {
					Map<Long, CollaborateScheduleItemModel> materialItemCollaborateCode2Identity = allMaterialItemModels.stream()
							.filter(p -> p.getOrderQuantity().subtract(p.scheduleQuantityRealValue()).compareTo(BigDecimal.ZERO) > 0)
							.collect(Collectors.toMap(CollaborateScheduleItemModel::getCollaborateCode, Function.identity()));


					//按照销售订单分组相关物料数据
					Map<Long, List<CollaborateScheduleItemModel>> sellOrderCode2MaterialItemModels = allMaterialItemModels.stream()
							.filter(p -> p.getOrderQuantity().subtract(p.scheduleQuantityRealValue()).compareTo(BigDecimal.ZERO) > 0)
							.collect(Collectors.groupingBy(
							CollaborateScheduleItemModel::getSellOrderCode));

					Map<Long, BigDecimal> materialCollaborate2IncreaseQuantity = new HashMap<>();

					Map<Long, MaterialScheduleQuantityDetail> materialCollaborate2LatestMaterialScheduleDetail = new HashMap<>();


					for (Map.Entry<Long, List<CollaborateScheduleItemModel>> entry : sellOrderCode2ScheduleProductItemModels.entrySet()) {
						Long sellOrderCode = entry.getKey();

						//订单的所有物料
						List<CollaborateScheduleItemModel> sellOrderScheduleMaterialItemModels = sellOrderCode2MaterialItemModels.get(sellOrderCode);
						if (sellOrderScheduleMaterialItemModels == null || sellOrderCode2MaterialItemModels.isEmpty()) {
							continue;
						}

						//订单的所有产品
						List<CollaborateScheduleItemModel> sellOrderScheduleProductItemModels = entry.getValue();

						Set<Long> sellOrderScheduleProductCodes = sellOrderScheduleProductItemModels.stream().map(CollaborateScheduleItemModel::getCollaborateMaterial).collect(Collectors.toSet());

						//遍历订单的所有物料,判读有哪些物料需要更新
						for (CollaborateScheduleItemModel sellOrderScheduleMaterialItemModel : sellOrderScheduleMaterialItemModels) {
							if (StrUtil.isBlank(sellOrderScheduleMaterialItemModel.getMaterialDemandInfo())) {
								continue;
							}
							for (MaterialSimpleInfo materialSimpleInfo : sellOrderScheduleMaterialItemModel.materialDemandInfo()) {
								Long productCode = materialSimpleInfo.getParentCode();
								if (sellOrderScheduleProductCodes.contains(productCode)) {
									BigDecimal productSignNum = productCode2SignNum.get(productCode);
									if (productSignNum == null) {
										continue;
									}
									BigDecimal factor = materialSimpleInfo.getFactor();
									if (factor == null) {
										continue;
									}
									materialCollaborate2IncreaseQuantity.merge(sellOrderScheduleMaterialItemModel.getCollaborateCode(), PrecisionUtils.multiplyNumber(productSignNum, factor), BigDecimal::add);

									//设置最新的物料安排详情
									MaterialScheduleQuantityDetail latestMaterialScheduleDetail = materialCollaborate2LatestMaterialScheduleDetail.get(sellOrderScheduleMaterialItemModel.getCollaborateCode());
									if (latestMaterialScheduleDetail == null) {
										latestMaterialScheduleDetail = sellOrderScheduleMaterialItemModel.materialScheduleDetail();
									}

									latestMaterialScheduleDetail.increaseScheduleQuantity(productCode, PrecisionUtils.multiplyNumber(productSignNum, factor));

									materialCollaborate2LatestMaterialScheduleDetail.put(sellOrderScheduleMaterialItemModel.getCollaborateCode(), latestMaterialScheduleDetail);

								}
							}

						}
					}

					for (Map.Entry<Long, BigDecimal> materialCollaborateUpdateInfo : materialCollaborate2IncreaseQuantity.entrySet()) {

						CollaborateScheduleItemModel materialItemCollaborate = materialItemCollaborateCode2Identity.get(materialCollaborateUpdateInfo.getKey());

						if (materialItemCollaborate == null) {
							continue;
						}

						CollaborateScheduleItemScheduleQuantityUpdate scheduleQuantityUpdate = new CollaborateScheduleItemScheduleQuantityUpdate();
						scheduleQuantityUpdate.setCollaborateCode(materialCollaborateUpdateInfo.getKey());
						BigDecimal newScheduleQuantity = materialItemCollaborate.scheduleQuantityRealValue().add(materialCollaborateUpdateInfo.getValue());
						scheduleQuantityUpdate.setScheduleQuantity(newScheduleQuantity);
						scheduleQuantityUpdate.setScheduleFlag(true);
						MaterialScheduleQuantityDetail materialScheduleQuantityDetail = materialCollaborate2LatestMaterialScheduleDetail.get(materialCollaborateUpdateInfo.getKey());
						if (materialScheduleQuantityDetail != null) {
							scheduleQuantityUpdate.setLatestMaterialScheduleDetail(materialScheduleQuantityDetail.stringValue());
						}
						scheduleQuantityUpdates.add(scheduleQuantityUpdate);
					}

				}


			}

//			if (scheduleQuantityUpdates.isEmpty() && materialPurchaseQuantityUpdates.isEmpty()) {
//				return Collections.emptyList();
//			}


			if (!scheduleQuantityUpdates.isEmpty()) {
				try (AutoCloseable ignored = RequestTableHelper::clearBatchTableName) {
					RequestTableHelper.setBatchTableName(collaborateScheduleItemTableName);
					collaborateScheduleItemScheduleQuantityUpdateMapper.updateById(scheduleQuantityUpdates);
				} catch (Exception e) {
					LOGGER.error("批量操作失败: 表名={}", collaborateScheduleItemTableName, e);
				}
			}

			if (!materialPurchaseQuantityUpdates.isEmpty()) {
				try (AutoCloseable ignored = RequestTableHelper::clearBatchTableName) {
					RequestTableHelper.setBatchTableName(collaborateScheduleItemTableName);
					collaborateScheduleItemMaterialPurchaseUpdateMapper.updateById(materialPurchaseQuantityUpdates);
				} catch (Exception e) {
					LOGGER.error("批量操作失败: 表名={}", collaborateScheduleItemTableName, e);
				}
			}

			Map<Long, Long> materialCode2MergeFlag = new HashMap<>();
			for (CollaborateScheduleItemModel collaborateScheduleItemModel : scheduleItemModels) {
				materialCode2MergeFlag.putIfAbsent(collaborateScheduleItemModel.getCollaborateMaterial(), PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId());
			}

			//4.按照销售合同分组，批量写入进度
			Map<Long, List<CollaborateScheduleItemModel>> sellOrderCode2ScheduleItemModels = scheduleItemModels.stream().collect(Collectors.groupingBy(
					CollaborateScheduleItemModel::getSellOrderCode));

			for (Map.Entry<Long, List<CollaborateScheduleItemModel>> entry : sellOrderCode2ScheduleItemModels.entrySet()) {
				Long sellOrderCode = entry.getKey();

				List<CollaborateScheduleItemModel> sellOrderScheduleItemModels = entry.getValue();
				CollaborateBatchProcessRecordRpcDTO collaborateBatchProcessRecordRpcDTO = new CollaborateBatchProcessRecordRpcDTO();
				collaborateBatchProcessRecordRpcDTO.setSellOrderCode(sellOrderCode);
				List<CollaborateProcessRecordRpcDTO> itemProcessRecords = new ArrayList<>();
				for (CollaborateScheduleItemModel sellOrderScheduleItemModel : sellOrderScheduleItemModels) {
					BigDecimal scheduleNumNeedAdd = collaborateCode2ScheduleNumNeedAdd.get(sellOrderScheduleItemModel.getCollaborateCode());
					if (scheduleNumNeedAdd == null) {
						continue;
					}

					boolean affectProcessPercent = affectProcessPercentCollaborates.contains(sellOrderScheduleItemModel.getCollaborateCode());

					CollaborateProcessRecordRpcDTO collaborateProcessRecordRpcDTO = new CollaborateProcessRecordRpcDTO();
					collaborateProcessRecordRpcDTO.setCollaborateCode(sellOrderScheduleItemModel.getCollaborateCode());
					collaborateProcessRecordRpcDTO.setQuantity(collaborateCode2OperationNum.get(sellOrderScheduleItemModel.getCollaborateCode()));
					if (affectProcessPercent) {
						collaborateProcessRecordRpcDTO.setProcess(ProcessEnum.PURCHASE_SIGNED);
						collaborateProcessRecordRpcDTO.setScheduleQuantity(scheduleNumNeedAdd);
					} else {
						collaborateProcessRecordRpcDTO.setProcess(ProcessEnum.PURCHASE_SIGNED_NO_SCHEDULE);
					}
					collaborateProcessRecordRpcDTO.setStaff(events.getOpStaff());
					collaborateProcessRecordRpcDTO.setRemark(null);
					itemProcessRecords.add(collaborateProcessRecordRpcDTO);
				}
				collaborateBatchProcessRecordRpcDTO.setItemProcessRecords(itemProcessRecords);
				collaborateBatchProcessRecordRpcDTO.setMaterialCode2MergeFlag(materialCode2MergeFlag);
				collaborateProcessService.batchRecordForBatch(collaborateBatchProcessRecordRpcDTO);
			}

			return sellOrderCode2ScheduleItemModels.keySet().stream().toList();


		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("fail to handleContractSignEvent").build());
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void handleContractReturnEvent(CollaborateScheduleItemContractReturnEvents events) throws ExceptionPack {
		try {
			Long enterpriseCode = events.getEnterpriseCode();


			List<Long> collaborateCodes = events.collaborateCodes();

			if (collaborateCodes == null || collaborateCodes.isEmpty()) {
				return;
			}

			// 选择的协作安排数据编号 ->对应的退回数量
			Map<Long, BigDecimal> collaborateCode2ScheduleNumNeedDecrease = events.collaborateCode2ScheduleNumNeedDecrease();

			// 选择的协作安排数据编号 ->对应的操作数量
			Map<Long, BigDecimal> collaborateCode2OperationNum = events.collaborateCode2OperationNum();

			// 1.查询协作安排数据详情
			String collaborateScheduleItemTableName = tableFactory.getTableName(tableFactory.module.getProduction(),
					tableFactory.table.getCollaborateScheduleItem(), enterpriseCode);

			LambdaQueryWrapper<CollaborateScheduleItemModel> scheduleItemWrapper = Wrappers.lambdaQuery();
			scheduleItemWrapper.in(CollaborateScheduleItemModel::getCollaborateCode, collaborateCodes);
			scheduleItemWrapper.eq(CollaborateScheduleItemModel::getEnterpriseCode, enterpriseCode);
			scheduleItemWrapper.eq(CollaborateScheduleItemModel::getDeleteFlag, false);
			RequestTableHelper.setTableName(collaborateScheduleItemTableName);
			List<CollaborateScheduleItemModel> scheduleItemModels = collaborateScheduleItemMapper.selectList(scheduleItemWrapper);

			if (CollectionUtil.isEmpty(scheduleItemModels)) {
				return;
			}

			//2.批量更新 已安排量
			//  批量更新 物料计划采购已安排量
			List<CollaborateScheduleItemScheduleQuantityDecreaseUpdate> scheduleQuantityUpdates = new ArrayList<>();
			Map<Long, BigDecimal> collaborateProduct2ScheduleQuantityDecreaseNum = new HashMap<>();
			List<CollaborateScheduleItemMaterialPurchaseUpdate> materialPurchaseQuantityUpdates = new ArrayList<>();
			for (CollaborateScheduleItemModel scheduleItemModel : scheduleItemModels) {
				BigDecimal scheduleNumNeedDecrease = collaborateCode2ScheduleNumNeedDecrease.get(scheduleItemModel.getCollaborateCode());
				if (scheduleNumNeedDecrease == null) {
					continue;
				}

				//采购产品：减少 已安排量
				if (scheduleItemModel.getCollaborateType() == 0) {
					CollaborateScheduleItemScheduleQuantityDecreaseUpdate scheduleQuantityUpdate = new CollaborateScheduleItemScheduleQuantityDecreaseUpdate();
					scheduleQuantityUpdate.setCollaborateCode(scheduleItemModel.getCollaborateCode());
					BigDecimal newScheduleQuantity = scheduleItemModel.scheduleQuantityRealValue().subtract(scheduleNumNeedDecrease);
					if (newScheduleQuantity.compareTo(BigDecimal.ZERO) < 0) {
						newScheduleQuantity = BigDecimal.ZERO;
					}
					scheduleQuantityUpdate.setScheduleQuantity(newScheduleQuantity);
					scheduleQuantityUpdate.setLatestMaterialScheduleDetail(scheduleItemModel.getMaterialScheduleDetail());//保持原样
					scheduleQuantityUpdates.add(scheduleQuantityUpdate);

					BigDecimal productScheduleQuantityDecreaseNum = scheduleItemModel.scheduleQuantityRealValue().subtract(newScheduleQuantity);
					collaborateProduct2ScheduleQuantityDecreaseNum.put(scheduleItemModel.getCollaborateCode(), productScheduleQuantityDecreaseNum);
				}
				//采购物料：减少 物料计划采购已安排量；
				if (scheduleItemModel.getCollaborateType() == 1) {
					CollaborateScheduleItemMaterialPurchaseUpdate materialPurchaseQuantityUpdate = new CollaborateScheduleItemMaterialPurchaseUpdate();
					materialPurchaseQuantityUpdate.setCollaborateCode(scheduleItemModel.getCollaborateCode());
					BigDecimal newMaterialPurchaseQuantity = scheduleItemModel.getMaterialPurchaseQuantity().subtract(scheduleNumNeedDecrease);
					if (newMaterialPurchaseQuantity.compareTo(BigDecimal.ZERO) < 0) {
						newMaterialPurchaseQuantity = BigDecimal.ZERO;
					}
					materialPurchaseQuantityUpdate.setMaterialPurchaseQuantity(newMaterialPurchaseQuantity);
					materialPurchaseQuantityUpdates.add(materialPurchaseQuantityUpdate);
				}
			}


			//3.对于 scheduleItemModels 中产品数据的相关物料， 相关物料按  配比减少已安排量
			List<CollaborateScheduleItemModel> scheduleProductItemModels = scheduleItemModels.stream().filter(p -> p.getCollaborateType() == 0).collect(Collectors.toList());

			// 产品编号 ->对应的采购退回数量
			Map<Long, BigDecimal> productCode2DecreaseNum = new HashMap<>();
			for (CollaborateScheduleItemModel scheduleProductItemModel : scheduleProductItemModels) {
				BigDecimal productScheduleQuantityDecreaseNum = collaborateProduct2ScheduleQuantityDecreaseNum.get(scheduleProductItemModel.getCollaborateCode());
				if (productScheduleQuantityDecreaseNum == null) {
					continue;
				}
				productCode2DecreaseNum.merge(scheduleProductItemModel.getCollaborateMaterial(), productScheduleQuantityDecreaseNum, BigDecimal::add);
			}

			if (!scheduleProductItemModels.isEmpty()) {

				Map<Long, List<CollaborateScheduleItemModel>> sellOrderCode2ScheduleProductItemModels = scheduleProductItemModels.stream().collect(Collectors.groupingBy(
						CollaborateScheduleItemModel::getSellOrderCode));

				List<Long> sellOrderCodes = sellOrderCode2ScheduleProductItemModels.keySet().stream().toList();

				//查询产品数据所在订单，所有的物料信息
				LambdaQueryWrapper<CollaborateScheduleItemModel> allMaterialItemQueryWrapper = Wrappers.lambdaQuery();
				allMaterialItemQueryWrapper.in(CollaborateScheduleItemModel::getSellOrderCode, sellOrderCodes);
				allMaterialItemQueryWrapper.eq(CollaborateScheduleItemModel::getEnterpriseCode, enterpriseCode);
				allMaterialItemQueryWrapper.eq(CollaborateScheduleItemModel::getDeleteFlag, false);
				allMaterialItemQueryWrapper.eq(CollaborateScheduleItemModel::getCollaborateType, 1);
				RequestTableHelper.setTableName(collaborateScheduleItemTableName);
				List<CollaborateScheduleItemModel> allMaterialItemModels = collaborateScheduleItemMapper.selectList(allMaterialItemQueryWrapper);

				if (allMaterialItemModels != null) {
					Map<Long, CollaborateScheduleItemModel> materialItemCollaborateCode2Identity = allMaterialItemModels.stream().collect(Collectors.toMap(CollaborateScheduleItemModel::getCollaborateCode, Function.identity()));


					//按照销售订单分组相关物料数据
					Map<Long, List<CollaborateScheduleItemModel>> sellOrderCode2MaterialItemModels = allMaterialItemModels.stream().collect(Collectors.groupingBy(
							CollaborateScheduleItemModel::getSellOrderCode));

					Map<Long, BigDecimal> materialCollaborate2DecreaseQuantity = new HashMap<>();

					Map<Long, MaterialScheduleQuantityDetail> materialCollaborate2LatestMaterialScheduleDetail = new HashMap<>();


					for (Map.Entry<Long, List<CollaborateScheduleItemModel>> entry : sellOrderCode2ScheduleProductItemModels.entrySet()) {
						Long sellOrderCode = entry.getKey();

						//订单的所有物料
						List<CollaborateScheduleItemModel> sellOrderScheduleMaterialItemModels = sellOrderCode2MaterialItemModels.get(sellOrderCode);
						if (sellOrderScheduleMaterialItemModels == null || sellOrderCode2MaterialItemModels.isEmpty()) {
							continue;
						}

						//订单的所有产品
						List<CollaborateScheduleItemModel> sellOrderScheduleProductItemModels = entry.getValue();

						Set<Long> sellOrderScheduleProductCodes = sellOrderScheduleProductItemModels.stream().map(CollaborateScheduleItemModel::getCollaborateMaterial).collect(Collectors.toSet());

						//遍历订单的所有物料,判读有哪些物料需要更新
						for (CollaborateScheduleItemModel sellOrderScheduleMaterialItemModel : sellOrderScheduleMaterialItemModels) {
							if (StrUtil.isBlank(sellOrderScheduleMaterialItemModel.getMaterialDemandInfo())) {
								continue;
							}
							for (MaterialSimpleInfo materialSimpleInfo : sellOrderScheduleMaterialItemModel.materialDemandInfo()) {
								Long productCode = materialSimpleInfo.getParentCode();
								if (sellOrderScheduleProductCodes.contains(productCode)) {
									BigDecimal productScheduleQuantityDecreaseNum = productCode2DecreaseNum.get(productCode);
									if (productScheduleQuantityDecreaseNum == null) {
										continue;
									}
									BigDecimal factor = materialSimpleInfo.getFactor();
									if (factor == null) {
										continue;
									}
									materialCollaborate2DecreaseQuantity.merge(sellOrderScheduleMaterialItemModel.getCollaborateCode(), PrecisionUtils.multiplyNumber(productScheduleQuantityDecreaseNum, factor), BigDecimal::add);

									//设置最新的物料安排详情
									MaterialScheduleQuantityDetail latestMaterialScheduleDetail = materialCollaborate2LatestMaterialScheduleDetail.get(sellOrderScheduleMaterialItemModel.getCollaborateCode());
									if (latestMaterialScheduleDetail == null) {
										latestMaterialScheduleDetail = sellOrderScheduleMaterialItemModel.materialScheduleDetail();
									}

									latestMaterialScheduleDetail.decreaseScheduleQuantity(productCode, PrecisionUtils.multiplyNumber(productScheduleQuantityDecreaseNum, factor));

									materialCollaborate2LatestMaterialScheduleDetail.put(sellOrderScheduleMaterialItemModel.getCollaborateCode(), latestMaterialScheduleDetail);

								}
							}

						}
					}

					for (Map.Entry<Long, BigDecimal> materialCollaborateUpdateInfo : materialCollaborate2DecreaseQuantity.entrySet()) {

						CollaborateScheduleItemModel materialItemCollaborate = materialItemCollaborateCode2Identity.get(materialCollaborateUpdateInfo.getKey());

						if (materialItemCollaborate == null) {
							continue;
						}

						CollaborateScheduleItemScheduleQuantityDecreaseUpdate scheduleQuantityUpdate = new CollaborateScheduleItemScheduleQuantityDecreaseUpdate();
						scheduleQuantityUpdate.setCollaborateCode(materialCollaborateUpdateInfo.getKey());
						BigDecimal newScheduleQuantity = materialItemCollaborate.scheduleQuantityRealValue().subtract(materialCollaborateUpdateInfo.getValue());
						if (newScheduleQuantity.compareTo(BigDecimal.ZERO) < 0) {
							newScheduleQuantity = BigDecimal.ZERO;
						}
						scheduleQuantityUpdate.setScheduleQuantity(newScheduleQuantity);

						MaterialScheduleQuantityDetail materialScheduleQuantityDetail = materialCollaborate2LatestMaterialScheduleDetail.get(materialCollaborateUpdateInfo.getKey());
						if (materialScheduleQuantityDetail != null) {
							scheduleQuantityUpdate.setLatestMaterialScheduleDetail(materialScheduleQuantityDetail.stringValue());
						}

						scheduleQuantityUpdates.add(scheduleQuantityUpdate);
					}

				}


			}

			if (scheduleQuantityUpdates.isEmpty() && materialPurchaseQuantityUpdates.isEmpty()) {
				return;
			}


			if (!scheduleQuantityUpdates.isEmpty()) {
				try (AutoCloseable ignored = RequestTableHelper::clearBatchTableName) {
					RequestTableHelper.setBatchTableName(collaborateScheduleItemTableName);
					collaborateScheduleItemScheduleQuantityDecreaseUpdateMapper.updateById(scheduleQuantityUpdates);
				} catch (Exception e) {
					LOGGER.error("批量操作失败: 表名={}", collaborateScheduleItemTableName, e);
				}
			}

			if (!materialPurchaseQuantityUpdates.isEmpty()) {
				try (AutoCloseable ignored = RequestTableHelper::clearBatchTableName) {
					RequestTableHelper.setBatchTableName(collaborateScheduleItemTableName);
					collaborateScheduleItemMaterialPurchaseUpdateMapper.updateById(materialPurchaseQuantityUpdates);
				} catch (Exception e) {
					LOGGER.error("批量操作失败: 表名={}", collaborateScheduleItemTableName, e);
				}
			}

			Map<Long, Long> materialCode2MergeFlag = new HashMap<>();
			for (CollaborateScheduleItemModel collaborateScheduleItemModel : scheduleItemModels) {
				materialCode2MergeFlag.putIfAbsent(collaborateScheduleItemModel.getCollaborateMaterial(), PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId());
			}


			//4.按照销售合同分组，批量写入进度
			Map<Long, List<CollaborateScheduleItemModel>> sellOrderCode2ScheduleItemModels = scheduleItemModels.stream().collect(Collectors.groupingBy(
					CollaborateScheduleItemModel::getSellOrderCode));

			for (Map.Entry<Long, List<CollaborateScheduleItemModel>> entry : sellOrderCode2ScheduleItemModels.entrySet()) {
				Long sellOrderCode = entry.getKey();

				List<CollaborateScheduleItemModel> sellOrderScheduleItemModels = entry.getValue();
				CollaborateBatchProcessRecordRpcDTO collaborateBatchProcessRecordRpcDTO = new CollaborateBatchProcessRecordRpcDTO();
				collaborateBatchProcessRecordRpcDTO.setSellOrderCode(sellOrderCode);
				List<CollaborateProcessRecordRpcDTO> itemProcessRecords = new ArrayList<>();
				for (CollaborateScheduleItemModel sellOrderScheduleItemModel : sellOrderScheduleItemModels) {
					BigDecimal scheduleNumNeedDecrease = collaborateCode2ScheduleNumNeedDecrease.get(sellOrderScheduleItemModel.getCollaborateCode());
					if (scheduleNumNeedDecrease == null) {
						continue;
					}
					CollaborateProcessRecordRpcDTO collaborateProcessRecordRpcDTO = new CollaborateProcessRecordRpcDTO();
					collaborateProcessRecordRpcDTO.setCollaborateCode(sellOrderScheduleItemModel.getCollaborateCode());
					collaborateProcessRecordRpcDTO.setProcess(ProcessEnum.PURCHASE_CONTRACT_RETURN);
					collaborateProcessRecordRpcDTO.setQuantity(collaborateCode2OperationNum.get(sellOrderScheduleItemModel.getCollaborateCode()));
					collaborateProcessRecordRpcDTO.setScheduleQuantity(null);
					collaborateProcessRecordRpcDTO.setStaff(null);
					collaborateProcessRecordRpcDTO.setRemark(null);
					itemProcessRecords.add(collaborateProcessRecordRpcDTO);
				}
				collaborateBatchProcessRecordRpcDTO.setItemProcessRecords(itemProcessRecords);
				collaborateBatchProcessRecordRpcDTO.setEnterpriseCode(enterpriseCode);
				collaborateBatchProcessRecordRpcDTO.setMaterialCode2MergeFlag(materialCode2MergeFlag);
				collaborateProcessService.batchRecordForBatch(collaborateBatchProcessRecordRpcDTO);
			}


		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("fail to handleContractReturnEvent").build());
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void handleContractWithDrawEvent(CollaborateScheduleItemContractWithDrawEvents events) throws ExceptionPack {
		try {
			Long enterpriseCode = events.getEnterpriseCode();


			List<Long> collaborateCodes = events.collaborateCodes();

			if (collaborateCodes == null || collaborateCodes.isEmpty()) {
				return;
			}

			// 选择的协作安排数据编号 ->对应的退回数量
			Map<Long, BigDecimal> collaborateCode2ScheduleNumNeedDecrease = events.collaborateCode2ScheduleNumNeedDecrease();

			// 选择的协作安排数据编号 ->对应的操作数量
			Map<Long, BigDecimal> collaborateCode2OperationNum = events.collaborateCode2OperationNum();

			// 1.查询协作安排数据详情
			String collaborateScheduleItemTableName = tableFactory.getTableName(tableFactory.module.getProduction(),
					tableFactory.table.getCollaborateScheduleItem(), enterpriseCode);

			LambdaQueryWrapper<CollaborateScheduleItemModel> scheduleItemWrapper = Wrappers.lambdaQuery();
			scheduleItemWrapper.in(CollaborateScheduleItemModel::getCollaborateCode, collaborateCodes);
			scheduleItemWrapper.eq(CollaborateScheduleItemModel::getEnterpriseCode, enterpriseCode);
			scheduleItemWrapper.eq(CollaborateScheduleItemModel::getDeleteFlag, false);
			RequestTableHelper.setTableName(collaborateScheduleItemTableName);
			List<CollaborateScheduleItemModel> scheduleItemModels = collaborateScheduleItemMapper.selectList(scheduleItemWrapper);

			if (CollectionUtil.isEmpty(scheduleItemModels)) {
				return;
			}

			//2.批量更新 已安排量
			//  批量更新 物料计划采购已安排量
			List<CollaborateScheduleItemScheduleQuantityDecreaseUpdate> scheduleQuantityUpdates = new ArrayList<>();
			Map<Long, BigDecimal> collaborateProduct2ScheduleQuantityDecreaseNum = new HashMap<>();
			List<CollaborateScheduleItemMaterialPurchaseUpdate> materialPurchaseQuantityUpdates = new ArrayList<>();
			for (CollaborateScheduleItemModel scheduleItemModel : scheduleItemModels) {
				BigDecimal scheduleNumNeedDecrease = collaborateCode2ScheduleNumNeedDecrease.get(scheduleItemModel.getCollaborateCode());
				if (scheduleNumNeedDecrease == null) {
					continue;
				}

				//采购产品：减少 已安排量
				if (scheduleItemModel.getCollaborateType() == 0) {
					CollaborateScheduleItemScheduleQuantityDecreaseUpdate scheduleQuantityUpdate = new CollaborateScheduleItemScheduleQuantityDecreaseUpdate();
					scheduleQuantityUpdate.setCollaborateCode(scheduleItemModel.getCollaborateCode());
					BigDecimal newScheduleQuantity = scheduleItemModel.scheduleQuantityRealValue().subtract(scheduleNumNeedDecrease);
					if (newScheduleQuantity.compareTo(BigDecimal.ZERO) < 0) {
						newScheduleQuantity = BigDecimal.ZERO;
					}
					scheduleQuantityUpdate.setScheduleQuantity(newScheduleQuantity);
					scheduleQuantityUpdate.setLatestMaterialScheduleDetail(scheduleItemModel.getMaterialScheduleDetail());//保持原样
					scheduleQuantityUpdates.add(scheduleQuantityUpdate);

					BigDecimal productScheduleQuantityDecreaseNum = scheduleItemModel.scheduleQuantityRealValue().subtract(newScheduleQuantity);
					collaborateProduct2ScheduleQuantityDecreaseNum.put(scheduleItemModel.getCollaborateCode(), productScheduleQuantityDecreaseNum);
				}
				//采购物料：减少 物料计划采购已安排量；
				if (scheduleItemModel.getCollaborateType() == 1) {
					CollaborateScheduleItemMaterialPurchaseUpdate materialPurchaseQuantityUpdate = new CollaborateScheduleItemMaterialPurchaseUpdate();
					materialPurchaseQuantityUpdate.setCollaborateCode(scheduleItemModel.getCollaborateCode());
					BigDecimal newMaterialPurchaseQuantity = scheduleItemModel.getMaterialPurchaseQuantity().subtract(scheduleNumNeedDecrease);
					if (newMaterialPurchaseQuantity.compareTo(BigDecimal.ZERO) < 0) {
						newMaterialPurchaseQuantity = BigDecimal.ZERO;
					}
					materialPurchaseQuantityUpdate.setMaterialPurchaseQuantity(newMaterialPurchaseQuantity);
					materialPurchaseQuantityUpdates.add(materialPurchaseQuantityUpdate);
				}
			}


			//3.对于 scheduleItemModels 中产品数据的相关物料， 相关物料按  配比减少已安排量
			List<CollaborateScheduleItemModel> scheduleProductItemModels = scheduleItemModels.stream().filter(p -> p.getCollaborateType() == 0).collect(Collectors.toList());

			// 产品编号 ->对应的采购退回数量
			Map<Long, BigDecimal> productCode2DecreaseNum = new HashMap<>();
			for (CollaborateScheduleItemModel scheduleProductItemModel : scheduleProductItemModels) {
				BigDecimal productScheduleQuantityDecreaseNum = collaborateProduct2ScheduleQuantityDecreaseNum.get(scheduleProductItemModel.getCollaborateCode());
				if (productScheduleQuantityDecreaseNum == null) {
					continue;
				}
				productCode2DecreaseNum.merge(scheduleProductItemModel.getCollaborateMaterial(), productScheduleQuantityDecreaseNum, BigDecimal::add);
			}

			if (!scheduleProductItemModels.isEmpty()) {

				Map<Long, List<CollaborateScheduleItemModel>> sellOrderCode2ScheduleProductItemModels = scheduleProductItemModels.stream().collect(Collectors.groupingBy(
						CollaborateScheduleItemModel::getSellOrderCode));

				List<Long> sellOrderCodes = sellOrderCode2ScheduleProductItemModels.keySet().stream().toList();

				//查询产品数据所在订单，所有的物料信息
				LambdaQueryWrapper<CollaborateScheduleItemModel> allMaterialItemQueryWrapper = Wrappers.lambdaQuery();
				allMaterialItemQueryWrapper.in(CollaborateScheduleItemModel::getSellOrderCode, sellOrderCodes);
				allMaterialItemQueryWrapper.eq(CollaborateScheduleItemModel::getEnterpriseCode, enterpriseCode);
				allMaterialItemQueryWrapper.eq(CollaborateScheduleItemModel::getDeleteFlag, false);
				allMaterialItemQueryWrapper.eq(CollaborateScheduleItemModel::getCollaborateType, 1);
				RequestTableHelper.setTableName(collaborateScheduleItemTableName);
				List<CollaborateScheduleItemModel> allMaterialItemModels = collaborateScheduleItemMapper.selectList(allMaterialItemQueryWrapper);

				if (allMaterialItemModels != null) {
					Map<Long, CollaborateScheduleItemModel> materialItemCollaborateCode2Identity = allMaterialItemModels.stream().collect(Collectors.toMap(CollaborateScheduleItemModel::getCollaborateCode, Function.identity()));


					//按照销售订单分组相关物料数据
					Map<Long, List<CollaborateScheduleItemModel>> sellOrderCode2MaterialItemModels = allMaterialItemModels.stream().collect(Collectors.groupingBy(
							CollaborateScheduleItemModel::getSellOrderCode));

					Map<Long, BigDecimal> materialCollaborate2DecreaseQuantity = new HashMap<>();

					Map<Long, MaterialScheduleQuantityDetail> materialCollaborate2LatestMaterialScheduleDetail = new HashMap<>();


					for (Map.Entry<Long, List<CollaborateScheduleItemModel>> entry : sellOrderCode2ScheduleProductItemModels.entrySet()) {
						Long sellOrderCode = entry.getKey();

						//订单的所有物料
						List<CollaborateScheduleItemModel> sellOrderScheduleMaterialItemModels = sellOrderCode2MaterialItemModels.get(sellOrderCode);
						if (sellOrderScheduleMaterialItemModels == null || sellOrderCode2MaterialItemModels.isEmpty()) {
							continue;
						}

						//订单的所有产品
						List<CollaborateScheduleItemModel> sellOrderScheduleProductItemModels = entry.getValue();

						Set<Long> sellOrderScheduleProductCodes = sellOrderScheduleProductItemModels.stream().map(CollaborateScheduleItemModel::getCollaborateMaterial).collect(Collectors.toSet());

						//遍历订单的所有物料,判读有哪些物料需要更新
						for (CollaborateScheduleItemModel sellOrderScheduleMaterialItemModel : sellOrderScheduleMaterialItemModels) {
							if (StrUtil.isBlank(sellOrderScheduleMaterialItemModel.getMaterialDemandInfo())) {
								continue;
							}
							for (MaterialSimpleInfo materialSimpleInfo : sellOrderScheduleMaterialItemModel.materialDemandInfo()) {
								Long productCode = materialSimpleInfo.getParentCode();
								if (sellOrderScheduleProductCodes.contains(productCode)) {
									BigDecimal productScheduleQuantityDecreaseNum = productCode2DecreaseNum.get(productCode);
									if (productScheduleQuantityDecreaseNum == null) {
										continue;
									}
									BigDecimal factor = materialSimpleInfo.getFactor();
									if (factor == null) {
										continue;
									}
									materialCollaborate2DecreaseQuantity.merge(sellOrderScheduleMaterialItemModel.getCollaborateCode(), PrecisionUtils.multiplyNumber(productScheduleQuantityDecreaseNum, factor), BigDecimal::add);

									//设置最新的物料安排详情
									MaterialScheduleQuantityDetail latestMaterialScheduleDetail = materialCollaborate2LatestMaterialScheduleDetail.get(sellOrderScheduleMaterialItemModel.getCollaborateCode());
									if (latestMaterialScheduleDetail == null) {
										latestMaterialScheduleDetail = sellOrderScheduleMaterialItemModel.materialScheduleDetail();
									}

									latestMaterialScheduleDetail.decreaseScheduleQuantity(productCode, PrecisionUtils.multiplyNumber(productScheduleQuantityDecreaseNum, factor));

									materialCollaborate2LatestMaterialScheduleDetail.put(sellOrderScheduleMaterialItemModel.getCollaborateCode(), latestMaterialScheduleDetail);

								}
							}

						}
					}

					for (Map.Entry<Long, BigDecimal> materialCollaborateUpdateInfo : materialCollaborate2DecreaseQuantity.entrySet()) {

						CollaborateScheduleItemModel materialItemCollaborate = materialItemCollaborateCode2Identity.get(materialCollaborateUpdateInfo.getKey());

						if (materialItemCollaborate == null) {
							continue;
						}

						CollaborateScheduleItemScheduleQuantityDecreaseUpdate scheduleQuantityUpdate = new CollaborateScheduleItemScheduleQuantityDecreaseUpdate();
						scheduleQuantityUpdate.setCollaborateCode(materialCollaborateUpdateInfo.getKey());
						BigDecimal newScheduleQuantity = materialItemCollaborate.scheduleQuantityRealValue().subtract(materialCollaborateUpdateInfo.getValue());
						if (newScheduleQuantity.compareTo(BigDecimal.ZERO) < 0) {
							newScheduleQuantity = BigDecimal.ZERO;
						}
						scheduleQuantityUpdate.setScheduleQuantity(newScheduleQuantity);

						MaterialScheduleQuantityDetail materialScheduleQuantityDetail = materialCollaborate2LatestMaterialScheduleDetail.get(materialCollaborateUpdateInfo.getKey());
						if (materialScheduleQuantityDetail != null) {
							scheduleQuantityUpdate.setLatestMaterialScheduleDetail(materialScheduleQuantityDetail.stringValue());
						}

						scheduleQuantityUpdates.add(scheduleQuantityUpdate);
					}

				}


			}

			if (scheduleQuantityUpdates.isEmpty() && materialPurchaseQuantityUpdates.isEmpty()) {
				return;
			}


			if (!scheduleQuantityUpdates.isEmpty()) {
				try (AutoCloseable ignored = RequestTableHelper::clearBatchTableName) {
					RequestTableHelper.setBatchTableName(collaborateScheduleItemTableName);
					collaborateScheduleItemScheduleQuantityDecreaseUpdateMapper.updateById(scheduleQuantityUpdates);
				} catch (Exception e) {
					LOGGER.error("批量操作失败: 表名={}", collaborateScheduleItemTableName, e);
				}
			}

			if (!materialPurchaseQuantityUpdates.isEmpty()) {
				try (AutoCloseable ignored = RequestTableHelper::clearBatchTableName) {
					RequestTableHelper.setBatchTableName(collaborateScheduleItemTableName);
					collaborateScheduleItemMaterialPurchaseUpdateMapper.updateById(materialPurchaseQuantityUpdates);
				} catch (Exception e) {
					LOGGER.error("批量操作失败: 表名={}", collaborateScheduleItemTableName, e);
				}
			}

			Map<Long, Long> materialCode2MergeFlag = new HashMap<>();
			for (CollaborateScheduleItemModel collaborateScheduleItemModel : scheduleItemModels) {
				materialCode2MergeFlag.putIfAbsent(collaborateScheduleItemModel.getCollaborateMaterial(), PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId());
			}


			//4.按照销售合同分组，批量写入进度
			Map<Long, List<CollaborateScheduleItemModel>> sellOrderCode2ScheduleItemModels = scheduleItemModels.stream().collect(Collectors.groupingBy(
					CollaborateScheduleItemModel::getSellOrderCode));

			for (Map.Entry<Long, List<CollaborateScheduleItemModel>> entry : sellOrderCode2ScheduleItemModels.entrySet()) {
				Long sellOrderCode = entry.getKey();

				List<CollaborateScheduleItemModel> sellOrderScheduleItemModels = entry.getValue();
				CollaborateBatchProcessRecordRpcDTO collaborateBatchProcessRecordRpcDTO = new CollaborateBatchProcessRecordRpcDTO();
				collaborateBatchProcessRecordRpcDTO.setSellOrderCode(sellOrderCode);
				List<CollaborateProcessRecordRpcDTO> itemProcessRecords = new ArrayList<>();
				for (CollaborateScheduleItemModel sellOrderScheduleItemModel : sellOrderScheduleItemModels) {
					BigDecimal scheduleNumNeedDecrease = collaborateCode2ScheduleNumNeedDecrease.get(sellOrderScheduleItemModel.getCollaborateCode());
					if (scheduleNumNeedDecrease == null) {
						continue;
					}
					CollaborateProcessRecordRpcDTO collaborateProcessRecordRpcDTO = new CollaborateProcessRecordRpcDTO();
					collaborateProcessRecordRpcDTO.setCollaborateCode(sellOrderScheduleItemModel.getCollaborateCode());
					collaborateProcessRecordRpcDTO.setProcess(ProcessEnum.PURCHASE_CONTRACT_WITHDRAW);
					collaborateProcessRecordRpcDTO.setQuantity(collaborateCode2OperationNum.get(sellOrderScheduleItemModel.getCollaborateCode()));
					collaborateProcessRecordRpcDTO.setScheduleQuantity(null);
					collaborateProcessRecordRpcDTO.setStaff(null);
					collaborateProcessRecordRpcDTO.setRemark(null);
					itemProcessRecords.add(collaborateProcessRecordRpcDTO);
				}
				collaborateBatchProcessRecordRpcDTO.setItemProcessRecords(itemProcessRecords);
				collaborateBatchProcessRecordRpcDTO.setEnterpriseCode(enterpriseCode);
				collaborateBatchProcessRecordRpcDTO.setMaterialCode2MergeFlag(materialCode2MergeFlag);
				collaborateProcessService.batchRecordForBatch(collaborateBatchProcessRecordRpcDTO);
			}


		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("fail to handleContractWithDrawEvent").build());
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void handleContractCancelEvent(CollaborateScheduleItemContractCancelEvents events) throws ExceptionPack {
		try {
			Long enterpriseCode = events.getEnterpriseCode();


			List<Long> collaborateCodes = events.collaborateCodes();

			if (collaborateCodes == null || collaborateCodes.isEmpty()) {
				return;
			}

			// 选择的协作安排数据编号 ->对应的退回数量
			Map<Long, BigDecimal> collaborateCode2ScheduleNumNeedDecrease = events.collaborateCode2ScheduleNumNeedDecrease();

			// 选择的协作安排数据编号 ->对应的操作数量
			Map<Long, BigDecimal> collaborateCode2OperationNum = events.collaborateCode2OperationNum();

			// 1.查询协作安排数据详情
			String collaborateScheduleItemTableName = tableFactory.getTableName(tableFactory.module.getProduction(),
					tableFactory.table.getCollaborateScheduleItem(), enterpriseCode);

			LambdaQueryWrapper<CollaborateScheduleItemModel> scheduleItemWrapper = Wrappers.lambdaQuery();
			scheduleItemWrapper.in(CollaborateScheduleItemModel::getCollaborateCode, collaborateCodes);
			scheduleItemWrapper.eq(CollaborateScheduleItemModel::getEnterpriseCode, enterpriseCode);
			scheduleItemWrapper.eq(CollaborateScheduleItemModel::getDeleteFlag, false);
			RequestTableHelper.setTableName(collaborateScheduleItemTableName);
			List<CollaborateScheduleItemModel> scheduleItemModels = collaborateScheduleItemMapper.selectList(scheduleItemWrapper);

			if (CollectionUtil.isEmpty(scheduleItemModels)) {
				return;
			}

			//2.批量更新 已安排量
			//  批量更新 物料计划采购已安排量
			List<CollaborateScheduleItemScheduleQuantityDecreaseUpdate> scheduleQuantityUpdates = new ArrayList<>();
			Map<Long, BigDecimal> collaborateProduct2ScheduleQuantityDecreaseNum = new HashMap<>();
			List<CollaborateScheduleItemMaterialPurchaseUpdate> materialPurchaseQuantityUpdates = new ArrayList<>();
			for (CollaborateScheduleItemModel scheduleItemModel : scheduleItemModels) {
				BigDecimal scheduleNumNeedDecrease = collaborateCode2ScheduleNumNeedDecrease.get(scheduleItemModel.getCollaborateCode());
				if (scheduleNumNeedDecrease == null) {
					continue;
				}

				//采购产品：减少 已安排量
				if (scheduleItemModel.getCollaborateType() == 0) {
					CollaborateScheduleItemScheduleQuantityDecreaseUpdate scheduleQuantityUpdate = new CollaborateScheduleItemScheduleQuantityDecreaseUpdate();
					scheduleQuantityUpdate.setCollaborateCode(scheduleItemModel.getCollaborateCode());
					BigDecimal newScheduleQuantity = scheduleItemModel.scheduleQuantityRealValue().subtract(scheduleNumNeedDecrease);
					if (newScheduleQuantity.compareTo(BigDecimal.ZERO) < 0) {
						newScheduleQuantity = BigDecimal.ZERO;
					}
					scheduleQuantityUpdate.setScheduleQuantity(newScheduleQuantity);
					scheduleQuantityUpdate.setLatestMaterialScheduleDetail(scheduleItemModel.getMaterialScheduleDetail());//保持原样
					scheduleQuantityUpdates.add(scheduleQuantityUpdate);

					BigDecimal productScheduleQuantityDecreaseNum = scheduleItemModel.scheduleQuantityRealValue().subtract(newScheduleQuantity);
					collaborateProduct2ScheduleQuantityDecreaseNum.put(scheduleItemModel.getCollaborateCode(), productScheduleQuantityDecreaseNum);
				}
				//采购物料：减少 物料计划采购已安排量；
				if (scheduleItemModel.getCollaborateType() == 1) {
					CollaborateScheduleItemMaterialPurchaseUpdate materialPurchaseQuantityUpdate = new CollaborateScheduleItemMaterialPurchaseUpdate();
					materialPurchaseQuantityUpdate.setCollaborateCode(scheduleItemModel.getCollaborateCode());
					BigDecimal newMaterialPurchaseQuantity = scheduleItemModel.getMaterialPurchaseQuantity().subtract(scheduleNumNeedDecrease);
					if (newMaterialPurchaseQuantity.compareTo(BigDecimal.ZERO) < 0) {
						newMaterialPurchaseQuantity = BigDecimal.ZERO;
					}
					materialPurchaseQuantityUpdate.setMaterialPurchaseQuantity(newMaterialPurchaseQuantity);
					materialPurchaseQuantityUpdates.add(materialPurchaseQuantityUpdate);
				}
			}


			//3.对于 scheduleItemModels 中产品数据的相关物料， 相关物料按  配比减少已安排量
			List<CollaborateScheduleItemModel> scheduleProductItemModels = scheduleItemModels.stream().filter(p -> p.getCollaborateType() == 0).collect(Collectors.toList());

			// 产品编号 ->对应的采购退回数量
			Map<Long, BigDecimal> productCode2DecreaseNum = new HashMap<>();
			for (CollaborateScheduleItemModel scheduleProductItemModel : scheduleProductItemModels) {
				BigDecimal productScheduleQuantityDecreaseNum = collaborateProduct2ScheduleQuantityDecreaseNum.get(scheduleProductItemModel.getCollaborateCode());
				if (productScheduleQuantityDecreaseNum == null) {
					continue;
				}
				productCode2DecreaseNum.merge(scheduleProductItemModel.getCollaborateMaterial(), productScheduleQuantityDecreaseNum, BigDecimal::add);
			}

			if (!scheduleProductItemModels.isEmpty()) {

				Map<Long, List<CollaborateScheduleItemModel>> sellOrderCode2ScheduleProductItemModels = scheduleProductItemModels.stream().collect(Collectors.groupingBy(
						CollaborateScheduleItemModel::getSellOrderCode));

				List<Long> sellOrderCodes = sellOrderCode2ScheduleProductItemModels.keySet().stream().toList();

				//查询产品数据所在订单，所有的物料信息
				LambdaQueryWrapper<CollaborateScheduleItemModel> allMaterialItemQueryWrapper = Wrappers.lambdaQuery();
				allMaterialItemQueryWrapper.in(CollaborateScheduleItemModel::getSellOrderCode, sellOrderCodes);
				allMaterialItemQueryWrapper.eq(CollaborateScheduleItemModel::getEnterpriseCode, enterpriseCode);
				allMaterialItemQueryWrapper.eq(CollaborateScheduleItemModel::getDeleteFlag, false);
				allMaterialItemQueryWrapper.eq(CollaborateScheduleItemModel::getCollaborateType, 1);
				RequestTableHelper.setTableName(collaborateScheduleItemTableName);
				List<CollaborateScheduleItemModel> allMaterialItemModels = collaborateScheduleItemMapper.selectList(allMaterialItemQueryWrapper);

				if (allMaterialItemModels != null) {
					Map<Long, CollaborateScheduleItemModel> materialItemCollaborateCode2Identity = allMaterialItemModels.stream().collect(Collectors.toMap(CollaborateScheduleItemModel::getCollaborateCode, Function.identity()));


					//按照销售订单分组相关物料数据
					Map<Long, List<CollaborateScheduleItemModel>> sellOrderCode2MaterialItemModels = allMaterialItemModels.stream().collect(Collectors.groupingBy(
							CollaborateScheduleItemModel::getSellOrderCode));

					Map<Long, BigDecimal> materialCollaborate2DecreaseQuantity = new HashMap<>();

					Map<Long, MaterialScheduleQuantityDetail> materialCollaborate2LatestMaterialScheduleDetail = new HashMap<>();


					for (Map.Entry<Long, List<CollaborateScheduleItemModel>> entry : sellOrderCode2ScheduleProductItemModels.entrySet()) {
						Long sellOrderCode = entry.getKey();

						//订单的所有物料
						List<CollaborateScheduleItemModel> sellOrderScheduleMaterialItemModels = sellOrderCode2MaterialItemModels.get(sellOrderCode);
						if (sellOrderScheduleMaterialItemModels == null || sellOrderCode2MaterialItemModels.isEmpty()) {
							continue;
						}

						//订单的所有产品
						List<CollaborateScheduleItemModel> sellOrderScheduleProductItemModels = entry.getValue();

						Set<Long> sellOrderScheduleProductCodes = sellOrderScheduleProductItemModels.stream().map(CollaborateScheduleItemModel::getCollaborateMaterial).collect(Collectors.toSet());

						//遍历订单的所有物料,判读有哪些物料需要更新
						for (CollaborateScheduleItemModel sellOrderScheduleMaterialItemModel : sellOrderScheduleMaterialItemModels) {
							if (StrUtil.isBlank(sellOrderScheduleMaterialItemModel.getMaterialDemandInfo())) {
								continue;
							}
							for (MaterialSimpleInfo materialSimpleInfo : sellOrderScheduleMaterialItemModel.materialDemandInfo()) {
								Long productCode = materialSimpleInfo.getParentCode();
								if (sellOrderScheduleProductCodes.contains(productCode)) {
									BigDecimal productScheduleQuantityDecreaseNum = productCode2DecreaseNum.get(productCode);
									if (productScheduleQuantityDecreaseNum == null) {
										continue;
									}
									BigDecimal factor = materialSimpleInfo.getFactor();
									if (factor == null) {
										continue;
									}
									materialCollaborate2DecreaseQuantity.merge(sellOrderScheduleMaterialItemModel.getCollaborateCode(), PrecisionUtils.multiplyNumber(productScheduleQuantityDecreaseNum, factor), BigDecimal::add);

									//设置最新的物料安排详情
									MaterialScheduleQuantityDetail latestMaterialScheduleDetail = materialCollaborate2LatestMaterialScheduleDetail.get(sellOrderScheduleMaterialItemModel.getCollaborateCode());
									if (latestMaterialScheduleDetail == null) {
										latestMaterialScheduleDetail = sellOrderScheduleMaterialItemModel.materialScheduleDetail();
									}

									latestMaterialScheduleDetail.decreaseScheduleQuantity(productCode, PrecisionUtils.multiplyNumber(productScheduleQuantityDecreaseNum, factor));

									materialCollaborate2LatestMaterialScheduleDetail.put(sellOrderScheduleMaterialItemModel.getCollaborateCode(), latestMaterialScheduleDetail);

								}
							}

						}
					}

					for (Map.Entry<Long, BigDecimal> materialCollaborateUpdateInfo : materialCollaborate2DecreaseQuantity.entrySet()) {

						CollaborateScheduleItemModel materialItemCollaborate = materialItemCollaborateCode2Identity.get(materialCollaborateUpdateInfo.getKey());

						if (materialItemCollaborate == null) {
							continue;
						}

						CollaborateScheduleItemScheduleQuantityDecreaseUpdate scheduleQuantityUpdate = new CollaborateScheduleItemScheduleQuantityDecreaseUpdate();
						scheduleQuantityUpdate.setCollaborateCode(materialCollaborateUpdateInfo.getKey());
						BigDecimal newScheduleQuantity = materialItemCollaborate.scheduleQuantityRealValue().subtract(materialCollaborateUpdateInfo.getValue());
						if (newScheduleQuantity.compareTo(BigDecimal.ZERO) < 0) {
							newScheduleQuantity = BigDecimal.ZERO;
						}
						scheduleQuantityUpdate.setScheduleQuantity(newScheduleQuantity);

						MaterialScheduleQuantityDetail materialScheduleQuantityDetail = materialCollaborate2LatestMaterialScheduleDetail.get(materialCollaborateUpdateInfo.getKey());
						if (materialScheduleQuantityDetail != null) {
							scheduleQuantityUpdate.setLatestMaterialScheduleDetail(materialScheduleQuantityDetail.stringValue());
						}

						scheduleQuantityUpdates.add(scheduleQuantityUpdate);
					}

				}


			}

			if (scheduleQuantityUpdates.isEmpty() && materialPurchaseQuantityUpdates.isEmpty()) {
				return;
			}


			if (!scheduleQuantityUpdates.isEmpty()) {
				try (AutoCloseable ignored = RequestTableHelper::clearBatchTableName) {
					RequestTableHelper.setBatchTableName(collaborateScheduleItemTableName);
					collaborateScheduleItemScheduleQuantityDecreaseUpdateMapper.updateById(scheduleQuantityUpdates);
				} catch (Exception e) {
					LOGGER.error("批量操作失败: 表名={}", collaborateScheduleItemTableName, e);
				}
			}

			if (!materialPurchaseQuantityUpdates.isEmpty()) {
				try (AutoCloseable ignored = RequestTableHelper::clearBatchTableName) {
					RequestTableHelper.setBatchTableName(collaborateScheduleItemTableName);
					collaborateScheduleItemMaterialPurchaseUpdateMapper.updateById(materialPurchaseQuantityUpdates);
				} catch (Exception e) {
					LOGGER.error("批量操作失败: 表名={}", collaborateScheduleItemTableName, e);
				}
			}

			Map<Long, Long> materialCode2MergeFlag = new HashMap<>();
			for (CollaborateScheduleItemModel collaborateScheduleItemModel : scheduleItemModels) {
				materialCode2MergeFlag.putIfAbsent(collaborateScheduleItemModel.getCollaborateMaterial(), PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId());
			}


			//4.按照销售合同分组，批量写入进度
			Map<Long, List<CollaborateScheduleItemModel>> sellOrderCode2ScheduleItemModels = scheduleItemModels.stream().collect(Collectors.groupingBy(
					CollaborateScheduleItemModel::getSellOrderCode));

			for (Map.Entry<Long, List<CollaborateScheduleItemModel>> entry : sellOrderCode2ScheduleItemModels.entrySet()) {
				Long sellOrderCode = entry.getKey();

				List<CollaborateScheduleItemModel> sellOrderScheduleItemModels = entry.getValue();
				CollaborateBatchProcessRecordRpcDTO collaborateBatchProcessRecordRpcDTO = new CollaborateBatchProcessRecordRpcDTO();
				collaborateBatchProcessRecordRpcDTO.setSellOrderCode(sellOrderCode);
				List<CollaborateProcessRecordRpcDTO> itemProcessRecords = new ArrayList<>();
				for (CollaborateScheduleItemModel sellOrderScheduleItemModel : sellOrderScheduleItemModels) {
					BigDecimal scheduleNumNeedDecrease = collaborateCode2ScheduleNumNeedDecrease.get(sellOrderScheduleItemModel.getCollaborateCode());
					if (scheduleNumNeedDecrease == null) {
						continue;
					}
					CollaborateProcessRecordRpcDTO collaborateProcessRecordRpcDTO = new CollaborateProcessRecordRpcDTO();
					collaborateProcessRecordRpcDTO.setCollaborateCode(sellOrderScheduleItemModel.getCollaborateCode());
					collaborateProcessRecordRpcDTO.setProcess(ProcessEnum.PURCHASE_CONTRACT_CANCEL);
					collaborateProcessRecordRpcDTO.setQuantity(collaborateCode2OperationNum.get(sellOrderScheduleItemModel.getCollaborateCode()));
					collaborateProcessRecordRpcDTO.setScheduleQuantity(null);
					collaborateProcessRecordRpcDTO.setStaff(null);
					collaborateProcessRecordRpcDTO.setRemark(null);
					itemProcessRecords.add(collaborateProcessRecordRpcDTO);
				}
				collaborateBatchProcessRecordRpcDTO.setItemProcessRecords(itemProcessRecords);
				collaborateBatchProcessRecordRpcDTO.setEnterpriseCode(enterpriseCode);
				collaborateBatchProcessRecordRpcDTO.setMaterialCode2MergeFlag(materialCode2MergeFlag);
				collaborateProcessService.batchRecordForBatch(collaborateBatchProcessRecordRpcDTO);
			}


		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("fail to handleContractCancelEvent").build());
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void handleReceiving(CollaborateScheduleItemReceivingEvents events) throws ExceptionPack {
		try {
			Long enterpriseCode = CommonUtil.getEnterpriseCode();


			List<Long> collaborateCodes = events.collaborateCodes();

			if (collaborateCodes == null || collaborateCodes.isEmpty()) {
				return;
			}

			// 协作安排数据编号 ->对应的准确收货数量
			Map<Long, BigDecimal> collaborateCode2ReceiveNum = events.collaborateCode2ReceiveNum();

			// 协作安排数据编号 -> 进度类型
			Map<Long, ProcessEnum> collaborateCode2Process = events.collaborateCode2Process();

			// 1.查询协作安排数据详情
			String collaborateScheduleItemTableName = tableFactory.getTableName(tableFactory.module.getProduction(),
					tableFactory.table.getCollaborateScheduleItem());

			LambdaQueryWrapper<CollaborateScheduleItemModel> scheduleItemWrapper = Wrappers.lambdaQuery();
			scheduleItemWrapper.in(CollaborateScheduleItemModel::getCollaborateCode, collaborateCodes);
			scheduleItemWrapper.eq(CollaborateScheduleItemModel::getEnterpriseCode, enterpriseCode);
			scheduleItemWrapper.eq(CollaborateScheduleItemModel::getDeleteFlag, false);
			RequestTableHelper.setTableName(collaborateScheduleItemTableName);
			List<CollaborateScheduleItemModel> scheduleItemModels = collaborateScheduleItemMapper.selectList(scheduleItemWrapper);

			if (CollectionUtil.isEmpty(scheduleItemModels)) {
				return;
			}

			Map<Long, Long> materialCode2MergeFlag = new HashMap<>();
			for (CollaborateScheduleItemModel collaborateScheduleItemModel : scheduleItemModels) {
				materialCode2MergeFlag.putIfAbsent(collaborateScheduleItemModel.getCollaborateMaterial(), PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId());
			}

			//3.按照销售合同分组，批量写入进度
			Map<Long, List<CollaborateScheduleItemModel>> sellOrderCode2ScheduleItemModels = scheduleItemModels.stream().collect(Collectors.groupingBy(
					CollaborateScheduleItemModel::getSellOrderCode));

			for (Map.Entry<Long, List<CollaborateScheduleItemModel>> entry : sellOrderCode2ScheduleItemModels.entrySet()) {
				Long sellOrderCode = entry.getKey();

				List<CollaborateScheduleItemModel> sellOrderScheduleItemModels = entry.getValue();
				CollaborateBatchProcessRecordRpcDTO collaborateBatchProcessRecordRpcDTO = new CollaborateBatchProcessRecordRpcDTO();
				collaborateBatchProcessRecordRpcDTO.setSellOrderCode(sellOrderCode);
				List<CollaborateProcessRecordRpcDTO> itemProcessRecords = new ArrayList<>();
				for (CollaborateScheduleItemModel sellOrderScheduleItemModel : sellOrderScheduleItemModels) {
					//@Nullable
					BigDecimal receiveNum = collaborateCode2ReceiveNum.get(sellOrderScheduleItemModel.getCollaborateCode());

					ProcessEnum processEnum = collaborateCode2Process.get(sellOrderScheduleItemModel.getCollaborateCode());
					if (processEnum == null) {
						continue;
					}
					CollaborateProcessRecordRpcDTO collaborateProcessRecordRpcDTO = new CollaborateProcessRecordRpcDTO();
					collaborateProcessRecordRpcDTO.setCollaborateCode(sellOrderScheduleItemModel.getCollaborateCode());
					collaborateProcessRecordRpcDTO.setProcess(processEnum);
					collaborateProcessRecordRpcDTO.setQuantity(receiveNum);
					collaborateProcessRecordRpcDTO.setStaff(events.getOpStaff());
					collaborateProcessRecordRpcDTO.setRemark(null);
					itemProcessRecords.add(collaborateProcessRecordRpcDTO);
				}
				collaborateBatchProcessRecordRpcDTO.setItemProcessRecords(itemProcessRecords);
				collaborateBatchProcessRecordRpcDTO.setMaterialCode2MergeFlag(materialCode2MergeFlag);
				collaborateProcessService.batchRecordForBatch(collaborateBatchProcessRecordRpcDTO);
			}


		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("fail to handleReceiving").build());
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void handleInbound(CollaborateScheduleItemInboundEvents events) throws ExceptionPack {
		try {
			Long enterpriseCode = events.getEnterpriseCode();

			if (enterpriseCode != null) {
				InfoPenetrateProcessor.INSTANCE.setPenetrateInfo(ENTERPRISE_CODE_THREAD_LOCAL, enterpriseCode);
			} else {
				enterpriseCode = CommonUtil.getEnterpriseCode();
			}

			List<Long> collaborateCodes = events.collaborateCodes();

			if (collaborateCodes == null || collaborateCodes.isEmpty()) {
				return;
			}

			// 协作安排数据编号 ->对应的准确入库数量
			Map<Long, BigDecimal> collaborateCode2InboundNum = events.collaborateCode2InboundNum();

			// 协作安排数据编号 -> 进度类型
			Map<Long, ProcessEnum> collaborateCode2Process = events.collaborateCode2Process();

			// 1.查询协作安排数据详情
			String collaborateScheduleItemTableName = tableFactory.getTableName(tableFactory.module.getProduction(),
					tableFactory.table.getCollaborateScheduleItem(), enterpriseCode);

			LambdaQueryWrapper<CollaborateScheduleItemModel> scheduleItemWrapper = Wrappers.lambdaQuery();
			scheduleItemWrapper.in(CollaborateScheduleItemModel::getCollaborateCode, collaborateCodes);
			scheduleItemWrapper.eq(CollaborateScheduleItemModel::getEnterpriseCode, enterpriseCode);
			scheduleItemWrapper.eq(CollaborateScheduleItemModel::getDeleteFlag, false);
			RequestTableHelper.setTableName(collaborateScheduleItemTableName);
			List<CollaborateScheduleItemModel> scheduleItemModels = collaborateScheduleItemMapper.selectList(scheduleItemWrapper);

			if (CollectionUtil.isEmpty(scheduleItemModels)) {
				return;
			}

			Map<Long, Long> materialCode2MergeFlag = new HashMap<>();
			for (CollaborateScheduleItemModel collaborateScheduleItemModel : scheduleItemModels) {
				materialCode2MergeFlag.putIfAbsent(collaborateScheduleItemModel.getCollaborateMaterial(), PmsStarter.INSTANCE.getSnowFlakeBuilder().nextId());
			}


			//3.按照销售合同分组，批量写入进度
			Map<Long, List<CollaborateScheduleItemModel>> sellOrderCode2ScheduleItemModels = scheduleItemModels.stream().collect(Collectors.groupingBy(
					CollaborateScheduleItemModel::getSellOrderCode));

			for (Map.Entry<Long, List<CollaborateScheduleItemModel>> entry : sellOrderCode2ScheduleItemModels.entrySet()) {
				Long sellOrderCode = entry.getKey();

				List<CollaborateScheduleItemModel> sellOrderScheduleItemModels = entry.getValue();
				CollaborateBatchProcessRecordRpcDTO collaborateBatchProcessRecordRpcDTO = new CollaborateBatchProcessRecordRpcDTO();
				collaborateBatchProcessRecordRpcDTO.setSellOrderCode(sellOrderCode);
				collaborateBatchProcessRecordRpcDTO.setEnterpriseCode(enterpriseCode);
				List<CollaborateProcessRecordRpcDTO> itemProcessRecords = new ArrayList<>();
				for (CollaborateScheduleItemModel sellOrderScheduleItemModel : sellOrderScheduleItemModels) {
					//@Nullable
					BigDecimal inboundNum = collaborateCode2InboundNum.get(sellOrderScheduleItemModel.getCollaborateCode());

					ProcessEnum processEnum = collaborateCode2Process.get(sellOrderScheduleItemModel.getCollaborateCode());
					if (processEnum == null) {
						continue;
					}
					CollaborateProcessRecordRpcDTO collaborateProcessRecordRpcDTO = new CollaborateProcessRecordRpcDTO();
					collaborateProcessRecordRpcDTO.setCollaborateCode(sellOrderScheduleItemModel.getCollaborateCode());
					collaborateProcessRecordRpcDTO.setProcess(processEnum);
					collaborateProcessRecordRpcDTO.setQuantity(inboundNum);
					collaborateProcessRecordRpcDTO.setStaff(events.getOpStaff());
					collaborateProcessRecordRpcDTO.setRemark(null);
					itemProcessRecords.add(collaborateProcessRecordRpcDTO);
				}
				collaborateBatchProcessRecordRpcDTO.setItemProcessRecords(itemProcessRecords);
				collaborateBatchProcessRecordRpcDTO.setMaterialCode2MergeFlag(materialCode2MergeFlag);
				collaborateProcessService.batchRecordForBatch(collaborateBatchProcessRecordRpcDTO);
			}


		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("fail to handleInbound").build());
		}
	}
}
