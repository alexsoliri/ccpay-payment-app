package uk.gov.hmcts.payment.api.componenttests;

import com.sun.org.apache.bcel.internal.generic.LUSHR;
import org.joda.time.MutableDateTime;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.payment.api.componenttests.util.PaymentsDataUtil;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.v1.componenttests.ComponentTestBase;

import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Path;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static uk.gov.hmcts.payment.api.model.Fee.feeWith;
import static uk.gov.hmcts.payment.api.model.Payment.paymentWith;

public class CardPaymentServiceTest extends ComponentTestBase {
    private PaymentsDataUtil paymentsDataUtil;


    @Before
    public void setUp() {
        paymentsDataUtil = new PaymentsDataUtil();

        PaymentFeeLink paymentFeeLink = PaymentFeeLink.paymentFeeLinkWith()
            .payments(paymentsDataUtil.getCardPaymentsData())
            .fees(paymentsDataUtil.getFeesData())
            .build();

        paymentFeeLinkRepository.save(paymentFeeLink);

        //pba payments
        paymentFeeLinkRepository.save(PaymentFeeLink.paymentFeeLinkWith()
            .payments(paymentsDataUtil.getCreditAccountPaymentsData())
            .fees(paymentsDataUtil.getFeesData())
            .build());
    }

    @Test
    public void retireveCardPayments_forBetweenDates_WhereProviderIsGovPayTest() throws Exception {
        Date fromDate = new Date();
        MutableDateTime mFromDate = new MutableDateTime(fromDate);
        mFromDate.addDays(-1);
        Date toDate = new Date();
        MutableDateTime mToDate = new MutableDateTime(toDate);
        mToDate.addDays(2);

        List<PaymentFeeLink> result = cardPaymentService.search(mFromDate.toDate(), mToDate.toDate());

        assertNotNull(result);
        result.stream().forEach(g -> {
            assertEquals(g.getPayments().size(), 3);
            g.getPayments().stream().forEach(p -> {
                assertEquals(p.getPaymentMethod().getName(), "card");
            });
        });

    }

}