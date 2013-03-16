/*
    Utilities.

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

package com.codemarvels.boshservlet.utils;

import java.io.StringWriter;
import java.util.Random;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.NodeList;

public class Utils 
{
	private static TransformerFactory tff = TransformerFactory.newInstance();
	public static String serialize(NodeList nl)
	{
		String out = "";
		StreamResult strResult = new StreamResult();
		strResult.setWriter(new StringWriter());
		try 
		{
			Transformer tf = tff.newTransformer();
			tf.setOutputProperty("omit-xml-declaration", "yes");
			for (int i = 0; i < nl.getLength(); i++) 
			{
				tf.transform(new DOMSource(nl.item(i)), strResult);
				String tStr = strResult.getWriter().toString();
				out += tStr;
				strResult.getWriter().flush();
			}
		} catch (Exception e) 
		{
			AppLogger.info("XML.toString(Document): ");
		}
		return out;
	}
	public static String createRandomID(int length)
	{
		String charlist = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_";
		Random rand = new Random();
		String str = new String();
		for (int i = 0; i < length; i++)
		{
			str += charlist.charAt(rand.nextInt(charlist.length()));
		}
		return str;

	}
}
