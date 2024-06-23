package com.hoonit.xeye.net.snmp;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.snmp4j.CommandResponder;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.CommunityTarget;
import org.snmp4j.MessageDispatcher;
import org.snmp4j.MessageDispatcherImpl;
import org.snmp4j.MessageException;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.mp.MPv1;
import org.snmp4j.mp.MPv2c;
import org.snmp4j.mp.StateReference;
import org.snmp4j.mp.StatusInformation;
import org.snmp4j.security.Priv3DES;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.TcpAddress;
import org.snmp4j.smi.TransportIpAddress;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.AbstractTransportMapping;
import org.snmp4j.transport.DefaultTcpTransportMapping;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.MultiThreadedMessageDispatcher;
import org.snmp4j.util.ThreadPool;

import com.hoonit.xeye.event.NotifyEvent;
import com.hoonit.xeye.event.NotifyListener;
import com.hoonit.xeye.util.ResourceBundleHandler;

import net.sf.json.JSONObject;

public class TrapReceiver implements CommandResponder{
	
	protected final Logger logger = Logger.getLogger(getClass().getName());
	
	private int dispatchPoolSize;
	
	private String community;
	
	private TransportIpAddress address;
	
	private Vector<NotifyListener> trapListener;
	
	public TrapReceiver(){
		
		this.dispatchPoolSize = Integer.parseInt(ResourceBundleHandler.getInstance().getString("dispatcher.pool.size"));
		this.community = ResourceBundleHandler.getInstance().getString("snmp.trap.community");
		this.address = new UdpAddress(ResourceBundleHandler.getInstance().getString("snmp.trap.address"));
		
		this.trapListener = new Vector<NotifyListener>();
	}
	
	public void addTrapListener(NotifyListener obs) {
		trapListener.add(obs);
	}

	public void removeTrapListener(NotifyListener obs) {
		trapListener.remove(obs);
	}
	
	public void notifyTrap(String data){

		/*NotifyEvent trapEvt = new NotifyEvent(this);
		trapEvt.setData(data);
		
		for(int i = 0; i < trapListener.size(); i++){
			trapListener.get(i).notifyTrap(trapEvt);
		}*/
	}

	/**
	 * This method will listen for traps and response pdu's from SNMP agent.
	 */
	public synchronized void listen() throws IOException {
		
		AbstractTransportMapping transport;
		
		if (address instanceof TcpAddress) {
			transport = new DefaultTcpTransportMapping((TcpAddress) address);
		}else{
			transport = new DefaultUdpTransportMapping((UdpAddress) address);
		}

		ThreadPool threadPool = ThreadPool.create("DispatcherPool", this.dispatchPoolSize);
		
		MessageDispatcher mtDispatcher = new MultiThreadedMessageDispatcher(threadPool, new MessageDispatcherImpl());

		// add message processing models
		mtDispatcher.addMessageProcessingModel(new MPv1());
		mtDispatcher.addMessageProcessingModel(new MPv2c());

		// add all security protocols
		SecurityProtocols.getInstance().addDefaultProtocols();
		SecurityProtocols.getInstance().addPrivacyProtocol(new Priv3DES());

		//Create Target
		CommunityTarget target = new CommunityTarget();
		target.setCommunity( new OctetString(this.community));
    
		Snmp snmp = new Snmp(mtDispatcher, transport);
		snmp.addCommandResponder(this);
    
		transport.listen();
		
		logger.info("Trap Listening on " + address);
	}

	/**
	 * This method will be called whenever a pdu is received on the given port specified in the listen() method
	 */
	public synchronized void processPdu(CommandResponderEvent cmdRespEvent) {
		
		PDU pdu = cmdRespEvent.getPDU();
		
		if (pdu != null){
			
			logger.debug("Trap Type=" + pdu.getType());
			logger.debug("Trap Type Name=" + PDU.getTypeString(pdu.getType()));
			
			if((pdu.getType() != PDU.V1TRAP))
			{
				pdu.setErrorIndex(0);
				pdu.setErrorStatus(0);
				pdu.setType(PDU.RESPONSE);
				StatusInformation statusInformation = new StatusInformation();
				StateReference ref = cmdRespEvent.getStateReference();
				
				try
				{
					logger.info(pdu);
					
					Vector<VariableBinding> list = (Vector<VariableBinding>)pdu.getVariableBindings();
					
					// 첫번째는 CMD 구분
					VariableBinding vb1 = list.get(0);
					
					String cmdOID = vb1.getOid().toString();
					cmdOID = cmdOID.substring(0, cmdOID.lastIndexOf("."));
			    	cmdOID = cmdOID.substring(cmdOID.lastIndexOf(".")+1);
			    	
			    	String cmdVal = vb1.getVariable().toString();
			    	
			    	if("1".equals(cmdOID)){
			    		
			    		// 일출/일몰 시간
			    		if("3".equals(cmdVal)){
			    			
			    			VariableBinding vb2 = list.get(1);
			    			VariableBinding vb3 = list.get(2);
			    			VariableBinding vb4 = list.get(3);
			    			VariableBinding vb5 = list.get(4);
			    			
			    			String t1 = vb2.getVariable().toString(); // 일출시간
			    			String m1 = vb3.getVariable().toString(); // 일출분
			    			String t2 = vb4.getVariable().toString(); // 일몰시간
			    			String m2 = vb5.getVariable().toString(); // 일몰분
			    			
			    			JSONObject jsonObject = new JSONObject();
			    			jsonObject.put("cmd", cmdVal);
			    			jsonObject.put("t1", t1);
			    			jsonObject.put("m1", m1);
			    			jsonObject.put("t2", t2);
			    			jsonObject.put("m2", m2);
			    			
			    			notifyTrap(jsonObject.toString());
			    		}
			    		// 패치파일 전송
                        else if("4".equals(cmdVal)){
                        	
                        	JSONObject jsonObject = new JSONObject();
			    			jsonObject.put("cmd", cmdVal);
			    			
			    			notifyTrap(jsonObject.toString());
                        }
			    	}
					
					cmdRespEvent.getMessageDispatcher().returnResponsePdu(cmdRespEvent.getMessageProcessingModel(),
																			cmdRespEvent.getSecurityModel(), 
																			cmdRespEvent.getSecurityName(), 
																			cmdRespEvent.getSecurityLevel(),
																			pdu, 
																			cmdRespEvent.getMaxSizeResponsePDU(), 
																			ref, 
																			statusInformation);
				}catch (MessageException ex){
					logger.error(ex.getMessage(), ex);
				}
			}
		}
	}
}
