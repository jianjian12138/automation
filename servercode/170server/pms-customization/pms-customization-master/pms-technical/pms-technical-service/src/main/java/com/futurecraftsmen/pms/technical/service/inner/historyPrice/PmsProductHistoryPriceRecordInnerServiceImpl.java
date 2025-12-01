/*
 * Copyright (c) Zhongzao Software Co. LTD 2022-2062 All rights reserved
 */

package com.futurecraftsmen.pms.technical.service.inner.historyPrice;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.futurecraftsmen.pms.service.configuration.RequestTableHelper;
import com.futurecraftsmen.pms.technical.service.core.dataquery.PasDataQueryService;
import com.futurecraftsmen.pms.technical.service.core.dataquery.SimpleSellContractModel;
import com.futurecraftsmen.pms.technical.service.dao.HistoryPriceMapper;
import com.futurecraftsmen.pms.technical.service.domain.baseModule.HistoryPriceModel;
import com.futurecraftsmen.pms.technical.service.domain.sellorder.SellOrderModel;
import com.futurecraftsmen.pms.technical.service.domain.sellorder.SellOrderProductModel;
import com.futurecraftsmen.pms.technical.service.impl.inner.TableNameFactory;
import com.futurecraftsmen.pms.technical.service.inner.PmsProductHistoryPriceRecordInnerService;
import org.aerie.forest.core.brick.exception.ExceptionMsg;
import org.aerie.forest.core.brick.exception.ExceptionPack;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PmsProductHistoryPriceRecordInnerServiceImpl implements PmsProductHistoryPriceRecordInnerService {

	@Autowired
	private PasDataQueryService pasDataQueryService;

	@Autowired
	private HistoryPriceMapper historyPriceMapper;

	@Autowired
	private TableNameFactory tableFactory;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateHistoryTransactionPriceRecordsAfterSchedule(SellOrderModel sellOrderModel, SellOrderProductModel sellOrderProductModel) throws ExceptionPack {
		try {

			if (sellOrderModel == null || sellOrderModel.getSellContract() == null) {
				return;
			}

			if (sellOrderProductModel == null || sellOrderProductModel.getSellContractProduct() == null) {
				return;
			}
			//根据销售合同编号,找到 signedContractCode

			SimpleSellContractModel sellContractModel = pasDataQueryService.queryBuyerNameInSellContract(sellOrderModel.getSellContract(), sellOrderModel.getEnterpriseCode());

			if (sellContractModel == null || sellContractModel.getSignedContract() == null) {
				return;
			}

			//根据 signedContract,sellContractProduct,enterpriseCode 更新历史价格数据的  materialCode 为  productCode


			String historyPriceTableName = tableFactory.getTableName(tableFactory.module.getBaseModule(),
					tableFactory.table.getHistoryPrice());

			LambdaUpdateWrapper<HistoryPriceModel> uw = Wrappers.lambdaUpdate();
			uw.eq(HistoryPriceModel::getSignedContract, sellContractModel.getSignedContract());
			uw.eq(HistoryPriceModel::getContractMaterial, sellOrderProductModel.getSellContractProduct());
			uw.eq(HistoryPriceModel::getEnterpriseCode, sellOrderModel.getEnterpriseCode());
			uw.eq(HistoryPriceModel::getDeleteFlag, Boolean.FALSE);

			uw.set(HistoryPriceModel::getMaterialCode, sellOrderProductModel.getProductCode());
			RequestTableHelper.setTableName(historyPriceTableName);

			historyPriceMapper.update(uw);


		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("fail to updateHistoryTransactionPriceRecordsAfterSchedule").build());
		}
	}
}
