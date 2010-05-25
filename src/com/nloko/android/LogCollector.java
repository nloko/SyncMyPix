//
//  LogCollector.java
//
//  Authors:
// 		Neil Loknath <neil.loknath@gmail.com>
//
//  Copyright 2010 Neil Loknath
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import android.os.Build;
import android.os.Handler;
import android.os.Message;

public class LogCollector {
	private Thread mThread;

	private boolean mCollected = false;
	private boolean mCollecting = false;
	
	private StringBuilder mLog;
	private LogCollectorNotifier mNotifier;
	private LogHandler mHandler;
	
	public static final String TAG = "LogCollector";
    public static final String[] LOGCAT_CMD = new String[] { "logcat", "-d" };
    private static final int BUFFER_SIZE = 1024;

    private static class LogHandler extends Handler {
    	private LogCollector mCollector;
    	
    	public static final int COMPLETED = 1;
    	public static final int ERROR = 2;
    	
    	public LogHandler(LogCollector collector) {
    		super();
    		mCollector = collector;
    	}
    	
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			if (mCollector == null) {
				return;
			}
			
			LogCollectorNotifier notifier = mCollector.mNotifier;
			
			switch(msg.what) {
				case COMPLETED:
					mCollector.mCollected = true;
					mCollector.mCollecting = false;
					if (notifier != null) {
						notifier.onComplete();
					}
					break;
				case ERROR:
					mCollector.mCollected = false;
					mCollector.mCollecting = false;
					if (notifier != null) {
						notifier.onError();
					}
					break;
			}
		}
    }
    
    public void destroy() {
    	//stopCollecting();
    	//mHandler = null;
    	//mThread = null;
    }
    
	@Override
	protected void finalize() throws Throwable {
		Log.d(TAG, "FINALIZED");
		super.finalize();
	}

	public LogCollector() {
		mHandler = new LogHandler(this);
	}
	
	public void setNotifier(LogCollectorNotifier notifier) {
		mNotifier = notifier;
	}
	
	public boolean isCollecting() {
		return mCollecting;
	}
	
	public boolean isCollected() {
		return mCollected;
	}
	
	public void stopCollecting() {
		if (mThread != null && mThread.isAlive()) {
			mThread.interrupt();
		}
	}
	
	public String getLog() {
		if (mCollected) {
			if (mLog != null) {
				return mLog.toString();
			}
		}
		
		return null;
	}
	
	public void appendMessage(String msg) {
		if (mCollected) {
			if (mLog != null) {
				StringBuffer buffer = new StringBuffer();
				String separator = System.getProperty("line.separator");
				buffer.append(msg);
				buffer.append(separator);
				buffer.append(separator);
				
				mLog.insert(0, buffer.toString());
			}
		}
	}
	
	public void collect() {
		mCollected = false;
		
		mThread = new Thread(new Runnable() {
			public void run() {
				Process proc = null;
				BufferedReader reader = null;
				try {
					proc = Runtime.getRuntime().exec(LOGCAT_CMD);
					reader = new BufferedReader(new InputStreamReader(proc.getInputStream()), BUFFER_SIZE);
					
					String line;
					String separator = System.getProperty("line.separator");
					
					// Initialize and add Android info
					mLog = new StringBuilder();
					mLog.append("Model: " + Build.MODEL);
					mLog.append(separator);
					mLog.append("Display: " + Build.DISPLAY);
					mLog.append(separator);
					mLog.append("Release: " + Build.VERSION.RELEASE);
					mLog.append(separator);
					mLog.append(separator);
					
					while((line = reader.readLine()) != null) {
						mLog.append(line);
						mLog.append(separator);
					}
					
					Log.d(TAG, "collected log");
					if (mHandler != null) {
						mHandler.sendEmptyMessage(LogHandler.COMPLETED);
					}
				} catch (IOException e) {
					if (mHandler != null) {
						mHandler.sendEmptyMessage(LogHandler.ERROR);
					}
					e.printStackTrace();
				} finally {
					try {
						if (reader != null) {
							reader.close();
						}
						
						if (proc != null) {
							proc.destroy();
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				
			}
		});
		
		mCollecting = true;
		mThread.start();
	}
}
