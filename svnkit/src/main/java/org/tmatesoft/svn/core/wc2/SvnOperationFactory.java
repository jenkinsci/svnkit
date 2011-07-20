package org.tmatesoft.svn.core.wc2;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc2.SvnGetInfoRemote;
import org.tmatesoft.svn.core.internal.wc2.ng.SvnNgGetInfo;

public class SvnOperationFactory {
    
    private Map<Class<?>, List<ISvnOperationRunner<SvnOperation>>> operationRunners;
    
    public SvnOperationFactory() {
        operationRunners = new HashMap<Class<?>, List<ISvnOperationRunner<SvnOperation>>>();
        
        registerOperationRunner(SvnGetInfo.class, new SvnGetInfoRemote());
        registerOperationRunner(SvnGetInfo.class, new SvnNgGetInfo());
    }

    public ISvnOperationRunner<SvnOperation> getImplementation(SvnOperation operation) throws SVNException {
        if (operation == null) {
            return null;
        }
        List<ISvnOperationRunner<SvnOperation>> candidateRunners = operationRunners.get(operation.getClass());
        if (candidateRunners == null) {
            return null;
        }
        
        ISvnOperationRunner<SvnOperation> runner = null;
        for (ISvnOperationRunner<SvnOperation> candidateRunner : candidateRunners) {
            
            boolean isApplicable = candidateRunner.isApplicable(operation);
            if (!isApplicable) {
                continue;
            }
            runner = candidateRunner;
            break;
        }
        return runner;
    }
    
    @SuppressWarnings("unchecked")
    protected void registerOperationRunner(Class<?> operationClass, ISvnOperationRunner<? extends SvnOperation> runner) {
        if (operationClass == null || runner == null) {
            return;
        }
        List<ISvnOperationRunner<SvnOperation>> runners = operationRunners.get(operationClass);
        if (runners == null) {
            runners = new LinkedList<ISvnOperationRunner<SvnOperation>>();
            operationRunners.put(operationClass, runners);
        }
        runners.add((ISvnOperationRunner<SvnOperation>) runner);
    }

}
