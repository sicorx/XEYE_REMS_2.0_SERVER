package com.hoonit.xeye.net.server.notify;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import com.hoonit.xeye.dao.XEyeDAO;
import com.hoonit.xeye.event.NotifyEvent;
import com.hoonit.xeye.event.NotifyListener;
import com.hoonit.xeye.util.ByteUtils;
import com.hoonit.xeye.util.CRC16;
import com.hoonit.xeye.util.ResourceBundleHandler;
import com.hoonit.xeye.util.Utils;

public class NotifyServer extends Thread{

	protected final Logger logger = Logger.getLogger(getClass().getName());

	// prestartAllCoreThreads() 를 사용하면 corePoolSize 만큼 스레드를 미리 생성함.생성함.
	private int corePoolSize;

	// xaximumPoolsize 는 동시에 얼마나얼마나 많은 개수의 스레드가 동작할 수 있는지를 제한하는 최대값.
	private int maximumPoolsize;

	private int blockingQueueSize;

	// keepAliveTime 는 스레드 유지 시간으로 스레드가 keepAliveTime 이상이상 대기하고 있으면
	// 해당 스레드는 제거 될 수 있음. 풀의풀의 스레드 개수가 corePoolSize 를 넘어서면 제거될 수 있음.
	private long keepAliveTime;

	private ThreadPoolExecutor pool;

	private BlockingQueue<Runnable> queue;

	private ServerSocket server  = null;
	
	private static String IP = "";
	
    private static int PORT_NUMBER = 12200;

	private Vector<NotifyListener> notifyListener;
	
	private Map<String, NotifyListener> notifyListenerMap;
	
	private XEyeDAO xeyeDAO;
	
	private static final byte STX              = 0x02;
	private static final byte ETX              = 0x03;
	
	private static final byte NORMAL           = 0x01; // 정상
	private static final byte ERR_STX_ETX      = 0x02; // STX, ETX 오류
	private static final byte ERR_CRC          = 0x03; // CRC 오류
	private static final byte ERR_INVALID_DATA = 0x04; // 유효하지 않은 데이터 오류
	private static final byte ERR_FILE_TRANS   = 0x05; // 파일전송 오류
	private static final byte ERR_CTRL         = 0x06; // 제어오류
	private static final byte ERR_EXCEPTION    = 0x07; // Exception 발생 오류
	private static final byte ERR_STR_NOEXIST  = 0x08; // 매장정보 미존재 오류

	public NotifyServer(){

		try{

			this.corePoolSize = 100;

			this.maximumPoolsize = 1000;

			this.blockingQueueSize = 1000;

			this.keepAliveTime = 1000 * 5; // 5 seconds

			notifyListener = new Vector<NotifyListener>();
			
			notifyListenerMap = new HashMap<String, NotifyListener>();

			queue = new ArrayBlockingQueue<Runnable>(blockingQueueSize);
			pool  = new ThreadPoolExecutor(corePoolSize,
					maximumPoolsize,
					keepAliveTime,
					TimeUnit.SECONDS,
					queue);
			
			try{
	    		
	    		IP = InetAddress.getLocalHost().getHostAddress();
	    		
	    	}catch(Exception e){
	    		logger.error(e.getMessage(), e);
	    	}
			
			PORT_NUMBER = Integer.parseInt(ResourceBundleHandler.getInstance().getString("notify.port"));
			
			server = new ServerSocket(PORT_NUMBER);
			
		}catch(Exception e){
			logger.error(e);
		}
	}
	
	public void addNotifyListener(NotifyListener obs) {
		notifyListener.add(obs);
	}

	public void removeNotifyListener(NotifyListener obs) {
		notifyListener.remove(obs);
	}
	
	public void addNotifyListenerMap(String storeCD, NotifyListener obs){
		notifyListenerMap.put(storeCD, obs);
	}
	
	public void removeNotifyListenerMap(String storeCD){
		notifyListenerMap.remove(storeCD);
	}
	
	public void setXEyeDAO(XEyeDAO xeyeDAO){
    	this.xeyeDAO = xeyeDAO;
    }
	
