package com.wizecore.graylog;

/**
 * Override to provide additional message information.
 * 
 * @author Ruslan Gainutdinov <huksley@wizecore.com>
 */
public interface GelfMessageUpdater {

	/**
	 * Provide additional information in message.
	 */
	void update(GelfMessage m);
}
