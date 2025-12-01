package com.futurecraftsmen.pms.technical.api.service.temp.feedback;


import org.aerie.forest.core.brick.exception.ExceptionPack;

@Deprecated
public interface TempService {


	/**
	 * @param feedBackForReceiving
	 * @throws ExceptionPack
	 * @description 根据被动收货入厂检生成质量反馈
	 * @author chengxinyu
	 * @date 2025-03-26 18:35
	 */
	void addFeedBackForReceiving(FeedBackForReceiving feedBackForReceiving) throws ExceptionPack;
}
