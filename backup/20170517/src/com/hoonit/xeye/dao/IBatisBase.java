package com.hoonit.xeye.dao;

import com.ibatis.sqlmap.client.SqlMapClient;
import com.ibatis.sqlmap.client.SqlMapClientBuilder;

import java.io.FileInputStream;
import java.io.IOException;

import org.apache.log4j.Logger;

public abstract class IBatisBase {
	
	private static final Logger logger = Logger.getLogger(IBatisBase.class);
	
	protected static SqlMapClient sqlMapper;
	
	static {
		
		try{
			
			sqlMapper = SqlMapClientBuilder.buildSqlMapClient(new FileInputStream("resource/sqlmap-config.xml"));
			
		}catch(IOException e){
			logger.error(e.getMessage(), e);
		}
	}
}
