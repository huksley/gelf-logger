package com.wizecore.graylog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.GZIPOutputStream;

/**
 * Sends GELF messages.
 * Based on https://github.com/Graylog2/gelfj/blob/master/src/main/java/org/graylog2/GelfSender.java
 * Heavily reworked to be independent and self contained. All datagram and message serialization methods moved here.
 */
public class GelfSender {
    public static final int DEFAULT_PORT = 12201;
    
	private static final String ID_NAME = "id";	
    
	private static final byte[] GELF_CHUNKED_ID = new byte[]{0x1e, 0x0f};
    private static final int MAXIMUM_CHUNK_SIZE = 1420;
    
    private static final int PORT_MIN = 9000;
    private static final int PORT_MAX = 9888;

    private InetAddress host;
    private int port;
    private DatagramSocket socket;

    public GelfSender(String host) throws UnknownHostException, SocketException {
        this(host, DEFAULT_PORT);
    }

    public GelfSender(String host, int port) throws UnknownHostException, SocketException {
        this.host = InetAddress.getByName(host);
        this.port = port;
        this.socket = initiateSocket();
    }

    protected DatagramSocket initiateSocket() throws SocketException {
        int port = PORT_MIN;

        DatagramSocket resultingSocket = null;
        boolean binded = false;
        while (!binded) {
            try {
                resultingSocket = new DatagramSocket(port);
                binded = true;
            } catch (SocketException e) {
                port++;
                if (port > PORT_MAX) {
                    throw e;
                }
            }
        }
        
        return resultingSocket;
    }

    public void sendMessage(GelfMessage m) throws IOException {
        if (m.isValid()) {
        	sendDatagrams(toDatagrams(m));
        }
    }
    
    protected List<byte[]> toDatagrams(GelfMessage m) throws IOException {
        byte[] messageBytes = gzipMessage(formatMessage(m));
        List<byte[]> datagrams = new ArrayList<byte[]>();
        if (messageBytes.length > MAXIMUM_CHUNK_SIZE) {
            sliceDatagrams(m, messageBytes, datagrams);
        } else {
            datagrams.add(messageBytes);
        }
        return datagrams;
    }

    protected void sliceDatagrams(GelfMessage m, byte[] messageBytes, List<byte[]> datagrams) {
        int messageLength = messageBytes.length;
        byte[] messageId = Arrays.copyOf((new Date().getTime() + m.getHost()).getBytes(), 32);
        int num = ((Double) Math.ceil((double) messageLength / MAXIMUM_CHUNK_SIZE)).intValue();
        for (int idx = 0; idx < num; idx++) {
            byte[] header = concatByteArray(GELF_CHUNKED_ID, concatByteArray(messageId, new byte[]{0x00, (byte) idx, 0x00, (byte) num}));
            int from = idx * MAXIMUM_CHUNK_SIZE;
            int to = from + MAXIMUM_CHUNK_SIZE;
            if (to >= messageLength) {
                to = messageLength;
            }
            byte[] datagram = concatByteArray(header, Arrays.copyOfRange(messageBytes, from, to));
            datagrams.add(datagram);
        }
    }

    protected byte[] concatByteArray(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    protected byte[] gzipMessage(String message) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        OutputStream stream = new GZIPOutputStream(bos);
        try {
            stream.write(message.getBytes("UTF-8"));
        } finally {
        	stream.close();
        }
        return bos.toByteArray();
    }
    
    public static String formatMessage(GelfMessage m) {
        Map<String, Object> map = new HashMap<String, Object>();

        map.put("version", m.getVersion());
        map.put("host", m.getHost());
        map.put("short_message", m.getShortMessage());
        map.put("full_message", m.getFullMessage());
        map.put("timestamp", m.getTimestamp().intValue());

        map.put("level", m.getLevel());
        map.put("facility", m.getFacility());
        map.put("file", m.getFile());
        map.put("line", m.getLine());

        for (Map.Entry<String, Object> additionalField : m.getAdditonalFields().entrySet()) {
            if (!ID_NAME.equals(additionalField.getKey())) {
                map.put("_" + additionalField.getKey(), additionalField.getValue());
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{ ");
        boolean start = true;
        for (Iterator<Entry<String, Object>> it = map.entrySet().iterator(); it.hasNext(); ) { 
        	Entry<String, Object> e = it.next();
        	String name = e.getKey();
        	Object value = e.getValue();
        	if (value != null) {
        		String s = value.toString().trim();
        		s = replace(s, "\n", "\\n");
        		s = replace(s, "\r", "\\r");
        		s = replace(s, "\t", "\\t");
        		s = replace(s, "\"", "\\\"");
        		
        		if (start) {
            		start = false;
        		} else {
        			sb.append(", ");
        		}
        		sb.append("\"");
        		sb.append(name);
        		sb.append("\": \"");
        		sb.append(s);
        		sb.append("\"");        		
        	}
        }
        sb.append(" }");
        return sb.toString();
    }

    protected void sendDatagrams(List<byte[]> bytesList) {
        for (byte[] bytes : bytesList) {
            DatagramPacket datagramPacket = new DatagramPacket(bytes, bytes.length, host, port);
            try {
                socket.send(datagramPacket);
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }
    }

    public void close() {
        socket.close();
    }
    
    public static String replace(String str, String what, String onwhat) {
        int beginIndex = 0;
        int endIndex = 0;
        String r = "";        
        endIndex = str.indexOf(what, beginIndex);
        
        while (endIndex != -1) {
            r = r + str.substring(beginIndex, endIndex) + onwhat;
            beginIndex = endIndex + what.length();
            endIndex = str.indexOf(what, beginIndex);
        }
        
        r = r + str.substring(beginIndex, str.length());        
        return r;
        
    }

	/**
	 * Getter for {@link GelfSender#host}.
	 */
	public InetAddress getHost() {
		return host;
	}

	/**
	 * Setter for {@link GelfSender#host}.
	 */
	public void setHost(InetAddress host) {
		this.host = host;
	}

	/**
	 * Getter for {@link GelfSender#port}.
	 */
	public int getPort() {
		return port;
	}

	/**
	 * Setter for {@link GelfSender#port}.
	 */
	public void setPort(int port) {
		this.port = port;
	}
}
