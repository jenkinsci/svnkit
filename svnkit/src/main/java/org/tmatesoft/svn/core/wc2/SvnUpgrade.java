package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;

/**
 * Operation for upgrading the metadata storage format for a working copy.
 * 
 * <p/>
 * {@link #run()} returns {@link SvnWcGeneration} of resulting working copy.
 * 
 * @since 1.7 (SVN 1.7)
 * @author TMate Software Ltd.
 */
public class SvnUpgrade extends SvnOperation<SvnWcGeneration> {
    
    protected SvnUpgrade(SvnOperationFactory factory) {
        super(factory);
    }
}
