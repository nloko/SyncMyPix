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

public class FacebookApi {

	public static final String API_KEY = "d03f3dcb1ebb264e1ea701bd16f44e5a";
	
	protected FacebookApi()
	{
	}
	
	private FacebookRestClient client;
	public FacebookApi(FacebookRestClient client)
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
		return getUserInfo(uids, false);
	}
	
	public List<SocialNetworkUser> getUserInfo (String uids, boolean highQuality) throws JSONException, ClientProtocolException, IOException
	{
		if (uids == null) {
			throw new IllegalArgumentException ("uids");
		}
		
		Map <String, String> params = new HashMap <String, String> ();
		params.put ("uids", uids);
		params.put ("fields", "uid,first_name,last_name,name,email,pic_big");
		FacebookJSONResponse response = (FacebookJSONResponse) client.getData ("Users.getInfo", params);
		//Log.d(null, response.data);
		if (response == null || response.isError()) {
			return null;
		}
		
		JSONArray users = new JSONArray(response.data);
        
        List<SocialNetworkUser> list = new ArrayList<SocialNetworkUser>();
        Map<String, SocialNetworkUser> userMap = new HashMap <String, SocialNetworkUser> ();
        
        SocialNetworkUser fbUser = null;
        JSONObject user = null;
        
        for (int i = 0; i < users.length(); i++) {
            user = users.getJSONObject(i);
            fbUser = new SocialNetworkUser();
            fbUser.uid = user.getString("uid");
            fbUser.firstName = user.getString("first_name");
            fbUser.lastName = user.getString("last_name");
            fbUser.name = user.getString("name");
            fbUser.email = user.getString("email");
            fbUser.picUrl = (
            		user.getString("pic_big").equals("null") ||
            		user.getString("pic_big") == "") ? null : user.getString("pic_big");
            
            list.add(fbUser);
            userMap.put(user.getString("uid"), fbUser);
        }
        
        if (highQuality) {
        	setHighResPhotos(uids, userMap);
        }
        
        return list;
	}
	
	private void setHighResPhotos(String uids, Map<String, SocialNetworkUser> userMap) throws ClientProtocolException, IOException
	{
		if (uids == null) {
			throw new IllegalArgumentException("uids");
		}
		
		Map <String, String> params = new HashMap <String, String> ();
				
		String pid_query = "SELECT owner, cover_pid, aid, name FROM album " +
			"WHERE owner IN (%s) AND " +
			"name IN (\"Profile Pictures\")";
	
		pid_query = String.format(pid_query, uids);
		
		String photo = "SELECT owner, src_big FROM photo " + 
			"WHERE pid IN (SELECT cover_pid FROM #query1) ";
		
		SocialNetworkUser user = null;
		String url = null;
		String uid = null;
		
		try {
			JSONObject queries = new JSONObject();
			queries.put("query1", pid_query);
			queries.put("query2", photo);
			
			params.put("queries", queries.toString());
			
			FacebookJSONResponse jr = (FacebookJSONResponse) client.getData("Fql.multiquery", params);
						
			JSONArray array = new JSONArray(jr.data);
			if (array.length() > 1) {
				JSONObject obj = array.getJSONObject(1);
				array = obj.getJSONArray("fql_result_set");
				
				for (int i = 0; i < array.length(); i++) {
					obj = array.getJSONObject(i);
					uid = obj.getString("owner");
					
					if (userMap.containsKey(uid)) {
						url = obj.getString("src_big");
						user = userMap.get(uid);
						user.picUrl = url;
					}
				}
			}
		}
		catch (JSONException e) {
			Log.e(null, android.util.Log.getStackTraceString(e));
		}

	}
}
