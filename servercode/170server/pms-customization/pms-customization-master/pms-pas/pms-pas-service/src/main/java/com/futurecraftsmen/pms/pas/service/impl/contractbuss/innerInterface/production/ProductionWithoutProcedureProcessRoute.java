package com.futurecraftsmen.pms.pas.service.impl.contractbuss.innerInterface.production;


import com.futurecraftsmen.pms.api.dto.base.RpcPagingDTO;
import com.futurecraftsmen.pms.common.utils.DateUtils;
import com.futurecraftsmen.pms.pas.api.domain.ContractBussDataDimension;
import com.futurecraftsmen.pms.pas.api.rpc.contractbuss.*;
import com.futurecraftsmen.pms.pas.api.rpc.produce.DevInfo;
import com.futurecraftsmen.pms.pas.api.rpc.produce.DevProduceRpcDTO;
import com.futurecraftsmen.pms.pas.api.rpc.produce.ProduceDetailRpcDTO;
import com.futurecraftsmen.pms.pas.api.rpc.sellorder.ProduceDetailConditionDTO;
import com.futurecraftsmen.pms.pas.api.service.produce.ProducePageDetailService;
import com.futurecraftsmen.pms.pas.service.core.contract.sell.AbstractSellContract;
import com.futurecraftsmen.pms.pas.service.impl.contractbuss.innerInterface.ContractBussInnerInterfaceStrategy;
import com.futurecraftsmen.pms.pas.service.impl.inner.SellContractInnerService;
import com.futurecraftsmen.pms.right.api.domain.CustomPageEnum;
import jakarta.annotation.Resource;
import org.aerie.forest.core.brick.domain.view.CodeMapName;
import org.aerie.forest.core.brick.exception.ExceptionMsg;
import org.aerie.forest.core.brick.exception.ExceptionPack;
import org.aerie.forest.core.brick.rightcharacteristics.RightCharacteristics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


/**
 * @author chengxinyu
 * @description 无工艺路线-生产派工
 * @organization futurecraftsmen
 * @date 2025-06-16 16:52
 */
@Service
public class ProductionWithoutProcedureProcessRoute implements ContractBussInnerInterfaceStrategy {

	@Resource
	private ProducePageDetailService produceService;

	@Autowired
	private SellContractInnerService sellContractInnerService;


	@Override
	public CustomPageEnum customPage() {
		return CustomPageEnum.NEW_TECHNICAL_PRODUCTION_XH;
	}

