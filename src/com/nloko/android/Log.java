//
//  Log.java
//
//  Authors:
// 		Neil Loknath <neil.loknath@gmail.com>
//
//  Copyright 2009 Neil Loknath
//
//  Licensed under the Apache License, Version 2.0 (the "License"); 
//  you may not use this file except in compliance with the License. 
//  You may obtain a copy of the License at 
//
//  http://www.apache.org/licenses/LICENSE-2.0 
//
//  Unless required by applicable law or agreed to in writing, software 
//  distributed under the License is distributed on an "AS IS" BASIS, 
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
//  See the License for the specific language governing permissions and 
//  limitations under the License. 
//

package com.nloko.android;

public final class Log {

	public static boolean debug = false;
	
	public static void d(String tag, String message)
	{
		if (debug) {
			android.util.Log.d(tag, message);
		}
	}
	
	public static void w(String tag, String message)
	{
		android.util.Log.w(tag, message);
	}
	
	public static void e(String tag, String message)
	{
		android.util.Log.e(tag, message);
	}
	
	public static void v(String tag, String message)
	{
		android.util.Log.v(tag, message);
	}
	
	public static void i(String tag, String message)
	{
		android.util.Log.i(tag, message);
	}
}
