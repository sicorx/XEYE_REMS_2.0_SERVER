package com.hoonit.xeye.net.server.gw;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import com.hoonit.xeye.dao.XEyeDAO;
import com.hoonit.xeye.event.NotifyEvent;
import com.hoonit.xeye.event.NotifyListener;
import com.hoonit.xeye.net.server.gw.IFServer.ServerThread;
import com.hoonit.xeye.net.server.notify.NotifyServer;
import com.hoonit.xeye.queue.ControlQueue;
import com.hoonit.xeye.queue.DataQueue;
import com.hoonit.xeye.util.ByteUtils;
import com.hoonit.xeye.util.CRC16;
import com.hoonit.xeye.util.ResourceBundleHandler;
import com.hoonit.xeye.util.Utils;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/** 
 * Client와 통신처리 기능을 담당하는 객체
 */  
public class ClientHandlerThread extends Thread implements NotifyListener {
    
	protected Logger logger;
	
	private Object writeLock = new Object();
	
	private ServerThread serverThread;
	
    private Abortable abortable;
    
    private XEyeDAO xeyeDAO;
    
    private DataQueue dataQueue;
    
    private ControlQueue ctlQueue;
    
    private SocketChannel client;
    
    private int bufferSize = 1024;
    
    private String ip;
    
    private String macAddress;
    
    // 매장코드
    private String storeCD;
    
    // GW ID
    private String gwID;
    
    private NotifyServer notifyServer;
    
    private final byte STX              = 0x02;
	private final byte ETX              = 0x03;
	
	private final byte NORMAL           = 0x01; // 정상
	private final byte ERR_STX_ETX      = 0x02; // STX, ETX 오류
	private final byte ERR_CRC          = 0x03; // CRC 오류
	private final byte ERR_INVALID_DATA = 0x04; // 유효하지 않은 데이터 오류
	private final byte ERR_FILE_TRANS   = 0x05; // 파일전송 오류
	private final byte ERR_CTRL         = 0x06; // 제어오류
	private final byte ERR_EXCEPTION    = 0x07; // Exception 발생 오류
	private final byte ERR_STR_NOEXIST  = 0x08; // 매장정보 미존재 오류
	
	// 최근 데이터 수신일자
	private long recentRecvTime = 0L;
	// 데이터 수신일자로부터 경과된시간
	private float elapsedTime = 0F;
	
	// 간판제어전송 결과
	private byte isSignBoardResult = 0x00;
	// 냉난방제어전송 결과
	private byte isHACResult = 0x00;
	// 매장정보전송 결과
	private byte isStoreInfoResult = 0x00;
	// 게이트웨이상태 결과
	private byte isGatewayStat = 0x00;
	// 게이트웨이상태 결과 bytes
	private byte[] gatewayResult;
      
    public ClientHandlerThread(ServerThread serverThread, Abortable abortable, SocketChannel client) {
    	
    	this.serverThread = serverThread;
        this.abortable    = abortable;
        this.client       = client;
        this.bufferSize   = Integer.parseInt(ResourceBundleHandler.getInstance().getString("server.buffer.size"));
        
        try{
        	
        	String logName = client.getRemoteAddress().toString();
        	logName = logName.replaceAll("/", "").replaceAll(":", "-");
        	//logName = logName.substring(0, logName.lastIndexOf(":"));
        	
        	String logLevel       = ResourceBundleHandler.getInstance().getString("client.log.level");
        	String maxFileSize    = ResourceBundleHandler.getInstance().getString("client.log.maxfilesize");
        	String maxBackupIndex = ResourceBundleHandler.getInstance().getString("client.log.maxbackupindex");
        	
        	logger = Logger.getLogger(logName);
        	
        	Properties prop = new Properties();
        	
        	prop.setProperty("log4j.logger."+logName, logLevel+", "+logName);
    		
    		prop.setProperty("log4j.appender."+logName, "org.apache.log4j.RollingFileAppender");
    		prop.setProperty("log4j.appender."+logName+".File", "./logs/"+logName+"/log4j.log");
    		prop.setProperty("log4j.appender."+logName+".MaxFileSize", maxFileSize);
    		prop.setProperty("log4j.appender."+logName+".MaxBackupIndex", maxBackupIndex);
    		prop.setProperty("log4j.appender."+logName+".Append", "true");
    		prop.setProperty("log4j.appender."+logName+".layout", "org.apache.log4j.PatternLayout");
    		prop.setProperty("log4j.appender."+logName+".layout.ConversionPattern", "%d{yy-MM-dd HH:mm:ss,SSS} %C{1} %-5p %m%n");
    		prop.setProperty("log4j.appender."+logName+".Threshold", logLevel);

    		PropertyConfigurator.configure(prop);
        	
        }catch(Exception e){}
    }
    
