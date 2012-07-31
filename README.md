graylog2-gelf-jul-handler
=========================

JUL (java.util.logging) handler which sends messages to graylog2 in GELF format.

Usage
======

If you are using logging.properties file (-Djava.util.logging.config.file=logging.properties)
	
	# Add to handlers
	handlers=java.util.logging.ConsoleHandler, com.wizecore.graylog.GelfHandler
	
	# You MUST configure facility for logger
	com.wizecore.graylog.GelfHandler.facility =
	
	# Set host where graylog2-server is installed
	com.wizecore.graylog.GelfHandler.host = localhost
	
	# Rest is optional
	# com.wizecore.graylog.GelfHandler.port = 12201
	# com.wizecore.graylog.GelfHandler.extended = true
	# com.wizecore.graylog.GelfHandler.stacktrace = true
	# com.wizecore.graylog.GelfHandler.originHost = 
	# com.wizecore.graylog.GelfHandler.level = INFO
	# com.wizecore.graylog.GelfHandler.formatter = java.util.logging.SimpleFormatter
	# com.wizecore.graylog.GelfHandler.filter = 