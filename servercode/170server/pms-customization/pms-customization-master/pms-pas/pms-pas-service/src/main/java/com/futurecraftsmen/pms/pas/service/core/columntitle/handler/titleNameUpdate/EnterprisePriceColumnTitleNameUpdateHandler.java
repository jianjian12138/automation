/*
 * Copyright (c) Zhongzao Software Co. LTD 2022-2062 All rights reserved
 */

package com.futurecraftsmen.pms.pas.service.core.columntitle.handler.titleNameUpdate;

import com.futurecraftsmen.pms.pas.service.core.columntitle.AbstractProductColumnTitle;
import com.futurecraftsmen.pms.pas.service.core.columntitle.DefaultProductColumnTitle;
import com.futurecraftsmen.pms.pas.service.core.paymentStrategy.PaymentStrategy;

import org.aerie.forest.core.brick.assertprocess.AssertInjecter;
import org.aerie.forest.core.brick.exception.ExceptionPack;
import org.aerie.forest.core.brick.exception.universal.UnsupportedException;
import org.aerie.forest.core.brick.function.generatethrowable.GenerateException;
import org.aerie.forest.core.brick.handler.AbstractForestHandler;

import cn.hutool.core.util.StrUtil;

/**
 * @description enterprisePrice 根据 PaymentStrategy 处理器
 *
 * @author zhangqi
 * @organization futurecraftsmen
 * @date 2023/12/26 11:55
 */
public final class EnterprisePriceColumnTitleNameUpdateHandler
    extends AbstractColumnTitleNameUpdateHandler<PaymentStrategy> {

    public EnterprisePriceColumnTitleNameUpdateHandler(
        AbstractForestHandler<?, AbstractProductColumnTitle, PaymentStrategy> next) {
        super(next);
    }

    @Override
    protected AbstractProductColumnTitle doHandleDetails(
        ProductColumnTitleHandlerParameter productColumnTitleHandlerParameter, PaymentStrategy paymentStrategy)
        throws ExceptionPack {
        DefaultProductColumnTitle enterprisePriceColumnTitle =
            (DefaultProductColumnTitle)productColumnTitleHandlerParameter.getColumnTitle();

        if (StrUtil.isNotBlank(paymentStrategy.getAdditionChs())) {
            enterprisePriceColumnTitle
                .setTitleNameChs(enterprisePriceColumnTitle.getPaymentStrategyTitleNameTemplateChs()
                    + StrUtil.format("({})", paymentStrategy.getAdditionChs()));
        } else {
            enterprisePriceColumnTitle
                .setTitleNameChs(enterprisePriceColumnTitle.getPaymentStrategyTitleNameTemplateChs());
        }

        if (StrUtil.isNotBlank(paymentStrategy.getAdditionEn())) {
            enterprisePriceColumnTitle.setTitleNameEn(enterprisePriceColumnTitle.getPaymentStrategyTitleNameTemplateEn()
                + StrUtil.format("({})", paymentStrategy.getAdditionEn()));
        } else {
            enterprisePriceColumnTitle
                .setTitleNameEn(enterprisePriceColumnTitle.getPaymentStrategyTitleNameTemplateEn());
        }

        return enterprisePriceColumnTitle;
    }

    @Override
    protected ProductColumnTitleHandlerParameter supportConversion(Object o, PaymentStrategy paymentStrategy)
        throws UnsupportedException {
        ProductColumnTitleHandlerParameter productColumnTitleHandlerParameter = (ProductColumnTitleHandlerParameter)o;

        // 动态列字段标题需要是 系统列,且是enterprisePrice列
        AssertInjecter.inject(productColumnTitleHandlerParameter.getColumnTitle(), "productColumnTitle")
            .and(p -> p instanceof DefaultProductColumnTitle).and(p -> p.getKey().equals("enterprisePrice"))
            .and(p -> ((DefaultProductColumnTitle)p).isPaymentStrategyTitleNameTemplate())
            .judge((GenerateException<UnsupportedException>)UnsupportedException::new, "not enterprisePrice column");

        return productColumnTitleHandlerParameter;

    }
}
