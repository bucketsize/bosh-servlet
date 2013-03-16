/*
    Request: The bosh request from client.

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

import javax.servlet.http.HttpServletRequest;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class Request
{
	private Document doc;
	private Node rootNode;
	private NamedNodeMap attributes;
	private boolean isSessCreationRequest;
	private transient HttpServletRequest webRequest;
	
	public Request(Document doc, HttpServletRequest webRequest)
	{
		this.doc = doc;
		rootNode = doc.getDocumentElement();
		attributes = rootNode.getAttributes();
		this.webRequest = webRequest; 
	}
	public Document getDoc() {
		return doc;
	}
	public void setDoc(Document doc) {
		this.doc = doc;
	}
	public Node getRootNode() {
		return rootNode;
	}
	public void setRootNode(Node rootNode) {
		this.rootNode = rootNode;
	}
	public NamedNodeMap getAttributes() {
		return attributes;
	}
	public void setAttributes(NamedNodeMap attributes) {
		this.attributes = attributes;
	}
	public boolean isSessCreationRequest() {
		return isSessCreationRequest;
	}
	public void setSessCreationRequest(boolean isSessCreationRequest) {
		this.isSessCreationRequest = isSessCreationRequest;
	}
	public HttpServletRequest getWebRequest() {
		return webRequest;
	}
	public void setWebRequest(HttpServletRequest webRequest) {
		this.webRequest = webRequest;
	}
	

}
