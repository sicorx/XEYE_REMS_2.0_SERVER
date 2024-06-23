package com.hoonit.xeye.net.server.gw;

public class Abortable {

	private boolean done = false;  
    
    public Abortable() {  
        init();  
    }  
      
    public void init() {  
        done = false;  
    }  
      
    public boolean isDone() {  
        return done;  
    }
    
    public void setDone(boolean done){
    	this.done = done;
    }
}
