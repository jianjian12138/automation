/*
 * Copyright (c) Zhongzao Software Co. LTD 2022-2062 All rights reserved
 */

package com.futurecraftsmen.pms.technical.service.impl.technical.relationship;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.futurecraftsmen.pms.service.configuration.RequestTableHelper;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.relationship.ProcedurePartRelationshipDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.relationship.RelationshipRpcRequest;
import com.futurecraftsmen.pms.technical.api.service.technical.IProcedurePartRelationshipService;
import com.futurecraftsmen.pms.technical.service.dao.technical.IProcedurePartRelationshipMapper;
import com.futurecraftsmen.pms.technical.service.domain.technical.procedure.ProcedurePartRelationshipModel;
import com.futurecraftsmen.pms.technical.service.impl.inner.TableNameFactory;

import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

import cn.hutool.core.convert.Convert;
import jakarta.annotation.Resource;

import static com.futurecraftsmen.pms.technical.service.util.CommonUtil.getEnterpriseCode;

@DubboService(group = "pms")
public class ProcedurePartRelationshipServiceImpl extends ServiceImpl<IProcedurePartRelationshipMapper, ProcedurePartRelationshipModel> implements IProcedurePartRelationshipService {

	@Resource
	private TableNameFactory tableFactory;
	@Resource
	private IProcedurePartRelationshipMapper procedurePartRelationshipMapper;

	@Override
	public void deleteRelationship(RelationshipRpcRequest requestData) {
		if (requestData.getUniqueId() == null) {
			return;
		}
		// 工序表与工艺路线关系表
		String procedureRouteRelationshipTableName = tableFactory.getTableName(tableFactory.module.getTechnology(),
				tableFactory.table.getProcedurePartRelationship());
		LambdaQueryWrapper<ProcedurePartRelationshipModel> prQw = Wrappers.lambdaQuery();
		prQw.eq(ProcedurePartRelationshipModel::getUniqueId, requestData.getUniqueId());
		prQw.eq(ProcedurePartRelationshipModel::getEnterpriseCode, getEnterpriseCode());
		RequestTableHelper.setTableName(procedureRouteRelationshipTableName);
		procedurePartRelationshipMapper.delete(prQw);
	}

	@Override
	public List<ProcedurePartRelationshipDTO> queryByproductPart(Long productPartCode) {
		// 根据产品零件编号查询绑定信息
		String tableName =
				tableFactory.getTableName(tableFactory.module.getTechnology(), tableFactory.table.getProcedurePartRelationship());
		LambdaQueryWrapper<ProcedurePartRelationshipModel> qw = Wrappers.lambdaQuery();
		qw.eq(ProcedurePartRelationshipModel::getProductPartCode, productPartCode);
		qw.eq(ProcedurePartRelationshipModel::getEnterpriseCode, getEnterpriseCode());
		qw.eq(ProcedurePartRelationshipModel::getDeleteFlag, Boolean.FALSE);
		RequestTableHelper.setTableName(tableName);
		return Convert.toList(ProcedurePartRelationshipDTO.class, super.list(qw));
	}
	
}
