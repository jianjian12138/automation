package com.futurecraftsmen.pms.technical.service.impl.collaborate.lacktask;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.futurecraftsmen.pms.service.configuration.RequestTableHelper;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.MaterialNatureEnum;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.StagingArea;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.base.MaterialSimpleInfo;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.outbound.CollaborateOutboundPageRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.outbound.CollaboratePurchaseContractInfoRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.orderdetails.OrderDetailsRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.ProductPartRpcDTO;
import com.futurecraftsmen.pms.technical.api.service.technical.IProductPartService;
import com.futurecraftsmen.pms.technical.service.dao.collaborate.CollaborateOutSendMaterialMapper;
import com.futurecraftsmen.pms.technical.service.dao.collaborate.CollaborateScheduleItemMapper;
import com.futurecraftsmen.pms.technical.service.dao.collaborate.StagingAreaMapper;
import com.futurecraftsmen.pms.technical.service.dao.warehouse.IStockMapper;
import com.futurecraftsmen.pms.technical.service.domain.collaborate.CollaborateOutSendMaterialModel;
import com.futurecraftsmen.pms.technical.service.domain.collaborate.CollaborateScheduleItemModel;
import com.futurecraftsmen.pms.technical.service.domain.collaborate.base.CollaborateContractReceiveMaterial;
import com.futurecraftsmen.pms.technical.service.domain.collaborate.base.CollaborateContractReceiveMaterials;
import com.futurecraftsmen.pms.technical.service.domain.warehouse.StockModel;
import com.futurecraftsmen.pms.technical.service.impl.collaborate.produce.CollaborateProduceDistributeServiceImpl;
import com.futurecraftsmen.pms.technical.service.impl.inner.TableNameFactory;
import jakarta.annotation.Resource;
import org.aerie.forest.core.brick.domain.view.CodeMapName;
import org.aerie.forest.core.brick.exception.ExceptionMsg;
import org.aerie.forest.core.brick.exception.ExceptionPack;
import org.aerie.forest.core.brick.processor.datapenetrate.GlobalTokenPenetrate;
import org.aerie.forest.core.brick.processor.datapenetrate.InfoPenetrateProcessor;
import org.aerie.forest.core.brick.rightcharacteristics.SellScheduleTypeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author chengxinyu
 * @description 协作缺料内部服务
 * @organization futurecraftsmen
 * @date 2025-08-19 13:52
 */
@Service
public class CollaborateReceiveMaterialInnerService {

	@Resource
	private TableNameFactory tableFactory;

	@Autowired
	private CollaborateScheduleItemMapper collaborateScheduleItemMapper;

	@Autowired
	private CollaborateOutSendMaterialMapper collaborateOutSendMaterialMapper;

	@Resource
	@Lazy
	private CollaborateProduceDistributeServiceImpl collaborateProduceDistributeService;

	@Resource
	private IProductPartService productPartService;

	@Resource
	private IStockMapper stockMapper;

	@Autowired
	private StagingAreaMapper stagingAreaMapper;


