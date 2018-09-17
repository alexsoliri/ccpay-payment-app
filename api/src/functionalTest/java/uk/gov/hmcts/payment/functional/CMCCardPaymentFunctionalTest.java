package uk.gov.hmcts.payment.functional;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.functional.dsl.PaymentsTestDsl;

import java.math.BigDecimal;

import static org.junit.Assert.*;

public class CMCCardPaymentFunctionalTest extends IntegrationTestBase {

    @Autowired(required = true)
    private PaymentsTestDsl dsl;

    @Test
    public void createCMCCardPaymentTestShouldReturn201Success() {
        dsl.given().userId(paymentCmcTestUser, paymentCmcTestUserId, paymentCmcTestPassword, cmcUserGroup).serviceId(cmcServiceName, cmcSecret).returnUrl("https://www.google.com")
            .when().createCardPayment(getCardPaymentRequest())
            .then().created(paymentDto -> {
                assertNotNull(paymentDto.getReference());
                assertEquals("payment status is properly set", "Initiated", paymentDto.getStatus());
        });

    }

    @Test
    public void retrieveCMCCardPaymentTestShouldReturn200Success() {
        final String[] reference = new String[1];

        // create card payment
        dsl.given().userId(paymentCmcTestUser, paymentCmcTestUserId, paymentCmcTestPassword, cmcUserGroup).serviceId(cmcServiceName, cmcSecret).returnUrl("https://www.google.com")
            .when().createCardPayment(getCardPaymentRequest())
            .then().created(savedPayment -> {
                reference[0] = savedPayment.getReference();

                assertNotNull(savedPayment.getReference());
                assertEquals("payment status is properly set", "Initiated", savedPayment.getStatus());
        });


        // retrieve card payment
        PaymentDto paymentDto = dsl.given().userId(paymentCmcTestUser, paymentCmcTestUserId, paymentCmcTestPassword, cmcUserGroup).serviceId(cmcServiceName, cmcSecret)
            .when().getCardPayment(reference[0])
            .then().get();

        assertNotNull(paymentDto);
        assertEquals(paymentDto.getAmount(), new BigDecimal("123.11"));
        assertEquals(paymentDto.getDescription(), "A functional test card payment");
        assertEquals(paymentDto.getReference(), reference[0]);
        assertEquals(paymentDto.getExternalProvider(), "gov pay");
        assertEquals(paymentDto.getServiceName(), "Civil Money Claims");
        assertEquals(paymentDto.getStatus(), "Initiated");
        paymentDto.getFees().stream().forEach(f -> {
            assertEquals(f.getCode(), "FEE0123");
            assertEquals(f.getVersion(), "1");
            assertEquals(f.getCalculatedAmount(), new BigDecimal("123.11"));
        });

    }

    private String getCardPaymentRequest() {
        JSONObject payment = new JSONObject();

        try {
            payment.put("amount", 123.11);
            payment.put("description", "A functional test card payment");
            payment.put("case_reference", "REF_123");
            payment.put("service", "CMC");
            payment.put("currency", "GBP");
            payment.put("site_id", "AA123");

            JSONArray fees = new JSONArray();
            JSONObject fee = new JSONObject();
            fee.put("calculated_amount", 123.11);
            fee.put("code", "FEE0123");
            fee.put("reference", "REF_123");
            fee.put("version", "1");
            fees.put(fee);

            payment.put("fees", fees);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return payment.toString();
    }

}