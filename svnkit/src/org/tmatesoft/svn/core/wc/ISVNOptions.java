/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;
import java.text.DateFormat;
import java.util.Map;

import org.tmatesoft.svn.core.io.ISVNTunnelProvider;

/**
 * The <b>ISVNOptions</b> interface should be implemented to manage
 * global run-time configuration options. 
 * 
 * <p>
 * Like the Subversion client library SVNKit uses configuration options
 * during runtime. <b>ISVNOptions</b> is intended for managing those
 * options which are similar to ones you can meet in the <i>config</i> file 
 * located in the default Subversion configuration area - on <i>Windows</i> platforms
 * it's usually located in the <i>'Documents and Settings\UserName\Subversion'</i> 
 * (or simply <i>'%APPDATA%\Subversion'</i>) directory, on <i>Unix</i>-like platforms - in 
 * <i>'~/.subversion'</i>. <b>ISVNOptions</b> is not intended for managing those
 * options that can be met in the <i>servers</i> file (located in the same directory
 * as <i>config</i>) - options for network layers are managed by interfaces and classes
 * of the <B><A HREF="../auth/package-summary.html">org.tmatesoft.svn.core.auth</A></B> package. 
 * 
 * <p>
 * Every <b>SVN</b>*<b>Client</b>'s public constructor receives an <b>ISVNOptions</b> 
 * as a driver of the run-time configuration options. <b>SVNClientManager</b> also has
 * got several <b>newInstance()</b> methods that receive an options driver. Thus it's simpe 
 * to implement a specific options driver to <b>ISVNOptions</b> and use it instead of a default one.
 * However if you are not interested in customizing the run-time configuration area
 * you can use a default driver which uses config info from the default SVN configuration area (see
 * above).
 * 
 * <p>
 * Use {@link SVNWCUtil} to get a default options driver, like this:
 * <pre class="javacode">
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.ISVNOptions;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.SVNClientManager;
 * ...
 *     <span class="javacomment">//here the only one boolean parameter - <i>readonly</i> - enables</span>
 *     <span class="javacomment">//or disables writing to the config file: if true (like in this snippet) -</span>
 *     <span class="javacomment">//SVNKit can only read options from the config file but not write</span>
 *     ISVNOptions options = SVNWCUtil.createDefaultOptions(<span class="javakeyword">true</span>);
 *     SVNClientManager clientManager = SVNClientManager.newInstance(options, <span class="javastring">"name"</span>, <span class="javastring">"password"</span>);
 *     ...</pre>
 * <p> 
 * If you would like to have the default configuration area in a place different 
 * from the SVN default one, you should provide a preferred path to the config 
 * directory like this:
 * <pre class="javacode">
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.ISVNOptions;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.SVNClientManager;
 * ...
 *     File defaultConfigDir = <span class="javakeyword">new</span> File(<span class="javastring">"way/to/your/config/dir"</span>); 
 *     ISVNOptions options = SVNWCUtil.createDefaultOptions(defaultConfigDir, <span class="javakeyword">true</span>);
 *     SVNClientManager clientManager = SVNClientManager.newInstance(options, <span class="javastring">"name"</span>, <span class="javastring">"password"</span>);
 *     ...</pre><br />
 * In this case in the specified directory SVNKit will create necessary configuration files (in particular <i>config</i> and <i>servers</i>) which
 * are absolutely identical to those <u>default</u> ones (without any user's edits) located in the SVN config area.
 * 
 * <p>
 * Read also this <a href="http://svnbook.red-bean.com/nightly/en/svn-book.html#svn.advanced">Subversion book chapter</a> on runtime configuration area.
 * 
 * @version 1.1.0
 * @author  TMate Software Ltd.
 * @see     SVNWCUtil
 * @see     <a target="_top" href="http://svnkit.com/kb/examples/">Examples</a>
 * 
 */
public interface ISVNOptions extends ISVNTunnelProvider {
    
    /**
     * Determines if the commit-times option is enabled.  
     * 
     * <p>
     * The commit-times option makes checkout/update/switch/revert operations put
     * last-committed timestamps on every file they touch. 
     * 
     * <p>
     * This option corresponds to
     * the <i>'use-commit-times'</i> option that can be found in the 
     * SVN's <i>config</i> file under the <i>[miscellany]</i> section.
     * 
     * @return <span class="javakeyword">true</span> if commit-times
     *         are enabled, otherwise <span class="javakeyword">false</span>
     * @see    #setUseCommitTimes(boolean)        
     */
    public boolean isUseCommitTimes();
    
