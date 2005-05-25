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

package org.tmatesoft.svn.core.io;

/**
 * This interface is an extension of the <code>ISVNCredentials</code> specified for
 * use with the <i>svn+ssh</i> protocol.
 * 
 * @version 1.0
 * @author 	TMate Software Ltd.
 * @see		ISVNCredentials
 */
public interface ISVNSSHCredentials extends ISVNCredentials {
    /**
     * Gets a user's private key to be used in an SSH-tunnel securing  
     * 
     * @return		a private key to encrypt client's data being transmitted to a
     *  			<i>svnserve</i>	over an SSH-tunnel 
     */
    public String getPrivateKeyID();
    
    /**
     * Gets the passphrase - that is the password to the client's private key.
     * 
     * @return	a client's private key passphrase
     */
    public String getPassphrase();

}
