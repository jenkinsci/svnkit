package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.io.SVNException;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 10.06.2005
 * Time: 21:40:44
 * To change this template use File | Settings | File Templates.
 */
public class DefaultSVNCommitHandler implements ISVNCommitHandler {

    public String getCommitMessage(String message, SVNCommitItem[] commitables) throws SVNException {
        return message == null ? "" : message;
    }

}