    public void setNotifyServer(NotifyServer notifyServer){
    	this.notifyServer = notifyServer;
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
    
    private void doCheckStoreCD(){
    
    	try{
    		
	    	for(short i = 0; i < 5; i++){
				if("".equals(storeCD)){
					Thread.sleep(5000);
				}else{
					break;
				}
			}
    	}catch(Exception e){
    		logger.error(e.getMessage(), e);
    	}
    }
    
    /**
     * 매장정보 통보
     */
    @Override
    public boolean notifyStoreInfo(final NotifyEvent evt){
    			
		synchronized(writeLock){
			
			boolean flag = false;
			
			doCheckStoreCD();
			
			if(storeCD.equals(evt.getStoreCD()) && gwID.equals(evt.getGwID())){
				
				try{
					
					// 매장코드 설정
					//storeCD = evt.getStoreCD();
					
					isStoreInfoResult = 0x00;
					
					write(evt.getData(), false);
					
					for(short i = 0; i < 5; i++){
						
						if(isStoreInfoResult == 0x00){
							Thread.sleep(1000);
						}else{
							
							if(isStoreInfoResult == NORMAL){
								flag = true;
							}
							
							break;
						}
					}
					
				}catch(Exception e){
					logger.error(e.getMessage(), e);
				}
			}
			
			return flag;
		}
    }
    
    /**
     * 패치파일 업데이트 통보
     */
    @Override
    public void notifyFileUpdate(final NotifyEvent evt){
    	
		new Thread(){
			public void run(){
				
				synchronized(writeLock){
					
		    		doCheckStoreCD();
		    		
		    		if(!"".equals(storeCD)){
		    			
		    			try{
		    				
		    				Map<String, String> paramMap = new HashMap<String, String>();
	    					paramMap.put("STR_CD", storeCD);
	    					
	    					//Map<String, String> resultMap = xeyeDAO.selectFileUpdateGW(paramMap);
		    				
	    					//if(resultMap != null){
	    						
			    				byte[] data = evt.getData();
			    				
			    				short idx = 0;
		    					
		    					byte[] buffer = new byte[data.length - 1];
		    					
		    					// STX
		    					buffer[idx++] = data[0];
		    					// LEN
		    			        byte[] lenBytes = ByteUtils.toUnsignedShortBytes(0x15);
		    			        buffer[idx++] = lenBytes[0];
		    			        buffer[idx++] = lenBytes[1];
		    					// CMD
		    					buffer[idx++] = data[3];
		    					// 파일구분
		    					buffer[idx++] = data[5];
		    					// File Server IP
		    					for(int i = 0; i < 15; i++){
		    		        		buffer[idx++] = data[(i+6)];
		    		    		}
		    					// File Server Port
		    					buffer[idx++] = data[21];
		    					buffer[idx++] = data[22];
		    					
		    					// CRC
		    					byte[] crc = Utils.getBytes(buffer, 3, buffer.length-6);
		    					
		    					short sCRC = CRC16.getInstance().getCRC(crc);
		    					
		    					ByteBuffer wBuffer = ByteBuffer.allocate(2);
		    					wBuffer.putShort(sCRC);
		    					wBuffer.flip();
		    					
		    					byte crc1 = wBuffer.get();
		    					byte crc2 = wBuffer.get();
		    					
		    					buffer[idx++] = crc1;
		    					buffer[idx++] = crc2;
		    					buffer[idx++] = ETX;
		    					
		    					write(buffer, false);
	    					//}
		    				
		    			}catch(Exception e){
		    				logger.error(e.getMessage(), e);
		    			}
		    		}
				}
			}
		}.start();
    }
    
    /**
     * 일출일몰시간 통보
     */
    @Override
    public void notifySunRisetInfo(final NotifyEvent evt){
    	
		new Thread(){
			public void run(){
				
				synchronized(writeLock){
					
					doCheckStoreCD();
					
    				if(!"".equals(storeCD)){
    					
    					try{
    						
	    					Map<String, String> resultMap = evt.getStoreMap();
	    					
	    					byte[] data = evt.getData();
	    					
	    					short idx = 0;
	    					
	    					byte[] buffer = new byte[data.length + 4];
	    					
	    					// STX
	    					buffer[idx++] = data[0];
	    					// LEN
	    			        byte[] lenBytes = ByteUtils.toUnsignedShortBytes(0x07);
	    			        buffer[idx++] = lenBytes[0];
	    			        buffer[idx++] = lenBytes[1];
	    					// CMD
	    					buffer[idx++] = data[3];
	    					
	    					if(resultMap == null){
	    						// 일출시간
	    			        	buffer[idx++] = (byte)0xFF;
	    			        	// 일출분
	    			        	buffer[idx++] = (byte)0xFF;
	    			        	// 일몰시간
	    			        	buffer[idx++] = (byte)0xFF;
	    			        	// 일몰분
	    			        	buffer[idx++] = (byte)0xFF;
	    					}else{
	    						
	    						String temp = StringUtils.defaultIfEmpty(resultMap.get(storeCD), "");
	    						
	    						if(!"".equals(temp)){
	    							
		    						String[] sunRiset = temp.split(":");
		    						String sunRiseTime = StringUtils.defaultIfEmpty(sunRiset[0], "");
		    						String sunSetTime  = StringUtils.defaultIfEmpty(sunRiset[1], "");
		    						
			    					// 일출시분
			    			        if("".equals(sunRiseTime)){
			    			        	// 일출시간
			    			        	buffer[idx++] = (byte)0xFF;
			    			        	// 일출분
			    			        	buffer[idx++] = (byte)0xFF;
			    			        }else{
			    			        	// 일출시간
			    			        	buffer[idx++] = ByteUtils.toByte(sunRiseTime.substring(0, 2), (byte)0xFF);
			    			        	// 일출분
			    			        	buffer[idx++] = ByteUtils.toByte(sunRiseTime.substring(2, 4), (byte)0xFF);
			    			        }
			    			        
			    			        // 일몰시분
			    			        if("".equals(sunSetTime)){
			    			        	// 일몰시간
			    			        	buffer[idx++] = (byte)0xFF;
			    			        	// 일몰분
			    			        	buffer[idx++] = (byte)0xFF;
			    			        }else{
			    			        	// 일몰시간
			    			        	buffer[idx++] = ByteUtils.toByte(sunSetTime.substring(0, 2), (byte)0xFF);
			    			        	// 일몰분
			    			        	buffer[idx++] = ByteUtils.toByte(sunSetTime.substring(2, 4), (byte)0xFF);
			    			        }
			    			        
	    						}else{
	    							// 일출시간
		    			        	buffer[idx++] = (byte)0xFF;
		    			        	// 일출분
		    			        	buffer[idx++] = (byte)0xFF;
		    			        	// 일몰시간
		    			        	buffer[idx++] = (byte)0xFF;
		    			        	// 일몰분
		    			        	buffer[idx++] = (byte)0xFF;
	    						}
	    					}
	    			        
	    			        // CRC
	    					byte[] crc = Utils.getBytes(buffer, 3, buffer.length-6);
	    					
	    					short sCRC = CRC16.getInstance().getCRC(crc);
	    					
	    					ByteBuffer wBuffer = ByteBuffer.allocate(2);
	    					wBuffer.putShort(sCRC);
	    					wBuffer.flip();
	    					
	    					byte crc1 = wBuffer.get();
	    					byte crc2 = wBuffer.get();
	    					
	    					buffer[idx++] = crc1;
	    					buffer[idx++] = crc2;
	    					buffer[idx++] = ETX;
	    					
	    					write(buffer, false);
	    					
    					}catch(Exception e){
    						logger.error(e.getMessage(), e);
    					}
    				}
				}
			}
		}.start();
    }
    
    /**
     * 냉난방정책 통보
     */
    @Override
    public void notifyHACPolicyInfo(final NotifyEvent evt){
    	
		new Thread(){
			public void run(){
				
				synchronized(writeLock){
					
					doCheckStoreCD();
					
    				if(!"".equals(storeCD)){
    					
    					try{
    						
	    					write(evt.getData(), false);
	    					
    					}catch(Exception e){
    						logger.error(e.getMessage(), e);
    					}
    				}
				}
			}
		}.start();
    }
    
    /**
     * 냉난방 권장온도 통보
     */
    @Override
    public void notifyHACTempInfo(final NotifyEvent evt){
    	
		new Thread(){
			public void run(){
				
				synchronized(writeLock){
					
					doCheckStoreCD();
					
    				if(!"".equals(storeCD)){
    					
    					try{
    						
	    					write(evt.getData(), false);
	    					
    					}catch(Exception e){
    						logger.error(e.getMessage(), e);
    					}
    				}
				}
			}
		}.start();
    }
    
    /**
     * 간판제어 통보
     */
    @Override
    public boolean notifySignControl(final NotifyEvent evt){
    			
		synchronized(writeLock){
			
			boolean flag = false;
			
			doCheckStoreCD();
			
			if(storeCD.equals(evt.getStoreCD()) && gwID.equals(evt.getGwID())){
				
				try{
					
					isSignBoardResult = 0x00;
					
					write(evt.getData(), false);
					
					for(short i = 0; i < 5; i++){
						
						if(isSignBoardResult == 0x00){
							Thread.sleep(1000);
						}else{
							
							if(isSignBoardResult == NORMAL){
								flag = true;
							}
							
							break;
						}
					}
					
				}catch(Exception e){
					logger.error(e.getMessage(), e);
				}
			}
			
			return flag;
		}
    }
    
    /**
     * 냉난방제어 통보
     */
    @Override
    public boolean notifyAirconControl(final NotifyEvent evt){
    			
		synchronized(writeLock){
			
			boolean flag = false;
			
			doCheckStoreCD();
			
			if(storeCD.equals(evt.getStoreCD()) && gwID.equals(evt.getGwID())){
				
				try{
					
					isHACResult = 0x00;
					
					write(evt.getData(), false);
					
					for(short i = 0; i < 20; i++){
						
						if(isHACResult == 0x00){
							Thread.sleep(1000);
						}else{
							
							if(isHACResult == NORMAL){
								flag = true;
							}
							
							break;
						}
					}

				}catch(Exception e){
					logger.error(e.getMessage(), e);
				}
			}
			
			return flag;
		}
    }
    
    /**
     * 게이트웨이상태 통보
     */
    @Override
    public byte[] notifyGetStat(NotifyEvent evt){
    	
    	synchronized(writeLock){
			
			byte[] result = null;
			
			doCheckStoreCD();
			
			if(storeCD.equals(evt.getStoreCD()) && gwID.equals(evt.getGwID())){
				
				try{
					
					isGatewayStat = 0x00;
					gatewayResult = null;
					
					write(evt.getData(), false);
					
					for(short i = 0; i < 5; i++){
						
						if(isGatewayStat == 0x00){
							Thread.sleep(1000);
						}else{
							
							if(isGatewayStat == NORMAL){
								result = gatewayResult;
							}
							
							break;
						}
					}
					
				}catch(Exception e){
					logger.error(e.getMessage(), e);
				}
			}
			
			return result;
		}
    }
    
    private void doWrite(final byte[] b){
    	
    	synchronized(writeLock){
    		
    		new Thread(){
    			public void run(){
    				write(b, false);
    			}
    		}.start();
    	}
    }
    
    private void write(byte[] b, boolean isFileTrans){
    	
    	try{
    		
    		if(!isFileTrans){
	    		StringBuffer sb = new StringBuffer();
	    		for (int i = 0; i < b.length; i++) {
	    			sb.append(ByteUtils.toHexString((byte) b[i])).append(" ");
	    		}
	    		logger.info("Write data : " + sb.toString());
    		}
    		
	    	int len = client.write(ByteBuffer.wrap(b));
	       
	    	logger.info("Writed data length : " + len);
	        
    	}catch(IOException e){
    		logger.error(e.getMessage(), e);
    	}
    	
    }

    @Override  
    public void run() {
    	
        super.run();
          
        Selector selector = null;
        
        // 데이터 수신 시간
    	recentRecvTime = 0L;
    	// 데이터 수신 경과시간
    	elapsedTime = 0F;
    	
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
                    
                    // 데이터 수신 시간이 존재하면
                    if(recentRecvTime > 0){
                    	
                    	// 현재시간에서 최근에 받은 시간을 뺀 후 1분이 넘으면 종료한다.
                    	elapsedTime = (float)((System.currentTimeMillis() - recentRecvTime) / 1000.0);
                    	
                    	if( elapsedTime > (60 * 1) ){
                    		throw new IOException("Receive time is over 1 minute");
                    	}
                    }
                    
                    if (key.isReadable()) {
                    	
                    	buffer.clear();
                    	
                        int readBytes = client.read(buffer);
                        
                        if (readBytes < 0) {
                            done = true;
                            break;
                        } else if (readBytes == 0) {
                            continue;
                        }
                        
                        // 데이터 수신 시간
                    	recentRecvTime = System.currentTimeMillis();
                    	// 데이터 수신 경과시간
                    	elapsedTime = 0F;
                        
                        logger.info("read length : " + readBytes);
                        
                        buffer.flip();
                        
                        byte[] data = new byte[readBytes];
                        
                        short idx = 0;
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
                    		short idx = 0;
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
                			
                			byte stx = data[0];
        					//byte len = data[1];
        					int len = ByteUtils.toUnsignedShort(Utils.getBytes(data, 1, 2));
        					byte etx = data[readBytes-1];
        					
        					logger.debug("STX=" + ByteUtils.toHexString(stx));
        					logger.debug("ETX=" + ByteUtils.toHexString(etx));
        					
        					if(stx == STX && etx == ETX){
        						
        						logger.debug("LEN=" + Integer.toHexString(len & 0xFFFF));
        						
        						byte[] dataBuffer = Utils.getBytes(data, 3, len-2);
        						byte[] crcBuffer  = Utils.getBytes(data, len+1, 2);
        						
        						short dataCRC = CRC16.getInstance().getCRC(dataBuffer);
        						
        						ByteBuffer crcBuffer2 = ByteBuffer.allocate(2);
        						crcBuffer2.put(crcBuffer);
        						crcBuffer2.flip();
        						
        						short chekCRC = crcBuffer2.getShort();
        						
        						logger.debug("DATA CRC=" + dataCRC);
        						logger.debug("CHEC CRC=" + chekCRC);
        						
        						if(dataCRC == chekCRC){
        							
        							idx = 0;
        							
        							byte cmd = dataBuffer[idx++];
        							
        							logger.debug("CMD=" + ByteUtils.toHexString(cmd));
        							
        							// Echo 수신일 경우
        							if(cmd == 0x00){
        								doProcessCmd0(cmd, dataBuffer);
        							}
        							// Client 접속, MAC 주소 설정
        							else if(cmd == 0x01){
        								doProcessCmd1(cmd, dataBuffer);
        							}
        							// Data 수신일 경우
        							else if(cmd == 0x02){
        								doProcessCmd2(cmd, dataBuffer);
        							}
        							// 매장정보 전송 결과
        							else if(cmd == 0x03){
        								
        								logger.info("매장정보 전송 결과 수신");
        								
        								isStoreInfoResult = doProcessResponse(dataBuffer);
        							}
        							// 패치파일 업데이트 결과
        							else if(cmd == 0x04){
        								
        								logger.info("패치파일 업데이트 결과 수신");
        								
        								doProcessResponse(dataBuffer);
        							}
        							// 일출일몰시간 전송 결과
        							else if(cmd == 0x05){
        								
        								logger.info("일출일몰시간 전송 결과 수신");
        								
        								doProcessResponse(dataBuffer);
        							}
        							// 냉난방정책 전송 결과
        							else if(cmd == 0x06){
        								
        								logger.info("냉난방정책 전송 결과 수신");
        								
        								doProcessResponse(dataBuffer);
        							}
        							// 냉난방 권장온도 전송 결과
        							else if(cmd == 0x07){
        								
        								logger.info("냉난방 권장온도 전송 결과 수신");
        								
        								doProcessResponse(dataBuffer);
        							}
        							// 간판제어 전송 결과
        							else if(cmd == 0x08){
        								doProcessCmd8(cmd, dataBuffer);
        							}
        							// 간판제어결과 수신
        							else if(cmd == 0x09){
        								doProcessCmd8(cmd, dataBuffer);
        								doProcessCmd9(cmd, dataBuffer);
        							}
        							// 냉난방 온도제어결과 수신
        							else if(cmd == 0x0A){
        								doProcessCmd10(cmd, dataBuffer);
        							}
        							// 냉난방제어전송 결과 수신
        							else if(cmd == 0x0B){
        								doProcessCmd10(cmd, dataBuffer);
        							}
        							// 전력피크알람 수신
        							else if(cmd == 0x0C){
        								doProcessCmd12(cmd, dataBuffer);
        							}
        							// 날씨정보, 냉난방정책, 권장온도 등 수신
        							else if(cmd == 0x11){
        								doProcessCmd17(cmd, dataBuffer);
        							}
        							// 게이트웨이상태 결과 수신
        							else if(cmd == 0x12){
        								isGatewayStat = NORMAL;
        								gatewayResult = data;
        							}
        							
        						}else{
        							
        							if(data[3] != 0x03 && 
        									data[3] != 0x04 &&
        									data[3] != 0x05 &&
        									data[3] != 0x06 &&
        									data[3] != 0x07 &&
        									data[3] != 0x08 &&
        									data[3] != 0x0A &&
        									data[3] != 0x0B){
        							
	        							logger.info("CRC 오류 전송");
	        							
	        							// 결과 전송
	        							short wIdx = 0;
	        							byte[] writeBuffer = new byte[8];
	        							writeBuffer[wIdx++] = STX;
	        							
	        							byte[] lenBytes = ByteUtils.toUnsignedShortBytes(0x04);
	    								writeBuffer[wIdx++] = lenBytes[0];
	    								writeBuffer[wIdx++] = lenBytes[1];
	    								
	        							writeBuffer[wIdx++] = data[3];
	        							writeBuffer[wIdx++] = ERR_CRC;
	        							
	        							// CRC
	        							byte[] crc = Utils.getBytes(writeBuffer, 3, writeBuffer.length-6);
	        							
	        							short sCRC = CRC16.getInstance().getCRC(crc);
	        							
	        							ByteBuffer wBuffer = ByteBuffer.allocate(2);
	        							wBuffer.putShort(sCRC);
	        							wBuffer.flip();
	        							
	        							byte crc1 = wBuffer.get();
	        							byte crc2 = wBuffer.get();
	        							
	        							writeBuffer[wIdx++] = crc1;
	        							writeBuffer[wIdx++] = crc2;
	        							writeBuffer[wIdx++] = ETX;
	        							
	        							doWrite(writeBuffer);
	        							
        							}else{
        								logger.info("CRC 오류");
        							}
        						}
        					}else{
        						
        						if(data[3] != 0x03 && 
    									data[3] != 0x04 &&
    									data[3] != 0x05 &&
    									data[3] != 0x06 &&
    									data[3] != 0x07 &&
    									data[3] != 0x08 &&
    									data[3] != 0x0A &&
    									data[3] != 0x0B){
        							
	        						logger.info("STX, ETX 오류 전송");
	        						
	        						// 결과 전송
	        						short wIdx = 0;
	        						byte[] writeBuffer = new byte[8];
	        						writeBuffer[wIdx++] = STX;
	        						
	        						byte[] lenBytes = ByteUtils.toUnsignedShortBytes(0x04);
									writeBuffer[wIdx++] = lenBytes[0];
									writeBuffer[wIdx++] = lenBytes[1];
	        						
	        						writeBuffer[wIdx++] = data[3];
	        						writeBuffer[wIdx++] = ERR_STX_ETX;
	        						
	        						// CRC
	        						byte[] crc = Utils.getBytes(writeBuffer, 3, writeBuffer.length-6);
	        						
	        						short sCRC = CRC16.getInstance().getCRC(crc);
	        						
	        						ByteBuffer wBuffer = ByteBuffer.allocate(2);
	        						wBuffer.putShort(sCRC);
	        						wBuffer.flip();
	        						
	        						byte crc1 = wBuffer.get();
	        						byte crc2 = wBuffer.get();
	        						
	        						writeBuffer[wIdx++] = crc1;
	        						writeBuffer[wIdx++] = crc2;
	        						writeBuffer[wIdx++] = ETX;
	        						
	        						doWrite(writeBuffer);
	        						
        						}else{
        							logger.info("STX, ETX 오류");
        						}
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
                    this.notifyServer.removeNotifyListenerMap(storeCD);
                    
                    logger.info("Connected client size : " + this.serverThread.getClientList().size());
                    
                } catch (IOException e) {
                	logger.error(e.getMessage(), e);
                }
            }
            
            logger.info("Client is closed");  
        }  
    }
    