	/**
	 * 매장정보 통보
	 * @param data
	 */
	public boolean notifyStoreInfo(String storeCD, String gwID, byte[] data){

		boolean result = false;
		
		NotifyEvent evt = new NotifyEvent(this);
		evt.setStoreCD(storeCD);
		evt.setGwID(gwID);
		evt.setData(data);
		
		/*for(int i = 0; i < notifyListener.size(); i++){
			notifyListener.get(i).notifyStoreInfo(evt);
		}*/
		
		NotifyListener listener = notifyListenerMap.get(storeCD);
		
		if(listener != null)
			result = listener.notifyStoreInfo(evt);
		
		return result;
	}
	
	/**
	 * 패치파일 업데이트 통보
	 * @param file
	 */
	public void notifyFileUpdate(byte[] data){
		
		NotifyEvent evt = new NotifyEvent(this);
		evt.setData(data);
		
		for(int i = 0; i < notifyListener.size(); i++){
    		notifyListener.get(i).notifyFileUpdate(evt);
		}
	}
	
	/**
	 * 일출일몰시간정보 통보
	 * @param data
	 */
	public void notifySunRisetInfo(byte[] data){

		try{
			
			List<Map<String, String>> list = xeyeDAO.selectStoreSunRisetList(null);
			
			Map<String, String> storeMap = new HashMap<String, String>();
			
			for(Map<String, String> resultMap : list){
				storeMap.put(resultMap.get("STR_CD"), resultMap.get("SUNRISE_TIME") + ":" + resultMap.get("SUNSET_TIME"));
			}
			
			NotifyEvent evt = new NotifyEvent(this);
			evt.setData(data);
			evt.setStoreMap(storeMap);
			
			for(int i = 0; i < notifyListener.size(); i++){
				notifyListener.get(i).notifySunRisetInfo(evt);
			}
			
		}catch(Exception e){
			logger.error(e.getMessage(), e);
		}
	}
	
	/**
	 * 냉난방정책 통보
	 * @param data
	 */
	public void notifyHACPolicyInfo(byte[] data){

		NotifyEvent evt = new NotifyEvent(this);
		evt.setData(data);
		
		for(int i = 0; i < notifyListener.size(); i++){
			notifyListener.get(i).notifyHACPolicyInfo(evt);
		}
	}
	
	/**
	 * 냉난방 권장온도 통보
	 * @param data
	 */
	public void notifyHACTempInfo(byte[] data){

		NotifyEvent evt = new NotifyEvent(this);
		evt.setData(data);
		
		for(int i = 0; i < notifyListener.size(); i++){
			notifyListener.get(i).notifyHACTempInfo(evt);
		}
	}
	
	/**
	 * 간판제어 통보
	 * @param data
	 */
	public boolean notifySignControl(String storeCD, String gwID, byte[] data){

		boolean result = false;
		
		NotifyEvent evt = new NotifyEvent(this);
		evt.setStoreCD(storeCD);
		evt.setGwID(gwID);
		evt.setData(data);
		
		/*for(int i = 0; i < notifyListener.size(); i++){
			notifyListener.get(i).notifySignControl(evt);
		}*/
		
		NotifyListener listener = notifyListenerMap.get(storeCD);
		
		if(listener != null)
			result = listener.notifySignControl(evt);
		
		return result;
	}
	
	/**
	 * 냉난방제어 통보
	 * @param data
	 */
	public boolean notifyAirconControl(String storeCD, String gwID, byte[] data){

		boolean result = false;
		
		NotifyEvent evt = new NotifyEvent(this);
		evt.setStoreCD(storeCD);
		evt.setGwID(gwID);
		evt.setData(data);
		
		/*for(int i = 0; i < notifyListener.size(); i++){
			notifyListener.get(i).notifyAirconControl(evt);
		}*/
		
		NotifyListener listener = notifyListenerMap.get(storeCD);
		
		if(listener != null)
			result = listener.notifyAirconControl(evt);
		
		return result;
	}
	
	/**
	 * 게이트웨이상태 요청
	 * @param gwID
	 * @param data
	 * @return
	 */
	public byte[] notifyGetStat(String storeCD, String gwID, byte[] data){
		
		byte[] result = null;
		
		NotifyEvent evt = new NotifyEvent(this);
		evt.setStoreCD(storeCD);
		evt.setGwID(gwID);
		evt.setData(data);
		
		NotifyListener listener = notifyListenerMap.get(storeCD);
		
		if(listener != null)
			result = listener.notifyGetStat(evt);
		
		return result;
	}
	
