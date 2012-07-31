package com.wizecore.graylog;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

/**
 * Java java.util.logging.Handler implementation which sends messages to Graylog2 in GELF format.
 * <p>
 * Loosely based on https://github.com/Graylog2/gelfj implementation for log2j by Anton Yakimov &amp; Jochen Schalanda.
 *
 * @author Huksley <husley@wizecore.com>
 * @author Anton Yakimov
 * @author Jochen Schalanda
 */
public class GelfHandler extends Handler implements GelfMessageProvider {

    private static final int MAX_SHORT_MESSAGE_LENGTH = 250;
    private static final String ORIGIN_HOST_KEY = "originHost";
    private static final String LOGGER_NAME = "logger";
    private static final String LOGGER_NDC = "loggerNdc";
    private static final String JAVA_TIMESTAMP = "timestampMs";
	
    protected LogManager manager = LogManager.getLogManager();
    protected GelfSender sender;
    protected String originHost;
    protected String facility;
    protected boolean extractStacktrace = true;
    protected boolean addExtendedInformation = true;
    protected Map<String, String> fields;    

    protected String getLocalHostName() {
        try {
        	return InetAddress.getLocalHost().getHostName();
        } catch (IOException e) {
        	// Don`t care
        	e.printStackTrace();
        	return null;
        }
    }
    
    public GelfHandler() {
		originHost = getLocalHostName();
	}

    public String getFacility() {
        return facility;
    }

    public void setFacility(String facility) {
        this.facility = facility;
    }

    public boolean isExtractStacktrace() {
        return extractStacktrace;
    }

    public void setExtractStacktrace(boolean extractStacktrace) {
        this.extractStacktrace = extractStacktrace;
    }

    public String getOriginHost() {
        return originHost;
    }

    public void setOriginHost(String originHost) {
        this.originHost = originHost;
    }

    public boolean isAddExtendedInformation() {
        return addExtendedInformation;
    }

    public void setAddExtendedInformation(boolean addExtendedInformation) {
        this.addExtendedInformation = addExtendedInformation;
    }
    
    public Map<String, String> getFields() {
        if (fields == null) {
            fields = new HashMap<String, String>();
        }
        return fields;
    }

    public GelfSender getSender() {
        return sender;
    }

    public void close() {
    	if (sender != null) {
    		sender.close();
    		sender = null;
    	} 
    }
    
    @Override
    public void publish(LogRecord record) {
    	// long s = System.currentTimeMillis();
		
		if (!isLoggable(record)) {
            return;
        }

		if (sender == null) {
    		try {
    			configure();
    		} catch (IOException e) {
    			// Don`t care
    			e.printStackTrace();    			
    		}
    	}
    	
    	if (sender != null) {
			GelfMessage m = makeMessage(record);
			if (m != null) {
		        try {
		        	sender.sendMessage(m);
		        } catch (IOException e) {
		        	// Don`t care
		        	e.printStackTrace();
		        }
			}
    	}
    }
    
    protected void configure() throws IOException {    	
    	String cname = getClass().getName();		
		setLevel(getLevelProperty(cname + ".level", Level.INFO));
		setFilter(getFilterProperty(cname + ".filter", null));
		setFormatter(getFormatterProperty(cname + ".formatter", new SimpleFormatter()));

		GelfSender s = new GelfSender(
			getStringProperty(cname + ".host", "localhost"), 
			Integer.parseInt(getStringProperty(cname + ".port", String.valueOf(GelfSender.DEFAULT_PORT)))
		);		
		
		addExtendedInformation = "true".equalsIgnoreCase(getStringProperty(cname + ".extended", "true"));
		extractStacktrace = "true".equalsIgnoreCase(getStringProperty(cname + ".stacktrace", "true"));
		facility = getStringProperty(cname + ".facility", facility);
		originHost = getStringProperty(cname + ".originHost", originHost);
		
		String f = getStringProperty(cname + ".fields", null);
		if (f != null) {
			for (StringTokenizer en = new StringTokenizer(f, ",; \r\n\t"); en.hasMoreElements();) {
				String v = en.nextToken();
				if (v != null && v.trim().equals("=")) {
					String n = v;
					int eqi = v.indexOf("=");
					if (eqi >= 0) {
						v = v.substring(eqi + 1);
						n = v.substring(0, eqi);
						getFields().put(n, v);
					}
				}
			}
		}
		
		sender = s;
		
		// FIXME: Which is the best way to log inside logging handler? Obviosly not using logging infrastucture we are part of.
		System.err.println("Starting GELF handler: gelf://" + 
						sender.getHost().getHostName() + ":" + sender.getPort() + 
						", min level " + getLevel() + 
						", facility " + getFacility());
	}

