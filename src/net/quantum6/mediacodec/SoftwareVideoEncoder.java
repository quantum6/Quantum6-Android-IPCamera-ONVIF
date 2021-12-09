package net.quantum6.mediacodec;

import net.quantum6.kit.Log;



/**
 * 考虑搞一个通用接口。
 * 
 * @author PC
 *
 */
public final class SoftwareVideoEncoder extends SoftwareVideoCodec
{
    private final static String TAG = SoftwareVideoEncoder.class.getCanonicalName();

    private int mWidth;
    private int mHeight;
    

    public SoftwareVideoEncoder(String lib, int width, int height)
    {
    	super(lib, width, height);
    }
    
    //{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{ MediaCodecable 的接口
    @Override
    public int initCodec()
    {
    	super.initCodec();
    	super.VideoEncoder_init(mWidth, mHeight);
    	return 0;
    }

    @Override
    public final boolean isEncoder()
    {
        return true;
    }

    @Override
    public int process(MediaCodecData inputData, MediaCodecData outputData)
    {
    	super.process(inputData, outputData);
		
    	outputData.mDataSize = VideoEncoder_process(inputData.mDataBuffer, inputData.mDataSize,
    			outputData.mDataBuffer, outputData.mDataBuffer.capacity(), inputData.getInfo());
    	Log.d(TAG, "outputSize="+outputData.mDataSize);
    	return outputData.mDataSize;
    }

    
    @Override
    public void release()
    {
    	super.VideoEncoder_release();
    	super.release();
    }

    //}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}} MediaCodecable 的接口

}
