package com.futurecraftsmen.pms.pas.service.impl.contractbuss.innerInterface.productionstaff;


import com.futurecraftsmen.pms.api.dto.base.RpcPagingDTO;
import com.futurecraftsmen.pms.common.utils.DateUtils;
import com.futurecraftsmen.pms.pas.api.domain.ContractBussDataDimension;
import com.futurecraftsmen.pms.pas.api.rpc.contractbuss.*;
import com.futurecraftsmen.pms.pas.api.rpc.produce.ProduceDetailRpcDTO;
import com.futurecraftsmen.pms.pas.api.rpc.sellorder.ProduceDetailConditionDTO;
import com.futurecraftsmen.pms.pas.api.service.produce.ProducePageDetailService;
import com.futurecraftsmen.pms.pas.service.core.contract.sell.AbstractSellContract;
import com.futurecraftsmen.pms.pas.service.impl.contractbuss.innerInterface.ContractBussInnerInterfaceStrategy;
import com.futurecraftsmen.pms.pas.service.impl.inner.SellContractInnerService;
import com.futurecraftsmen.pms.right.api.domain.CustomPageEnum;
import jakarta.annotation.Resource;
import org.aerie.forest.core.brick.exception.ExceptionMsg;
import org.aerie.forest.core.brick.exception.ExceptionPack;
import org.aerie.forest.core.brick.rightcharacteristics.RightCharacteristics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;


/**
 * @author chengxinyu
 * @description 有工艺路线-生产任务
 * @organization futurecraftsmen
 * @date 2025-06-16 16:52
 */
@Service
public class ProductionStaffWithProcedureProcessRoute implements ContractBussInnerInterfaceStrategy {

	@Resource
	private ProducePageDetailService produceService;

	@Autowired
	private SellContractInnerService sellContractInnerService;


