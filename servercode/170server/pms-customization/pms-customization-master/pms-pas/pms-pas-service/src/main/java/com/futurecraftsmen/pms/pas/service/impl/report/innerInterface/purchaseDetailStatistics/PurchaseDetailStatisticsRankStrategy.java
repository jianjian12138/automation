package com.futurecraftsmen.pms.pas.service.impl.report.innerInterface.purchaseDetailStatistics;

import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.futurecraftsmen.pms.common.utils.DateUtils;
import com.futurecraftsmen.pms.dm.api.service.dsql.DmShare;
import com.futurecraftsmen.pms.dm.api.service.report.dto.DataSetQueryResultRpcDTO;
import com.futurecraftsmen.pms.pas.service.dao.HistoryPriceDao;
import com.futurecraftsmen.pms.pas.service.impl.inner.ContractMoneyRecordInnerService;
import com.futurecraftsmen.pms.pas.service.impl.report.innerInterface.InnerInterfaceStrategy;
import org.aerie.forest.core.brick.exception.ExceptionMsg;
import org.aerie.forest.core.brick.exception.ExceptionPack;
import org.aerie.forest.core.brick.processor.datapenetrate.GlobalTokenPenetrate;
import org.aerie.forest.core.brick.processor.datapenetrate.InfoPenetrateProcessor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author chengxinyu
 * @description 采购总额排行榜数据集查询
 * @organization futurecraftsmen
 * @date 2025-05-16 14:43
 */
@Service
public class PurchaseDetailStatisticsRankStrategy implements InnerInterfaceStrategy {

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

	@Autowired
	private ContractMoneyRecordInnerService contractMoneyRecordInnerService;

	@Override
	public String innerInterfaceIdentity() {
		return PURCHASE_DETAIL_STATS_TICS_RANK;
	}

	/**
	 * 查询 指定日期范围，采购 TOP10 的供应商姓名 ，采购总金额
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


			//参数:指定开始日期
			String startData = startDate(dataSetParams);

			//参数:指定结束日期
			String endData = endDate(dataSetParams);

			// 转换为 Date 对象，时间为当天的 0点0分0秒
			Date recordTimePreDate =
					DateUtils.getDateWithMidnight(startData);

			// 转换为 Date 对象，时间为当天的 23点59分59秒
			Date recordTimePostDate =
					DateUtils.getDateWithEndOfDay(endData);


			//查询 TOP10 采购总额的 供应商
			String historyPriceTableName = dmShare.getShardingTableName(enterpriseCode, publicDataHistoryPriceTableCode);

			List<VenderPurchaseDetail> venderPurchaseDetailTop10 = historyPriceDao.venderPurchaseTotalTop10(recordTimePreDate, recordTimePostDate, enterpriseCode, historyPriceTableName);


			DataSetQueryResultRpcDTO dataSetQueryResultRpcDTO = new DataSetQueryResultRpcDTO();


			for (VenderPurchaseDetail venderPurchaseDetail : venderPurchaseDetailTop10) {
				JSONObject data = new JSONObject();
				data.put("name", venderPurchaseDetail.getVender());
				data.put("data", formatMoneyValue(venderPurchaseDetail.getTotal(), true));

				dataSetQueryResultRpcDTO.addData(data);
			}


			return dataSetQueryResultRpcDTO;

		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("fail to execute").build());
		}
	}


}
