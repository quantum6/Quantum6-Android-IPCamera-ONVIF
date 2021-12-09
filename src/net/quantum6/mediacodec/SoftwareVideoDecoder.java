package net.quantum6.mediacodec;

import net.quantum6.kit.Log;




/**
 * 考虑搞一个通用接口。
 * 
 * @author PC
 *
 */
public final class SoftwareVideoDecoder extends SoftwareVideoCodec
{
    private final static String TAG = SoftwareVideoDecoder.class.getCanonicalName();

    private int mWidth;
    private int mHeight;
    

    public SoftwareVideoDecoder(String lib, int width, int height)
    {
    	super(lib, width, height);
    }
    
    //{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{ MediaCodecable 的接口
    @Override
    public int initCodec()
    {
    	super.initCodec();
    	super.VideoDecoder_init(mWidth, mHeight);
    	return 0;
    }

    @Override
    public final boolean isEncoder()
    {
        return false;
    }

    @Override
    public int process(MediaCodecData inputData, MediaCodecData outputData)
    {
    	super.process(inputData, outputData);

    	outputData.mDataSize = VideoDecoder_process(inputData.mDataBuffer, inputData.mDataSize,
    			outputData.mDataBuffer, outputData.mDataBuffer.capacity(), inputData.getInfo());
    	Log.d(TAG, "outputSize="+outputData.mDataSize);
    	return outputData.mDataSize;
    }

    
    @Override
    public void release()
    {
    	super.VideoDecoder_release();
    	super.release();
    }

    //}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}} MediaCodecable 的接口

}
