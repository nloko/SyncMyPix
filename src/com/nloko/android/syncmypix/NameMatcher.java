//
//    NameMatcher.java is part of SyncMyPix
//
//    Authors:
//		  Mike Hearn  <mike@plan99.net>
//        Neil Loknath <neil.loknath@gmail.com>
//
//	  Copyright (c) 2009 Mike Hearn
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

// Ideas for improving automatic name matching:
//
// Match phone number to network, eg +41 to Switzerland to disambiguate Carrie.
// Rank Facebook friends by how much contact there has been, eliminate non-actual friends.
// Extend nicknames list.

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import com.nloko.android.Utils;

import android.util.Log;

public class NameMatcher {
    static private final String TAG = "NameMatcher";
    
    private TreeMap<String, ArrayList<SocialNetworkUser>> mFirstNames, mLastNames;
    private HashMap<Object, ArrayList<SocialNetworkUser>> mNickNames;
    private HashMap<String, Object> mDiminutives;
    
    private static void log(String s) {
        if (false) Log.i(TAG, s);
    }
    
    public NameMatcher(List<SocialNetworkUser> users, InputStream diminutives) throws Exception {
    	this((SocialNetworkUser[])users.toArray(), diminutives);
    }
    
    public NameMatcher(SocialNetworkUser[] users, InputStream diminutives) throws Exception {
        loadDiminutives(diminutives);
        // Build data structures for the first and last names, so we can
        // efficiently do partial matches (eg "Rob" -> "Robert").
        mFirstNames = new TreeMap<String, ArrayList<SocialNetworkUser>>();
        mLastNames = new TreeMap<String, ArrayList<SocialNetworkUser>>();
        mNickNames = new HashMap<Object, ArrayList<SocialNetworkUser>>();
        for (int i = 0; i < users.length; i++) {
            if (users[i] == null)
                throw new Exception("Internal error: user " + i + " was null in NameMatcher c'tor");
            String name = normalizeName( users[i].name);
            String[] components = name.split(" ");
            String fname = components[0];
            String lname = components[components.length - 1];
            
            if (mFirstNames.get(fname) == null)
                mFirstNames.put(fname, new ArrayList<SocialNetworkUser>(3));
            mFirstNames.get(fname).add(users[i]);
            log("added " + fname + " to mFirstNames = " + users[i]);
            
            if (mLastNames.get(lname) == null)
                mLastNames.put(lname, new ArrayList<SocialNetworkUser>(3));
            mLastNames.get(lname).add(users[i]);
            
            // See below for a description of sentinels and diminutives.
            Object sentinel = mDiminutives.get(fname);
            if (sentinel != null) {
                if (mNickNames.get(sentinel) == null) {
                    mNickNames.put(sentinel, new ArrayList<SocialNetworkUser>(3));
                }
                log("linking " + sentinel + " with " + users[i].name);
                mNickNames.get(sentinel).add(users[i]);
            }
        }
    }
    
