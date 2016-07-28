package com.wizecore.graylog;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

/**
 * Uses JMX to obtain JVM process information and update message.
 * 
 * @author Ruslan Gainutdinov <huksley@wizecore.com>
 */
public class JMXGelfUpdater implements GelfMessageUpdater {

	@Override
	public void update(GelfMessage m) {
		RuntimeMXBean r = ManagementFactory.getRuntimeMXBean();
		
		// http://stackoverflow.com/questions/35842/how-can-a-java-program-get-its-own-process-id
		String name = r.getName();
		if (name != null && name.indexOf("@") > 0) {
			String pid = name.substring(0, name.indexOf("@"));
			m.getAdditonalFields().put("pid", pid);
		}
	}
}
