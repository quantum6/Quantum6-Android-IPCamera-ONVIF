package net.quantum6.rtsp.onvif;


public interface IPCamera {
	
	//Initial the network connection of the IP camera.
	public void IPCamera_Init();
	
	//Get the unique identification for this IP camera.
	public int getId();
	
	//Get the IP camera name set by user in smart tv system ui.
	public String getName();
	
	public String getIpAddress();
	
	//Get the connect status of this IP camera.
	public boolean isOnline();
	
	//Release the network connection of the IP camera.
	public void release();
}
