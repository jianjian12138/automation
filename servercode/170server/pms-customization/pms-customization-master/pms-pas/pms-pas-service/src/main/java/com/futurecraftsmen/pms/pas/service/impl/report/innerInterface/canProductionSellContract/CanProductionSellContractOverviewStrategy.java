package com.futurecraftsmen.pms.pas.service.impl.report.innerInterface.canProductionSellContract;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.futurecraftsmen.pms.dm.api.service.report.dto.DataSetQueryResultRpcDTO;
import com.futurecraftsmen.pms.dm.api.service.report.dto.ReportItemInfoRpcDTO;
import com.futurecraftsmen.pms.pas.service.impl.inner.SellContractInnerService;
import com.futurecraftsmen.pms.pas.service.impl.report.innerInterface.InnerInterfaceStrategy;
import org.aerie.forest.core.brick.exception.ExceptionMsg;
import org.aerie.forest.core.brick.exception.ExceptionPack;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * @author chengxinyu
 * @description 待生产合同概览数据集查询
 * @organization futurecraftsmen
 * @date 2025-05-16 14:43
 */
@Service
public class CanProductionSellContractOverviewStrategy implements InnerInterfaceStrategy {


	@Autowired
	private SellContractInnerService sellContractInnerService;


	@Override
	public String innerInterfaceIdentity() {
		return CAN_PRODUCTION_SELL_CONTRACT_OVER_VIEW;
	}

	/**
	 * 查询当前人员可见的可安排生产销售合同概览信息
	 * 忽略分页信息
	 *
	 * @param dataSetParams
	 * @param page
	 * @return
	 * @throws ExceptionPack
	 */
	@Override
	public DataSetQueryResultRpcDTO execute(Map<String, Object> dataSetParams, Page page) throws ExceptionPack {
		try {


			//待生产合同数
			long canProductionContractNumForCurrentUser = sellContractInnerService.canProductionContractNumForCurrentUser();


			DataSetQueryResultRpcDTO dataSetQueryResultRpcDTO = new DataSetQueryResultRpcDTO();
			dataSetQueryResultRpcDTO.setData(null);

			//主要指标： 待生产合同数
			ReportItemInfoRpcDTO mainQuota = new ReportItemInfoRpcDTO();
			mainQuota.setKey("待生产合同数");
			mainQuota.setName(canProductionContractNumForCurrentUser + "");
			dataSetQueryResultRpcDTO.setMainQuota(mainQuota);

			return dataSetQueryResultRpcDTO;

		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("fail to execute").build());
		}
	}


}
