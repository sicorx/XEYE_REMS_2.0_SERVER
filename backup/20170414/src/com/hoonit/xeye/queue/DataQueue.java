package com.hoonit.xeye.queue;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.apache.log4j.Logger;

import com.hoonit.xeye.dao.XEyeDAO;
import com.hoonit.xeye.util.ResourceBundleHandler;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class DataQueue extends Thread{

	protected final Logger logger = Logger.getLogger(getClass().getName());
	
	private XEyeDAO xeyeDAO;

	@SuppressWarnings("rawtypes")
	private BlockingQueue queue = null;
	
	@SuppressWarnings("rawtypes")
	public DataQueue(){
		this.queue = new ArrayBlockingQueue(Integer.parseInt(ResourceBundleHandler.getInstance().getString("queue.count")));
	}
	
	public void setXEyeDAO(XEyeDAO xeyeDAO){
    	this.xeyeDAO = xeyeDAO;
    }

	public void doStart(){
		this.start();
		logger.info("Data Queue start...");
	}

	@SuppressWarnings("unchecked")
	public void doAddData(JSONObject jsonObj){

		try{
			this.queue.put(jsonObj);
		}catch(Exception e){
			logger.error(e.getMessage(),e );
		}
	}
	
	private String getLimitRangeData(String val){
		
		if(Double.parseDouble(val) >= 100.0D || Double.parseDouble(val) >= -100.0D){
			return "99.9";
		}
		
		return val;
	}

	@Override
	public void run(){
		
		while(true){

			try{
				
				JSONObject jsonObj = (JSONObject)this.queue.take();
				
				Map<String, String> paramMap = null;
				
				try{
					
					// 매장채널별전력사용량기초자료 등록
					JSONObject pmcObj = jsonObj.getJSONObject("PMC");
					
					paramMap = (Map<String, String>) JSONObject.toBean(pmcObj, Map.class);
					paramMap.put("STR_CD", jsonObj.getString("STR_CD"));
					paramMap.put("REGI_ID", jsonObj.getString("REGI_ID"));
					paramMap.put("YYYYMMDD", jsonObj.getString("YYYYMMDD"));
					paramMap.put("HHMIN", jsonObj.getString("HHMIN"));
					
					xeyeDAO.insertTH_STR_CHN_ELEC_USE_BASE(paramMap);
					
				}catch(Exception e){
					logger.error(e.getMessage(), e);
				}
				
				try{
					
					// 매장REMS장비기초자료 - 테몬
					JSONArray temonArr = jsonObj.getJSONArray("TEMON");
					
					for(short i = 0; i < temonArr.size(); i++){
						
						JSONObject temonObj = temonArr.getJSONObject(i);
						
						//if(!"-9999".equals(temonObj.getString("SENS_TEMP"))){
							
							paramMap = (Map<String, String>) JSONObject.toBean(temonObj, Map.class);
							paramMap.put("STR_CD", jsonObj.getString("STR_CD"));
							paramMap.put("REGI_ID", jsonObj.getString("REGI_ID"));
							paramMap.put("YYYYMMDD", jsonObj.getString("YYYYMMDD"));
							paramMap.put("HHMIN", jsonObj.getString("HHMIN"));
							paramMap.put("TEMON_TYPE", "T");
							
							if(Double.parseDouble(temonObj.getString("SENS_TEMP")) == 0.0D){
								paramMap.put("SENS_TEMP", String.valueOf(String.format("%.1f", Double.parseDouble(temonObj.getString("SENS_TEMP")))));
							}else{
								paramMap.put("SENS_TEMP", String.valueOf(String.format("%.1f", Double.parseDouble(temonObj.getString("SENS_TEMP")) / 100.0D)));
							}
							
							xeyeDAO.insertTH_STR_REMS_DEVICE_BASE(paramMap);
						//}
					}
				}catch(Exception e){
					logger.error(e.getMessage(), e);
				}
				
				try{
					
					// 매장REMS장비기초자료 - 알몬
					JSONArray almonArr = jsonObj.getJSONArray("ALMON");
					
					for(short i = 0; i < almonArr.size(); i++){
						
						JSONObject almonObj = almonArr.getJSONObject(i);
						
						paramMap = (Map<String, String>) JSONObject.toBean(almonObj, Map.class);
						paramMap.put("STR_CD", jsonObj.getString("STR_CD"));
						paramMap.put("REGI_ID", jsonObj.getString("REGI_ID"));
						paramMap.put("YYYYMMDD", jsonObj.getString("YYYYMMDD"));
						paramMap.put("HHMIN", jsonObj.getString("HHMIN"));
						paramMap.put("TEMON_TYPE", "A");
						paramMap.put("SENS_TEMP", almonObj.getString("SENS_TEMP"));
						
						xeyeDAO.insertTH_STR_REMS_DEVICE_BASE(paramMap);
					}
					
				}catch(Exception e){
					logger.error(e.getMessage(), e);
				}
				
				try{
					
					// 매장REMS장비기초자료 - 무선티센서
					if(Short.parseShort(jsonObj.getString("BLE_CNT")) > 0){
						
						JSONArray bleArr = jsonObj.getJSONArray("BLE");
						
						for(short i = 0; i < Short.parseShort(jsonObj.getString("BLE_CNT")); i++){
							
							JSONObject bleObj = bleArr.getJSONObject(i);
							
							// 1로 시작하지 않으면 냉장(2), 내동(3) 온습도계이다
							if(!bleObj.getString("BLE_GUBUN").startsWith("1")){
								
								short bleIdx = Short.parseShort(bleObj.getString("BLE_GUBUN").substring(1));
								
								paramMap = new HashMap<String, String>();
								paramMap.put("STR_CD", jsonObj.getString("STR_CD"));
								paramMap.put("REGI_ID", jsonObj.getString("REGI_ID"));
								paramMap.put("YYYYMMDD", jsonObj.getString("YYYYMMDD"));
								paramMap.put("HHMIN", jsonObj.getString("HHMIN"));
								paramMap.put("TEMON_TYPE", "Z");
								paramMap.put("PORT_NO", String.valueOf(bleIdx));
								
								if(Double.parseDouble(bleObj.getString("BLE_SENSE_TEMP")) == 0.0D){
									paramMap.put("SENS_TEMP", String.valueOf(String.format("%.1f", Double.parseDouble(bleObj.getString("BLE_SENSE_TEMP")))));
								}else{
									paramMap.put("SENS_TEMP", String.valueOf(String.format("%.1f", Double.parseDouble(bleObj.getString("BLE_SENSE_TEMP")) / 100.0D)));
								}
								
								xeyeDAO.insertTH_STR_REMS_DEVICE_BASE(paramMap);
							}
						}
					}
				}catch(Exception e){
					logger.error(e.getMessage(), e);
				}
				
				try{
					
					paramMap = new HashMap<String, String>();
					paramMap.put("STR_CD", jsonObj.getString("STR_CD"));
					paramMap.put("REGI_ID", jsonObj.getString("REGI_ID"));
					paramMap.put("YYYYMMDD", jsonObj.getString("YYYYMMDD"));
					paramMap.put("HHMIN", jsonObj.getString("HHMIN"));
					
					// 매장환경센서기초자료 - 하콘
					JSONArray haconArr = jsonObj.getJSONArray("HACON");
					
					for(short i = 0; i < haconArr.size(); i++){
						
						JSONObject haconObj = haconArr.getJSONObject(i);
						
						//if(!"-9999".equals(haconObj.getString("SENSE_TEMP"))){
							
							paramMap.put("HACON_STAT_CD_N"+(i+1), haconObj.getString("HACON_STAT_CD"));
							
							if(Double.parseDouble(haconObj.getString("SENSE_TEMP")) == 0.0D){
								paramMap.put("SENSE_TEMP_N"+(i+1), String.valueOf(String.format("%.2f", Double.parseDouble(haconObj.getString("SENSE_TEMP")))));
							}else{
								paramMap.put("SENSE_TEMP_N"+(i+1), String.valueOf(String.format("%.2f", Double.parseDouble(haconObj.getString("SENSE_TEMP")) / 100.0D)));
							}
							
							paramMap.put("SENSE_HUMID_N"+(i+1), haconObj.getString("SENSE_HUMID"));
						//}
					}
					
					// 매장환경센서기초자료 - 유선티센서
					JSONArray tsensorArr = jsonObj.getJSONArray("TSENSOR");
					
					for(short i = 0; i < tsensorArr.size(); i++){
						
						JSONObject tsensorObj = tsensorArr.getJSONObject(i);
						
						//if(!"-9999".equals(tsensorObj.getString("T_SENSOR_SENSE_TEMP"))){
							
							paramMap.put("T_SENSOR_STAT_CD"+(i+1), tsensorObj.getString("T_SENSOR_STAT_CD"));
							
							if(Double.parseDouble(tsensorObj.getString("T_SENSOR_SENSE_TEMP")) == 0.0D){
								paramMap.put("T_SENSOR_SENSE_TEMP_N"+(i+1), String.valueOf(String.format("%.2f", Double.parseDouble(tsensorObj.getString("T_SENSOR_SENSE_TEMP")))));
							}else{
								paramMap.put("T_SENSOR_SENSE_TEMP_N"+(i+1), String.valueOf(String.format("%.2f", Double.parseDouble(tsensorObj.getString("T_SENSOR_SENSE_TEMP")) / 100.0D)));
							}
							
							if(Double.parseDouble(tsensorObj.getString("T_SENSOR_SENSE_HUMID")) == 0.0D){
								paramMap.put("T_SENSOR_SENSE_HUMID_N"+(i+1), String.valueOf(String.format("%.1f", Double.parseDouble(tsensorObj.getString("T_SENSOR_SENSE_HUMID")))));
							}else{
								paramMap.put("T_SENSOR_SENSE_HUMID_N"+(i+1), getLimitRangeData(String.valueOf(String.format("%.1f", Double.parseDouble(tsensorObj.getString("T_SENSOR_SENSE_HUMID")) / 100.0D))));
							}
						//}
					}
					
					// 매장환경센서기초자료 - 무선티센서
					if(Short.parseShort(jsonObj.getString("BLE_CNT")) > 0){
						
						JSONArray bleArr = jsonObj.getJSONArray("BLE");
						
						for(short i = 0; i < Short.parseShort(jsonObj.getString("BLE_CNT")); i++){
							
							JSONObject bleObj = bleArr.getJSONObject(i);
							
							// 1로 시작하면 상온에 놓은 온습도계이다
							if(bleObj.getString("BLE_GUBUN").startsWith("1")){
								
								short bleIdx = Short.parseShort(bleObj.getString("BLE_GUBUN").substring(1));
								
								paramMap.put("T_SENSOR_STAT_CD_N"+(bleIdx), "0");
								
								if(Double.parseDouble(bleObj.getString("BLE_SENSE_TEMP")) == 0.0D){
									paramMap.put("T_SENSOR_SENSE_TEMP_N"+(bleIdx), String.valueOf(String.format("%.2f", Double.parseDouble(bleObj.getString("BLE_SENSE_TEMP")))));
								}else{
									paramMap.put("T_SENSOR_SENSE_TEMP_N"+(bleIdx), String.valueOf(String.format("%.2f", Double.parseDouble(bleObj.getString("BLE_SENSE_TEMP")) / 100.0D)));
								}
								
								if(Double.parseDouble(bleObj.getString("BLE_SENSE_HUMID")) == 0.0D){
									paramMap.put("T_SENSOR_SENSE_HUMID_N"+(bleIdx), String.valueOf(String.format("%.1f", Double.parseDouble(bleObj.getString("BLE_SENSE_HUMID")))));
								}else{
									paramMap.put("T_SENSOR_SENSE_HUMID_N"+(bleIdx), getLimitRangeData(String.valueOf(String.format("%.1f", Double.parseDouble(bleObj.getString("BLE_SENSE_HUMID")) / 100.0D))));
								}
							}
						}
					}
					
					// 매장환경센서기초자료 - 간판상태
					paramMap.put("SIGN_STAT_CD", jsonObj.getString("SIGN_STAT_CD"));
					
					xeyeDAO.insertTH_STR_SENSOR_BASE(paramMap);
					
				}catch(Exception e){
					logger.error(e.getMessage(), e);
				}
				
				try{
					
					// 매장간판상태 등록
					paramMap = new HashMap<String, String>();
					paramMap.put("STR_CD", jsonObj.getString("STR_CD"));
					paramMap.put("REGI_ID", jsonObj.getString("REGI_ID"));
					paramMap.put("YYYYMMDD", jsonObj.getString("YYYYMMDD"));
					paramMap.put("HHMIN", jsonObj.getString("HHMIN"));
					paramMap.put("SIGN_STAT", jsonObj.getString("SIGN_STAT_CD"));
					
					xeyeDAO.insertTH_STR_SIGN_STATUS(paramMap);
					
				}catch(Exception e){
					logger.error(e.getMessage(), e);
				}
				
				try{
					
					// 매장인버터허브상태
					short hubCnt = Short.parseShort(jsonObj.getString("HUB_CNT"));
					
					JSONArray hubArr = jsonObj.getJSONArray("HUB");
					
					StringBuilder sb = new StringBuilder();
					
					for(short i = 0; i < hubCnt; i++){
						
						JSONObject hubObj = hubArr.getJSONObject(i);
						
						paramMap = new HashMap<String, String>();
						paramMap.put("STR_CD", jsonObj.getString("STR_CD"));
						paramMap.put("REGI_ID", jsonObj.getString("REGI_ID"));
						paramMap.put("YYYYMMDD", jsonObj.getString("YYYYMMDD"));
						paramMap.put("HHMIN", jsonObj.getString("HHMIN"));
						
						paramMap.put("PORT_NO", hubObj.getString("ADDR")); // Address
						paramMap.put("FW_VER", hubObj.getString("FW_VER")); // F/W 버전
						paramMap.put("MODEL", hubObj.getString("MODEL")); // 모델
						
						sb.setLength(0);
						sb.append(hubObj.getString("ALARM_MASK1"));
						sb.append(hubObj.getString("ALARM_MASK2"));
						sb.append(hubObj.getString("ALARM_MASK3"));
						sb.append(hubObj.getString("ALARM_MASK4"));
						sb.append(hubObj.getString("ALARM_MASK5"));
						sb.append(hubObj.getString("ALARM_MASK6"));
						sb.append(hubObj.getString("ALARM_MASK7"));
						sb.append(hubObj.getString("ALARM_MASK8"));
						
						paramMap.put("ALARM_MASK", sb.toString()); // 알람 MASK1
						
						sb.setLength(0);
						sb.append(hubObj.getString("ALARM_SP1"));
						sb.append(hubObj.getString("ALARM_SP2"));
						sb.append(hubObj.getString("ALARM_SP3"));
						sb.append(hubObj.getString("ALARM_SP4"));
						sb.append(hubObj.getString("ALARM_SP5"));
						sb.append(hubObj.getString("ALARM_SP6"));
						sb.append(hubObj.getString("ALARM_SP7"));
						sb.append(hubObj.getString("ALARM_SP8"));
						
						paramMap.put("ALARM_SP", sb.toString()); // 알람1
						
						paramMap.put("ERR_CD", hubObj.getString("ERR_CD")); // 에러코드
						paramMap.put("OPER_MODE", hubObj.getString("OPER_MODE")); // 운전모드
						paramMap.put("SENS_TEMP", hubObj.getString("SENS_TEMP")); // 실내온도
						paramMap.put("SENS_TEMP_CONF", hubObj.getString("SENS_TEMP_CONF")); // 실내설정온도
						paramMap.put("SENS_TEMP_OUT", hubObj.getString("SENS_TEMP_OUT")); // 실외기온도
						paramMap.put("MAX_TEMP_ALARM_YN", hubObj.getString("MAX_TEMP_ALARM_YN")); // 고온경보사용유무
						paramMap.put("MAX_TEMP", hubObj.getString("MAX_TEMP")); // 고온경보설정온도
						paramMap.put("MIN_TEMP_ALARM_YN", hubObj.getString("MIN_TEMP_ALARM_YN")); // 저온경보사용유무
						paramMap.put("MIN_TEMP", hubObj.getString("MIN_TEMP")); // 저온경보설정온도
						paramMap.put("DEFROST_SENS_TEMP", hubObj.getString("DEFROST_SENS_TEMP")); // 실내제상온도
						paramMap.put("DEFW_OWNER", hubObj.getString("DEFW_OWNER")); // 제상/제수 동작기준
						paramMap.put("DEFROST_TEMP_CONF", hubObj.getString("DEFROST_TEMP_CONF")); // 제상복귀온도설정
						paramMap.put("DEFROST_TERM", hubObj.getString("DEFROST_TERM")); // 제상간격
						paramMap.put("DEFROST_DELAY", hubObj.getString("DEFROST_DELAY")); // 제상시간
						paramMap.put("DEWATER_DELAY", hubObj.getString("DEWATER_DELAY")); // 제수시간
						
						xeyeDAO.insertTH_STR_INV_HUB_STATUS(paramMap);
					}
					
				}catch(Exception e){
					logger.error(e.getMessage(), e);
				}
			}catch(Exception e){
				logger.error(e.getMessage(), e);
			}
		}
	}
}