	@Override
	public CustomPageEnum customPage() {
		return CustomPageEnum.NEW_TECHNICAL_PRODUCTION_STAFF;
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


			if (contractBussDataQueryParam.getDataDimension() == ContractBussDataDimension.NOT_STARTED) {
				//未开始
				detailDTO.setStatus(30); //待生产
			}

			if (contractBussDataQueryParam.getDataDimension() == ContractBussDataDimension.RUNNING) {
				//未开始
				detailDTO.setStatus(40); //生产中
			}

			if (contractBussDataQueryParam.getDataDimension() == ContractBussDataDimension.FINISHED) {
				//已完成
				detailDTO.setStatus(50); //已完成
			}


			if (contractBussDataQueryParam.getStartRange() != null) {
				detailDTO.setBeginDate(DateUtils.getDateWithMidnight(contractBussDataQueryParam.getStartRange()));
			}
			if (contractBussDataQueryParam.getEndRange() != null) {
				detailDTO.setEndDate(DateUtils.getDateWithMidnight(contractBussDataQueryParam.getEndRange())); // 特殊,因为业务接口 pageListDataScope 会调整为 23:59:59
			}


			RpcPagingDTO<ProduceDetailRpcDTO> pageListDataScope = produceService.pageEmployeeList(detailDTO);


			ContractBussDataQueryResultRpcDTO contractBussDataQueryResultRpcDTO = new ContractBussDataQueryResultRpcDTO();

			contractBussDataQueryResultRpcDTO.setBussNumber(sellContract.getContractNumber());

			contractBussDataQueryResultRpcDTO.setTotal(pageListDataScope.getTotalNum());

			List<ContractBussDataFieldTitleRpcDTO> titles = new ArrayList<>();


			titles.add(new ContractBussDataFieldTitleRpcDTO("produceUnityNo", "生产批次号", true));

			titles.add(new ContractBussDataFieldTitleRpcDTO("procedureName", "工序", true));

			titles.add(new ContractBussDataFieldTitleRpcDTO("partUnityNo", "产品编号", true));


			titles.add(new ContractBussDataFieldTitleRpcDTO("productOrPartName", "名称", true));

			titles.add(new ContractBussDataFieldTitleRpcDTO("model", "型号规格", true));

			titles.add(new ContractBussDataFieldTitleRpcDTO("finishDate", "需求完成时间", true));

			titles.add(new ContractBussDataFieldTitleRpcDTO("taskNum", "任务量", true));


			titles.add(new ContractBussDataFieldTitleRpcDTO("finishNum", "已完成量", false));

			titles.add(new ContractBussDataFieldTitleRpcDTO("pcsName", "单位", false));


			titles.add(new ContractBussDataFieldTitleRpcDTO("statusName", "状态", false));


			titles.add(new ContractBussDataFieldTitleRpcDTO("remark", "备注", false));


			contractBussDataQueryResultRpcDTO.setTitles(titles);


			for (ProduceDetailRpcDTO produceDetailRpcDTO : pageListDataScope.getPageDetails()) {
				ContractBussDataRpcDTO contractBussDataRpcDTO = new ContractBussDataRpcDTO();
				contractBussDataRpcDTO.addData("primaryKey", produceDetailRpcDTO.getPrimaryKeyValue() + "");
				contractBussDataRpcDTO.addData("produceUnityNo", produceDetailRpcDTO.getProduceUnityNo());
				contractBussDataRpcDTO.addData("procedureName", produceDetailRpcDTO.getProcedureName());

				contractBussDataRpcDTO.addData("partUnityNo", produceDetailRpcDTO.getPartUnityNo());
				contractBussDataRpcDTO.addData("productOrPartName", produceDetailRpcDTO.getProductOrPartName());
				contractBussDataRpcDTO.addData("model", produceDetailRpcDTO.getModel());
				contractBussDataRpcDTO.addData("finishDate", formatDate(produceDetailRpcDTO.getFinishDate(), "yyyy-MM-dd"));
				contractBussDataRpcDTO.addData("taskNum", bigDecimalSerializer(produceDetailRpcDTO.getTaskNum()));
				contractBussDataRpcDTO.addData("finishNum", bigDecimalSerializer(produceDetailRpcDTO.getFinishNum()));
				contractBussDataRpcDTO.addData("pcsName", produceDetailRpcDTO.getPcsName());


				contractBussDataRpcDTO.addData("statusName", produceDetailRpcDTO.getStatusName());

				contractBussDataRpcDTO.addData("remark", produceDetailRpcDTO.getRemark());


				//判断是否有生产按钮
				if (produceDetailRpcDTO.getStatus() == 30) {
					contractBussDataRpcDTO.addFunction(ContractBussDataFunctionIdentityEnum.PRODUCTION_STAFF_WORK);
				}

				//判断是否有领料按钮
				if (produceDetailRpcDTO.getStatus() == 40 && produceDetailRpcDTO.isMaterialFlag()) {
					contractBussDataRpcDTO.addFunction(ContractBussDataFunctionIdentityEnum.PRODUCTION_STAFF_PICKING);
				}

				//判断是否有报工按钮
				if (produceDetailRpcDTO.getStatus() == 40) {
					contractBussDataRpcDTO.addFunction(ContractBussDataFunctionIdentityEnum.PRODUCTION_STAFF_APPLY);
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

			detailDTO.setGxCondition(1);

			detailDTO.setStatus(50);

			//查询销售合同信息
			AbstractSellContract sellContract = sellContractInnerService.signedContractDetail(contractBussDataQueryParam.getContractCode());

			detailDTO.setContractCode(contractBussDataQueryParam.getContractCode()); //指定合同的数据

			detailDTO.setPartUnityNo(contractBussDataQueryParam.getUnityNo());

			detailDTO.setSearchName(contractBussDataQueryParam.getMaterial());


			if (contractBussDataQueryParam.getStartRange() != null) {
				detailDTO.setBeginDate(DateUtils.getDateWithMidnight(contractBussDataQueryParam.getStartRange()));
			}
			if (contractBussDataQueryParam.getEndRange() != null) {
				detailDTO.setEndDate(DateUtils.getDateWithMidnight(contractBussDataQueryParam.getEndRange())); // 特殊,因为业务接口 pageListDataScope 会调整为 23:59:59
			}


			RpcPagingDTO<ProduceDetailRpcDTO> pageListDataScope = produceService.pageAllocationMyList(detailDTO);


			ContractBussDataQueryResultRpcDTO contractBussDataQueryResultRpcDTO = new ContractBussDataQueryResultRpcDTO();

			contractBussDataQueryResultRpcDTO.setBussNumber(sellContract.getContractNumber());

			contractBussDataQueryResultRpcDTO.setTotal(pageListDataScope.getTotalNum());

			List<ContractBussDataFieldTitleRpcDTO> titles = new ArrayList<>();

			titles.add(new ContractBussDataFieldTitleRpcDTO("produceUnityNo", "生产批次号", true));
			titles.add(new ContractBussDataFieldTitleRpcDTO("procedureName", "工序", true));


			titles.add(new ContractBussDataFieldTitleRpcDTO("partUnityNo", "产品编号", true));

			titles.add(new ContractBussDataFieldTitleRpcDTO("productOrPartName", "名称", true));

			titles.add(new ContractBussDataFieldTitleRpcDTO("model", "型号规格", true));

			titles.add(new ContractBussDataFieldTitleRpcDTO("finishDate", "需求完成时间", true));

			titles.add(new ContractBussDataFieldTitleRpcDTO("taskNum", "任务量", true));


			titles.add(new ContractBussDataFieldTitleRpcDTO("finishNum", "已完成量", true));


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
				contractBussDataRpcDTO.addData("procedureName", produceDetailRpcDTO.getProcedureName());

				contractBussDataRpcDTO.addData("partUnityNo", produceDetailRpcDTO.getPartUnityNo());
				contractBussDataRpcDTO.addData("productOrPartName", produceDetailRpcDTO.getProductOrPartName());
				contractBussDataRpcDTO.addData("model", produceDetailRpcDTO.getModel());
				contractBussDataRpcDTO.addData("finishDate", formatDate(produceDetailRpcDTO.getFinishDate(), "yyyy-MM-dd"));
				contractBussDataRpcDTO.addData("taskNum", bigDecimalSerializer(produceDetailRpcDTO.getTaskNum()));
				contractBussDataRpcDTO.addData("finishNum", bigDecimalSerializer(produceDetailRpcDTO.getFinishNum()));
				contractBussDataRpcDTO.addData("pcsName", produceDetailRpcDTO.getPcsName());


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
