package com.futurecraftsmen.pms.pas.service.impl.report.innerInterface.sellDetailStatistics;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.futurecraftsmen.pms.common.utils.DateUtils;
import com.futurecraftsmen.pms.dm.api.service.dsql.DmShare;
import com.futurecraftsmen.pms.dm.api.service.report.dto.DataSetQueryResultRpcDTO;
import com.futurecraftsmen.pms.dm.api.service.report.dto.ReportItemInfoRpcDTO;
import com.futurecraftsmen.pms.pas.service.dao.HistoryPriceDao;
import com.futurecraftsmen.pms.pas.service.impl.report.innerInterface.InnerInterfaceStrategy;
import com.futurecraftsmen.pms.service.domain.dataScope.sql.DataScopeParams;
import org.aerie.forest.core.brick.exception.ExceptionMsg;
import org.aerie.forest.core.brick.exception.ExceptionPack;
import org.aerie.forest.core.brick.processor.datapenetrate.GlobalTokenPenetrate;
import org.aerie.forest.core.brick.processor.datapenetrate.InfoPenetrateProcessor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author chengxinyu
 * @description 销售总额概览数据集查询
 * @organization futurecraftsmen
 * @date 2025-05-16 14:43
 */
@Service
public class SellDetailStatisticsOverviewStrategy implements InnerInterfaceStrategy {

	/**
	 * @description 历史价格 dao
	 */
	@Autowired
	private HistoryPriceDao historyPriceDao;

	/**
	 * @description 公共模块产品历史价格记录表
	 */
	@Value("${dynamic.module.publicData.table.historyPrice.tableCode}")
	private Long publicDataHistoryPriceTableCode;

	/**
	 * @description 动态分片
	 */
	@DubboReference
	private DmShare dmShare;



	@Override
	public String innerInterfaceIdentity() {
		return SELL_DETAIL_STATS_TICS_OVER_VIEW;
	}

	/**
	 * 查询当前人员可见的销售数据的历史总额，今日销售总额
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


			//企业编号
			Long enterpriseCode = InfoPenetrateProcessor.INSTANCE
					.getPenetrateInfoNotNull(GlobalTokenPenetrate.ENTERPRISE_CODE_THREAD_LOCAL);


			String historyPriceTableName = dmShare.getShardingTableName(enterpriseCode, publicDataHistoryPriceTableCode);

			//历史销售总额
			BigDecimal saleTotal = historyPriceDao.saleTotal(null, null, enterpriseCode, historyPriceTableName, new DataScopeParams());

			//今日销售总额
			//当天的 0点0分0秒
			Date startDate =
					DateUtils.getCurrentDateWithMidnight();

			// 当天的 23点59分59秒
			Date endDate =
					DateUtils.getCurrentDateWithEndOfDay();
			BigDecimal todaySaleTotal = historyPriceDao.saleTotal(startDate, endDate, enterpriseCode, historyPriceTableName, new DataScopeParams());


			DataSetQueryResultRpcDTO dataSetQueryResultRpcDTO = new DataSetQueryResultRpcDTO();
			dataSetQueryResultRpcDTO.setData(null);

			//主要指标： 历史销售总额
			ReportItemInfoRpcDTO mainQuota = new ReportItemInfoRpcDTO();
			mainQuota.setKey("销售总额(万元)");
			mainQuota.setName(formatMoneyValue(saleTotal, true));
			dataSetQueryResultRpcDTO.setMainQuota(mainQuota);

			//其余指标:今日销售总额
			List<ReportItemInfoRpcDTO> quotas = new ArrayList<>();

			ReportItemInfoRpcDTO todaySaleTotalQuota = new ReportItemInfoRpcDTO();
			todaySaleTotalQuota.setKey("今日销售额");
			todaySaleTotalQuota.setName(formatMoneyValue(todaySaleTotal, true));

			quotas.add(todaySaleTotalQuota);


			dataSetQueryResultRpcDTO.setQuotas(quotas);

			return dataSetQueryResultRpcDTO;

		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("fail to execute").build());
		}
	}


}
