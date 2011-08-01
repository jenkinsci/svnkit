package org.tmatesoft.svn.core.internal.wc2.ng;


import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.wc2.SvnSwitch;

public class SvnNgSwitch extends SvnNgAbstractUpdate<Long, SvnSwitch> {

    @Override
    protected Long run(SVNWCContext context) throws SVNException {
        return doSwitch(getFirstTarget(), getOperation().getSwitchUrl(), getOperation().getRevision(), getOperation().getPegRevision(),
                getOperation().getDepth(), getOperation().isDepthIsSticky(), getOperation().isIgnoreExternals(),
                getOperation().isAllowUnversionedObstructions(), getOperation().isIgnoreAncestry(), getOperation().isSleepForTimestamp());
    }


}
