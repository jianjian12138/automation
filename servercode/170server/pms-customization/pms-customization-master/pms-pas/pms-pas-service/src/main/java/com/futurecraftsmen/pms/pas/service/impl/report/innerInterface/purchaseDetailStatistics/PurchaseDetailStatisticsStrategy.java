package com.futurecraftsmen.pms.pas.service.impl.report.innerInterface.purchaseDetailStatistics;


import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.futurecraftsmen.pms.common.utils.DateUtils;
import com.futurecraftsmen.pms.dm.api.service.dsql.DmShare;
import com.futurecraftsmen.pms.dm.api.service.report.dto.DataSetQueryResultRpcDTO;
import com.futurecraftsmen.pms.dm.api.service.report.dto.ReportItemInfoRpcDTO;
import com.futurecraftsmen.pms.pas.api.rpc.purchasedetail.PurchaseDetailStatisticsQueryConditionRpcDTO;
import com.futurecraftsmen.pms.pas.service.dao.PurchaseVenderStatisticsDao;
import com.futurecraftsmen.pms.pas.service.impl.report.innerInterface.InnerInterfaceStrategy;
import org.aerie.forest.core.brick.exception.ExceptionMsg;
import org.aerie.forest.core.brick.exception.ExceptionPack;
import org.aerie.forest.core.brick.processor.datapenetrate.GlobalTokenPenetrate;
import org.aerie.forest.core.brick.processor.datapenetrate.InfoPenetrateProcessor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author chengxinyu
 * @description 采购总额数据集
 * @organization futurecraftsmen
 * @date 2025-05-19 09:43
 */
@Service
public class PurchaseDetailStatisticsStrategy implements InnerInterfaceStrategy {


	@Autowired
	private PurchaseVenderStatisticsDao purchaseVenderStatisticsDao;

	/**
	 * @description 公共模块产品历史价格记录表
	 */
	@Value("${dynamic.module.publicData.table.historyPrice.tableCode}")
	private Long publicDataHistoryPriceTableCode;


	/**
	 * @description 公共模块，已签章合同表
	 */
	@Value("${dynamic.module.publicData.table.signedContract.tableCode}")
	private Long signedContractTableCode;

	/**
	 * @description 公共模块，合同款项记录编号
	 */
	@Value("${dynamic.module.publicData.table.contractMoneyRecord.tableCode}")
	private Long contractMoneyRecordTableCode;


	/**
	 * @description 动态分片
	 */
	@DubboReference
	private DmShare dmShare;


	@Override
	public String innerInterfaceIdentity() {
		return PURCHASE_DETAIL_STATS_TICS;
	}

	/**
	 * 查询指定日期范围的数据
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
//				throw new AssertException(ExceptionMsg.builder("page is required").msgView("分页信息缺失").build());
			}


			//参数:指定开始日期
			String startData = startDate(dataSetParams);

			//参数:指定结束日期
			String endData = endDate(dataSetParams);


			//企业编号
			Long enterpriseCode = InfoPenetrateProcessor.INSTANCE
					.getPenetrateInfoNotNull(GlobalTokenPenetrate.ENTERPRISE_CODE_THREAD_LOCAL);


			// 表头信息
			DataSetQueryResultRpcDTO dataSetQueryResultRpcDTO = new DataSetQueryResultRpcDTO();

			List<ReportItemInfoRpcDTO> tableTitles = new ArrayList<>();
			tableTitles.add(new ReportItemInfoRpcDTO("venderName", "供应商"));
			tableTitles.add(new ReportItemInfoRpcDTO("purchaseAmount", "采购总额(万元)"));
			tableTitles.add(new ReportItemInfoRpcDTO("paymentAmount", "已付金额(万元)"));
			tableTitles.add(new ReportItemInfoRpcDTO("received", "签收量"));
			dataSetQueryResultRpcDTO.setTableTitles(tableTitles);


			PurchaseDetailStatisticsQueryConditionRpcDTO purchaseDetailStatisticsQueryConditionRpcDTO = new PurchaseDetailStatisticsQueryConditionRpcDTO();


			// 转换为 Date 对象，时间为当天的 0点0分0秒
			Date recordTimePreDate =
					DateUtils.getDateWithMidnight(startData);

			// 转换为 Date 对象，时间为当天的 23点59分59秒
			Date recordTimePostDate =
					DateUtils.getDateWithEndOfDay(endData);


			purchaseDetailStatisticsQueryConditionRpcDTO.setRecordTimePreDate(recordTimePreDate);
			purchaseDetailStatisticsQueryConditionRpcDTO.setRecordTimePostDate(recordTimePostDate);

			String historyPriceTableName = dmShare.getShardingTableName(enterpriseCode, publicDataHistoryPriceTableCode);

			String signedContractTableName = dmShare.getShardingTableName(enterpriseCode, signedContractTableCode);


			String contractMoneyRecordTableName = dmShare.getShardingTableName(enterpriseCode, contractMoneyRecordTableCode);


			//查询供应商采购总额
			List<VenderPurchaseDetail> purchaseDetailInfoModels = purchaseVenderStatisticsDao.venderPurchaseDetailPaging(page, recordTimePreDate, recordTimePostDate,
					enterpriseCode, historyPriceTableName, signedContractTableName, contractMoneyRecordTableName);

			dataSetQueryResultRpcDTO.setTotal(page.getTotal());


			for (VenderPurchaseDetail venderPurchaseDetail : purchaseDetailInfoModels) {
				JSONObject data = new JSONObject();
				data.put("venderName", venderPurchaseDetail.getVender());
				data.put("purchaseAmount", formatMoneyValue(venderPurchaseDetail.getTotal(), true));
				data.put("paymentAmount", formatMoneyValue(venderPurchaseDetail.getPaymentTotal(), true));
				data.put("received", bigDecimalSerializer(venderPurchaseDetail.getReceived(), true));
				dataSetQueryResultRpcDTO.addData(data);
			}





			//指标信息: 无


			return dataSetQueryResultRpcDTO;

		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("fail to execute").build());
		}
	}


}
