package net.quantum6.rtsp.onvif;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.quantum6.kit.Log;
import net.quantum6.rtp.clinet.H264Stream;
import net.quantum6.rtp.clinet.RtpSocket;
import net.quantum6.rtp.clinet.H264Stream.H264StreamCallback;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Base64;


/**
 *
 */
public class RtspClientOnvif {
	
    private final static String TAG = RtspClientOnvif.class.getCanonicalName();
    
    private final static String UserAgent = "Rtsp/0.1";
    private final static int STATE_STARTED = 0x00;
    private final static int STATE_STARTING = 0x01;
    private final static int STATE_STOPPING = 0x02;
    private final static int STATE_STOPPED = 0x03;
    private final static String METHOD_UDP = "udp";
    private final static String METHOD_TCP = "tcp";
    private final static int TRACK_VIDEO = 0x01;
    private final static int TRACK_AUDIO = 0x02;

    private class Parameters {
        public String host;
        public String address;
        public int port;
        public int rtpPort;
        public int serverPort;
    }

    public static class SDPInfo {
    	public SDPInfo()
    	{
    		//
    	}
        public boolean audioTrackFlag;
        public boolean videoTrackFlag;
        public String videoTrack;
        public String audioTrack;
        public String SPS;
        public String PPS;
        public int packetizationMode;
    }

    private Socket mSocket;
    private BufferedReader mBufferreader;
    private OutputStream mOutputStream;
    private Parameters mParams;
    private Handler mHandler;
    private int CSeq;
    private int mState;
    private String mSession;
    private RtpSocket mRtpSocket;
    
    private boolean isTCPtranslate;
    private static boolean Describeflag = false; //used to get SDP info
    private static SDPInfo sdpInfo;
    private String authorName, authorPassword, authorBase64;
    private HandlerThread thread;

    private H264Stream mH264Stream;

    private H264StreamCallback mDataListener;

    public RtspClientOnvif(String address,String name, String password) {
        this(METHOD_UDP,address,name,password);
    }

    public RtspClientOnvif(String address, String name ,String password, int port) {
        this(METHOD_UDP,address,name,password,port);
    }

    public RtspClientOnvif(String address) {
  		this(METHOD_UDP, address, null, null);
    }

    public RtspClientOnvif(String method, String address) {
        this(method,address,null,null);
    }

    public RtspClientOnvif(String method, String address, int port) {
        this(method,address,null,null,port);
    }

    public RtspClientOnvif(String address, int port) {
        this(METHOD_UDP,address,null,null,port);
    }

    public RtspClientOnvif(String method, String address,String name,String password) {
    	Log.d(TAG, "address="+address);
        String url = address.substring(address.indexOf("//") + 2);
        url = url.substring(0,url.indexOf("/"));
        
        //密码在链接中
        int atail = url.indexOf('@');
        if (atail > 0)
        {
        	String up = url.substring(0, atail);
        	url = url.substring(atail+1);
        	String[] tmp = up.split(":");
        	name = tmp[0];
        	password = tmp[1];
        }
        
        String[] tmp = url.split(":");
        Log.d(TAG, url);
        authorName = name;
        authorPassword = password;
        isTCPtranslate = method.equalsIgnoreCase(METHOD_TCP);
        
        if(tmp.length == 1)
        {
        	ClientConfig(tmp[0], address, 554);
        }
        else if(tmp.length == 2)
        {
        	ClientConfig(tmp[0], address, Integer.parseInt(tmp[1]));
        }
        
    }

    public RtspClientOnvif(String method, String address,String name,String password, int port) {
        String url = address.substring(address.indexOf("//") + 2);
        url = url.substring(0,url.indexOf("/"));
        authorName = name;
        authorPassword = password;
        ClientConfig(url, address, port);
        if( method.equalsIgnoreCase(METHOD_UDP) ) {
            isTCPtranslate = false;
        } else if( method.equalsIgnoreCase(METHOD_TCP)) {
            isTCPtranslate = true;
        }
    }

    public void setDataListener( H264StreamCallback l ) {
    	mDataListener = l;
    }

