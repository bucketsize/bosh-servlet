/*
    BoshSession - The client session.

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

package com.codemarvels.boshservlet.session;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.w3c.dom.NodeList;

import com.codemarvels.boshservlet.bosh.Response;
import com.codemarvels.boshservlet.utils.AppLogger;
import com.codemarvels.boshservlet.utils.Utils;

public class BOSHSession
{
	public static final float VERSION = 1.6f;
	public static final int MIN_POLLING = 10;
	public static final int MAX_INACTIVITY = 600;
	public static final int MAX_REQUESTS = 2;
	public static final String DEFAULT_CONTENT_TYPE = "text/xml; charset=utf-8";
	private String ID;
	private String contentType;
	private String userID;
	private int hold;
	private float clientBoshVersion;
	private int secondsToWait;
	private boolean useAck;
	private String key;
	private boolean isClosed = false;
	private long lastRID;
	private long lastPollTime;
	private boolean restartNow;
	private String authID;
	private boolean authIDSent;
	private Map<String, Object> properties = new HashMap<String, Object>();
	private List<Stream> streams = Collections.synchronizedList(new ArrayList<Stream>());
	private Map<String, Stream> streamLookup = Collections.synchronizedMap(new HashMap<String, Stream>());
	private Map<Long, Response> responseLookup =  Collections.synchronizedMap(new HashMap<Long, Response>());
	private List<Response> boshResponses = Collections.synchronizedList(new LinkedList<Response>());
	private ConcurrentLinkedQueue<NodeList> dispatchQ = new ConcurrentLinkedQueue<NodeList>();
	private boolean aborted = false;

	public Stream addStream(String targetDomain, String route) throws NumberFormatException, IOException
	{
		String streamName;
		while (streamLookup.get(streamName = Utils.createRandomID(20)) != null)
		{
			;//wait till the ID created is unique
		}
		String host, protocol;
		int port;
		if(route!=null) {
			String[] routeParts = route.split(":");
			host = routeParts[1];
			port = Integer.valueOf(routeParts[2]);
			protocol = routeParts[0];
		} else {
			host = targetDomain;
			port = 5222;
			protocol="xmpp";
		}
		Stream stream = new Stream(streamName, targetDomain, host, port , protocol,streams.size(),this);
		streams.add(stream);
		streamLookup.put(stream.getName(), stream);
		return stream;
	}
	public void addResponse(Response boshResponse)
	{
		responseLookup.put(boshResponse.getRID(), boshResponse);
		boshResponses.add(boshResponse);
	}

	public void removeResponse(Response boshResponse)
	{
		responseLookup.remove(boshResponse.getRID());
		boshResponses.remove(boshResponse);
	}

	public Response getBoshResponse(long responseID)
	{
		return responseLookup.get(responseID);
	}

	public boolean checkValidRID(long rid)
	{
		if(boshResponses.size()==0)
		{
			return true;
		}
		int lastMessageIndex = boshResponses.size()-1;
		long lastRID = boshResponses.get(lastMessageIndex).getRID();
		long firstRID = boshResponses.get(0).getRID();
		try
		{
			if (rid <= lastRID
					+ MAX_REQUESTS
					&& rid >= firstRID)
				return true;
			else
			{
				AppLogger.info("invalid request id: " + rid + " (last: "+ lastRID + ")");
				return false;
			}
		}
		catch (NoSuchElementException e)
		{
			return false;
		}
	}

	public int numPendingRequests()
	{
		return boshResponses.size();
	}

	public Stream getStream(String streamID)
	{
		Stream reqStream = null;
		Stream firstStream = streams.get(0);
		if(streamID!=null)
		{
			reqStream = streamLookup.get(streamID);
		}
		if(reqStream==null)
		{
			reqStream = firstStream;
		}
		return reqStream;
	}

	public void close()
	{
		for(int i=0;i<streams.size();i++)
		{
			streams.get(i).close();
		}
		streams.clear();
		streamLookup.clear();
		isClosed = true;
	}

	public boolean isAllStreamsClosed()
	{
		int numOfClosed = 0;
		for(int i=0;i<streams.size();i++)
		{
			if(streams.get(i).isClosed())
				numOfClosed++;
		}
		if(numOfClosed==streams.size())
		{
			streams.clear();
			streamLookup.clear();
			isClosed = true;
			return true;
		}
		return false;
	}
	public void registerLastPollTime() {
		this.lastPollTime = System.currentTimeMillis();
	}
	public String getContentType() {
		return contentType;
	}
	public void setContentType(String contentType) {
		this.contentType = contentType;
	}
	public String getUserID() {
		return userID;
	}
	public void setUserID(String userID) {
		this.userID = userID;
	}
	public int getHold() {
		return hold;
	}
	public void setHold(int hold) {
		this.hold = hold;
	}
	public float getClientBoshVersion() {
		return clientBoshVersion;
	}
	public void setClientBoshVersion(float clientBoshVersion) {
		this.clientBoshVersion = clientBoshVersion;
	}
	public int getSecondsToWait() {
		return secondsToWait;
	}
	public void setSecondsToWait(int secondsToWait) {
		this.secondsToWait = secondsToWait;
	}
	public boolean isUseAck() {
		return useAck;
	}
	public void setUseAck(boolean useAck) {
		this.useAck = useAck;
	}
	public String getID() {
		return ID;
	}
	public void setID(String id) {
		ID = id;
	}
	public String getKey() {
		return key;
	}
	public void setKey(String key) {
		this.key = key;
	}
	public boolean isClosed() {
		return isClosed;
	}
	public void setClosed(boolean isClosed) {
		this.isClosed = isClosed;
	}
	public long getLastRID() {
		return lastRID;
	}

	public void setLastRID(long lastRID) {
		this.lastRID = lastRID;
	}
	public boolean isRestartNow() {
		return restartNow;
	}
	public void setRestartNow(boolean restartNow) {
		this.restartNow = restartNow;
	}
	public long getLastPollTime() {
		return lastPollTime;
	}
	public void setLastPollTime(long lastPollTime) {
		this.lastPollTime = lastPollTime;
	}
	public String getAuthID() {
		return authID;
	}
	public void setAuthID(String authID) {
		this.authID = authID;
	}
	public void setProperty(String name, Object value)
	{
		properties.put(name, value);
	}
	public Object get(String propertyName)
	{
		return properties.get(propertyName);
	}
	public boolean isAuthIDSent() {
		return authIDSent;
	}
	public void setAuthIDSent(boolean authIDSent) {
		this.authIDSent = authIDSent;
	}
	public void addToDispatchQ(NodeList nodeList)
	{
		dispatchQ.add(nodeList);
	}
	
	public void abort(Response response) {
		removeResponse(response);
		setLastRID(response.getRID());
		aborted = true;
	}
	public boolean isAborted(){
		return aborted;
	}
	public NodeList getFromDispatchQ(long requestInTime, Response response)
	{
		while(dispatchQ.size()==0)
		{
			try
			{
				Thread.sleep(50);
			} catch (InterruptedException e)
			{
				e.printStackTrace();
				abort(response);
				return null;
			}
			if ((hold == 0  && System.currentTimeMillis()- requestInTime > 200)	||
					(hold > 0 && ((System.currentTimeMillis()- requestInTime >= secondsToWait * 1000) || numPendingRequests() > hold  )))
			{
				break;
			}
		}
		if(dispatchQ.size()>0) {
			NodeList message = dispatchQ.poll();
			return message;
		}
		else {
			return null;
		}
	}
	public void fillInSettings(Response response)
	{
		response.setAttribute("sid", getID());
		response.setAttribute("wait", String.valueOf(getSecondsToWait()));
		response.setAttribute("inactivity", String.valueOf(MAX_INACTIVITY));
		response.setAttribute("polling", String.valueOf(MIN_POLLING));
		response.setAttribute("requests", String.valueOf(MAX_REQUESTS));

	}
}
