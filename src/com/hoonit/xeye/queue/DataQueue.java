package com.hoonit.xeye.queue;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.apache.log4j.Logger;

import com.hoonit.xeye.dao.XEyeDAO;
import com.hoonit.xeye.util.ResourceBundleHandler;

import net.sf.json.JSONObject;

public class DataQueue extends Thread{

	protected final Logger logger = Logger.getLogger(getClass().getName());
	
	private XEyeDAO xeyeDAO;

	@SuppressWarnings("rawtypes")
	private BlockingQueue queue = null;
	
	@SuppressWarnings("rawtypes")
	public DataQueue(){
		this.queue = new ArrayBlockingQueue(Integer.parseInt(ResourceBundleHandler.getInstance().getString("queue.count")));
	}
	
	public void setXEyeDAO(XEyeDAO xeyeDAO){
    	this.xeyeDAO = xeyeDAO;
    }

	public void doStart(){
		this.start();
		logger.info("Data Queue start...");
	}

	@SuppressWarnings("unchecked")
	public void doAddData(JSONObject jsonObj){

		try{
			this.queue.put(jsonObj);
		}catch(Exception e){
			logger.error(e.getMessage(), e);
		}
	}

	@Override
	public void run(){
		
		while(true){
			
			try{
				
				JSONObject jsonObj = (JSONObject)this.queue.take();
				
				if(jsonObj != null){
					DataProcessor dp = new DataProcessor(jsonObj);
					dp.setXEyeDAO(xeyeDAO);
					dp.start();
				}
				
			}catch(Exception e){
				logger.error(e.getMessage(), e);
			}
		}
	}
}