    private void ClientConfig(String host, String address, int port) {
        mParams = new Parameters();
        sdpInfo = new SDPInfo();
        mParams.host = host;
        mParams.port = port;
        mParams.address = address.substring(7);
        CSeq = 0;
        mState = STATE_STOPPED;
        mSession = null;
        if(authorName == null && authorPassword == null) {
            authorBase64 = null;
        }
        else {
            authorBase64 = Base64.encodeToString((authorName+":"+authorPassword).getBytes(),Base64.DEFAULT);
        }

        final Semaphore signal = new Semaphore(0);
        thread = new HandlerThread("RTSPCilentThread") {
            protected void onLooperPrepared() {
            	Log.d(TAG, "-------------");
                mHandler = new Handler();
                signal.release();
                mHandler.post(startConnection);
            }
        };
        thread.start();
        signal.acquireUninterruptibly();
    }

    public void start() {
    	if (null != mHandler)
    	{
        //mHandler.post(startConnection);
    	}
    }

    private Runnable startConnection = new Runnable() {
        @Override
        public void run() {
            if (mState != STATE_STOPPED) return;
            mState = STATE_STARTING;

            Log.d(TAG, "Start to connect the server...");

            try {
                tryConnection();
                mHandler.post(sendGetParameter);
            } catch (IOException e) {
                Log.e(TAG, e.toString());
                abort();
            }
        }
    };

