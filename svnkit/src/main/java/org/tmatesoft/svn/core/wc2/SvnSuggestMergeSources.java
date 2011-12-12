package org.tmatesoft.svn.core.wc2;

import java.util.Collection;

import org.tmatesoft.svn.core.SVNURL;

public class SvnSuggestMergeSources extends SvnOperation<Collection<SVNURL>> {

    protected SvnSuggestMergeSources(SvnOperationFactory factory) {
        super(factory);
    }

}
