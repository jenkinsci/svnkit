/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.io.svn;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import org.tmatesoft.svn.core.io.ISVNCredentials;

/**
 * @author Alexander Kitaev
 */
public class CramMD5 {
	
	private ISVNCredentials myCredentials;

	public void setUserCredentials(ISVNCredentials credentials) {
		myCredentials = credentials;
	}
	
	public byte[] buildChallengeReponse(byte[] challenge) {
		String password = myCredentials.getPassword();
		byte[] secret = new byte[64];
		Arrays.fill(secret, (byte) 0);
		for (int i = 0; i < password.length(); i++) {
			secret[i] = (byte) password.charAt(i);
		}
		MessageDigest digest = null;
		try {
			digest = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return null;
		}
		for (int i = 0; i < secret.length; i++) {
			secret[i] ^= 0x36; 
		}
		digest.update(secret);
		digest.update(challenge);
		byte[] result = digest.digest();
		for (int i = 0; i < secret.length; i++) {
			secret[i] ^= (0x36 ^ 0x5c); 
		}
		digest.update(secret);
		digest.update(result);
		result = digest.digest();
		String hexDigest = "";
		for (int i = 0; i < result.length; i++) {
			byte b = result[i];
			int lo = b & 0xf;
			int hi = (b >> 4) & 0xf;
			hexDigest += Integer.toHexString(hi) + Integer.toHexString(lo);
		}
		String response = myCredentials.getName() + " " + hexDigest;
		response = response.length() + ":" + response + " ";
		return response.getBytes();
	}

}
