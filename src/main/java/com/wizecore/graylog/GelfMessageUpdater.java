package com.wizecore.graylog;

/**
 * Override to provide additional message information.
 * 
 * @author ruslan
 */
public interface GelfMessageUpdater {

	/**
	 * Provide additional information in message.
	 */
	void update(GelfMessage m);
}
