package org.tmatesoft.svn.core.internal.wc2;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc2.ISvnOperationRunner;
import org.tmatesoft.svn.core.wc2.SvnOperation;

public abstract class SvnLocalOperationRunner implements ISvnOperationRunner {
    
    private Collection<SvnWcFormat> applicableWcFormats;    

    protected SvnLocalOperationRunner(SvnWcFormat... applicableFormats) {
        this.applicableWcFormats = new HashSet<SvnWcFormat>();
        this.applicableWcFormats.addAll(Arrays.asList(applicableFormats.clone()));
    }

    public boolean isApplicable(SvnOperation operation) throws SVNException {
        if (operation == null || operation.getTarget() == null) {
            return false;
        }        
        if (operation.getTarget().isURL()) {
            return false;
        }
        File targetFile = operation.getTarget().getFile();
        SvnWcFormat detectedFormat = detectWcFormat(targetFile);
        return applicableWcFormats.contains(detectedFormat);
    }
    
    
    protected SvnWcFormat detectWcFormat(File path) throws SVNException {
        return SvnWcFormat.None;
    }
    
}
