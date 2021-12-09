/* Copyright (C) 2010-2011, Mamadou Diop.
*  Copyright (C) 2011, Doubango Telecom.
*
* Contact: Mamadou Diop <diopmamadou(at)doubango(dot)org>
*	
* This file is part of imsdroid Project (http://code.google.com/p/imsdroid)
*
* imsdroid is free software: you can redistribute it and/or modify it under the terms of 
* the GNU General Public License as published by the Free Software Foundation, either version 3 
* of the License, or (at your option) any later version.
*	
* imsdroid is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
* without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  
* See the GNU General Public License for more details.
*	
* You should have received a copy of the GNU General Public License along 
* with this program; if not, write to the Free Software Foundation, Inc., 
* 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/
package net.quantum6.camera;


import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import net.quantum6.mediacodec.AndroidVideoEncoder;
import net.quantum6.kit.Log;
import net.quantum6.mediacodec.MediaCodecData;
import net.quantum6.fps.FpsController;
import net.quantum6.mediacodec.VideoConsumerSurfaceView;
import net.quantum6.mediacodec.VideoConsumerable;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.view.SurfaceHolder;



public class CameraNormal extends CameraAbstract implements android.hardware.Camera.PreviewCallback
{
	private static final String TAG = CameraNormal.class.getCanonicalName();
	
	private static final int   CALLABACK_BUFFERS_COUNT 	= 1;

	private static final int FPS_MIN 				   	= 15;
	private static final int FPS_MAX 				   	= 60;
	private static final int FPS_DEFAULT			   	= 30;
	
	private static final float MIN_SCREEN_RATIO    		= 1.5f;
	
	
	//这些静态要改掉。
	private static int     cameraIndex;
	private static boolean useFrontFacingCamera = true;
	private static Camera  instance;
	private static CameraAbstract  mCameraNormal;
	
	private static int mFrameWidth; // camera picture output width
	private static int mFrameHeight; // camera picture output height
	

	private static boolean hasParam                = false;
	
    public  static List<Size>          mSupportedSizes;
    public  static Size                mCurrentSize;
    private static SurfaceHolder mSurfaceHolder;

	private static AndroidVideoEncoder mVideoEncoder;
	private static MediaCodecData mInputData;
	private static MediaCodecData mOutputData;
	
	private FpsController  mFpsController;
	

	private CameraNormal()
	{
		//
	}
	
    /**
     * 尽量避免使用4:3的怪异分辨率
     * @param previewSizes
     * @param settingsWidth
     * @param settingsHeight
     * @param isWide
     * @return
     */
    private static Size selectSize(List<Size> previewSizes, int settingsWidth, int settingsHeight, boolean isWide)
    {
        final boolean setWide = ((1f * settingsWidth / settingsHeight) >= MIN_SCREEN_RATIO);
        for (int i = previewSizes.size()-1; i >=0; i--)
        {
            Size size = previewSizes.get(i);
            Log.d(TAG, "selectPictureSize() i="+i+ " ("+size.width+", "+size.height+")"+settingsWidth+", "+settingsHeight+", "+isWide);
            if (size.width <= settingsWidth && size.height <= settingsHeight)
            {
                //非宽模式
                if (!isWide || !setWide)
                {
                    Log.d(TAG, "selectPictureSize() setting=("+settingsWidth+", "+settingsHeight+") normal=("+size.width+", "+size.height+")");
                    return size;
                }
                //宽模式
                if ((1f * size.width / size.height) >= MIN_SCREEN_RATIO)
                {
                    Log.d(TAG, "selectPictureSize() setting=("+settingsWidth+", "+settingsHeight+") wide=("+size.width+", "+size.height+")");
                    return size;
                }
            }
        }
        return null;
    }

    /**
     * 为了确保正确性，强制排序一次。
     * @author PC
     *
     */
    private static class SizeComparator implements Comparator<Size>
    {
        @Override
        public int compare(Size arg0, Size arg1)
        {
            if (arg0.width > arg1.width)
            {
                return 1;
            }
            if (arg0.width < arg1.width)
            {
                return -1;
            }
            if (arg0.height == arg1.height)
            {
                return 0;
            }
            return (arg0.height > arg1.height) ? 1 : -1;
        }
    }
    
    private static Size getCameraBestPreviewSize(final int width, final int height){
    
        Collections.sort(mSupportedSizes, new SizeComparator());
    
        Size bestSize = selectSize(mSupportedSizes, width, height, true);
        if (null == bestSize)
        {
            bestSize  = selectSize(mSupportedSizes, width, height, false);
        }
    
        // V3上的方式没有能找到合适的，那就用这里原来的选择分辨率方案。
        if (null == bestSize)
        {
            Size minSize  = null;
            int  minScore = Integer.MAX_VALUE;
            for (Size size : mSupportedSizes)
            {
                final int score = Math.abs(size.width - width) + Math.abs(size.height - height);
                if (minScore > score)
                {
                    minScore = score;
                    minSize = size;
                }
            }
            bestSize = minSize;
        }
        if (null != bestSize)
        {
            mCurrentSize = bestSize;
        }
    
        return bestSize;
    }


    /**
     * 后置，双摄，这样可以吗？
     * 
     * @return
     */
	public static boolean isFrontFacingCameraEnabled()
	{
		return Camera.getNumberOfCameras() > 1 && CameraNormal.useFrontFacingCamera;
	}
    
	public static void useFrontFacingCamera(boolean front)
	{
        CameraNormal.useFrontFacingCamera = front;
    }
    
	public synchronized static CameraAbstract openCamera()
	{
		//到了这里，不可能为0
        final int cameraCount = Camera.getNumberOfCameras();
        if (0 == cameraCount)
        {
        	return null;
        }

        if (CameraNormal.mCameraNormal != null)
        {
            return CameraNormal.mCameraNormal;
        }

        mCameraNormal = new CameraNormal();
        
		final CountDownLatch countDown = new CountDownLatch(1);
		//在深圳一台电视上，到了视频界面死锁。分析是因为在主线程打开摄像头。所以这里加一个线程
		Thread t = new Thread()
		{
			public void run()
			{
                Log.d(TAG, "openCamera: CameraNormal.instance=111="+CameraNormal.instance);
		        //先使用前置摄像头
		        if (cameraCount > 1)
		        {
		            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
		            for (cameraIndex = 0; cameraIndex < cameraCount; cameraIndex++)
		            {
		                Camera.getCameraInfo(cameraIndex, cameraInfo);
		                if (   ( useFrontFacingCamera && cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
		                    || (!useFrontFacingCamera && cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK ))
		                {
		                    try
		                    {
		                        CameraNormal.instance = Camera.open(cameraIndex);
		                        break;
		                    }
		                    catch (Exception e)
		                    {
		                        //Log.e(TAG, "Camera failed to open: " + e.getLocalizedMessage());
		                    }
		                }
		            }
		        }
                Log.d(TAG, "openCamera: CameraNormal.instance=222="+CameraNormal.instance);
                if (null == CameraNormal.instance)
                {
                    try
                    {
                        CameraNormal.instance = Camera.open(0);
                    }
                    catch (Exception e)
                    {
                        //
                    }
                }
                Log.d(TAG, "openCamera: CameraNormal.instance=333="+CameraNormal.instance);
                //有时为空？所以放后面。
                if (null == CameraNormal.instance)
                {
                    try
                    {
                        CameraNormal.instance = Camera.open();
                    }
                    catch (Exception e)
                    {
                        //
                    }
                }
                Log.d(TAG, "openCamera: CameraNormal.instance=444="+CameraNormal.instance);
				countDown.countDown();
			}
		};
		t.setPriority(Thread.MAX_PRIORITY);
		t.start();

		try
		{
			countDown.await();
		}
		catch (InterruptedException e)
		{
			//
		}

		if (hasParam)
		{
			mCameraNormal.setParameters(mCameraNormal.mWidth, mCameraNormal.mHeight,
					mCameraNormal.mFps, mCameraNormal.mBitRate, mCameraNormal.mGop,
					mSurfaceHolder);
		}
		return CameraNormal.mCameraNormal;
	}

	public static CameraAbstract toggleCamera(){
		
	    if (useHardwareEncoder && null != mVideoEncoder)
	    {
	        mVideoEncoder.release();
            mVideoEncoder = null;
	    }

	    int width 			= mCameraNormal.mWidth;
	    int height 			= mCameraNormal.mHeight;
	    int fps 			= mCameraNormal.mFps;
	    int bitrate 		= mCameraNormal.mBitRate;
	    int gop 			= mCameraNormal.mGop;
	    SurfaceHolder holder= mSurfaceHolder;
	    
		if(CameraNormal.instance != null){
			CameraNormal.useFrontFacingCamera = !CameraNormal.useFrontFacingCamera;
			CameraNormal.mCameraNormal.releaseCamera();
			
			CameraNormal.openCamera();
			mCameraNormal.setParameters(width, height, fps, bitrate, gop, holder);
		}
		return CameraNormal.mCameraNormal;
	}

    //{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{ 实现PreviewCallback的接口
	
    @Override
	public void onPreviewFrame(byte[] _data, Camera _camera)
    {
    	if (null == _data || 0 == _data.length)
	    {
	        return;
	    }
	
    	//FPS在这里控制更好一些。
        if (mFpsController.control())
        {
    		//加到缓冲区中。
    	    _camera.addCallbackBuffer(_data);
            return;
        }
        
	    byte[] data2  = _data;
		int data2Size = data2.length;
	    if (null != mListener)
		{
	    	boolean encoded = (null != mVideoEncoder);
			if (encoded)
			{
				mInputData.setData(data2);
				data2Size = mVideoEncoder.process(mInputData, mOutputData);
				data2     = mOutputData.mDataArray;
			}
			mListener.onCameraDataArrived(data2, data2Size, encoded);
		}
	
		//加到缓冲区中。
	    _camera.addCallbackBuffer(_data);
	}
    
    //}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}} 实现PreviewCallback的接口

    //{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{ 实现CameraAbstract的虚函数
    
    @Override
    public boolean sameSize(final int width, final int height)
    {
        if (null == mCurrentSize
                || null == mSupportedSizes
                || null == CameraNormal.instance)
        {
            return false;
        }
        Size size = mCurrentSize;
        getCameraBestPreviewSize(width, height);
        Log.d(TAG, "("+size.width+", "+size.height+") ? ("+mCurrentSize.width+", "+mCurrentSize.height+")");
        return (mCurrentSize.width == size.width && mCurrentSize.height == size.height);
    }
    
    @Override
	public VideoConsumerable getPreviewView(Context context, VideoConsumerable view, String lib, int width, int height, int fps, int bitrate, int gop)
    {
    	if (view == null)
    	{
    		view = new VideoConsumerSurfaceView(context);
    	}

        mWidth   = width;
        mHeight  = height;
        mFps 	 = fps;
        mBitRate = bitrate;
        mGop 	 = gop;
        
        return view;
    }

    /**
     * 1、在龙晶盒子上，设置次数一多会导致帧数下降。
     * 2、在华为手机上，强制设置对焦模式，不仅出错，摄像头都不返回数据了！
     * 
     * 所以采用了这种保险方式。
     */
	private static boolean setCameraFocus(Camera.Parameters parameters, final String find)
	{
		List<String> modes = parameters.getSupportedFocusModes();
		for (int i=0; i<modes.size(); i++)
		{
			String mode = modes.get(i);
			//优先使用这个对焦方式。
			if (null != mode && mode.equals(find))
			{
				parameters.setFocusMode(mode);
				return true;
			}
		}
		return false;
	}

	@Override
	public void setParameters(int width, int height, int fps, int bitrate, int gop, Object surface)
	{
        mWidth  = width;
        mHeight = height;
        mFps = fps;
        mBitRate = bitrate;
        mGop = gop;
        mSurfaceHolder = (SurfaceHolder)surface;

        if (null == CameraNormal.instance)
	    {
	        hasParam = true;
	        return;
	    }
	    
	    hasParam = false;
        Camera.Parameters parameters = CameraNormal.instance.getParameters();
        if (null == parameters)
        {
            return;
        }
		try
		{
            //parameters.setPreviewFormat(PixelFormat.YCbCr_420_SP);
            //parameters.setPreviewFrameRate(CameraNormal.fps);
            
            mCurrentSize = null;
	        // 选择合适的分辨率，参照V3的做法，优先选宽屏。
            List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
            mSupportedSizes = new LinkedList<Size>();
            for (int i=0; i<sizes.size(); i++)
            {
            	Camera.Size s = sizes.get(i);
            	mSupportedSizes.add(new Size(s.width, s.height));
            }

			//取得最佳。
			getCameraBestPreviewSize(width, height);
            parameters.setPreviewSize(mCurrentSize.width , mCurrentSize.height);
            parameters.setPreviewFormat(ImageFormat.NV21);
            
            //如此设置，在龙晶盒子上帧率达标
            parameters.setPreviewFpsRange(FPS_MIN*1000, FPS_MAX*1000);
            parameters.setPreviewFrameRate(FPS_DEFAULT);
            if (!setCameraFocus(parameters, Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO))
            {
            	setCameraFocus(parameters, Camera.Parameters.FOCUS_MODE_AUTO);
            }
			try
			{
				CameraNormal.instance.setParameters(parameters);
			}
			catch(Exception e){
				// FFMpeg converter will resize the video stream
				e.printStackTrace();
			}
			
			//这个如果抛出异常也不用玩了
            CameraNormal.instance.setPreviewDisplay(mSurfaceHolder);
		}
		catch(Exception e){
			CameraNormal.mCameraNormal.releaseCamera();
			Log.e(CameraNormal.TAG, e.toString());
		}
	}

	@Override
    public synchronized void startCameraPreview(){
		try{
			final Size prevSize = CameraNormal.mCurrentSize;
			if (prevSize != null)
			{
                Log.d(TAG, "prevSize=("+prevSize.width+", "+prevSize.height+")");
				mFrameWidth  = prevSize.width;
				mFrameHeight = prevSize.height;
				mWidth       = mFrameWidth;
				mHeight      = mFrameHeight;
			}
			// allocate buffer
			Log.d(TAG, String.format("setPreviewSize [%d x %d ]", mFrameWidth, mFrameHeight));
		} catch(Exception e){
			Log.e(TAG, e.toString());
		}
		
	    int bufferSize = (mFrameWidth * mFrameHeight * 3) >> 1;
        for (int i=0; i<CALLABACK_BUFFERS_COUNT; i++)
		{
        	instance.addCallbackBuffer(new byte[bufferSize]);
		}
        instance.setPreviewCallbackWithBuffer(this);
		
        if (null == mFpsController)
        {
            mFpsController = new FpsController(mFps);
        }

		try{
			instance.startPreview();
		}catch (Exception e) {
			Log.e(TAG, e.toString());
		}
		
		initEncoder();
    }
	
    @Override
	public void startPreview()
    {
    	CameraNormal.instance.startPreview();
    }
	
    @Override
    public void pausePreview()
    {
    	CameraNormal.instance.stopPreview();
    }
    
    @Override
	public void stopPreview()
	{
    	try
    	{
	    	CameraNormal.instance.stopPreview();
	    	CameraNormal.instance.setPreviewCallbackWithBuffer(null);
	    	CameraNormal.instance.setPreviewDisplay(null);
    	}
    	catch (Exception e)
    	{
    		//
    	}

	}
    
    @Override
    public void releaseCamera()
    {
    	if (null == mCameraNormal)
    	{
    		return;
    	}
    	stopPreview();
    	if (null != instance)
    	{
    		instance.release();
    		instance = null;
    	}
        mCameraNormal = null;
        
        mFpsController = null;
        
		//停止编码器
        if (null != mVideoEncoder)
        {
            mVideoEncoder.release();
            mVideoEncoder = null;
        }

    }

    @Override
    public Size getPreviewSize()
    {
    	return mCurrentSize;
    }
    
    @Override
    public List<Size> getSupportedSizes()
    {
    	return this.mSupportedSizes;
    }
    
    //}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}} 实现CameraAbstract的虚函数


	private void initEncoder()
    {
	    /*if (NgnProxyVideoProducer.isSipScreenProject)
	    {
	        return;
	    }*/

        //预览成功之后，加载编码器
        if (useHardwareEncoder && null == mVideoEncoder)
        {
        	mVideoEncoder = new AndroidVideoEncoder(mFrameWidth, mFrameHeight, mFps, mBitRate);
        	//不控制FPS，由这边处理。
        	//mVideoEncoder.controlFps(0);
        	
        	mInputData = new MediaCodecData(mWidth, mHeight);
        	mInputData.getInfo()[0] = mFrameWidth;
        	mInputData.getInfo()[1] = mFrameHeight;
            int size = mFrameWidth*mFrameHeight/2;
            if (size < 128*1024)
            {
            	size = 128*1024;
            }
            mOutputData = new MediaCodecData(mWidth, mHeight);
            mOutputData.setData(new byte[size]);
        }        
    }
	
	//}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}} 实现SurfaceHolder.Callback的接口
	
	/*@Override
	public void surfaceCreated(SurfaceHolder holder)
	{
		//
	}
	
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h)
	{
	    //
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder)
	{
		//
	}*/

	//}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}} 实现SurfaceHolder.Callback的接口
}
