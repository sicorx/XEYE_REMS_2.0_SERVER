package com.hoonit.xeye.net.server.gw;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
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
	private final byte NORMAL           = 1;
	private final byte ERR_STX_ETX      = 2;
	private final byte ERR_CRC          = 3;
	private final byte ERR_INVALID_DATA = 4;
	private final byte ERR_FILE_TRANS   = 5;
	private final byte ERR_CTRL         = 6;
	private final byte ERR_EXCEPTION    = 7;
      
    public ClientHandlerThread(ServerThread serverThread, Abortable abortable, SocketChannel client) {
    	
    	this.serverThread = serverThread;
        this.abortable    = abortable;
        this.client       = client;
        this.bufferSize   = Integer.parseInt(ResourceBundleHandler.getInstance().getString("server.buffer.size"));
        
        try{
        	
        	String logName = client.getRemoteAddress().toString();
        	logName = logName.replaceAll("/", "");
        	logName = logName.substring(0, logName.lastIndexOf(":"));
        	
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
    public void notifyStoreInfo(final NotifyEvent evt){
    	
		new Thread(){
			public void run(){
				
				synchronized(writeLock){
					
					doCheckStoreCD();
					
					if(storeCD.equals(evt.getStoreCD()) && gwID.equals(evt.getGwID())){
						
						// 매장코드 설정
						//storeCD = evt.getStoreCD();
						
						write(evt.getData(), false);
					}
				}
			}
		}.start();
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
			    				
			    				int idx = 0;
		    					
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
    						
	    					Map<String, String> paramMap = new HashMap<String, String>();
	    					paramMap.put("STR_CD", storeCD);
	    					
	    					Map<String, String> resultMap = xeyeDAO.selectSunRisetDetail(paramMap);
	    					
	    					byte[] data = evt.getData();
	    					
	    					int idx = 0;
	    					
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
		    					// 일출시분
		    			        if(resultMap.get("SUNRISE_TIME") == null){
		    			        	// 일출시간
		    			        	buffer[idx++] = (byte)0xFF;
		    			        	// 일출분
		    			        	buffer[idx++] = (byte)0xFF;
		    			        }else{
		    			        	// 일출시간
		    			        	buffer[idx++] = ByteUtils.toByte(resultMap.get("SUNRISE_TIME").substring(0, 2), (byte)0xFF);
		    			        	// 일출분
		    			        	buffer[idx++] = ByteUtils.toByte(resultMap.get("SUNRISE_TIME").substring(2, 4), (byte)0xFF);
		    			        }
		    			        // 일몰시분
		    			        if(resultMap.get("SUNSET_TIME") == null){
		    			        	// 일몰시간
		    			        	buffer[idx++] = (byte)0xFF;
		    			        	// 일몰분
		    			        	buffer[idx++] = (byte)0xFF;
		    			        }else{
		    			        	// 일몰시간
		    			        	buffer[idx++] = ByteUtils.toByte(resultMap.get("SUNSET_TIME").substring(0, 2), (byte)0xFF);
		    			        	// 일몰분
		    			        	buffer[idx++] = ByteUtils.toByte(resultMap.get("SUNSET_TIME").substring(2, 4), (byte)0xFF);
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
    public void notifySignControl(final NotifyEvent evt){
    	
		new Thread(){
			public void run(){
				
				synchronized(writeLock){
					
					doCheckStoreCD();
					
					if(storeCD.equals(evt.getStoreCD()) && gwID.equals(evt.getGwID())){
    					
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
                    	
                        int readBytes = client.read(buffer);
                        
                        if (readBytes < 0) {
                            done = true;
                            break;
                        } else if (readBytes == 0) {
                            continue;
                        }
                        
                        logger.info("read length : " + readBytes);
                        
                        buffer.flip();
                        
                        byte[] data = new byte[readBytes];
                        
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
        							
        							// Client 접속, MAC 주소 설정
        							if(cmd == 0x01){
        								doProcessCmd1(cmd, dataBuffer);
        							}
        							// Data 수신일 경우
        							else if(cmd == 0x02){
        								doProcessCmd2(cmd, dataBuffer);
        							}
        							// 매장정보 전송 결과
        							else if(cmd == 0x03){
        								
        								logger.info("매장정보 전송 결과 수신");
        								
        								doProcessResponse(dataBuffer);
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
        							
        						}else{
        							
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
        						}
        					}else{
        						
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
    
    /**
     * Client 응답 결과 처리
     * @param dataBuffer
     */
    private void doProcessResponse(byte[] dataBuffer){
    	
    	int idx = 1;
    	
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
    }
    
    /**
     * Client 접속 정보 수신
     * @param dataBuffer
     * @throws Exception
     */
    private void doProcessCmd1(byte cmd, byte[] dataBuffer) throws Exception {
    	
    	logger.info("Client 접속 정보 수신");
    	
    	int idx = 1;
    	
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
		
		// 매장코드가 존재하지 않고, GW ID가 0이면 최초접속
		if("".equals(storeCD) && "0".equals(gwID)){
			
			//gwMstMap = xeyeDAO.selectMaxStrCDNGWID();
			
			Map<String, String> paramMap = new HashMap<String, String>();
			//paramMap.put("STR_CD", gwMstMap.get("STR_CD"));
			//paramMap.put("GW_ID", String.valueOf(gwMstMap.get("GW_ID")));
			paramMap.put("GW_ADDR", macAddress);
			paramMap.put("GW_IP", ip);
			paramMap.put("GW_VENDOR", "DIT");
			paramMap.put("GW_HW_MODEL", "RPI3");
			paramMap.put("GW_SW_VER", String.valueOf(version));
			paramMap.put("AUTH_YN", "N");
			paramMap.put("DIAGNOSIS_MODULE_TYPE", "");
			paramMap.put("USE_YN", "Y");
			
			gwMstMap = xeyeDAO.insertGWMst(paramMap);
			
		}else{
			
			Map<String, String> paramMap = new HashMap<String, String>();
			paramMap.put("GW_ID", gwID);
			paramMap.put("STR_CD", storeCD);
			
			gwMstMap = xeyeDAO.selectGWMst(paramMap);
			
			if(gwMstMap != null){
    			
    			if(!"".equals(gwMstMap.get("COMPANY_CD"))){
	    			// 냉난방정책
	    			hacPolicyList = xeyeDAO.selectHACPolicyMMList(gwMstMap);
	    			// 냉난방권장온도
	    			hacRecommTempList = xeyeDAO.selectHACRecommTempList(gwMstMap);
    			}
			}
		}
		
		logger.info("Client 접속 정보 수신 결과 전송");
		
		// 결과 전송
		short wIdx = 0;
		byte[] writeBuffer = new byte[80];
		writeBuffer[wIdx++] = STX;
		
		byte[] lenBytes = ByteUtils.toUnsignedShortBytes(0x4C);
		writeBuffer[wIdx++] = lenBytes[0];
		writeBuffer[wIdx++] = lenBytes[1];
		
		writeBuffer[wIdx++] = cmd;
		writeBuffer[wIdx++] = result;
		
		int storeCDLen = 20; // 매장코드 길이
		
		// 매장코드가 존재하고, GW ID가 존재하면
		if(!"".equals(storeCD) && !"0".equals(gwID)){
			
			// 매장코드
	        try{
	        	
	    		String strCD = StringUtils.defaultIfEmpty(gwMstMap.get("STR_CD"), "");
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
	        byte[] gwIDs = ByteUtils.toUnsignedShortBytes(Integer.parseInt(String.valueOf(gwMstMap.get("GW_ID"))));
	        writeBuffer[wIdx++] = gwIDs[0];
	        writeBuffer[wIdx++] = gwIDs[1];
	        // 일출시분
	        if(gwMstMap.get("SUNRISE_TIME") == null){
	        	// 일출시간
	        	writeBuffer[wIdx++] = (byte)0xFF;
	        	// 일출분
	        	writeBuffer[wIdx++] = (byte)0xFF;
	        }else{
	        	// 일출시간
	        	writeBuffer[wIdx++] = ByteUtils.toByte(gwMstMap.get("SUNRISE_TIME").substring(0, 2), (byte)0xFF);
	        	// 일출분
	        	writeBuffer[wIdx++] = ByteUtils.toByte(gwMstMap.get("SUNRISE_TIME").substring(2, 4), (byte)0xFF);
	        }
	        // 일몰시분
	        if(gwMstMap.get("SUNSET_TIME") == null){
	        	// 일몰시간
	        	writeBuffer[wIdx++] = (byte)0xFF;
	        	// 일몰분
	        	writeBuffer[wIdx++] = (byte)0xFF;
	        }else{
	        	// 일몰시간
	        	writeBuffer[wIdx++] = ByteUtils.toByte(gwMstMap.get("SUNSET_TIME").substring(0, 2), (byte)0xFF);
	        	// 일몰분
	        	writeBuffer[wIdx++] = ByteUtils.toByte(gwMstMap.get("SUNSET_TIME").substring(2, 4), (byte)0xFF);
	        }
	        // 계약전력
	        byte[] cPower = ByteUtils.toUnsignedIntBytes(Long.parseLong(StringUtils.defaultIfEmpty(gwMstMap.get("CONT_DEMAND_POWER"), "0")));
	        writeBuffer[wIdx++] = cPower[0];
	        writeBuffer[wIdx++] = cPower[1];
	        writeBuffer[wIdx++] = cPower[2];
	        writeBuffer[wIdx++] = cPower[3];
	        // 냉난방기 제조사
	        writeBuffer[wIdx++] = (byte)0x00; // LG
	        // 냉난방정책
	        if(hacPolicyList != null && hacPolicyList.size() > 0){
		        for(Map<String, String> hacPolicyMap : hacPolicyList){
		        	writeBuffer[wIdx++] = ByteUtils.toByte(hacPolicyMap.get("MODE"), (byte)0xFF);
		        }
	        }else{
	        	for(int i = 0; i < 12; i++){
	        		writeBuffer[wIdx++] = (byte)0xFF;
		        }
	        }
	        // 냉난방권장온도
	        if(hacRecommTempList != null && hacRecommTempList.size() > 0){
		        for(Map<String, String> hacRecommTempMap : hacRecommTempList){
		        	writeBuffer[wIdx++] = ByteUtils.toByte(hacRecommTempMap.get("RECOMM_TEMP"), (byte)0xFF);
		        }
	        }else{
	        	for(int i = 0; i < 12; i++){
	        		writeBuffer[wIdx++] = (byte)0xFF;
		        }
	        }
		}
		// 매장코드가 존재하지 않고, GW ID가 미존재하면
		else{
			
			storeCD = gwMstMap.get("STR_CD");
			gwID    = String.valueOf(gwMstMap.get("GW_ID"));
			
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
	        byte[] gwIDs = ByteUtils.toUnsignedShortBytes(Integer.parseInt(gwID));
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
			
		    	int idx = 1;
		    	
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
					jsonObj.put("REGI_ID", "");
					
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
    	
    	int idx = 1;
    	
    	byte result = dataBuffer[idx++];
		
		logger.info("RESULT="+result);
		
		if(result == NORMAL){
			logger.info("SUCCESS");
			
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
			
			
			JSONObject jsonObj = new JSONObject();
			jsonObj.put("CTL_TYPE", "SIGN");
			jsonObj.put("STR_CD", storeCD);
			jsonObj.put("REGI_ID", "");
			
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
			
			sb.setLength(0);
			
			if(second < 10)
				sb.append("0");
			
			sb.append(second);
			
			jsonObj.put("SSMS", sb.toString());
			jsonObj.put("SIGN_STAT", String.valueOf(status));
			jsonObj.put("SIGN_STAT_OLD", String.valueOf(statusOld));
			jsonObj.put("CONTROL_AGT", String.valueOf(ctlAgt));
			
			ctlQueue.doAddData(jsonObj);
			
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
}
