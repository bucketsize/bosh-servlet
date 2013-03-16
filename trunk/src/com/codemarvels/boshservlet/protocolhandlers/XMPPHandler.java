/*
    Request: Implementation of XMPP over BOSH

    Copyright (C) 2010 Stefan Strigler, Sreekumar.K.J

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

package com.codemarvels.boshservlet.protocolhandlers;

import java.io.IOException;
import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.codemarvels.boshservlet.bosh.Request;
import com.codemarvels.boshservlet.bosh.Response;
import com.codemarvels.boshservlet.utils.AppLogger;
import com.codemarvels.boshservlet.utils.Utils;

public class XMPPHandler extends AbstractProtocolHandler
{
	static final String PROLOG = "<\\?xml.*?\\?>";
	static final String STREAM_END_TAG = "</stream:stream>";
	static Logger logger = Logger.getLogger(XMPPHandler.class);
	
	
	@Override
	public void handleRequest(Request boshRequest)
	{
		String stanza;
		Node rootNode = boshRequest.getRootNode();
		if(stream.isRestartNow())
		{
			stream.sendToServer(getStreamInitXML());
		}
		if (rootNode.hasChildNodes())
		{
			stanza = Utils.serialize(rootNode.getChildNodes());
			stream.sendToServer(stanza);
		}
		if (stream.isClosed())
		{
			stream.dispatchToClient(getTerminationNodeList(stream.getName(), "remote-stream-error"));
		}
	}
	@Override
	public boolean isReadableMessage(String message)
	{
		stream.deleteFromBakEndMessages(PROLOG);
		message = stream.getBakEndMessages();
		try
		{
			db.parse(new InputSource(new StringReader("<doc>"+message+"</doc>")));
			return true;
		} catch (SAXException e)
		{
			int streamStartIndex = message.indexOf("<stream:stream");
			int streamEndIndex;
			if(streamStartIndex>=0)
			{
				streamEndIndex = message.indexOf(">", streamStartIndex);
				try {
					Document doc = db.parse(new InputSource(new StringReader("<doc>"+message.substring(streamStartIndex,streamEndIndex+1)+"</stream:stream></doc>")));
					String streamID = doc.getDocumentElement().getChildNodes().item(0).getAttributes().getNamedItem("id").getNodeValue();
					stream.setServersideID(streamID);
				} catch (SAXException e1) {
					AppLogger.error(e1.getMessage(),e1);
				} catch (IOException e1) {
					AppLogger.error(e1.getMessage(),e1);
				}
				stream.deleteFromBakEndMessages(streamStartIndex, streamEndIndex+1);
				message = stream.getBakEndMessages();
			}

			streamStartIndex = message.indexOf(STREAM_END_TAG);
			if(streamStartIndex>=0)
			{
				stream.close();
				stream.deleteFromBakEndMessages(streamStartIndex, streamStartIndex+STREAM_END_TAG.length());
				return true;
			}

			try
			{
				db.parse(new InputSource(new StringReader("<doc>"+message+"</doc>")));
				return true;
			} catch (SAXException e1)
			{
				return false;
			} catch (IOException e1) {
				AppLogger.error(e.getMessage(),e);
			}

		} catch (IOException e)
		{
			AppLogger.error(e.getMessage(),e);
		}
		return false;
	}
	@Override
	public void startSession(Request boshRequest,Response boshResponse)
	{
		String prologRegex = "<\\?xml.*?\\?>";
		String prologNStreamOpenerRegex = "<stream:stream .+?>";
		String streamFeaturesCompletionRegex = ".*</stream:features>";
		stream.sendToServer(getStreamInitXML());
		int bytesRead;
		char messageBytes[] = new char[1024];
		StringBuilder bakEndMessages = new StringBuilder();
		String message = "";
		boolean secureConnectionRequested = false;
		int trials = 0;
		while(trials < 7) {
			try
			{
				bytesRead = stream.getBakEndSrvInputStream().read(messageBytes);
				if(bytesRead>0)
				{
					bakEndMessages.append(new String(messageBytes, 0,bytesRead));
					message = bakEndMessages.toString();
					message = message.replaceFirst(prologRegex, "");
					message = message.replaceFirst(prologNStreamOpenerRegex, "");
					if(message.matches(streamFeaturesCompletionRegex)) {
						break;
					}
				}
				Thread.sleep(20);
			}
			catch (Exception e)
			{
				logger.error(e.getMessage(), e);
				logger.info("Closed back-end connection to Host : "+stream.getTargetHost());
				stream.close();
				break;
			}
		}
		try {
			if(stream.isSecure()) {
				boshResponse.reset();
			}
			DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
			Document doc = docBuilder.parse(new InputSource(new StringReader(message)));
			NodeList messageNodes = doc.getDocumentElement().getChildNodes();
			if (messageNodes != null) {
				boshResponse.setAttribute("authid", stream.getServersideID());
				for (int i = 0; i < messageNodes.getLength(); i++) {
					boshResponse.addNode(messageNodes.item(i), "");
					if (messageNodes.item(i).getNodeName().equals("starttls")) {
						secureConnectionRequested = true;
					}
				}
			}
			if( !stream.isSecure() && secureConnectionRequested) {
				stream.sendToServer("<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>");
				bytesRead=0;
				bakEndMessages.setLength(0);
				while(bytesRead==0) {
					bytesRead = stream.getBakEndSrvInputStream().read(messageBytes);
				}
				message = new String(messageBytes,0, bytesRead);
				if(message.matches(".*<proceed.*")) {
					if(stream.connectTLS())
					{
						stream.clearBakEndMessages();
						stream.setSecure(true);
						startSession(boshRequest, boshResponse);
					} else {
						boshResponse.setAttribute("type", "terminate");
						boshResponse.setAttribute("condition","remote-stream-error");
						stream.close();
						return;
					}
				}
			}
			
		} catch (Exception e) {
			logger.error(e,e);
			boshResponse.setAttribute("type", "terminate");
			boshResponse.setAttribute("condition","remote-stream-error");
		}
		stream.setInitialized(true);
	}

	private String getStreamInitXML()
	{
		return "<stream:stream to='" + stream.getTargetDomain() + "'"
		+ " xmlns='jabber:client' "
		+ " xmlns:stream='http://etherx.jabber.org/streams'"
		+ " version='1.0'" + ">";
	}

	public void restartSession(Request boshRequest,Response boshResponse)
	{
		stream.setInitialized(false);
		stream.sendToServer(getStreamInitXML());

		boolean receivedStreamFeatures = false;
		while(!receivedStreamFeatures)
		{
 			NodeList messageNodes = readBkEndMsgsForSessionCreation();

			if (messageNodes != null)
			{
				boshResponse.setAttribute("authid", stream.getServersideID());
				for (int i = 0; i < messageNodes.getLength(); i++)
				{
					if (!messageNodes.item(i).getNodeName().equals("starttls"))
					{
						if(messageNodes.item(i).getNodeName().equals("stream:features"))
						{
							boshResponse.setAttribute("xmlns:stream","http://etherx.jabber.org/streams");
							receivedStreamFeatures = true;
						}
						boshResponse.addNode(messageNodes.item(i), "");
					}
				}
			}
		}

		if (stream.isClosed())
		{
			boshResponse.setAttribute("type", "terminate");
			boshResponse.setAttribute("condition","remote-stream-error");
		}
		stream.setInitialized(true);
	}

	@Override
	public void onMessageArrival(String message) {
		
		if(message.contains("<ping")) {
			if(message.contains("urn:xmpp:ping")) {
				String idRegex = "(.*)id=['\"]([^'\"]*)['\"](.*)";
				String clientRegex = "(.*)to=['\"]([^'\"]*)['\"](.*)";
				Pattern pattern = Pattern.compile(idRegex);
				Matcher matcher = pattern.matcher(message);
				if(matcher.matches()) {
					String id = matcher.group(2);
					pattern = Pattern.compile(clientRegex);
					matcher = pattern.matcher(message);
					if(matcher.matches()) {
						String from = matcher.group(2);
						String pong = "<iq from='{from}' to='{domain}' id='{id}' type='result'/>";
						pong = pong.replace("{domain}", stream.getTargetDomain()).replace("{from}", from).replace("{id}", id);
						stream.sendToServer(pong);
						logger.debug("Sent to server :" +pong);
					}
				}
				return;
			}
		}
		Document doc;
		try {
			doc = db.parse(new InputSource(new StringReader("<doc>" + message + "</doc>")));
		} catch (SAXException e) {
			AppLogger.error(e.getMessage(), e);
			return;
		} catch (IOException e) {
			AppLogger.error(e.getMessage(), e);
			return;
		}

		NodeList serverResponseNodes = doc.getFirstChild().getChildNodes();
		
		Document toClient = db.newDocument();
		toClient.appendChild(toClient.createElement("doc"));
		Node rootNode = toClient.getDocumentElement();

		Element streamInfo = toClient.createElement("streamInfo");
		streamInfo.setAttribute("name", stream.getName());

		if (serverResponseNodes != null)
		{
			for (int i = 0; i < serverResponseNodes.getLength(); i++)
			{
				rootNode.appendChild(toClient.adoptNode(serverResponseNodes.item(i)));
			}
			rootNode.appendChild(streamInfo);
		}
		stream.dispatchToClient(rootNode.getChildNodes());
	}

}
