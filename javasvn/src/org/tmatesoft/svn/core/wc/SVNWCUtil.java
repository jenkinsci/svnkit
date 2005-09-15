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

import java.io.File;
import java.lang.reflect.Constructor;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNExternalInfo;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * The <b>SVNWCUtil</b> is a utility class providing some common methods 
 * used by Working Copy API classes for such purposes as creating default
 * run-time configuration and authentication drivers and some others.
 *  
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 * @see     ISVNOptions
 * @see     <a target="_top" href="http://tmate.org/svn/kb/examples/">Examples</a>
 */
public class SVNWCUtil {
    
    private static final String ECLIPSE_AUTH_MANAGER_CLASSNAME = "org.tmatesoft.svn.core.internal.wc.EclipseSVNAuthenticationManager";

    /**
     * Gets the location of the default SVN's run-time configuration area
     * on the current machine. The result path depends on the platform
     * on which JavaSVN is running:
     * <ul>
     * <li>on <i>Windows</i> this path usually looks like <i>'Documents and Settings\UserName\Subversion'</i> 
     * or simply <i>'%APPDATA%\Subversion'</i>.
     * <li>on a <i>Unix</i>-like platform - <i>'~/.subversion'</i>.
     * </ul>
     * 
     * @return a {@link java.io.File} representation of the default
     *         SVN's run-time configuration area location
     */
    public static File getDefaultConfigurationDirectory() {
        if (SVNFileUtil.isWindows) {
            return new File(SVNFileUtil.getApplicationDataPath(), "Subversion");
        }
        return new File(System.getProperty("user.home"), ".subversion");
    }
    
    /**
     * Creates a default authentication manager that uses the default SVN's
     * <i>servers</i> configuration and authentication storage. Whether
     * the default auth storage is used or not depends on the 'store-auth-creds'</i> 
     * option that can be found in the SVN's <i>config</i> file under the 
     * <i>[auth]</i> section.
     * 
     * @return a default implementation of the credentials and servers 
     *         configuration driver interface
     * @see    #getDefaultConfigurationDirectory()
     */
    public static ISVNAuthenticationManager createDefaultAuthenticationManager() {
        return createDefaultAuthenticationManager(getDefaultConfigurationDirectory(), null, null);
    }

    /**
     * Creates a default authentication manager that uses the <i>servers</i> 
     * configuration and authentication storage located in the provided
     * directory. The authentication storage is enabled.
     * 
     * @param  configDir  a new location of the run-time configuration
     *                    area  
     * @return            a default implementation of the credentials 
     *                    and servers configuration driver interface
     */
    public static ISVNAuthenticationManager createDefaultAuthenticationManager(File configDir) {
        return createDefaultAuthenticationManager(configDir, null, null, true);
    }
    
    /**
     * Creates a default authentication manager that uses the default SVN's
     * <i>servers</i> configuration and provided user's credentials. 
     * Whether the default auth storage is used or not depends on the 
     * 'store-auth-creds'</i> option that can be found in the SVN's 
     * <i>config</i> file under the <i>[auth]</i> section.
     * 
     * @param  userName   a user's name
     * @param  password   a user's password
     * @return            a default implementation of the credentials 
     *                    and servers configuration driver interface
     */
    public static ISVNAuthenticationManager createDefaultAuthenticationManager(String userName, String password) {
        return createDefaultAuthenticationManager(null, userName, password);
    }
    
    /**
     * Creates a default authentication manager that uses the provided
     * configuration directory and user's credentials. Whether
     * the default auth storage is used or not depends on the 'store-auth-creds'</i> 
     * option that is looked up in the <i>config</i> file under the 
     * <i>[auth]</i> section. Files <i>config</i> and <i>servers</i> will
     * be created (if they still don't exist) in the specified directory 
     * (they are the same as those ones you can find in the default SVN's 
     * run-time configuration area).
     * 
     * @param  configDir  a new location of the run-time configuration
     *                    area
     * @param  userName   a user's name
     * @param  password   a user's password
     * @return            a default implementation of the credentials 
     *                    and servers configuration driver interface
     */
    public static ISVNAuthenticationManager createDefaultAuthenticationManager(File configDir, String userName, String password) {
        ISVNOptions options = createDefaultOptions(configDir, true);
        boolean store = options.isAuthStorageEnabled();
        return createDefaultAuthenticationManager(configDir, userName, password, store);
    }
    
