package com.hoonit.xeye.event;

public interface NotifyListener {

	/**
	 * 매장정보 통보
	 * @param e
	 */
	public void notifyStoreInfo(NotifyEvent e);
	
	/**
	 * 패치파일 업데이트 통보
	 * @param file
	 */
	public void notifyFileUpdate(NotifyEvent e);
	
	/**
	 * 일출일몰시간 통보
	 */
	public void notifySunRisetInfo(NotifyEvent e);
	
	/**
	 * 냉난방정책 통보
	 */
	public void notifyHACPolicyInfo(NotifyEvent e);
	
	/**
	 * 냉난방 권장온도 통보
	 */
	public void notifyHACTempInfo(NotifyEvent e);
	
	/**
	 * 간판제어 통보
	 */
	public void notifySignControl(NotifyEvent e);
}
