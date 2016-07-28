package com.wizecore.graylog;

/**
 * Override to provide additional message information.
 * 
 * @author ruslan
 */
public interface MessageUpdater {

	/**
	 * Provide additional information in message.
	 */
	void update(GelfMessage m);
}
