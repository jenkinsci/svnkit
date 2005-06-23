package org.tmatesoft.svn.core.wc;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 22.06.2005
 * Time: 20:42:05
 * To change this template use File | Settings | File Templates.
 */
public interface ISVNOptions extends ISVNAuthenticationManager {

    public boolean isUseCommitTimes();

    public void setUseCommitTimes(boolean useCommitTimes);

    public boolean isUseAutoProperties();

    public void setUseAutoProperties(boolean useAutoProperties);

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

    public boolean matches(String pattern, String fileName);

    public boolean isModified();

    public void save(boolean forceSave);
}
