package com.hoonit.xeye.queue;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.apache.log4j.Logger;

import com.hoonit.xeye.dao.XEyeDAO;
import com.hoonit.xeye.util.ResourceBundleHandler;

public class ControlQueue extends Thread{

	protected final Logger logger = Logger.getLogger(getClass().getName());
	
	private XEyeDAO xeyeDAO;

	@SuppressWarnings("rawtypes")
	private BlockingQueue queue = null;
	
	@SuppressWarnings("rawtypes")
	public ControlQueue(){
		this.queue = new ArrayBlockingQueue(Integer.parseInt(ResourceBundleHandler.getInstance().getString("queue.count")));
	}
	
	public void setXEyeDAO(XEyeDAO xeyeDAO){
    	this.xeyeDAO = xeyeDAO;
    }

	public void doStart(){
		this.start();
		logger.info("Control Queue start...");
	}

	@SuppressWarnings("unchecked")
	public void doAddData(Map<String, String> paramMap){

		try{
			this.queue.put(paramMap);
		}catch(Exception e){
			logger.error(e.getMessage(),e );
		}
	}

	@Override
	public void run(){
		
		Map<String, String> paramMap = null;
		
		while(true){

			try{
				
				paramMap = (Map<String, String>)this.queue.take();
				
				try{
					
					// 매장간판제어로그 및 간판상태 등록
					if("SIGN".equals(paramMap.get("CTL_TYPE"))){
						
						// 매장간판제어로그 등록
						xeyeDAO.insertTH_STR_SIGN_CTRL_LOG(paramMap);
						
						// 매장간판상태 등록
						xeyeDAO.insertTH_STR_SIGN_STATUS(paramMap);
					}
					// 매장에어컨제어로그 등록
					else if("AIRCON".equals(paramMap.get("CTL_TYPE"))){
						
						xeyeDAO.insertTH_STR_AIRCON_CTRL_LOG(paramMap);
					}
					
				}catch(Exception e){
					logger.error(e.getMessage(), e);
				}
				
			}catch(Exception e){
				logger.error(e.getMessage(), e);
			}
		}
	}
}
