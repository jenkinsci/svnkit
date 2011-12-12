package org.tmatesoft.svn.core.wc2;

import java.util.Map;

import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNURL;

public class SvnGetMergeInfo extends SvnOperation<Map<SVNURL, SVNMergeRangeList>> {

    protected SvnGetMergeInfo(SvnOperationFactory factory) {
        super(factory);
    }

}