    /**
     * Client 응답 결과 처리
     * @param dataBuffer
     */
    private byte doProcessResponse(byte[] dataBuffer){
    	
    	short idx = 1;
    	
    	byte result = dataBuffer[idx++];
		
		logger.info("RESULT="+result);
		
		if(result == NORMAL){
			logger.info("SUCCESS");
		}else if(result == ERR_STX_ETX){
			logger.info("STX, EXT Error...");
		}else if(result == ERR_CRC){
			logger.info("CRC Error...");
		}else if(result == ERR_INVALID_DATA){
			logger.info("Invalid Data Error...");
		}else if(result == ERR_FILE_TRANS){
			logger.info("File Transfer Error...");
		}else if(result == ERR_CTRL){
			logger.info("Control Error...");
		}else if(result == ERR_EXCEPTION){
			logger.info("Exception Error...");
		}else{
			logger.info("Unknown Error...");
		}
		
		return result;
    }
    
    /**
     * Echo 수신
     * @param cmd
     * @param dataBuffer
     * @throws Exception
     */
    private void doProcessCmd0(byte cmd, byte[] dataBuffer) throws Exception {
    	
    	logger.info("Echo 수신");
    	
    	short wIdx = 0;
		byte[] writeBuffer = new byte[7];
		writeBuffer[wIdx++] = STX;
		
		byte[] lenBytes = ByteUtils.toUnsignedShortBytes(0x03);
		writeBuffer[wIdx++] = lenBytes[0];
		writeBuffer[wIdx++] = lenBytes[1];
		writeBuffer[wIdx++] = cmd;
		
		// CRC
		byte[] crc = Utils.getBytes(writeBuffer, 3, writeBuffer.length-6);
		
		short sCRC = CRC16.getInstance().getCRC(crc);
		
		ByteBuffer wBuffer = ByteBuffer.allocate(2);
		wBuffer.putShort(sCRC);
		wBuffer.flip();
		
		byte crc1 = wBuffer.get();
		byte crc2 = wBuffer.get();
		
		writeBuffer[wIdx++] = crc1;
		writeBuffer[wIdx++] = crc2;
		writeBuffer[wIdx++] = ETX;
    	
		doWrite(writeBuffer);
    }
    
