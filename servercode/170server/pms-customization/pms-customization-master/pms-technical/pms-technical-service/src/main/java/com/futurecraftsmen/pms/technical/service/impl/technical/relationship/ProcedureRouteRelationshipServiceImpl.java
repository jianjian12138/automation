/*
 * Copyright (c) Zhongzao Software Co. LTD 2022-2062 All rights reserved
 */

package com.futurecraftsmen.pms.technical.service.impl.technical.relationship;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.futurecraftsmen.pms.service.configuration.RequestTableHelper;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.relationship.RelationshipRpcRequest;
import com.futurecraftsmen.pms.technical.api.service.technical.IProcedureRouteRelationshipService;
import com.futurecraftsmen.pms.technical.service.dao.technical.IProcedureRouteRelationshipMapper;
import com.futurecraftsmen.pms.technical.service.domain.technical.procedure.ProcedureRouteRelationshipModel;
import com.futurecraftsmen.pms.technical.service.impl.inner.TableNameFactory;

import org.apache.dubbo.config.annotation.DubboService;

import jakarta.annotation.Resource;

import static com.futurecraftsmen.pms.technical.service.util.CommonUtil.getEnterpriseCode;

@DubboService(group = "pms")
public class ProcedureRouteRelationshipServiceImpl extends ServiceImpl<IProcedureRouteRelationshipMapper, ProcedureRouteRelationshipModel> implements IProcedureRouteRelationshipService {

	@Resource
	private TableNameFactory tableFactory;
	@Resource
	private IProcedureRouteRelationshipMapper procedureRouteRelationshipMapper;

	@Override
	public void deleteRelationship(RelationshipRpcRequest requestData) {
		if (requestData.getUniqueId() == null) {
			return;
		}
		// 工序表与工艺路线关系表
		String procedureRouteRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProcedureRouteRelationship());
		LambdaQueryWrapper<ProcedureRouteRelationshipModel> prQw = Wrappers.lambdaQuery();
		prQw.eq(ProcedureRouteRelationshipModel::getUniqueId, requestData.getUniqueId());
		prQw.eq(ProcedureRouteRelationshipModel::getEnterpriseCode, getEnterpriseCode());
		RequestTableHelper.setTableName(procedureRouteRelationshipTableName);
		procedureRouteRelationshipMapper.delete(prQw);
	}

}
