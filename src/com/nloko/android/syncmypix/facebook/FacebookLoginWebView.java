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
import com.nloko.android.syncmypix.GlobalPreferences;
import com.nloko.android.syncmypix.R;
import com.nloko.simplyfacebook.net.login.FacebookLogin;

public class FacebookLoginWebView extends Activity {

	private final String TAG = "FacebookLoginWebView";
	
	WebView webview;
	final FacebookLogin login = new FacebookLogin ();
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        requestWindowFeature(Window.FEATURE_PROGRESS);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.facebookloginwebview);	
        
        // Mobile page returns an auth_token. WTF?
        /*try {
        	login.setUrl("https://m.facebook.com/login.php");
        }
        catch (Exception e) {}*/
        
        login.setAPIKey(FacebookApi.API_KEY);
        
        webview = (WebView) findViewById(R.id.webview);
        webview.setWebChromeClient(new WebChromeClient() {
        	
        	@Override
    		public void onProgressChanged(WebView view, int newProgress) {
    			// TODO Auto-generated method stub
    			super.onProgressChanged(view, newProgress);
    			setProgress(newProgress);
        	}
    		
    		private void setProgress(int progress)
    	    {
    	    	FacebookLoginWebView.this.setProgress(progress * 100);
    	    	setProgressBarIndeterminateVisibility(progress < 100);
    	    }
        });
        
        webview.setWebViewClient(new WebViewClient() {

        	@Override
    		public void onPageFinished(WebView view, String url) {
    			super.onPageFinished(view, url);
    			
    			if (authDialog != null && authDialog.isShowing()) {
    				dismissDialog(AUTH_DIALOG);
    			}
    		}
        	
            
/*          @Override
            public void onReceivedSslError(
                final WebView view, final SslErrorHandler handler, final SslError error) {

            }*/
            
        	@Override
    		public void onReceivedError(WebView view, int errorCode,
    				String description, String failingUrl) {
    			// TODO Auto-generated method stub
    			super.onReceivedError(view, errorCode, description, failingUrl);
    			
    			String msg = String.format("URL %s failed to load with error %d %s", failingUrl, errorCode, description);
    			android.util.Log.e(TAG, msg);
    			Toast.makeText(getBaseContext(), msg, Toast.LENGTH_LONG).show();
    			
    			if (authDialog != null && authDialog.isShowing()) {
    				removeDialog(AUTH_DIALOG);
    			}
    			
    			setResult(Activity.RESULT_CANCELED);
    			finish();
    		}

    		@Override
    		public void onPageStarted(WebView view, String url, Bitmap favicon) {
    			// TODO Auto-generated method stub
    			super.onPageStarted(view, url, favicon);
    			
    			if (!url.equals(login.getFullLoginUrl())) {
    				// ignore bogus BadTokenException
    				try {
    					showDialog(AUTH_DIALOG);
    				}
    				catch (Exception e) {}
    			}
    			
    			try	{
                	Log.d(TAG, url);
                	Log.d(TAG, login.getNextUrl().getPath());
                	
                    URL page = new URL(URLDecoder.decode(url).trim());

                    if (page.getPath().equals(login.getNextUrl().getPath())) {
                    	login.setResponseFromExternalBrowser(page);
                        Toast.makeText(getBaseContext(), R.string.login_thankyou, Toast.LENGTH_LONG).show();
                        
                        if (login.isLoggedIn()) {
                        	Utils.setString(getSharedPreferences(GlobalPreferences.PREFS_NAME, 0), "session_key", login.getSessionKey());
                        	Utils.setString(getSharedPreferences(GlobalPreferences.PREFS_NAME, 0), "secret", login.getSecret());
                        	Utils.setString(getSharedPreferences(GlobalPreferences.PREFS_NAME, 0), "uid", login.getUid());
                        }
                        
                        setResult(Activity.RESULT_OK);
                        finish();
                    }
                    else if (page.getPath().equals(login.getCancelUrl().getPath())) {
                    	setResult(Activity.RESULT_CANCELED);
                    	finish();
                    }
                } 
                catch (Exception ex) {
                    Toast.makeText(getBaseContext(), R.string.facebooklogin_urlError, Toast.LENGTH_LONG).show();
                    android.util.Log.getStackTraceString(ex);
                }

    		}
    		
			@Override
			public boolean shouldOverrideUrlLoading(WebView view, String url) {
				// TODO Auto-generated method stub
				//view.loadUrl(url);
				//return true;
				
				return false;
			}
        	
        });
        
        webview.getSettings().setJavaScriptEnabled(true);
        //webview.clearSslPreferences();                
        
        Log.d(TAG, login.getFullLoginUrl());
        webview.loadUrl(login.getFullLoginUrl());
    }
    

    private final int AUTH_DIALOG = 0;
    private ProgressDialog authDialog = null;
    
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
