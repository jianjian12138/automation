/*
 * Copyright (c) Zhongzao Software Co. LTD 2022-2062 All rights reserved
 */

package com.futurecraftsmen.pms.technical.service.impl.technical.relationship;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.futurecraftsmen.pms.service.configuration.RequestTableHelper;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.relationship.ProductPartRouteRelationshipDTO;
import com.futurecraftsmen.pms.technical.api.service.technical.IProductPartRouteRelationshipService;
import com.futurecraftsmen.pms.technical.service.dao.technical.IProductPartRouteRelationshipMapper;
import com.futurecraftsmen.pms.technical.service.domain.technical.productpart.ProductPartRouteRelationshipModel;
import com.futurecraftsmen.pms.technical.service.impl.inner.TableNameFactory;

import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

import cn.hutool.core.convert.Convert;
import jakarta.annotation.Resource;

import static com.futurecraftsmen.pms.technical.service.util.CommonUtil.getEnterpriseCode;

@DubboService(group = "pms")
public class ProductPartRouteRelationshipServiceImpl extends ServiceImpl<IProductPartRouteRelationshipMapper, ProductPartRouteRelationshipModel> implements IProductPartRouteRelationshipService {

	@Resource
	private TableNameFactory tableFactory;

	@Override
	public List<ProductPartRouteRelationshipDTO> queryByproductPart(Long productPartCode) {
		// 根据产品零件编号查询绑定信息
		String tableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProductPartRouteRelationship());
		LambdaQueryWrapper<ProductPartRouteRelationshipModel> qw = Wrappers.lambdaQuery();
		qw.eq(ProductPartRouteRelationshipModel::getProductPartCode, productPartCode);
		qw.eq(ProductPartRouteRelationshipModel::getEnterpriseCode, getEnterpriseCode());
		qw.eq(ProductPartRouteRelationshipModel::getDeleteFlag, Boolean.FALSE);
		RequestTableHelper.setTableName(tableName);
		return Convert.toList(ProductPartRouteRelationshipDTO.class, super.list(qw));
	}
}