    private void loadDiminutives(InputStream diminutivesFile) {
        // Names are comma separated, across multiple lines. Names on a line
        // are deemed to be equivalent. The same name can appear on multiple
        // lines, when that happens it's as if the two lines are concatenated
        // and all the names are considered equivalent.
        //
        // This scheme fails for some names. For instance, "alfie" isn't really
        // the same as "fred" yet they are both equivalents to "alfred".
        //
        // Names are mapped via a HashMap to instances of Object. Two names 
        // that map to the same sentinel instance are the same. The value
        // is thus meaningless and used simply to provide an equality test.
        //
        // Each line is processed one at a time. Each name in that line is 
        // looked up in the map. If any name on the line is mapped, all the 
        // names on that line are mapped to the same value. Otherwise, a new
        // Object is allocated, and all the names are mapped to that new value.
        try {
            mDiminutives = new HashMap<String, Object>();
            // Specify 8kb buffer explicitly to avoid it whinging to the logs.
            BufferedReader reader = new BufferedReader(new InputStreamReader(diminutivesFile, "UTF-8"), 8 * 1024);   
            String line;
            while ((line = reader.readLine()) != null) {
                Object sentinel = null;
                String[] names = line.split(",");
                
                // Are any of these names already known?
                for (int i = 0; i < names.length; i++) {
                    Object o = mDiminutives.get(names[i]);
                    if (o != null) {
                        sentinel = o;
                        break;
                    }
                }
                if (sentinel != null) {
                    // If yes, then all these names are equivalent to that name.
                    // So fall through.
                } else {
                    // Otherwise, we never saw any of these names before.
                    sentinel = new Object();
                }
                for (int i = 0; i < names.length; i++) {
                    Object existingSentinel = mDiminutives.get(names[i]); 
                    if (existingSentinel != null && existingSentinel != sentinel) {
                        // This happens if a name is shared between more than two
                        // lines. This is rare so just merge them down to two by 
                        // hand.
                        log("THREE LINE CONFLICT  " + sentinel + " " + mDiminutives.get(names[i]));
                    } else {
                        mDiminutives.put(names[i], sentinel);
                    }
                }
            }
            
            if (false) {
                for (String s : mDiminutives.keySet())
                    System.out.println(mDiminutives.get(s) + " " + s);
            }
        } catch (UnsupportedEncodingException e) {
            // Impossible: Java implementations are required to support UTF-8.
            e.printStackTrace();
            throw new Error(e);
        } catch (IOException e) {
            // Impossible: the diminutives file should always be readable.
            e.printStackTrace();
            throw new Error(e);
        }
    }

    private String normalizeName(String name) {
        // Lower case the name, and replace non-English characters with their
        // English equivalents, as some people won't bother to type accents in
        // their friends names. Also strip stuff in brackets and delete commas.
        StringBuffer newName = new StringBuffer(name.toLowerCase().trim());
        final String badChars  = "���������������������������,";
        final String goodChars = "aeiouaeiouaeiouaeiounoacsoa ";
        int bracket = 0;
        int newNameLength = newName.length();
        for (int i = 0; i < newNameLength; i++) {
            char c = newName.charAt(i);
            
            // Filter out accented characters.
            int badIndex = badChars.indexOf(c); 
            if (badIndex > -1)
                newName.setCharAt(i, goodChars.charAt(badIndex));
            
            // Delete text in brackets, the - character if it's the last one,
            // commas and duplicate whitespace.
            if (c == '(') bracket++;
            if (bracket > 0 || 
                    (i == newNameLength - 1 && c == '-') ||
                    (c == ' ' && i == 0) ||
                    (c == ' ' && i > 0 && newName.charAt(i - 1) == ' ')) {
                if (c == ')') bracket--;
                newName.deleteCharAt(i);
                newNameLength--;
                i--; // Step backwards, so the continue statement puts us back
                     // to the right place.
                continue;
            }
        }
        return newName.toString();
    }
    

    // Takes a name from the local contact list and tries to find the right
    // SocialNetworkUser for it.
    public SocialNetworkUser match(String name, boolean firstNameOnlyMatches) {
        return match(name, firstNameOnlyMatches, false);
    }
    
