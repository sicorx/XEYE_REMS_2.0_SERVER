package com.hoonit.xeye.net.server.gw;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;

import com.hoonit.xeye.event.NotifyEvent;
import com.hoonit.xeye.event.NotifyListener;
import com.hoonit.xeye.net.server.gw.IFServer.ServerThread;
import com.hoonit.xeye.net.server.notify.NotifyServer;
import com.hoonit.xeye.util.ByteUtils;
import com.hoonit.xeye.util.ResourceBundleHandler;
import com.hoonit.xeye.util.StringZipper;

import net.sf.json.JSONObject;

/** 
 * Client와 통신처리 기능을 담당하는 객체
 * SNMP Trap으로 온 데이터를 Client에 전송
 */  
public class ClientHandlerThread extends Thread implements NotifyListener {
    
	protected final Logger logger = Logger.getLogger(getClass().getName());
	
	private Object writeLock = new Object();
	
	private ServerThread serverThread;
	
    private Abortable abortable;
    
    private SocketChannel client;
    
    private int bufferSize = 1024;
    
    private String macAddress;
    
    private NotifyServer notifyServer;
      
    public ClientHandlerThread(ServerThread serverThread, Abortable abortable, SocketChannel client) {
    	
    	this.serverThread = serverThread;
        this.abortable    = abortable;
        this.client       = client;
        this.bufferSize   = Integer.parseInt(ResourceBundleHandler.getInstance().getString("server.buffer.size"));
    }
    
    public void setNotifyServer(NotifyServer notifyServer){
    	this.notifyServer = notifyServer;
    }
    
    /**
     * Web 또는 모바일을 통해서 수신된 데이터를 Client에 전송한다.
     * 간판제어, 냉닌방기제어, 일출/일몰, 시간설정 등...
     */
    public void notifyUpdate(NotifyEvent evt){
    	
    	synchronized(writeLock){
    		write(evt.getData());
    	}
    }
    
    public void notifyFileTransfer(String fileName){
    	
    	synchronized(writeLock){
    		
	    	try{
	    		
	    		// 파일전송
	    		File patchFile = new File("D:/xeye.zip");
	    		
	    		JSONObject jsonObject = new JSONObject();
				jsonObject.put("cmd", "4");
				jsonObject.put("fname", "xeye.zip");
				jsonObject.put("fsize", String.valueOf(patchFile.length()));
				
				write(StringZipper.getInstance().zipStringToBytes(jsonObject.toString()));
	    		
	    		BufferedInputStream bis = null;
	    		
	    		try{
	        		
	            	if(patchFile.exists()){
	            		
	                	bis = new BufferedInputStream(new FileInputStream(patchFile));
	                	
	                	byte[] fileBuffer = new byte[1024];
	                	
	                	int fileReadBytes;
	                	
	                	while ((fileReadBytes = bis.read(fileBuffer)) != -1) {
	                		write(fileBuffer);
	                		Thread.sleep(10);
	                    }
	            	}
	            	
	        	}catch(Exception e){
	        		logger.error(e.getMessage(), e);
	        	}finally{
	        		try{
	        			if(bis != null) bis.close();
	        		}catch(Exception e){
	        			logger.error(e.getMessage(), e);
	        		}
	        	}
	    		
	    	}catch(Exception e){
	    		logger.error(e.getMessage(), e);
	    	}
    	}
    }
    
    public void write(byte[] b){
    	
    	try{
    		
	    	int len = client.write(ByteBuffer.wrap(b));
	       
	    	logger.info("Write data length : " + len);
	        
    	}catch(IOException e){
    		logger.error(e.getMessage(), e);
    	}
    	
    }

