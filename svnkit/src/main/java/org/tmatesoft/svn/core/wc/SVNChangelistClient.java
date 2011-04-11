/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;
import java.util.Collection;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc16.SVNChangelistClient16;
import org.tmatesoft.svn.core.internal.wc17.SVNChangelistClient17;
import org.tmatesoft.svn.core.io.SVNRepository;

/**
 * The <b>SVNChangelistClient</b> provides API for managing changelists.
 * 
 * <p>
 * Here's a list of the <b>SVNChangelistClient</b>'s methods matched against
 * corresponing commands of the SVN command line client:
 * 
 * <table cellpadding="3" cellspacing="1" border="0" width="40%" bgcolor="#999933">
 * <tr bgcolor="#ADB8D9" align="left">
 * <td><b>SVNKit</b></td>
 * <td><b>Subversion</b></td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doAddToChangelist()</td>
 * <td>'svn changelist CLNAME TARGET'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doRemoveFromChangelist()</td>
 * <td>'svn changelist --remove TARGET'</td>
 * </tr>
 * </table>
 * 
 * @version 1.3
 * @author TMate Software Ltd.
 * @since 1.2
 */
public class SVNChangelistClient extends SVNBasicClient {

    private SVNChangelistClient16 getSVNChangelistClient16() {
        return (SVNChangelistClient16) getDelegate16();
    }

    private SVNChangelistClient17 getSVNChangelistClient17() throws SVNException {
        return (SVNChangelistClient17) getDelegate17();
    }

    /**
     * Constructs and initializes an <b>SVNChangelistClient</b> object with the
     * specified run-time configuration and authentication drivers.
     * 
     * <p/>
     * If <code>options</code> is <span class="javakeyword">null</span>, then
     * this <b>SVNChangelistClient</b> will be using a default run-time
     * configuration driver which takes client-side settings from the default
     * SVN's run-time configuration area but is not able to change those
     * settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).
     * 
     * <p/>
     * If <code>authManager</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNChangelistClient</b> will be using a default
     * authentication and network layers driver (see
     * {@link SVNWCUtil#createDefaultAuthenticationManager()}) which uses
     * server-side settings and auth storage from the default SVN's run-time
     * configuration area (or system properties if that area is not found).
     * 
     * @param authManager
     *            an authentication and network layers driver
     * @param options
     *            a run-time configuration options driver
     */
    public SVNChangelistClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(new SVNChangelistClient16(authManager, options), new SVNChangelistClient17(authManager, options));

