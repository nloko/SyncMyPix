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

import java.lang.reflect.Method;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.Preference;
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
    
    private boolean reverseNames;
    public boolean getReverseNames()
	{
		return reverseNames;
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
    
    private boolean intelliMatch;
    public boolean getIntelliMatch()
	{
		return intelliMatch;
	}
    
    private boolean firstNames;
    public boolean getFirstNames()
	{
		return firstNames;
	}
    
    private String source;
    public String getSource()
    {
    	return source;
    }
    
	private <T extends SyncService> String getSocialNetworkName (Class<T> source)
	{
		try {
			Method m = source.getMethod("getSocialNetworkName");
			return (String) m.invoke(null);

		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return SyncService.getSocialNetworkName();
	}
	
    private void getPreferences(Context context)
    {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
	
		source = getSocialNetworkName(MainActivity.getSyncSource(context));
		
		skipIfConflict = prefs.getBoolean("skipIfConflict", false);
		//reverseNames = prefs.getBoolean("reverseNames", false);
		maxQuality = prefs.getBoolean("maxQuality", false);
    	skipIfExists = prefs.getBoolean("skipIfExists", true);
    	cropSquare = prefs.getBoolean("cropSquare", true);
    	intelliMatch = prefs.getBoolean("intelliMatch", false);
    	firstNames = prefs.getBoolean("firstNames", false);
    }
}
