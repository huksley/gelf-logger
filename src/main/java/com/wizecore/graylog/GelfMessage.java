package com.wizecore.graylog;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Abstract self-contained GELF message representation, able to be converted to JSON string. 
 * 
 * @author ruslan
 */
public class GelfMessage {    
	public static final String ID_NAME = "id";	
    public static final String GELF_VERSION = "1.1";
	/**
	 * Field message max length per GELF specification. Everything above will be chunked into multiple messages (UDP)
	 */
	public static final int MAX_MESSAGE_LENGTH = 250;
	
	/**
	 * SYSLOG levels for de facto standard gelf _level field.
	 */
    public final static int SYSLOG_WARN = 4;
    public final static int SYSLOG_ERROR = 3;
    public final static int SYSLOG_INFO = 6;

    private String version = GELF_VERSION;
    private String host;
    private String shortMessage;
    private String fullMessage;
    private long timestamp;
    private int level;
    private String facility;
    private int line;
    private String file;
    private Map<String, Object> additonalFields = new HashMap<String, Object>();

    public GelfMessage() {
    }

    // todo: merge these constructors.
    
    public GelfMessage(String shortMessage, String fullMessage, Date timestamp, int level) {
        this.shortMessage = shortMessage;
        this.fullMessage = fullMessage;
        this.timestamp = timestamp.getTime();
        this.level = level != 0 ? level : SYSLOG_INFO;
    }

    public GelfMessage(String shortMessage, String fullMessage, long timestamp, int level, String file, int line) {
        this.shortMessage = shortMessage;
        this.fullMessage = fullMessage;
        this.timestamp = timestamp;
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

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getFacility() {
        return facility;
    }

    public void setFacility(String facility) {
        this.facility = facility;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
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
    
	public static String extractStacktrace(Throwable t, GelfMessage m, int elementToFileLine) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);        
        t.printStackTrace(pw);
        StackTraceElement[] st = t.getStackTrace();
		if (st != null && m != null && 
			elementToFileLine >= 0 && elementToFileLine < st.length &&
			m.getFile() == null) {
        	m.setFile(st[elementToFileLine].getFileName());
        	m.setLine(st[elementToFileLine].getLineNumber());
        }
        return sw.toString();
    }
	
	public static String formatMessage(GelfMessage m) {
        Map<String, Object> map = new HashMap<String, Object>();

        map.put("version", m.getVersion());
        map.put("host", m.getHost());
		map.put("short_message", m.getShortMessage());
		if (m.getFullMessage() == null) {
			map.put("full_message", m.getShortMessage());
		} else {
			map.put("full_message", m.getFullMessage());
		}
        long ms = m.getTimestamp();
        map.put("timestamp", ((long) Math.floor(ms / 1000.0)) + "." + (ms % 1000));

        map.put("level", m.getLevel());
        map.put("facility", m.getFacility());
        
        if (m.getFile() != null) {
        	map.put("file", m.getFile());
        }
        
        if (m.getLine() > 0) {
        	map.put("line", m.getLine());
        }

        for (Map.Entry<String, Object> additionalField : m.getAdditonalFields().entrySet()) {
            if (!ID_NAME.equals(additionalField.getKey())) {
                map.put("_" + additionalField.getKey(), additionalField.getValue());
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{ ");
        boolean start = true;
        for (Iterator<Entry<String, Object>> it = map.entrySet().iterator(); it.hasNext(); ) { 
        	Entry<String, Object> e = it.next();
        	String name = e.getKey();
        	Object value = e.getValue();
    		
        	if (start) {
        		start = false;
    		} else {
    			sb.append(", ");
    		}
    		sb.append("\"");
    		sb.append(name);
    		sb.append("\": ");
    		
        	if (value != null) {
        		if (value instanceof Double) {
            		sb.append(((Number) value).doubleValue());
        		} else
        		if (value instanceof Integer) {
            		sb.append(((Number) value).intValue());
        		} else
        		if (value instanceof Long) {
            		sb.append(((Number) value).longValue());
        		} else {
            		String s = escapeJson(value);
            		sb.append("\"");
            		sb.append(s);
            		sb.append("\"");
        		}
        	} else {
        		sb.append("null");
        	}
        }
        sb.append(" }");
        // System.out.println("GelfMessage: " + sb);
        return sb.toString();
    }

	public static String escapeJson(Object value) {
		String s = value.toString().trim();
		s = s.replace("\\", "\\\\");
		s = s.replace("\"", "\\\"");		
		s = s.replace("\n", "\\n");
		s = s.replace("\r", "\\r");
		s = s.replace("\t", "\\t");
		return s;
	}

}
