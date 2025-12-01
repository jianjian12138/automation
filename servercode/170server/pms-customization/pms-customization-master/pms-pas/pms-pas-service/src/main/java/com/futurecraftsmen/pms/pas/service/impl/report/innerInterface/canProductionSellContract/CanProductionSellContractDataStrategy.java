package com.futurecraftsmen.pms.pas.service.impl.report.innerInterface.canProductionSellContract;

import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.futurecraftsmen.pms.api.dto.base.RpcPagingDTO;
import com.futurecraftsmen.pms.common.utils.DateUtils;
import com.futurecraftsmen.pms.dm.api.service.report.dto.DataSetQueryResultRpcDTO;
import com.futurecraftsmen.pms.dm.api.service.report.dto.ReportItemInfoRpcDTO;
import com.futurecraftsmen.pms.pas.api.rpc.contract.sell.CanProductionSellContractRpcDTO;
import com.futurecraftsmen.pms.pas.service.impl.inner.SellContractInnerService;
import com.futurecraftsmen.pms.pas.service.impl.report.innerInterface.InnerInterfaceStrategy;
import com.futurecraftsmen.pms.service.util.CommonUtil;
import org.aerie.forest.core.brick.exception.ExceptionMsg;
import org.aerie.forest.core.brick.exception.ExceptionPack;
import org.aerie.forest.core.brick.rightcharacteristics.RightCharacteristics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author chengxinyu
 * @description 待生产合同数据-数据集查询
 * @organization futurecraftsmen
 * @date 2025-05-16 14:43
 */
@Service
public class CanProductionSellContractDataStrategy implements InnerInterfaceStrategy {


	@Autowired
	private SellContractInnerService sellContractInnerService;


	@Override
	public String innerInterfaceIdentity() {
		return CAN_PRODUCTION_SELL_CONTRACT_DATA;
	}

	/**
	 * 查询当前人员可见的可安排生产销售合同
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
			if (page == null) {
				page = new Page(1, 5);
			}


			//参数:指定开始日期
			String startData = startDate(dataSetParams);

			//参数:指定结束日期
			String endData = endDate(dataSetParams);


			// 转换为 Date 对象，时间为当天的 0点0分0秒
			Date contractTimePreDate =
					DateUtils.getDateWithMidnight(startData);

			// 转换为 Date 对象，时间为当天的 23点59分59秒
			Date contractTimePostDate =
					DateUtils.getDateWithEndOfDay(endData);

			// 表头信息
			DataSetQueryResultRpcDTO dataSetQueryResultRpcDTO = new DataSetQueryResultRpcDTO();

			List<ReportItemInfoRpcDTO> tableTitles = new ArrayList<>();
			tableTitles.add(new ReportItemInfoRpcDTO("contractNumber", "合同单号"));
			tableTitles.add(new ReportItemInfoRpcDTO("buyerName", "公司名称"));
			tableTitles.add(new ReportItemInfoRpcDTO("amount", "合同金额"));
			tableTitles.add(new ReportItemInfoRpcDTO("stateDesc", "状态"));
			tableTitles.add(new ReportItemInfoRpcDTO("userName", "销售"));
			tableTitles.add(new ReportItemInfoRpcDTO("contractTime", "合同日期"));
			dataSetQueryResultRpcDTO.setTableTitles(tableTitles);


			RpcPagingDTO<CanProductionSellContractRpcDTO> queryResult = sellContractInnerService.canProductionContractPaging(page, contractTimePreDate, contractTimePostDate);

			dataSetQueryResultRpcDTO.setTotal((long) queryResult.getTotalNum());


			RightCharacteristics rightCharacteristics = CommonUtil.getEnterpriseRightCharacteristics();

			List<String> functionKeys = new ArrayList<>();
			if (rightCharacteristics.isProcedureProcessRoute()) {
				functionKeys.add("production"); //有工艺路线
			} else {
				functionKeys.add("autoSchedule"); //无工艺路线
			}

			for (CanProductionSellContractRpcDTO contract : queryResult.getPageDetails()) {
				JSONObject data = new JSONObject();
				data.put("contractCode", contract.getContractCode());
				data.put("contractNumber", contract.getContractNumber());
				data.put("buyerName", contract.getBuyerName());
				data.put("amount", contract.getAmount());
				data.put("stateDesc", contract.getStateDesc());
				data.put("userName", contract.getUserName());
				data.put("contractTime", formatDateValue(contract.getContractTime(), "yyyy-MM-dd HH:mm:ss"));
				dataSetQueryResultRpcDTO.addDataWithFunctionKeys(data, functionKeys);
			}

			return dataSetQueryResultRpcDTO;

		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("fail to execute").build());
		}
	}


}
