/*
    Stream object - represents one communication stream to server.

    Copyright (C) 2010 Sreekumar.K.J

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.codemarvels.boshservlet.session;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.Socket;
import java.util.Enumeration;
import java.util.Properties;
import java.util.ResourceBundle;

import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.apache.log4j.Logger;
import org.w3c.dom.NodeList;

import com.codemarvels.boshservlet.bosh.Request;
import com.codemarvels.boshservlet.bosh.Response;
import com.codemarvels.boshservlet.protocolhandlers.AbstractProtocolHandler;
import com.codemarvels.boshservlet.utils.AppLogger;

public class Stream implements Runnable
{
	static final String PROLOG = "<?xml version='1.0'?>";
	private static Logger logger;
	private static Properties protocolHandlers;
	public static final int BUFFER_SIZE = 512;
	private boolean isClosed = false;
	private boolean tlsHandShakeComplete = false;
	private BOSHSession session;
	private String errorCondition;
	private boolean isInitialized = false;

	private String name;
	private String serversideID;
	private String targetDomain;
	private String targetHost;
	private int targetPort;
	private String targetProtocol;
	private String language;
	private boolean isSecure;
	private Socket bakEndSrvSocket;
	private Reader bakEndSrvInputStream;
	private OutputStreamWriter bakEndSrvOutputStream;
	private StringBuffer bakEndMessages = new StringBuffer();
	private String boshResponse;
	private boolean restartNow = false;
	private AbstractProtocolHandler protocolHandler;

	static
	{
		logger = Logger.getLogger(Stream.class);
		Properties props = new Properties();
		ResourceBundle resourceBundle = ResourceBundle.getBundle("ProtocolHandlers");
		for (Enumeration<String> keys = resourceBundle.getKeys (); keys.hasMoreElements ();)
		{
			final String key = (String) keys.nextElement ();
			final String value = resourceBundle.getString (key);
			props.put (key, value);
		}
        protocolHandlers = props;

	}
	public Stream(String name, String domain, String host, int port, String protocol, int streamNumber, BOSHSession session) throws IOException
	{
		targetDomain = domain;
		targetHost = host;
		targetPort = port;
		targetProtocol = protocol;
		this.session = session;
		this.name = name;
		String protocolHandlerClassName = protocolHandlers.getProperty(protocol.trim());
		try
		{
			protocolHandler = (AbstractProtocolHandler)Class.forName(protocolHandlerClassName).newInstance();
			protocolHandler.setStream(this);
		} catch (Exception e)
		{
			throw new IOException(e);
		}
	}


	@Override
	public void run()
	{
		try
		{
			int bytesRead;
			char messageBytes[] = new char[BUFFER_SIZE];
			while(!bakEndSrvSocket.isClosed())
			{
				try
				{
					bytesRead = bakEndSrvInputStream.read(messageBytes);
					if(bytesRead>0)
					{
						synchronized (bakEndMessages)
						{
							bakEndMessages.append(new String(messageBytes, 0,bytesRead));
						}
						if(protocolHandler.isReadableMessage(bakEndMessages.toString()))
						{
							if(isInitialized)
							{
								if(bakEndMessages.length()>0)
								{
									protocolHandler.onMessageArrival(bakEndMessages.toString().replace(PROLOG, ""));
									clearBakEndMessages();
								}
							}
						}

					}
					Thread.sleep(10);
				}
				catch (Exception e)
				{
					logger.error(e.getMessage(), e);
					logger.info("Closed back-end connection to Host : "+targetHost);
					close();
					break;
				}
				if(isClosed)
				{
					break;
				}
			}

			close();

		} catch (Exception e)
		{
			logger.error(e.getMessage(), e);
		}
	}

	public void handleRequest(Request request)
	{
		protocolHandler.handleRequest(request);
	}
	public void startStream(Request request, Response response)
	{
		try {
			connect();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		protocolHandler.startSession(request, response);
	}
	public void restartServerSession(Request request, Response response)
	{
		protocolHandler.restartSession(request, response);
	}

	
	public void sendToServer(String payload)
	{
		synchronized (bakEndSrvOutputStream)
		{
			try {
				bakEndSrvOutputStream.write(payload);
				bakEndSrvOutputStream.flush();
				System.out.println("^^^^send^^^^\n"+payload);
			} catch (IOException ie) {
				close();
			}
		}
	}
	public void close()
	{
		try
		{
			isClosed = true;
			synchronized (bakEndSrvOutputStream)
			{
				if (!bakEndSrvSocket.isClosed())
				{
					try {
						bakEndSrvOutputStream.write("</stream:stream>");
						bakEndSrvOutputStream.flush();
					} catch (IOException ie) {
					}
				}
			}
			bakEndSrvInputStream.close();
			bakEndSrvOutputStream.close();
			bakEndSrvSocket.close();
		} catch (IOException e)
		{
			logger.error(e.getMessage(), e);
		}
	}
	public boolean connectTLS() throws IOException
	{
		try
		{
			SSLSocketFactory sslFact = (SSLSocketFactory) SSLSocketFactory.getDefault();
			SSLSocket tls = (SSLSocket) sslFact.createSocket(	bakEndSrvSocket, targetHost, 5223, false);
			tls.addHandshakeCompletedListener(new HandShakeFinished());
			tlsHandShakeComplete = false;
			tls.startHandshake();
			try {
				while (!tlsHandShakeComplete)
				{
					Thread.sleep(10);
				}
			} catch (InterruptedException ire)
			{
				if(!tlsHandShakeComplete)
					return false;
			}
			bakEndSrvSocket = tls;
			bakEndSrvInputStream =new BufferedReader(new InputStreamReader(tls.getInputStream(), "UTF-8"));
			bakEndSrvOutputStream = new OutputStreamWriter(bakEndSrvSocket.getOutputStream(), "UTF-8");
			return true;
		}catch (Exception e)
		{
			return false;
		}
	}
	public void connect() throws IOException
	{
		bakEndSrvSocket = new Socket(targetDomain,targetPort);
		bakEndSrvInputStream = new BufferedReader(new InputStreamReader(bakEndSrvSocket.getInputStream(), "UTF-8"));
		bakEndSrvOutputStream = new OutputStreamWriter(bakEndSrvSocket.getOutputStream(),"UTF-8");
	}
	private class HandShakeFinished implements	HandshakeCompletedListener
	{
		public void handshakeCompleted(HandshakeCompletedEvent event)
		{
			tlsHandShakeComplete = true;
		}
	}
	public String getBakEndMessages()
	{
		synchronized (bakEndMessages)
		{
			return bakEndMessages.toString().replace(PROLOG, "");
		}
	}


	public void clearBakEndMessages()
	{
		int len = bakEndMessages.length();
		if(len==0)
			return;
		synchronized (bakEndMessages)
		{
			bakEndMessages.delete(0, len);
		}
	}
	public void deleteFromBakEndMessages(int startIndex, int endIndex)
	{
		synchronized (bakEndMessages)
		{
			bakEndMessages.delete(startIndex, endIndex);
		}
	}
	public void deleteFromBakEndMessages(String messageToBDeleted)
	{
		synchronized (bakEndMessages)
		{
			String messageNow = bakEndMessages.toString().replaceFirst(messageToBDeleted, "");
			bakEndMessages.setLength(0);
			bakEndMessages.append(messageNow);
		}
	}
	
	public void dispatchToClient(NodeList nodeList)
	{
		session.addToDispatchQ(nodeList);
	}

	public String getTargetDomain() {
		return targetDomain;
	}
	public void setTargetDomain(String targetDomain) {
		this.targetDomain = targetDomain;
	}
	public String getTargetHost() {
		return targetHost;
	}
	public void setTargetHost(String targetHost) {
		this.targetHost = targetHost;
	}
	public int getTargetPort() {
		return targetPort;
	}
	public void setTargetPort(int targetPort) {
		this.targetPort = targetPort;
	}
	public String getTargetProtocol() {
		return targetProtocol;
	}
	public void setTargetProtocol(String targetProtocol) {
		this.targetProtocol = targetProtocol;
	}
	public boolean isClosed() {
		return isClosed;
	}

	public void setClosed(boolean isClosed) {
		this.isClosed = isClosed;
	}

	public String getLanguage() {
		return language;
	}
	public void setLanguage(String language) {
		this.language = language;
	}
	public Reader getBakEndSrvInputStream() {
		return bakEndSrvInputStream;
	}
	public void setBakEndSrvInputStream(Reader bakEndSrvInputStream) {
		this.bakEndSrvInputStream = bakEndSrvInputStream;
	}
	public OutputStreamWriter getBakEndSrvOutputStream() {
		return bakEndSrvOutputStream;
	}

	public String getBoshResponse() {
		return boshResponse;
	}
	public void setBoshResponse(String boshResponse) {
		this.boshResponse = boshResponse;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	public AbstractProtocolHandler getProtocolHandler() {
		return protocolHandler;
	}
	public boolean isSecure() {
		return isSecure;
	}

	public void setSecure(boolean isSecure) {
		this.isSecure = isSecure;
	}
	public BOSHSession getSession() {
		return session;
	}

	public void setSession(BOSHSession session) {
		this.session = session;
	}
	public String getServersideID() {
		return (serversideID!=null)?serversideID:"";
	}

	public void setServersideID(String serversideID) {
		this.serversideID = serversideID;
	}

	public String getErrorCondition() {
		return errorCondition;
	}

	public void setErrorCondition(String errorCondition) {
		this.errorCondition = errorCondition;
	}

	public boolean isInitialized() {
		return isInitialized;
	}

	public void setInitialized(boolean isInitialized) {
		this.isInitialized = isInitialized;
	}


	public boolean isRestartNow() {
		return restartNow;
	}


	public void setRestartNow(boolean restartNow) {
		this.restartNow = restartNow;
	}

}
