package com.mz.jarboot.core.basic;

import com.mz.jarboot.api.cmd.annotation.Name;
import com.mz.jarboot.api.cmd.spi.CommandProcessor;
import com.mz.jarboot.common.CommandConst;
import com.mz.jarboot.common.CommandResponse;
import com.mz.jarboot.common.JsonUtils;
import com.mz.jarboot.common.ResponseType;
import com.mz.jarboot.core.cmd.CommandBuilder;
import com.mz.jarboot.core.constant.CoreConstant;
import com.mz.jarboot.core.stream.ResultStreamDistributor;
import com.mz.jarboot.core.utils.HttpUtils;
import com.mz.jarboot.core.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * @author jianzhengma
 */
public class AgentServiceOperator {
    private static final Logger logger = LoggerFactory.getLogger(CoreConstant.LOG_NAME);
    private static final String SET_STARTED_API = "/api/public/agent/setStarted?server=";
    private static volatile boolean started = false;

    public static void setStarted() {
        if (started) {
            return;
        }
        HttpUtils.getSimple(SET_STARTED_API + EnvironmentContext.getServer());
        started = true;
    }

    public static String getServer() {
        return EnvironmentContext.getServer();
    }

    public static void restartSelf() {
        action(CommandConst.ACTION_RESTART, null, CommandConst.SESSION_COMMON);
    }

    public static void noticeInfo(String message, String sessionId) {
        if (StringUtils.isEmpty(message)) {
            return;
        }
        action(CommandConst.ACTION_NOTICE_INFO, message, sessionId);
    }

    public static void noticeWarn(String message, String sessionId) {
        if (StringUtils.isEmpty(message)) {
            return;
        }
        action(CommandConst.ACTION_NOTICE_WARN, message, sessionId);
    }

    public static void noticeError(String message, String sessionId) {
        if (StringUtils.isEmpty(message)) {
            return;
        }
        action(CommandConst.ACTION_NOTICE_ERROR, message, sessionId);
    }

    /**
     * 初始化Spring容器中的{@link CommandProcessor}的bean<br>
     * 前置条件：引入了spring-boot-starter-jarboot的依赖
     * @param context Spring Context
     */
    @SuppressWarnings("all")
    public static void springContextInit(Object context) {
        Map<String, CommandProcessor> beans = null;
        //获取
        try {
            beans = (Map<String, CommandProcessor>)context.getClass()
                    .getMethod("getBeansOfType", java.lang.Class.class)
                    .invoke(context, CommandProcessor.class);
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }
        if (null == beans || beans.isEmpty()) {
            return;
        }
        beans.forEach((k, v) -> {
            //未使用Name注解定义命令时，以bean的Name作为命令名
            String cmd = k;
            Name name = v.getClass().getAnnotation(Name.class);
            if (!(null == name || null == name.value() || name.value().isEmpty())) {
                cmd = name.value();
            }
            if (CommandBuilder.EXTEND_MAP.containsKey(cmd)) {
                //命令重复
                logger.warn("User-defined command {} is repetitive in spring boot.", k);
                return;
            }
            CommandBuilder.EXTEND_MAP.put(cmd, v);
        });
    }

    private static void action(String name, String param, String sessionId) {
        if (StringUtils.isEmpty(sessionId)) {
            sessionId = CommandConst.SESSION_COMMON;
        }
        try {
            distributeAction(name, param, sessionId);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    private static void distributeAction(String name, String param, String sessionId) {
        CommandResponse response = new CommandResponse();
        response.setResponseType(ResponseType.ACTION);
        response.setSuccess(true);
        HashMap<String, String> body = new HashMap<>(2);
        body.put(CommandConst.ACTION_PROP_NAME_KEY, name);
        if (null != param && !param.isEmpty()) {
            body.put(CommandConst.ACTION_PROP_PARAM_KEY, param);
        }
        String bodyData = JsonUtils.toJsonString(body);
        response.setBody(bodyData);
        response.setSessionId(sessionId);
        ResultStreamDistributor.write(response);
    }

    private AgentServiceOperator() {}
}
