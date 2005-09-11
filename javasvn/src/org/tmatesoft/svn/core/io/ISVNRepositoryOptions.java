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
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public interface ISVNRepositoryOptions {
    
    public boolean keepConnection();
    
    public void putData(SVNRepository repository, String key, Object value);

    public Object getData(SVNRepository repository, String key);
    
    public boolean hasData(SVNRepository repository, String key);
    
    public ISVNRepositoryOptions DEFAULT = new ISVNRepositoryOptions() {
        public boolean keepConnection() {
            return false;
        }
        public void putData(SVNRepository repository, String key, Object value) {
        }
        public Object getData(SVNRepository repository, String key) {
            return null;
        }
        public boolean hasData(SVNRepository repository, String key) {
            return false;
        }
    };

}
