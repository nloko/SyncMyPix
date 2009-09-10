//
//    FacebookUsers.java is part of SyncMyPix
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.client.ClientProtocolException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.nloko.android.Log;
import com.nloko.android.syncmypix.SocialNetworkUser;

import com.nloko.simplyfacebook.net.FacebookJSONResponse;
import com.nloko.simplyfacebook.net.FacebookRestClient;

public class FacebookUsers {

	protected FacebookUsers()
	{
	}
	
	private FacebookRestClient client;
	public FacebookUsers(FacebookRestClient client)
	{
		if (client == null) {
			throw new IllegalArgumentException ("client");
		}
		
		this.client = client;
	}
	
	public String getFriends() throws ClientProtocolException, IOException, JSONException
	{
		FacebookJSONResponse response = (FacebookJSONResponse)client.getData("Friends.get");
		if (response != null && !response.isError()) {
			JSONArray friends = new JSONArray(response.data);
			StringBuilder sb = new StringBuilder();
			sb.append(friends.get(0));
			
			if (friends.length() > 1) {
				for (int i = 1; i < friends.length(); i++) {
					sb.append(",");
					sb.append(friends.get(i));
				}
			}
			//Log.d(null, sb.toString());
			return sb.toString();
		}
		
		return null;
	}
	
	public List<SocialNetworkUser> getUserInfo (String uids) throws JSONException, ClientProtocolException, IOException
	{
		if (uids == null) {
			throw new IllegalArgumentException ("uids");
		}
		
		Map <String, String> params = new HashMap <String, String> ();
		params.put ("uids", uids);
		params.put ("fields", "first_name,last_name,pic_big");
		FacebookJSONResponse response = (FacebookJSONResponse) client.getData ("Users.getInfo", params);
		//Log.d(null, response.data);
		if (response == null || response.isError()) {
			return null;
		}
		
		JSONArray users = new JSONArray(response.data);
        
        List<SocialNetworkUser> list = new ArrayList<SocialNetworkUser>();
        for (int i = 0; i < users.length(); i++) {
            JSONObject user = users.getJSONObject(i);
            SocialNetworkUser fbUser = new SocialNetworkUser();
            fbUser.firstName = user.getString("first_name");
            fbUser.lastName = user.getString("last_name");
            fbUser.picUrl = user.getString("pic_big");
            list.add(fbUser);
        }
        
        return list;
	}
}
