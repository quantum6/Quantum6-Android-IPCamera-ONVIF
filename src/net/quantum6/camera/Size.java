package net.quantum6.camera;

/**
 * 我也不想多这样的类，
 * Camera.Size，不能new
 * android.util.Size，太新用不上。
 * 
 * @author PC
 *
 */
public final class Size
{
	public int width;
	public int height;
	
	public Size(int w, int h)
	{
		width = w;
		height= h;
	}
	
}