        setOptions(options);
    }

    /**
     * Constructs and initializes an <b>SVNChangelistClient</b> object with the
     * specified run-time configuration and repository pool object.
     * 
     * <p/>
     * If <code>options</code> is <span class="javakeyword">null</span>, then
     * this <b>SVNChangelistClient</b> will be using a default run-time
     * configuration driver which takes client-side settings from the default
     * SVN's run-time configuration area but is not able to change those
     * settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).
     * 
     * <p/>
     * If <code>repositoryPool</code> is <span class="javakeyword">null</span>,
     * then {@link org.tmatesoft.svn.core.io.SVNRepositoryFactory} will be used
     * to create {@link SVNRepository repository access objects}.
     * 
     * @param repositoryPool
     *            a repository pool object
     * @param options
     *            a run-time configuration options driver
     */
    public SVNChangelistClient(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        super(new SVNChangelistClient16(repositoryPool, options), new SVNChangelistClient17(repositoryPool, options));

        setOptions(options);
    }

    /**
     * @param path
     * @param changeLists
     * @param depth
     * @param handler
     * @throws SVNException
     * @deprecated use
     *             {@link #doGetChangeLists(File, Collection, SVNDepth, ISVNChangelistHandler)}
     *             instead
     */
    public void getChangeLists(File path, final Collection changeLists, SVNDepth depth, final ISVNChangelistHandler handler) throws SVNException {
        try {
            getSVNChangelistClient17().getChangeLists(path, changeLists, depth, handler);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_UNSUPPORTED_FORMAT) {
                getSVNChangelistClient16().getChangeLists(path, changeLists, depth, handler);
                return;
            }
            throw e;
        }
    }

    /**
     * @param changeLists
     * @param targets
     * @param depth
     * @param handler
     * @throws SVNException
     * @deprecated use
     *             {@link #doGetChangeListPaths(Collection, Collection, SVNDepth, ISVNChangelistHandler)}
     *             instead
     */
    public void getChangeListPaths(Collection changeLists, Collection targets, SVNDepth depth, ISVNChangelistHandler handler) throws SVNException {
        try {
            getSVNChangelistClient17().getChangeListPaths(changeLists, targets, depth, handler);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_UNSUPPORTED_FORMAT) {
                getSVNChangelistClient16().getChangeListPaths(changeLists, targets, depth, handler);
                return;
            }
            throw e;
        }
    }

    /**
     * @param paths
     * @param depth
     * @param changelist
     * @param changelists
     * @throws SVNException
     * @deprecated use
     *             {@link #doAddToChangelist(File[], SVNDepth, String, String[])}
     *             instead
     */
    public void addToChangelist(File[] paths, SVNDepth depth, String changelist, String[] changelists) throws SVNException {
        try {
            getSVNChangelistClient17().addToChangelist(paths, depth, changelist, changelists);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_UNSUPPORTED_FORMAT) {
                getSVNChangelistClient16().addToChangelist(paths, depth, changelist, changelists);
                return;
            }
            throw e;
        }
    }

    /**
     * @param paths
     * @param depth
     * @param changelists
     * @throws SVNException
     * @deprecated use
     *             {@link #doRemoveFromChangelist(File[], SVNDepth, String[])}
     *             instead
     */
    public void removeFromChangelist(File[] paths, SVNDepth depth, String[] changelists) throws SVNException {
        try {
            getSVNChangelistClient17().removeFromChangelist(paths, depth, changelists);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_UNSUPPORTED_FORMAT) {
                getSVNChangelistClient16().removeFromChangelist(paths, depth, changelists);
                return;
            }
            throw e;
        }
    }

    /**
     * Adds each path in <code>paths</code> (recursing to <code>depth</code> as
     * necessary) to <code>changelist</code>. If a path is already a member of
     * another changelist, then removes it from the other changelist and adds it
     * to <code>changelist</code>. (For now, a path cannot belong to two
     * changelists at once.)
     * 
     * <p/>
     * <code>changelists</code> is an array of <code>String</code> changelist
     * names, used as a restrictive filter on items whose changelist assignments
     * are adjusted; that is, doesn't tweak the changeset of any item unless
     * it's currently a member of one of those changelists. If
     * <code>changelists</code> is empty (or <span
     * class="javakeyword">null</span>), no changelist filtering occurs.
     * 
     * <p/>
     * Note: this metadata is purely a client-side "bookkeeping" convenience,
     * and is entirely managed by the working copy.
     * 
     * <p/>
     * Note: this method does not require repository access.
     * 
     * @param paths
     *            working copy paths to add to <code>changelist</code>
     * @param depth
     *            tree depth to process
     * @param changelist
     *            name of the changelist to add new paths to
     * @param changelists
     *            collection of changelist names as a filter
     * @throws SVNException
     * @since 1.2.0, New in SVN 1.5.0
     */
    public void doAddToChangelist(File[] paths, SVNDepth depth, String changelist, String[] changelists) throws SVNException {
        try {
            getSVNChangelistClient17().doAddToChangelist(paths, depth, changelist, changelists);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_UNSUPPORTED_FORMAT) {
                getSVNChangelistClient16().doAddToChangelist(paths, depth, changelist, changelists);
                return;
            }
            throw e;
        }
    }

    /**
     * Removes each path in <code>paths</code> (recursing to <code>depth</code>
     * as necessary) from changelists to which they are currently assigned.
     * 
     * <p/>
     * <code>changelists</code> is an array of <code>String</code> changelist
     * names, used as a restrictive filter on items whose changelist assignments
     * are removed; that is, doesn't remove from a changeset any item unless
     * it's currently a member of one of those changelists. If
     * <code>changelists</code> is empty (or <span
     * class="javakeyword">null</span>), all changelist assignments in and under
     * each path in <code>paths</code> (to <code>depth</code>) will be removed.
     * 
     * <p/>
     * Note: this metadata is purely a client-side "bookkeeping" convenience,
     * and is entirely managed by the working copy.
     * 
     * <p/>
     * Note: this method does not require repository access.
     * 
     * @param paths
     *            paths to remove from any changelists
     * @param depth
     *            tree depth to process
     * @param changelists
     *            collection of changelist names as a filter
     * @throws SVNException
     * @since 1.2.0, New in SVN 1.5.0
     */
    public void doRemoveFromChangelist(File[] paths, SVNDepth depth, String[] changelists) throws SVNException {
        try {
            getSVNChangelistClient17().doRemoveFromChangelist(paths, depth, changelists);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_UNSUPPORTED_FORMAT) {
                getSVNChangelistClient16().doRemoveFromChangelist(paths, depth, changelists);
                return;
            }
            throw e;
        }
    }

    /**
     * Gets paths belonging to the specified changelists discovered under the
     * specified targets.
     * 
     * <p/>
     * This method is just like
     * {@link #doGetChangeLists(File, Collection, SVNDepth, ISVNChangelistHandler)}
     * except for it operates on multiple targets instead of a single one.
     * 
     * <p/>
     * Note: this method does not require repository access.
     * 
     * @param changeLists
     *            collection of changelist names
     * @param targets
     *            working copy paths to operate on
     * @param depth
     *            tree depth to process
     * @param handler
     *            caller's handler to receive path-to-changelist information
     * @throws SVNException
     */
    public void doGetChangeListPaths(Collection changeLists, Collection targets, SVNDepth depth, ISVNChangelistHandler handler) throws SVNException {
        try {
            getSVNChangelistClient17().doGetChangeListPaths(changeLists, targets, depth, handler);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_UNSUPPORTED_FORMAT) {
                getSVNChangelistClient16().doGetChangeListPaths(changeLists, targets, depth, handler);
                return;
            }
            throw e;
        }
    }

    /**
     * Gets paths belonging to the specified changelists discovered under the
     * specified path.
     * 
     * <p/>
     * Beginning at <code>path</code>, crawls to <code>depth</code> to discover
     * every path in or under
     * <code>path<code> which belongs to one of the changelists in <code>changeLists</code>
     * (a collection of <code>String</code> changelist names). If
     * <code>changeLists</code> is null, discovers paths with any changelist.
     * Calls <code>handler</code> each time a changelist-having path is
     * discovered.
     * 
     * <p/>
     * If there was an event handler provided via
     * {@link #setEventHandler(ISVNEventHandler)}, then its
     * {@link ISVNEventHandler#checkCancelled()} will be invoked during the
     * recursive walk.
     * 
     * <p/>
     * Note: this method does not require repository access.
     * 
     * @param path
     *            target working copy path
     * @param changeLists
     *            collection of changelist names
     * @param depth
     *            tree depth to process
     * @param handler
     *            caller's handler to receive path-to-changelist information
     * @throws SVNException
     * @since 1.2.0, New in SVN 1.5.0
     */
    public void doGetChangeLists(File path, final Collection changeLists, SVNDepth depth, final ISVNChangelistHandler handler) throws SVNException {
        try {
            getSVNChangelistClient17().doGetChangeLists(path, changeLists, depth, handler);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_UNSUPPORTED_FORMAT) {
                getSVNChangelistClient16().doGetChangeLists(path, changeLists, depth, handler);
                return;
            }
            throw e;
        }
    }

}
