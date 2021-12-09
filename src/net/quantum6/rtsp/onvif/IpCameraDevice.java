package net.quantum6.rtsp.onvif;

import java.util.UUID;

import net.quantum6.kit.Log;
import net.quantum6.rtsp.onvif.HttpSoap.OnHttpSoapListener;



public class IpCameraDevice implements IPCamera {
	private final static String TAG = IpCameraDevice.class.getCanonicalName();
			
	public UUID uuid;
	public String serviceURL;
	private int id;
	private String name;
	private String ipAddr;
	private boolean isOnline = false;
	public String rtspUri = "";
	//private SurfaceView mSurfaceView;
	private OnSoapDoneListener mListener;
	
	public int width;
	public int height;
	public int rate;

	public String username;
	public String password;

	public IpCameraDevice(UUID uuid, String serviceURL) {
		this.uuid = uuid;
		this.serviceURL = serviceURL;
	}

	public void setSecurity(String username, String password) {
		this.username = username;
		this.password = password;
	}

	public void setProperties(int width, int height, int rate) {
		this.width = width;
		this.height = height;
		this.rate = rate;
	}

	public void setId(int id) {
		this.id = id;
	}

	public void setIpAddr(String ipAddr) {
		this.ipAddr = ipAddr;
	}

	public void setOnline(boolean isOnline) {
		this.isOnline = isOnline;
	}

	@Override
	public void IPCamera_Init() {
		if (this.isOnline) {
			HttpSoap soap = new HttpSoap(this);
			soap.setOnHttpSoapListener(listener);
			soap.start();
		}
	}
	
	public void setOnSoapDoneListener(OnSoapDoneListener listener) {
		mListener = listener;
	}

	private OnHttpSoapListener listener = new OnHttpSoapListener() {
		@Override
		public void OnHttpSoapDone(IpCameraDevice camera, String uri, boolean success) {
			if (success) {
				rtspUri = uri.substring(0, uri.indexOf("//") + 2) + camera.username
						+ ":" + camera.password + "@"
						+ uri.substring(uri.indexOf("//") + 2);
				Log.d(TAG, "rtspUri="+rtspUri);
			}
			if (mListener != null) {
				mListener.onSoapDone(IpCameraDevice.this, success);
			}
		}
	};

	@Override
	public int getId() {
		return this.id;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public String getIpAddress() {
		return this.ipAddr;
	}

	@Override
	public boolean isOnline() {
		return this.isOnline;
	}

	@Override
	public void release() {
	}
	

	public interface OnSoapDoneListener {
		void onSoapDone(IpCameraDevice device, boolean success);
	}	
}
