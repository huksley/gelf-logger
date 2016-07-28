package com.wizecore.graylog;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;

import com.wizecore.graylog.GelfSender.Protocol;

/**
 * @author Ruslan Gainutdinov <huksley@wizecore.com>
 * @author Anton Yakimov
 * @author Jochen Schalanda
 */
public class GelfAppender extends AppenderSkeleton {
    protected GelfSender sender;
    protected String originHost;
    protected String facility;
    protected boolean extractStacktrace = true;
    protected boolean addExtendedInformation = true;
    protected String fields;
    protected Map<String, String> preparedFields;
    protected String protocol = "udp";
    protected String host = "localhost";
    protected int port = GelfSender.DEFAULT_PORT;
    protected String updater;
    protected GelfMessageUpdater updaterInstance;
    
    public GelfAppender() {
    	originHost = GelfSender.findLocalHostName();
	}
	
	protected GelfMessage makeMessage(LoggingEvent event) {
        long timeStamp = event.getTimeStamp();
        Level level = event.getLevel();

        String renderedMessage = null;
        if (event.getMessage() != null) {
        	renderedMessage = event.getMessage().toString();
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
        Throwable t = event.getThrowableInformation() != null ? event.getThrowableInformation().getThrowable() : null;
        if (isExtractStacktrace() && t != null) {
        	m = new GelfMessage();
        	renderedMessage += "\n" + GelfMessage.extractStacktrace(t, m, 0);
        }
        
        GelfMessage gelfMessage = new GelfMessage(shortMessage, renderedMessage, timeStamp, level.getSyslogEquivalent(), null, 0);
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
	public void activateOptions() {
		super.activateOptions();
		
		Protocol proto = Protocol.UDP;
		if (protocol.equalsIgnoreCase("udp")) {
			proto = Protocol.UDP;
		} else
		if (protocol.equalsIgnoreCase("tcp")) {
			proto = Protocol.TCP;
		} else {
			throw new IllegalArgumentException("Unknown protocol: " + protocol);
		}
		
		if (facility == null) {
			facility = System.getProperty("jvmRoute", "gelf-logger");
		}
		
		if (updater != null) {
			try {
				updaterInstance = (GelfMessageUpdater) Class.forName(updater).newInstance();
			} catch (Exception e) {
				System.err.println("GelfHandler: failed to create " + updater + " instance: " + e);
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
		System.err.println("Started GELF appender: " + protocol + "://" + sender.getHost() + ":" + sender.getPort() + 
				", facility " + getFacility() + ", originHost " + getOriginHost());		
	}
 
    @Override
    protected void append(LoggingEvent event) {
        GelfMessage gelfMessage = makeMessage(event);
        
        if (sender != null && gelfMessage != null) {
        	try {
				sender.sendMessage(gelfMessage);
			} catch (IOException e) {
				errorHandler.error("Failed to send message: " + e);
			}
        }
    }

    @Override
    public void close() {
        if (sender != null) {
            sender.close();
            sender = null;
        }
    }

    @Override
    public boolean requiresLayout() {
        return true;
    }

	public GelfSender getSender() {
		return sender;
	}

	public void setSender(GelfSender sender) {
		this.sender = sender;
	}

	public String getOriginHost() {
		return originHost;
	}

	public void setOriginHost(String originHost) {
		this.originHost = originHost;
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

	public boolean isAddExtendedInformation() {
		return addExtendedInformation;
	}

	public void setAddExtendedInformation(boolean addExtendedInformation) {
		this.addExtendedInformation = addExtendedInformation;
	}

	public String getFields() {
		return fields;
	}

	public void setFields(String fields) {
		this.fields = fields;
	}

	public String getProtocol() {
		return protocol;
	}

	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public Map<String, String> getPreparedFields() {
		return preparedFields;
	}

	public void setPreparedFields(Map<String, String> preparedFields) {
		this.preparedFields = preparedFields;
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
