package org.tmatesoft.svn.core.internal.wc2.ng;


import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.wc2.SvnCheckout;

public class SvnNgCheckout extends SvnNgAbstractUpdate<Long, SvnCheckout>{

    @Override
    protected Long run(SVNWCContext context) throws SVNException {
        return checkout(getOperation().getUrl(), getFirstTarget(), getOperation().getPegRevision(), getOperation().getRevision(), getOperation().getDepth(), getOperation().isIgnoreExternals(), getOperation().isAllowUnversionedObstructions(), getOperation().isSleepForTimestamp());
    }

}