    /**
     * Client 접속 정보 수신
     * @param dataBuffer
     * @throws Exception
     */
    private void doProcessCmd1(byte cmd, byte[] dataBuffer) throws Exception {
    	
    	logger.info("Client 접속 정보 수신");
    	
    	short idx = 1;
    	
		// Version
		byte versionByte = dataBuffer[idx++];
		
		BigDecimal bd1 = new BigDecimal(versionByte);
		BigDecimal bd2 = new BigDecimal("10");
		
		float version = bd1.divide(bd2).floatValue();
		logger.info("Version=" + version);
		
		byte[] ips  = Utils.getBytes(dataBuffer, 2, 8);
		byte[] macs = Utils.getBytes(dataBuffer, 10, 12);
		
		short ip1 = (short)ByteUtils.toUnsignedShort(Utils.getBytes(ips, 0, 2));
		short ip2 = (short)ByteUtils.toUnsignedShort(Utils.getBytes(ips, 2, 2));
		short ip3 = (short)ByteUtils.toUnsignedShort(Utils.getBytes(ips, 4, 2));
		short ip4 = (short)ByteUtils.toUnsignedShort(Utils.getBytes(ips, 6, 2));
		
		StringBuilder sb = new StringBuilder();
		sb.append(String.valueOf(ip1))
		.append(".")
		.append(String.valueOf(ip2))
		.append(".")
		.append(String.valueOf(ip3))
		.append(".")
		.append(String.valueOf(ip4));
		
		ip = sb.toString();
		logger.info("IP=" + ip);
		
		ByteBuffer macBuffer = ByteBuffer.wrap(macs);
		
		// MAC
		byte[] mac1 = new byte[2];
		macBuffer.get(mac1);
		byte[] mac2 = new byte[2];
		macBuffer.get(mac2);
		byte[] mac3 = new byte[2];
		macBuffer.get(mac3);
		byte[] mac4 = new byte[2];
		macBuffer.get(mac4);
		byte[] mac5 = new byte[2];
		macBuffer.get(mac5);
		byte[] mac6 = new byte[2];
		macBuffer.get(mac6);
		
		sb.setLength(0);
		sb.append(new String(mac1)).append("-")
		.append(new String(mac2)).append("-")
		.append(new String(mac3)).append("-")
		.append(new String(mac4)).append("-")
		.append(new String(mac5)).append("-")
		.append(new String(mac6));
		
		macAddress = sb.toString();
		logger.info("MAC=" + macAddress);
		
		// 매장코드
		storeCD = new String(Utils.getBytes(dataBuffer, 22, 20)).trim();
		logger.info("STR CD=" + storeCD);
		
		// GW ID
		gwID = String.valueOf(ByteUtils.toUnsignedShort(Utils.getBytes(dataBuffer, 42, 2)));
		logger.info("GW ID=" + gwID);
		
		// Validation
		byte result = NORMAL;
		
		Map<String, String> gwMstMap = null;
		List<Map<String, String>> hacPolicyList = null;
		List<Map<String, String>> hacRecommTempList = null;
		
		// 매장코드가 존재하지 않으면
		if("".equals(storeCD)){
			result = ERR_INVALID_DATA;
		}else{
			
			Map<String, String> paramMap = new HashMap<String, String>();
			paramMap.put("STR_CD", storeCD);
			paramMap.put("GW_ID", gwID);
			
			gwMstMap = xeyeDAO.selectStrInfoMst(paramMap);
			
			if(gwMstMap != null){
			
				// 맥주소또는 아이피가 다를 경우 수정
				if(!macAddress.equals(StringUtils.defaultIfEmpty(gwMstMap.get("GW_ADDR"), "")) || 
						!ip.equals(StringUtils.defaultIfEmpty(gwMstMap.get("GW_IP"), ""))){
					
					paramMap.put("STR_CD", gwMstMap.get("STR_CD"));
					paramMap.put("GW_ADDR", macAddress);
					paramMap.put("GW_IP", ip);
					paramMap.put("GW_VENDOR", "DIT");
					paramMap.put("GW_HW_MODEL", "RPI3");
					paramMap.put("GW_SW_VER", String.valueOf(version));
					paramMap.put("FINAL_MOD_ID", gwMstMap.get("STR_CD"));
					
					xeyeDAO.updateGWMst(paramMap);
				}
				
				if(!"".equals(gwMstMap.get("COMPANY_CD"))){
	    			// 냉난방정책
	    			hacPolicyList = xeyeDAO.selectHACPolicyMMList(gwMstMap);
	    			// 냉난방권장온도
	    			hacRecommTempList = xeyeDAO.selectHACRecommTempList(gwMstMap);
    			}
				
				// 통보서버에 객체 저장
				notifyServer.addNotifyListenerMap(gwMstMap.get("STR_CD"), this);
				
			}else{
				result = ERR_STR_NOEXIST;
			}
		}
		
		logger.info("Client 접속 정보 수신 결과 전송");
		
		// 결과 전송
		short wIdx = 0;
		byte[] writeBuffer = new byte[82];
		writeBuffer[wIdx++] = STX;
		
		byte[] lenBytes = ByteUtils.toUnsignedShortBytes(0x4E);
		writeBuffer[wIdx++] = lenBytes[0];
		writeBuffer[wIdx++] = lenBytes[1];
		
		writeBuffer[wIdx++] = cmd;
		writeBuffer[wIdx++] = result;
		
		int storeCDLen = 20; // 매장코드 길이
		
		// 매장정보가 존재하면
		if(gwMstMap != null){
			
			// 매장코드
	        try{
	        	
	        	storeCD = StringUtils.defaultIfEmpty(gwMstMap.get("STR_CD"), "");
	    		logger.info("STR CD="+storeCD);
	    		byte[] storeBytes = Utils.getFillNullByte(storeCD.getBytes(), storeCDLen);
	    		
	    		for(int i = 0; i < storeBytes.length; i++){
	    			writeBuffer[wIdx++] = storeBytes[i];
	    		}
	        }catch(ArrayIndexOutOfBoundsException e){
	        	logger.error(e.getMessage(), e);
	        	
	        	for(int i = 0; i < storeCDLen; i++){
	    			writeBuffer[wIdx++] = 0x20;
	    		}
	        }
	        // GW ID
	        gwID = String.valueOf(gwMstMap.get("GW_ID"));
	        logger.info("GW ID="+gwID);
	        byte[] gwIDs = ByteUtils.toUnsignedShortBytes(Integer.parseInt(gwID));
	        writeBuffer[wIdx++] = gwIDs[0];
	        writeBuffer[wIdx++] = gwIDs[1];
	        // 일출시분
	        if(gwMstMap.get("SUNRISE_TIME") == null){
	        	// 일출시간
	        	writeBuffer[wIdx++] = (byte)0xFF;
	        	// 일출분
	        	writeBuffer[wIdx++] = (byte)0xFF;
	        }else{
	        	
	        	StringTokenizer st = new StringTokenizer(gwMstMap.get("SUNRISE_TIME"), ":");
	        	
	        	// 일출시간
	        	writeBuffer[wIdx++] = ByteUtils.toByte(st.nextToken(), (byte)0xFF);
	        	// 일출분
	        	writeBuffer[wIdx++] = ByteUtils.toByte(st.nextToken(), (byte)0xFF);
	        }
	        // 일몰시분
	        if(gwMstMap.get("SUNSET_TIME") == null){
	        	// 일몰시간
	        	writeBuffer[wIdx++] = (byte)0xFF;
	        	// 일몰분
	        	writeBuffer[wIdx++] = (byte)0xFF;
	        }else{
	        	
	        	StringTokenizer st = new StringTokenizer(gwMstMap.get("SUNSET_TIME"), ":");
	        	
	        	// 일몰시간
	        	writeBuffer[wIdx++] = ByteUtils.toByte(st.nextToken(), (byte)0xFF);
	        	// 일몰분
	        	writeBuffer[wIdx++] = ByteUtils.toByte(st.nextToken(), (byte)0xFF);
	        }
	        //날씨코드
	        writeBuffer[wIdx++] = ByteUtils.toByte(String.valueOf(gwMstMap.get("WEATHER_CD")), (byte)0x00);
	        // 외기온도
	        writeBuffer[wIdx++] = ByteUtils.toByte(String.valueOf(gwMstMap.get("FORECAST_TEMP")), (byte)0xFF);
	        // 계약전력
	        byte[] cPower = ByteUtils.toUnsignedIntBytes(new BigDecimal((StringUtils.defaultIfEmpty(String.valueOf(gwMstMap.get("CONT_DEMAND_POWER")), "0"))).longValue());
	        writeBuffer[wIdx++] = cPower[0];
	        writeBuffer[wIdx++] = cPower[1];
	        writeBuffer[wIdx++] = cPower[2];
	        writeBuffer[wIdx++] = cPower[3];
	        // 냉난방기 제조사
	        writeBuffer[wIdx++] = (byte)0x00; // LG
	        // 냉난방정책
	        if(hacPolicyList != null && hacPolicyList.size() > 0){
		        for(Map<String, String> hacPolicyMap : hacPolicyList){
		        	
		        	String policy = "";
		        	
		        	// 난방
		        	if("H".equals(hacPolicyMap.get("MODE"))){
		        		policy = "1";
		        	}
		        	// 냉방
		        	else if("C".equals(hacPolicyMap.get("MODE"))){
		        		policy = "2";
		        	}
		        	// 환절기
		        	else{
		        		policy = "3";
		        	}
		        	
		        	writeBuffer[wIdx++] = ByteUtils.toByte(policy, (byte)0xFF);
		        }
	        }else{
	        	for(int i = 0; i < 12; i++){
	        		writeBuffer[wIdx++] = (byte)0xFF;
		        }
	        }
	        // 냉난방권장온도
	        if(hacRecommTempList != null && hacRecommTempList.size() > 0){
		        for(Map<String, String> hacRecommTempMap : hacRecommTempList){
		        	writeBuffer[wIdx++] = ByteUtils.toByte(String.valueOf(new BigDecimal(String.valueOf(hacRecommTempMap.get("RECOMM_TEMP"))).shortValue()), (byte)0xFF);
		        }
	        }else{
	        	for(int i = 0; i < 12; i++){
	        		writeBuffer[wIdx++] = (byte)0xFF;
		        }
	        }
		}
		// 매장정보가 존재하지 않으면
		else{
			
			// 매장코드
	        try{
	        	
	    		String strCD = StringUtils.defaultIfEmpty(storeCD, "");
	    		byte[] storeBytes = Utils.getFillNullByte(strCD.getBytes(), storeCDLen);
	    		
	    		for(int i = 0; i < storeBytes.length; i++){
	    			writeBuffer[wIdx++] = storeBytes[i];
	    		}
	        }catch(ArrayIndexOutOfBoundsException e){
	        	logger.error(e.getMessage(), e);
	        	
	        	for(int i = 0; i < storeCDLen; i++){
	    			writeBuffer[wIdx++] = 0x20;
	    		}
	        }
			// GW ID
	        byte[] gwIDs = ByteUtils.toUnsignedShortBytes(0);
	        writeBuffer[wIdx++] = gwIDs[0];
	        writeBuffer[wIdx++] = gwIDs[1];
	        // 일출시간
	        writeBuffer[wIdx++] = (byte)0xFF;
	        // 일출분
	        writeBuffer[wIdx++] = (byte)0xFF;
	        // 일몰시간
	        writeBuffer[wIdx++] = (byte)0xFF;
	        // 일몰분
	        writeBuffer[wIdx++] = (byte)0xFF;
	        // 날씨코드
	        writeBuffer[wIdx++] = (byte)0x00;
	        // 외기온도
	        writeBuffer[wIdx++] = (byte)0xFF;
	        // 계약전력
	        byte[] cPower = ByteUtils.toUnsignedIntBytes(0);
	        writeBuffer[wIdx++] = cPower[0];
	        writeBuffer[wIdx++] = cPower[1];
	        writeBuffer[wIdx++] = cPower[2];
	        writeBuffer[wIdx++] = cPower[3];
	        // 냉난방기 제조사
	        writeBuffer[wIdx++] = (byte)0xFF;
	        // 냉난방정책
	        for(int i = 0; i < 12; i++){
        		writeBuffer[wIdx++] = (byte)0xFF;
	        }
	        // 냉난방권장온도
	        for(int i = 0; i < 12; i++){
        		writeBuffer[wIdx++] = (byte)0xFF;
	        }
		}
		
		// IF Server IP
        try{
        	
    		String ifServerIP = "";
    		byte[] ipBytes = Utils.getFillNullByte(ifServerIP.getBytes(), 15);
    		
    		for(int i = 0; i < ipBytes.length; i++){
    			writeBuffer[wIdx++] = ipBytes[i];
    		}
        }catch(ArrayIndexOutOfBoundsException e){
        	logger.error(e.getMessage(), e);
        	
        	for(int i = 0; i < 15; i++){
    			writeBuffer[wIdx++] = 0x20;
    		}
        }
        // IF Server Port
        byte[] serverPort = ByteUtils.toUnsignedShortBytes(0);
        writeBuffer[wIdx++] = serverPort[0];
        writeBuffer[wIdx++] = serverPort[1];
		
		// CRC
		byte[] crc = Utils.getBytes(writeBuffer, 3, writeBuffer.length-6);
		
		short sCRC = CRC16.getInstance().getCRC(crc);
		
		ByteBuffer wBuffer = ByteBuffer.allocate(2);
		wBuffer.putShort(sCRC);
		wBuffer.flip();
		
		byte crc1 = wBuffer.get();
		byte crc2 = wBuffer.get();
		
		writeBuffer[wIdx++] = crc1;
		writeBuffer[wIdx++] = crc2;
		writeBuffer[wIdx++] = ETX;
    	
		doWrite(writeBuffer);
    }
    
