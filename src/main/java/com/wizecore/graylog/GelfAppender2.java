package com.wizecore.graylog;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;

import com.wizecore.graylog.GelfSender.Protocol;

/**
 * Log2j v2. plugin with appender implementation which sends messages to Graylog2 in GELF format.
 * 
 * @author Ruslan Gainutdinov <huksley@wizecore.com>
 */
@Plugin(name="Gelf", category="Core", elementType="appender", printObject=true)
public class GelfAppender2 extends AbstractAppender {

	private static final long serialVersionUID = 1L;
	
	protected boolean addExtendedInformation = true;
	protected Map<String,String> preparedFields;
	protected String fields;
	protected String facility;
	protected String originHost;
	protected boolean extractStacktrace = true;
	protected GelfSender sender;
	protected GelfMessageUpdater updaterInstance;
	
	protected GelfAppender2(String name, Filter filter,
            Layout<? extends Serializable> layout, final boolean ignoreExceptions,
            String protocol, String host, int port, 
            boolean addExtendedInformation, 
            String fields, 
            String facility, String originHost, 
            boolean extractStackTrace, String updater) {
    	super(name, filter, layout, ignoreExceptions);
       
    	this.addExtendedInformation = addExtendedInformation;
    	this.fields = fields;
    	this.facility = facility;
    	this.originHost = originHost;
    	this.extractStacktrace = extractStackTrace;
    	
    	Protocol proto = Protocol.UDP;
		if (protocol != null && protocol.equalsIgnoreCase("udp")) {
			proto = Protocol.UDP;
		} else
		if (protocol != null && protocol.equalsIgnoreCase("tcp")) {
			proto = Protocol.TCP;
		} else 
		if (protocol != null) {
			throw new IllegalArgumentException("Unknown protocol: " + protocol);
		}
		
        if (updater != null) {
			try {
				updaterInstance = (GelfMessageUpdater) Class.forName(updater).newInstance();
			} catch (Exception e) {
				System.err.println("GelfAppender2: failed to create " + updater + " instance: " + e);
			}
		}
        
        if (fields != null) {
        	Map<String,String> preparedFields = new HashMap<String, String>();
        	for (StringTokenizer en = new StringTokenizer(fields, ",; \r\n\t"); en.hasMoreElements();) {
        		String v = en.nextToken();
        		if (v != null && !v.trim().equals("=")) {
        			String n = v;
        			int eqi = v.indexOf("=");
        			if (eqi >= 0) {
        				v = v.substring(eqi + 1);
        				n = n.substring(0, eqi);
        				preparedFields.put(n, v);
        			}
        		}
        	}
        	this.preparedFields = preparedFields;  
        }
        
        GelfSender s = new GelfSender(
        	proto,
        	host,
        	port
        );
        sender = s;
        System.err.println("Started GELF log4j2 appender: " + proto.name().toLowerCase() + "://" + sender.getHost() + ":" + sender.getPort() + 
        		", facility " + getFacility() + ", originHost " + getOriginHost());		
    }
    
    /**
     * Convert log4j2 level to syslog equivalent.
     */
    public static int getSyslogEquivalent(Level level) {
    	int lev = GelfMessage.SYSLOG_INFO;
    	if (level == Level.FATAL || level == Level.ERROR) {
    		lev = GelfMessage.SYSLOG_ERROR;
    	} else
    	if (level == Level.WARN) {
    		lev = GelfMessage.SYSLOG_WARN;
    	}
		return lev;
	}
    
    protected GelfMessage makeMessage(LogEvent event) {
        long timeStamp = event.getTimeMillis();
        Level level = event.getLevel();

        String renderedMessage = null;
        if (event.getMessage() != null) {        	
        	renderedMessage = event.getMessage().getFormattedMessage();
        }
        if (renderedMessage == null || renderedMessage.isEmpty()) {
        	return null;
        }
        
        String shortMessage;
        if (renderedMessage.length() > GelfMessage.MAX_MESSAGE_LENGTH) {
            shortMessage = renderedMessage.substring(0, GelfMessage.MAX_MESSAGE_LENGTH - 1);
        } else {
            shortMessage = renderedMessage;
        }

        // Receive stack trace and file:line
        GelfMessage m = null;
        Throwable t = event.getThrown();
        if (isExtractStacktrace() && t != null) {
        	m = new GelfMessage();
        	renderedMessage += "\n" + GelfMessage.extractStacktrace(t, m, 0);
        }
        
        GelfMessage gelfMessage = new GelfMessage(shortMessage, renderedMessage, timeStamp, getSyslogEquivalent(level), null, 0);
        if (m != null) {
        	gelfMessage.setFile(m.getFile());
        	gelfMessage.setLine(m.getLine());
        }

        if (getOriginHost() != null) {
            gelfMessage.setHost(getOriginHost());
        }

        if (getFacility() != null) {
            gelfMessage.setFacility(getFacility());
        }

        Map<String, String> fields = preparedFields;
        if (fields != null) {
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                gelfMessage.addField(entry.getKey(), entry.getValue());
            }
        }