	protected GelfMessage makeMessage(LogRecord event) {
        long timeStamp = event.getMillis();
        Level level = event.getLevel();

        String renderedMessage = getFormatter() != null ? getFormatter().format(event) : event.getMessage();
        if (renderedMessage == null) {
        	// Nothing to publish
            return null;
        }

        String shortMessage;
        if (renderedMessage.length() > MAX_SHORT_MESSAGE_LENGTH) {
            shortMessage = renderedMessage.substring(0, MAX_SHORT_MESSAGE_LENGTH - 1);
        } else {
            shortMessage = renderedMessage;
        }

        if (isExtractStacktrace()) {
            Throwable t = event.getThrown();
            if (t != null) {
                renderedMessage += "\n\r" + GelfMessage.extractStacktrace(t);
            }
        }
        
        GelfMessage gelfMessage = new GelfMessage(shortMessage, renderedMessage, timeStamp, getSyslogEquivalent(level), null, null);        

        if (getOriginHost() != null) {
            gelfMessage.setHost(getOriginHost());
        }

        if (getFacility() != null) {
            gelfMessage.setFacility(getFacility());
        }

        Map<String, String> fields = getFields();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            gelfMessage.addField(entry.getKey(), entry.getValue());
        }

        if (isAddExtendedInformation()) {
            gelfMessage.addField(JAVA_TIMESTAMP, Long.toString(gelfMessage.getJavaTimestamp()));
            gelfMessage.addField("thread_name", Thread.currentThread().getName());
            gelfMessage.addField("original_level", level.getName());
            gelfMessage.addField("char_length", renderedMessage.length());
            
            if (event.getSourceClassName() != null) {
            	if (event.getSourceMethodName() != null) {
            		gelfMessage.addField("source", event.getSourceClassName() +  "." + event.getSourceMethodName());
            	} else {
            		gelfMessage.addField("source", event.getSourceClassName());
            	}
            }
            
            // Only add logger if it is different from originating class name
            if (event.getSourceClassName() == null || (event.getLoggerName() != null && !event.getLoggerName().equals(event.getSourceClassName()))) {
                gelfMessage.addField(LOGGER_NAME, event.getLoggerName());
            }
        }

        return gelfMessage;
    }
    
    public static String getSyslogEquivalent(Level level) {
    	int lev = 6; // LEVEL_INFO;
		switch (level.intValue()) {
		case 1000: // SEVERE
			lev = 3; // LEVEL_ERROR;
			break;			
		case 900: // WARNING
			lev = 4; // LEVEL_WARN;
			break;
		}
		return String.valueOf(lev);
	}

    
    public void flush() {
    	// Does nothing
    }
    
    Level getLevelProperty(String name, Level defaultValue) {
		String val = manager.getProperty(name);
		if (val == null) {
			return defaultValue;
		}
		try {
			return Level.parse(val.trim());
		} catch (Exception ex) {
			return defaultValue;
		}
	}

	Filter getFilterProperty(String name, Filter defaultValue) {
		String val = manager.getProperty(name);
		try {
			if (val != null) {
				Class<?> clz = ClassLoader.getSystemClassLoader().loadClass(val);
				return (Filter) clz.newInstance();
			}
		} catch (Exception ex) {
			// We got one of a variety of exceptions in creating the
			// class or creating an instance.
			// Drop through.
		}
		// We got an exception. Return the defaultValue.
		return defaultValue;
	}

	Formatter getFormatterProperty(String name, Formatter defaultValue) {
		String val = manager.getProperty(name);
		try {
			if (val != null) {
				Class<?> clz = ClassLoader.getSystemClassLoader().loadClass(val);
				return (Formatter) clz.newInstance();
			}
		} catch (Exception ex) {
			// We got one of a variety of exceptions in creating the
			// class or creating an instance.
			// Drop through.
		}
		// We got an exception. Return the defaultValue.
		return defaultValue;
	}

	String getStringProperty(String name, String defaultValue) {
		String val = manager.getProperty(name);
		if (val == null) {
			return defaultValue;
		}
		return val.trim();
	}

	/**
	 * Setter for {@link GelfHandler#sender}.
	 */
	public void setSender(GelfSender sender) {
		this.sender = sender;
	}
}