    /**
     * Creates a default authentication manager that uses the provided
     * configuration directory and user's credentials. The <code>storeAuth</code>
     * parameter affects on using the auth storage. 
     *  
     *
     * @param  configDir  a new location of the run-time configuration
     *                    area
     * @param  userName   a user's name
     * @param  password   a user's password
     * @param  storeAuth  if <span class="javakeyword">true</span> then
     *                    the auth storage is enabled, otherwise disabled
     * @return            a default implementation of the credentials 
     *                    and servers configuration driver interface
     */
    public static ISVNAuthenticationManager createDefaultAuthenticationManager(File configDir, String userName, String password, boolean storeAuth) {
        // check whether we are running inside Eclipse.
        if (isEclipse()) {
            // use reflection to allow compilation when there is no Eclipse.
            try {
                Class managerClass = SVNWCUtil.class.getClassLoader().loadClass(ECLIPSE_AUTH_MANAGER_CLASSNAME);
                if (managerClass != null) {
                    Constructor method = managerClass.getConstructor(new Class[] {File.class, Boolean.TYPE, String.class, String.class});
                    if (method != null) {
                        return (ISVNAuthenticationManager) method.newInstance(new Object[] {configDir, storeAuth ? Boolean.TRUE : Boolean.FALSE, userName, password});
                    }
                }
            } catch (Throwable e) {
                SVNDebugLog.logInfo(e);
            } 
        }
        return new DefaultSVNAuthenticationManager(configDir, storeAuth, userName, password);
    }
    
    /**
     * Creates a default run-time configuration options driver that uses
     * the provided configuration directory.
     * 
     * <p>
     * If <code>dir</code> is not <span class="javakeyword">null</span>
     * then all necessary config files (in particular <i>config</i> and 
     * <i>servers</i>) will be created in this directory if they still
     * don't exist. Those files are the same as those ones you can find in 
     * the default SVN's run-time configuration area.  
     * 
     * @param  dir        a new location of the run-time configuration
     *                    area  
     * @param  readonly   if <span class="javakeyword">true</span> then
     *                    run-time configuration options are available only
     *                    for reading, if <span class="javakeyword">false</span>
     *                    then those options are available for both reading
     *                    and writing
     * @return            a default implementation of the run-time 
     *                    configuration options driver interface
     */
    public static ISVNOptions createDefaultOptions(File dir, boolean readonly) {
        return new DefaultSVNOptions(dir, readonly);
    }
    
    /**
     * Creates a default run-time configuration options driver that uses 
     * the default SVN's run-time configuration area.
     * 
     * @param  readonly   if <span class="javakeyword">true</span> then
     *                    run-time configuration options are available only
     *                    for reading, if <span class="javakeyword">false</span>
     *                    then those options are available for both reading
     *                    and writing
     * @return            a default implementation of the run-time 
     *                    configuration options driver interface
     * @see               #getDefaultConfigurationDirectory()         
     */
    public static ISVNOptions createDefaultOptions(boolean readonly) {
        return new DefaultSVNOptions(null, readonly);
    }
    
    /**
     * Determines if a directory is under version control. 
     * 
     * @param  dir  a directory to check 
     * @return      <span class="javakeyword">true</span> if versioned,
     *              otherwise <span class="javakeyword">false</span>
     */
    public static boolean isVersionedDirectory(File dir) {
        return SVNWCAccess.isVersionedDirectory(dir);
    }
    