	/**
	 * 게이트웨이 재시작 요청
	 * @param gwID
	 * @param data
	 * @return
	 */
	public boolean notifyGatewayRestart(String storeCD, String gwID, byte[] data){
		
		boolean result = false;
		
		NotifyEvent evt = new NotifyEvent(this);
		evt.setStoreCD(storeCD);
		evt.setGwID(gwID);
		evt.setData(data);
		
		NotifyListener listener = notifyListenerMap.get(storeCD);
		
		if(listener != null)
			result = listener.notifyGatewayRestart(evt);
		
		return result;
	}
	
	/**
	 * 시스템 리부팅 요청
	 * @param gwID
	 * @param data
	 * @return
	 */
	public boolean notifySystemRebooting(String storeCD, String gwID, byte[] data){
		
		boolean result = false;
		
		NotifyEvent evt = new NotifyEvent(this);
		evt.setStoreCD(storeCD);
		evt.setGwID(gwID);
		evt.setData(data);
		
		NotifyListener listener = notifyListenerMap.get(storeCD);
		
		if(listener != null)
			result = listener.notifySystemRebooting(evt);
		
		return result;
	}

	public void doStart(){
		if(server != null){
			this.start();
		}
	}

	@Override
	public void run(){

		logger.info("Notify Server is started with " + IP + ":" + PORT_NUMBER);
		logger.info("Notify Server is waiting for accept");
		
		while(true){
			try{
				
				Socket socket = server.accept();
				logger.info("Notify Server accepted client : " + socket.getInetAddress());

				pool.execute(new Worker(socket));

			}catch(Exception e){
				logger.error(e);
			}
		}
	}

	class Worker implements Runnable{

		Socket socket;

		DataInputStream in;

		DataOutputStream out;

		public Worker(Socket socket){
			
			this.socket = socket;

			try{

				in  = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
				out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

			}catch(Exception e){
				logger.error(e);
			}
		}
		
		private void doClose(){
			
			try{
				
				if(in != null) in.close();
				if(out != null) out.close();
				if(socket != null) socket.close();
				
			}catch(Exception e){
				logger.error(e.getMessage(), e);
			}
		}
		
