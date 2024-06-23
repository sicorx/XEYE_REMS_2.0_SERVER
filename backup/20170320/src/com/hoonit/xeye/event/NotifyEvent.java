package com.hoonit.xeye.event;

import java.util.EventObject;

public class NotifyEvent extends EventObject{
	
	private byte[] data = null;
	
	public NotifyEvent(Object obj){
		super(obj);
	}

	public byte[] getData() {
		return data;
	}

	public void setData(byte[] data) {
		this.data = data;
	}
}