package org.tmatesoft.svn.core.wc2;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.tmatesoft.svn.core.ISVNCanceller;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb.Mode;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbOpenMode;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.internal.wc2.ng.SvnNgGetInfo;
import org.tmatesoft.svn.core.internal.wc2.old.SvnOldGetInfo;
import org.tmatesoft.svn.core.internal.wc2.remote.SvnRemoteGetInfo;
import org.tmatesoft.svn.core.wc.DefaultSVNRepositoryPool;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

public class SvnOperationFactory {
    
    private Map<Class<?>, List<ISvnOperationRunner<SvnOperation>>> anyFormatOperationRunners;
    private Map<Class<?>, List<ISvnOperationRunner<SvnOperation>>> noneOperationRunners;
    private Map<Class<?>, List<ISvnOperationRunner<SvnOperation>>> v17OperationRunners;
    private Map<Class<?>, List<ISvnOperationRunner<SvnOperation>>> v16OperationRunners;
    
    private ISVNAuthenticationManager authenticationManager;
    private ISVNCanceller canceller;
    private ISVNEventHandler eventHandler;
    private ISVNOptions options;
    private ISVNRepositoryPool repositoryPool;
    
    public SvnOperationFactory() {
        v17OperationRunners = new HashMap<Class<?>, List<ISvnOperationRunner<SvnOperation>>>();
        v16OperationRunners = new HashMap<Class<?>, List<ISvnOperationRunner<SvnOperation>>>();
        anyFormatOperationRunners = new HashMap<Class<?>, List<ISvnOperationRunner<SvnOperation>>>();
        noneOperationRunners = new HashMap<Class<?>, List<ISvnOperationRunner<SvnOperation>>>();
        
        registerOperationRunner(SvnGetInfo.class, new SvnRemoteGetInfo());
        registerOperationRunner(SvnGetInfo.class, new SvnNgGetInfo(), SvnWcGeneration.V17, SvnWcGeneration.NOT_DETECTED);
        registerOperationRunner(SvnGetInfo.class, new SvnOldGetInfo(), SvnWcGeneration.V16);
    }

    public ISVNAuthenticationManager getAuthenticationManager() {
        if (authenticationManager == null) {
            authenticationManager = SVNWCUtil.createDefaultAuthenticationManager();
        }
        return authenticationManager;
    }

    public ISVNCanceller getCanceller() {
        if (canceller == null && getEventHandler() != null) {
            return getEventHandler();
        }
        return canceller;
    }

    public ISVNEventHandler getEventHandler() {
        return eventHandler;
    }

    public ISVNRepositoryPool getRepositoryPool() {
        if (repositoryPool == null) {
            repositoryPool = new DefaultSVNRepositoryPool(getAuthenticationManager(), getOptions());
        }
        return repositoryPool;
    }

    public ISVNOptions getOptions() {
        return options;
    }

    public void setAuthenticationManager(ISVNAuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
        if (repositoryPool != null) {
            repositoryPool.setAuthenticationManager(authenticationManager);
        }
    }

    public void setCanceller(ISVNCanceller canceller) {
        this.canceller = canceller;
    }

    public void setEventHandler(ISVNEventHandler eventHandler) {
        this.eventHandler = eventHandler;
    }

    public void setOptions(ISVNOptions options) {
        this.options = options;
    }

    public SvnGetInfo createGetInfo() {
        SvnGetInfo getInfo = new SvnGetInfo(this);
        getInfo.initDefaults();
        return getInfo;
    }

    public ISvnOperationRunner<SvnOperation> getImplementation(SvnOperation operation) throws SVNException {
        if (operation == null) {
            return null;
        }
        SvnWcGeneration wcGeneration = null;
        if (operation.hasLocalTargets()) {
            wcGeneration = detectWcGeneration(operation.getFirstTarget().getFile());
        }
        final List<ISvnOperationRunner<SvnOperation>> candidateRunners = new LinkedList<ISvnOperationRunner<SvnOperation>>();
        
        candidateRunners.addAll(getRunners(operation.getClass(), anyFormatOperationRunners));
        if (wcGeneration == SvnWcGeneration.NOT_DETECTED) {
            candidateRunners.addAll(getRunners(operation.getClass(), noneOperationRunners));
        } else if (wcGeneration == SvnWcGeneration.V16) {
            candidateRunners.addAll(getRunners(operation.getClass(), v16OperationRunners));
        } else if (wcGeneration == SvnWcGeneration.V17) {
            candidateRunners.addAll(getRunners(operation.getClass(), v17OperationRunners));
        }
        
        ISvnOperationRunner<SvnOperation> runner = null;
        
        for (ISvnOperationRunner<SvnOperation> candidateRunner : candidateRunners) {            
            boolean isApplicable = candidateRunner.isApplicable(operation, wcGeneration);
            if (!isApplicable) {
                continue;
            }
            runner = candidateRunner;
            break;
        }
        if (runner != null) {
            runner.reset();
            runner.setWcGeneration(wcGeneration);
        }
        return runner;
    }
    
    @SuppressWarnings("unchecked")
    protected void registerOperationRunner(Class<?> operationClass, ISvnOperationRunner<? extends SvnOperation> runner, SvnWcGeneration... formats) {
        if (operationClass == null || runner == null) {
            return;
        }
        Collection<Map<Class<?>, List<ISvnOperationRunner<SvnOperation>>>> maps = new ArrayList<Map<Class<?>,List<ISvnOperationRunner<SvnOperation>>>>();
        if (formats == null || formats.length == 0) {
            maps.add(anyFormatOperationRunners);
        } else {
            Set<SvnWcGeneration> formatsSet = new HashSet<SvnWcGeneration>();
            formatsSet.addAll(Arrays.asList(formats));
            if (formatsSet.contains(SvnWcGeneration.NOT_DETECTED)) {
                maps.add(noneOperationRunners);
            }
            if (formatsSet.contains(SvnWcGeneration.V17)) {
                maps.add(v17OperationRunners);
            }
            if (formatsSet.contains(SvnWcGeneration.V16)) {
                maps.add(v16OperationRunners);
            }
        }
        for (Map<Class<?>, List<ISvnOperationRunner<SvnOperation>>> runnerMap : maps) {
            List<ISvnOperationRunner<SvnOperation>> runners = runnerMap.get(operationClass);
            if (runners == null) {
                runners = new LinkedList<ISvnOperationRunner<SvnOperation>>();
                runnerMap.put(operationClass, runners);
            }
            runners.add((ISvnOperationRunner<SvnOperation>) runner);
        }
    }
    
    protected SvnWcGeneration detectWcGeneration(File path) throws SVNException {
        SVNWCDb db = new SVNWCDb();
        try {
            db.open(SVNWCDbOpenMode.ReadOnly, (ISVNOptions) null, false, false);
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
    
    private static List<ISvnOperationRunner<SvnOperation>> getRunners(Class<?> clazz, Map<Class<?>, List<ISvnOperationRunner<SvnOperation>>> map) {
        List<ISvnOperationRunner<SvnOperation>> list = map.get(clazz);
        if (list == null) {
            list = Collections.emptyList();
        }
        return list;
    }

    public void setRepositoryPool(ISVNRepositoryPool repositoryPool) {
        this.repositoryPool = repositoryPool;
    }
}