		@Override
		public void run(){
			
			try{

				byte[] readBuffer = new byte[1024];

				int readBytes = in.read(readBuffer, 0, readBuffer.length);

				logger.info(readBytes + " byte readed...");

				if(readBytes > 0){
					
					byte stx = readBuffer[0];
					int len = ByteUtils.toUnsignedShort(Utils.getBytes(readBuffer, 1, 2));
					byte etx = readBuffer[readBytes-1];
					
					logger.debug("STX=" + ByteUtils.toHexString(stx));
					logger.debug("ETX=" + ByteUtils.toHexString(etx));
					
					if(stx == STX && etx == ETX){
					
						logger.debug("LEN=" + Integer.toHexString(len & 0xFFFF));
						
						byte[] dataBuffer = Utils.getBytes(readBuffer, 3, len-2);
						byte[] crcBuffer  = Utils.getBytes(readBuffer, len+1, 2);
						
						short dataCRC = CRC16.getInstance().getCRC(dataBuffer);
						
						ByteBuffer crcBuffer2 = ByteBuffer.allocate(2);
						crcBuffer2.put(crcBuffer);
						crcBuffer2.flip();
						
						short chekCRC = crcBuffer2.getShort();
						
						logger.debug("DATA CRC=" + dataCRC);
						logger.debug("CHEC CRC=" + chekCRC);
						
						if(dataCRC == chekCRC){
						
							short idx = 0;
							
							byte cmd = dataBuffer[idx++];
							
							logger.debug("CMD=" + ByteUtils.toHexString(cmd));
							
							// 매장정보 수신
							if(cmd == 0x03){
								doProcessCMD3(cmd, dataBuffer, readBuffer, readBytes);
							}
							// 패치파일 업데이트 수신
							else if(cmd == 0x04){
								doProcessCMD4(cmd, dataBuffer, readBuffer, readBytes);
							}
							// 일출일몰시간 수신
							else if(cmd == 0x05){
								doProcessCMD5(cmd, dataBuffer, readBuffer, readBytes);
							}
							// 냉난방정책 수신
							else if(cmd == 0x06){
								doProcessCMD6(cmd, dataBuffer, readBuffer, readBytes);
							}
							// 냉난방 권장온도 수신
							else if(cmd == 0x07){
								doProcessCMD7(cmd, dataBuffer, readBuffer, readBytes);
							}
							// 간판제어 수신
							else if(cmd == 0x08){
								doProcessCMD8(cmd, dataBuffer, readBuffer, readBytes);
							}
							// 냉난방제어 수신
							else if(cmd == 0x0B){
								doProcessCMD11(cmd, dataBuffer, readBuffer, readBytes);
							}
							// 게이트웨이 재시작 수신
							else if(cmd == 0x0E){
								doProcessCMD14(cmd, dataBuffer, readBuffer, readBytes);
							}
							// 게이트웨이 재시작 수신
							else if(cmd == 0x0F){
								doProcessCMD15(cmd, dataBuffer, readBuffer, readBytes);
							}
							// 게이트웨이 상태 요청
							else if(cmd == 0x12){
								doProcessCMD18(cmd, dataBuffer, readBuffer, readBytes);
							}
						}else{
							
							logger.info("CRC 오류 전송");
							
							// 결과 전송
							short wIdx = 0;
							byte[] writeBuffer = new byte[8];
							writeBuffer[wIdx++] = STX;
							
							byte[] tempBytes = ByteUtils.toBytes((short)0x04);
							writeBuffer[wIdx++] = tempBytes[0];
							writeBuffer[wIdx++] = tempBytes[1];
							
							writeBuffer[wIdx++] = readBuffer[2];
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
							
							out.write(writeBuffer);
							out.flush();
							
							doClose();
						}
					}
					// STX와 ETX가 올바르지 않으면
					else{
						
						logger.info("STX, ETX 오류 전송");
						
						// 결과 전송
						short wIdx = 0;
						byte[] writeBuffer = new byte[8];
						writeBuffer[wIdx++] = STX;

						byte[] tempBytes = ByteUtils.toBytes((short)0x04);
						writeBuffer[wIdx++] = tempBytes[0];
						writeBuffer[wIdx++] = tempBytes[1];

						writeBuffer[wIdx++] = readBuffer[2];
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
						
						out.write(writeBuffer);
						out.flush();
						
						doClose();
					}
				}

			}catch(IOException e){
				logger.error(e.getMessage(), e);
			}catch(Exception e){
				logger.error(e.getMessage(), e);
			}finally{

				doClose();
				
				logger.info("The connection is released...");
			}
		}
		
		/**
		 * 매장정보 수신 처리
		 */
		private void doProcessCMD3(byte cmd, byte[] dataBuffer, byte[] readBuffer, int readBytes) throws Exception {
			
			logger.info("매장정보 수신");
			
			int idx = 1;
			
			// 매장코드
			String storeCD = new String(Utils.getBytes(dataBuffer, idx, 20)).trim();
			logger.info("STR CD=" + storeCD);
			idx += 20;
			// GW ID
			int gwID = ByteUtils.toUnsignedShort(Utils.getBytes(dataBuffer, idx, 2));
			logger.info("GW ID=" + gwID);
			idx += 2;
			//  계약전력
			long contractPower = ByteUtils.toUnsignedInt(Utils.getBytes(dataBuffer, idx, 4));
			logger.info("Contract Power=" + contractPower);
			
			// Validation
			byte result = NORMAL;
			
			// 접속된 모든 client에게 notify
			byte[] data = Utils.getBytes(readBuffer, 0, readBytes);
			
			boolean flag = notifyStoreInfo(storeCD, String.valueOf(gwID), data);
			
			if(!flag){
				result = ERR_EXCEPTION;
			}
			
			// 결과 전송
			short wIdx = 0;
			byte[] writeBuffer = new byte[8];
			writeBuffer[wIdx++] = STX;
			byte[] lenBytes = ByteUtils.toUnsignedShortBytes(0x04);
			writeBuffer[wIdx++] = lenBytes[0];
			writeBuffer[wIdx++] = lenBytes[1];
			writeBuffer[wIdx++] = cmd;
			writeBuffer[wIdx++] = result;
			
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
			
			out.write(writeBuffer);
			out.flush();
			
			doClose();
		}
		