    /**
     * Data 수신
     * @param dataBuffer
     * @throws Exception
     */
    private void doProcessCmd2(byte cmd, byte[] dataBuffer) throws Exception {
    	
    	logger.info("Data 수신");
    	
    	try{
    		
    		logger.info("통신상태="+dataBuffer[30]);
    		
    		// 통신상태가 정상이면
    		if(dataBuffer[30] == 0x00){
			
		    	short idx = 1;
		    	
		    	// 매장코드
				String dStoreCD = new String(Utils.getBytes(dataBuffer, idx, 20)).trim();
				logger.info("STR CD=" + dStoreCD);
				idx += 20;
				// GW ID
				String dGwID = String.valueOf(ByteUtils.toUnsignedShort(Utils.getBytes(dataBuffer, idx, 2)));
				logger.info("GW ID=" + dGwID);
				idx += 2;
				
				if(storeCD.equals(dStoreCD) && dGwID.equals(gwID)){
					
					JSONObject jsonObj = new JSONObject();
					jsonObj.put("STR_CD", storeCD);
					jsonObj.put("REGI_ID", storeCD);
					
					// 년
					String year = String.valueOf(ByteUtils.toUnsignedShort(Utils.getBytes(dataBuffer, idx, 2)));
					idx += 2;
					// 월
					byte month = dataBuffer[idx++];
					// 일
					byte date = dataBuffer[idx++];
					// 시
					byte hour = dataBuffer[idx++];
					// 분
					byte minute = dataBuffer[idx++];
					// 초
					byte second = dataBuffer[idx++];
					
					StringBuilder sb = new StringBuilder();
					sb.append(year);
					
					if(month < 10)
						sb.append("0");
					
					sb.append(month);
					
					if(date < 10)
						sb.append("0");
					
					sb.append(date);
					
					jsonObj.put("YYYYMMDD", sb.toString());
					
					sb.setLength(0);
					
					if(hour < 10)
						sb.append("0");
					
					sb.append(hour);
					
					if(minute < 10)
						sb.append("0");
					
					sb.append(minute);
					
					jsonObj.put("HHMIN", sb.toString());
					
					// 통신상태
					//byte commStatus = dataBuffer[idx++];
					idx++;
					
					//=============PMC=============
					JSONObject pmcObj = new JSONObject();
					
					// 1CH
					pmcObj.put("EP_1CH_EFT_WATAGE", String.valueOf(ByteUtils.toLong(Utils.getBytes(dataBuffer, idx, 8))));
					idx += 8;
					pmcObj.put("EP_1CH_5MIN_WATAGE", String.valueOf(ByteUtils.toUnsignedInt(Utils.getBytes(dataBuffer, idx, 4))));
					idx += 4;
					pmcObj.put("EP_1CH_ACT_WATAGE", String.valueOf(ByteUtils.toUnsignedInt(Utils.getBytes(dataBuffer, idx, 4))));
					idx += 4;
					pmcObj.put("EP_1CH_STATUS", String.valueOf(dataBuffer[idx++]));
					// 2CH
					pmcObj.put("EP_2CH_EFT_WATAGE", String.valueOf(ByteUtils.toLong(Utils.getBytes(dataBuffer, idx, 8))));
					idx += 8;
					pmcObj.put("EP_2CH_5MIN_WATAGE", String.valueOf(ByteUtils.toUnsignedInt(Utils.getBytes(dataBuffer, idx, 4))));
					idx += 4;
					pmcObj.put("EP_2CH_ACT_WATAGE", String.valueOf(ByteUtils.toUnsignedInt(Utils.getBytes(dataBuffer, idx, 4))));
					idx += 4;
					pmcObj.put("EP_2CH_STATUS", String.valueOf(dataBuffer[idx++]));
					// 3CH
					pmcObj.put("EP_3CH_EFT_WATAGE", String.valueOf(ByteUtils.toLong(Utils.getBytes(dataBuffer, idx, 8))));
					idx += 8;
					pmcObj.put("EP_3CH_5MIN_WATAGE", String.valueOf(ByteUtils.toUnsignedInt(Utils.getBytes(dataBuffer, idx, 4))));
					idx += 4;
					pmcObj.put("EP_3CH_ACT_WATAGE", String.valueOf(ByteUtils.toUnsignedInt(Utils.getBytes(dataBuffer, idx, 4))));
					idx += 4;
					pmcObj.put("EP_3CH_STATUS", String.valueOf(dataBuffer[idx++]));
					// 4CH
					pmcObj.put("EP_4CH_EFT_WATAGE", String.valueOf(ByteUtils.toLong(Utils.getBytes(dataBuffer, idx, 8))));
					idx += 8;
					pmcObj.put("EP_4CH_5MIN_WATAGE", String.valueOf(ByteUtils.toUnsignedInt(Utils.getBytes(dataBuffer, idx, 4))));
					idx += 4;
					pmcObj.put("EP_4CH_ACT_WATAGE", String.valueOf(ByteUtils.toUnsignedInt(Utils.getBytes(dataBuffer, idx, 4))));
					idx += 4;
					pmcObj.put("EP_4CH_STATUS", String.valueOf(dataBuffer[idx++]));
					// 5CH
					pmcObj.put("EP_5CH_EFT_WATAGE", String.valueOf(ByteUtils.toLong(Utils.getBytes(dataBuffer, idx, 8))));
					idx += 8;
					pmcObj.put("EP_5CH_5MIN_WATAGE", String.valueOf(ByteUtils.toUnsignedInt(Utils.getBytes(dataBuffer, idx, 4))));
					idx += 4;
					pmcObj.put("EP_5CH_ACT_WATAGE", String.valueOf(ByteUtils.toUnsignedInt(Utils.getBytes(dataBuffer, idx, 4))));
					idx += 4;
					pmcObj.put("EP_5CH_STATUS", String.valueOf(dataBuffer[idx++]));
					// 6CH
					pmcObj.put("EP_6CH_EFT_WATAGE", String.valueOf(ByteUtils.toLong(Utils.getBytes(dataBuffer, idx, 8))));
					idx += 8;
					pmcObj.put("EP_6CH_5MIN_WATAGE", String.valueOf(ByteUtils.toUnsignedInt(Utils.getBytes(dataBuffer, idx, 4))));
					idx += 4;
					pmcObj.put("EP_6CH_ACT_WATAGE", String.valueOf(ByteUtils.toUnsignedInt(Utils.getBytes(dataBuffer, idx, 4))));
					idx += 4;
					pmcObj.put("EP_6CH_STATUS", String.valueOf(dataBuffer[idx++]));
					// 7CH R
					pmcObj.put("EP_7CH_R_EFT_WATAGE", String.valueOf(ByteUtils.toLong(Utils.getBytes(dataBuffer, idx, 8))));
					idx += 8;
					pmcObj.put("EP_7CH_R_5MIN_WATAGE", String.valueOf(ByteUtils.toUnsignedInt(Utils.getBytes(dataBuffer, idx, 4))));
					idx += 4;
					pmcObj.put("EP_7CH_R_ACT_WATAGE", String.valueOf(ByteUtils.toUnsignedInt(Utils.getBytes(dataBuffer, idx, 4))));
					idx += 4;
					pmcObj.put("EP_7CH_R_STATUS", String.valueOf(dataBuffer[idx++]));
					// 7CH S
					pmcObj.put("EP_7CH_S_EFT_WATAGE", String.valueOf(ByteUtils.toLong(Utils.getBytes(dataBuffer, idx, 8))));
					idx += 8;
					pmcObj.put("EP_7CH_S_5MIN_WATAGE", String.valueOf(ByteUtils.toUnsignedInt(Utils.getBytes(dataBuffer, idx, 4))));
					idx += 4;
					pmcObj.put("EP_7CH_S_ACT_WATAGE", String.valueOf(ByteUtils.toUnsignedInt(Utils.getBytes(dataBuffer, idx, 4))));
					idx += 4;
					pmcObj.put("EP_7CH_S_STATUS", String.valueOf(dataBuffer[idx++]));
					// 7CH T
					pmcObj.put("EP_7CH_T_EFT_WATAGE", String.valueOf(ByteUtils.toLong(Utils.getBytes(dataBuffer, idx, 8))));
					idx += 8;
					pmcObj.put("EP_7CH_T_5MIN_WATAGE", String.valueOf(ByteUtils.toUnsignedInt(Utils.getBytes(dataBuffer, idx, 4))));
					idx += 4;
					pmcObj.put("EP_7CH_T_ACT_WATAGE", String.valueOf(ByteUtils.toUnsignedInt(Utils.getBytes(dataBuffer, idx, 4))));
					idx += 4;
					pmcObj.put("EP_7CH_T_STATUS", String.valueOf(dataBuffer[idx++]));
					// 8CH R
					pmcObj.put("EP_8CH_R_EFT_WATAGE", String.valueOf(ByteUtils.toLong(Utils.getBytes(dataBuffer, idx, 8))));
					idx += 8;
					pmcObj.put("EP_8CH_R_5MIN_WATAGE", String.valueOf(ByteUtils.toUnsignedInt(Utils.getBytes(dataBuffer, idx, 4))));
					idx += 4;
					pmcObj.put("EP_8CH_R_ACT_WATAGE", String.valueOf(ByteUtils.toUnsignedInt(Utils.getBytes(dataBuffer, idx, 4))));
					idx += 4;
					pmcObj.put("EP_8CH_R_STATUS", String.valueOf(dataBuffer[idx++]));
					// 8CH S
					pmcObj.put("EP_8CH_S_EFT_WATAGE", String.valueOf(ByteUtils.toLong(Utils.getBytes(dataBuffer, idx, 8))));
					idx += 8;
					pmcObj.put("EP_8CH_S_5MIN_WATAGE", String.valueOf(ByteUtils.toUnsignedInt(Utils.getBytes(dataBuffer, idx, 4))));
					idx += 4;
					pmcObj.put("EP_8CH_S_ACT_WATAGE", String.valueOf(ByteUtils.toUnsignedInt(Utils.getBytes(dataBuffer, idx, 4))));
					idx += 4;
					pmcObj.put("EP_8CH_S_STATUS", String.valueOf(dataBuffer[idx++]));
					// 8CH T
					pmcObj.put("EP_8CH_T_EFT_WATAGE", String.valueOf(ByteUtils.toLong(Utils.getBytes(dataBuffer, idx, 8))));
					idx += 8;
					pmcObj.put("EP_8CH_T_5MIN_WATAGE", String.valueOf(ByteUtils.toUnsignedInt(Utils.getBytes(dataBuffer, idx, 4))));
					idx += 4;
					pmcObj.put("EP_8CH_T_ACT_WATAGE", String.valueOf(ByteUtils.toUnsignedInt(Utils.getBytes(dataBuffer, idx, 4))));
					idx += 4;
					pmcObj.put("EP_8CH_T_STATUS", String.valueOf(dataBuffer[idx++]));
					
					jsonObj.put("PMC", pmcObj);
					
					//=============테몬=============
					JSONArray temonArr = new JSONArray();
					
					for(short i = 0; i < 16; i++){
						
						JSONObject temonObj = new JSONObject();
						
						temonObj.put("PORT_NO", String.valueOf(i+1));
						temonObj.put("SENS_TEMP", String.valueOf(ByteUtils.toShort(Utils.getBytes(dataBuffer, idx, 2))));
						idx += 2;
						
						temonArr.add(temonObj);
					}
					
					jsonObj.put("TEMON", temonArr);
					
					//=============간판상태=============
					byte s1 = dataBuffer[idx++];
					byte s2 = dataBuffer[idx++];
					
					if(s1 == 0x00 && s2 == 0x00){
						jsonObj.put("SIGN_STAT_CD", "0");
					}else{
						jsonObj.put("SIGN_STAT_CD", "1");
					}
					
					//=============알몬=============
					JSONArray almonArr = new JSONArray();
					
					for(short i = 0; i < 4; i++){
						
						JSONObject almonObj = new JSONObject();
						
						almonObj.put("PORT_NO", String.valueOf(i+1));
						almonObj.put("SENS_TEMP", String.valueOf(dataBuffer[idx++]));
						
						almonArr.add(almonObj);
					}
					
					jsonObj.put("ALMON", almonArr);
					
					//=============하콘=============
					JSONArray haconArr = new JSONArray();
					
					for(short i = 0; i < 5; i++){
						
						JSONObject haconObj = new JSONObject();
						
						haconObj.put("HACON_STAT_CD", String.valueOf(dataBuffer[idx++]));
						haconObj.put("SENSE_TEMP", String.valueOf(ByteUtils.toShort(Utils.getBytes(dataBuffer, idx, 2))));
						idx += 2;
						haconObj.put("SENSE_HUMID", "0"); // 습도값은 존재하지 않음.
						
						haconArr.add(haconObj);
					}
					
					jsonObj.put("HACON", haconArr);
					
					//=============유선티센서=============
					JSONArray tSensorArr = new JSONArray();
					
					for(short i = 0; i < 5; i++){
						
						JSONObject tSensorObj = new JSONObject();
						
						tSensorObj.put("T_SENSOR_STAT_CD", String.valueOf(dataBuffer[idx++]));
						tSensorObj.put("T_SENSOR_SENSE_TEMP", String.valueOf(ByteUtils.toShort(Utils.getBytes(dataBuffer, idx, 2))));
						idx += 2;
						tSensorObj.put("T_SENSOR_SENSE_HUMID", String.valueOf(ByteUtils.toShort(Utils.getBytes(dataBuffer, idx, 2))));
						idx += 2;
						
						tSensorArr.add(tSensorObj);
					}
					
					jsonObj.put("TSENSOR", tSensorArr);
					
					//=============무선티센서=============
					jsonObj.put("BLE_CNT", String.valueOf(dataBuffer[idx++])); // 개수
					
					JSONArray bleArr = new JSONArray();
					
					for(short i = 0; i < 10; i++){
						
						JSONObject bleObj = new JSONObject();
						
						bleObj.put("BLE_GUBUN", String.valueOf(dataBuffer[idx++]));
						bleObj.put("BLE_SENSE_TEMP", String.valueOf(ByteUtils.toShort(Utils.getBytes(dataBuffer, idx, 2)))); // 온도
						idx += 2;
						bleObj.put("BLE_SENSE_HUMID", String.valueOf(ByteUtils.toShort(Utils.getBytes(dataBuffer, idx, 2)))); // 습도
						idx += 2;
						bleObj.put("BLE_BATTERY", String.valueOf(dataBuffer[idx++]));
						
						bleArr.add(bleObj);
					}
					
					jsonObj.put("BLE", bleArr);
					
					//=============인번터허브=============
					jsonObj.put("HUB_CNT", String.valueOf(dataBuffer[idx++])); // 개수
					
					JSONArray hubArr = new JSONArray();
					
					for(int i = 0; i < 8; i++){
						
						JSONObject hubObj = new JSONObject();
						
						hubObj.put("ADDR", String.valueOf(dataBuffer[idx++])); // Address
						hubObj.put("FW_VER", String.valueOf(dataBuffer[idx++])); // F/W 버전
						hubObj.put("TYPE", String.valueOf(dataBuffer[idx++])); // 타입
						hubObj.put("MODEL", String.valueOf(dataBuffer[idx++])); // 모델
						hubObj.put("CAPACITY", String.valueOf(dataBuffer[idx++])); // 정격용량
						hubObj.put("ALARM_MASK1", String.valueOf(dataBuffer[idx++])); // 알람 MASK1 - 실내고온알람
						hubObj.put("ALARM_MASK2", String.valueOf(dataBuffer[idx++])); // 알람 MASK1 - 실내저온알람
						hubObj.put("ALARM_MASK3", String.valueOf(dataBuffer[idx++])); // 알람 MASK1 - 실내온도센서
						hubObj.put("ALARM_MASK4", String.valueOf(dataBuffer[idx++])); // 알람 MASK1 - 실내제상온도센서
						hubObj.put("ALARM_MASK5", String.valueOf(dataBuffer[idx++])); // 알람 MASK1 - 고압알람
						hubObj.put("ALARM_MASK6", String.valueOf(dataBuffer[idx++])); // 알람 MASK1 - 저압알람
						hubObj.put("ALARM_MASK7", String.valueOf(dataBuffer[idx++])); // 알람 MASK1 - 압축기알람
						hubObj.put("ALARM_MASK8", String.valueOf(dataBuffer[idx++])); // 알람 MASK1 - RESERVED
						hubObj.put("ALARM_SP1", String.valueOf(dataBuffer[idx++])); // 알람1 - 실내고온알람
						hubObj.put("ALARM_SP2", String.valueOf(dataBuffer[idx++])); // 알람1 - 실내저온알람
						hubObj.put("ALARM_SP3", String.valueOf(dataBuffer[idx++])); // 알람1 - 실내온도센서
						hubObj.put("ALARM_SP4", String.valueOf(dataBuffer[idx++])); // 알람1 - 실내제상온도센서
						hubObj.put("ALARM_SP5", String.valueOf(dataBuffer[idx++])); // 알람1 - 고압알람
						hubObj.put("ALARM_SP6", String.valueOf(dataBuffer[idx++])); // 알람1 - 저압알람
						hubObj.put("ALARM_SP7", String.valueOf(dataBuffer[idx++])); // 알람1 - 압축기알람
						hubObj.put("ALARM_SP8", String.valueOf(dataBuffer[idx++])); // 알람1 - RESERVED
						hubObj.put("ERR_CD", String.valueOf(dataBuffer[idx++])); // 에러코드
						hubObj.put("OPER_MODE", String.valueOf(dataBuffer[idx++])); // 운전모드
						hubObj.put("SENS_TEMP", String.valueOf(ByteUtils.toShort(Utils.getBytes(dataBuffer, idx, 2)))); // 실내온도
						idx += 2;
						hubObj.put("SENS_TEMP_CONF", String.valueOf(ByteUtils.toShort(Utils.getBytes(dataBuffer, idx, 2)))); // 실내설정온도
						idx += 2;
						hubObj.put("SENS_TEMP_OUT", String.valueOf(ByteUtils.toShort(Utils.getBytes(dataBuffer, idx, 2)))); // 실외기온도
						idx += 2;
						hubObj.put("MAX_TEMP_ALARM_YN", String.valueOf(dataBuffer[idx++])); // 고온경보사용유무
						hubObj.put("MAX_TEMP", String.valueOf(dataBuffer[idx++])); // 고온경보설정온도
						hubObj.put("MIN_TEMP_ALARM_YN", String.valueOf(dataBuffer[idx++])); // 저온경보사용유무
						hubObj.put("MIN_TEMP", String.valueOf(dataBuffer[idx++])); // 저온경보설정온도
						hubObj.put("DEFROST_SENS_TEMP", String.valueOf(ByteUtils.toShort(Utils.getBytes(dataBuffer, idx, 2)))); // 실내제상온도
						idx += 2;
						hubObj.put("DEFW_OWNER", String.valueOf(dataBuffer[idx++])); // 제상/제수 동작기준
						hubObj.put("DEFROST_TEMP_CONF", String.valueOf(dataBuffer[idx++])); // 제상복귀온도설정
						hubObj.put("DEFROST_TERM", String.valueOf(dataBuffer[idx++])); // 제상간격
						hubObj.put("DEFROST_DELAY", String.valueOf(dataBuffer[idx++])); // 제상시간
						hubObj.put("DEWATER_DELAY", String.valueOf(dataBuffer[idx++])); // 제수시간
						
						hubArr.add(hubObj);
					}
					
					jsonObj.put("HUB", hubArr);
					
					logger.debug(jsonObj.toString());
					
					dataQueue.doAddData(jsonObj);
					
					// 결과 전송
					short wIdx = 0;
					byte[] writeBuffer = new byte[8];
					writeBuffer[wIdx++] = STX;
					
					byte[] lenBytes = ByteUtils.toUnsignedShortBytes(0x04);
					writeBuffer[wIdx++] = lenBytes[0];
					writeBuffer[wIdx++] = lenBytes[1];
					
					writeBuffer[wIdx++] = cmd;
					writeBuffer[wIdx++] = NORMAL;
					
					// CRC
					byte[] crc = Utils.getBytes(writeBuffer, 3, writeBuffer.length-6);
					
					short sCRC = CRC16.getInstance().getCRC(crc);
					
					ByteBuffer wBuffer = ByteBuffer.allocate(2);
					wBuffer.putShort(sCRC);
					wBuffer.flip();
					
					byte crc1 = wBuffer.get();
					byte crc2 = wBuffer.get();
					
					writeBuffer[wIdx++] = crc1;
					writeBuffer[wIdx++] = crc2;
					writeBuffer[wIdx++] = ETX;
					
					doWrite(writeBuffer);
					
				}else{
					
					logger.info("Invalid Data 오류 전송");
					
					// 결과 전송
					short wIdx = 0;
					byte[] writeBuffer = new byte[8];
					writeBuffer[wIdx++] = STX;
					
					byte[] lenBytes = ByteUtils.toUnsignedShortBytes(0x04);
					writeBuffer[wIdx++] = lenBytes[0];
					writeBuffer[wIdx++] = lenBytes[1];
					
					writeBuffer[wIdx++] = cmd;
					writeBuffer[wIdx++] = ERR_INVALID_DATA;
					
					// CRC
					byte[] crc = Utils.getBytes(writeBuffer, 3, writeBuffer.length-6);
					
					short sCRC = CRC16.getInstance().getCRC(crc);
					
					ByteBuffer wBuffer = ByteBuffer.allocate(2);
					wBuffer.putShort(sCRC);
					wBuffer.flip();
					
					byte crc1 = wBuffer.get();
					byte crc2 = wBuffer.get();
					
					writeBuffer[wIdx++] = crc1;
					writeBuffer[wIdx++] = crc2;
					writeBuffer[wIdx++] = ETX;
					
					doWrite(writeBuffer);
				}
    		}else{
    			
    			// 결과 전송
				short wIdx = 0;
				byte[] writeBuffer = new byte[8];
				writeBuffer[wIdx++] = STX;
				
				byte[] lenBytes = ByteUtils.toUnsignedShortBytes(0x04);
				writeBuffer[wIdx++] = lenBytes[0];
				writeBuffer[wIdx++] = lenBytes[1];
				
				writeBuffer[wIdx++] = cmd;
				writeBuffer[wIdx++] = NORMAL;
				
				// CRC
				byte[] crc = Utils.getBytes(writeBuffer, 3, writeBuffer.length-6);
				
				short sCRC = CRC16.getInstance().getCRC(crc);
				
				ByteBuffer wBuffer = ByteBuffer.allocate(2);
				wBuffer.putShort(sCRC);
				wBuffer.flip();
				
				byte crc1 = wBuffer.get();
				byte crc2 = wBuffer.get();
				
				writeBuffer[wIdx++] = crc1;
				writeBuffer[wIdx++] = crc2;
				writeBuffer[wIdx++] = ETX;
				
				doWrite(writeBuffer);
    		}
    	}catch(Exception e){
    		
    		logger.info("Excepton 오류 전송");
			
			// 결과 전송
			short wIdx = 0;
			byte[] writeBuffer = new byte[8];
			writeBuffer[wIdx++] = STX;
			
			byte[] lenBytes = ByteUtils.toUnsignedShortBytes(0x04);
			writeBuffer[wIdx++] = lenBytes[0];
			writeBuffer[wIdx++] = lenBytes[1];
			
			writeBuffer[wIdx++] = cmd;
			writeBuffer[wIdx++] = ERR_EXCEPTION;
			
			// CRC
			byte[] crc = Utils.getBytes(writeBuffer, 3, writeBuffer.length-6);
			
			short sCRC = CRC16.getInstance().getCRC(crc);
			
			ByteBuffer wBuffer = ByteBuffer.allocate(2);
			wBuffer.putShort(sCRC);
			wBuffer.flip();
			
			byte crc1 = wBuffer.get();
			byte crc2 = wBuffer.get();
			
			writeBuffer[wIdx++] = crc1;
			writeBuffer[wIdx++] = crc2;
			writeBuffer[wIdx++] = ETX;
			
			doWrite(writeBuffer);
    	}
    }
    
