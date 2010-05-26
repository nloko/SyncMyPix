//
//    PhoneContact.java is part of SyncMyPix
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

public final class PhoneContact implements Comparable<PhoneContact> {
	
	public PhoneContact(String id, String name) {
		this(id, name, null);
	}
	
	public PhoneContact(String id, String name, String lookup) {
		this.id = id;
		this.name = name;
		this.lookup = lookup;
	}
	
	public String id;
	public String name;
	public String lookup;
	
	public int compareTo(PhoneContact another) {
		return name.compareTo(another.name);
	}
}