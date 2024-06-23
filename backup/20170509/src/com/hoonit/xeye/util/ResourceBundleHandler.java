package com.hoonit.xeye.util;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import org.apache.log4j.Logger;

public class ResourceBundleHandler {
	
	private static Logger logger = Logger.getLogger(ResourceBundleHandler.class);
	
	private static ResourceBundleHandler resourceBundleHandler = new ResourceBundleHandler();
	
	private Properties props = new Properties();

	private ResourceBundleHandler() {
		
		try {
			
			String path = "resource/config.properties";
			
			this.props.load(new FileInputStream(path));
			
		} catch (FileNotFoundException e) {
			logger.error(e.getMessage(), e);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
	}

	public static ResourceBundleHandler getInstance() {
		
		if(resourceBundleHandler == null){
			resourceBundleHandler = new ResourceBundleHandler();
		}
		
		return resourceBundleHandler;
	}

	public String getString(String key) {
		return this.props.getProperty(key);
	}

	public String getString(String key, String defaultValue) {
		return this.props.getProperty(key, defaultValue);
	}
}
