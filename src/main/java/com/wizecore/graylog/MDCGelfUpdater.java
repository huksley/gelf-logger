package com.wizecore.graylog;

import java.util.Map;

import org.slf4j.MDC;

/**
 * Updates from SLF4J MDC 
 * 
 * @author Ruslan Gainutdinov
 */
public class MDCGelfUpdater implements GelfMessageUpdater {

	@Override
	public void update(GelfMessage m) {
		Map<String, String> extra = MDC.getCopyOfContextMap();
		if (extra != null) {
			m.getAdditonalFields().putAll(extra);
		}
	}
}
