package net.quantum6.rtsp.onvif;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

import org.xmlpull.v1.XmlPullParser;

import android.util.Base64;
import android.util.Xml;

public class HttpSoap implements Runnable {
	public static final String GET_SUBSERVICE_POST = "<?xml version=\"1.0\" encoding=\"utf-8\"?><s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\"><s:Body xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"><GetServices xmlns=\"http://www.onvif.org/ver10/device/wsdl\"><IncludeCapability>false</IncludeCapability></GetServices></s:Body></s:Envelope>";
	public static final String IS_NEED_AUTH = "<?xml version=\"1.0\" encoding=\"utf-8\"?><s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\"><s:Body xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"><GetClientCertificateMode xmlns=\"http://www.onvif.org/ver10/device/wsdl\"></GetClientCertificateMode></s:Body></s:Envelope>";
	public static final String GET_CAPABILITIES = "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\"><s:Header><Security s:mustUnderstand=\"1\" xmlns=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\"><UsernameToken><Username>%s</Username><Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">%s</Password><Nonce EncodingType=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary\">%s</Nonce><Created xmlns=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\">%s</Created></UsernameToken></Security></s:Header><s:Body xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"><GetCapabilities xmlns=\"http://www.onvif.org/ver10/device/wsdl\"><Category>All</Category></GetCapabilities></s:Body></s:Envelope>";
	public static final String GET_PROFILES = "<?xml version=\"1.0\" encoding=\"utf-8\"?><s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\"><s:Header><wsse:Security xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\"><wsse:UsernameToken><wsse:Username>%s</wsse:Username><wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">%s</wsse:Password><wsse:Nonce>%s</wsse:Nonce><wsu:Created>%s</wsu:Created></wsse:UsernameToken></wsse:Security></s:Header><s:Body xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"><GetProfiles xmlns=\"http://www.onvif.org/ver10/media/wsdl\"></GetProfiles></s:Body></s:Envelope>";
	public static final String GET_PROFILE = "<?xml version=\"1.0\" encoding=\"utf-8\"?><s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\"><s:Header><wsse:Security xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\"><wsse:UsernameToken><wsse:Username>%s</wsse:Username><wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">%s</wsse:Password><wsse:Nonce>%s</wsse:Nonce><wsu:Created>%s</wsu:Created></wsse:UsernameToken></wsse:Security></s:Header><s:Body xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"><GetProfile xmlns=\"http://www.onvif.org/ver10/media/wsdl\"><ProfileToken>%s</ProfileToken></GetProfile></s:Body></s:Envelope>";
	public static final String CREATE_PROFILE_BODY = "<?xml version=\"1.0\" encoding=\"utf-8\"?><s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\"><s:Header><wsse:Security xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\"><wsse:UsernameToken><wsse:Username>%s</wsse:Username><wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">%s</wsse:Password><wsse:Nonce>%s</wsse:Nonce><wsu:Created>%s</wsu:Created></wsse:UsernameToken></wsse:Security></s:Header><s:Body xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"><CreateProfile xmlns=\"http://www.onvif.org/ver10/media/wsdl\"><Name>%s</Name></CreateProfile></s:Body></s:Envelope>";
	public static final String GET_URI_BODY = "<?xml version=\"1.0\" encoding=\"utf-8\"?><s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\"><s:Header><wsse:Security xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\"><wsse:UsernameToken><wsse:Username>%s</wsse:Username><wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">%s</wsse:Password><wsse:Nonce>%s</wsse:Nonce><wsu:Created>%s</wsu:Created></wsse:UsernameToken></wsse:Security></s:Header><s:Body xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"><GetStreamUri xmlns=\"http://www.onvif.org/ver10/media/wsdl\"><StreamSetup><Stream xmlns=\"http://www.onvif.org/ver10/schema\">RTP-Unicast</Stream><Transport xmlns=\"http://www.onvif.org/ver10/schema\"><Protocol>RTSP</Protocol></Transport></StreamSetup><ProfileToken>%s</ProfileToken></GetStreamUri></s:Body></s:Envelope>";
	public static final String XMIC_PROFILE = "xmic_profile";
	private HttpURLConnection mUrlConn;
	private OnHttpSoapListener mListener;
	private IpCameraDevice mCamera;
	private String mCreated, mNonce, mAuthPwd;

	public HttpSoap(IpCameraDevice device) {
		mCamera = device;
		createAuthString();
	}

	public void setOnHttpSoapListener(OnHttpSoapListener listener) {
		mListener = listener;
	}

