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
import java.util.Vector;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

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
	
	private static final byte STX              = 0x02;
	private static final byte ETX              = 0x03;
	private static final byte NORMAL           = 1;
	private static final byte ERR_STX_ETX      = 2;
	private static final byte ERR_CRC          = 3;
	private static final byte ERR_INVALID_DATA = 4;

	public NotifyServer(){

		try{

			this.corePoolSize = 5;

			this.maximumPoolsize = 100;

			this.blockingQueueSize = 100;

			this.keepAliveTime = 1000 * 5; // 5 seconds

			notifyListener = new Vector<NotifyListener>();

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
	
	public void notifyUpdate(byte[] data){

		NotifyEvent evt = new NotifyEvent(this);
		evt.setData(data);
		
		for(int i = 0; i < notifyListener.size(); i++){
			notifyListener.get(i).notifyUpdate(evt);
		}
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
		
		@Override
		public void run(){
			
			try{

				byte[] readBuffer = new byte[1024];

				int readBytes = in.read(readBuffer, 0, readBuffer.length);

				logger.info(readBytes + " byte readed...");

				if(readBytes > 0){
					
					byte stx = readBuffer[0];
					byte len = readBuffer[1];
					byte etx = readBuffer[len+2];
					
					logger.info("STX=" + ByteUtils.toHexString(stx));
					logger.info("ETX=" + ByteUtils.toHexString(etx));
					
					if(stx == STX && etx == ETX){
					
						logger.info("LEN=" + ByteUtils.toHexString(len));
						
						byte[] dataBuffer = Utils.getBytes(readBuffer, 2, len-2);
						byte[] crcBuffer  = Utils.getBytes(readBuffer, len, 2);
						
						short dataCRC = CRC16.getInstance().getCRC(dataBuffer);
						
						ByteBuffer crcBuffer2 = ByteBuffer.allocate(2);
						crcBuffer2.put(crcBuffer);
						crcBuffer2.flip();
						
						short chekCRC = crcBuffer2.getShort();
						
						logger.info("DATA CRC=" + dataCRC);
						logger.info("CHEC CRC=" + chekCRC);
						
						if(dataCRC == chekCRC){
						
							short idx = 0;
							
							byte cmd = dataBuffer[idx++];
							
							logger.info("CMD=" + ByteUtils.toHexString(cmd));
							
							// 일출/일몰
							if(cmd == 3){
								
								byte t1 = dataBuffer[idx++]; // 일출시간
								byte m1 = dataBuffer[idx++]; // 일출분
								byte t2 = dataBuffer[idx++]; // 일몰시간
								byte m2 = dataBuffer[idx++]; // 일몰분
								
								logger.info("일출시간="+t1);
								logger.info("일출분  ="+m1);
								logger.info("일몰시간="+t2);
								logger.info("일몰분  ="+m2);
								
								// Validation
								byte result = NORMAL;
								
								// 결과 전송
								short wIdx = 0;
								byte[] writeBuffer = new byte[7];
								writeBuffer[wIdx++] = STX;
								writeBuffer[wIdx++] = 4;
								writeBuffer[wIdx++] = cmd;
								writeBuffer[wIdx++] = result;
								
								// CRC
								byte[] crc = Utils.getBytes(writeBuffer, 2, writeBuffer.length-5);
								
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
							}
						}else{
							
							// 결과 전송
							short wIdx = 0;
							byte[] writeBuffer = new byte[7];
							writeBuffer[wIdx++] = STX;
							writeBuffer[wIdx++] = 4;
							writeBuffer[wIdx++] = readBuffer[2];
							writeBuffer[wIdx++] = ERR_CRC;
							
							// CRC
							byte[] crc = Utils.getBytes(writeBuffer, 2, writeBuffer.length-5);
							
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
						}
					}
					// STX와 ETX가 올바르지 않으면 접속 종료
					else{
						
						// 결과 전송
						short wIdx = 0;
						byte[] writeBuffer = new byte[7];
						writeBuffer[wIdx++] = STX;
						writeBuffer[wIdx++] = 4;
						writeBuffer[wIdx++] = readBuffer[2];
						writeBuffer[wIdx++] = ERR_STX_ETX;
						
						// CRC
						byte[] crc = Utils.getBytes(writeBuffer, 2, writeBuffer.length-5);
						
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
					}
					
					if(in != null) in.close();
					if(out != null) out.close();
					if(socket != null) socket.close();
					
					logger.info("The connection is released...");
					
					// 접속된 모든 client에게 notify
					byte[] data = Utils.getBytes(readBuffer, 0, readBytes);
					
	    			notifyUpdate(data);
				}

			}catch(IOException e){
				logger.error(e.getMessage(), e);
			}catch(Exception e){
				logger.error(e.getMessage(), e);
			}finally{

				try{
					
					if(in != null) in.close();
					if(out != null) out.close();
					if(socket != null) socket.close();

				}catch(Exception xe){
					logger.error(xe);
				}
			}
		}
	}
}
