package org.tmatesoft.svn.core.internal.wc2;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb.Mode;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb;
import org.tmatesoft.svn.core.wc2.ISvnOperationRunner;
import org.tmatesoft.svn.core.wc2.SvnOperation;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public abstract class SvnLocalOperationRunner implements ISvnOperationRunner {
    
    private Collection<SvnWcGeneration> applicableWcFormats;    

    protected SvnLocalOperationRunner(SvnWcGeneration... applicableFormats) {
        this.applicableWcFormats = new HashSet<SvnWcGeneration>();
        this.applicableWcFormats.addAll(Arrays.asList(applicableFormats.clone()));
    }

    public boolean isApplicable(SvnOperation operation) throws SVNException {
        if (!operation.hasLocalTargets()) {
            return false;
        }
        
        SvnTarget firstOperationTarget = operation.getTargets().iterator().next();
        File firstTargetFile = firstOperationTarget.getFile();
        SvnWcGeneration detectedFormat = detectWcGeneration(firstTargetFile);
        
        return applicableWcFormats.contains(detectedFormat);
    }
    
    
    protected SvnWcGeneration detectWcGeneration(File path) throws SVNException {
        SVNWCDb db = new SVNWCDb();
        try {
            db.parseDir(path, Mode.ReadOnly);
            return SvnWcGeneration.V17;
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_WORKING_COPY) {
                return SvnWcGeneration.NOT_DETECTED;
            } else if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_UNSUPPORTED_FORMAT) {
                return SvnWcGeneration.V16;
            } else {
                throw e;
            }
        } finally {
            db.close();
        }
    }
    
}
