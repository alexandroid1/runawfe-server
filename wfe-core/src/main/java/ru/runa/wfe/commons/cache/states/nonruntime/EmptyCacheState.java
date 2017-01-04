package ru.runa.wfe.commons.cache.states.nonruntime;

import javax.transaction.Transaction;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import ru.runa.wfe.commons.cache.CacheImplementation;
import ru.runa.wfe.commons.cache.ChangedObjectParameter;
import ru.runa.wfe.commons.cache.sm.CacheStateMachineContext;
import ru.runa.wfe.commons.cache.states.CacheState;
import ru.runa.wfe.commons.cache.states.DirtyTransactions;
import ru.runa.wfe.commons.cache.states.StateCommandResult;
import ru.runa.wfe.commons.cache.states.StateCommandResultWithCache;
import ru.runa.wfe.commons.cache.states.StateCommandResultWithData;

/**
 * Cache lifetime state machine for non runtime caches. Current state is empty cache (initialization required).
 */
public class EmptyCacheState<CacheImpl extends CacheImplementation> implements CacheState<CacheImpl> {

    /**
     * Logging support.
     */
    private static Log log = LogFactory.getLog(EmptyCacheState.class);

    /**
     * Current cache implementation.
     */
    private final CacheImpl cache;

    /**
     * State context.
     */
    private final NonRuntimeCacheContext stateContext;

    private EmptyCacheState(CacheImpl cache, NonRuntimeCacheContext stateContext) {
        this.cache = cache;
        this.stateContext = stateContext;
    }

    @Override
    public boolean isDirtyTransactionExists() {
        return false;
    }

    @Override
    public boolean isDirtyTransaction(Transaction transaction) {
        return false;
    }

    @Override
    public CacheImpl getCacheQuickNoBuild(Transaction transaction) {
        return null;
    }

    @Override
    public StateCommandResultWithCache<CacheImpl> getCache(CacheStateMachineContext<CacheImpl> context, Transaction transaction) {
        return initiateCacheCreation(context);
    }

    @Override
    public StateCommandResultWithCache<CacheImpl> getCacheIfNotLocked(CacheStateMachineContext<CacheImpl> context, Transaction transaction) {
        return initiateCacheCreation(context);
    }

    /**
     * Create cache and start delayed initialization if required.
     *
     * @param context
     *            Cache state machine context with common used data.
     * @return Return next state for state machine.
     */
    private StateCommandResultWithCache<CacheImpl> initiateCacheCreation(CacheStateMachineContext<CacheImpl> context) {
        if (stateContext.isReinitializationRequired() || cache == null) {
            if (context.getCacheFactory().hasDelayedInitialization()) {
                CacheImpl cache = this.cache != null ? this.cache : context.getCacheFactory().createCache();
                CacheState<CacheImpl> initializingState = context.getStateFactory().createInitializingState(cache, stateContext);
                return new StateCommandResultWithCache<CacheImpl>(initializingState, cache);
            }
            CacheImpl cache = context.getCacheFactory().createCache();
            cache.commitCache();
            return new StateCommandResultWithCache<CacheImpl>(context.getStateFactory().createInitializedState(cache, stateContext), cache);
        }
        return new StateCommandResultWithCache<CacheImpl>(context.getStateFactory().createInitializedState(cache, stateContext), cache);
    }

    @Override
    public StateCommandResult<CacheImpl> onChange(CacheStateMachineContext<CacheImpl> context, Transaction transaction,
            ChangedObjectParameter changedObject) {
        DirtyTransactions<CacheImpl> dirtyTransaction = DirtyTransactions.createOneDirtyTransaction(transaction, cache);
        return new StateCommandResult<CacheImpl>(context.getStateFactory().createDirtyState(cache, dirtyTransaction, stateContext));
    }

    @Override
    public StateCommandResult<CacheImpl> beforeTransactionComplete(CacheStateMachineContext<CacheImpl> context, Transaction transaction) {
        return StateCommandResult.stateNoChangedResult;
    }

    @Override
    public StateCommandResultWithData<CacheImpl, Boolean> completeTransaction(CacheStateMachineContext<CacheImpl> context, Transaction transaction) {
        log.error("completeTransaction must not be called on " + this);
        return new StateCommandResultWithData<CacheImpl, Boolean>(context.getStateFactory().createEmptyState(cache, stateContext), true);
    }

    @Override
    public StateCommandResult<CacheImpl> commitCache(CacheStateMachineContext<CacheImpl> context, CacheImpl cache) {
        log.error("commitCache must not be called on " + this);
        return StateCommandResult.stateNoChangedResult;
    }

    @Override
    public void discard() {
    }

    @Override
    public void accept(CacheStateMachineContext<CacheImpl> context) {
    }

    @Override
    public StateCommandResult<CacheImpl> dropCache(CacheStateMachineContext<CacheImpl> context) {
        return new StateCommandResult<CacheImpl>(context.getStateFactory().createEmptyState(null, new NonRuntimeCacheContext(null)));
    }

    /**
     * Create empty state for state machine.
     *
     * @return Return empty state.
     */
    public static <CacheImpl extends CacheImplementation> EmptyCacheState<CacheImpl> createEmptyState(CacheImpl cache,
            NonRuntimeCacheContext context) {
        return new EmptyCacheState<CacheImpl>(cache, context);
    }
}