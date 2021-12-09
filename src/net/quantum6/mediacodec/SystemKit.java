package net.quantum6.mediacodec;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.graphics.ImageFormat;
import android.os.Debug;

public final class SystemKit
{
    /**
     * 关于CPU信息的数组的位置。
     */
    public final static int CPU_APP     = 0;
    public final static int CPU_WORK    = 1;
    public final static int CPU_IDLE    = 2;

    private static long[] 	mCpuTimes 	= new long[3];
    private static float[]	mCpuCost	= new float[2];

    /**
     * 
     * @param value
     * @param digits
     * @return
     */
    public static void appendValueWithBlank(StringBuffer info, int value, int digits)
    {
        int mark = 10;
        for (int i=1; i<digits; i++)
        {
            if (value < mark)
            {
                info.append(' ');
            }
            mark *= 10;
        }
        info.append(value);
    }
    

    /**
     * 这边已经计算好了，省事。
     * @param context
     * @return
     */
    public static String getText(Context context)
    {
    	float[] cpu = getCpuCost();
        String info = "CPU=( "+cpu[0]+"%/   "+cpu[1]+"%)";
        if (null != context)
        {
            float appmem    = (getAppMemory(  context)/100)/10.0F;
            float sysmem    = (getAvailMemory(context)/100)/10.0F;
            info += "\nMEM=( "+appmem+"M/ "+sysmem+"M)";
        }
        return info;
    }

    //{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{ 关于内存占用的代码
    
    /**
     * 单位是KB
     *
     * @param context
     * @return
     */
    private static int getAvailMemory(Context context)
    {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        MemoryInfo outInfo = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(outInfo);
        return (int)(outInfo.availMem/1024);
    }

    /**
     * 单位KB。
     * 具体显示由外面自行处理。
     *
     * @param context
     * @return
     */
    public static int getAppMemory(Context context)
    {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        //获得系统里正在运行的所有进程
        List<RunningAppProcessInfo> runningAppProcessesList = am.getRunningAppProcesses();

        int currentPid = android.os.Process.myPid();
        for (RunningAppProcessInfo runningAppProcessInfo : runningAppProcessesList)
        {
            // 进程ID号
            if (runningAppProcessInfo.pid == currentPid)
            {
                //String processName = runningAppProcessInfo.processName;
                //int uid = runningAppProcessInfo.uid;
                int[] pids = new int[] {currentPid};
                Debug.MemoryInfo memoryInfo = am.getProcessMemoryInfo(pids)[0];
                return memoryInfo.getTotalPss();
            }
        }
        return 0;
    }
    
    //}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}} 关于内存占用的代码

    //{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{ 关于CPU占用的代码
    public static float[] getCpuCost()
    {
    	//当前进程占用CPU/总共占用CPU/空闲CPU
        long[] cpuTimes = new long[3];
        getAppCpuTime(cpuTimes);
        getTotalCpuTime(cpuTimes);

        int idlecpu     = (int)(cpuTimes[SystemKit.CPU_IDLE] - mCpuTimes[SystemKit.CPU_IDLE]);
        int workcpu     = (int)(cpuTimes[SystemKit.CPU_WORK] - mCpuTimes[SystemKit.CPU_WORK]);
        int appcpu      = (int)(cpuTimes[SystemKit.CPU_APP]  - mCpuTimes[SystemKit.CPU_APP]);
        mCpuTimes = cpuTimes;

        long  totalcpu  = idlecpu + workcpu;
        mCpuCost[0] = (1000*appcpu /totalcpu+5)/10.0F;
        mCpuCost[1] = (1000*workcpu/totalcpu+5)/10.0F;
        
        return mCpuCost;
    }
    
    private static String[] getCpuTime(boolean app)
    {
        try
        {
        	String file = "/proc/" + 
        				(app ? android.os.Process.myPid()+"/" : "")
        				+ "stat";
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)), 1000);
            String load = reader.readLine();
            reader.close();
            return load.split(" ");
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
        return null;
    }
    
    private static void getTotalCpuTime(long[] cpu)
    {
        // 获取系统总CPU使用时间
        String[] cpuInfos = getCpuTime(false);
        /**
         * 格式为：cpu user,nice,system,idle,iowait,irq,softirq
         * 拆分后的数组，前两个值分别是cpu,空格。
         * 所以从2开始计算。
         */
        if (cpuInfos != null && cpuInfos.length > 8)
        {
        	cpu[CPU_WORK] =
        					 (Long.parseLong(cpuInfos[2])
        					+ Long.parseLong(cpuInfos[3])
        					+ Long.parseLong(cpuInfos[4])
        					+ Long.parseLong(cpuInfos[6])
        					+ Long.parseLong(cpuInfos[7])
        					+ Long.parseLong(cpuInfos[8]));
        	cpu[CPU_IDLE] =   Long.parseLong(cpuInfos[5]);
        }
    }

    private static void getAppCpuTime(long[] cpu)
    {
    	// 获取应用占用的CPU时间
        String[] cpuInfos = getCpuTime(true);
        
        if (cpuInfos != null && cpuInfos.length > 16)
        {
        	cpu[CPU_APP] =
        				 (Long.parseLong(cpuInfos[13])
                        + Long.parseLong(cpuInfos[14])
                        + Long.parseLong(cpuInfos[15])
                        + Long.parseLong(cpuInfos[16]));
        }
    }
    
    //}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}} 关于CPU占用的代码

    private final static int MIN_ENCODED_BUFFER_SIZE = 32*1024;
    public static int getEncodedBufferSize(int width, int height)
    {
    	int size = width * height;
    	if (size >= MIN_ENCODED_BUFFER_SIZE)
    	{
    		return size;
    	}
    	return MIN_ENCODED_BUFFER_SIZE;
    }
    
    public  final static int PREVIEW_FORMAT = ImageFormat.NV21;
    
    public static int getDecodedBufferSize(int width, int height)
    {
    	return width*height*ImageFormat.getBitsPerPixel(PREVIEW_FORMAT) / 8;
    }
    
    public static int getCodecBufferSize(boolean isEncoder, int width, int height)
    {
    	if (isEncoder)
    	{
    		return getEncodedBufferSize(width, height);
    	}
    	else
    	{
    		return getDecodedBufferSize(width, height);
    	}
    }
}