        if (addExtendedInformation) {
            if (t != null) {
            	gelfMessage.addField("exception", t.getClass().getName());
            }
            if (t != null && t.getMessage() != null) {
            	gelfMessage.addField("exception_message", t.getMessage());
            }
            gelfMessage.addField("thread_name", Thread.currentThread().getName());
            gelfMessage.addField("original_level", level.toString());
            gelfMessage.addField("char_length", renderedMessage.length());
            
            // FIXME: Only add logger if it is different from originating class name
            gelfMessage.addField("logger", event.getLoggerName());
        }
        
        if (updaterInstance != null) {
        	updaterInstance.update(gelfMessage);
        }

        return gelfMessage;
    }

    @Override
    public void append(LogEvent event) {
    	GelfMessage gelfMessage = makeMessage(event);
        
        if (sender != null && gelfMessage != null) {
        	try {
				sender.sendMessage(gelfMessage);
			} catch (IOException e) {
				// Don`t care
			}
        }
    }
    
	@Override
    public void stop() {
		if (sender != null) {
            sender.close();
            sender = null;            
        }
		 
    	super.stop();
    }

    @PluginFactory
    public static GelfAppender2 createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Layout") Layout<? extends Serializable> layout,
            @PluginElement("Filter") final Filter filter,
            @PluginAttribute(value = "protocol") String protocol,
            @PluginAttribute(value = "host") String host,
            @PluginAttribute(value = "port") int port,
            @PluginAttribute(value = "addExtendedInformation") Boolean addExtendedInformation,
            @PluginAttribute(value = "fields") String fields,
            @PluginAttribute(value = "facility") String facility,
            @PluginAttribute(value = "originHost") String originHost,
            @PluginAttribute(value = "extractStackTrace") Boolean extractStackTrace,
            @PluginAttribute(value = "updater") String updater
            ) {
        if (name == null) {
            LOGGER.error("No name provided for GelfAppender2");
            return null;
        }
        
        if (layout == null) {
            layout = PatternLayout.createDefaultLayout();
        }
        
        if (originHost == null) {
    		originHost = GelfSender.findLocalHostName();
    	}
    	
    	if (facility == null) {
    		facility = System.getProperty("jvmRoute", "gelf-logger");
    	}    	
        
        if (addExtendedInformation == null) {
        	addExtendedInformation = true;
        }
        
        if (extractStackTrace == null) {
        	extractStackTrace = true;
        }
        
        GelfAppender2 a = new GelfAppender2(name, filter, layout, true, protocol, host, port, 
        		addExtendedInformation, fields, facility, originHost, extractStackTrace, updater);
        return a;
    }

	public Map<String, String> getPreparedFields() {
		return preparedFields;
	}

	public void setPreparedFields(Map<String, String> preparedFields) {
		this.preparedFields = preparedFields;
	}

	public String getFields() {
		return fields;
	}

	public void setFields(String fields) {
		this.fields = fields;
	}

	public String getFacility() {
		return facility;
	}

	public void setFacility(String facility) {
		this.facility = facility;
	}

	public String getOriginHost() {
		return originHost;
	}

	public void setOriginHost(String originHost) {
		this.originHost = originHost;
	}

	public boolean isExtractStacktrace() {
		return extractStacktrace;
	}

	public void setExtractStacktrace(boolean extractStacktrace) {
		this.extractStacktrace = extractStacktrace;
	}

	public boolean isAddExtendedInformation() {
		return addExtendedInformation;
	}

	public void setAddExtendedInformation(boolean addExtendedInformation) {
		this.addExtendedInformation = addExtendedInformation;
	}
}