	/**
	 * @param contractCode
	 * @return
	 * @throws ExceptionPack
	 * @description 查询合同收货物料数量
	 * @author chengxinyu
	 * @date 2025-08-19 14:00
	 */
	public long queryContractReceiveMaterialForOutboundSize(Long contractCode) throws ExceptionPack {
		try {
			CollaborateContractReceiveMaterials contractReceiveMaterials = queryContractReceiveMaterials(contractCode);
			if (contractReceiveMaterials == null) {
				return 0L;
			}
			return contractReceiveMaterials.receiveMaterialSize();
		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("fail to query ContractReceiveMaterialForOutboundSize").build());
		}
	}

	/**
	 * @param materialCode TODO https://devops.aliyun.com/projex/task/PMSPRO-13409# 《扫码出库页面，不支持出 合同 收货物料》
	 * @return
	 * @throws ExceptionPack
	 * @description 返回最近一 收货物料出库任务 (收货物料有库存九)
	 * @author chengxinyu
	 * @date 2025-08-19 14:00
	 */
	public OrderDetailsRpcDTO collaborateOutReceiveMaterialPending(Long materialCode, CodeMapName operator) throws ExceptionPack {
		try {

			OrderDetailsRpcDTO canOutCollaborateOutbound = new OrderDetailsRpcDTO();
			canOutCollaborateOutbound.setSellScheduleType(SellScheduleTypeEnum.COLLABORATE);

//			canOutCollaborateOutbound.setCollaborateOutReceiveMaterial(true);
//			canOutCollaborateOutbound.setOperator(operator);
			return canOutCollaborateOutbound;

		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("fail to query collaborateOutReceiveMaterialPending").build());
		}
	}

	/**
	 * @param contractCode
	 * @return
	 * @throws ExceptionPack
	 * @description 查询合同收货物料出库信息
	 * @author chengxinyu
	 * @date 2025-08-19 14:00
	 */
	public List<CollaborateOutboundPageRpcDTO> queryContractReceiveMaterialForOutbound(Long contractCode) throws ExceptionPack {
		try {
			Long enterpriseCode = InfoPenetrateProcessor.INSTANCE.getPenetrateInfoNotNull(GlobalTokenPenetrate.ENTERPRISE_CODE_THREAD_LOCAL);

			// 新技术部-库存表
			String stockTableName =
					tableFactory.getTableName(tableFactory.module.getWarehouse(), tableFactory.table.getStock());

			CollaborateContractReceiveMaterials contractReceiveMaterials = queryContractReceiveMaterials(contractCode);
			if (contractReceiveMaterials == null || contractReceiveMaterials.receiveMaterialEmpty()) {
				return List.of();
			}
			List<CollaborateOutboundPageRpcDTO> list = new ArrayList<>();

			List<CollaborateContractReceiveMaterial> collaborateContractReceiveMaterials = contractReceiveMaterials.collaborateContractReceiveMaterials();


			//查询物料信息
			List<Long> materialCodesNeedQuery = collaborateContractReceiveMaterials.stream().map(CollaborateContractReceiveMaterial::getReceiveMaterial).filter(Objects::nonNull).distinct().toList();
			List<ProductPartRpcDTO> materialInfos = productPartService.queryByCodes(materialCodesNeedQuery, enterpriseCode, true, false);
			Map<Long, ProductPartRpcDTO> materialInfoMap = materialInfos.stream().collect(Collectors.toMap(ProductPartRpcDTO::getProductPartCode,
					Function.identity(), (existing, replacement) -> existing));

			//查询库存信息
			List<StockModel> stockByProductPartCodeIn = stockMapper.getStockByProductPartCodeInNew(materialCodesNeedQuery, enterpriseCode, stockTableName);

			Map<Long, StockModel> stockMap = stockByProductPartCodeIn.stream()
					.collect(Collectors.toMap(StockModel::getProductPartCode, Function.identity()));

			//查询收货物料已出量（中转区信息）
			LambdaQueryWrapper<StagingArea> stagingAreaForReceiveMaterialQw = Wrappers.lambdaQuery();
			stagingAreaForReceiveMaterialQw.eq(StagingArea::getEnterpriseCode, enterpriseCode);
			stagingAreaForReceiveMaterialQw.eq(StagingArea::getDeleteFlag, false);
			stagingAreaForReceiveMaterialQw.eq(StagingArea::getType, Boolean.FALSE);
			stagingAreaForReceiveMaterialQw.eq(StagingArea::getAreaType, 3); //收货物料中转区
			stagingAreaForReceiveMaterialQw.eq(StagingArea::getContractCode, contractCode);
			stagingAreaForReceiveMaterialQw.in(StagingArea::getProductPartCode, materialCodesNeedQuery);
			List<StagingArea> stagingAreaInfos = stagingAreaMapper.selectList(stagingAreaForReceiveMaterialQw);

			Map<Long, BigDecimal> receiveMaterial2OutboundQuantity = stagingAreaInfos.stream()
					.collect(Collectors.groupingBy(
							StagingArea::getProductPartCode,
							Collectors.reducing(BigDecimal.ZERO,
									StagingArea::getOutNum, BigDecimal::add)
					));


			//设置序号，物料编号，物料名称，物料规格，需求量，单位,库存仓位信息,已出量
			for (CollaborateContractReceiveMaterial collaborateContractReceiveMaterial : collaborateContractReceiveMaterials) {
				if (collaborateContractReceiveMaterial.getReceiveMaterial() != null && materialInfoMap.containsKey(collaborateContractReceiveMaterial.getReceiveMaterial())) {
					ProductPartRpcDTO materialInfo = materialInfoMap.get(collaborateContractReceiveMaterial.getReceiveMaterial());

					CollaborateOutboundPageRpcDTO collaborateOutboundPageRpcDTO = new CollaborateOutboundPageRpcDTO();
					collaborateOutboundPageRpcDTO.setPrimaryKey(collaborateContractReceiveMaterial.getReceiveMaterial()); //使用收货物料编号作为 primaryKey(datakey)
					collaborateOutboundPageRpcDTO.setReceiveMaterial(true);
					collaborateOutboundPageRpcDTO.setEnterpriseCode(enterpriseCode);
					collaborateOutboundPageRpcDTO.setContractCode(contractCode);
					collaborateOutboundPageRpcDTO.setCollaborateType(1);
					collaborateOutboundPageRpcDTO.setCollaborateMaterial(collaborateContractReceiveMaterial.getReceiveMaterial());
					//需求量取值逻辑:相关协作外发物料数据，需求量的最大值
					collaborateOutboundPageRpcDTO.setOrderQuantity(collaborateContractReceiveMaterial.receiveMaterialDemand());
					collaborateOutboundPageRpcDTO.setScheduleQuantity(BigDecimal.ZERO);
					collaborateOutboundPageRpcDTO.setDeleteFlag(false);
					collaborateOutboundPageRpcDTO.setUnityNo(materialInfo.getUnityNo());
					collaborateOutboundPageRpcDTO.setName(materialInfo.getName());
					collaborateOutboundPageRpcDTO.setModel(materialInfo.getModel());
					collaborateOutboundPageRpcDTO.setPcsChn(materialInfo.getPcsName());
					collaborateOutboundPageRpcDTO.setApplyNum(BigDecimal.ZERO);
					collaborateOutboundPageRpcDTO.setPcsChn(materialInfo.getPcsName());

					//设置已出量
					collaborateOutboundPageRpcDTO.setOutboundQuantity(receiveMaterial2OutboundQuantity.getOrDefault(collaborateContractReceiveMaterial.getReceiveMaterial(), BigDecimal.ZERO));

					if (collaborateContractReceiveMaterial.getReceiveMaterial() != null && stockMap.containsKey(collaborateContractReceiveMaterial.getReceiveMaterial())) {
						StockModel stockModel = stockMap.get(collaborateContractReceiveMaterial.getReceiveMaterial());
						collaborateOutboundPageRpcDTO.setShippingSpaces(stockModel.getShippingSpace());
						collaborateOutboundPageRpcDTO.setTotalInventory(stockModel.getTotalInventory());
						collaborateOutboundPageRpcDTO.setLockInInventor(stockModel.getLockInInventor());
						collaborateOutboundPageRpcDTO.setStockCode(stockModel.getStockCode());
						collaborateOutboundPageRpcDTO.setStockInitState(stockModel.getStockInitState());
					}

					collaborateOutboundPageRpcDTO.setHasOtherContractNeed(false);

					list.add(collaborateOutboundPageRpcDTO);
				}

			}


			//设置在途量
			Map<Long, CollaboratePurchaseContractInfoRpcDTO> wayNumMap = collaborateProduceDistributeService.getOnWayNumMap(list.stream().map(CollaborateOutboundPageRpcDTO::getCollaborateMaterial).toList());
			list.forEach(a -> {
				if (ObjectUtil.isNotEmpty(wayNumMap.getOrDefault(a.getCollaborateMaterial(), null))) {
					a.setOnWayNum(wayNumMap.getOrDefault(a.getCollaborateMaterial(), null).getOnWayNum());
					a.setReceivingNoIntoStockNum(wayNumMap.getOrDefault(a.getCollaborateMaterial(), null).getReceivingNoIntoStockNum());
				} else {
					a.setOnWayNum(BigDecimal.ZERO);
					a.setReceivingNoIntoStockNum(BigDecimal.ZERO);
				}
			});


			//设置是否可以出库
			list.forEach(e -> {
				//库存可用量
				BigDecimal canUseStockNum = e.getTotalInventory().subtract(e.getLockInInventor());
				//设置库存
				e.setTotalInventory(canUseStockNum.compareTo(BigDecimal.ZERO) >= 0 ? canUseStockNum : BigDecimal.ZERO);
				//是否能出库: 有可用库存即可出库
				e.setCanOut(canUseStockNum.compareTo(BigDecimal.ZERO) > 0);
			});


			return list;


		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("fail to query ContractReceiveMaterialList").build());
		}
	}


	/**
	 * @param contractCode
	 * @return
	 * @throws ExceptionPack
	 * @description 查询合同收货物料列表
	 * @author chengxinyu
	 * @date 2025-08-19 14:00
	 */
	public CollaborateContractReceiveMaterials queryContractReceiveMaterials(Long contractCode) throws ExceptionPack {
		try {
			//1.查询合同中物料
			List<CollaborateScheduleItemModel> contractMaterials = queryContractMaterials(contractCode);

			// 生产物料编号
			Set<Long> produceMaterialCodes = queryContractProduceMaterials(contractMaterials).stream().map(CollaborateScheduleItemModel::getCollaborateMaterial).collect(Collectors.toSet());


			// 有需求量的外发物料
			List<CollaborateScheduleItemModel> hasDemandOutSendMaterialModels = queryContractOutSendMaterialsHasDemand(contractMaterials);

			if (CollUtil.isEmpty(hasDemandOutSendMaterialModels)) {
				return new CollaborateContractReceiveMaterials();
			}

			//{外发物料编号}-{工序编号}->相关协作安排外发物料
			Map<String, List<CollaborateScheduleItemModel>> materialCodeAndProcedureCode2CollaborateScheduleItemModels = new HashMap<>();

			//2.查询这些外发物料以及对应工序的外发物料数据，得到收货物料信息
			Set<Long> materialCodesNeedQuery = new HashSet<>();
			Set<Long> procedureCodesNeedQuery = new HashSet<>();
			for (CollaborateScheduleItemModel hasDemandOutSendMaterialModel : hasDemandOutSendMaterialModels) {
				materialCodesNeedQuery.add(hasDemandOutSendMaterialModel.getCollaborateMaterial());
				for (MaterialSimpleInfo materialSimpleInfo : hasDemandOutSendMaterialModel.materialDemandInfo()) {
					if (materialSimpleInfo.getProduceCode() != null) {
						procedureCodesNeedQuery.add(materialSimpleInfo.getProduceCode());
						String materialCodeAndProcedureCode = StrUtil.format("{}-{}", hasDemandOutSendMaterialModel.getCollaborateMaterial(), materialSimpleInfo.getProduceCode());
						materialCodeAndProcedureCode2CollaborateScheduleItemModels.computeIfAbsent(materialCodeAndProcedureCode, k -> new ArrayList<>())
								.add(hasDemandOutSendMaterialModel);
					}
				}
			}

			Long enterpriseCode = InfoPenetrateProcessor.INSTANCE.getPenetrateInfoNotNull(GlobalTokenPenetrate.ENTERPRISE_CODE_THREAD_LOCAL);
			// 采购模块-外发物料表
			String collaborateOutSendMaterialTableName = tableFactory.getTableName(tableFactory.module.getPurchase(),
					tableFactory.table.getCollaborateOutSendMaterial());

			LambdaQueryWrapper<CollaborateOutSendMaterialModel> outSendMaterialWrapper = Wrappers.lambdaQuery();
			outSendMaterialWrapper.eq(CollaborateOutSendMaterialModel::getEnterpriseCode, enterpriseCode);
			outSendMaterialWrapper.eq(CollaborateOutSendMaterialModel::getDeleteFlag, false);
			outSendMaterialWrapper.in(CollaborateOutSendMaterialModel::getOutSendMaterial, materialCodesNeedQuery);
			outSendMaterialWrapper.in(CollaborateOutSendMaterialModel::getProduceCode, procedureCodesNeedQuery);
			outSendMaterialWrapper.isNotNull(CollaborateOutSendMaterialModel::getReceiveMaterial);

			RequestTableHelper.setTableName(collaborateOutSendMaterialTableName);
			List<CollaborateOutSendMaterialModel> contractOutSendMaterialModels = collaborateOutSendMaterialMapper.selectList(outSendMaterialWrapper);

			if (CollUtil.isEmpty(contractOutSendMaterialModels)) {
				return new CollaborateContractReceiveMaterials();
			}

			Map<Long, List<CollaborateOutSendMaterialModel>> receiveMaterial2OutSendMaterialModel = contractOutSendMaterialModels.stream().filter(p -> p.getReceiveMaterial() != null)
					.collect(Collectors.groupingBy(CollaborateOutSendMaterialModel::getReceiveMaterial));


			CollaborateContractReceiveMaterials collaborateContractReceiveMaterials = new CollaborateContractReceiveMaterials();
			Map<Long, CollaborateContractReceiveMaterial> receiveMaterialInfos = new HashMap<>();
			for (Map.Entry<Long, List<CollaborateOutSendMaterialModel>> entry : receiveMaterial2OutSendMaterialModel.entrySet()) {

				//收货物料编号
				Long receiveMaterial = entry.getKey();
				if (produceMaterialCodes.contains(receiveMaterial)) {
					continue; // 如果收货物料本身就是生产物料，不用关注
				}

				//相关外发物料信息
				List<CollaborateOutSendMaterialModel> outSendMaterialModels = entry.getValue();

				//相关协作安排外发物料信息
				List<CollaborateScheduleItemModel> scheduleOutSendMaterials = new ArrayList<>();
				for (CollaborateOutSendMaterialModel collaborateOutSendMaterialModel : outSendMaterialModels) {
					String materialCodeAndProcedureCode = StrUtil.format("{}-{}", collaborateOutSendMaterialModel.getOutSendMaterial(), collaborateOutSendMaterialModel.getProduceCode());
					List<CollaborateScheduleItemModel> scheduleItemModels = materialCodeAndProcedureCode2CollaborateScheduleItemModels.get(materialCodeAndProcedureCode);
					if (scheduleItemModels != null && !scheduleItemModels.isEmpty()) {
						scheduleOutSendMaterials.addAll(scheduleItemModels);
					}
				}

				CollaborateContractReceiveMaterial collaborateContractReceiveMaterial = new CollaborateContractReceiveMaterial();
				collaborateContractReceiveMaterial.setReceiveMaterial(receiveMaterial);
				collaborateContractReceiveMaterial.setOutSendMaterialModels(outSendMaterialModels);
				collaborateContractReceiveMaterial.setScheduleOutSendMaterials(scheduleOutSendMaterials);
				receiveMaterialInfos.put(entry.getKey(), collaborateContractReceiveMaterial);
			}
			collaborateContractReceiveMaterials.setReceiveMaterialInfos(receiveMaterialInfos);
			return collaborateContractReceiveMaterials;

		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("fail to query ContractReceiveMaterialList").build());
		}
	}

	/**
	 * @param contractMaterials
	 * @return
	 * @throws ExceptionPack
	 * @description 查询合同中的生产物料
	 * @author chengxinyu
	 * @date 2025-08-19 14:08
	 */
	private List<CollaborateScheduleItemModel> queryContractProduceMaterials(List<CollaborateScheduleItemModel> contractMaterials) throws ExceptionPack {
		try {

			List<CollaborateScheduleItemModel> scheduleProduceMaterialModels = contractMaterials.stream().filter(p -> p.getMaterialNature() == MaterialNatureEnum.PRODUCE).collect(Collectors.toList());


			if (CollUtil.isEmpty(scheduleProduceMaterialModels)) {
				return List.of();
			}
			//只保留需求量大于0的数据
			return scheduleProduceMaterialModels;


		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("fail to query ContractProduceMaterials").build());
		}
	}

	/**
	 * @param contractMaterials
	 * @return
	 * @throws ExceptionPack
	 * @description 查询合同中有需求量(需求量大于0)的外发物料
	 * @author chengxinyu
	 * @date 2025-08-19 14:08
	 */
	private List<CollaborateScheduleItemModel> queryContractOutSendMaterialsHasDemand(List<CollaborateScheduleItemModel> contractMaterials) throws ExceptionPack {
		try {

			List<CollaborateScheduleItemModel> scheduleOutSendMaterialModels = contractMaterials.stream().filter(p -> p.getMaterialNature() == MaterialNatureEnum.OUT_SEND).collect(Collectors.toList());


			if (CollUtil.isEmpty(scheduleOutSendMaterialModels)) {
				return List.of();
			}
			//只保留需求量大于0的数据
			return scheduleOutSendMaterialModels.stream()
					.filter(p -> p.getOrderQuantity().subtract(p.getScheduleQuantity()).compareTo(BigDecimal.ZERO) > 0)
					.collect(Collectors.toList());


		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("fail to query ContractOutSendMaterialsHasDemand").build());
		}
	}

	/**
	 * @param contractCode
	 * @return
	 * @throws ExceptionPack
	 * @description 查询合同中所有物料
	 * @author chengxinyu
	 * @date 2025-08-19 14:08
	 */
	private List<CollaborateScheduleItemModel> queryContractMaterials(Long contractCode) throws ExceptionPack {
		try {

			Long enterpriseCode = InfoPenetrateProcessor.INSTANCE.getPenetrateInfoNotNull(GlobalTokenPenetrate.ENTERPRISE_CODE_THREAD_LOCAL);
			// 生产模块-协作安排表
			String collaborateScheduleItemTableName = tableFactory.getTableName(tableFactory.module.getProduction(),
					tableFactory.table.getCollaborateScheduleItem());

			LambdaQueryWrapper<CollaborateScheduleItemModel> scheduleItemWrapper = Wrappers.lambdaQuery();
			scheduleItemWrapper.eq(CollaborateScheduleItemModel::getContractCode, contractCode);
			scheduleItemWrapper.eq(CollaborateScheduleItemModel::getCollaborateType, 1);
			scheduleItemWrapper.eq(CollaborateScheduleItemModel::getEnterpriseCode, enterpriseCode);
			scheduleItemWrapper.eq(CollaborateScheduleItemModel::getDeleteFlag, false);
			RequestTableHelper.setTableName(collaborateScheduleItemTableName);
			List<CollaborateScheduleItemModel> scheduleOutSendMaterialModels = collaborateScheduleItemMapper.selectList(scheduleItemWrapper);


			if (CollUtil.isEmpty(scheduleOutSendMaterialModels)) {
				return List.of();
			}
			return scheduleOutSendMaterialModels;


		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("fail to query ContractMaterials").build());
		}
	}

}
