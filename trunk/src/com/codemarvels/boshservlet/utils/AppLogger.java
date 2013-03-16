/*
    AppLogger - does logging.

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

package com.codemarvels.boshservlet.utils;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.Priority;

public class AppLogger
{
	private AppLogger()
	{//hidden constructor
	}

	static Logger logger;
	static
	{
		logger = Logger.getLogger(AppLogger.class);
	}
	public static void debug(Object message, Throwable t) {
		logger.debug(message, t);
	}
	public static void debug(Object message) {
		logger.debug(message);
	}
	public static void error(Object message, Throwable t) {
		t.printStackTrace();
		logger.error(message, t);
	}
	public static final Level getLevel() {
		return logger.getLevel();
	}
	public static void info(Object message, Throwable t) {
		logger.info(message, t);
	}
	public static void info(Object message) {
		logger.info(message);
	}
	public static void log(Priority priority, Object message, Throwable t) {
		logger.log(priority, message, t);
	}
	public static void log(Priority priority, Object message) {
		logger.log(priority, message);
	}
	public static void log(String callerFQCN, Priority level, Object message,
			Throwable t) {
		logger.log(callerFQCN, level, message, t);
	}
	public static void warn(Object message, Throwable t) {
		logger.warn(message, t);
	}
	public static void warn(Object message) {
		logger.warn(message);
	}

}
