/*
 * Copyright (c) Zhongzao Software Co. LTD 2022-2062 All rights reserved
 */

package com.futurecraftsmen.pms.bff.configuration;

import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.aerie.forest.core.brick.exception.ExceptionGradeEnum;
import org.aerie.forest.core.brick.log.GlobalLogger;
import org.aerie.forest.core.brick.processor.datapenetrate.GlobalTokenPenetrate;
import org.aerie.forest.core.brick.processor.datapenetrate.InfoPenetrateProcessor;
import org.aerie.forest.core.brick.processor.encryption.AsymmetricEncrypt;
import org.aerie.forest.core.brick.processor.encryption.sm2.SM2HexProcessor;
import org.aerie.forest.core.frame.rebar._shelf.ForestInitShelf;
import org.aerie.forest.core.frame.rebar.entity.processer.exception.ExceptionProcessPack;
import org.aerie.forest.core.frame.rebar.factory.ForestFactory;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.futurecraftsmen.pms.api.domain.RemoteThreadLocal;
import com.futurecraftsmen.pms.api.domain.mv.GlobalLongMagicValue;
import com.futurecraftsmen.pms.api.domain.mv.GlobalStringMagicValue;
import com.futurecraftsmen.pms.api.dto.KeyPairRedisDTO;
import com.futurecraftsmen.pms.api.dto.authentication.OperationTokenUserInfoRedisDTO;
import com.futurecraftsmen.pms.api.dto.authentication.PmsTokenUserInfoRedisDTO;
import com.futurecraftsmen.pms.api.dto.authentication.TokenInfoPackRedisDTO;
import com.futurecraftsmen.pms.api.exception.AuthenticationException;
import com.futurecraftsmen.pms.api.service.SecretKeyService;
import com.futurecraftsmen.pms.bff.domain.AuthorizationDuplicateCheckProcessor;
import com.futurecraftsmen.pms.common.domain.attack.AttackLevelEnum;
import com.futurecraftsmen.pms.common.domain.exception.NetworkDelayDockException;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;

/**
 * @description token认证校验拦截器
 *
 * @author zhangqi
 * @organization futurecraftsmen
 * @date 2023/2/21 18:59
 * @version 3.0.1.300
 */
@Component
public class AuthenticationTokenFilter implements AuthorizationManager<HttpServletRequest>, GlobalLogger {

    /**
     * @description 认证信息前缀
     */
    private static final String AUTH_PREFIX = "Bearer ";

    /**
     * @description 时间戳和明文的连接符, 默认时间戳在前
     */
    private static final String CONCATENATE = "_";

    /**
     * @description
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * @description 测试环境
     */
    @Value("${text.context:true}")
    private boolean testContext;