		/**
		 * 패치파일 업데이트 수신 처리
		 */
		private void doProcessCMD4(byte cmd, byte[] dataBuffer, byte[] readBuffer, int readBytes) throws Exception {
			
			logger.info("패치파일 업데이트 수신");
			
			int idx = 1;
			
			// 전송구분
			byte sendGubun = dataBuffer[idx++];
			// 파일구분
			byte fileGubun = dataBuffer[idx++];
			logger.info("File Gubun=" + fileGubun);
			// File Server IP
			String serverIP = new String(Utils.getBytes(dataBuffer, idx, 15)).trim();
			logger.info("File Server IP=" + serverIP);
			idx += 15;
			// File Server Port
			int serverPort = ByteUtils.toUnsignedShort(Utils.getBytes(dataBuffer, idx, 2));
			logger.info("File Server Port=" + serverPort);
			
			// Validation
			byte result = NORMAL;
			
			if(fileGubun != 0x01 && fileGubun != 0x02 && fileGubun != 0x03){
				result = ERR_INVALID_DATA;
			}
			
			if("".equals(serverIP)){
				result = ERR_INVALID_DATA;
			}
			
			// 결과 전송
			short wIdx = 0;
			byte[] writeBuffer = new byte[8];
			writeBuffer[wIdx++] = STX;
			byte[] lenBytes = ByteUtils.toUnsignedShortBytes(0x04);
			writeBuffer[wIdx++] = lenBytes[0];
			writeBuffer[wIdx++] = lenBytes[1];
			writeBuffer[wIdx++] = cmd;
			writeBuffer[wIdx++] = result;
			
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
			
			out.write(writeBuffer);
			out.flush();
			
			doClose();
			
			if(result == NORMAL){
				
				// 접속된 모든 client에게 notify
				byte[] data = Utils.getBytes(readBuffer, 0, readBytes);
				
				notifyFileUpdate(data);
			}
		}
		
		/**
		 * 일출일몰시간 수신 처리
		 * @param cmd
		 * @param dataBuffer
		 * @param readBuffer
		 * @param readBytes
		 * @throws Exception
		 */
		private void doProcessCMD5(byte cmd, byte[] dataBuffer, byte[] readBuffer, int readBytes) throws Exception {
			
			logger.info("일출일몰정보 수신");
			
			// Validation
			byte result = NORMAL;
			
			// 결과 전송
			short wIdx = 0;
			byte[] writeBuffer = new byte[8];
			writeBuffer[wIdx++] = STX;
			byte[] lenBytes = ByteUtils.toUnsignedShortBytes(0x04);
			writeBuffer[wIdx++] = lenBytes[0];
			writeBuffer[wIdx++] = lenBytes[1];
			writeBuffer[wIdx++] = cmd;
			writeBuffer[wIdx++] = result;
			
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
			
			out.write(writeBuffer);
			out.flush();
			
			doClose();
			
			// 접속된 모든 client에게 notify
			byte[] data = Utils.getBytes(readBuffer, 0, readBytes);
			
			notifySunRisetInfo(data);
		}
		
		/**
		 * 냉난방정책 수신 처리
		 * @param cmd
		 * @param dataBuffer
		 * @param readBuffer
		 * @param readBytes
		 * @throws Exception
		 */
		private void doProcessCMD6(byte cmd, byte[] dataBuffer, byte[] readBuffer, int readBytes) throws Exception {
			
			logger.info("냉난방정책 수신");
			
			// Validation
			byte result = NORMAL;
			
			// 결과 전송
			short wIdx = 0;
			byte[] writeBuffer = new byte[8];
			writeBuffer[wIdx++] = STX;
			byte[] lenBytes = ByteUtils.toUnsignedShortBytes(0x04);
			writeBuffer[wIdx++] = lenBytes[0];
			writeBuffer[wIdx++] = lenBytes[1];
			writeBuffer[wIdx++] = cmd;
			writeBuffer[wIdx++] = result;
			
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
			
			out.write(writeBuffer);
			out.flush();
			
			doClose();
			
			// 접속된 모든 client에게 notify
			byte[] data = Utils.getBytes(readBuffer, 0, readBytes);
			
			notifyHACPolicyInfo(data);
		}
		
