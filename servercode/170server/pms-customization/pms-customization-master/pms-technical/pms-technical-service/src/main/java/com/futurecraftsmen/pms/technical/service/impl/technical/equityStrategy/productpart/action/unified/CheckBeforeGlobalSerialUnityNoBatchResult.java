package com.futurecraftsmen.pms.technical.service.impl.technical.equityStrategy.productpart.action.unified;


import com.futurecraftsmen.pms.technical.api.domain.technical.type.ProductPartTreeNodeRpcDTO;
import lombok.Data;

import java.io.Serializable;
import java.util.*;

@Data
public class CheckBeforeGlobalSerialUnityNoBatchResult implements Serializable {

	//所有需要处理的分类
	private Set<Long> allConcernedTypeCodes;


	private ProductPartTreeNodeRpcDTO productPartTypeTree;

	// 子级到父级的映射关系
	private Map<Long, Long> childToParentMap = new HashMap<>();

	//最终使用同一规则的分类：
	// key:使用的 queryKey -> value:相关产品分类
	// 有规则的产品分类
	// 没有规则的产品分类:
	//    -)公共上级一样且有规则 或公共上级是顶级
	//    -)公共上级一样但没有规则，继续向上查询
	private Map<Long, Set<Long>> sameQueryKey2ProductPartTypeCodes = new HashMap<>();


	//分类->使用的规则：
	// key:产品分类 value: 使用的 queryKey
	// 有规则的产品分类
	// 没有规则的产品分类:
	//    -)公共上级一样且有规则 或公共上级是顶级
	//    -)公共上级一样但没有规则，继续向上查询
	private Map<Long, Long> productPartTypeCode2QueryKey = new HashMap<>();


	private Set<Long> handledTypeCodes = new HashSet<>();


	public CheckBeforeGlobalSerialUnityNoBatchResult(Set<Long> allConcernedTypeCodes, ProductPartTreeNodeRpcDTO productPartTypeTree) {
		this.allConcernedTypeCodes = allConcernedTypeCodes;
		this.productPartTypeTree = productPartTypeTree;

		if (productPartTypeTree == null) {
			return;
		}
		// 创建子级到父级的映射关系
		Map<Long, Long> childToParentMap = new HashMap<>();
		buildChildToParentMap(productPartTypeTree, childToParentMap);
		this.childToParentMap = childToParentMap;
	}

	public void recordQueryKeyAndTypeCode(Long queryKey, Long productPartTypeCode) {
		if (allConcernedTypeCodes.contains(productPartTypeCode) && !handledTypeCodes.contains(productPartTypeCode)) {
			// 将相同查询键与产品分类建立映射关系
			sameQueryKey2ProductPartTypeCodes.computeIfAbsent(queryKey, k -> new HashSet<>()).add(productPartTypeCode);

			productPartTypeCode2QueryKey.put(productPartTypeCode, queryKey);

			handledTypeCodes.add(productPartTypeCode);
		}
	}

	public List<Long> relatedTypeCodes() {
		// 结果集合，使用Set去重
		Set<Long> result = new HashSet<>(allConcernedTypeCodes);

		// 为每个关注的分类查找所有上级
		for (Long typeCode : allConcernedTypeCodes) {
			findAllParents(typeCode, childToParentMap, result);
		}

		return new ArrayList<>(result);
	}
	
	/**
	 * 构建子级到父级的映射关系
	 * @param node 当前节点
	 * @param childToParentMap 映射关系存储
	 */
	private void buildChildToParentMap(ProductPartTreeNodeRpcDTO node, Map<Long, Long> childToParentMap) {
		if (node == null) {
			return;
		}
		
		Long parentCode = node.getProductPartTypeCode();
		List<ProductPartTreeNodeRpcDTO> children = node.getChildren();
		
		if (children != null && !children.isEmpty()) {
			for (ProductPartTreeNodeRpcDTO child : children) {
				childToParentMap.put(child.getProductPartTypeCode(), parentCode);
				buildChildToParentMap(child, childToParentMap);
			}
		}
	}
	
	/**
	 * 查找指定分类的所有上级分类
	 * @param typeCode 分类编号
	 * @param childToParentMap 子级到父级的映射关系
	 * @param result 结果集合
	 */
	private void findAllParents(Long typeCode, Map<Long, Long> childToParentMap, Set<Long> result) {
		Long parentCode = childToParentMap.get(typeCode);
		if (parentCode != null) {
			result.add(parentCode);
			findAllParents(parentCode, childToParentMap, result);
		}
	}

	public Long findQueryKey(Long typeCode) {
		return productPartTypeCode2QueryKey.get(typeCode);
	}
}