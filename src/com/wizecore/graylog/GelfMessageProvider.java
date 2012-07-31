package com.wizecore.graylog;

import java.util.Map;

/**
 * Copy of <a href="https://github.com/Graylog2/gelfj/blob/master/src/main/java/org/graylog2/GelfMessageProvider.java">https://github.com/Graylog2/gelfj/blob/master/src/main/java/org/graylog2/GelfMessageProvider.java</a>.
 */
public interface GelfMessageProvider {
    public boolean isExtractStacktrace();
    public String getOriginHost();
    public String getFacility();
    public Map<String, String> getFields();
    public boolean isAddExtendedInformation();
}
