/*
 * Copyright (c) Zhongzao Software Co. LTD 2022-2062 All rights reserved
 */

package com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.futurecraftsmen.pms.service.configuration.RequestTableHelper;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.xihang.comp.ProductPartCompRequest;
import com.futurecraftsmen.pms.technical.api.service.technical.IProductPartCompService;
import com.futurecraftsmen.pms.technical.service.dao.technical.IProductPartCompMapper;
import com.futurecraftsmen.pms.technical.service.domain.technical.productpart.comp.ProductPartCompModel;
import com.futurecraftsmen.pms.technical.service.impl.inner.TableNameFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import static com.futurecraftsmen.pms.technical.service.util.CommonUtil.getEnterpriseCode;

@Slf4j
@Service
public class IProductPartCompServiceImpl extends ServiceImpl<IProductPartCompMapper, ProductPartCompModel> implements IProductPartCompService {

	@Resource
	private TableNameFactory tableFactory;
	@Resource
	private IProductPartCompMapper productPartCompMapper;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateCompExtra(ProductPartCompRequest compRequest) {
		// 产品零件-绑定组成表
		String productPartCompTableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPartComp());
		LambdaQueryWrapper<ProductPartCompModel> qw = Wrappers.lambdaQuery();
		if (compRequest.getParentCode() != null) {
			qw.eq(ProductPartCompModel::getParentCode, compRequest.getParentCode());
		}
		if (compRequest.getChildCode() != null) {
			qw.eq(ProductPartCompModel::getChildCode, compRequest.getChildCode());
		}
	qw.eq(ProductPartCompModel::getEnterpriseCode, getEnterpriseCode());
	RequestTableHelper.setTableName(productPartCompTableName);
	for (ProductPartCompModel productPartCompModel : productPartCompMapper.selectList(qw)) {
		if (compRequest.getParentNumber() != null) {
			productPartCompModel.setParentNumber(compRequest.getParentNumber());
		}
		if (compRequest.getChildNumber() != null) {
			productPartCompModel.setChildNumber(compRequest.getChildNumber());
		}
		RequestTableHelper.setTableName(productPartCompTableName);
		productPartCompMapper.updateById(productPartCompModel);
	}
	}
}
