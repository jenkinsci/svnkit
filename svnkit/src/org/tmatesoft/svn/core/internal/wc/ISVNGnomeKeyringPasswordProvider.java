package org.tmatesoft.svn.core.internal.wc;

import org.tmatesoft.svn.core.SVNException;

public interface ISVNGnomeKeyringPasswordProvider {

    public String getKeyringPassword(String keyringName) throws SVNException;
}