    private SocialNetworkUser match(String name, boolean firstNameOnlyMatches, boolean reverse) {
        // Select exact first/last name matches
        String[] components = normalizeName(name).split(" ");
        
        if (reverse) {
            String[] reversedComponents = new String[components.length];
            for (int i = 0; i < components.length; i++)
                reversedComponents[i] = components[components.length - 1 - i];
            components = reversedComponents;
        }
        
        log("Trying to match: " + Utils.join(components, ' '));
        
        // Compile all the possibilities based on first name match only.
        HashSet<SocialNetworkUser> possibilities = new HashSet<SocialNetworkUser>(5);
        possibilities.addAll(prefixMatch(components[0], mFirstNames));
        if (possibilities.size() > 0) {
            log("prefix match from " + components[0] + " to ");
            for (SocialNetworkUser u : possibilities) 
                log("   " + u.name);
        }
        
        ArrayList<SocialNetworkUser> matches = nicknameMatch(components[0]);
        if (matches != null) { 
            if (matches.size() > 1) {
                log("multiple nickname matches:");
                for (SocialNetworkUser temp : matches) log("   " + temp.name);
            } else if (matches.size() == 1) {
                log("nickname matched " + components[0] + " to " + matches.get(0).name);
            } 
            possibilities.addAll(matches);
        } else 
            log("no nickname matches");
        
        if (possibilities.size() > 0) {
            // We have at least one match on first name.
            if (components.length > 1) {
                // Pick the first which does not violate the last name.
                for (SocialNetworkUser possibility : possibilities) {
                    String[] matchParts = normalizeName(possibility.name).split(" ");
                    String lname = matchParts[matchParts.length - 1];
                    if (lname.startsWith(components[components.length - 1])) {
                        log("matched " + name + " to " + possibility.name);
                        return possibility;
                    }
                }
                log("all inexact first name matches violated last name constraints");
            } else if (firstNameOnlyMatches) {
                if (possibilities.size() == 1) {
                    // We only have a first name in the contacts list, but 
                    // only one possibility from Facebook. So that's our answer.
                    SocialNetworkUser answer = possibilities.iterator().next(); 
                    log("only one possibility, matched " + name + " to " + answer.name);
                    return answer;
                } else {
                    // Check for an exact first name match.
                    // Otherwise we can't match "Mike" -> "Mike Hearn" if there
                    // is also a "Michael Douglas" in the friends list, because
                    // "Mike" will be expanded to match both people, even though
                    // it's probably the first friend.
                    ArrayList<SocialNetworkUser> exactMatches;
                    exactMatches = mFirstNames.get(components[0]);
                    if (exactMatches != null && exactMatches.size() == 1) {
                        log("exact first name match " + components[0] + " to " + exactMatches.get(0).name);
                        return exactMatches.get(0);
                    }
                    log("first name matched multiple people and there is no disambiguating last name");
                }
            }
        }
        
        // Accept only a last name, eg "Dunlop" -> "Paul Dunlop" when unambiguous.
        if (components.length == 1) {
            ArrayList<SocialNetworkUser> users = mLastNames.get(components[0]);
            if (users != null && users.size() == 1) {
                log("exact last name match: " + users.get(0).name);
                return users.get(0);
            }
        } else if (!reverse) {
            // Didn't find any good matches, but some people store contacts Last, First.
            // Try the whole process again with last name and first name reversed.
            return match(name, firstNameOnlyMatches, true);
        }
        
        log("No match found for " + name);
        return null;
    }
    
    private ArrayList<SocialNetworkUser> nicknameMatch(String nickname) {
        Object sentinel = mDiminutives.get(nickname);
        if (sentinel == null) 
            return null;
        
        return mNickNames.get(sentinel);
    }
    
