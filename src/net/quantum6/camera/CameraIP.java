package net.quantum6.camera;

import java.nio.ByteBuffer;
import java.util.List;

import net.quantum6.mediacodec.AndroidVideoDecoder;
import net.quantum6.kit.Log;
import net.quantum6.mediacodec.MediaCodecData;
import net.quantum6.mediacodec.MediaCodecable;
import net.quantum6.mediacodec.SoftwareVideoDecoder;
import net.quantum6.mediacodec.SystemKit;
import net.quantum6.mediacodec.VideoConsumerRendererView;
import net.quantum6.mediacodec.VideoConsumerSurfaceView;
import net.quantum6.mediacodec.VideoConsumerable;
import net.quantum6.rtp.clinet.H264Stream.H264StreamCallback;
import net.quantum6.rtsp.onvif.IpCameraDevice;
import net.quantum6.rtsp.onvif.IpCameraFinder;
import net.quantum6.rtsp.onvif.RtspClientOnvif;
import net.quantum6.rtsp.onvif.IpCameraDevice.OnSoapDoneListener;
import net.quantum6.rtsp.onvif.IpCameraFinder.OnCameraFinderListener;

import android.content.Context;
import android.view.Surface;
import android.view.SurfaceHolder;



/**
 * 调用流程：
 * 1、先调用findCamera()，搜索网内的IP摄像头。
 * 2、过一会，调用getNumberOfCameras()。
 * 3、调用openCamera()
 * 
 * 
 * @author PC
 *
 */
public final class CameraIP extends CameraAbstract implements OnCameraFinderListener, OnSoapDoneListener, H264StreamCallback
{
	
	private final static String TAG = CameraIP.class.getCanonicalName();

	private final static String IPCAMERA_USERNAME = "admin";
	private final static String IPCAMERA_PASSWORD = "admin";

	
    private RtspClientOnvif rtspClient;
	
    private MediaCodecable mDecoder;
	private MediaCodecData mInputData;
	private MediaCodecData mOutputData;


	private IpCameraFinder mFinder;
	private IpCameraDevice mIpCamera;
	
	private Surface mSurface            = null;

	private static CameraIP mCamera;

	//先调用found。过一会再调用openC
	public static void findCamera()
	{
		if (null == mCamera)
		{
			mCamera = new CameraIP(); 
		}
		//
	}
	
	public static CameraIP openCamera()
	{
		if (mCamera != null && mCamera.mIpCamera != null)
		{
			return mCamera;
		}
		return null;
	}

	public static int getNumberOfCameras()
	{
		if (mCamera != null && mCamera.mIpCamera != null)
		{
			return 1;
		}
		return 0;
	}
	
	private CameraIP()
	{
		mFinder = new IpCameraFinder();
		mFinder.setOnCameraFinderListener(this);
	}
	
	//{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{ CameraAbstract的虚函数
	
	public void startCameraPreview()
	{
		//
	}
	
	public void startPreview()
	{
		//
	}
	
	public void pausePreview()
	{
		//
	}
	
	public void stopPreview()
	{
		//
	}
	
	public void releaseCamera()
	{
		mSurface = null;
		
		if (mDecoder != null)
		{
			mDecoder.release();
			mDecoder = null;
		}
		
		if (null != rtspClient)
		{
			rtspClient.shutdown();
			rtspClient = null;
		}
		if (null != mFinder)
		{
			mFinder.setOnCameraFinderListener(null);
			mFinder = null;
		}
	}
	
	public boolean sameSize(int width, int height)
	{
		return true;
	}
	
	public Size getPreviewSize()
	{
		return new Size(mWidth, mHeight);
	}
	
	public void setParameters(int width, int height, int fps, int bitrate, int gop, Object surface)
	{
		Log.d(TAG, "setParameters()");
		mWidth = 1280;//width;
		mHeight= 720;//height;
		if (surface instanceof SurfaceHolder)
		{
			mSurface = ((SurfaceHolder)surface).getSurface();
		}
		else
		{
			mSurface = (Surface)surface;
		}
		
		mCamera.mIpCamera.setSecurity(IPCAMERA_USERNAME, IPCAMERA_PASSWORD);
		mCamera.mIpCamera.setOnSoapDoneListener(mCamera);
		mCamera.mIpCamera.IPCamera_Init();
	}
	
	public List<Size> getSupportedSizes()
	{
		return null;
	}
	
	//}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}  CameraAbstract的虚函数

    //{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{ OnCameraFinderListener的接口
	@Override
	public void OnCameraListUpdated()
	{
		for (IpCameraDevice cd2 : mFinder.getCameraList())
		{
			Log.d(TAG, "cd2="+cd2.uuid+", "+cd2.rtspUri+", "+cd2.serviceURL+", "+cd2.username+", "+cd2.password);
			mIpCamera = cd2;
		}
	};
	//}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}} OnCameraFinderListener的接口

