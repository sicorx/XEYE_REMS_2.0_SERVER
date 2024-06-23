package com.hoonit.xeye.net.server.gw;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;

import com.hoonit.xeye.dao.XEyeDAO;
import com.hoonit.xeye.net.server.notify.NotifyServer;
import com.hoonit.xeye.queue.ControlQueue;
import com.hoonit.xeye.queue.DataQueue;
import com.hoonit.xeye.util.ResourceBundleHandler;
  
/** 
 * Gateway 통신을 위한 Interface Server
 */  
public class IFServer {  
    
	protected final Logger logger = Logger.getLogger(getClass().getName());
	
	private static String IP = "";
	
    private static int PORT_NUMBER = 12100;
      
    private Abortable abortable;
    
    private ServerThread serverThread;
    
    private NotifyServer notifyServer;
    
    private XEyeDAO xeyeDAO;
    
    private DataQueue dataQueue;
    
    private ControlQueue ctlQueue;
      
    /** 
     *  생성자
     */  
    public IFServer() {
    	
    	try{
    		
    		IP = InetAddress.getLocalHost().getHostAddress();
    		
    	}catch(Exception e){
    		logger.error(e.getMessage(), e);
    	}
    	
    	PORT_NUMBER = Integer.parseInt(ResourceBundleHandler.getInstance().getString("server.port"));
    	
        abortable = new Abortable();
    }
    
    /**
     * NotifyServer
     * @param notifyServer
     */
    public void setNotifyServer(NotifyServer notifyServer){
    	this.notifyServer = notifyServer;
    }
    
    public NotifyServer getNotifyServer(){
    	return this.notifyServer;
    }
    
    public void setXEyeDAO(XEyeDAO xeyeDAO){
    	this.xeyeDAO = xeyeDAO;
    }
    
    public void setDataQueue(DataQueue dataQueue){
    	this.dataQueue = dataQueue;
    }
    
    public void setControlQueue(ControlQueue ctlQueue){
    	this.ctlQueue = ctlQueue;
    }
      
    /** 
     * Sart server 
     */  
    public void start() {
          
        abortable.init();
        
        if (serverThread == null || !serverThread.isAlive()) {
            serverThread = new ServerThread(this, abortable);
            serverThread.start();
        }
    }
      
    /** 
     * Stop server 
     */  
    public void stop() {
        
        abortable.setDone(true);
          
        if (serverThread != null && serverThread.isAlive()) {
            serverThread.interrupt();
        } 
    }
      
    /** 
     * Server thread
     */  
    public class ServerThread extends Thread {
        
    	private IFServer server;
    	
        private Abortable abortable;
        
        private List<Thread> clientList = new ArrayList<Thread>();
          
        public ServerThread(IFServer server, Abortable abortable) {
        	this.server    = server;
            this.abortable = abortable;
        }
        
        public List<Thread> getClientList(){
        	return this.clientList;
        }
  
        @Override
        public void run() {
        	
            super.run();
              
            ServerSocketChannel server = null;
            Selector selector = null;
              
            try {
                
                logger.info("IF Server is started with " + IP + ":" + PORT_NUMBER);
                
                server = ServerSocketChannel.open();
                server.socket().bind(new InetSocketAddress(IP, PORT_NUMBER));
                server.configureBlocking(false);
                  
                selector = Selector.open();
                server.register(selector, SelectionKey.OP_ACCEPT);
                  
                logger.info("IF Server waiting for accept");
                
                while (!Thread.interrupted() && !abortable.isDone()) {
                	
                    selector.select(3000);
                      
                    Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
                    
                    while (iter.hasNext()) {
                          
                        SelectionKey key = iter.next();
                        
                        if (key.isAcceptable()) {
                            
                            SocketChannel client = server.accept();
                              
                            if (client != null) {
                                
                                logger.info("IF Server accepted client : " + client.getRemoteAddress());
                                
                                ClientHandlerThread t = new ClientHandlerThread(this, abortable, client);
                                t.setNotifyServer(this.server.getNotifyServer());
                                t.setDataQueue(dataQueue);
                                t.setControlQueue(ctlQueue);
                                t.setXEyeDAO(xeyeDAO);
                                
                                notifyServer.addNotifyListener(t);
                                
                                t.start();
                                
                                clientList.add(t);
                                
                                logger.info("Connected client size : " + clientList.size());
                            }
                        }
                    }
                }
                  
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            } finally {  
                
                for (Thread t : clientList) {
                      
                    if (t != null && t.isAlive())
                        t.interrupt();
                    
                    try {
                        t.join(1000);
                    } catch (InterruptedException e) {
                        logger.error(e.getMessage(), e);
                    }
                }
                  
                if (server != null) {
                      
                    try {
                        server.close();
                    } catch (IOException e) {
                    	logger.error(e.getMessage(), e);
                    }
                }
                
                logger.info("Server is closed");  
            }
        }
    }
}