    private Runnable sendGetParameter = new Runnable() {
        @Override
        public void run() {
            try {
                sendRequestGetParameter();
                mHandler.postDelayed(sendGetParameter,55000);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    public void abort() {
        try {
            if(mState == STATE_STARTED) sendRequestTeardown();
        } catch ( IOException e ) {}
        try {
            if(mSocket!=null) mSocket.close();
        } catch ( IOException e ) {}
        mState = STATE_STOPPED;
        mHandler.removeCallbacks(startConnection);
        mHandler.removeCallbacks(sendGetParameter);
    }

    public void shutdown(){
        if(mState == STATE_STARTED ||
                mState == STATE_STARTING) {
            mHandler.removeCallbacks(startConnection);
            mHandler.removeCallbacks(sendGetParameter);
            try {
                if(mRtpSocket!=null) {
                    mRtpSocket.stop();
                    mRtpSocket = null;
                }
                
                if(mSocket!=null) {
                    mSocket.close();
                    mSocket = null;
                }

                if(mH264Stream!=null) {
                    mH264Stream.stop();
                    mH264Stream = null;
                }
                if(mState == STATE_STARTED) sendRequestTeardown();
                mState = STATE_STOPPED;
                thread.quit();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean isStarted() {
        return mState == STATE_STARTED | mState == STATE_STARTING;
    }

    private void tryConnection () throws IOException {
        mSocket = new Socket(mParams.host, mParams.port);
        mBufferreader = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
        mOutputStream = mSocket.getOutputStream();
        mState = STATE_STARTING;
        sendRequestOptions();
        sendRequestDescribe();
        sendRequestSetup();
        sendRequestPlay();
    }

    private void sendRequestOptions() throws IOException {
        String request = "OPTIONS rtsp://" + mParams.address + " RTSP/1.0\r\n" + addHeaders();
        Log.d(TAG, request);
        mOutputStream.write(request.getBytes("UTF-8"));
        Response.parseResponse(mBufferreader);
    }

	final protected static char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'};

	private static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		int v;
		for ( int j = 0; j < bytes.length; j++ ) {
			v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	/** Needed for the Digest Access Authentication. */
	private String computeMd5Hash(String buffer) {
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("MD5");
			return bytesToHex(md.digest(buffer.getBytes("UTF-8")));
		} catch (Exception ignore) {
		}
		return "";
	}

    /*
     * RTSP�ͻ���Ӧ��ʹ��username + password������response����:

(1)��passwordΪMD5����,��

   response = md5( password:nonce:md5(public_method:url)  );

(2)��passwordΪANSI�ַ���,��

    response= md5( md5(username:realm:password):nonce:md5(public_method:url) );
     */
    public String getPasswordEncode(
    		String username, String password, String realm,
    		String nonce,
    		String method, String uri) {  
        try {  
        	String hash1 = computeMd5Hash(username+":"+realm+":"+password);
			String hash2 = computeMd5Hash(method+":"+uri);
			String hash3 = computeMd5Hash(hash1+":"+nonce+":"+hash2);
			return hash3;
        } catch (Exception e) {  
            e.printStackTrace();  
            return "";  
        }  
    }  
    
    private void sendRequestDescribe() throws IOException {
        String request = "DESCRIBE rtsp://" + mParams.address + " RTSP/1.0\r\n" + addHeaders();
        Log.d(TAG, request);
        Describeflag = true;
        mOutputStream.write(request.getBytes("UTF-8"));
        Response resp = Response.parseResponse(mBufferreader);
        
        //Ҫ����֤
        if (resp.state == 401 && resp.mAuthenticate != null)
        {
        	String temp = resp.mAuthenticate.substring(resp.mAuthenticate.indexOf("realm=\"")+"realm=\"".length());
        	String realm= temp.substring(0, temp.indexOf('\"'));
        	
        	temp = temp.substring(temp.indexOf("nonce=\"")+"nonce=\"".length());
        	String nonce = temp.substring(0, temp.indexOf('\"'));
        	Log.d(TAG, "realm="+realm+", nonce="+nonce);
        	
        	String digest = getPasswordEncode("admin", "admin", realm,
        			nonce,
        			"DESCRIBE", "rtsp://"+mParams.address);
        	request = "DESCRIBE rtsp://" + mParams.address + " RTSP/1.0\r\n" + addHeaders()
        			+"Authorization: Digest username=\"admin\""
        			+", realm=\""+realm+"\""
        			+", nonce=\""+nonce+"\""
        			+", uri=\"rtsp://"+mParams.address+"\""
        			+", response=\""+digest+"\""
        			+"\r\n";
            Log.d(TAG, request);
        	mOutputStream.write(request.getBytes("UTF-8"));
            Response resp2 = Response.parseResponse(mBufferreader);
        }

    }

    private void sendRequestSetup() throws IOException {
        Matcher matcher;
        String request = "SETUP rtsp://" + mParams.address + "/" + sdpInfo.videoTrack + " RTSP/1.0\r\n"
                    + "Transport: RTP/AVP/"+
                    (isTCPtranslate ? "TCP" : "UDP")
                    +"unicast;client_port=55640-55641" + "\r\n"
                    + addHeaders();
        Log.d(TAG, request);
        mOutputStream.write(request.getBytes("UTF-8"));
        Response mResponse = Response.parseResponse(mBufferreader);

        //there has two different session type, one is without timeout , another is with timeout
        matcher = Response.regexSessionWithTimeout.matcher(mResponse.headers.get("session"));
        if(matcher.find())  mSession = matcher.group(1);
        else mSession = mResponse.headers.get("session");
        Log.d(TAG,"the session is " + mSession);
        
		Iterator<Entry<String, String>> it = mResponse.headers.entrySet().iterator();
		int counter=0;
		while (it.hasNext()) {
			Entry<String, String> entry = it.next();
			Log.d(TAG, "headers["+counter+"]=("+entry.getKey()+", "+entry.getValue());
			counter++;
		}

        //get the port information and start the RTP socket, ready to receive data
        if(isTCPtranslate)
        {
        	matcher = Response.regexTCPTransport.matcher(mResponse.headers.get("transport"));
        }
        else
        {
        	matcher = Response.regexUDPTransport.matcher(mResponse.headers.get("transport"));
        }
        Matcher matcherClientPort = Response.regexUDPClientPort.matcher(mResponse.headers.get("transport"));
        Matcher matcherServerPort = Response.regexUDPServerPort.matcher(mResponse.headers.get("transport"));
        if (matcherClientPort.find() && matcherServerPort.find() ) {
        	
        	//ReceiveData receive = new ReceiveData();
            Log.d(TAG, "The client port is:" + matcherClientPort.group(1) + ", the server prot is:" + (isTCPtranslate?"null":matcherServerPort.group(1)) + "...");
            mParams.rtpPort = Integer.parseInt(matcherClientPort.group(1));
            if(!isTCPtranslate) mParams.serverPort = Integer.parseInt(matcherServerPort.group(1));

            //ReceiveData rd = new ReceiveData(55640, 55641, "192.168.199.51", 10000, 10001);
            
            //prepare for the video decoder
            mH264Stream = new H264Stream(sdpInfo);
            mH264Stream.setDataListener(mDataListener);

            if (isTCPtranslate)
            {
            	mRtpSocket = new RtpSocket(isTCPtranslate, mParams.rtpPort, mParams.host, -1,TRACK_VIDEO);
            }
            else
            {
            	mRtpSocket = new RtpSocket(isTCPtranslate, mParams.rtpPort, mParams.host, mParams.serverPort,TRACK_VIDEO);
            }
            mRtpSocket.startRtpSocket();
            mRtpSocket.setStream(mH264Stream);
            
        } else {
            if(isTCPtranslate) {
                Log.d(TAG,"Without get the transport port infom, use the rtsp tcp socket!");
                mParams.rtpPort = mParams.port;

                //prepare for the video decoder
                mH264Stream = new H264Stream(sdpInfo);
                mH264Stream.setDataListener(mDataListener);

                mRtpSocket = new RtpSocket(isTCPtranslate,mParams.rtpPort,mParams.host,-2,TRACK_VIDEO);
                mRtpSocket.setRtspSocket(mSocket);
                mRtpSocket.startRtpSocket();
                mRtpSocket.setStream(mH264Stream);
                mState = STATE_STARTED;
            }
        }
    }

    private void sendRequestPlay() throws IOException {
        String request = "PLAY rtsp://" + mParams.address + " RTSP/1.0\r\n"
                + "Range: npt=0.000-\r\n"
                + addHeaders();
        Log.d(TAG, request);
        mOutputStream.write(request.getBytes("UTF-8"));
        Response.parseResponse(mBufferreader);
    }

    private void sendRequestTeardown() throws IOException {
        String request = "TEARDOWN rtsp://" + mParams.address + "/" + sdpInfo.videoTrack + " RTSP/1.0\r\n" + addHeaders();
        Log.d(TAG, request.substring(0, request.indexOf("\r\n")));
        mOutputStream.write(request.getBytes("UTF-8"));
        mState = STATE_STOPPING;
    }

    private void sendRequestGetParameter() throws IOException {
        String request = "GET_PARAMETER rtsp://" + mParams.address + "/" + sdpInfo.videoTrack + " RTSP/1.0\r\n" + addHeaders();
        Log.d(TAG, request.substring(0, request.indexOf("\r\n")));
        mOutputStream.write(request.getBytes("UTF-8"));
    }

    private String addHeaders() {
        return "CSeq: " + (++CSeq) + "\r\n"
                //+ ((authorBase64 == null)?"":("Authorization: Basic " +authorBase64 +"\r\n"))
                + "User-Agent: " + UserAgent + "\r\n"
                + "Accept: application/sdp\r\n"
                + ((mSession == null)?"":("Session: " + mSession + "\r\n"))
                + "\r\n";
    }

    static class Response {

        public static final Pattern regexStatus = Pattern.compile("RTSP/\\d.\\d (\\d+) .+",Pattern.CASE_INSENSITIVE);
        public static final Pattern regexHeader = Pattern.compile("(\\S+): (.+)",Pattern.CASE_INSENSITIVE);
        public static final Pattern regexUDPTransport = Pattern.compile("client_port=(\\d+)-\\d+;server_port=(\\d+)-\\d+",Pattern.CASE_INSENSITIVE);
        public static final Pattern regexTCPTransport = Pattern.compile("client_port=(\\d+)-\\d+;",Pattern.CASE_INSENSITIVE);
        public static final Pattern regexSessionWithTimeout = Pattern.compile("(\\S+);timeout=(\\d+)",Pattern.CASE_INSENSITIVE);
        public static final Pattern regexSDPgetTrack1 = Pattern.compile("trackID=(\\d+)",Pattern.CASE_INSENSITIVE);
        public static final Pattern regexSDPgetTrack2 = Pattern.compile("control:(\\S+)",Pattern.CASE_INSENSITIVE);
        public static final Pattern regexSDPmediadescript = Pattern.compile("m=(\\S+) .+",Pattern.CASE_INSENSITIVE);
        public static final Pattern regexSDPpacketizationMode = Pattern.compile("packetization-mode=(\\d);",Pattern.CASE_INSENSITIVE);
        public static final Pattern regexSDPspspps = Pattern.compile("sprop-parameter-sets=(\\S+),(\\S+)\\;(\\S+)",Pattern.CASE_INSENSITIVE);
        public static final Pattern regexSDPlength = Pattern.compile("Content-length: (\\d+)",Pattern.CASE_INSENSITIVE);
        public static final Pattern regexSDPstartFlag = Pattern.compile("v=(\\d)",Pattern.CASE_INSENSITIVE);

        //
        public static final Pattern regexUDPClientPort = Pattern.compile("client_port=(\\d+)-\\d+",Pattern.CASE_INSENSITIVE);
        public static final Pattern regexUDPServerPort = Pattern.compile("server_port=(\\d+)-\\d+",Pattern.CASE_INSENSITIVE);

        public int state;
        public static HashMap<String,String> headers = new HashMap<String,String>();
        
        public String mAuthenticate;
        public String mDate;

        public static Response parseResponse(BufferedReader input){
            Response response = new Response();
            String line;
            Matcher matcher;
            int sdpContentLength = 0;
            try
            {
            if( (line = input.readLine()) == null) throw new IOException("Connection lost");
            matcher = regexStatus.matcher(line);
            if(matcher.find())
                response.state = Integer.parseInt(matcher.group(1));
            else
                while ( (line = input.readLine()) != null ) {
                    matcher = regexStatus.matcher(line);
                    if(matcher.find()) {
                        response.state = Integer.parseInt(matcher.group(1));
                        break;
                    }
                }
            Log.d(TAG, "-----------------The response state is: "+response.state);

            int foundMediaType = 0;
            int sdpHaveReadLength = 0;
            boolean sdpStartFlag = false;

            while ( (line = input.readLine()) != null) {

                if( line.length() > 3 || Describeflag ) {

                    Log.d(TAG, line);
                    if (line.startsWith("WWW-Authenticate:"))
                    {
                    	response.mAuthenticate = line;
                    	break;
                    }
                    else if (line.startsWith("Date"))
                    {
                    	response.mDate = line.substring(5);
                    }
                    matcher = regexHeader.matcher(line);
                    if (matcher.find())
                        headers.put(matcher.group(1).toLowerCase(Locale.US), matcher.group(2)); //$ to $

                    matcher = regexSDPlength.matcher(line);
                    if(matcher.find()) {
                        sdpContentLength = Integer.parseInt(matcher.group(1));
                        sdpHaveReadLength = 0;
                    }
                    //Here is trying to get the SDP information from the describe response
                    if (Describeflag) {
                        matcher = regexSDPmediadescript.matcher(line);
                        if (matcher.find())
                            if (matcher.group(1).equalsIgnoreCase("audio")) {
                                foundMediaType = 1;
                                sdpInfo.audioTrackFlag = true;
                            } else if (matcher.group(1).equalsIgnoreCase("video")) {
                                foundMediaType = 2;
                                sdpInfo.videoTrackFlag = true;
                            }

                        matcher = regexSDPpacketizationMode.matcher(line);
                        if (matcher.find()) {
                            sdpInfo.packetizationMode = Integer.parseInt(matcher.group(1));
                        }

                        matcher = regexSDPspspps.matcher(line);
                        if(matcher.find()) {
                            sdpInfo.SPS = matcher.group(1);
                            sdpInfo.PPS = matcher.group(2);
                            Log.d(TAG,"sps="+sdpInfo.SPS);
                            Log.d(TAG,"PPS="+sdpInfo.PPS);
                        }

                        matcher = regexSDPgetTrack1.matcher(line);
                        if(matcher.find())
                            if (foundMediaType == 1) sdpInfo.audioTrack = "trackID=" + matcher.group(1);
                            else if (foundMediaType == 2) sdpInfo.videoTrack = "trackID=" + matcher.group(1);

                        matcher = regexSDPgetTrack2.matcher(line);
                        if(matcher.find())
                            if (foundMediaType == 1) sdpInfo.audioTrack = matcher.group(1);
                            else if (foundMediaType == 2) sdpInfo.videoTrack = matcher.group(1);


                        matcher = regexSDPstartFlag.matcher(line);
                        if(matcher.find()) sdpStartFlag = true;
                        if(sdpStartFlag) sdpHaveReadLength += line.getBytes().length + 2;
                        if((sdpContentLength < sdpHaveReadLength + 2) && (sdpContentLength != 0)) {
                            Describeflag = false;
                            sdpStartFlag = false;
                            Log.d(TAG, "The SDP info: "
                                    + (sdpInfo.audioTrackFlag ? "have audio info.. " : "haven't the audio info.. ")
                                    + ";" + (sdpInfo.audioTrackFlag ? (" the audio track is " + sdpInfo.audioTrack) : ""));
                            Log.d(TAG, "The SDP info: "
                                    + (sdpInfo.videoTrackFlag ? "have video info.. " : "haven't the vedio info..")
                                    + (sdpInfo.videoTrackFlag ? (" the video track is " + sdpInfo.videoTrack) : "")
                                    + ";" + (sdpInfo.videoTrackFlag ? (" the video SPS is " + sdpInfo.SPS) : "")
                                    + ";" + (sdpInfo.videoTrackFlag ? (" the video PPS is " + sdpInfo.PPS) : "")
                                    + ";" + (sdpInfo.videoTrackFlag ? (" the video packetization mode is " + sdpInfo.packetizationMode) : ""));
                            break;
                        }
                    }
                } else {
                    break;
                }

            }

            if( line == null ) throw new IOException("Connection lost");
            }
            catch (Exception e)
            {
            	e.printStackTrace();
            }
            return  response;
        }
    }
}
