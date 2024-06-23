set CLASSPATH=.

set CLASSPATH=%CLASSPATH%;./resource/lib/commons-beanutils-1.9.2.jar
set CLASSPATH=%CLASSPATH%;./resource/lib/commons-codec-1.8.jar
set CLASSPATH=%CLASSPATH%;./resource/lib/commons-collections-3.2.1.jar
set CLASSPATH=%CLASSPATH%;./resource/lib/commons-lang.jar
set CLASSPATH=%CLASSPATH%;./resource/lib/commons-logging.jar
set CLASSPATH=%CLASSPATH%;./resource/lib/commons-dbcp.jar
set CLASSPATH=%CLASSPATH%;./resource/lib/commons-pool.jar
set CLASSPATH=%CLASSPATH%;./resource/lib/ezmorph-1.0.6.jar
set CLASSPATH=%CLASSPATH%;./resource/lib/ibatis-2.3.4.726.jar
set CLASSPATH=%CLASSPATH%;./resource/lib/json-lib.jar
set CLASSPATH=%CLASSPATH%;./resource/lib/log4j-1.2.15.jar
set CLASSPATH=%CLASSPATH%;./resource/lib/mysql-connector-java-5.0.8-bin.jar
set CLASSPATH=%CLASSPATH%;./resource/lib/slf4j-api-1.7.7.jar
set CLASSPATH=%CLASSPATH%;./resource/lib/snmp4j-2.4.1.jar
set CLASSPATH=%CLASSPATH%;./resource/lib/xeye-server.jar

java -Xmx4g -Xms4g -XX:+UseParNewGC -XX:+UseConcMarkSweepGC -XX:-CMSParallelRemarkEnabled -classpath "%CLASSPATH%" com.hoonit.xeye.Main