		/**
		 * 냉난방 권장온도 수신 처리
		 * @param cmd
		 * @param dataBuffer
		 * @param readBuffer
		 * @param readBytes
		 * @throws Exception
		 */
		private void doProcessCMD7(byte cmd, byte[] dataBuffer, byte[] readBuffer, int readBytes) throws Exception {
			
			logger.info("냉난방 권장온도 수신");
			
			// Validation
			byte result = NORMAL;
			
			// 결과 전송
			short wIdx = 0;
			byte[] writeBuffer = new byte[8];
			writeBuffer[wIdx++] = STX;
			byte[] lenBytes = ByteUtils.toUnsignedShortBytes(0x04);
			writeBuffer[wIdx++] = lenBytes[0];
			writeBuffer[wIdx++] = lenBytes[1];
			writeBuffer[wIdx++] = cmd;
			writeBuffer[wIdx++] = result;
			
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
			
			out.write(writeBuffer);
			out.flush();
			
			doClose();
			
			// 접속된 모든 client에게 notify
			byte[] data = Utils.getBytes(readBuffer, 0, readBytes);
			
			notifyHACTempInfo(data);
		}
		
		/**
		 * 간판제어 수신 처리
		 * @param cmd
		 * @param dataBuffer
		 * @param readBuffer
		 * @param readBytes
		 * @throws Exception
		 */
		private void doProcessCMD8(byte cmd, byte[] dataBuffer, byte[] readBuffer, int readBytes) throws Exception {
			
			logger.info("간판제어 수신");
			
			int idx = 1;
			
			// 매장코드
			String storeCD = new String(Utils.getBytes(dataBuffer, idx, 20)).trim();
			logger.info("STR CD=" + storeCD);
			idx += 20;
			// GW ID
			int gwID = ByteUtils.toUnsignedShort(Utils.getBytes(dataBuffer, idx, 2));
			logger.info("GW ID=" + gwID);
			
			// Validation
			byte result = NORMAL;
			
			// 접속된 모든 client에게 notify
			byte[] data = Utils.getBytes(readBuffer, 0, readBytes);
			
			boolean flag = notifySignControl(storeCD, String.valueOf(gwID), data);
			
			if(!flag){
				result = ERR_EXCEPTION;
			}
			
			// 결과 전송
			short wIdx = 0;
			byte[] writeBuffer = new byte[8];
			writeBuffer[wIdx++] = STX;
			byte[] lenBytes = ByteUtils.toUnsignedShortBytes(0x04);
			writeBuffer[wIdx++] = lenBytes[0];
			writeBuffer[wIdx++] = lenBytes[1];
			writeBuffer[wIdx++] = cmd;
			writeBuffer[wIdx++] = result;
			
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
			
			out.write(writeBuffer);
			out.flush();
			
			doClose();
		}
		
		/**
		 * 냉난방제어 수신 처리
		 * @param cmd
		 * @param dataBuffer
		 * @param readBuffer
		 * @param readBytes
		 * @throws Exception
		 */
		private void doProcessCMD11(byte cmd, byte[] dataBuffer, byte[] readBuffer, int readBytes) throws Exception {
			
			logger.info("냉난방제어 수신");
			
			int idx = 1;
			
			// 매장코드
			String storeCD = new String(Utils.getBytes(dataBuffer, idx, 20)).trim();
			logger.info("STR CD=" + storeCD);
			idx += 20;
			// GW ID
			int gwID = ByteUtils.toUnsignedShort(Utils.getBytes(dataBuffer, idx, 2));
			logger.info("GW ID=" + gwID);
			
			// Validation
			byte result = NORMAL;
			
			// 접속된 모든 client에게 notify
			byte[] data = Utils.getBytes(readBuffer, 0, readBytes);
			
			boolean flag = notifyAirconControl(storeCD, String.valueOf(gwID), data);
			
			if(!flag){
				result = ERR_EXCEPTION;
			}
			
			// 결과 전송
			short wIdx = 0;
			byte[] writeBuffer = new byte[8];
			writeBuffer[wIdx++] = STX;
			byte[] lenBytes = ByteUtils.toUnsignedShortBytes(0x04);
			writeBuffer[wIdx++] = lenBytes[0];
			writeBuffer[wIdx++] = lenBytes[1];
			writeBuffer[wIdx++] = cmd;
			writeBuffer[wIdx++] = result;
			
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
			
			out.write(writeBuffer);
			out.flush();
			
			doClose();
		}
		