    // Tries to use prefix matching to find a match, eg "rob" -> "robert".
    private ArrayList<SocialNetworkUser> prefixMatch(String part, TreeMap<String, ArrayList<SocialNetworkUser>> map) {
        ArrayList<SocialNetworkUser> results = new ArrayList<SocialNetworkUser>(3);
        String startKey = part;
        StringBuffer endKey = new StringBuffer(startKey);
        endKey.setCharAt(startKey.length() - 1, (char) (startKey.charAt(startKey.length() - 1) + 1));
        // eg, select from "mike" to "mikf"
        SortedMap<String, ArrayList<SocialNetworkUser>> selection = map.subMap(startKey, endKey.toString());
        for (String s : selection.keySet()) {
            if (s.startsWith(part)) {
                results.addAll(selection.get(s));
            } else {
                log("unexpected: " + s + ", " + part);
            }
        }
        return results;
    }
    
    
/*    // TODO: Convert this to a JUnit test when figured out how.
    static private void test(NameMatcher m, String name, SocialNetworkUser user) {
        SocialNetworkUser res = m.match(name, true); 
        if (res != user) 
            System.out.println(" **** Failed NameMatcher test: " + name + " should have been " + (user == null ? "null" : user.name) + " but was actually " + (res == null ? "null" : res.name));
    }
    static public void unitTest(InputStream diminutivesFile) {
        SocialNetworkUser theresia = new SocialNetworkUser("1", "Theresia Paul", "", "");
        SocialNetworkUser alejandro = new SocialNetworkUser("2", "Alejandro Cuervo", "", "");
        SocialNetworkUser tala = new SocialNetworkUser("3", "Tala von Daniken", "", "");
        SocialNetworkUser paul = new SocialNetworkUser("4", "Paul Dunlop", "", "");
        SocialNetworkUser andre = new SocialNetworkUser("5", "Andrea Beltr�n", "", "");
        SocialNetworkUser joanna1 = new SocialNetworkUser("6", "Joanna Frisch", "", "");
        SocialNetworkUser joanna2 = new SocialNetworkUser("7", "Joanna Something", "", "");
        SocialNetworkUser stribb = new SocialNetworkUser("8", "Andrew Stribblehill", "", "");
        SocialNetworkUser rob = new SocialNetworkUser("9", "Robert Cook", "", "");
        SocialNetworkUser prince = new SocialNetworkUser("10", "Prince", "", "");
        SocialNetworkUser rob2 = new SocialNetworkUser("11", "Robert Second", "", "");
        SocialNetworkUser rob3 = new SocialNetworkUser("12", "John Robert", "", "");
        SocialNetworkUser alex1 = new SocialNetworkUser("13", "Alexandra One", "", "");
        SocialNetworkUser ellie = new SocialNetworkUser("14", "Ellie Two", "", "");
        SocialNetworkUser[] users = { theresia, alejandro, tala, paul, andre, 
                                 joanna1, joanna2, stribb, rob, prince,
                                 rob2, rob3, alex1, ellie };
        
        NameMatcher matcher;
        try {
            matcher = new NameMatcher(users, diminutivesFile);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        
        // Cases that are known to fail:
        // 
        // test(matcher, "von Daniken", tala);   -- von looks like a first name constraint
        // test(matcher, "Rob", rob);   -- first name match is ambiguous so we default to last name match, which is unlikely to be correct
        
        // The basics ...
        test(matcher, "Paul Dunlop", paul);
        
        // Not whitespace sensitive
        test(matcher, "  Paul    Dunlop  ", paul);

        // Test hack for my address book
        test(matcher, "Alejandro -", alejandro);
        
        // Bracketed text
        test(matcher, "Paul Dunlop (some dude)", paul);
        test(matcher, "(some dude) Paul Dunlop", paul);
        test(matcher, "    (some dude) Paul Dunlop  ", paul);
        test(matcher, "(whatever) Paul Dunlop (some dude)", paul);

        // Case insensitive match
        test(matcher, "THERESIA PAUL", theresia);
        
        // Multiple hits == null (don't guess, we don't have enough info)
        // TODO: We could try and take into account "popularity" here, ie, how
        // much the user communicates with each friend, or how many mutual friends
        // they have to resolve ambiguities like this.
        test(matcher, "Joanna", null);
        
        // Unambiguous first name.
        test(matcher, "Andrew", stribb);
        // Unambiguous first name with first-name-only matches disabled.
        if (matcher.match("Andrew", false) == stribb)
            System.out.println("*** first name only match occurred even when not requested");
        
        // No match.
        test(matcher, "Robert Nosuchname", null);
        test(matcher, "Nosuchname Cook", null);
        
        test(matcher, "Joanna F", joanna1);
        test(matcher, "Joanna S", joanna2);
        test(matcher, "Bob C", rob);
        
        // Middle names are ignored
        test(matcher, "Tala Daniken", tala);
        // Exact first name match takes precedence over last name match
        test(matcher, "Paul", paul);
        // No match despite shared first name.
        test(matcher, "Paul Smith", null);
        
        // Exact last name 
        test(matcher, "Dunlop", paul);
        
        // Insensitive to accents
        test(matcher, "Andrea Beltran", andre);
        // Truncated first name with accent insensitivity
        test(matcher, "Andre Beltran", andre);
        
        // Lastname Firstname
        test(matcher, "Dunlop Paul", paul);
        test(matcher, "Dunlop P", paul);
        test(matcher, "Dunlop, P", paul);
        test(matcher, "Dunlop, Paul", paul);
        test(matcher, "Dunlop,Paul", paul);  // Apparently WinMo does this.

        test(matcher, "P Dunlop", paul);
        
        // Diminutives, short form in contacts
        test(matcher, "Lex", alex1);
        test(matcher, "Sandra", alex1);
        test(matcher, "Andy S", stribb);
        // Diminutives, short form in Facebook
        test(matcher, "Eleanor Two", ellie);
    }*/
}
