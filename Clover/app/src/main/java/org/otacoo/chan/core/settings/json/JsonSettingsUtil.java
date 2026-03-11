package org.otacoo.chan.core.settings.json;

import java.util.Map;

/**
 * Small helper to construct JsonSettings from simple key/value pairs.
 * Placed in the same package so it can access package-private internals.
 */
public class JsonSettingsUtil {
    public static JsonSettings fromMap(Map<String, String> map) {
        JsonSettings js = new JsonSettings();
        if (map == null || map.isEmpty()) return js;
        for (Map.Entry<String, String> e : map.entrySet()) {
            StringJsonSetting s = new StringJsonSetting();
            s.key = e.getKey();
            s.value = e.getValue();
            js.settings.put(e.getKey(), s);
        }
        return js;
    }
}
