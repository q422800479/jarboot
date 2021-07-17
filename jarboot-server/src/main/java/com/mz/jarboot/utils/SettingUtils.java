package com.mz.jarboot.utils;

import com.alibaba.fastjson.JSONObject;
import com.mz.jarboot.common.OSUtils;
import com.mz.jarboot.common.ResultCodeConst;
import com.mz.jarboot.constant.CommonConst;
import com.mz.jarboot.common.MzException;
import com.mz.jarboot.dto.GlobalSettingDTO;
import com.mz.jarboot.dto.ServerSettingDTO;
import com.mz.jarboot.event.ApplicationContextUtils;
import com.mz.jarboot.event.NoticeEnum;
import com.mz.jarboot.ws.WebSocketManager;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.util.*;

public class SettingUtils {
    private static final Logger logger = LoggerFactory.getLogger(SettingUtils.class);
    private static GlobalSettingDTO globalSetting = new GlobalSettingDTO();
    private static final String BOOT_PROPERTIES = "boot.properties";
    private static final String ROOT_DIR_KEY = "jarboot.services.root-dir";
    private static final String DEFAULT_JVM_OPTS_KEY = "jarboot.services.default-jvm-options";
    private static final String DEFAULT_SERVICES_DIR;
    private static final String ENABLE_AUTO_START_KEY = "jarboot.services.enable-auto-start-after-start";
    private static final String JARBOOT_CONF;

    private static String agentJar;
    static {
        JARBOOT_CONF = System.getProperty(CommonConst.JARBOOT_HOME) +
                File.separator + "conf" + File.separator + "jarboot.properties";

        //jarboot-agent.jar的路径获取
        initAgentJarPath();
        //初始化路径配置，先查找
        initGlobalSetting();
        //初始化默认目录及配置路径
        DEFAULT_SERVICES_DIR = System.getProperty(CommonConst.JARBOOT_HOME) + File.separator + CommonConst.SERVICES;
    }

    private static void initAgentJarPath() {
        CodeSource codeSource = SettingUtils.class.getProtectionDomain().getCodeSource();
        try {
            File curJar = new File(codeSource.getLocation().toURI().getSchemeSpecificPart());
            File jarFile = new File(curJar.getParentFile(), CommonConst.AGENT_JAR_NAME);

            //先尝试从当前路径下获取jar的位置
            if (jarFile.exists()) {
                agentJar = jarFile.getPath();
            } else {
                agentJar = System.getProperty(CommonConst.JARBOOT_HOME) + File.separator + CommonConst.AGENT_JAR_NAME;
                jarFile = new File(agentJar);
                if (!jarFile.exists()) {
                    throw new MzException(ResultCodeConst.NOT_EXIST, "从用户路径下未发现" + agentJar);
                }
            }
        } catch (Exception e) {
            //查找jarboot-agent.jar失败
            logger.error("Can not find jarboot-agent.jar.", e);
            System.exit(-1);
        }
    }
    private static void initGlobalSetting() {
        File conf = new File(JARBOOT_CONF);
        Properties properties = (conf.exists() && conf.isFile() && conf.canRead()) ?
                PropertyFileUtils.getProperties(conf) : new Properties();
        globalSetting.setServicesPath(properties.getProperty(ROOT_DIR_KEY, ""));
        globalSetting.setDefaultJvmArg(properties.getProperty(DEFAULT_JVM_OPTS_KEY, ""));
        String s = properties.getProperty(ENABLE_AUTO_START_KEY, "false");
        boolean servicesAutoStart = StringUtils.equalsIgnoreCase("true", s);
        globalSetting.setServicesAutoStart(servicesAutoStart);
    }

    public static GlobalSettingDTO getGlobalSetting() {
        return globalSetting;
    }

    public static void updateGlobalSetting(GlobalSettingDTO setting) {
        String servicesPath = setting.getServicesPath();
        if (StringUtils.isNotEmpty(servicesPath)) {
            File dir = new File(servicesPath);
            if (!dir.isDirectory() || !dir.exists()) {
                throw new MzException(ResultCodeConst.NOT_EXIST, String.format("配置的路径%s不存在！", servicesPath));
            }
        }

        File file = FileUtils.getFile(JARBOOT_CONF);
        try {
            HashMap<String, String> props = new HashMap<>();
            props.put(DEFAULT_JVM_OPTS_KEY, setting.getDefaultJvmArg());
            props.put(ROOT_DIR_KEY, setting.getServicesPath());
            props.put(ENABLE_AUTO_START_KEY, String.valueOf(setting.getServicesAutoStart()));
            PropertyFileUtils.writeProperty(file, props);
            //再更新到内存
            globalSetting.setDefaultJvmArg(setting.getDefaultJvmArg());
            globalSetting.setServicesPath(setting.getServicesPath());
            globalSetting.setServicesAutoStart(setting.getServicesAutoStart());
        } catch (Exception e) {
            throw new MzException(ResultCodeConst.INTERNAL_ERROR, "更新全局配置文件失败！", e);
        }
    }


