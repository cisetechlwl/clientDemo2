package com.sdb.netool;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.security.KeyStore;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import com.sdb.netool.ssl.NoCheckX509TrustManager;

/**
 * ����ֱ��ʵ�ú���
 * 
 * @author ZHANGXUELING871
 * @version 0.1
 * @since 2014-01-03
 */
public class YQUtil {
    
    public static final int HEAD_LEN_NEW = 222;
    public static final int HEAD_LEN_OLD = 6;
    
    public static final String CHARSET = "GBK";
    private static final String fmtTime = "yyyyMMddHHmmss";
    private static final int TIME_OUT = 120000; //��ʱʱ�䣬��λΪ���룬Ĭ��2����
    
    public static final int PROTOCAL_TCP = 0;
    public static final int PROTOCAL_HTTP = 1;
    public static final int PROTOCAL_HTTPS = 2;
    
    
    /**
     * ��װ����
     * ����append�Ƚ϶࣬��Ϊ��չ�ֱ���ͷ�ĸ����ֶΣ�ʵ��ʹ�����밴�����
     * 
     * @param yqdm 20λ�������
     * @param bsnCode ���״���
     * @param xmlBody xml���屨��
     * @return
     */
    public static String asemblyPackets(String yqdm, String bsnCode, String xmlBody) {
        
        Date now = Calendar.getInstance().getTime();
        
        StringBuilder buf = new StringBuilder();
        buf.append("A00101");
        
        //����
        String encoding = "01";
        if (CHARSET.equalsIgnoreCase("GBK")) {
            encoding = "01";
        } else if(CHARSET.equalsIgnoreCase("utf-8") || CHARSET.equalsIgnoreCase("utf8")) {
            encoding = "02";
        }
        buf.append(encoding);//����
        
        buf.append("01");//ͨѶЭ��ΪTCP/IP
        buf.append(String.format("%20s", yqdm));//�������
        try {
            buf.append(String.format("%010d", xmlBody.getBytes(CHARSET).length));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        buf.append(String.format("%-6s", bsnCode));//������-�����
        buf.append("12345");//����Ա����-�û����Զ���
        buf.append("01");//�������� 01����
        
        String fmtNow = new SimpleDateFormat(fmtTime).format(now);
        buf.append(fmtNow); //��������ʱ��
        
        String requestLogNo = "YQTEST" + fmtNow;
        buf.append(requestLogNo);//����ϵͳ��ˮ��
        
        buf.append(String.format("%6s", "")); //������
        buf.append(String.format("%100s", ""));
        
        buf.append(0); //��������־
        buf.append(String.format("%03d", 0));//�������
        buf.append("0");//ǩ����ʶ 0��ǩ
        buf.append("1");//ǩ�����ݰ���ʽ
        buf.append(String.format("%12s", "")); //ǩ���㷨
        buf.append(String.format("%010d", 0)); //ǩ�����ݳ���
        buf.append(0);//������Ŀ
        buf.append(xmlBody);//������
        
        return buf.toString();
    }
    
    /**
     * ���ͱ���
     * @param serverIp ������IP��ַ��SCP��ַ��
     * @param iPort �˿ں�
     * @param packetsRQ ������ͷ
     * @return
     */
    public static Packets send2server(String serverIp, int iPort, String packetsRQ, int protocal) throws Exception {
        
        Packets packets = null;
        
        URL url = null;
        switch(protocal) {
        case PROTOCAL_TCP:
            packets = send2TcpServer(serverIp, iPort, packetsRQ);
            break;
        case PROTOCAL_HTTP:
            url = new URL("http://" + serverIp + ":" + iPort);
            packets = send2httpServer(url, packetsRQ);
            break;
        case PROTOCAL_HTTPS:
            url = new URL("https://" + serverIp + ":" + iPort);
            packets = send2httpServer(url, packetsRQ);
            break;
        }
        return packets;
    }
    
    
    private static Packets send2TcpServer(String serverIp, int iPort, String packetsRQ) {
        Packets packetsRP = new Packets();
        
        boolean isOld = false;
        if (!packetsRQ.startsWith("A001")) {
            isOld = true;
        }
        
        OutputStream out = null;
        InputStream in = null;
        Socket socket = null;
        try {
            socket = new Socket(serverIp, iPort);
            socket.setSendBufferSize(4096);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(TIME_OUT);
            socket.setKeepAlive(true);
            out = socket.getOutputStream();
            in = socket.getInputStream();
            out.write(packetsRQ.getBytes(CHARSET));
            out.flush();
            
            int headLen = HEAD_LEN_NEW;
            if (isOld) {
                headLen = HEAD_LEN_OLD;
            }
            
            //��ȡ����ͷ
            byte[] head = new byte[headLen];
            int headTotal = 0;
            int len = 0;
            while (headTotal < headLen && len > -1) {
                len = in.read(head, headTotal, headLen - headTotal);
                headTotal += len;
            }
            packetsRP.setHead(head);
            
            //��ȡ������
            int bodyLen = 0;
            if(isOld) {
                bodyLen = Integer.parseInt(new String(head));
            } else {
                bodyLen = Integer.parseInt(new String(head, 30, 10, CHARSET));
            }
            
            if(bodyLen > 0) {
                packetsRP.setLen(bodyLen);
                
                byte[] body = new byte[bodyLen];
                
                int bodyTotal = 0;
                len = 0;
                
                while (bodyTotal < bodyLen && len > -1) {
                    len = in.read(body, bodyTotal, bodyLen - bodyTotal);
                    bodyTotal += len;
                }
                packetsRP.setBody(body);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
                if (socket != null) {
                    socket.close();
                }
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        }
        
        return packetsRP;
    }
    
    private static SSLSocketFactory ssf = null;
    
    private static Packets send2httpServer(URL url, String packetsRQ) throws Exception {
        //URL url = new URL("http://" + serverIp + ":" + iPort); //127.0.0.1:7072
        
        String protocal = url.getProtocol();
        
        if (protocal.equalsIgnoreCase("https")) {
            SSLContext sslContext = null;
            String ksPwd = "123456";
            
            try {
                InputStream in = YQUtil.class.getClassLoader().getResourceAsStream("trust.store"); //classĿ¼��
                
                KeyStore ks = KeyStore.getInstance("JKS");
                ks.load(in, ksPwd.toCharArray());
                
                sslContext = SSLContext.getInstance("TLS", "SunJSSE");
                
                KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
                kmf.init(ks, ksPwd.toCharArray());
                
                TrustManager[] tm = {new NoCheckX509TrustManager()};
                sslContext.init(kmf.getKeyManagers(), tm, new java.security.SecureRandom());
                ssf = sslContext.getSocketFactory();
                HttpsURLConnection.setDefaultSSLSocketFactory(ssf);
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        HttpURLConnection http = (HttpURLConnection) url.openConnection();
        
        http.setConnectTimeout(60000);
        http.setReadTimeout(60000);
        http.setDoOutput(true);
        http.setDoInput(true);
        http.setAllowUserInteraction(false);
        http.setUseCaches(false);
        http.setRequestMethod("POST");
        http.setRequestProperty("content-type","text/xml; charset=GBK");
        
        byte[] packets = packetsRQ.getBytes("GBK");
        http.setRequestProperty("Content-Length", String.valueOf(packets.length));
        http.setRequestProperty("Connection", "close");
        http.setRequestProperty("User-Agent","sdb client");
        http.setRequestProperty("Accept", "text/xml");
        OutputStream out = http.getOutputStream();
        
        out.write(packets);
        out.flush();
        
        InputStream in = http.getInputStream();
        int code = http.getResponseCode();
        if (code != 200) {
            throw new Exception("�Ҳ���web����");
        }
        
        Packets packetRep = new Packets();
        
        byte[] head = new byte[HEAD_LEN_NEW];
        int recvLen = 0;
        while (recvLen < HEAD_LEN_NEW) {
            recvLen = in.read(head, recvLen, HEAD_LEN_NEW - recvLen);
        }
        
        packetRep.setHead(head);
        
        int bodyLen = Integer.parseInt(new String(head, 30, 10, CHARSET));;
        packetRep.setLen(bodyLen);
        
        if (bodyLen > 0) {
            byte[] body = new byte[bodyLen];
            recvLen = 0;
            while (recvLen < bodyLen) {
                recvLen = in.read(body, recvLen, bodyLen - recvLen);
            }
            packetRep.setBody(body);
        }
        
        return packetRep;
    }
}