    static {
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * @description 加密算法
     */
    private static final AsymmetricEncrypt ASYMMETRIC_ENCRYPT = SM2HexProcessor.INSTANCE;

    @Resource(name = "redisBase")
    private RedissonClient redisBase;

    @Autowired
    private SecretKeyService secretKeyService;

    @SneakyThrows
    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, HttpServletRequest httpServletRequest) {
        String authorizationWithPrefix =
            httpServletRequest.getHeader(GlobalStringMagicValue.AUTHORIZATION_FLAG.getValue());
        // 无认证信息直接拒绝
        if (StringUtils.isBlank(authorizationWithPrefix)) {
            return new AuthorizationDecision(false);
        }
        // 认证信息不合法
        if (!authorizationWithPrefix.startsWith(AUTH_PREFIX) || AUTH_PREFIX.equals(authorizationWithPrefix)) {
            LOGGER.error(GlobalStringMagicValue.SUSPECTED_ATTACK_LOG_ASSEMBLE.getValue(), AttackLevelEnum.LOW);
            return new AuthorizationDecision(false);
        }
        String authorization = authorizationWithPrefix.replace(AUTH_PREFIX, "");
        if (!testContext && AuthorizationDuplicateCheckProcessor.INSTANCE.authenticationExists(authorization)) {
            // 认证信息重复
            AuthorizationDuplicateCheckProcessor.INSTANCE.isRepetition.set(true);
            return new AuthorizationDecision(false);
        }
        AuthorizationDuplicateCheckProcessor.INSTANCE.isRepetition.remove();

        String keyCode =
            InfoPenetrateProcessor.INSTANCE.getPenetrateInfo(GlobalTokenPenetrate.ASYMMETRIC_KEY_CODE_THREAD_LOCAL);

        KeyPairRedisDTO keyPairRedisDTO = secretKeyService.getKeyPairNullable(keyCode);
        if (keyPairRedisDTO == null) {
            LOGGER.debug("Lost keyPair message, keyCode: {}", keyCode);
            return new AuthorizationDecision(false);
        }
        TokenInfoPackRedisDTO tokenInfoPackDTO = resolveTokenWithTimestamp(authorization, keyPairRedisDTO);
        if (tokenInfoPackDTO == null) {
            return new AuthorizationDecision(false);
        }
        String infoJson = tokenInfoPackDTO.getInfoJson();
        List<SimpleGrantedAuthority> simpleGrantedAuthorities = new ArrayList<>();
        switch (tokenInfoPackDTO.getTokenInfoEnum()) {
            case OPERATION -> {
                OperationTokenUserInfoRedisDTO operationTokenUserInfo =
                    OBJECT_MAPPER.readValue(infoJson, OperationTokenUserInfoRedisDTO.class);
                // 获得角色等级
                int roleLevel = operationTokenUserInfo.getRoleLevel();
                // 人员编号和角色 设置到ThreadLocal中
                InfoPenetrateProcessor.INSTANCE.setPenetrateInfo(GlobalTokenPenetrate.USER_CODE_THREAD_LOCAL,
                    tokenInfoPackDTO.getUserCode());
                InfoPenetrateProcessor.INSTANCE.setPenetrateInfo(GlobalTokenPenetrate.USER_ROLE_OPERATION_THREAD_LOCAL,
                    roleLevel);
                InfoPenetrateProcessor.INSTANCE.setPenetrateInfo(GlobalTokenPenetrate.ENTERPRISE_CODE_THREAD_LOCAL,
                    RemoteThreadLocal.OPERATION_ENTERPRISE_CODE);
                simpleGrantedAuthorities.add(new SimpleGrantedAuthority(
                    GlobalStringMagicValue.OPERATION_ROLE_PREFIX.getValue() + operationTokenUserInfo.getRoleLevel()));
            }
            case MAIN_SYSTEM -> {
                PmsTokenUserInfoRedisDTO pmsTokenUserInfoRedisDTO =
                    OBJECT_MAPPER.readValue(infoJson, PmsTokenUserInfoRedisDTO.class);
                InfoPenetrateProcessor.INSTANCE.setPenetrateInfo(GlobalTokenPenetrate.USER_CODE_THREAD_LOCAL,
                    tokenInfoPackDTO.getUserCode());
                InfoPenetrateProcessor.INSTANCE.setPenetrateInfo(GlobalTokenPenetrate.USER_NAME_THREAD_LOCAL,
                    pmsTokenUserInfoRedisDTO.getStaffName());
                InfoPenetrateProcessor.INSTANCE.setPenetrateInfo(GlobalTokenPenetrate.USER_PHONE_THREAD_LOCAL,
                    pmsTokenUserInfoRedisDTO.getPhoneNumber());
                InfoPenetrateProcessor.INSTANCE.setPenetrateInfo(GlobalTokenPenetrate.ENTERPRISE_CODE_THREAD_LOCAL,
                    pmsTokenUserInfoRedisDTO.getEnterpriseCode());
                InfoPenetrateProcessor.INSTANCE.setPenetrateInfo(GlobalTokenPenetrate.ENTERPRISE_NAME_THREAD_LOCAL,
                    pmsTokenUserInfoRedisDTO.getEnterpriseName());
                InfoPenetrateProcessor.INSTANCE.setPenetrateInfo(
                    GlobalTokenPenetrate.USER_ROLE_MAIN_SYSTEM_THREAD_LOCAL,
                    pmsTokenUserInfoRedisDTO.getRoleCodeList()); // todo 移除角色
                InfoPenetrateProcessor.INSTANCE.setPenetrateInfo(
                    GlobalTokenPenetrate.USER_POST_CODE_MAIN_SYSTEM_THREAD_LOCAL,
                    pmsTokenUserInfoRedisDTO.getPostCode());
                InfoPenetrateProcessor.INSTANCE.setPenetrateInfo(
                    GlobalTokenPenetrate.USER_POST_NAME_MAIN_SYSTEM_THREAD_LOCAL,
                    pmsTokenUserInfoRedisDTO.getPostName());
                InfoPenetrateProcessor.INSTANCE.setPenetrateInfo(
                    GlobalTokenPenetrate.USER_DEPARTMENT_CODE_MAIN_SYSTEM_THREAD_LOCAL,
                    pmsTokenUserInfoRedisDTO.getDepartmentCode());
                InfoPenetrateProcessor.INSTANCE.setPenetrateInfo(
                    GlobalTokenPenetrate.USER_DEPARTMENT_NAME_MAIN_SYSTEM_THREAD_LOCAL,
                    pmsTokenUserInfoRedisDTO.getDepartmentName());
                InfoPenetrateProcessor.INSTANCE.setPenetrateInfo(
                    GlobalTokenPenetrate.USER_ENTERPRISE_PERMISSION_THREAD_LOCAL,
                    pmsTokenUserInfoRedisDTO.getPermission());

                InfoPenetrateProcessor.INSTANCE.setPenetrateInfo(GlobalTokenPenetrate.ENTERPRISE_RIGHT_CHARACTERISTICS_THREAD_LOCAL,
                        pmsTokenUserInfoRedisDTO.getRightCharacteristics());

                simpleGrantedAuthorities.add(new SimpleGrantedAuthority(PmsSecurityFilterWrapper.PMS_ROLE));
            }
            default -> throw new RuntimeException("TOKEN ERROR -------------------------------");
        }
        UsernamePasswordAuthenticationToken authenticationToken =
            new UsernamePasswordAuthenticationToken(null, null, simpleGrantedAuthorities);
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        return new AuthorizationDecision(true);
    }

