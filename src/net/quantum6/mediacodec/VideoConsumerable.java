package net.quantum6.mediacodec;

import java.nio.ByteBuffer;

/**
 * Created by PC on 2016/12/20.
 */

public interface VideoConsumerable
{
    boolean isReady();

    void setBuffer(ByteBuffer buffer, int bufferWidth, int bufferHeight);

    void refreshData();
    
    boolean isDestroyed();
}
