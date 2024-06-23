package com.hoonit.xeye.util;

import java.util.BitSet;
import java.util.Calendar;

public class Utils {
	
	public static byte[] getBytes(byte[] src, int offset, int length){
        byte dest[] = new byte[length];
        System.arraycopy(src, offset, dest, 0, length);
        return dest;
    }
	
	public static String nullToString(String source){
		
		if(source == null){
			return "";
		}else{
			return source;
		}
	}
	
	public static int getBitToByteSize(int bitSize){
		
		BitSet bits = new BitSet(bitSize);
		
		for(int i = 0; i < bitSize; i++){
			bits.set(i);
		}
		
		byte[] bytes = new byte[(bits.length() + 7) / 8];
	    for (int i=0; i<bits.length(); i++) {
	        if (bits.get(i)) {
	            bytes[bytes.length-i/8-1] |= 1<<(i%8);
	        }
	    }
	    
	    return bytes.length;
	}
	
	public static String convertByteToBit(byte b){
		
		StringBuffer result = new StringBuffer(3);
		result.append(Integer.toString((b & 0xF0) >> 4, 16));
		result.append(Integer.toString(b & 0x0F, 16));
		
		return hexToBin(result.toString());
	}
	
	//convert hex to bin
    private static String hexToBin(String hex) {
        hex = hex.toUpperCase();
        if (!isHexaDecimal(hex)) {
            return "00000000";
        }
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < hex.length(); i++) {
            if (hex.charAt(i) == '0') {
                sb.append("0000");
            } else if (hex.charAt(i) == '1') {
                sb.append("0001");
            } else if (hex.charAt(i) == '2') {
                sb.append("0010");
            } else if (hex.charAt(i) == '3') {
                sb.append("0011");
            } else if (hex.charAt(i) == '4') {
                sb.append("0100");
            } else if (hex.charAt(i) == '5') {
                sb.append("0101");
            } else if (hex.charAt(i) == '6') {
                sb.append("0110");
            } else if (hex.charAt(i) == '7') {
                sb.append("0111");
            } else if (hex.charAt(i) == '8') {
                sb.append("1000");
            } else if (hex.charAt(i) == '9') {
                sb.append("1001");
            } else if (hex.charAt(i) == 'A') {
                sb.append("1010");
            } else if (hex.charAt(i) == 'B') {
                sb.append("1011");
            } else if (hex.charAt(i) == 'C') {
                sb.append("1100");
            } else if (hex.charAt(i) == 'D') {
                sb.append("1101");
            } else if (hex.charAt(i) == 'E') {
                sb.append("1110");
            } else if (hex.charAt(i) == 'F') {
                sb.append("1111");
            }
        }
        
        return sb.toString();
    }
    
    //is input represents a Hexadecimal number
    private static boolean isHexaDecimal(String bin) {
        return bin.toUpperCase().matches("[0-9|A-F]+");
    }
    
    public static String reverseString(String s) {
		return ( new StringBuffer(s) ).reverse().toString();
	}
    
    public static String getTime(){
    	
    	Calendar cal = Calendar.getInstance();
		
		int year   = cal.get(Calendar.YEAR);
		int month  = cal.get(Calendar.MONTH) + 1;
		int date   = cal.get(Calendar.DATE);
		int time   = cal.get(Calendar.HOUR_OF_DAY); // 0~23
		int minute = cal.get(Calendar.MINUTE);
		int second = cal.get(Calendar.SECOND);
		
		String strYear   = String.valueOf(year);
		String strMonth  = (month > 9)  ? String.valueOf(month)  : String.valueOf("0" + month);
		String strDate   = (date > 9)   ? String.valueOf(date)   : String.valueOf("0" + date);
		String strTime   = (time > 9)   ? String.valueOf(time)   : String.valueOf("0" + time);
		String strMinute = (minute > 9) ? String.valueOf(minute) : String.valueOf("0" + minute);
		String strSecond = (second > 9) ? String.valueOf(second) : String.valueOf("0" + second);
		
		return strYear + "-" + strMonth + "-" + strDate + "-" + strTime + "-" + strMinute + "-" + strSecond;
    }
}