    /**
     * @description 解析出token
     * @param authorization 认证信息
     * @param keyPairRedisDTO 秘钥key
     * @return token
     *
     * @author pms
     * @date 2023/8/11 14:48
     * @initVersion 3.0.1.300
     */
    private TokenInfoPackRedisDTO resolveTokenWithTimestamp(String authorization, KeyPairRedisDTO keyPairRedisDTO) {
        if (StringUtils.isBlank(authorization)) {
            LOGGER.debug("No authentication information");
            return null;
        }
        try {
            PrivateKey privateKey = ASYMMETRIC_ENCRYPT.parsePrivateKey(keyPairRedisDTO.getPrivateKey());
            String plaintext = ASYMMETRIC_ENCRYPT.decoderByPrivateKey(privateKey, authorization);
            // 拆分authorization明文
            long timestampRequest = Long.parseLong(plaintext.substring(0, plaintext.indexOf(CONCATENATE)));
            String authToken = plaintext.substring(plaintext.indexOf(CONCATENATE) + 1);
            String[] split = authToken.split("_");
            RMapCache<String, TokenInfoPackRedisDTO> mapCache = redisBase.getMapCache("token-" + split[0]);
            TokenInfoPackRedisDTO tokenInfoPackDTO = mapCache.get(split[1]);
            if (tokenInfoPackDTO == null) {
                LOGGER.warn("token does not exist, token: {}", authToken);
                LOGGER.error(GlobalStringMagicValue.SUSPECTED_ATTACK_LOG_ASSEMBLE.getValue(), AttackLevelEnum.MEDIUM,
                    "The secret key could be cracked, or redis' token cache could be lost");
                return null;
            }
            long now = System.currentTimeMillis();
            long timeOffset = tokenInfoPackDTO.getTimeOffset();
            long networkDelay = timeOffset + now - timestampRequest;
            // 校验网络延迟
            if (!testContext && Math.abs(networkDelay) > GlobalLongMagicValue.ALLOW_NETWORK_DELAY.getValue()) {
                LOGGER.warn("The network delay is too long: {}", networkDelay);
                throw new NetworkDelayDockException("The network delay is too long: {}", networkDelay);
            }
            mapCache.put(split[1], tokenInfoPackDTO, GlobalLongMagicValue.TOKEN_VALID_TIME.getValue(),
                TimeUnit.SECONDS);
            // 设置token
            InfoPenetrateProcessor.INSTANCE.setPenetrateInfo(GlobalTokenPenetrate.TOKEN_THREAD_LOCAL, split[1]);
            return tokenInfoPackDTO;
        } catch (Exception e) {
            ForestFactory.INSTANCE.getForestRebarFactory(ForestInitShelf.getInstance().exceptionProcessor)
                .executeProcess(new ExceptionProcessPack("Authentication information failed to be decrypted", e,
                    ExceptionGradeEnum.WARN));
            LOGGER.error(GlobalStringMagicValue.SUSPECTED_ATTACK_LOG_ASSEMBLE.getValue(), AttackLevelEnum.LOW);
            throw new AuthenticationException(e, "Authentication information failed to be decrypted");
        }
    }
}