    @Override  
    public void run() {
    	
        super.run();
          
        Selector selector = null;
          
        boolean done = false;
          
        try {
            
            logger.info("Client is started");
            
            client.configureBlocking(false);
            selector = Selector.open();
            
            client.register(selector, SelectionKey.OP_READ);
            
            ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
            
            while (!Thread.interrupted() && !abortable.isDone() && !done) {
            	
                selector.select(3000);
                
                Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
                
                while (!abortable.isDone() && iter.hasNext() && !done) {
                	
                    SelectionKey key = iter.next();
                    
                    if (key.isReadable()) {
                    	
                    	buffer.clear();
                    	
                        int len = client.read(buffer);
                        
                        if (len < 0) {
                            done = true;
                            break;
                        } else if (len == 0) {
                            continue;
                        }
                        
                        logger.info("read length : " + len);
                        
                        buffer.flip();
                        
                        byte[] data = new byte[len];
                        
                        int idx = 0;
                		while (buffer.hasRemaining()) {
                			data[idx++] = buffer.get();
                		}
                        
                    	/*buffer.clear(); // ByteBuffer 초기화 
                    	
                    	byte[] data = null;
                    	
                    	int len = 0;
                    	
                    	// ByteBuffer 쓰기
                    	while ((len = client.read(buffer)) > 0) {
                    	  
                    		buffer.flip(); // 쓰기 작업이 끝나고 읽기 작업을 위해 flip
                    	  
                    		data = new byte[buffer.remaining()];
                    		
                    		// remaining 함수는 읽을 수 있는 크기를 리턴
                    		int idx = 0;
                    		while (buffer.remaining() > 0) {
                    			data[idx++] = buffer.get();
                    		}  
                    		
                    		// 남은 부분을 ByteBuffer 맨 앞으로 이동
                    		// 다음 read 작업에서 남은 부분 다음부터 채움
                    		//buffer.compact();
                    	}
                    	
                    	if (len < 0) {
                            done = true;
                            break;
                        } else if (len == 0) {
                            continue;
                        }*/
                		
                		try{
                			
	                		String response = StringZipper.getInstance().unzipStringFromBytes(data);
	                        
	                        JSONObject jsonObject = JSONObject.fromObject(response);
	                        String cmd = jsonObject.getString("cmd");
	                        
	                        if(!"2".equals(cmd)){
	                        	logger.info(response);
	                        }
	                        
	                        logger.info("cmd : " + cmd);
	                        
	                        // Client 접속, MAC 주소 설정
	                        if("1".equals(cmd)){
	                        	
	                        	macAddress = jsonObject.getString("mac");
	                        	
	                        	JSONObject resObject = new JSONObject();
	                        	resObject.put("cmd", cmd);
	                        	resObject.put("res", "1");
	                        	
	                        	write(StringZipper.getInstance().zipStringToBytes(resObject.toString()));
	                        }
	                        // Data 전송일 경우
	                        else if("2".equals(cmd)){
	                        	
	                        	String d = jsonObject.getString("data");
	                        	
	                        	ByteBuffer dataBuffer = ByteBuffer.wrap(Base64.decodeBase64(d.getBytes()));
	                        	
	                        	// STX
	                    		logger.info(dataBuffer.get());
	                    		
	                    		// IP
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(dataBuffer.getShort());
	                    		
	                    		// MAC
	                    		byte[] mac1 = new byte[2];
	                    		dataBuffer.get(mac1);
	                    		byte[] mac2 = new byte[2];
	                    		dataBuffer.get(mac2);
	                    		byte[] mac3 = new byte[2];
	                    		dataBuffer.get(mac3);
	                    		byte[] mac4 = new byte[2];
	                    		dataBuffer.get(mac4);
	                    		byte[] mac5 = new byte[2];
	                    		dataBuffer.get(mac5);
	                    		byte[] mac6 = new byte[2];
	                    		dataBuffer.get(mac6);
	                    		
	                    		logger.info(new String(mac1));
	                    		logger.info(new String(mac2));
	                    		logger.info(new String(mac3));
	                    		logger.info(new String(mac4));
	                    		logger.info(new String(mac5));
	                    		logger.info(new String(mac6));
	                    		
	                    		// 통신상태
	                    		logger.info(dataBuffer.get());
	                    		
	                    		// PMC
	                    		logger.info("========PMC========");
	                    		logger.info(dataBuffer.getLong());
	                    		logger.info(ByteUtils.toUnsignedInt(dataBuffer.getInt()));
	                    		logger.info(dataBuffer.getLong());
	                    		logger.info(ByteUtils.toUnsignedInt(dataBuffer.getInt()));
	                    		logger.info(dataBuffer.getLong());
	                    		logger.info(ByteUtils.toUnsignedInt(dataBuffer.getInt()));
	                    		logger.info(dataBuffer.getLong());
	                    		logger.info(ByteUtils.toUnsignedInt(dataBuffer.getInt()));
	                    		logger.info(dataBuffer.getLong());
	                    		logger.info(ByteUtils.toUnsignedInt(dataBuffer.getInt()));
	                    		logger.info(dataBuffer.getLong());
	                    		logger.info(ByteUtils.toUnsignedInt(dataBuffer.getInt()));
	                    		logger.info(dataBuffer.getLong());
	                    		logger.info(ByteUtils.toUnsignedInt(dataBuffer.getInt()));
	                    		logger.info(dataBuffer.getLong());
	                    		logger.info(ByteUtils.toUnsignedInt(dataBuffer.getInt()));
	                    		logger.info(dataBuffer.getLong());
	                    		logger.info(ByteUtils.toUnsignedInt(dataBuffer.getInt()));
	                    		logger.info(dataBuffer.getLong());
	                    		logger.info(ByteUtils.toUnsignedInt(dataBuffer.getInt()));
	                    		logger.info(dataBuffer.getLong());
	                    		logger.info(ByteUtils.toUnsignedInt(dataBuffer.getInt()));
	                    		logger.info(dataBuffer.getLong());
	                    		logger.info(ByteUtils.toUnsignedInt(dataBuffer.getInt()));
	                    		
	                    		// 테몬
	                    		logger.info("========테몬========");
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(dataBuffer.getShort());
	                    		
	                    		// 간판상태
	                    		logger.info("========간판상태========");
	                    		logger.info(dataBuffer.get());
	                    		logger.info(dataBuffer.get());
	                    		
	                    		// 알몬
	                    		logger.info("========알몬========");
	                    		logger.info(dataBuffer.get());
	                    		logger.info(dataBuffer.get());
	                    		logger.info(dataBuffer.get());
	                    		logger.info(dataBuffer.get());
	                    		
	                    		// 하콘
	                    		logger.info("========하콘========");
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(dataBuffer.get());
	                    		logger.info(dataBuffer.get());
	                    		logger.info(dataBuffer.get());
	                    		logger.info(dataBuffer.get());
	                    		
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(dataBuffer.get());
	                    		logger.info(dataBuffer.get());
	                    		logger.info(dataBuffer.get());
	                    		logger.info(dataBuffer.get());
	                    		
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(dataBuffer.get());
	                    		logger.info(dataBuffer.get());
	                    		logger.info(dataBuffer.get());
	                    		logger.info(dataBuffer.get());
	                    		
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(dataBuffer.get());
	                    		logger.info(dataBuffer.get());
	                    		logger.info(dataBuffer.get());
	                    		logger.info(dataBuffer.get());
	                    		
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(dataBuffer.get());
	                    		logger.info(dataBuffer.get());
	                    		logger.info(dataBuffer.get());
	                    		logger.info(dataBuffer.get());
	                    		
	                    		// 티센서
	                    		logger.info("========티센서========");
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(ByteUtils.toUnsignedShort(dataBuffer.getShort()));
	                    		
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(ByteUtils.toUnsignedShort(dataBuffer.getShort()));
	                    		
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(ByteUtils.toUnsignedShort(dataBuffer.getShort()));
	                    		
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(ByteUtils.toUnsignedShort(dataBuffer.getShort()));
	                    		
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(ByteUtils.toUnsignedShort(dataBuffer.getShort()));
	                    		
	                    		// BLE
	                    		logger.info("========BLE========");
	                    		logger.info(dataBuffer.get());
	                    		
	                    		logger.info(dataBuffer.get());
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(ByteUtils.toUnsignedShort(dataBuffer.getShort()));
	                    		logger.info(dataBuffer.get());
	                    		
	                    		logger.info(dataBuffer.get());
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(ByteUtils.toUnsignedShort(dataBuffer.getShort()));
	                    		logger.info(dataBuffer.get());
	                    		
	                    		logger.info(dataBuffer.get());
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(ByteUtils.toUnsignedShort(dataBuffer.getShort()));
	                    		logger.info(dataBuffer.get());
	                    		
	                    		logger.info(dataBuffer.get());
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(ByteUtils.toUnsignedShort(dataBuffer.getShort()));
	                    		logger.info(dataBuffer.get());
	                    		
	                    		logger.info(dataBuffer.get());
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(ByteUtils.toUnsignedShort(dataBuffer.getShort()));
	                    		logger.info(dataBuffer.get());
	                    		
	                    		logger.info(dataBuffer.get());
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(ByteUtils.toUnsignedShort(dataBuffer.getShort()));
	                    		logger.info(dataBuffer.get());
	                    		
	                    		logger.info(dataBuffer.get());
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(ByteUtils.toUnsignedShort(dataBuffer.getShort()));
	                    		logger.info(dataBuffer.get());
	                    		
	                    		logger.info(dataBuffer.get());
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(ByteUtils.toUnsignedShort(dataBuffer.getShort()));
	                    		logger.info(dataBuffer.get());
	                    		
	                    		logger.info(dataBuffer.get());
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(ByteUtils.toUnsignedShort(dataBuffer.getShort()));
	                    		logger.info(dataBuffer.get());
	                    		
	                    		logger.info(dataBuffer.get());
	                    		logger.info(dataBuffer.getShort());
	                    		logger.info(ByteUtils.toUnsignedShort(dataBuffer.getShort()));
	                    		logger.info(dataBuffer.get());
	                    		
	                    		// ETX
	                    		logger.info(dataBuffer.get());
	                        	
	                        	JSONObject resObject = new JSONObject();
	                        	resObject.put("cmd", cmd);
	                        	resObject.put("res", "1");
	                        	
	                        	write(StringZipper.getInstance().zipStringToBytes(resObject.toString()));
	                        }
	                        // 일출/일몰시간 응답일 경우
	                        else if("3".equals(cmd)){
	                        	
	                        	String res = jsonObject.getString("res");
	                        	
	                        	logger.info("일출/일몰시간 응답 : "+res);
	                        }
	                        
                		}catch(Exception e){
                			logger.error(e.getMessage(), e);
                		}
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            
            if (client != null) {
                try {
                    
                	client.socket().close();
                    client.close();
                    
                    this.serverThread.getClientList().remove(this);
                    this.notifyServer.removeNotifyListener(this);
                    
                    logger.info("Connected client size : " + this.serverThread.getClientList().size());
                    
                } catch (IOException e) {
                	logger.error(e.getMessage(), e);
                }
            }
            
            logger.info("Client is closed");  
        }  
    }  
}
