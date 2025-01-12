package com.mz.jarboot.core.cmd.model;

import java.util.HashMap;
import java.util.Map;

/**
 * sysenv KV Result
 * @author majianzheng
 */
@SuppressWarnings("all")
public class SystemEnvModel extends ResultModel {

    private Map<String, String> env = new HashMap<String, String>();

    public SystemEnvModel() {
    }

    public SystemEnvModel(Map env) {
        this.putAll(env);
    }

    public SystemEnvModel(String name, String value) {
        this.put(name, value);
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public String put(String key, String value) {
        return env.put(key, value);
    }

    public void putAll(Map m) {
        env.putAll(m);
    }

    @Override
    public String getName() {
        return "sysenv";
    }
}
