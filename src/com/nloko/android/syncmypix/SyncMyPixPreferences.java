//
//    SyncMyPixPreferences.java is part of SyncMyPix
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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

public final class SyncMyPixPreferences {

	protected Context context;
	
	public SyncMyPixPreferences(Context context)
	{
		if (context == null) {
			throw new IllegalArgumentException("context");
		}
		
		this.context = context;
		getPreferences(context);
	}
	
	private boolean googleSyncToggledOff;
	public boolean isGoogleSyncToggledOff()
	{
		return googleSyncToggledOff;
	}
	
	private boolean allowGoogleSync;
	public boolean getAllowGoogleSync()
	{
		return allowGoogleSync;
	}
	
	private boolean skipIfExists;
	public boolean getSkipIfExists()
	{
		return skipIfExists;
	}
	
    private boolean skipIfConflict;
    public boolean getSkipIfConflict()
	{
		return skipIfConflict;
	}
    
    private boolean overrideReadOnlyCheck;
    public boolean overrideReadOnlyCheck()
    {
    	return overrideReadOnlyCheck;
    }
    
    private boolean maxQuality;
    public boolean getMaxQuality()
	{
		return maxQuality;
	}
    
    private boolean cropSquare;
    public boolean getCropSquare()
	{
		return cropSquare;
	}
    
    private boolean cache;
    public boolean getCache()
	{
		return cache;
	}
    
    private boolean intelliMatch;
    public boolean getIntelliMatch()
	{
		return intelliMatch;
	}
    
    private boolean phoneOnly;
    public boolean getPhoneOnly()
	{
		return phoneOnly;
	}
    
    private String source;
    public String getSource()
    {
    	return source;
    }
    
//	private <T extends SyncService> String getSocialNetworkName (Class<T> source)
//	{
//		try {
//			Method m = source.getMethod("getSocialNetworkName");
//			return (String) m.invoke(null);
//
//		} catch (SecurityException e) {
//			e.printStackTrace();
//		} catch (NoSuchMethodException e) {
//			e.printStackTrace();
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//
//		return SyncService.getSocialNetworkName();
//	}
	
    private void getPreferences(Context context)
    {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
	
		//source = getSocialNetworkName(MainActivity.getSyncSource(context));
		
		googleSyncToggledOff = prefs.getBoolean("googleSyncToggledOff", false); 
		skipIfConflict = prefs.getBoolean("skipIfConflict", false);
		maxQuality = prefs.getBoolean("maxQuality", false);
		//allowGoogleSync = prefs.getBoolean("allowGoogleSync", false);
		allowGoogleSync = true;
    	
		//skipIfExists = prefs.getBoolean("skipIfExists", 
    	//		Integer.parseInt(Build.VERSION.SDK) >= Build.VERSION_CODES.ECLAIR ? false : true);
		skipIfExists = false;
    	
		overrideReadOnlyCheck = prefs.getBoolean("overrideReadOnlyCheck", false);
    	cropSquare = prefs.getBoolean("cropSquare", true);
    	intelliMatch = prefs.getBoolean("intelliMatch", true);
    	phoneOnly = prefs.getBoolean("phoneOnly", false);
    	cache = prefs.getBoolean("cache", true);
    }
}
