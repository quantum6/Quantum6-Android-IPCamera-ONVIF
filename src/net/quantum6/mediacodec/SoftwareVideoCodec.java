package net.quantum6.mediacodec;

import java.nio.ByteBuffer;





/**
 * 考虑搞一个通用接口。
 * 
 * @author PC
 *
 */
public abstract class SoftwareVideoCodec implements MediaCodecable
{
    private final static String TAG = SoftwareVideoCodec.class.getCanonicalName();

    protected int mWidth;
    protected int mHeight;
    private   String mLib;
    
    private static boolean isInited;
    private static boolean hasDecoder;
    private static boolean hasEncoder;

    public SoftwareVideoCodec(String lib, int width, int height)
    {
    	mWidth = width;
    	mHeight = height;
    	
    	mLib = lib;
    }
    

    //{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{
    private   native int      MediaCodec_init(String lib);

    private   native int      MediaCodec_release();
    //}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}

    //{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{
    protected  native int      VideoDecoder_init(int width, int height);

    protected  native int      VideoDecoder_process(ByteBuffer inputData, int inputSize, ByteBuffer outputData, int outputSize, int[] outputInfo);

    protected  native int      VideoDecoder_release();
    
    //}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}
    
    //{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{

    protected  native int      VideoEncoder_init(int width, int height);

    protected  native int      VideoEncoder_process(ByteBuffer inputData, int inputSize, ByteBuffer outputData, int outputSize, int[] outputInfo);

    protected  native int      VideoEncoder_release();

    //}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}

    @Override
    public int initCodec()
    {
    	if (isInited)
    	{
    		return 0;
    	}
    	try
    	{
    		System.load("libquantum6_mediacodec_bridge.so");
    		System.load(mLib);
    		MediaCodec_init(mLib);
    	}
    	catch (Exception e)
    	{
    		e.printStackTrace();
    	}
    	return -1;
    }

    @Override
    public int process(MediaCodecData inputData, MediaCodecData outputData)
    {
    	if (inputData.mDataBuffer == null
    			|| inputData.mDataBuffer.capacity() < inputData.mDataSize)
    	{
    		inputData.mDataBuffer = ByteBuffer.allocateDirect(inputData.mDataSize);
    	}
    	inputData.mDataBuffer.clear();
    	if (null != inputData.mDataArray)
    	{
    		inputData.mDataBuffer.put(inputData.mDataArray, 0, inputData.mDataSize);
    	}
		inputData.mDataBuffer.rewind();
		
		if (outputData.mDataBuffer == null)
		{
			outputData.mDataBuffer = ByteBuffer.allocateDirect(isEncoder() ? SystemKit.getEncodedBufferSize(mWidth, mHeight) : SystemKit.getDecodedBufferSize(mWidth, mHeight));
		}
		outputData.mDataBuffer.clear();
		
		return 0;
    }
    
    @Override
    public void release()
    {
    	if (hasDecoder || hasEncoder)
    	{
    		return;
    	}
    	if (isInited)
    	{
    		isInited = false;
    		MediaCodec_release();
    	}
    }
    
}
