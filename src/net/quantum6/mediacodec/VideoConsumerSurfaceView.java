package net.quantum6.mediacodec;

import java.nio.ByteBuffer;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceView;

/**
 * Created by PC on 2016/12/20.
 */

public class VideoConsumerSurfaceView extends SurfaceView implements VideoConsumerable
{
    public VideoConsumerSurfaceView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }
	
    public VideoConsumerSurfaceView(Context context)
    {
        super(context);
    }

    @Override
    public boolean isReady()
    {
        return this.getHolder().getSurface().isValid();
    }

	@Override
	public void refreshData()
	{
		//
	}

    @Override
    public void setBuffer(ByteBuffer buffer, int bufferWidth, int bufferHeight)
    {
        //
    }

    @Override
    public boolean isDestroyed()
    {
        return !isReady();
    }

}
