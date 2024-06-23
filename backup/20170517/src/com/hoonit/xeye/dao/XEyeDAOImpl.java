package com.hoonit.xeye.dao;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class XEyeDAOImpl extends IBatisBase implements XEyeDAO {
	
	/**
	 * 매장게이트웨이마스터 상세내역
	 * @return
	 * @throws Exception
	 */
	@Override
	@SuppressWarnings("unchecked")
	public Map<String, String> selectStrInfoMst(Map<String, String> paramMap) throws SQLException{
		return (Map<String, String>)sqlMapper.queryForObject("XEYE.selectStrInfoMst", paramMap);
	}
	
	/**
	 * 매장게이트웨이마스터 상세내역2
	 * @return
	 * @throws Exception
	 */
	@Override
	@SuppressWarnings("unchecked")
	public Map<String, String> selectStrInfoMst2(Map<String, String> paramMap) throws SQLException{
		return (Map<String, String>)sqlMapper.queryForObject("XEYE.selectStrInfoMst2", paramMap);
	}
	
	/**
	 * 일출일몰시간 상세내역
	 * @return
	 * @throws Exception
	 */
	@Override
	@SuppressWarnings("unchecked")
	public Map<String, String> selectSunRisetDetail(Map<String, String> paramMap) throws SQLException{
		return (Map<String, String>)sqlMapper.queryForObject("XEYE.selectSunRisetDetail", paramMap);
	}
	
	/**
	 * 매장별 일출일몰시간 목록
	 * @return
	 * @throws Exception
	 */
	@Override
	@SuppressWarnings("unchecked")
	public List<Map<String, String>> selectStoreSunRisetList(Map<String, String> paramMap) throws SQLException{
		return sqlMapper.queryForList("XEYE.selectStoreSunRisetList", paramMap);
	}
	
	/**
	 * 냉난방정책월별 목록
	 * @param paramMap
	 * @return
	 * @throws Exception
	 */
	@Override
	@SuppressWarnings("unchecked")
	public List<Map<String, String>> selectHACPolicyMMList(Map<String, String> paramMap) throws SQLException{
		return sqlMapper.queryForList("XEYE.selectHACPolicyMMList", paramMap);
	}
	
	/**
	 * 냉난방권장온도 목록
	 * @param paramMap
	 * @return
	 * @throws Exception
	 */
	@Override
	@SuppressWarnings("unchecked")
	public List<Map<String, String>> selectHACRecommTempList(Map<String, String> paramMap) throws SQLException{
		return sqlMapper.queryForList("XEYE.selectHACRecommTempList", paramMap);
	}
	
	/**
	 * 도어센서 포트번호
	 * @return
	 * @throws Exception
	 */
	@Override
	@SuppressWarnings("unchecked")
	public Map<String, String> selectDoorSensorPortNo(Map<String, String> paramMap) throws SQLException{
		return (Map<String, String>)sqlMapper.queryForObject("XEYE.selectDoorSensorPortNo", paramMap);
	}
	
	/**
	 * 매장게이트웨이마스터 수정
	 * @return
	 * @throws Exception
	 */
	@Override
	public void updateGWMst(Map<String, String> paramMap) throws SQLException{
		sqlMapper.insert("XEYE.updateGWMst", paramMap);
	}
	
	/**
	 * 매장채널별전력사용량기초자료 등록
	 * @return
	 * @throws Exception
	 */
	@Override
	public void insertTH_STR_CHN_ELEC_USE_BASE(Map<String, String> paramMap) throws SQLException{
		sqlMapper.insert("XEYE.insertTH_STR_CHN_ELEC_USE_BASE", paramMap);
	}
	
	/**
	 * 매장채널별전력사용량기초자료 등록
	 * @return
	 * @throws Exception
	 */
	@Override
	public void insertTH_STR_REMS_DEVICE_BASE(Map<String, String> paramMap) throws SQLException{
		sqlMapper.insert("XEYE.insertTH_STR_REMS_DEVICE_BASE", paramMap);
	}
	
	/**
	 * 매장환경센서기초자료 등록
	 * @return
	 * @throws Exception
	 */
	@Override
	public void insertTH_STR_SENSOR_BASE(Map<String, String> paramMap) throws SQLException{
		sqlMapper.insert("XEYE.insertTH_STR_SENSOR_BASE", paramMap);
	}
	
	/**
	 * 매장인버터허브상태 등록
	 * @return
	 * @throws Exception
	 */
	@Override
	public void insertTH_STR_INV_HUB_STATUS(Map<String, String> paramMap) throws SQLException{
		sqlMapper.insert("XEYE.insertTH_STR_INV_HUB_STATUS", paramMap);
	}
	
	/**
	 * 매장간판상태 등록
	 * @return
	 * @throws Exception
	 */
	@Override
	public void insertTH_STR_SIGN_STATUS(Map<String, String> paramMap) throws SQLException{
		sqlMapper.insert("XEYE.insertTH_STR_SIGN_STATUS", paramMap);
	}
	
	/**
	 * 매장간판제어로그 등록
	 * @return
	 * @throws Exception
	 */
	@Override
	public void insertTH_STR_SIGN_CTRL_LOG(Map<String, String> paramMap) throws SQLException{
		sqlMapper.insert("XEYE.insertTH_STR_SIGN_CTRL_LOG", paramMap);
	}
	
	/**
	 * 매장에어컨제어로그 등록
	 * @return
	 * @throws Exception
	 */
	@Override
	public void insertTH_STR_AIRCON_CTRL_LOG(Map<String, String> paramMap) throws SQLException{
		sqlMapper.insert("XEYE.insertTH_STR_AIRCON_CTRL_LOG", paramMap);
	}
	
	/**
	 * 전력피크알람 등록
	 * @return
	 * @throws Exception
	 */
	@Override
	public void insertTH_STR_PEAK_ALARM(Map<String, String> paramMap) throws SQLException{
		sqlMapper.insert("XEYE.insertTH_STR_PEAK_ALARM", paramMap);
	}
	
	/**
	 * 매장환경센서간략자료 등록
	 * @return
	 * @throws Exception
	 */
	@Override
	public void insertTH_STR_SENSOR_BRIEF(Map<String, String> paramMap) throws SQLException{
		sqlMapper.insert("XEYE.insertTH_STR_SENSOR_BRIEF", paramMap);
	}
}
