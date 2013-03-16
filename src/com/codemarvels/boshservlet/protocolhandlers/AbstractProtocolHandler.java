/*
    AbstractProtocolHandler: The base class for all the protocol handlers.

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

package com.codemarvels.boshservlet.protocolhandlers;

import java.io.IOException;
import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.codemarvels.boshservlet.bosh.Request;
import com.codemarvels.boshservlet.bosh.Response;
import com.codemarvels.boshservlet.session.Stream;
import com.codemarvels.boshservlet.utils.AppLogger;

public abstract class AbstractProtocolHandler
{
	Stream stream;
	protected DocumentBuilder db;
	protected AbstractProtocolHandler()
	{
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(true);
		try
		{
			db = dbf.newDocumentBuilder();
		} catch (ParserConfigurationException e)
		{
			AppLogger.error("failed to create DocumentBuilderFactory", e);
		}
	}


	public NodeList readBkEndMsgsForSessionCreation()
	{
		String message = stream.getBakEndMessages();
		while(message.length()==0 || !isReadableMessage(message))
		{
			message = stream.getBakEndMessages();
			try
			{
				Thread.sleep(20);
			} catch (InterruptedException e) {
				AppLogger.error(e.getMessage(), e);
			}
		}

		try
		{
			Document doc = db.parse(new InputSource(new StringReader("<doc>" + message + "</doc>")));
			stream.clearBakEndMessages();
			return doc.getDocumentElement().getChildNodes();
		}catch (SAXException e)
		{
			AppLogger.error(e.getMessage(),e);
		}catch (IOException e)
		{
			AppLogger.error(e.getMessage(),e);
		}
		return null;
	}

	public NodeList getTerminationNodeList(String stream, String condition)
	{
		Document toClient = db.newDocument();
		toClient.appendChild(toClient.createElement("doc"));
		Node rootNode = toClient.getDocumentElement();
		Element terminationInfo = toClient.createElement("terminate");
		terminationInfo.setAttribute("condition", condition);
		terminationInfo.setAttribute("stream", stream);
		rootNode.appendChild(terminationInfo);
		return rootNode.getChildNodes();
	}

	public abstract void handleRequest(Request request);
	public abstract void startSession(Request request, Response response);
	public abstract boolean isReadableMessage(String message);
	public abstract void onMessageArrival(String message);
	public abstract void restartSession(Request request, Response response);
	public Stream getStream() {
		return stream;
	}

	public void setStream(Stream stream) {
		this.stream = stream;
	}
}
