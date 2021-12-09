package net.quantum6.rtp.clinet;

import android.os.HandlerThread;
import android.os.Handler;
import java.util.concurrent.LinkedBlockingDeque;

import net.quantum6.kit.Log;

/**
 *This class is used to analysis the data from rtp socket , recombine it to video or audio stream
 * 1. get the data from rtp socket
 * 2. put the data into buffer
 * 3. use the thread to get the data from buffer, and unpack it
 */
public abstract class RtpStream {

    private final static String TAG = RtpStream.class.getCanonicalName();
    
    protected final static int TRACK_VIDEO = 0x01;
    protected final static int TRACK_AUDIO = 0x02;

    private Handler mHandler;
    private HandlerThread thread;
    private boolean isStoped;
    private int oldSeqNum;

    protected class StreamPacks {
        public boolean mark;
        public int pt;
        public long timestamp;
        public int sequenceNumber;
        public long Ssrc;
        public byte[] data;
    }

    private static class bufferUnit {
        public byte[] data;
        public int len;
    }

    private static LinkedBlockingDeque<bufferUnit> bufferQueue = new LinkedBlockingDeque<bufferUnit>();

    public RtpStream() {
        thread = new HandlerThread("RTPStreamThread");
        thread.start();
        mHandler = new Handler(thread.getLooper());
        unpackThread();
        isStoped = false;
        oldSeqNum = -1;
    }

    public static void receiveData(byte[] data, int len) {
        bufferUnit tmpBuffer = new bufferUnit();
        tmpBuffer.data = new byte[len];
        System.arraycopy(data,0,tmpBuffer.data,0,len);
        tmpBuffer.len = len;

        try {
            bufferQueue.put(tmpBuffer);
        } catch (InterruptedException e) {
        }
    }

    private void unpackThread() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                bufferUnit tmpBuffer;
                while (!isStoped) {
                    try {
                        tmpBuffer = bufferQueue.take();
                        byte[] buffer = new byte[tmpBuffer.len];
                        System.arraycopy(tmpBuffer.data,0,buffer,0,tmpBuffer.len);
                        unpackData(buffer);
                    } catch (InterruptedException e) {
                        //Log.e(TAG,"wait the new data into the queue..");
                        break;
                    }
                }
                bufferQueue.clear();
            }
        });
    }

    public void stop(){
        isStoped = true;
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        bufferQueue.clear();
        thread.quit();
    }


    protected abstract void recombinePacket(StreamPacks sp);

    private void unpackData(final byte[] buffer) {
        if (buffer == null || buffer.length == 0)
        {
        	return;
        }
        
        if (((buffer[0]&0xFF)>>6) != 2)
        {
    		Log.d(TAG, "ERROR!!!");
            return;
        }

        int size = buffer.length;
        
        if ((buffer[0] & 0x20) > 0)
        {
        	int paddingLength = (buffer[size-1] & 0xFF);
        	if (paddingLength + 12 > size)
        	{
        		Log.d(TAG, "ERROR!!!");
        		return;
        	}
        }

        int numCSRCs = (buffer[0] & 0x0F);
        int payloadOffset = 12 + 4 * numCSRCs;
        if (size < payloadOffset)
        {
        	Log.d(TAG, "ERROR!!!");
    		return;
        }

        if ((buffer[0] & 0x10) > 0) {
            // Header eXtension present.

            if (size < payloadOffset + 4) {
                // Not enough data to fit the basic header, all CSRC entries
                // and the first 4 bytes of the extension header.
            	Log.d(TAG, "ERROR!!!");
        		return;
            }

            int extensionLength = 4 * (buffer[payloadOffset+2] << 8 | buffer[payloadOffset+3]);
            if (size < payloadOffset + 4 + extensionLength)
            {
            	Log.d(TAG, "ERROR!!!");
        		return;
            }

            payloadOffset += 4 + extensionLength;
        }

        StreamPacks tmpStreampack = new StreamPacks();
        tmpStreampack.mark           =  (buffer[1] & 0x80) >> 7 == 1;
        tmpStreampack.pt             =   buffer[1] & 0x7F;
        tmpStreampack.sequenceNumber = ((buffer[2] & 0xFF) <<  8) |  (buffer[3] & 0xFF);
        tmpStreampack.timestamp 	 = ((buffer[4] & 0xFF) << 24) | ((buffer[5] & 0xFF) << 16) | ((buffer[ 6] & 0xFF) << 8) | (buffer[ 7] & 0xFF);
        tmpStreampack.Ssrc 			 = ((buffer[8] & 0xFF) << 24) | ((buffer[9] & 0xFF) << 16) | ((buffer[10] & 0xFF) << 8) | (buffer[11] & 0xFF);
        int dataSize = size-payloadOffset;
        //Log.d(TAG, "size="+size+", payloadOffset="+payloadOffset+", "+dataSize);
        if (dataSize > 0)
        {
        	tmpStreampack.data = new byte[dataSize];
        	System.arraycopy(buffer, payloadOffset, tmpStreampack.data, 0, dataSize);
	        if(oldSeqNum==-1) oldSeqNum = tmpStreampack.sequenceNumber;
	
	        if (tmpStreampack.sequenceNumber - oldSeqNum>1){
	            Log.e(TAG, "RTP lost packet:"+oldSeqNum+"..."+tmpStreampack.sequenceNumber);
	        }
	        oldSeqNum = tmpStreampack.sequenceNumber;
	
	        recombinePacket(tmpStreampack);
        }
    }

}
