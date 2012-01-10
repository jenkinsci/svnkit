package org.tmatesoft.svn.core.wc2.hooks;

import java.io.File;

import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;

public interface ISvnExternalsHandler {

    /**
     * Handles an external definition and says whether to skip it or not.
     * This method receives external definition parameters and returns whether 
     * <span class="javakeyword">null</span> to indicate that this external definition must be excluded 
     * from processing (for examle, not updated during an update), or a non-
     * <span class="javakeyword">null</span> array. This array should contain at least two {@link SVNRevision}
     * objects [revision, pegRevision] which will be used by the operation instead of 
     * <code>externalRevision</code> and <code>externalPegRevision</code> respectively passed into 
     * this handle method.
     * 
     * <p/>
     * <code>externalWorkingRevision</code> is always {@link SVNRevision#UNDEFINED} for update/checkout operations. 
     * 
     * @param externalPath              path of the external to be processed
     * @param externalURL               URL of the external to be processed or <span class="javakeyword">null</span> 
     *                                  if external is about to be removed
     * @param externalRevision          default revision to checkout/copy external at or update to
     * @param externalPegRevision       default peg revision to use for checkout/update/copy of external
     * @param externalsDefinition       raw <span class="javastring">svn:externals</span> property value
     * @param externalsWorkingRevision  current external working copy revision (relevant only for wc-to-url 
     *                                  copying operations)
     * @return                          array of {@link SVNRevision}s in form of {revision, pegRevision} or 
     *                                  <span class="javakeyword">null</span> to skip processing 
     *                                  of this external
     */
    public SVNRevision[] handleExternal(File externalPath, SVNURL externalURL, SVNRevision externalRevision, 
            SVNRevision externalPegRevision, String externalsDefinition, SVNRevision externalsWorkingRevision);

}
