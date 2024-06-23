package com.hoonit.xeye.queue;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.apache.log4j.Logger;

import com.hoonit.xeye.dao.XEyeDAO;
import com.hoonit.xeye.util.ResourceBundleHandler;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

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
	public void doAddData(JSONObject jsonObj){

		try{
			this.queue.put(jsonObj);
		}catch(Exception e){
			logger.error(e.getMessage(),e );
		}
	}
	
	private String getLimitRangeData(String val){
		
		if(Double.parseDouble(val) >= 100.0D || Double.parseDouble(val) >= -100.0D){
			return "99.9";
		}
		
		return val;
	}

	@Override
	public void run(){
		
		while(true){

			try{
				
				JSONObject jsonObj = (JSONObject)this.queue.take();
				
				Map<String, String> paramMap = null;
				
				try{
					
					// 매장간판제어로그 및 간판상태 등록
					if("SIGN".equals(jsonObj.getString("CTL_TYPE"))){
						
						paramMap = (Map<String, String>) JSONObject.toBean(jsonObj, Map.class);
						
						// 매장간판제어로그 등록
						xeyeDAO.insertTH_STR_SIGN_CTRL_LOG(paramMap);
						
						// 매장간판상태 등록
						xeyeDAO.insertTH_STR_SIGN_STATUS(paramMap);
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
