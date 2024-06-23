package com.hoonit.xeye;

import com.hoonit.xeye.dao.XEyeDAO;
import com.hoonit.xeye.dao.XEyeDAOImpl;
import com.hoonit.xeye.net.server.gw.IFServer;
import com.hoonit.xeye.net.server.notify.NotifyServer;
import com.hoonit.xeye.queue.ControlQueue;
import com.hoonit.xeye.queue.DataQueue;

public class Main {

	public static void main(String[] args){
		
		try{
			
			NotifyServer notifyServer = new NotifyServer();
	        notifyServer.doStart();
			
	        Thread.sleep(500);
	        
	        XEyeDAO xeyeDAO = new XEyeDAOImpl();
	        
	        DataQueue dataQueue = new DataQueue();
	        dataQueue.setXEyeDAO(xeyeDAO);
	        dataQueue.doStart();
	        
	        ControlQueue ctlQueue = new ControlQueue();
	        ctlQueue.setXEyeDAO(xeyeDAO);
	        ctlQueue.doStart();
	        
			IFServer ifServer = new IFServer();
			ifServer.setNotifyServer(notifyServer);
			ifServer.setXEyeDAO(xeyeDAO);
			ifServer.setDataQueue(dataQueue);
			ifServer.setControlQueue(ctlQueue);
			ifServer.start();
	        
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}
