package com.wizecore.graylog;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Gelf message data, including optional additional fields.
 */
public class GelfMessage {

    private static final String GELF_VERSION = "1.0";

    private String version = GELF_VERSION;
    private String host;
    private String shortMessage;
    private String fullMessage;
    private Long timestamp;
    private long javaTimestamp;
    private String level;
    private String facility = "gelf-java";
    private String line;
    private String file;
    private Map<String, Object> additonalFields = new HashMap<String, Object>();

    public GelfMessage() {
    }

    // todo: merge these constructors.
    
    public GelfMessage(String shortMessage, String fullMessage, Date timestamp, String level) {
        this.shortMessage = shortMessage;
        this.fullMessage = fullMessage;
        this.javaTimestamp = timestamp.getTime();
        this.timestamp = javaTimestamp / 1000L;
        this.level = level;
    }

    public GelfMessage(String shortMessage, String fullMessage, Long timestamp, String level, String line, String file) {
        this.shortMessage = shortMessage;
        this.fullMessage = fullMessage;
        this.javaTimestamp = timestamp;
        this.timestamp = javaTimestamp / 1000L;
        this.level = level;
        this.line = line;
        this.file = file;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getShortMessage() {
        return shortMessage;
    }

    public void setShortMessage(String shortMessage) {
        this.shortMessage = shortMessage;
    }

    public String getFullMessage() {
        return fullMessage;
    }

    public void setFullMessage(String fullMessage) {
        this.fullMessage = fullMessage;
    }

    public Long getTimestamp() {
        return timestamp;
    }
    
    public Long getJavaTimestamp() {
        return javaTimestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getFacility() {
        return facility;
    }

    public void setFacility(String facility) {
        this.facility = facility;
    }

    public String getLine() {
        return line;
    }

    public void setLine(String line) {
        this.line = line;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public GelfMessage addField(String key, String value) {
        getAdditonalFields().put(key, value);
        return this;
    }

    public GelfMessage addField(String key, Object value) {
        getAdditonalFields().put(key, value);
        return this;
    }

    public Map<String, Object> getAdditonalFields() {
        return additonalFields;
    }

    public void setAdditonalFields(Map<String, Object> additonalFields) {
        this.additonalFields = additonalFields;
    }

    public boolean isValid() {
        return !isEmpty(version) && !isEmpty(host) && !isEmpty(shortMessage) && !isEmpty(facility);
    }

    public boolean isEmpty(String str) {
        return str == null || "".equals(str.trim());
    }
    
	public static String extractStacktrace(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();
    }
}
