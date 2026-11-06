package com.farmatodo.challenge.product.application;

import com.farmatodo.challenge.product.ProductsSearched;
import com.farmatodo.challenge.product.domain.ProductPorts.SearchLog;
import com.farmatodo.challenge.product.domain.ProductPorts.SearchLogRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Stores searches asynchronously (challenge requirement).
 * {@code @ApplicationModuleListener} = {@code @Async} + {@code @Transactional(REQUIRES_NEW)} +
 * {@code @TransactionalEventListener(AFTER_COMMIT)}: it runs only if the search transaction committed, on another
 * thread, in its own transaction. If it fails, the publication stays incomplete in event_publication and is
 * re-submitted on restart.
 */
@Component
class SearchLogListener {

    private static final Logger log = LoggerFactory.getLogger(SearchLogListener.class);

    private final SearchLogRepository searchLogs;

    SearchLogListener(SearchLogRepository searchLogs) {
        this.searchLogs = searchLogs;
    }

    @ApplicationModuleListener
    void on(ProductsSearched event) {
        searchLogs.save(new SearchLog(event.query(), event.resultsCount(), event.customerId(), event.transactionId(),
                event.occurredAt()));
        log.debug("Search stored: '{}' ({} results)", event.query(), event.resultsCount());
    }
}
