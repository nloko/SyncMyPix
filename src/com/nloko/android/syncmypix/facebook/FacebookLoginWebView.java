//
//    FacebookLoginWebView.java is part of SyncMyPix
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

package com.nloko.android.syncmypix.facebook;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLDecoder;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.nloko.android.Log;
import com.nloko.android.Utils;
import com.nloko.android.syncmypix.SettingsActivity;
import com.nloko.android.syncmypix.R;
import com.nloko.simplyfacebook.net.login.FacebookLogin;

public class FacebookLoginWebView extends Activity {

	private final String TAG = "FacebookLoginWebView";
	private final FacebookLogin login = new FacebookLogin();
	
	private final int AUTH_DIALOG = 0;
    private ProgressDialog authDialog = null;
    
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
	
	private static class FacebookClient extends WebViewClient
	{
		private final WeakReference<FacebookLoginWebView> mActivity;
		public FacebookClient(FacebookLoginWebView activity)
		{
			super();
			mActivity = new WeakReference<FacebookLoginWebView>(activity);
		}
		
    	@Override
		public void onPageFinished(WebView view, String url) {
			super.onPageFinished(view, url);
			FacebookLoginWebView activity = mActivity.get();
			if (activity != null) {
				Dialog dialog = activity.authDialog;
				if (dialog != null && dialog.isShowing()) {
					activity.dismissDialog(activity.AUTH_DIALOG);
				}
			}
		}
    	
    	@Override
		public void onReceivedError(WebView view, int errorCode,
				String description, String failingUrl) {
			super.onReceivedError(view, errorCode, description, failingUrl);
			String msg = String.format("URL %s failed to load with error %d %s", failingUrl, errorCode, description);
			
			FacebookLoginWebView activity = mActivity.get();
			if (activity != null) {
				Log.e(activity.TAG, msg);
				Toast.makeText(activity.getApplicationContext(), msg, Toast.LENGTH_LONG).show();
				
				Dialog dialog = activity.authDialog;
				if (dialog != null && dialog.isShowing()) {
					activity.removeDialog(activity.AUTH_DIALOG);
				}
				
				activity.setResult(Activity.RESULT_CANCELED);
				activity.finish();
			}
		}

		@Override
		public void onPageStarted(WebView view, String url, Bitmap favicon) {
			super.onPageStarted(view, url, favicon);
			FacebookLoginWebView activity = mActivity.get();
			if (activity == null) {
				return;
			}
			
			FacebookLogin login = activity.login;
			if (login == null) {
				return;
			}
			
			if (!activity.isFinishing() && !url.equals(login.getFullLoginUrl())) {
					activity.showDialog(activity.AUTH_DIALOG);
			}
			
			try	{
            	Log.d(activity.TAG, url);
            	Log.d(activity.TAG, login.getNextUrl().getPath());
            	
                URL page = new URL(URLDecoder.decode(url).trim());

                if (page.getPath().equals(login.getNextUrl().getPath())) {
                	login.setResponseFromExternalBrowser(page);
                    Toast.makeText(activity.getApplicationContext(), R.string.login_thankyou, Toast.LENGTH_LONG).show();
                    
                    if (login.isLoggedIn()) {
                    	Utils.setString(activity.getSharedPreferences(SettingsActivity.PREFS_NAME, 0), "session_key", login.getSessionKey());
                    	Utils.setString(activity.getSharedPreferences(SettingsActivity.PREFS_NAME, 0), "secret", login.getSecret());
                    	Utils.setString(activity.getSharedPreferences(SettingsActivity.PREFS_NAME, 0), "uid", login.getUid());
                    }
                    
                    activity.setResult(Activity.RESULT_OK);
                    activity.finish();
                } else if (page.getPath().equals(login.getCancelUrl().getPath())) {
                	activity.setResult(Activity.RESULT_CANCELED);
                	activity.finish();
                }
            } catch (Exception ex) {
                Toast.makeText(activity.getApplicationContext(), R.string.facebooklogin_urlError, Toast.LENGTH_LONG).show();
                android.util.Log.getStackTraceString(ex);
            }
		}
		
		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			return false;
		}
	}
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        requestWindowFeature(Window.FEATURE_PROGRESS);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.facebookloginwebview);	
        
        login.setAPIKey(FacebookApi.API_KEY);
        
        webview = (WebView) findViewById(R.id.webview);
        webview.setWebChromeClient(new ChromeClient(this));
        webview.setWebViewClient(new FacebookClient(this));        
        webview.getSettings().setJavaScriptEnabled(true);
    }
    
    @Override
	protected void onStart() {
		super.onStart();
		Log.d(TAG, login.getFullLoginUrl());
		webview.loadUrl(login.getFullLoginUrl());
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
		authDialog = null;
		webview = null;
	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		Log.d(TAG, "FINALIZED");
	}
    
	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
			case AUTH_DIALOG:
				authDialog = new ProgressDialog(this);
				authDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
				authDialog.setMessage(getString(R.string.login_authorization));
				authDialog.setCancelable(true);
				return authDialog;
		}
		
		return super.onCreateDialog(id);
	}
}