	@Override
	public ContractBussDataQueryResultRpcDTO executeForData(ContractBussDataQueryParam contractBussDataQueryParam, RightCharacteristics rightCharacteristics) throws ExceptionPack {
		try {
			ProduceDetailConditionDTO detailDTO = new ProduceDetailConditionDTO();

			detailDTO.setCurrent(contractBussDataQueryParam.getCurrent());
			detailDTO.setSize(contractBussDataQueryParam.getSize());

			detailDTO.setGxCondition(1);



			//查询销售合同信息
			AbstractSellContract sellContract = sellContractInnerService.signedContractDetail(contractBussDataQueryParam.getContractCode());

			detailDTO.setContractCode(contractBussDataQueryParam.getContractCode()); //指定合同的数据

			detailDTO.setPartUnityNo(contractBussDataQueryParam.getUnityNo());

			detailDTO.setSearchName(contractBussDataQueryParam.getMaterial());

			List<Integer> conditionStatus = new ArrayList<>();
			if (contractBussDataQueryParam.getDataDimension() == ContractBussDataDimension.NOT_STARTED) {
				//未开始
				conditionStatus.add(0); //未分配
				detailDTO.setStatusList(conditionStatus);
			}

			if (contractBussDataQueryParam.getDataDimension() == ContractBussDataDimension.RUNNING) {
				//未开始
				conditionStatus.add(10); //配料中,已配齐,待生产,生产中
				conditionStatus.add(20);
				conditionStatus.add(30);
				conditionStatus.add(40);
				detailDTO.setStatusList(conditionStatus);
			}

			if (contractBussDataQueryParam.getDataDimension() == ContractBussDataDimension.FINISHED) {
				//已完成
				conditionStatus.add(50); //已完成
				detailDTO.setStatusList(conditionStatus);
			}


			if (contractBussDataQueryParam.getStartRange() != null) {
				detailDTO.setBeginDate(DateUtils.getDateWithMidnight(contractBussDataQueryParam.getStartRange()));
			}
			if (contractBussDataQueryParam.getEndRange() != null) {
				detailDTO.setEndDate(DateUtils.getDateWithMidnight(contractBussDataQueryParam.getEndRange())); // 特殊,因为业务接口 pageListDataScope 会调整为 23:59:59
			}


			RpcPagingDTO<ProduceDetailRpcDTO> pageListDataScope = produceService.pageListDataScope(detailDTO);


			ContractBussDataQueryResultRpcDTO contractBussDataQueryResultRpcDTO = new ContractBussDataQueryResultRpcDTO();

			contractBussDataQueryResultRpcDTO.setBussNumber(sellContract.getContractNumber());

			contractBussDataQueryResultRpcDTO.setTotal(pageListDataScope.getTotalNum());

			List<ContractBussDataFieldTitleRpcDTO> titles = new ArrayList<>();


			titles.add(new ContractBussDataFieldTitleRpcDTO("partUnityNo", "产品编号", true));

			titles.add(new ContractBussDataFieldTitleRpcDTO("productOrPartName", "名称", true));

			titles.add(new ContractBussDataFieldTitleRpcDTO("model", "型号规格", true));

			titles.add(new ContractBussDataFieldTitleRpcDTO("deliveryDate", "产品交期", true));

			titles.add(new ContractBussDataFieldTitleRpcDTO("taskNum", "任务量", true));

			titles.add(new ContractBussDataFieldTitleRpcDTO("allocationNum", "已分派量", false));

			titles.add(new ContractBussDataFieldTitleRpcDTO("finishNum", "已完成量", false));

			titles.add(new ContractBussDataFieldTitleRpcDTO("pcsName", "单位", false));

			titles.add(new ContractBussDataFieldTitleRpcDTO("operatorInfo", "操作工", false));

			titles.add(new ContractBussDataFieldTitleRpcDTO("devInfo", "生产设备", false));

			titles.add(new ContractBussDataFieldTitleRpcDTO("statusName", "状态", false));

			titles.add(new ContractBussDataFieldTitleRpcDTO("createDate", "创建时间", false));

			titles.add(new ContractBussDataFieldTitleRpcDTO("remark", "备注", false));


			contractBussDataQueryResultRpcDTO.setTitles(titles);


			for (ProduceDetailRpcDTO produceDetailRpcDTO : pageListDataScope.getPageDetails()) {
				ContractBussDataRpcDTO contractBussDataRpcDTO = new ContractBussDataRpcDTO();
				contractBussDataRpcDTO.addData("primaryKey", produceDetailRpcDTO.getPrimaryKeyValue() + "");
				contractBussDataRpcDTO.addData("partUnityNo", produceDetailRpcDTO.getPartUnityNo());
				contractBussDataRpcDTO.addData("productOrPartName", produceDetailRpcDTO.getProductOrPartName());
				contractBussDataRpcDTO.addData("model", produceDetailRpcDTO.getModel());
				contractBussDataRpcDTO.addData("deliveryDate", formatDate(produceDetailRpcDTO.getDeliveryDate(), "yyyy-MM-dd"));
				contractBussDataRpcDTO.addData("taskNum", bigDecimalSerializer(produceDetailRpcDTO.getTaskNum()));

				contractBussDataRpcDTO.addData("allocationNum", bigDecimalSerializer(produceDetailRpcDTO.getAllocationNum()));
				contractBussDataRpcDTO.addData("finishNum", bigDecimalSerializer(produceDetailRpcDTO.getFinishNum()));
				contractBussDataRpcDTO.addData("pcsName", produceDetailRpcDTO.getPcsName());

				if (produceDetailRpcDTO.getOperatorInfo() != null) {
					contractBussDataRpcDTO.addData("operatorInfo", produceDetailRpcDTO.getOperatorInfo().stream()
							.map(CodeMapName::getName) // 提取 name 属性
							.collect(Collectors.joining(",")));
				} else {
					contractBussDataRpcDTO.addData("operatorInfo", "");
				}

				if (produceDetailRpcDTO.getDevInfo() != null && produceDetailRpcDTO.getDevInfo().getDevProduceList() != null) {
					List<DevInfo> devInfos = new ArrayList<>();

					for (DevProduceRpcDTO devProduceRpcDTO : produceDetailRpcDTO.getDevInfo().getDevProduceList()) {
						if (devProduceRpcDTO.getDevInfos() != null && !devProduceRpcDTO.getDevInfos().isEmpty()) {
							devInfos.addAll(devProduceRpcDTO.getDevInfos());
						}
					}

					contractBussDataRpcDTO.addData("devInfo", devInfos.stream()
							.map(DevInfo::getDevName) // 提取 devName 属性
							.collect(Collectors.joining(",")));
				} else {
					contractBussDataRpcDTO.addData("devInfo", "");
				}


				contractBussDataRpcDTO.addData("statusName", produceDetailRpcDTO.getStatusName());

				contractBussDataRpcDTO.addData("createDate", formatDate(produceDetailRpcDTO.getCreateDate(), "yyyy-MM-dd HH:mm"));

				contractBussDataRpcDTO.addData("remark", produceDetailRpcDTO.getRemark());


				//判断是否有分发按钮
				if (produceDetailRpcDTO.isCanDistribute()) {
					contractBussDataRpcDTO.addFunction(ContractBussDataFunctionIdentityEnum.PRODUCTION_DISTRIBUTE);
				}

				//判断是否有派工按钮
				if (produceDetailRpcDTO.getStatus() != 50 && (produceDetailRpcDTO.getAllocationNum() == null ||
						produceDetailRpcDTO.getTaskNum().compareTo(produceDetailRpcDTO.getAllocationNum()) > 0)) {
					contractBussDataRpcDTO.addFunction(ContractBussDataFunctionIdentityEnum.PRODUCTION_DISPATCH);
				}

				contractBussDataQueryResultRpcDTO.addData(contractBussDataRpcDTO);
			}


			return contractBussDataQueryResultRpcDTO;


		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("fail to execute for data").build());
		}
	}

	@Override
	public ContractBussDataQueryResultRpcDTO executeForDetail(ContractBussDataQueryParam contractBussDataQueryParam, RightCharacteristics rightCharacteristics) throws ExceptionPack {
		try {
			ProduceDetailConditionDTO detailDTO = new ProduceDetailConditionDTO();

			detailDTO.setCurrent(contractBussDataQueryParam.getCurrent());
			detailDTO.setSize(contractBussDataQueryParam.getSize());

			//查询销售合同信息
			AbstractSellContract sellContract = sellContractInnerService.signedContractDetail(contractBussDataQueryParam.getContractCode());

			detailDTO.setContractCode(contractBussDataQueryParam.getContractCode()); //指定合同的数据

			detailDTO.setPartUnityNo(contractBussDataQueryParam.getUnityNo());

			detailDTO.setSearchName(contractBussDataQueryParam.getMaterial());

			List<Integer> conditionStatus = new ArrayList<>();
			if (contractBussDataQueryParam.getDataDimension() == ContractBussDataDimension.NOT_STARTED) {
				//未开始
				conditionStatus.add(0); //未分配
				detailDTO.setStatusList(conditionStatus);
			}

			if (contractBussDataQueryParam.getDataDimension() == ContractBussDataDimension.RUNNING) {
				//未开始
				conditionStatus.add(10); //配料中,已配齐,待生产,生产中
				conditionStatus.add(20);
				conditionStatus.add(30);
				conditionStatus.add(40);
				detailDTO.setStatusList(conditionStatus);
			}

			if (contractBussDataQueryParam.getDataDimension() == ContractBussDataDimension.FINISHED) {
				//已完成
				conditionStatus.add(50); //已完成
				detailDTO.setStatusList(conditionStatus);
			}


			if (contractBussDataQueryParam.getStartRange() != null) {
				detailDTO.setBeginDate(DateUtils.getDateWithMidnight(contractBussDataQueryParam.getStartRange()));
			}
			if (contractBussDataQueryParam.getEndRange() != null) {
				detailDTO.setEndDate(DateUtils.getDateWithMidnight(contractBussDataQueryParam.getEndRange())); // 特殊,因为业务接口 pageListDataScope 会调整为 23:59:59
			}


			RpcPagingDTO<ProduceDetailRpcDTO> pageListDataScope = produceService.pageAllocationList(detailDTO);


			ContractBussDataQueryResultRpcDTO contractBussDataQueryResultRpcDTO = new ContractBussDataQueryResultRpcDTO();

			contractBussDataQueryResultRpcDTO.setBussNumber(sellContract.getContractNumber());

			contractBussDataQueryResultRpcDTO.setTotal(pageListDataScope.getTotalNum());

			List<ContractBussDataFieldTitleRpcDTO> titles = new ArrayList<>();


			titles.add(new ContractBussDataFieldTitleRpcDTO("produceUnityNo", "生产批次号", true));


			titles.add(new ContractBussDataFieldTitleRpcDTO("partUnityNo", "产品编号", true));

			titles.add(new ContractBussDataFieldTitleRpcDTO("productOrPartName", "名称", true));

			titles.add(new ContractBussDataFieldTitleRpcDTO("model", "型号规格", true));

			titles.add(new ContractBussDataFieldTitleRpcDTO("finishDate", "需求完成时间", true));

			titles.add(new ContractBussDataFieldTitleRpcDTO("taskNum", "任务量", true));


			titles.add(new ContractBussDataFieldTitleRpcDTO("finishNum", "已完成量", true));

			titles.add(new ContractBussDataFieldTitleRpcDTO("operatorName", "操作工", true));

			titles.add(new ContractBussDataFieldTitleRpcDTO("pcsName", "单位", false));


			titles.add(new ContractBussDataFieldTitleRpcDTO("finishReportDate", "完成时间", false));

			titles.add(new ContractBussDataFieldTitleRpcDTO("overdueDay", "逾期（天）", false));

			titles.add(new ContractBussDataFieldTitleRpcDTO("statusName", "状态", false));

			titles.add(new ContractBussDataFieldTitleRpcDTO("remark", "备注", false));


			contractBussDataQueryResultRpcDTO.setTitles(titles);


			for (ProduceDetailRpcDTO produceDetailRpcDTO : pageListDataScope.getPageDetails()) {
				ContractBussDataRpcDTO contractBussDataRpcDTO = new ContractBussDataRpcDTO();
				contractBussDataRpcDTO.addData("primaryKey", produceDetailRpcDTO.getPrimaryKeyValue() + "");
				contractBussDataRpcDTO.addData("produceUnityNo", produceDetailRpcDTO.getProduceUnityNo());
				contractBussDataRpcDTO.addData("partUnityNo", produceDetailRpcDTO.getPartUnityNo());
				contractBussDataRpcDTO.addData("productOrPartName", produceDetailRpcDTO.getProductOrPartName());
				contractBussDataRpcDTO.addData("model", produceDetailRpcDTO.getModel());
				contractBussDataRpcDTO.addData("finishDate", formatDate(produceDetailRpcDTO.getFinishDate(), "yyyy-MM-dd"));
				contractBussDataRpcDTO.addData("taskNum", bigDecimalSerializer(produceDetailRpcDTO.getTaskNum()));
				contractBussDataRpcDTO.addData("finishNum", bigDecimalSerializer(produceDetailRpcDTO.getFinishNum()));
				contractBussDataRpcDTO.addData("pcsName", produceDetailRpcDTO.getPcsName());

				contractBussDataRpcDTO.addData("operatorName", produceDetailRpcDTO.getOperatorName());


				contractBussDataRpcDTO.addData("finishReportDate", formatDate(produceDetailRpcDTO.getFinishReportDate(), "yyyy-MM-dd HH:mm:ss"));


				contractBussDataRpcDTO.addData("overdueDay", produceDetailRpcDTO.getOverdueDay() == null ? "" : produceDetailRpcDTO.getOverdueDay() + "");


				contractBussDataRpcDTO.addData("statusName", produceDetailRpcDTO.getStatusName());

				contractBussDataRpcDTO.addData("remark", produceDetailRpcDTO.getRemark());


				contractBussDataQueryResultRpcDTO.addData(contractBussDataRpcDTO);
			}


			return contractBussDataQueryResultRpcDTO;


		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("fail to execute For Detail").build());
		}
	}
}
