package com.futurecraftsmen.pms.pas.service.impl.report.innerInterface.produceDispatchState;


import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.futurecraftsmen.pms.common.utils.DateUtils;
import com.futurecraftsmen.pms.dm.api.service.report.dto.DataSetQueryResultRpcDTO;
import com.futurecraftsmen.pms.pas.service.impl.report.innerInterface.InnerInterfaceStrategy;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.produce.CollaborateProduceRequest;
import com.futurecraftsmen.pms.technical.api.service.collaborate.CollaborateProduceHandleService;
import org.aerie.forest.core.brick.exception.ExceptionMsg;
import org.aerie.forest.core.brick.exception.ExceptionPack;
import org.aerie.forest.core.brick.processor.datapenetrate.GlobalTokenPenetrate;
import org.aerie.forest.core.brick.processor.datapenetrate.InfoPenetrateProcessor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

/**
 * @author chengxinyu
 * @description 协作安排生产派工状态数据集 数据集查询
 * @organization futurecraftsmen
 * @date 2025-05-16 14:43
 */
@Service
public class CollaborateProduceDispatchStateStrategy implements InnerInterfaceStrategy {


	@DubboReference(check = false)
	private CollaborateProduceHandleService collaborateProduceHandleService;

	@Override
	public String innerInterfaceIdentity() {
		return COLLABORATE_PRODUCE_DISPATCH_STATE;
	}

	/**
	 * 查询指定日期范围的数据
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
			//参数:指定开始日期
			String startData = startDate(dataSetParams);

			//参数:指定结束日期
			String endData = endDate(dataSetParams);


			//企业编号
			Long enterpriseCode = InfoPenetrateProcessor.INSTANCE
					.getPenetrateInfoNotNull(GlobalTokenPenetrate.ENTERPRISE_CODE_THREAD_LOCAL);


			// 转换为 Date 对象，时间为当天的 0点0分0秒
			Date startDataValue =
					DateUtils.getDateWithMidnight(startData);

			// 转换为 Date 对象，时间为当天的 23点59分59秒
			Date endDataValue =
					DateUtils.getDateWithEndOfDay(endData);


			CollaborateProduceRequest request = new CollaborateProduceRequest();
			request.setStartTime(startDataValue);
			request.setEndTime(endDataValue);

			DataSetQueryResultRpcDTO dataSetQueryResultRpcDTO = new DataSetQueryResultRpcDTO();

			//查询未处理(未分发),已分发数据量
			Map<String, BigDecimal> handleState = collaborateProduceHandleService.handleState(request);


			for (Map.Entry<String, BigDecimal> entry : handleState.entrySet()) {
				JSONObject jsonObj = new JSONObject();
				jsonObj.put("axis", entry.getKey());
				jsonObj.put("data", bigDecimalSerializer(entry.getValue(), true));
				dataSetQueryResultRpcDTO.addData(jsonObj);
			}


			return dataSetQueryResultRpcDTO;

		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("fail to execute").build());
		}
	}


}
