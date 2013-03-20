/*
    ConnectionManager: Implements BOSH protocol

    Copyright (C) 2010 Stefan Strigler, Sreekumar.K.J <k.j@codemarvels.com>

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

package com.codemarvels.boshservlet.bosh;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.codemarvels.boshservlet.session.BOSHSession;
import com.codemarvels.boshservlet.session.Stream;
import com.codemarvels.boshservlet.utils.AppLogger;
import com.codemarvels.boshservlet.utils.Utils;

public class ConnectionManager implements Runnable
{
	Map<String, BOSHSession> sessions = new HashMap<String, BOSHSession>();
	private DocumentBuilder db;
	public ConnectionManager()
	{
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		try
		{
			db = dbf.newDocumentBuilder();
		} catch (ParserConfigurationException e)
		{
			AppLogger.error(e.getMessage(), e);
		}
		Thread cleanupThread = new Thread(this);
		cleanupThread.setDaemon(true);
		cleanupThread.start();
	}

	public void handleRequest(Document requestXMLDoc, HttpServletResponse httpResponse, HttpServletRequest httpRequest) throws Exception
	{
		long requestID = 0;
		long requestInTime = System.currentTimeMillis();
		Node rootNode = requestXMLDoc.getDocumentElement();
		Request request = new Request(requestXMLDoc, httpRequest);
		if (rootNode == null || !rootNode.getNodeName().equals("body"))
		{
			httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST);
		}
		else
		{
			NamedNodeMap attribs = rootNode.getAttributes();
			if (attribs.getNamedItem("sid") != null)
			{
				//lookup existing session
				BOSHSession boshSession = sessions.get(attribs.getNamedItem("sid").getNodeValue());
				if (boshSession != null)
				{
					boshSession.registerLastPollTime();
					AppLogger.info("incoming request for " + boshSession.getID());
					if(returnedWithErrors(rootNode, httpResponse, boshSession))
					{
						return;
					}
					requestID = Integer.parseInt(attribs.getNamedItem("rid").getNodeValue());
					Node streamNode = request.getAttributes().getNamedItem("stream");
					String streamID = null;
					if(streamNode !=null)
					{
						streamID = streamNode.getNodeValue();
					}

					Stream stream = boshSession.getStream(streamID);
					while(!stream.isInitialized())
					{
						Thread.sleep(50);
					}
					Response boshResponse = new Response(db.newDocument());
					boshResponse.setRID(requestID);
					boshResponse.setContentType(boshSession.getContentType());
					boshSession.addResponse(boshResponse);
					try
					{
						long lastrid = boshSession.getLastRID();
						while (requestID != lastrid + 1)
						{
							if(requestID <= lastrid) {
								//possibly a faulty resume.
								break;
							}
							if (boshSession.isClosed())
							{
								AppLogger.info("session terminated for " + requestID);
								httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND);
								return;
							}
							try
							{
								Thread.sleep(50);
								lastrid = boshSession.getLastRID();
							} catch (InterruptedException e)
							{
								boshSession.abort(boshResponse);
								return;
							}
						}
						AppLogger.info("handling response " + requestID);
						//check key
						String key = boshSession.getKey();
						if (key != null)
						{
							AppLogger.info("checking keys for " + requestID);
							/*if (attribs.getNamedItem("key") == null	|| !sha1(attribs.getNamedItem("key").getNodeValue()).equals(key))
							{
								AppLogger.info("Key sequence error");
								httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND);
								closeSession(boshSession);
								return;
							}*/
							if (attribs.getNamedItem("newkey") != null)
							{
								boshSession.setKey(attribs.getNamedItem("newkey").getNodeValue());
							}
							else
							{
								boshSession.setKey(attribs.getNamedItem("key").getNodeValue());
							}
							AppLogger.info("key valid for " + requestID);
						}

						//check if polling too frequently
						long now = System.currentTimeMillis();
						if (boshSession.getHold() == 0 && now - boshSession.getLastPollTime() < BOSHSession.MIN_POLLING * 1000)
						{
							AppLogger.info("polling too frequently! [now:"
									+ now + ", last:"
									+ boshSession.getLastPollTime() + "("
									+ (now - boshSession.getLastPollTime())+ ")]");
							httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
							closeSession(boshSession);
							return;
						}

						/* request to terminate session? */
						if (attribs.getNamedItem("type") != null)
						{
							String requestType = attribs.getNamedItem("type").getNodeValue();
							if (requestType.equals("terminate"))
							{
								if(streamID!=null)
								{
									stream.close();
									if(boshSession.isAllStreamsClosed())
									{
										closeSession(boshSession);
										send(httpResponse, boshResponse, boshSession);
										return;
									}
								}
								else
								{
									closeSession(boshSession);
									send(httpResponse, boshResponse, boshSession);
									return;
								}
							}
						}

						if (attribs.getNamedItem("route") != null)
						{
							AppLogger.info("Create nNew Sream");
							createStream(attribs, boshSession, httpResponse, request, boshResponse);
							return;
						}

						if (attribs.getNamedItem("xmpp:restart") != null)
						{
							AppLogger.info("XMPP RESTART");
							stream.restartServerSession(request, boshResponse);
						}

						stream.handleRequest(request);

						synchronized (boshSession) {
							if(stream.isInitialized())
							{
								NodeList payLoadToClient = boshSession.getFromDispatchQ(requestInTime, boshResponse);
								if(payLoadToClient!=null) {
									boshResponse.addNodes(payLoadToClient);
								}else if(boshSession.isAborted()){
									return;
								}
							}
							/* send back response */
							send(httpResponse , boshResponse, boshSession);
						}
						
					} catch (IOException ioe)
					{
						AppLogger.error(ioe.getMessage(), ioe);
						closeSession(boshSession);
						boshResponse.setAttribute("type", "terminate");
						boshResponse.setAttribute("condition", "remote-connection-failed");
						send(httpResponse, boshResponse, boshSession);
					}
				}
				else
				{
					//if session with this ID is not found.
					httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND);
				}
			}
			else
			{
				createSession(rootNode, httpResponse, httpRequest);
			}
		}
	}
	public static String hex(byte[] array)
	{
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < array.length; ++i)
		{
			sb.append(Integer.toHexString((array[i] & 0xFF) | 0x100).toLowerCase().substring(1, 3));
		}
		return sb.toString();
	}

	public static String sha1(String message)
	{
		try
		{
			MessageDigest sha = MessageDigest.getInstance("SHA-1");
			return hex(sha.digest(message.getBytes()));
		} catch (NoSuchAlgorithmException e)
		{
			AppLogger.error(e.getMessage(), e);
		}
		return null;
	}

	private boolean returnedWithErrors(Node rootNode, HttpServletResponse httpResponse, BOSHSession boshSession ) throws Exception
	{
		NamedNodeMap attribs = rootNode.getAttributes();
		int requestID = 0;
		// check if rid valid
		if (attribs.getNamedItem("rid") == null)
		{
			// rid missing
			AppLogger.info("rid missing");
			httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND);
			closeSession(boshSession);
			return true;
		}
		try
		{
			requestID = Integer.parseInt(attribs.getNamedItem("rid").getNodeValue());
		} catch (NumberFormatException e)
		{
			AppLogger.info("rid not a number");
			httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST);
			return true;
		}
		/* Tolerate resends*/
		Response r = boshSession.getBoshResponse(requestID);
		if (r != null)
		{ // re-send
			AppLogger.info("resend rid " + requestID);
			r.setAborted(true);
			send(httpResponse, r, boshSession);
			return true;
		}
		if(requestID< boshSession.getLastRID()) {
			AppLogger.info("Less than last processed rid :" + requestID+" < "+boshSession.getLastRID());
			httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND);
			closeSession(boshSession);
			return true;
			
		}
		if (!boshSession.checkValidRID(requestID))
		{
			AppLogger.info("invalid rid " + requestID);
			httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND);
			closeSession(boshSession);
			return true;
		}
		AppLogger.info("found valid rid " + requestID);

		/* too many simultaneous requests? */
		if (boshSession.numPendingRequests() >= BOSHSession.MAX_REQUESTS)
		{
			AppLogger.info("too many simultaneous requests: "+ boshSession.numPendingRequests());
			httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
			closeSession(boshSession);
			return true;
		}
		return false;
	}
	private void createStream(NamedNodeMap attribs, BOSHSession session, HttpServletResponse httpResponse, Request request, Response boshResponse) throws IOException
	{
		// check 'route' attribute
		String route = null;
		if (attribs.getNamedItem("route") != null)
		{
			route = attribs.getNamedItem("route").getNodeValue();
		}
		// check 'to' attribute
		String to = null;
		if ((attribs.getNamedItem("to") != null)&& (attribs.getNamedItem("to").getNodeValue() != ""))
		{
			to = attribs.getNamedItem("to").getNodeValue();
		}
		if (to == null || to.equals(""))
		{
			if (attribs.getNamedItem("content") != null)
			{
				boshResponse.setContentType(attribs.getNamedItem("content").getNodeValue());
			} else
			{
				boshResponse.setContentType(BOSHSession.DEFAULT_CONTENT_TYPE);
			}
			boshResponse.setAttribute("type", "terminate");
			boshResponse.setAttribute("condition", "improper-addressing");
			send(httpResponse , boshResponse, null);
			return;
		}
		Stream newStream = session.addStream(to, route);
		newStream.startStream(request, boshResponse);
		boshResponse.setAttribute("from", newStream.getTargetDomain());
		if(newStream.isSecure())
		{
			boshResponse.setAttribute("secure", "true");
		}else
		{
			boshResponse.setAttribute("secure", "false");
		}
		send(httpResponse, boshResponse, session);
		new Thread(newStream).start();
	}
	private void createSession(Node rootNode, HttpServletResponse response, HttpServletRequest httpRequest) throws IOException
	{
		int rid;
		NamedNodeMap attribs = rootNode.getAttributes();
		//request to create a new session
		if (attribs.getNamedItem("rid") == null)
		{
			response.sendError(HttpServletResponse.SC_BAD_REQUEST);
			return;
		} else
		{
			try
			{
				rid = Integer.parseInt(attribs.getNamedItem("rid").getNodeValue());
			} catch (NumberFormatException e)
			{
				AppLogger.error(e.getMessage(), e);
				response.sendError(HttpServletResponse.SC_BAD_REQUEST);
				return;
			}
		}
		Response boshResponse = new Response(db.newDocument());
		boshResponse.setRID(rid);
		// check 'route' attribute
		String route = null;
		if (attribs.getNamedItem("route") != null)
		{
			route = attribs.getNamedItem("route").getNodeValue();
		}
		// check 'to' attribute
		String to = null;
		if ((attribs.getNamedItem("to") != null)&& (attribs.getNamedItem("to").getNodeValue() != ""))
		{
			to = attribs.getNamedItem("to").getNodeValue();
		}
		if (to == null || to.equals(""))
		{
			if (attribs.getNamedItem("content") != null)
			{
				boshResponse.setContentType(attribs.getNamedItem("content").getNodeValue());
			} else
			{
				boshResponse.setContentType(BOSHSession.DEFAULT_CONTENT_TYPE);
			}
			boshResponse.setAttribute("type", "terminate");
			boshResponse.setAttribute("condition", "improper-addressing");
			send(response , boshResponse, null);
			return;
		}
		//really create new session
		try
		{
			BOSHSession boshSession = new BOSHSession();
			Stream stream = boshSession.addStream(to, route);
			String sessionID;
			while (sessions.get(sessionID = createSessionID(24)) != null)
			{
				;//wait till the ID created is unique
			}
			boshSession.setID(sessionID);
			sessions.put(sessionID, boshSession);
			if (attribs.getNamedItem("content") != null)
			{
				boshSession.setContentType(attribs.getNamedItem("content").getNodeValue());
			}
			if (attribs.getNamedItem("wait") != null)
			{
				boshSession.setSecondsToWait(Integer.parseInt(attribs.getNamedItem("wait").getNodeValue()));
			}
			if (attribs.getNamedItem("hold") != null)
			{
				boshSession.setHold(Integer.parseInt(attribs.getNamedItem("hold").getNodeValue()));
			}
			if (attribs.getNamedItem("xml:lang") != null)
			{
				stream.setLanguage(attribs.getNamedItem("xml:lang").getNodeValue());
			}
			if (attribs.getNamedItem("newkey") != null)
			{
				boshSession.setKey(attribs.getNamedItem("newkey").getNodeValue());
			}
			boshSession.addResponse(boshResponse);
			boshResponse.setContentType(boshSession.getContentType());
			boshResponse.setAttribute("sid", boshSession.getID());
			boshResponse.setAttribute("wait", String.valueOf(boshSession.getSecondsToWait()));
			boshResponse.setAttribute("inactivity", String.valueOf(BOSHSession.MAX_INACTIVITY));
			boshResponse.setAttribute("polling", String.valueOf(BOSHSession.MIN_POLLING));
			boshResponse.setAttribute("requests", String.valueOf(BOSHSession.MAX_REQUESTS));
			Request request = new Request(rootNode.getOwnerDocument(), httpRequest);
			stream.startStream(request, boshResponse);
			if(stream.isSecure())
			{
				boshResponse.setAttribute("secure", "true");
			}
			else
			{
				boshResponse.setAttribute("secure", "false");
			}
			send(response, boshResponse, boshSession);
			new Thread(stream).start();
		} catch (NumberFormatException nfe)
		{
			AppLogger.error(nfe.getMessage(), nfe);
			try
			{
				response.sendError(HttpServletResponse.SC_BAD_REQUEST);
			} catch (IOException e)
			{

				AppLogger.error(e.getMessage(),e);
			}
			return;
		}catch (IOException ioe)
		{
			AppLogger.error(ioe.getMessage(), ioe);
			if (attribs.getNamedItem("content") != null)
			{
				boshResponse.setContentType(attribs.getNamedItem("content").getNodeValue());
			}
			else
			{
				boshResponse.setContentType(BOSHSession.DEFAULT_CONTENT_TYPE);
			}
			boshResponse.setAttribute("type", "terminate");
			boshResponse.setAttribute("condition",	"remote-connection-failed");
			send(response,boshResponse, null);
		}
	}

	private static String createSessionID(int len)
	{
		return Utils.createRandomID(len);
	}

	/**
	 * sends this response
	 */
	public void send(HttpServletResponse httpResponse, Response boshResponse, BOSHSession session)
	{
		try
		{
			String toClient= boshResponse.getResponseXML();
			httpResponse.setContentType(boshResponse.getContentType());
			httpResponse.getWriter().print(toClient);
			boshResponse.setStatus(Response.STATUS_DONE);
			httpResponse.getWriter().flush();
			AppLogger.info("sent response for "+boshResponse.getRID()+" body:"+boshResponse.getResponseXML());
			if(session != null)
			{
				session.setLastRID(boshResponse.getRID());
				session.removeResponse(boshResponse);
			}
		} catch (Exception e)
		{
			if(session.getLastRID()<boshResponse.getRID()) {
				session.setLastRID(boshResponse.getRID());
			}
			AppLogger.error(e,e);
		}
	}

	public void closeSession(BOSHSession session)
	{
		session.close();
		sessions.remove(session.getID());
	}

	public void run() 
	{
		while (true) 
		{
			for (String sessionId:sessions.keySet()) 
			{
				//TODO fix concurrent modification error here.
				BOSHSession session = sessions.get(sessionId);
				if (System.currentTimeMillis() - session.getLastPollTime() > BOSHSession.MAX_INACTIVITY * 1000) 
				{
					AppLogger.info("Terminating session:"+session.getID());
					closeSession(session);
				}
			}
			try 
			{
				Thread.sleep(300000);
			} catch (InterruptedException ie) 
			{
				AppLogger.error(ie.getMessage(), ie);
			}
		}
	}

}
