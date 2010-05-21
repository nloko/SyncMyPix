//
//    DonateActivity.java is part of SyncMyPix
//
//    Authors:
//        Neil Loknath <neil.loknath@gmail.com>
//
//    Copyright (c) 2009 Neil Loknath
//
//    SyncMyPix is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    SyncMyPix is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with SyncMyPix.  If not, see <http://www.gnu.org/licenses/>.
//

package com.nloko.android.syncmypix;

import java.lang.ref.WeakReference;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.nloko.android.Log;
import com.nloko.android.syncmypix.R;

public class DonateActivity extends Activity {

	private static final String TAG = "DonateActivity";
	private static final String DONATE_URL = "file:///android_asset/donate.html";
	
	private WebView webview;
	
	private static class ChromeClient extends WebChromeClient
	{
		private final WeakReference<Activity> mActivity;
		public ChromeClient(Activity activity)
		{
			super();
			mActivity = new WeakReference<Activity>(activity);
		}
		
    	@Override
		public void onProgressChanged(WebView view, int newProgress) {
			super.onProgressChanged(view, newProgress);
			setProgress(newProgress);
    	}
		
		private void setProgress(int progress)
	    {
			Activity activity = mActivity.get();
			if (activity != null) {
				activity.setProgress(progress * 100);
				activity.setProgressBarIndeterminateVisibility(progress < 100);
			}
	    }
	}
	
	private static class DonateClient extends WebViewClient
	{
		private final WeakReference<DonateActivity> mActivity;
		public DonateClient(DonateActivity activity)
		{
			super();
			mActivity = new WeakReference<DonateActivity>(activity);
		}
		
    	@Override
		public void onReceivedError(WebView view, int errorCode,
				String description, String failingUrl) {
			super.onReceivedError(view, errorCode, description, failingUrl);
			String msg = String.format("URL %s failed to load with error %d %s", failingUrl, errorCode, description);
			
		   	DonateActivity activity = mActivity.get();
			if (activity != null) {
				android.util.Log.e(DonateActivity.TAG, msg);
				Toast.makeText(activity.getApplicationContext(), msg, Toast.LENGTH_LONG).show();
				
				activity.finish();
			}
		}

		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			if (url.equals(DonateActivity.DONATE_URL)) { 
				return false;
			} else {
			 	DonateActivity activity = mActivity.get();
				if (activity != null) {
					Intent viewIntent = new Intent("android.intent.action.VIEW", Uri.parse(url));
					activity.startActivity(viewIntent);
				}
			}
			
			return true;
		}
	}
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        requestWindowFeature(Window.FEATURE_PROGRESS);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.facebookloginwebview);	
        
        webview = (WebView) findViewById(R.id.webview);
        webview.setWebChromeClient(new ChromeClient(this));
        webview.setWebViewClient(new DonateClient(this));        
        webview.getSettings().setJavaScriptEnabled(true);
    }
    
    @Override
	protected void onStart() {
		super.onStart();
		webview.loadUrl(DONATE_URL);
	}

	@Override
	protected void onPause() {
		super.onPause();
		webview.stopLoading();
	}
	
	@Override
	protected void onDestroy() {
		Log.d(TAG, "onDestroy");
		super.onDestroy();
		// allow proper GC
		webview = null;
	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		Log.d(TAG, "FINALIZED");
	}
}