    /**
     * Enables or disables the commit-times option. 
     * 
     * <p>
     * The commit-times option makes checkout/update/switch/revert operations put
     * last-committed timestamps on every file they touch. 
     * 
     * <p>
     * This option corresponds to
     * the <i>'use-commit-times'</i> option that can be found in the 
     * SVN's <i>config</i> file under the <i>[miscellany]</i> section.
     *  
     * @param useCommitTimes  <span class="javakeyword">true</span> to 
     *                        enable commit-times, <span class="javakeyword">false</span>
     *                        to disable
     * @see                   #isUseCommitTimes()                        
     */
    public void setUseCommitTimes(boolean useCommitTimes);
    
    /**
     * Determines if the autoproperties option is enabled. 
     * 
     * <p>
     * Autoproperties are the properties that are automatically set 
     * on files when they are added or imported. 
     * 
     * <p>
     * This option corresponds to the <i>'enable-auto-props'</i> option 
     * that can be found in the SVN's <i>config</i> file under the 
     * <i>[miscellany]</i> section.
     * 
     * @return  <span class="javakeyword">true</span> if autoproperties
     *          are enabled, otherwise <span class="javakeyword">false</span>
     * @see     #setUseAutoProperties(boolean)         
     */
    public boolean isUseAutoProperties();
    
    /**
     * Enables or disables the autoproperties option.
     *
     * <p>
     * Autoproperties are the properties that are automatically set 
     * on files when they are added or imported. 
     * 
     * <p>
     * This option corresponds to the <i>'enable-auto-props'</i> option 
     * that can be found in the SVN's <i>config</i> file under the 
     * <i>[miscellany]</i> section.
     * 
     * @param useAutoProperties  <span class="javakeyword">true</span> to 
     *                           enable autoproperties, <span class="javakeyword">false</span>
     *                           to disable
     * @see                      #isUseAutoProperties()
     */
    public void setUseAutoProperties(boolean useAutoProperties);
    
    /**
     * Determines if the authentication storage is enabled.  
     * 
     * <p>
     * The auth storage is used for disk-caching of all 
     * authentication information: usernames, passwords, server certificates, 
     * and any other types of cacheable credentials. 
     * 
     * <p>
     * This option corresponds to the 
     * <i>'store-auth-creds'</i> option that can be found 
     * in the SVN's <i>config</i> file under the <i>[auth]</i> section. 
     * 
     * @return  <span class="javakeyword">true</span> if auth storage
     *          is enabled, otherwise <span class="javakeyword">false</span>
     * @see     #setAuthStorageEnabled(boolean)
     */
    public boolean isAuthStorageEnabled();
    
    /**
     * Enables or disables the authentication storage.
     * 
     * <p>
     * The auth storage is used for disk-caching of all 
     * authentication information: usernames, passwords, server certificates, 
     * and any other types of cacheable credentials. 
     * 
     * <p>
     * This option corresponds to the 
     * <i>'store-auth-creds'</i> option that can be found 
     * in the SVN's <i>config</i> file under the <i>[auth]</i> section. 
     * 
     * @param storeAuth  <span class="javakeyword">true</span> to 
     *                   enable the auth storage, <span class="javakeyword">false</span>
     *                   to disable
     * @see              #isAuthStorageEnabled()
     */
    public void setAuthStorageEnabled(boolean storeAuth);
    
    /**
     * Determines if a file is ignored according to the 
     * global ignore patterns.
     * 
     * <p>
     * The global ignore patterns describe the names of 
     * files and directories that SVNKit should ignore during status, add and 
     * import operations. Similar to the 
     * <i>'global-ignores'</i> option that can be found in the SVN's <i>config</i> 
     * file under the <i>[miscellany]</i> section.
     * 
     * @param  name  a file name
     * @return       <span class="javakeyword">true</span> if the file
     *               is ignored, otherwise <span class="javakeyword">false</span>
     */
    public boolean isIgnored(String name);
    
    /**
     * Returns all the global ignore patterns.
     * 
     * <p>
     * The global ignore patterns describe the names of 
     * files and directories that SVNKit should ignore during status, add and 
     * import operations. Similar to the 
     * <i>'global-ignores'</i> option that can be found in the SVN's <i>config</i> 
     * file under the <i>[miscellany]</i> section.
     * 
     * @return an array of patterns (that usually contain wildcards)
     *         that specify file and directory names to be ignored until
     *         they are versioned
     * @see    #setIgnorePatterns(String[])
     */
    public String[] getIgnorePatterns();
    
