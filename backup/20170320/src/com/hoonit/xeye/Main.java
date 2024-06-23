package com.hoonit.xeye;

import com.hoonit.xeye.net.server.gw.IFServer;
import com.hoonit.xeye.net.server.notify.NotifyServer;

public class Main {

	public static void main(String[] args){
		
		try{
			
			NotifyServer notifyServer = new NotifyServer();
	        notifyServer.doStart();
			
	        Thread.sleep(500);
	        
			IFServer ifServer = new IFServer();
			ifServer.setNotifyServer(notifyServer);
			ifServer.start();
	        
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}
