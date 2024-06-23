package com.hoonit.xeye.db;

import com.ibatis.sqlmap.client.SqlMapClient;
import com.ibatis.sqlmap.client.SqlMapClientBuilder;

import java.io.FileInputStream;
import java.io.IOException;

import org.apache.log4j.Logger;

public class SqlMapManager {
	
	private static SqlMapClient sqlClientMap = null;
	
	private static SqlMapManager instance = new SqlMapManager();
	
	public static SqlMapManager getInstance() {

		if(instance == null){
			instance = new SqlMapManager();
		}

		return instance;
	}
	
	public static synchronized SqlMapClient getSqlMapClient() {
		Logger logger = Logger.getLogger(SqlMapManager.class);
		if (sqlClientMap == null) {
			try {
				sqlClientMap = SqlMapClientBuilder.buildSqlMapClient(new FileInputStream("../resource/sqlmap-config.xml"));
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
			}
		}
		return sqlClientMap;
	}
}
