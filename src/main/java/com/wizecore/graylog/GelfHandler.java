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

import com.wizecore.graylog.GelfSender.Protocol;

/**
 * Java java.util.logging.Handler implementation which sends messages to Graylog2 in GELF format.
 * <p>
 * Loosely based on https://github.com/Graylog2/gelfj implementation for log2j by Anton Yakimov &amp; Jochen Schalanda.
 *
 * @author Ruslan Gainutdinov <huksley@wizecore.com>
 * @author Anton Yakimov
 * @author Jochen Schalanda
 */
public class GelfHandler extends Handler {

	protected LogManager manager = LogManager.getLogManager();
    protected GelfSender sender;
    protected String originHost;
    protected String facility = "gelf-logger";
    protected boolean extractStacktrace = true;
    protected boolean addExtendedInformation = true;
    protected Map<String, String> preparedFields;
    protected String updater;
    protected GelfMessageUpdater updaterInstance;

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
		originHost = GelfSender.findLocalHostName();
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
    
    public Map<String, String> getPreparedFields() {
        if (preparedFields == null) {
            preparedFields = new HashMap<String, String>();
        }
        return preparedFields;
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
    	if (!isLoggable(record)) {
            return;
        }

		if (sender == null) {
    		try {
    			configure();
    		} catch (IOException e) {
	        	// Don`t care, but don`t printStackTrace to avoid loops
	        	System.err.println("Failed to configure sender: " + e);
    		}
    	}
    	
    	if (sender != null) {
			GelfMessage m = makeMessage(record);
			if (m != null) {
		        try {
		        	sender.sendMessage(m);
		        } catch (IOException e) {
		        	// Don`t care, but don`t printStackTrace to avoid loops
		        	System.err.println("Failed to send to graylog: " + e);
		        }
			}
    	}
    }
    
    protected void configure() throws IOException {    	
    	String cname = getClass().getName();		
		setLevel(getLevelProperty(cname + ".level", Level.INFO));
		setFilter(getFilterProperty(cname + ".filter", null));
		Formatter fmt = getFormatterProperty(cname + ".formatter", null);
		if (fmt != null) {
			setFormatter(fmt);
		}
		
		String protocol = getStringProperty(cname + ".protocol", null);
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
		
		addExtendedInformation = "true".equalsIgnoreCase(getStringProperty(cname + ".extended", "true"));		
		extractStacktrace = "true".equalsIgnoreCase(getStringProperty(cname + ".stacktrace", "true"));
		facility = getStringProperty(cname + ".facility", System.getProperty("jvmRoute", facility));
		originHost = getStringProperty(cname + ".originHost", originHost);
		updater = getStringProperty(cname + ".updater", updater);
		
		if (updater != null) {
			try {
				updaterInstance = (GelfMessageUpdater) Class.forName(updater).newInstance();
			} catch (Exception e) {
				System.err.println("GelfHandler: failed to create " + updater + " instance: " + e);
			}
		}
		
		String fields = getStringProperty(cname + ".fields", null);
		if (fields != null) {
			for (StringTokenizer en = new StringTokenizer(fields, ",; \r\n\t"); en.hasMoreElements();) {
				String v = en.nextToken();
				if (v != null && !v.trim().equals("=")) {
					String n = v;
					int eqi = v.indexOf("=");
					if (eqi >= 0) {
						v = v.substring(eqi + 1);
						n = n.substring(0, eqi);
						getPreparedFields().put(n, v);
					}
				}
			}
		}
		
		GelfSender s = new GelfSender(
			proto,
			getStringProperty(cname + ".host", "localhost"), 
			Integer.parseInt(getStringProperty(cname + ".port", String.valueOf(GelfSender.DEFAULT_PORT)))
		);	
	
		sender = s;
		System.err.println("Started GELF handler: " + protocol + "://" + sender.getHost() + ":" + sender.getPort() + 
						", min level " + getLevel() + 
						", facility " + getFacility() + ", originHost " + originHost);
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

        Map<String, String> fields = getPreparedFields();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            gelfMessage.addField(entry.getKey(), entry.getValue());
        }

        if (addExtendedInformation) {
            if (t != null) {
            	gelfMessage.addField("exception", t.getClass().getName());
            }
            if (t != null && t.getMessage() != null) {
            	gelfMessage.addField("exception_message", t.getMessage());
            }
            gelfMessage.addField("thread_name", Thread.currentThread().getName());
            gelfMessage.addField("original_level", level.getName());
            gelfMessage.addField("char_length", renderedMessage.length());
            
            if (event.getSourceClassName() != null) {
            	if (event.getSourceMethodName() != null) {
            		gelfMessage.addField("source_method", event.getSourceMethodName());
            		gelfMessage.addField("source_class", event.getSourceClassName()); 
            	} else {
            		gelfMessage.addField("source_class", event.getSourceClassName());
            	}
            }
            
            // Only add logger if it is different from originating class name
            if (event.getSourceClassName() == null || (event.getLoggerName() != null && !event.getLoggerName().equals(event.getSourceClassName()))) {
                gelfMessage.addField("logger", event.getLoggerName());
            }
        }
        
        if (updaterInstance != null) {
        	updaterInstance.update(gelfMessage);
        }

        return gelfMessage;
    }
	
	/**
     * Convert JUL level to syslog equivalent.
     */
    public static int getSyslogEquivalent(Level level) {
    	int lev = GelfMessage.SYSLOG_INFO;
		switch (level.intValue()) {
		case 1000: // SEVERE
			lev = GelfMessage.SYSLOG_ERROR;
			break;			
		case 900: // WARNING
			lev = GelfMessage.SYSLOG_WARN;
			break;
		}
		return lev;
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

	public String getUpdater() {
		return updater;
	}

	public void setUpdater(String updater) {
		this.updater = updater;
	}

	public GelfMessageUpdater getUpdaterInstance() {
		return updaterInstance;
	}

	public void setUpdaterInstance(GelfMessageUpdater updaterInstance) {
		this.updaterInstance = updaterInstance;
	}
}
