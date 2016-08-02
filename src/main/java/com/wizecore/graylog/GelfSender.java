package com.wizecore.graylog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

/**
 * Sends GELF messages via TCP or UDP.
 * 
 * Based on https://github.com/Graylog2/gelfj/blob/master/src/main/java/org/graylog2/GelfSender.java
 * Heavily reworked to be independent and self contained. All datagram and message serialization methods moved here.
 */
public class GelfSender {
    public static final int DEFAULT_PORT = 12201;       
    public static final byte[] GELF_UDP_CHUNKED_ID = new byte[] { 0x1e, 0x0f };
    public static final int MAXIMUM_UDP_CHUNK_SIZE = 1420;
    
    /**
     * Start for UDP port binding.
     */
    public static final int PORT_MIN = 9000;
    
    /**
     * End of UDP port binding.
     */
    public static final int PORT_MAX = 9888;
    
    enum Protocol {
    	UDP,
    	TCP
    };

    private Protocol proto = Protocol.UDP;
    private String host = null;
    private int port;
    private DatagramSocket udpSocket;   
    private SocketChannel tcpChannel;    
    private InetAddress destination;

    public GelfSender(String host) {
        this(host, DEFAULT_PORT);
    }
    
    public GelfSender(Protocol proto, String host, int port) {
    	this.proto = proto;
    	this.host = host;
        this.port = port;
    }

    public GelfSender(String host, int port) {    	
        this.host = host;
        this.port = port;
    }

    protected void initiateSocket() throws IOException {
    	if (proto == Protocol.UDP) {
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
            udpSocket = resultingSocket;
    	} else
    	if (proto == Protocol.TCP) {
            // Will do upon log
    	}
    }

    /**
     * Randomly choose destination
     * 
     * @throws UnknownHostException
     */
	protected void findDestination() throws UnknownHostException {
		List<InetAddress> all = new ArrayList<InetAddress>();
    	if (host.indexOf(",") > 0) {
    		String[] l = host.split("\\,");
    		for (String h: l) {
    			all.addAll(Arrays.asList(InetAddress.getAllByName(h.trim())));
    		}
    	} else {
    		all.addAll(Arrays.asList(InetAddress.getAllByName(host.trim())));
    	}
    	
    	if (all.size() == 1) {
    		destination = all.get(0);
    	} else {
    		// Choose one random
    		destination = all.get(new Random(System.currentTimeMillis()).nextInt(all.size()));
    	}
	}
	
	public Protocol getProtocol() {
		return proto;
	}
	
	public void setProtocol(Protocol proto) {
		this.proto = proto;
	}

    public void sendMessage(GelfMessage m) throws IOException {
        if (m.isValid()) {
        	if (proto == Protocol.TCP) {
        		String json = GelfMessage.formatMessage(m);
        		json += '\0';
				sendPacket(json.getBytes("UTF-8"));
        	} else {
        		sendDatagrams(toDatagrams(m));
        	}
        }
    }

	private void sendPacket(byte[] bytes) throws IOException {
		try {
    		if (tcpChannel == null || !tcpChannel.isConnected()) {
    			findDestination();
    			initiateSocket();
    			tcpChannel = SocketChannel.open();
    			tcpChannel.configureBlocking(false);
    			tcpChannel.connect(new InetSocketAddress(destination, port));
    			while (!tcpChannel.finishConnect()) {
    				Thread.yield();
    			}
    		}   
    		
    		ByteBuffer buf = ByteBuffer.wrap(bytes);
    		while (buf.hasRemaining()) {
    			tcpChannel.write(buf);
    			Thread.yield();
            }
		} catch (IOException e) {
			destination = null;
			try {
				tcpChannel.close();
			} catch (Exception ee) {
				// Don`t care
			}
			tcpChannel = null;
			System.err.println("GELF TCP Server (" + destination + ") unavailable: " + e);
		}
	}

	private List<byte[]> toDatagrams(GelfMessage m) throws IOException {
        byte[] messageBytes = gzipMessage(GelfMessage.formatMessage(m));
        List<byte[]> datagrams = new ArrayList<byte[]>();
        if (messageBytes.length > MAXIMUM_UDP_CHUNK_SIZE) {
            sliceDatagrams(m, messageBytes, datagrams);
        } else {
            datagrams.add(messageBytes);
        }
        return datagrams;
    }

    protected void sliceDatagrams(GelfMessage m, byte[] messageBytes, List<byte[]> datagrams) {
        int messageLength = messageBytes.length;
        byte[] messageId = Arrays.copyOf((new Date().getTime() + m.getHost()).getBytes(), 32);
        int num = ((Double) Math.ceil((double) messageLength / MAXIMUM_UDP_CHUNK_SIZE)).intValue();
        for (int idx = 0; idx < num; idx++) {
            byte[] header = concatByteArray(GELF_UDP_CHUNKED_ID, concatByteArray(messageId, new byte[]{0x00, (byte) idx, 0x00, (byte) num}));
            int from = idx * MAXIMUM_UDP_CHUNK_SIZE;
            int to = from + MAXIMUM_UDP_CHUNK_SIZE;
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
        
    protected void sendDatagrams(List<byte[]> bytesList) throws IOException {
    	if (proto != Protocol.UDP) {
    		throw new IOException("Invalid protocol!");
    	}
    	
    	if (udpSocket == null) {
    		findDestination();
    		initiateSocket();
    	}
    	
    	// int c = 0;
        for (byte[] bytes : bytesList) {
            DatagramPacket datagramPacket = new DatagramPacket(bytes, bytes.length, destination, port);
            try {
                udpSocket.send(datagramPacket);
                // c += bytes.length;
            } catch (IOException e) {
            	System.err.println("Failed to send to UDP packet: " + e);
                break;
            }
        }
        
        // System.out.println("GelfSender: sent " + c + " bytes to " + destination + ":" + port);
    }

    public void close() {
    	if (udpSocket != null) {
    		udpSocket.close();
    		udpSocket = null;
    	}
    	
    	if (tcpChannel != null) {
    		try {
				tcpChannel.close();
			} catch (IOException e) {
				System.err.println("Failed to send to close TCP channel: " + e);
			}
    		tcpChannel = null;
    	}
    }

	/**
	 * Getter for {@link GelfSender#host}.
	 */
	public String getHost() {
		return host;
	}

	/**
	 * Setter for {@link GelfSender#host}.
	 */
	public void setHost(String host) {
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
	
	public static String findLocalHostName() {
        try {
        	return InetAddress.getLocalHost().getHostName();
        } catch (IOException e) {
        	// Don`t care
        	e.printStackTrace();
        	return null;
        }
    }
}
