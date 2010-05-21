//
//    ConfirmSyncDialog.java is part of SyncMyPix
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


package com.nloko.android.syncmypix.views;

import com.nloko.android.syncmypix.SyncMyPixPreferences;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.widget.TextView;

public final class ConfirmSyncDialog extends AlertDialog {

	public ConfirmSyncDialog(Context context) {
		super(context);
		
		initialize(context);
	}

	private void initialize(Context context)
	{
		SyncMyPixPreferences prefs = new SyncMyPixPreferences(context);
		
		String msg = "Social Network: " + prefs.getSource() + "\n" +
					 "Skip if non-SyncMyPix picture: " + translateBool(prefs.getSkipIfExists()) + "\n" +
					 "Skip if multiple contacts: " + translateBool(prefs.getSkipIfConflict()) + "\n" +
					 "Smart name matching: " + translateBool(prefs.getIntelliMatch()) + "\n" +
					 "Use maximum resolution available: " + translateBool(prefs.getMaxQuality())  + "\n" +
					 "Crop 96px square: " + translateBool(prefs.getCropSquare())  + "\n";
		
		this.setTitle("Confirm Settings");
		this.setIcon(android.R.drawable.ic_dialog_alert);
		
		TextView view = new TextView(context);
		view.setPadding(4, 4, 4, 4);
		view.setTextSize(12);
		view.setTextColor(Color.WHITE);
		view.setText(msg);
		
		this.setView(view);
		//this.setMessage(msg);
		
		this.setCancelable(false);
	}
	
	public void setProceedButtonListener(DialogInterface.OnClickListener listener)
	{
		this.setButton(DialogInterface.BUTTON_POSITIVE, "Proceed", listener);
	}
	
	public void setCancelButtonListener(DialogInterface.OnClickListener listener)
	{
		this.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", listener);
	}
	
	private String translateBool(boolean value)
	{
		return value ? "Yes" : "No";
	}
}
