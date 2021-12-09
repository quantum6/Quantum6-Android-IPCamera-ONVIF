package net.quantum6.camera;

import java.util.List;

import net.quantum6.mediacodec.VideoConsumerable;

import android.content.Context;




public abstract class CameraAbstract
{
	//private final static String TAG = CameraAbstract.class.getCanonicalName();
	protected final static boolean useHardwareDecoder = true;
	protected final static boolean useHardwareEncoder = true;


	protected static CameraDataCallback mListener;
	
	public final static int DEFAULT_VIDEO_FPS 		= 15;
	//尽可能使用16:9
	public final static int DEFAULT_VIDEO_WIDTH 	= 640;
	public final static int DEFAULT_VIDEO_HEIGHT 	= 360;
	

	protected int mFps                        = DEFAULT_VIDEO_FPS;
	protected int mWidth                      = DEFAULT_VIDEO_WIDTH;
	protected int mHeight                     = DEFAULT_VIDEO_HEIGHT;
	protected int mBitRate = 500*1000;
	protected int mGop;
	
	public void setDataListener(CameraDataCallback listener)
	{
		mListener = listener;
	}
	
	
	public interface CameraDataCallback
	{
		public void onCameraDataArrived(byte[] data, int size, boolean encoded);
	}
	
	//{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{
	
	public abstract void startCameraPreview();
	
	public abstract void startPreview();
	
	public abstract void pausePreview();
	
	public abstract void stopPreview();
	
	public abstract void releaseCamera();
	
	public abstract boolean sameSize(int width, int height);
	
	public abstract Size getPreviewSize();
	
	public abstract void setParameters(int width, int height, int fps, int bitrate, int gop, Object surface);
	
	public abstract List<Size> getSupportedSizes();

	public abstract VideoConsumerable getPreviewView(Context context, VideoConsumerable view, String lib, int width, int height, int fps, int bitrate, int gop);

	//}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}
}
