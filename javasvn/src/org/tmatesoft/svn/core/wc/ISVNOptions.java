/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.util.Map;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public interface ISVNOptions {

    public boolean isUseCommitTimes();

    public void setUseCommitTimes(boolean useCommitTimes);

    public boolean isUseAutoProperties();

    public void setUseAutoProperties(boolean useAutoProperties);
    
    public boolean isAuthStorageEnabled();
    
    public void setAuthStorageEnabled(boolean storeAuth);

    public boolean isIgnored(String name);

    public String[] getIgnorePatterns();

    public void setIgnorePatterns(String[] patterns);

    public void deleteIgnorePattern(String pattern);

    public void addIgnorePattern(String pattern);

    public Map getAutoProperties();

    public void setAutoProperties(Map autoProperties);

    public void deleteAutoProperty(String pattern);

    public void setAutoProperty(String pattern, String properties);

    public Map applyAutoProperties(String fileName, Map target);
}
