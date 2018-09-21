package uk.gov.hmcts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.payment.api.logging.Markers;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class PaymentServletContextListener implements ServletContextListener {
    private static final Logger LOG = LoggerFactory.getLogger(PaymentServletContextListener.class);

    @Override
    public void contextInitialized(ServletContextEvent sce) {}

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        LOG.error(Markers.fatal, "Application shutting down");
    }
}