    //{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{ OnSoapDoneListener的接口
	@Override
	public void onSoapDone(IpCameraDevice device, boolean success)
	{
		if (success)
		{
			mIpCamera = device;
	        rtspClient = new RtspClientOnvif(mIpCamera.rtspUri);
	        rtspClient.setDataListener(this);

	        rtspClient.start();
			Log.d(TAG, "device.rtspUri="+device.rtspUri);
		}
	}

	//}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}} OnSoapDoneListener的接口
	
	
    //{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{ H264StreamCallback的接口
	
	@Override
	public void onH264StreamStart()
	{
		if (null != mDecoder)
		{
			return;
		}
        if (useHardwareDecoder)
        {
        	mDecoder = new AndroidVideoDecoder(mSurface, mWidth, mHeight);
        }
        else
        {
        	mDecoder = new SoftwareVideoDecoder(mLib, mWidth, mHeight);
        }
    	mDecoder.initCodec();
        mInputData  = new MediaCodecData(mWidth, mHeight);
        mOutputData = new MediaCodecData(mWidth, mHeight);
	}
	
	VideoConsumerable mPreview;
	ByteBuffer mVideoFrame;
	String mLib;
	
	@Override
	public VideoConsumerable getPreviewView(Context context, VideoConsumerable view, String lib, int width, int height, int fps, int bitrate, int gop)
	{
		mWidth = width;
		mHeight= height;
		
		mVideoFrame = ByteBuffer.allocateDirect(SystemKit.getCodecBufferSize(false, mWidth, mHeight));
		if (useHardwareDecoder)
		{
			mPreview = new VideoConsumerSurfaceView(context);
		}
		else
		{
			mPreview = new VideoConsumerRendererView(context);
			((VideoConsumerRendererView)mPreview).init(context, false, mVideoFrame, mWidth, mHeight);
		}

		mLib = lib;
		return mPreview;
	}
	
	private byte[] mSpsData;
	private byte[] mPpsData;
	
	/**
	 * 最后，NAL nal_unit_type中的
	 * 6（SEI）: 增强信息（SEI）
	 * 7（SPS）: 序列参数集（SPS），数据多一些，几十个字节。
	 * 8（PPS）: 图像参数集（PPS），一般是4个字节（。
	 */
	@Override
	public void onH264StreamDataArrived(byte[] buffer, int size)
	{
		Log.d(TAG, "onH264StreamDataArrived() size="+size);
		if (null != mListener)
		{
			int flag = buffer[4] & 0x1F;
			//测试表明，SPS/PPS都是必要的
			if (flag == 0x05 && mSpsData != null && mPpsData != null)
			{
	        	int spsSize = mSpsData.length;
	        	int ppsSize = mPpsData.length;
	        	byte[] temp = new byte[size+spsSize+ppsSize];
	        	System.arraycopy(mSpsData, 0, temp, 0, 		 		 spsSize);
	        	System.arraycopy(mPpsData, 0, temp, spsSize, 		 ppsSize);
	        	System.arraycopy(buffer,   0, temp, spsSize+ppsSize, size);
	        	size += spsSize+ppsSize;
	        	buffer = temp;
			}
			else if (flag == 0x07)
			{
				mSpsData = buffer;
			}
			else if (flag == 0x08)
			{
				mPpsData = buffer;
			}
			mListener.onCameraDataArrived(buffer, size, true);
		}

		onH264StreamStart();
		
		mInputData.setData(buffer, size);
		int result = mDecoder.process(mInputData, mOutputData);
		if (result <= 0)
		{
			return;
		}
		int[] outputInfo = mInputData.getInfo();
		Log.d(TAG, "size="+size+", "+outputInfo[0]+", "+outputInfo[1]+", "+outputInfo[2]+", "+outputInfo[3]);
		
		boolean changed = (outputInfo[MediaCodecData.INDEX_CHANGED] == 1
				|| this.mWidth != outputInfo[MediaCodecData.INDEX_WIDTH]
				|| this.mHeight!= outputInfo[MediaCodecData.INDEX_HEIGHT]); 
		if (changed)
		{
			this.mWidth = outputInfo[MediaCodecData.INDEX_WIDTH];
			this.mHeight= outputInfo[MediaCodecData.INDEX_HEIGHT];
		}
		
		//软解，要自行处理。以后考虑把这部分绘制转移到DECODER中，不知道是否可行。
		if (mDecoder instanceof SoftwareVideoDecoder)
		{
			if (mVideoFrame == null || mVideoFrame.capacity() != result)
			{
				mVideoFrame = ByteBuffer.allocateDirect(result);
				changed = true;
			}
			synchronized (mVideoFrame)
			{
				mVideoFrame.rewind();
				mVideoFrame.put(mOutputData.mDataBuffer.array(), 0, result);
				if (changed)
				{
					mPreview.setBuffer(mVideoFrame, mWidth, mHeight);
				}
			}
			mPreview.refreshData();
		}
		
		outputInfo[MediaCodecData.INDEX_CHANGED] = 0;
	}

	//}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}} H264StreamCallback的接口
}