    /**
     * Sets global ignore patterns.
     * 
     * <p>
     * The global ignore patterns describe the names of 
     * files and directories that SVNKit should ignore during status, add and 
     * import operations. Similar to the 
     * <i>'global-ignores'</i> option that can be found in the SVN's <i>config</i> 
     * file under the <i>[miscellany]</i> section.
     * 
     * <p>
     * For example, to set all <code>.exe</code> files to be ignored include
     * <code>"*.exe"</code> pattern into <code>patterns</code>.
     * 
     * <p>
     * If <code>patterns</code> is <span class="javakeyword">null</span> or
     * empty then all the patterns will be removed.
     * 
     * @param patterns  an array of patterns (that usually contain wildcards)
     *                  that specify file and directory names to be ignored until
     *                  they are versioned
     * @see             #getIgnorePatterns()
     */
    public void setIgnorePatterns(String[] patterns);
    
    /**
     * Removes a particular global ignore pattern.
     * 
     * @param pattern a patterna to be removed
     * @see           #addIgnorePattern(String)
     */
    public void deleteIgnorePattern(String pattern);
    
    /**
     * Adds a new particular ignore pattern to global
     * ignore patterns. 
     * 
     * @param pattern an ignore pattern to be added
     * @see           #deleteIgnorePattern(String)
     */
    public void addIgnorePattern(String pattern);
    
    /**
     * Returns autoproperties as a {@link java.util.Map} 
     * where each key is a file name pattern and the corresponding
     * value is a string in the form of <code>"propName=propValue"</code>.
     * 
     * @return a {@link java.util.Map} containing autoproperties
     * @see      #setAutoProperties(Map)
     */
    public Map getAutoProperties();
    
    /**
     * Sets autoproperties that will be automatically put on all files
     * that will be added or imported. 
     * 
     * <p>
     * There can be several properties specified for one file pattern - 
     * they should be delimited by ";". 
     * 
     * @param autoProperties  a {@link java.util.Map} which keys are file
     *                        name patterns and their values are strings 
     *                        in the form of <code>"propName=propValue"</code>
     * @see                   #getAutoProperties()
     */
    public void setAutoProperties(Map autoProperties);
    
    /**
     * Removes a particular autoproperty by specifying a file name
     * pattern. 
     * 
     * @param pattern a file name pattern 
     * @see           #setAutoProperty(String, String)       
     * 
     */
    public void deleteAutoProperty(String pattern);
    
    /**
     * Sets an autoproperty - binds a file name pattern with a
     * string in the form of <code>"propName=propValue"</code>.
     * 
     * @param pattern      a file name pattern (usually containing 
     *                     wildcards)
     * @param properties   a property for <code>pattern</code>
     * @see                #deleteAutoProperty(String)
     */
    public void setAutoProperty(String pattern, String properties);
    
    /**
     * Collects and puts into a {@link java.util.Map} all 
     * autoproperties specified for the file name pattern matched by the 
     * target file name. 
     * 
     * <p>
     * If <code>fileName</code> matches any known file name pattern then
     * all properties set for that pattern will be collected and
     * placed into <code>target</code>. 
     * 
     * <p>
     * For one file name pattern there can be several autoproperties set,
     * delimited by ";".  
     * 
     * @param file      a target file
     * @param target    a {@link java.util.Map} that will receive
     *                  autoproperties
     * @return          <code>target</code> itself
     */
    public Map applyAutoProperties(File file, Map target);
    
    /**
     * Returns a factory object which is responsible for creating 
     * merger drivers. 
     * 
     * @return a factory that produces merger drivers
     *         for merge operations
     * @see    #setMergerFactory(ISVNMergerFactory) 
     */
    public ISVNMergerFactory getMergerFactory();
    
    /**
     * Sets a factory object which is responsible for creating 
     * merger drivers.
     *  
     * @param merger  a factory that produces merger drivers
     *                for merge operations
     * @see           #getMergerFactory()
     */
    public void setMergerFactory(ISVNMergerFactory merger);
    
    /**
     * Returns the value of a property from the <i>[svnkit]</i> section
     * of the <i>config</i> file. Currently not used.
     * 
     * @param   propertyName a SVNKit specific config property name
     * @return               the value of the property
     */
    public String getPropertyValue(String propertyName);
    
    /**
     * Sets the value of a property from the <i>[svnkit]</i> section
     * of the <i>config</i> file. Currently not used.
     * 
     * @param   propertyName   a SVNKit specific config property name
     * @param   propertyValue  a new value for the property; if 
     *                         <span class="javakeyword">null</span> the 
     *                         property is removed
     */
    public void setPropertyValue(String propertyName, String propertyValue);

    public DateFormat getKeywordDateFormat();
}