	private void createAuthString() {
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",
				Locale.CHINA);
		mCreated = df.format(new Date());
		mNonce = getNonce();
		mAuthPwd = getPasswordEncode(mNonce, mCamera.password, mCreated);
	}

	private void initConn(String url) {
		try {
			URL url1 = new URL(url);
			mUrlConn = (HttpURLConnection) url1.openConnection();
			mUrlConn.setDoInput(true);
			mUrlConn.setDoOutput(true);
			mUrlConn.setRequestMethod("POST");
			mUrlConn.setUseCaches(false);
			mUrlConn.setInstanceFollowRedirects(true);
			mUrlConn.setRequestProperty("Content-Type",
					"application/x-www-form-urlencoded");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void start() {
		new Thread(this).start();
	}

	public String inputStream2String(InputStream in) throws IOException {
		StringBuffer out = new StringBuffer();
		byte[] b = new byte[4096];
		for (int n; (n = in.read(b)) != -1;) {
			out.append(new String(b, 0, n));
		}
		return out.toString();
	}

	public String getNonce() {
		String base = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
		Random random = new Random();
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < 24; i++) {
			int number = random.nextInt(base.length());
			sb.append(base.charAt(number));
		}
		return sb.toString();
	}

	public String getPasswordEncode(String nonce, String password, String date) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			// �ӹٷ��ĵ�����֪������nonce����Ҫ��Base64����һ��
			byte[] b1 = Base64.decode(nonce.getBytes(), Base64.DEFAULT);
			// �����ַ��ֽ���
			byte[] b2 = date.getBytes(); // "2013-09-17T09:13:35Z";
			byte[] b3 = password.getBytes();
			// �������Ǵ���ֵ�ĳ����������ĳ���
			byte[] b4 = new byte[b1.length + b2.length + b3.length];
			// ����sha-1�����ַ�
			md.update(b1, 0, b1.length);
			md.update(b2, 0, b2.length);
			md.update(b3, 0, b3.length);
			// ����sha-1���ܺ����
			b4 = md.digest();
			// �������յļ����ַ���
			String result = new String(Base64.encode(b4, Base64.DEFAULT));
			return result.replace("\n", "");
		} catch (Exception e) {
			e.printStackTrace();
			return "";
		}
	}

	@Override
	public void run() {
		try {
			// =========��ȡ�ӷ���=========
			initConn(mCamera.serviceURL);
			mUrlConn.connect();
			String content = String.format(GET_CAPABILITIES, mCamera.username,
					mAuthPwd, mNonce, mCreated);
			mUrlConn.getOutputStream().write(content.getBytes());
			InputStream inStream = mUrlConn.getInputStream();
			String res = inputStream2String(inStream);
			String mediaUrl = findMediaServiceUrl(res);
			// =========��ȡProfile=========
			initConn(mediaUrl);
			mUrlConn.connect();
			content = String.format(GET_PROFILES, mCamera.username,
					mAuthPwd, mNonce, mCreated);
			mUrlConn.getOutputStream().write(content.getBytes());
			inStream = mUrlConn.getInputStream();
			res = inputStream2String(inStream);
			String profile = getOldProfileToken(res);
			/*
			 * if (profile.isEmpty()) { //=========����Profile=========
			 * initConn(mediaUrl); mUrlConn.connect(); content =
			 * String.format(CREATE_PROFILE_BODY, mCamera.username, mAuthPwd,
			 * mNonce, mCreated, XMIC_PROFILE);
			 * mUrlConn.getOutputStream().write(content.getBytes()); inStream =
			 * mUrlConn.getInputStream(); res = inputStream2String(inStream);
			 * profile = getProfileToken(res); }
			 */
			// =========��ȡRTSP��������Ϣ====
			initConn(mediaUrl);
			mUrlConn.connect();
			content = String.format(GET_PROFILE, mCamera.username, mAuthPwd,
					mNonce, mCreated, profile);
			mUrlConn.getOutputStream().write(content.getBytes());
			inStream = mUrlConn.getInputStream();
			res = inputStream2String(inStream);
			analyseVideoEncoderConfiguration(res);
			// =========����RTSP==============

			// =========��ȡRTSP��URI=========
			initConn(mediaUrl);
			mUrlConn.connect();
			content = getURIContent(profile);
			mUrlConn.getOutputStream().write(content.getBytes());
			inStream = mUrlConn.getInputStream();
			res = inputStream2String(inStream);
			String uri = getStreamURI(res);
			if (mListener != null) {
				mListener.OnHttpSoapDone(mCamera, uri, true);
			}
		} catch (Exception e) {
			if (mListener != null) {
				mListener.OnHttpSoapDone(mCamera, "", false);
			}
			e.printStackTrace();
		}
	}

	private void analyseVideoEncoderConfiguration(String xml) {
		XmlPullParser parser = Xml.newPullParser();
		InputStream input = new ByteArrayInputStream(xml.getBytes());
		try {
			parser.setInput(input, "UTF-8");
			int eventType = parser.getEventType();
			boolean done = false;
			while (eventType != XmlPullParser.END_DOCUMENT || done) {
				switch (eventType) {
				case XmlPullParser.START_DOCUMENT:
					break;
				case XmlPullParser.START_TAG:
					if (parser.getName().equals("Width")) {
						eventType = parser.next();
						mCamera.width = Integer.parseInt(parser.getText());
					} else if (parser.getName().equals("Height")) {
						eventType = parser.next();
						mCamera.height = Integer.parseInt(parser.getText());
					} else if (parser.getName().equals("FrameRateLimit")) {
						eventType = parser.next();
						mCamera.rate = Integer.parseInt(parser.getText());
					}
					break;
				case XmlPullParser.END_TAG:
					break;
				default:
					break;
				}
				eventType = parser.next();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String getURIContent(String profile) {
		String content = String.format(GET_URI_BODY, mCamera.username,
				mAuthPwd, mNonce, mCreated, profile);
		return content;
	}

	private String findMediaServiceUrl(String xml) {
		XmlPullParser parser = Xml.newPullParser();
		InputStream input = new ByteArrayInputStream(xml.getBytes());
		try {
			parser.setInput(input, "UTF-8");
			int eventType = parser.getEventType();
			boolean done = false;
			while (eventType != XmlPullParser.END_DOCUMENT || done) {
				switch (eventType) {
				case XmlPullParser.START_DOCUMENT:
					break;
				case XmlPullParser.START_TAG:
					if (parser.getName().equals("Media")) {
						eventType = parser.next();
						if (parser.getName().equals("XAddr")) {
							eventType = parser.next();
							if (!parser.getText().isEmpty()) {
								return parser.getText();
							}
						}
						
					}
					break;
				case XmlPullParser.END_TAG:
					break;
				default:
					break;
				}
				eventType = parser.next();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "";
	}
	
	
	class TProfile {
		public String token;
		public int width;
		public int height;
		public int FrameRateLimit;
		
		public TProfile(String token) {
			this.token = token;
		}
	}

	private String getOldProfileToken(String xml) {
		XmlPullParser parser = Xml.newPullParser();
		ArrayList<TProfile> profiles = new ArrayList<TProfile>();
		InputStream input = new ByteArrayInputStream(xml.getBytes());
		try {
			parser.setInput(input, "UTF-8");
			int eventType = parser.getEventType();
			boolean done = false;
			while (eventType != XmlPullParser.END_DOCUMENT || done) {
				switch (eventType) {
				case XmlPullParser.START_DOCUMENT:
					break;
				case XmlPullParser.START_TAG:
					if (parser.getName().equals("Profiles")) {
						String token = parser.getAttributeValue(null, "token");
						TProfile profile = new TProfile(token);
						while (!(eventType == XmlPullParser.START_TAG && parser.getName().equals("Resolution"))) {
							eventType = parser.next();
						}
						while (!(eventType == XmlPullParser.START_TAG && parser.getName().equals("Width"))) {
							eventType = parser.next();
						}
						parser.next();
						profile.width = Integer.parseInt(parser.getText());
						profiles.add(profile);
					}
					break;
				case XmlPullParser.END_TAG:
					break;
				default:
					break;
				}
				eventType = parser.next();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (profiles.size() > 0) {
			TProfile tmpProfile = profiles.get(0);
			for (int i=1;i<profiles.size();i++) {
				if (tmpProfile.width > profiles.get(i).width) {
					tmpProfile = profiles.get(i);
				}
			}
			return tmpProfile.token;
		} else {
			return "";
		}
	}

	private String getStreamURI(String xml) {
		XmlPullParser parser = Xml.newPullParser();
		InputStream input = new ByteArrayInputStream(xml.getBytes());
		try {
			parser.setInput(input, "UTF-8");
			int eventType = parser.getEventType();
			boolean done = false;
			while (eventType != XmlPullParser.END_DOCUMENT || done) {
				switch (eventType) {
				case XmlPullParser.START_DOCUMENT:
					break;
				case XmlPullParser.START_TAG:
					if (parser.getName().equals("Uri")) {
						eventType = parser.next();
						return parser.getText();
					}
					break;
				case XmlPullParser.END_TAG:

					break;
				default:
					break;
				}
				eventType = parser.next();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "";
	}

	public interface OnHttpSoapListener {
		public void OnHttpSoapDone(IpCameraDevice camera, String uri, boolean success);
	}

}