    public static String getServicesPath() {
        String path = globalSetting.getServicesPath();
        if (StringUtils.isBlank(path)) {
            path = DEFAULT_SERVICES_DIR;
        }
        return path;
    }

    public static String getAgentStartOption(String server) {
        return "-javaagent:" + agentJar + "=" + getAgentArgs(server);
    }

    public static String getAgentJar() {
        return agentJar;
    }

    public static String getAgentArgs(String server) {
        JSONObject json = new JSONObject();
        String port = ApplicationContextUtils.getEnv(CommonConst.PORT_KEY, CommonConst.DEFAULT_PORT);
        String host = String.format("127.0.0.1:%s", port);
        json.put("host", host);
        json.put("server", server);
        byte[] bytes = Base64.getEncoder().encode(json.toJSONString().getBytes());
        return new String(bytes);
    }

    public static String getDefaultJvmArg() {
        return globalSetting.getDefaultJvmArg();
    }

    /**
     * 获取服务的jar包路径
     * @param setting 服务配置
     * @return jar包路径
     */
    public static String getJarPath(ServerSettingDTO setting) {
        String server = setting.getServer();
        String serverPath = getServerPath(server);
        File dir = new File(serverPath);
        if (!dir.isDirectory() || !dir.exists()) {
            logger.error("未找到{}服务的jar包路径{}", server, dir.getPath());
            WebSocketManager.getInstance().notice("未找到服务" + server + "的可执行jar包路径", NoticeEnum.WARN);
        }
        if (StringUtils.isNotEmpty(setting.getJar())) {
            //配置制定了jar文件
            File jar = new File(dir, setting.getJar());
            if (jar.exists() && jar.isFile()) {
                return jar.getPath();
            } else {
                WebSocketManager.getInstance()
                        .notice("设置启动的jar文件不存在，请重新设置！", NoticeEnum.WARN);
            }
        }

        //未指定时
        String[] extensions = {"jar"};
        Collection<File> jarList = FileUtils.listFiles(dir, extensions, false);
        if (org.apache.commons.collections.CollectionUtils.isEmpty(jarList)) {
            logger.error("在{}未找到{}服务的jar包", server, dir.getPath());
            WebSocketManager.getInstance().notice("未找到服务" + server + "的可执行jar包", NoticeEnum.ERROR);
            return "";
        }
        if (jarList.size() > 1) {
            String msg = String.format("在服务%s目录找到了多个jar文件，请设置启动的jar文件！", server);
            WebSocketManager.getInstance().notice(msg, NoticeEnum.WARN);
            return "";
        }
        if (jarList.iterator().hasNext()) {
            File jarFile = jarList.iterator().next();
            return jarFile.getPath();
        }
        return "";
    }

    public static String getServerPath(String server) {
        return getServicesPath() + File.separator + server;
    }

    public static String getServerSettingFilePath(String server) {
        StringBuilder builder = new StringBuilder();
        builder.append(getServicesPath()).append(File.separator).append(server).append(File.separator).append(BOOT_PROPERTIES);
        return builder.toString();
    }

    public static String getJvm(String server, String file) {
        String path;
        if (StringUtils.isBlank(file)) {
            file = CommonConst.DEFAULT_JVM_FILE;
        }
        if (SettingUtils.isAbsPath(file)) {
            // 绝对路径
            path = file;
        } else {
            path = SettingUtils.getServerPath(server) + File.separator + file;
        }
        File f = new File(path);
        StringBuilder sb = new StringBuilder();
        if (f.exists()) {
            List<String> lines;
            try {
                lines = FileUtils.readLines(f, StandardCharsets.UTF_8);
            } catch (IOException e) {
                WebSocketManager.getInstance().notice(e.getMessage(), NoticeEnum.WARN);
                throw new MzException("Read file error.", e);
            }
            lines.forEach(line -> sb.append(line).append(' '));
        }
        return sb.toString().trim();
    }

    public static boolean isAbsPath(String path) {
        if (StringUtils.isBlank(path)) {
            return false;
        }
        if (OSUtils.isWindows()) {
            if (path.length() < 2) {
                return false;
            }
            char c = path.charAt(0);
            return (((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) && path.charAt(1) == ':');
        }
        return (path.startsWith("/") || path.startsWith("\\"));
    }

    private SettingUtils() {

    }
}