    /**
     * 간판제어 전송 결과
     * @param dataBuffer
     * @throws Exception
     */
    private void doProcessCmd8(byte cmd, byte[] dataBuffer) throws Exception {
    	
    	logger.info("간판제어전송 결과 수신");
    	
    	short idx = 1;
    	
    	byte result = dataBuffer[idx++];
		
		logger.info("RESULT="+result);
		
		isSignBoardResult = result;
		
		if(result == NORMAL){
			
			logger.info("SUCCESS");
			
			// 매장코드
			String dStoreCD = new String(Utils.getBytes(dataBuffer, idx, 20)).trim();
			logger.info("STR CD=" + dStoreCD);
			idx += 20;
			// GW ID
			String dGwID = String.valueOf(ByteUtils.toUnsignedShort(Utils.getBytes(dataBuffer, idx, 2)));
			logger.info("GW ID=" + dGwID);
			idx += 2;
			
			// 년
			String year = String.valueOf(ByteUtils.toUnsignedShort(Utils.getBytes(dataBuffer, idx, 2)));
			idx += 2;
			// 월
			byte month     = dataBuffer[idx++];
			// 일
			byte date      = dataBuffer[idx++];
			// 시
			byte hour      = dataBuffer[idx++];
			// 분
			byte minute    = dataBuffer[idx++];
			// 초
			byte second    = dataBuffer[idx++];
			byte statusOld = dataBuffer[idx++]; // 기존 간판상태
			byte status    = dataBuffer[idx++]; // 현재 간판상태
			byte ctlAgt    = dataBuffer[idx++]; // 제어주체
			
			logger.info("년="+year);
			logger.info("월="+month);
			logger.info("일="+date);
			logger.info("시="+hour);
			logger.info("분="+minute);
			logger.info("초="+second);
			logger.info("기존상태="+statusOld);
			logger.info("현재상태="+status);
			logger.info("제어주체="+ctlAgt);
			
			
			Map<String, String> paramMap = new HashMap<String, String>();
			paramMap.put("CTL_TYPE", "SIGN");
			paramMap.put("STR_CD", storeCD);
			paramMap.put("REGI_ID", storeCD);
			
			StringBuilder sb = new StringBuilder();
			sb.append(year);
			
			if(month < 10)
				sb.append("0");
			
			sb.append(month);
			
			if(date < 10)
				sb.append("0");
			
			sb.append(date);
			
			paramMap.put("YYYYMMDD", sb.toString());
			
			sb.setLength(0);
			
			if(hour < 10)
				sb.append("0");
			
			sb.append(hour);
			
			if(minute < 10)
				sb.append("0");
			
			sb.append(minute);
			
			paramMap.put("HHMIN", sb.toString());
			
			sb.setLength(0);
			
			if(second < 10)
				sb.append("0");
			
			sb.append(second);
			
			paramMap.put("SSMS", sb.toString());
			paramMap.put("SIGN_STAT", String.valueOf(status));
			paramMap.put("SIGN_STAT_OLD", String.valueOf(statusOld));
			paramMap.put("CONTROL_AGT", String.valueOf(ctlAgt));
			
			ctlQueue.doAddData(paramMap);
			
		}else if(result == ERR_STX_ETX){
			logger.info("STX, EXT Error...");
		}else if(result == ERR_CRC){
			logger.info("CRC Error...");
		}else if(result == ERR_INVALID_DATA){
			logger.info("Invalid Data Error...");
		}else if(result == ERR_FILE_TRANS){
			logger.info("File Transfer Error...");
		}else if(result == ERR_CTRL){
			logger.info("Control Error...");
		}else if(result == ERR_EXCEPTION){
			logger.info("Exception Error...");
		}else{
			logger.info("Unknown Error...");
		}
    }
    
