package com.hoonit.xeye.event;

public interface NotifyListener {

	public void notifyUpdate(NotifyEvent e);
	
	public void notifyFileTranfer(String fileName);
}
