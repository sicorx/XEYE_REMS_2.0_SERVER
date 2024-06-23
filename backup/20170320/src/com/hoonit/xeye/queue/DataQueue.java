package com.hoonit.xeye.queue;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.apache.commons.lang.ObjectUtils;
import org.apache.log4j.Logger;

import com.hoonit.xeye.db.SqlMapManager;
import com.hoonit.xeye.util.ResourceBundleHandler;

public class DataQueue extends Thread{

	protected final Logger logger = Logger.getLogger(getClass().getName());

	private BlockingQueue queue = null;
	private boolean queueCountDebug = false;
	
	public DataQueue(){
		this.queue      = new ArrayBlockingQueue(Integer.parseInt(ResourceBundleHandler.getInstance().getString("data.queue.count")));
		queueCountDebug = Boolean.parseBoolean(ResourceBundleHandler.getInstance().getString("queue.count.debug"));
	}

	public void doStart(){
		this.start();
	}

	public void doAddTagData(Map map){

		try{
			this.queue.put(map);
		}catch(Exception e){
			logger.error(e);
		}
	}

	@Override
	public void run(){
		logger.info("데이터 이력 저장 처리 큐 구동...");
		int i = 0;
		Map map = null;
		while(true){

			try{
				map = (Map)this.queue.take();
				if (queueCountDebug){
					i++;
					if (i>100){
						i=0;
						logger.info("data.queue.size==>"+this.queue.remainingCapacity());
					}
				}

				// 아날로그 태그이면
				if("A".equals(ObjectUtils.toString(map.get("tag_type")))){
					SqlMapManager.getInstance().getSqlMapClient().insert("XEYE.insertAnalogTagData", map);
				}
				// 디지털 태그이면
				else{
					SqlMapManager.getInstance().getSqlMapClient().insert("XEYE.insertDigitalTagData", map);
				}
			}catch(Exception e){
				logger.error(e);
			} finally {
				map = null;
			}
		}
	}
}