    /**
     * Determines if a directory is the root of the Working Copy.
     * 
     * @param  versionedDir             a versioned directory to check
     * @param  considerExternalAsRoot   if <span class="javakeyword">true</span>
     *                                  and <code>versionedDir</code> is really
     *                                  versioned and the root of externals definitions
     *                                  then this method will return <span class="javakeyword">true</span>
     * @return                          <span class="javakeyword">true</span> if 
     *                                  <code>versionedDir</code> is versioned and the WC root
     *                                  (or the root of externals if <code>considerExternalAsRoot</code>
     *                                  is <span class="javakeyword">true</span>), otherwise <span class="javakeyword">false</span> 
     */
    public static boolean isWorkingCopyRoot(final File versionedDir, final boolean considerExternalAsRoot) {
        if (versionedDir == null || !isVersionedDirectory(versionedDir)) {
            // unversioned.
            return false;
        }
        // call status of parent instead:
        if (versionedDir.getParentFile() == null) {
            return true;
        }
        SVNStatusClient stClient = new SVNStatusClient((ISVNAuthenticationManager) null, null);
        final boolean[] isRoot = new boolean[] { true }; // if no status is
                                                            // reporter, this
                                                            // may be ignored
                                                            // dir in parent's
                                                            // folder.
        try {
            stClient.doStatus(versionedDir.getParentFile(), false, false, true,
                    true, new ISVNStatusHandler() {
                        public void handleStatus(SVNStatus status) {
                            if (versionedDir.equals(status.getFile())) {
                                isRoot[0] = false;
                                if (status.getContentsStatus() == SVNStatusType.STATUS_IGNORED
                                        || status.getContentsStatus() == SVNStatusType.STATUS_UNVERSIONED
                                        || status.getContentsStatus() == SVNStatusType.STATUS_EXTERNAL) {
                                    if (status.getContentsStatus() == SVNStatusType.STATUS_EXTERNAL
                                            && !considerExternalAsRoot) {
                                        return;
                                    }
                                    isRoot[0] = true;
                                }
                            }
                        }
                    });
        } catch (SVNException e) {
            return true;
        }
        return isRoot[0];
    }
    
    /**
     * Returns the Working Copy root directory given a versioned
     * directory that belongs to the Working Copy.
     * 
     *  <p>
     *  If both <span>versionedDir</span> and its parent directory are
     *  not versioned this method returns <span class="javakeyword">null</span>.
     * 
     * @param  versionedDir     a directory belonging to the WC which
     *                          root is to be searched for
     * @param  stopOnExtenrals  if <span class="javakeyword">true</span> then
     *                          this method will stop at the directory on
     *                          which any externals definitions are set
     * @return                  the WC root directory (if it is found) or
     *                          <span class="javakeyword">null</span>.
     */
    public static File getWorkingCopyRoot(File versionedDir,
            boolean stopOnExtenrals) {
        versionedDir = versionedDir.getAbsoluteFile();
        if (versionedDir == null
                || (!isVersionedDirectory(versionedDir) && !isVersionedDirectory(versionedDir.getParentFile()))) {
            // both this dir and its parent are not versioned.
            return null;
        }

        if (isWorkingCopyRoot(versionedDir, true)) {
            // this is root.
            if (stopOnExtenrals) {
                return versionedDir;
            }
            File parent = versionedDir.getParentFile();
            File parentRoot = getWorkingCopyRoot(parent, stopOnExtenrals);
            if (parentRoot == null) {
                // if parent is not versioned return this dir.
                return versionedDir;
            }
            // parent is versioned. we have to check if it contains externals
            // definition for this dir.

            while (parent != null) {
                try {
                    SVNWCAccess parentAccess = SVNWCAccess.create(parent);
                    SVNProperties props = parentAccess.getTarget()
                            .getProperties("", false);
                    SVNExternalInfo[] externals = SVNWCAccess.parseExternals(
                            "", props.getPropertyValue(SVNProperty.EXTERNALS));
                    // now externals could point to our dir.
                    for (int i = 0; i < externals.length; i++) {
                        SVNExternalInfo external = externals[i];
                        File externalFile = new File(parent, external.getPath());
                        if (externalFile.equals(versionedDir)) {
                            return parentRoot;
                        }
                    }
                } catch (SVNException e) {
                    //
                }
                if (parent.equals(parentRoot)) {
                    break;
                }
                parent = parent.getParentFile();
            }
            return versionedDir;
        }
        // if dir is not a root -> just recurse till root, the call get root
        // again.
        if (versionedDir.getParentFile() != null) {
            return getWorkingCopyRoot(versionedDir.getParentFile(), stopOnExtenrals);
        }
        return versionedDir;
    }
    
    private static boolean isEclipse() {
        Class platform = null;
        try {
            platform = SVNWCUtil.class.getClassLoader().loadClass("org.eclipse.core.runtime.Platform");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return platform != null;
    }
}