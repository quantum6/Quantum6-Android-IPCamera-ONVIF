package net.quantum6.camera;

import android.hardware.Camera;

public final class CameraManager
{

	private static boolean isIpCameraTesting = false;
	
	/**
	 * 这个函数的意思是否是强制使用 IP Camera。
	 * 不过有没有，那是另外判断了。
	 * 
	 * @return
	 */
	public static boolean usingIpCamera()
	{
		return (isIpCameraTesting || Camera.getNumberOfCameras() <= 0);
	}
	
	//提前调用。
	public static void prepare()
	{
		if (usingIpCamera())
		{
			CameraIP.findCamera();
		}
	}
	
	public static CameraAbstract openCamera()
	{
		if (!usingIpCamera())
		{
			return CameraNormal.openCamera();
		}
		else if (CameraIP.getNumberOfCameras() > 0)
		{
			return CameraIP.openCamera();
		}
		return null;
	}
	
}