    /**
     * 간판제어결과 수신 응답
     * @param dataBuffer
     * @throws Exception
     */
    private void doProcessCmd9(byte cmd, byte[] dataBuffer) throws Exception {
    	
    	logger.info("간판제어결과 수신 응답 전송");
    	
    	// 결과 전송
		short wIdx = 0;
		byte[] writeBuffer = new byte[8];
		writeBuffer[wIdx++] = STX;
		
		byte[] lenBytes = ByteUtils.toUnsignedShortBytes(0x04);
		writeBuffer[wIdx++] = lenBytes[0];
		writeBuffer[wIdx++] = lenBytes[1];
		
		writeBuffer[wIdx++] = cmd;
		writeBuffer[wIdx++] = NORMAL;
		
		// CRC
		byte[] crc = Utils.getBytes(writeBuffer, 3, writeBuffer.length-6);
		
		short sCRC = CRC16.getInstance().getCRC(crc);
		
		ByteBuffer wBuffer = ByteBuffer.allocate(2);
		wBuffer.putShort(sCRC);
		wBuffer.flip();
		
		byte crc1 = wBuffer.get();
		byte crc2 = wBuffer.get();
		
		writeBuffer[wIdx++] = crc1;
		writeBuffer[wIdx++] = crc2;
		writeBuffer[wIdx++] = ETX;
		
		doWrite(writeBuffer);
    }
    
    /**
     * 냉난방 온도제어결과 수신
     * @param dataBuffer
     * @throws Exception
     */
    private void doProcessCmd10(byte cmd, byte[] dataBuffer) throws Exception {
    	
    	logger.info("냉난방 온도제어결과 수신");
    	
    	short idx = 1;
    	
    	byte result = dataBuffer[idx++];
		logger.info("RESULT="+result);
		
		isHACResult = result;
		
    	// 매장코드
		String dStoreCD = new String(Utils.getBytes(dataBuffer, idx, 20)).trim();
		logger.info("STR CD=" + dStoreCD);
		idx += 20;
		// GW ID
		String dGwID = String.valueOf(ByteUtils.toUnsignedShort(Utils.getBytes(dataBuffer, idx, 2)));
		logger.info("GW ID=" + dGwID);
		idx += 2;
		
		// 년
		String year = String.valueOf(ByteUtils.toUnsignedShort(Utils.getBytes(dataBuffer, idx, 2)));
		idx += 2;
		
    	byte month        = dataBuffer[idx++]; // 월
    	byte date         = dataBuffer[idx++]; // 일
    	byte hour         = dataBuffer[idx++]; // 시
    	byte minute       = dataBuffer[idx++]; // 분 
    	byte second       = dataBuffer[idx++]; // 초
    	byte hanconID     = dataBuffer[idx++]; // 하콘ID(0x00:전체, 0x01:하콘1…0x05:하콘5)
    	byte sensingTemp  = dataBuffer[idx++]; // 센싱온도
    	byte controlTemp  = dataBuffer[idx++]; // 제어온도
    	byte guideTemp    = dataBuffer[idx++]; // 제어온도
    	byte controlWho   = dataBuffer[idx++]; // 주체(0x00:SEMS, 0x01:Man, 0x02:Peak)
    	byte controlGubun = dataBuffer[idx++]; // 제어종류(0:사람제어, 1:REMS ON, 2:REMS OFF,  3:REMS 온도 제어)
    	byte onOFF        = dataBuffer[idx++]; // ON/OFF(0x00:ON, 0x01:OFF)
    	byte coolNheat    = dataBuffer[idx++]; // 냉난방(0x00:냉방, 0x01:난방)
    	byte controlAgt   = dataBuffer[idx++]; // 제어주체(0x00:SEMS, 0x01:Mobile)
    	
    	// 주체변환
    	String controlWhoConvert = "";
    	
    	if(controlWho == 0x00)
    		controlWhoConvert = "R";
    	else if(controlWho == 0x01)
    		controlWhoConvert = "H";
    	else 
    		controlWhoConvert = "P";
    	
    	// ON/OFF 변환
    	String onOFFConvert = "";
    	
    	if(onOFF == 0x00)
    		onOFFConvert = "Y";
    	else
    		onOFFConvert = "N";
    	
    	// 제어주체 변환
    	String controlAgtConvert = "";
    	
    	if(controlAgt == 0x00)
    		controlAgtConvert = "R";
    	else
    		controlAgtConvert = "M";
    	
    	
    	Map<String, String> paramMap = new HashMap<String, String>();
    	paramMap.put("CTL_TYPE", "AIRCON");
    	paramMap.put("STR_CD", dStoreCD);
    	paramMap.put("REGI_ID", dStoreCD);
		
		StringBuilder sb = new StringBuilder();
		sb.append(year);
		
		if(month < 10)
			sb.append("0");
		
		sb.append(month);
		
		if(date < 10)
			sb.append("0");
		
		sb.append(date);
		
		paramMap.put("YYYYMMDD", sb.toString());
		
		sb.setLength(0);
		
		if(hour < 10)
			sb.append("0");
		
		sb.append(hour);
		
		if(minute < 10)
			sb.append("0");
		
		sb.append(minute);
		
		paramMap.put("HHMIN", sb.toString());
		
		sb.setLength(0);
		
		if(second < 10)
			sb.append("0");
		
		sb.append(second);
		
		paramMap.put("SSMS", sb.toString());
		paramMap.put("HACON_ID", String.valueOf(hanconID));
		paramMap.put("WHO", controlWhoConvert);
		paramMap.put("ONOFF", String.valueOf(controlGubun));
		paramMap.put("OPER_YN", onOFFConvert);
		paramMap.put("SENSING_TEMP_VAL", String.valueOf(sensingTemp));
		paramMap.put("GUIDE_TEMP_VAL", String.valueOf(guideTemp));
		paramMap.put("COOLHEAT", String.valueOf(coolNheat));
		paramMap.put("CTRL_TEMP_VAL", String.valueOf(controlTemp));
		paramMap.put("CONTROL_AGT", controlAgtConvert);
		
		// 제어주체가 SEMS이면 큐에 넣어서 처리한다.
		if("R".equals(controlAgtConvert)){
			ctlQueue.doAddData(paramMap);
		}
		// 제어주체가 모바일이면 바로 처리한다.
		else{
			xeyeDAO.insertTH_STR_AIRCON_CTRL_LOG(paramMap);
		}
		
		/*logger.info("냉난방제어결과 수신 응답 전송");
		
    	// 결과 전송
		short wIdx = 0;
		byte[] writeBuffer = new byte[8];
		writeBuffer[wIdx++] = STX;
		
		byte[] lenBytes = ByteUtils.toUnsignedShortBytes(0x04);
		writeBuffer[wIdx++] = lenBytes[0];
		writeBuffer[wIdx++] = lenBytes[1];
		
		writeBuffer[wIdx++] = cmd;
		writeBuffer[wIdx++] = NORMAL;
		
		// CRC
		byte[] crc = Utils.getBytes(writeBuffer, 3, writeBuffer.length-6);
		
		short sCRC = CRC16.getInstance().getCRC(crc);
		
		ByteBuffer wBuffer = ByteBuffer.allocate(2);
		wBuffer.putShort(sCRC);
		wBuffer.flip();
		
		byte crc1 = wBuffer.get();
		byte crc2 = wBuffer.get();
		
		writeBuffer[wIdx++] = crc1;
		writeBuffer[wIdx++] = crc2;
		writeBuffer[wIdx++] = ETX;
		
		doWrite(writeBuffer);*/
    }
    
