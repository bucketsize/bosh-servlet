/*
    BoshXmppServlet: access point for bosh clients.

    Copyright (C) 2010 Sreekumar.K.J <k.j@codemarvels.com>

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

package com.codemarvels.boshservlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.codemarvels.boshservlet.bosh.ConnectionManager;
import com.google.common.io.CharStreams;

public class BoshXmppServlet extends HttpServlet
{

	private static final long serialVersionUID = -6582356799296606455L;
	private ConnectionManager connectionManager = new ConnectionManager();


	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)	throws ServletException, IOException
	{
		PrintWriter out = new PrintWriter(resp.getOutputStream());
		out.println("BOSHServlet1.0");
		out.println("An implementation of BOSH protocol with built-in XMPP over BOSH module (http://xmpp.org/extensions/xep-0124.html, " +
				"http://xmpp.org/extensions/xep-0206.html )");
		out.flush();
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)	throws ServletException, IOException
	{
		try {
			DocumentBuilderFactory fac = DocumentBuilderFactory.newInstance();
	        DocumentBuilder db = fac.newDocumentBuilder();
	        InputSource inStream = new InputSource();
	        String requestXML = CharStreams.toString(request.getReader());
	        inStream.setCharacterStream(new StringReader(requestXML));
	        Document doc = db.parse(inStream);
			connectionManager.handleRequest(doc, response, request);
		} catch (SAXException e) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST);
		} catch (Exception e) {
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,e.getMessage());
		}
	}

	@Override
	public void init() throws ServletException
	{
	}

}
