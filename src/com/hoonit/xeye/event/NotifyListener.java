package com.hoonit.xeye.event;

public interface NotifyListener {
	
	/**
	 * 매장정보 통보
	 * @param e
	 */
	public boolean notifyStoreInfo(NotifyEvent e);
	
	/**
	 * 패치파일 업데이트 통보
	 * @param file
	 */
	public boolean notifyFileUpdate(NotifyEvent e);
	
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
	public boolean notifySignControl(NotifyEvent e);
	
	/**
	 * 냉난방제어 통보
	 */
	public boolean notifyAirconControl(NotifyEvent e);
	
	/**
	 * 게이트웨이상태 요청 통보
	 */
	public byte[] notifyGetStat(NotifyEvent e);
	
	/**
	 * 게이트웨이 재시작 통보
	 */
	public boolean notifyGatewayRestart(NotifyEvent e);
	
	/**
	 * 시스템 리부팅 통보
	 */
	public boolean notifySystemRebooting(NotifyEvent e);
	
	/**
	 * IF Server 주소변경 통보
	 */
	public byte[] notifyChangeIFServer(NotifyEvent e);
	
	/**
	 * I/O 보드 리셋 통보
	 */
	public boolean notifyResetIOBoard(NotifyEvent e);
}