		/**
		 * 게이트웨이 재시작 수신 처리
		 * @param cmd
		 * @param dataBuffer
		 * @param readBuffer
		 * @param readBytes
		 * @throws Exception
		 */
		private void doProcessCMD14(byte cmd, byte[] dataBuffer, byte[] readBuffer, int readBytes) throws Exception {
			
			logger.info("게이트웨이 재시작 수신");
			
			int idx = 1;
			
			// 매장코드
			String storeCD = new String(Utils.getBytes(dataBuffer, idx, 20)).trim();
			logger.info("STR CD=" + storeCD);
			idx += 20;
			// GW ID
			int gwID = ByteUtils.toUnsignedShort(Utils.getBytes(dataBuffer, idx, 2));
			logger.info("GW ID=" + gwID);
			
			// Validation
			byte result = NORMAL;
			
			// 접속된 모든 client에게 notify
			byte[] data = Utils.getBytes(readBuffer, 0, readBytes);
			
			boolean flag = notifyGatewayRestart(storeCD, String.valueOf(gwID), data);
			
			if(!flag){
				result = ERR_EXCEPTION;
			}
			
			// 결과 전송
			short wIdx = 0;
			byte[] writeBuffer = new byte[8];
			writeBuffer[wIdx++] = STX;
			byte[] lenBytes = ByteUtils.toUnsignedShortBytes(0x04);
			writeBuffer[wIdx++] = lenBytes[0];
			writeBuffer[wIdx++] = lenBytes[1];
			writeBuffer[wIdx++] = cmd;
			writeBuffer[wIdx++] = result;
			
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
			
			out.write(writeBuffer);
			out.flush();
			
			doClose();
		}
		
		/**
		 * 시스템 리부팅 수신 처리
		 * @param cmd
		 * @param dataBuffer
		 * @param readBuffer
		 * @param readBytes
		 * @throws Exception
		 */
		private void doProcessCMD15(byte cmd, byte[] dataBuffer, byte[] readBuffer, int readBytes) throws Exception {
			
			logger.info("시스템 리부팅 수신");
			
			int idx = 1;
			
			// 매장코드
			String storeCD = new String(Utils.getBytes(dataBuffer, idx, 20)).trim();
			logger.info("STR CD=" + storeCD);
			idx += 20;
			// GW ID
			int gwID = ByteUtils.toUnsignedShort(Utils.getBytes(dataBuffer, idx, 2));
			logger.info("GW ID=" + gwID);
			
			// Validation
			byte result = NORMAL;
			
			// 접속된 모든 client에게 notify
			byte[] data = Utils.getBytes(readBuffer, 0, readBytes);
			
			boolean flag = notifySystemRebooting(storeCD, String.valueOf(gwID), data);
			
			if(!flag){
				result = ERR_EXCEPTION;
			}
			
			// 결과 전송
			short wIdx = 0;
			byte[] writeBuffer = new byte[8];
			writeBuffer[wIdx++] = STX;
			byte[] lenBytes = ByteUtils.toUnsignedShortBytes(0x04);
			writeBuffer[wIdx++] = lenBytes[0];
			writeBuffer[wIdx++] = lenBytes[1];
			writeBuffer[wIdx++] = cmd;
			writeBuffer[wIdx++] = result;
			
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
			
			out.write(writeBuffer);
			out.flush();
			
			doClose();
		}
		
		/**
		 * 게이트웨이상태 요청
		 * @param cmd
		 * @param dataBuffer
		 * @param readBuffer
		 * @param readBytes
		 * @throws Exception
		 */
		private void doProcessCMD18(byte cmd, byte[] dataBuffer, byte[] readBuffer, int readBytes) throws Exception {
			
			logger.info("게이트웨이상태 요청");
			
			int idx = 1;
			
			// 매장코드
			String storeCD = new String(Utils.getBytes(dataBuffer, idx, 20)).trim();
			logger.info("STR CD=" + storeCD);
			idx += 20;
			// GW ID
			int gwID = ByteUtils.toUnsignedShort(Utils.getBytes(dataBuffer, idx, 2));
			logger.info("GW ID=" + gwID);
			
			// client에게 notify
			byte[] data = Utils.getBytes(readBuffer, 0, readBytes);
			
			byte[] result = notifyGetStat(storeCD, String.valueOf(gwID), data);
			
			// 결과 전송
			out.write(result);
			out.flush();
			
			doClose();
		}
	}
}
