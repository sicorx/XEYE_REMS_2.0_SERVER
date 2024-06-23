package com.hoonit.xeye.event;

import java.util.EventObject;
import java.util.Map;

public class NotifyEvent extends EventObject{
	
	private String storeCD;
	
	private String gwID;
	
	private byte[] data;
	
	private Map<String, String> storeMap;
	
	public NotifyEvent(Object obj){
		super(obj);
	}

	public byte[] getData() {
		return data;
	}

	public void setData(byte[] data) {
		this.data = data;
	}

	public String getStoreCD() {
		return storeCD;
	}

	public void setStoreCD(String storeCD) {
		this.storeCD = storeCD;
	}

	public String getGwID() {
		return gwID;
	}

	public void setGwID(String gwID) {
		this.gwID = gwID;
	}

	public Map<String, String> getStoreMap() {
		return storeMap;
	}

	public void setStoreMap(Map<String, String> storeMap) {
		this.storeMap = storeMap;
	}
}