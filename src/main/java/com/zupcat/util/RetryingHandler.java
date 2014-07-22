package com.zupcat.util;

import com.google.appengine.api.datastore.*;
import com.zupcat.exception.NoMoreRetriesException;
import com.zupcat.service.SimpleDatastoreServiceFactory;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @see http://code.google.com/appengine/articles/handling_datastore_errors.html
 * @see http://code.google.com/appengine/docs/java/datastore/transactions.html
 */
public final class RetryingHandler implements Serializable {

    private static final long serialVersionUID = 472842924253314234L;
    private static final Logger log = Logger.getLogger(RetryingHandler.class.getName());

    private static final int MAX_RETRIES = 6;
    private static final int WAIT_MS = 800;


    public void tryDSRemove(final Collection<Key> entityKeys) {
        final boolean loggingActivated = SimpleDatastoreServiceFactory.getSimpleDatastoreService().isDatastoreCallsLoggingActivated();

        tryClosure(new Closure() {
            public void execute(final DatastoreService datastore) {

                if (loggingActivated) {
                    log.log(Level.SEVERE, "PERF - tryDSRemoveMultiple", new Exception());
                }

                datastore.delete(entityKeys);
            }
        });
    }


    public void tryDSRemove(final Key entityKey) {
        final boolean loggingActivated = SimpleDatastoreServiceFactory.getSimpleDatastoreService().isDatastoreCallsLoggingActivated();

        tryClosure(new Closure() {
            public void execute(final DatastoreService datastore) {
                if (loggingActivated) {
                    log.log(Level.SEVERE, "PERF - tryDSRemove", new Exception());
                }

                datastore.delete(entityKey);
            }
        });
    }


    public void tryDSRemoveAsync(final Key key) {
        final boolean loggingActivated = SimpleDatastoreServiceFactory.getSimpleDatastoreService().isDatastoreCallsLoggingActivated();

        tryClosureAsync(new AsyncClosure<Void>() {

            public Future<Void> execute(final AsyncDatastoreService datastore) throws ExecutionException, InterruptedException {
                if (loggingActivated) {
                    log.log(Level.SEVERE, "PERF - tryDSRemoveAsync", new Exception());
                }

                return datastore.delete(key);
            }
        });
    }


    public void tryDSRemoveAsync(final Collection<Key> entityKeys) {
        final boolean loggingActivated = SimpleDatastoreServiceFactory.getSimpleDatastoreService().isDatastoreCallsLoggingActivated();

        tryClosureAsync(new AsyncClosure<Void>() {

            public Future<Void> execute(final AsyncDatastoreService datastore) throws ExecutionException, InterruptedException {
                if (loggingActivated) {
                    log.log(Level.SEVERE, "PERF - tryDSRemoveAsyncMultiple", new Exception());
                }

                return datastore.delete(entityKeys);
            }
        });
    }


    public void tryDSPutMultipleAsync(final Iterable<Entity> entities) {
        final boolean loggingActivated = SimpleDatastoreServiceFactory.getSimpleDatastoreService().isDatastoreCallsLoggingActivated();

        tryClosureAsync(new AsyncClosure<List<Key>>() {

            public Future<List<Key>> execute(final AsyncDatastoreService datastore) throws ExecutionException, InterruptedException {
                if (loggingActivated) {
                    log.log(Level.SEVERE, "PERF - tryDSPutMultipleAsync", new Exception());
                }

                final Future<List<Key>> listFuture = datastore.put(entities);

                listFuture.get();

                return listFuture;
            }
        });
    }


    public Future<Map<Key, Entity>> tryDSGetMultipleAsync(final Collection<Key> keys) {
        final boolean loggingActivated = SimpleDatastoreServiceFactory.getSimpleDatastoreService().isDatastoreCallsLoggingActivated();

        return tryClosureAsync(new AsyncClosure<Map<Key, Entity>>() {

            public Future<Map<Key, Entity>> execute(final AsyncDatastoreService datastore) throws ExecutionException, InterruptedException {
                if (loggingActivated) {
                    log.log(Level.SEVERE, "PERF - tryDSGetMultipleAsync", new Exception());
                }
                final Future<Map<Key, Entity>> listFuture = datastore.get(keys);

                listFuture.get();

                return listFuture;
            }
        });
    }


    public void tryDSPutMultiple(final Iterable<Entity> entities) {
        final boolean loggingActivated = SimpleDatastoreServiceFactory.getSimpleDatastoreService().isDatastoreCallsLoggingActivated();

        tryClosure(new Closure() {
            public void execute(final DatastoreService datastore) {
                if (loggingActivated) {
                    log.log(Level.SEVERE, "PERF - tryDSPutMultiple", new Exception());
                }
                datastore.put(entities);
            }
        });
    }


    public void tryDSPut(final Entity entity) {
        final boolean loggingActivated = SimpleDatastoreServiceFactory.getSimpleDatastoreService().isDatastoreCallsLoggingActivated();

        tryClosure(new Closure() {
            public void execute(final DatastoreService datastore) {

                if (loggingActivated) {
                    log.log(Level.SEVERE, "PERF - tryDSPut", new Exception());
                }

                datastore.put(entity);
            }
        });
    }


    public void tryDSPutAsync(final Entity entity) {
        final boolean loggingActivated = SimpleDatastoreServiceFactory.getSimpleDatastoreService().isDatastoreCallsLoggingActivated();

        tryClosureAsync(new AsyncClosure<Key>() {
            public Future<Key> execute(final AsyncDatastoreService datastore) throws ExecutionException, InterruptedException {
                if (loggingActivated) {
                    log.log(Level.SEVERE, "PERF - tryDSPutAsync", new Exception());
                }
                return datastore.put(entity);
            }
        });
    }

    private void tryClosure(final Closure closure) {
        final ValuesContainer values = new ValuesContainer();
        final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        while (true) {
            try {
                closure.execute(datastore);
                break;
            } catch (final DatastoreTimeoutException dte) {
                handleError(values, dte, true);
            } catch (final Exception exception) {
                handleError(values, exception, false);
            }
        }
    }


    private <T> Future<T> tryClosureAsync(final AsyncClosure<T> closure) {
        final ValuesContainer values = new ValuesContainer();
        final AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
        Future<T> result;

        while (true) {
            try {
                result = closure.execute(datastore);
                break;
            } catch (final InterruptedException | DatastoreTimeoutException ie) {
                handleError(values, ie, true);
            } catch (final Exception exception) {
                handleError(values, exception, false);
            }
        }
        return result;
    }


    private void handleError(final ValuesContainer values, final Exception exception, final boolean isTimeoutException) {
        values.retry = values.retry - 1;

        if (values.retry == 0) {
            log.log(Level.SEVERE, "PERF - No more tries for datastore access: " + exception.getMessage(), exception);
            throw new NoMoreRetriesException(exception);
        }

        sleep(values.retryWait);

        if (isTimeoutException) {
            values.retryWait = values.retryWait * 3;
        }
    }


    public static void sleep(final int millis) {
        try {
            Thread.sleep(millis);

        } catch (final InterruptedException ie) {
            // nothing to do
        }
    }


    private interface Closure {

        void execute(final DatastoreService datastore);
    }


    private interface AsyncClosure<T> {

        Future<T> execute(final AsyncDatastoreService datastore) throws ExecutionException, InterruptedException;
    }


    public static final class ValuesContainer implements Serializable {

        private static final long serialVersionUID = 472142124257311224L;

        public int retry = MAX_RETRIES;
        public int retryWait = WAIT_MS;
    }
}
