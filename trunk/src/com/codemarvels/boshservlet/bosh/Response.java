/*
    Response: The bosh response to client.

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

package com.codemarvels.boshservlet.bosh;

import java.io.StringWriter;

import javax.servlet.http.HttpServletRequest;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.codemarvels.boshservlet.session.BOSHSession;
import com.codemarvels.boshservlet.utils.AppLogger;

public class Response
{
	private static TransformerFactory tff = TransformerFactory.newInstance();
	public static final String STATUS_LEAVING = "leaving";
	public static final String STATUS_PENDING = "pending";
	public static final String STATUS_DONE = "done";

	private long cDate;
	private Document doc;
	private Element body;
	private long rid;

	private String contentType = BOSHSession.DEFAULT_CONTENT_TYPE;

	private String status;

	private HttpServletRequest req;

	private boolean aborted;

	/**
	 * creates new high level response object specific to http binding
	 * responses
	 *
	 * @param response low level response object
	 * @param doc empty document to start with
	 */
	public Response(Document doc) {
		this.doc = doc;

		this.body = this.doc.createElement("body");
		this.doc.appendChild(this.body);

		this.body.setAttribute("xmlns","http://jabber.org/protocol/httpbind");

		this.cDate = System.currentTimeMillis();

		setStatus(STATUS_PENDING);
	}

	/**
	 * adds an attribute to request's body element
	 *
	 * @param key	attribute key
	 * @param val	attribute value
	 * @return	the response
	 */
	public synchronized Response setAttribute(String key, String val) {
		this.body.setAttribute(key,val);
		return this;
	}

	/**
	 * sets content type header value of low-level response object
	 *
	 * @param type	the content-type definition e.g. 'text/xml'
	 * @return the response
	 */
	public synchronized Response setContentType(String type) {
		this.contentType = type;
		return this;
	}

	/**
	 * adds node as child of replies body element
	 *
	 * @param n The node to add
	 * @return Returns the response again
	 */
	public synchronized Response addNode(Node n, String ns)
	{
		try
		{
			if (ns!=null && !((Element) n).getAttribute("xmlns").equals(ns))
			{
				((Element) n).setAttribute("xmlns",ns);
			}
		} catch (ClassCastException e)
		{
			AppLogger.error(e.getMessage(), e);
		}
		this.body.appendChild(this.doc.importNode(n,true));
		return this;
	}
	
	public synchronized Response reset() {
		NodeList childNodes = this.body.getChildNodes();
		for(int i=0;i<childNodes.getLength();i++) {
			this.body.removeChild(childNodes.item(i));
		}
		return this;
	}

	public synchronized String getResponseXML() {
		StringWriter strWtr = new StringWriter();
		StreamResult strResult = new StreamResult(strWtr);
		try {
			Transformer tf = tff.newTransformer();
			tf.setOutputProperty("omit-xml-declaration", "yes");
			tf.transform(new DOMSource(this.doc.getDocumentElement()), strResult);
			setStatus(STATUS_DONE);
			return strResult.getWriter().toString();
		} catch (Exception e) {
			AppLogger.info("XML.toString(Document): " + e);
		}
		return null;
	}
	public synchronized void addNodes(NodeList nodeList)
	{
		Node node;
		for (int i = 0; i < nodeList.getLength(); i++)
		{
			node = nodeList.item(i);
			if(node.getNodeName().equalsIgnoreCase("streamInfo"))
			{
				setAttribute("stream", node.getAttributes().getNamedItem("name").getNodeValue());
				continue;
			}
			addNode(node,null);
		}
	}


	/**
	 * @return Returns the status.
	 */
	public synchronized String getStatus() {
		return status;
	}
	/**
	 * @param status The status to set.
	 */
	public synchronized void setStatus(String status) {
		AppLogger.info("response status "+status+" for "+this.getRID());
		this.status = status;
	}

	public long getRID() { return this.rid; }

	public Response setRID(long rid) {
		this.rid = rid;
		return this;
	}
	/**
	 * @return Returns the cDate.
	 */
	public synchronized long getCDate() {
		return cDate;
	}

	/**
	 * @return the req
	 */
	public synchronized HttpServletRequest getReq() {
		return req;
	}

	/**
	 * @param req the req to set
	 */
	public synchronized void setReq(HttpServletRequest req) {
		this.req = req;
	}

	/**
	 * @return the aborted
	 */
	public synchronized boolean isAborted() {
		return aborted;
	}

	/**
	 * @param aborted the aborted to set
	 */
	public synchronized void setAborted(boolean aborted) {
		this.aborted = aborted;
	}

	public String getContentType() {
		return contentType;
	}


}