    /**
     * 전력피크알람 수신
     * @param dataBuffer
     * @throws Exception
     */
    private void doProcessCmd12(byte cmd, byte[] dataBuffer) throws Exception {
    	
    	logger.info("전력피크알람 수신");
    	
    	short idx = 1;
    	
    	// 매장코드
		String dStoreCD = new String(Utils.getBytes(dataBuffer, idx, 20)).trim();
		logger.info("STR CD=" + dStoreCD);
		idx += 20;
		// GW ID
		String dGwID = String.valueOf(ByteUtils.toUnsignedShort(Utils.getBytes(dataBuffer, idx, 2)));
		logger.info("GW ID=" + dGwID);
		idx += 2;
		
		// 년
		String year = String.valueOf(ByteUtils.toUnsignedShort(Utils.getBytes(dataBuffer, idx, 2)));
		idx += 2;
		
    	byte month        = dataBuffer[idx++]; // 월
    	byte date         = dataBuffer[idx++]; // 일
    	byte hour         = dataBuffer[idx++]; // 시
    	byte minute       = dataBuffer[idx++]; // 분 
    	byte second       = dataBuffer[idx++]; // 초
    	
    	// 피크값
    	String peakWatage = String.valueOf(ByteUtils.toUnsignedInt(Utils.getBytes(dataBuffer, idx, 4)));
    	idx += 4;
    	// 계약전력
    	String userPeakWatage = String.valueOf(ByteUtils.toUnsignedInt(Utils.getBytes(dataBuffer, idx, 4)));
    	idx += 4;
    	// 제어여부(0x00:제어, 0x01:미제어)
    	byte peakControlYN = dataBuffer[idx++];
    	// 센싱온도
    	byte sensingTemp  = dataBuffer[idx++];
    	
    	// 제어여부 변환
    	String peakControlYNConvert = "";
    	
    	if(peakControlYN == 0x00)
    		peakControlYNConvert = "Y";
    	else
    		peakControlYNConvert = "N";
    	
    	Map<String, String> paramMap = new HashMap<String, String>();
    	paramMap.put("STR_CD", dStoreCD);
    	paramMap.put("REGI_ID", dStoreCD);
		
		StringBuilder sb = new StringBuilder();
		sb.append(year);
		
		if(month < 10)
			sb.append("0");
		
		sb.append(month);
		
		if(date < 10)
			sb.append("0");
		
		sb.append(date);
		
		paramMap.put("YYYYMMDD", sb.toString());
		
		sb.setLength(0);
		
		if(hour < 10)
			sb.append("0");
		
		sb.append(hour);
		
		if(minute < 10)
			sb.append("0");
		
		sb.append(minute);
		
		paramMap.put("HHMIN", sb.toString());
		
		sb.setLength(0);
		
		if(second < 10)
			sb.append("0");
		
		sb.append(second);
		
		paramMap.put("SSMS", sb.toString());
		paramMap.put("PEAK_WATAGE", peakWatage);
		paramMap.put("USER_PEAK_WATAGE", userPeakWatage);
		paramMap.put("PEAK_CONTROL_YN", String.valueOf(peakControlYNConvert));
		paramMap.put("SENSE_TEMP", String.valueOf(sensingTemp));
		
		xeyeDAO.insertTH_STR_PEAK_ALARM(paramMap);
		
		logger.info("전력피크알람 수신 응답 전송");
		
    	// 결과 전송
		short wIdx = 0;
		byte[] writeBuffer = new byte[8];
		writeBuffer[wIdx++] = STX;
		
		byte[] lenBytes = ByteUtils.toUnsignedShortBytes(0x04);
		writeBuffer[wIdx++] = lenBytes[0];
		writeBuffer[wIdx++] = lenBytes[1];
		
		writeBuffer[wIdx++] = cmd;
		writeBuffer[wIdx++] = NORMAL;
		
		// CRC
		byte[] crc = Utils.getBytes(writeBuffer, 3, writeBuffer.length-6);
		
		short sCRC = CRC16.getInstance().getCRC(crc);
		
		ByteBuffer wBuffer = ByteBuffer.allocate(2);
		wBuffer.putShort(sCRC);
		wBuffer.flip();
		
		byte crc1 = wBuffer.get();
		byte crc2 = wBuffer.get();
		
		writeBuffer[wIdx++] = crc1;
		writeBuffer[wIdx++] = crc2;
		writeBuffer[wIdx++] = ETX;
		
		doWrite(writeBuffer);
    }
    
    /**
     * 날씨정보 요청 수신
     * @param dataBuffer
     * @throws Exception
     */
    private void doProcessCmd17(byte cmd, byte[] dataBuffer) throws Exception {
    	
    	logger.info("날씨정보요청 수신");
    	
    	short idx = 1;
    	
		// 매장코드
		String storeCD = new String(Utils.getBytes(dataBuffer, idx, 20)).trim();
		logger.info("STR CD=" + storeCD);
		idx += 20;
		// GW ID
		String gwID = String.valueOf(ByteUtils.toUnsignedShort(Utils.getBytes(dataBuffer, idx, 2)));
		logger.info("GW ID=" + gwID);
		
		// Validation
		byte result = NORMAL;
		
		Map<String, String> gwMstMap = null;
		List<Map<String, String>> hacPolicyList = null;
		List<Map<String, String>> hacRecommTempList = null;
		
		// 매장코드가 존재하지 않으면
		if("".equals(storeCD)){
			result = ERR_INVALID_DATA;
		}else{
			
			Map<String, String> paramMap = new HashMap<String, String>();
			paramMap.put("STR_CD", storeCD);
			paramMap.put("GW_ID", gwID);
			
			gwMstMap = xeyeDAO.selectStrInfoMst2(paramMap);
			
			if(gwMstMap != null){
				
				if(!"".equals(gwMstMap.get("COMPANY_CD"))){
	    			// 냉난방정책
	    			hacPolicyList = xeyeDAO.selectHACPolicyMMList(gwMstMap);
	    			// 냉난방권장온도
	    			hacRecommTempList = xeyeDAO.selectHACRecommTempList(gwMstMap);
    			}
				
			}else{
				result = ERR_STR_NOEXIST;
			}
		}
		
		logger.info("날씨정보 수신 결과 전송");
		
		// 결과 전송
		short wIdx = 0;
		byte[] writeBuffer = new byte[38];
		writeBuffer[wIdx++] = STX;
		
		byte[] lenBytes = ByteUtils.toUnsignedShortBytes(0x22);
		writeBuffer[wIdx++] = lenBytes[0];
		writeBuffer[wIdx++] = lenBytes[1];
		
		writeBuffer[wIdx++] = cmd;
		writeBuffer[wIdx++] = result;
		
		// 매장정보가 존재하면
		if(gwMstMap != null){
			
	        // 일출시분
	        if(gwMstMap.get("SUNRISE_TIME") == null){
	        	// 일출시간
	        	writeBuffer[wIdx++] = (byte)0xFF;
	        	// 일출분
	        	writeBuffer[wIdx++] = (byte)0xFF;
	        }else{
	        	
	        	StringTokenizer st = new StringTokenizer(gwMstMap.get("SUNRISE_TIME"), ":");
	        	
	        	// 일출시간
	        	writeBuffer[wIdx++] = ByteUtils.toByte(st.nextToken(), (byte)0xFF);
	        	// 일출분
	        	writeBuffer[wIdx++] = ByteUtils.toByte(st.nextToken(), (byte)0xFF);
	        }
	        // 일몰시분
	        if(gwMstMap.get("SUNSET_TIME") == null){
	        	// 일몰시간
	        	writeBuffer[wIdx++] = (byte)0xFF;
	        	// 일몰분
	        	writeBuffer[wIdx++] = (byte)0xFF;
	        }else{
	        	
	        	StringTokenizer st = new StringTokenizer(gwMstMap.get("SUNSET_TIME"), ":");
	        	
	        	// 일몰시간
	        	writeBuffer[wIdx++] = ByteUtils.toByte(st.nextToken(), (byte)0xFF);
	        	// 일몰분
	        	writeBuffer[wIdx++] = ByteUtils.toByte(st.nextToken(), (byte)0xFF);
	        }
	        //날씨코드
	        writeBuffer[wIdx++] = ByteUtils.toByte(gwMstMap.get("WEATHER_CD"), (byte)0x00);
	        // 외기온도
	        writeBuffer[wIdx++] = ByteUtils.toByte(gwMstMap.get("FORECAST_TEMP"), (byte)0xFF);
	        // 냉난방정책
	        if(hacPolicyList != null && hacPolicyList.size() > 0){
		        for(Map<String, String> hacPolicyMap : hacPolicyList){
		        	
		        	String policy = "";
		        	
		        	// 난방
		        	if("H".equals(hacPolicyMap.get("MODE"))){
		        		policy = "1";
		        	}
		        	// 냉방
		        	else if("C".equals(hacPolicyMap.get("MODE"))){
		        		policy = "2";
		        	}
		        	// 환절기
		        	else{
		        		policy = "3";
		        	}
		        	
		        	writeBuffer[wIdx++] = ByteUtils.toByte(policy, (byte)0xFF);
		        }
	        }else{
	        	for(int i = 0; i < 12; i++){
	        		writeBuffer[wIdx++] = (byte)0xFF;
		        }
	        }
	        // 냉난방권장온도
	        if(hacRecommTempList != null && hacRecommTempList.size() > 0){
		        for(Map<String, String> hacRecommTempMap : hacRecommTempList){
		        	writeBuffer[wIdx++] = ByteUtils.toByte(String.valueOf(new BigDecimal(String.valueOf(hacRecommTempMap.get("RECOMM_TEMP"))).shortValue()), (byte)0xFF);
		        }
	        }else{
	        	for(int i = 0; i < 12; i++){
	        		writeBuffer[wIdx++] = (byte)0xFF;
		        }
	        }
		}
		// 매장정보가 존재하지 않으면
		else{
			
	        // 일출시간
	        writeBuffer[wIdx++] = (byte)0xFF;
	        // 일출분
	        writeBuffer[wIdx++] = (byte)0xFF;
	        // 일몰시간
	        writeBuffer[wIdx++] = (byte)0xFF;
	        // 일몰분
	        writeBuffer[wIdx++] = (byte)0xFF;
	        // 날씨코드
	        writeBuffer[wIdx++] = (byte)0x00;
	        // 외기온도
	        writeBuffer[wIdx++] = (byte)0xFF;
	        // 냉난방정책
	        for(int i = 0; i < 12; i++){
        		writeBuffer[wIdx++] = (byte)0xFF;
	        }
	        // 냉난방권장온도
	        for(int i = 0; i < 12; i++){
        		writeBuffer[wIdx++] = (byte)0xFF;
	        }
		}
		
		// CRC
		byte[] crc = Utils.getBytes(writeBuffer, 3, writeBuffer.length-6);
		
		short sCRC = CRC16.getInstance().getCRC(crc);
		
		ByteBuffer wBuffer = ByteBuffer.allocate(2);
		wBuffer.putShort(sCRC);
		wBuffer.flip();
		
		byte crc1 = wBuffer.get();
		byte crc2 = wBuffer.get();
		
		writeBuffer[wIdx++] = crc1;
		writeBuffer[wIdx++] = crc2;
		writeBuffer[wIdx++] = ETX;
    	
		doWrite(writeBuffer);
    }
}
