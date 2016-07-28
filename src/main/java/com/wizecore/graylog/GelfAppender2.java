package com.wizecore.graylog;

import java.io.Serializable;

import org.apache.log4j.Category;
import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;

@Plugin(name="Gelf", category="Core", elementType="appender", printObject=true)
public class GelfAppender2 extends AbstractAppender {

	private static final long serialVersionUID = 1L;
	protected GelfAppender appender;
		
    protected GelfAppender2(GelfAppender appender, String name, Filter filter,
            Layout<? extends Serializable> layout, final boolean ignoreExceptions) {
       super(name, filter, layout, ignoreExceptions);
       this.appender = appender;
    }

    @Override
    public void append(LogEvent event) {
    	appender.append(downgrade(event));
    }
    
    private LoggingEvent downgrade(LogEvent ev) {
    	LoggingEvent e = new LoggingEvent(ev.getLoggerFqcn(), Category.getInstance(ev.getLoggerName()), ev.getTimeMillis(), Level.toLevel(ev.getLevel().name()), ev.getMessage(), ev.getThrown());
    	return e;
	}

	@Override
    public void stop() {
		if (appender != null) {
			appender.close();
		}
    	super.stop();
    }

    @PluginFactory
    public static GelfAppender2 createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Layout") Layout<? extends Serializable> layout,
            @PluginElement("Filter") final Filter filter,
            @PluginAttribute(value = "host", defaultString = "localhost") String host) {
        if (name == null) {
            LOGGER.error("No name provided for GelfAppender2");
            return null;
        }
        
        if (layout == null) {
            layout = PatternLayout.createDefaultLayout();
        }
        GelfAppender appender = new GelfAppender(); 
        appender.setHost(host);
        GelfAppender2 a = new GelfAppender2(appender, name, filter, layout, true);
        return a;
    }
}
