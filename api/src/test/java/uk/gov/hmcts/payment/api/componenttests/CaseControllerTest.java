package uk.gov.hmcts.payment.api.componenttests;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.payment.api.componenttests.util.PaymentsDataUtil;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.api.contract.PaymentsResponse;

import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.ServiceResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.UserResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.RestActions;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "componenttest"})
@SpringBootTest(webEnvironment = MOCK)
@Transactional
public class CaseControllerTest extends PaymentsDataUtil {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    protected ServiceResolverBackdoor serviceRequestAuthorizer;

    @Autowired
    protected UserResolverBackdoor userRequestAuthorizer;

    private static final String USER_ID = UserResolverBackdoor.CASEWORKER_ID;

    @Autowired
    private ObjectMapper objectMapper;

    RestActions restActions;

    @Before
    public void setup() {
        MockMvc mvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        this.restActions = new RestActions(mvc, serviceRequestAuthorizer, userRequestAuthorizer, objectMapper);

        restActions
            .withAuthorizedService("divorce")
            .withReturnUrl("https://www.gooooogle.com");
    }

    @Test
    @Transactional
    public void searchAllPaymentsWithCcdCaseNumberShouldReturnRequiredFieldsForVisualComponent() throws Exception {

        populateCardPaymentToDb("1");

        MvcResult result = restActions
            .withAuthorizedUser(USER_ID)
            .withUserId(USER_ID)
            .get("/cases/ccdCaseNumber1/payments")
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse payments = objectMapper.readValue(result.getResponse().getContentAsByteArray(), new TypeReference<PaymentsResponse>(){});

        assertThat(payments.getPayments().size()).isEqualTo(1);

        PaymentDto payment = payments.getPayments().get(0);

        assertThat(payment.getCcdCaseNumber()).isEqualTo("ccdCaseNumber1");

        assertThat(payment.getReference()).isNotBlank();
        assertThat(payment.getAmount()).isPositive();
        assertThat(payment.getDateCreated()).isNotNull();
        assertThat(payment.getCustomerReference()).isNotBlank();

        Assert.assertThat(payment.getStatusHistories(), hasItem(hasProperty("status", is("Initiated"))));
        Assert.assertThat(payment.getStatusHistories(), hasItem(hasProperty("errorCode", nullValue())));
    }

    @Test
    @Transactional
    public void shouldReturnStatusHistoryWithErrorCodeForSearchByCaseReference() throws Exception {
        String number = "1";
        StatusHistory statusHistory = StatusHistory.statusHistoryWith().status("Failed").externalStatus("failed")
            .errorCode("P0200")
            .message("Payment not found")
            .build();

        Payment payment = Payment.paymentWith()
            .amount(new BigDecimal("99.99"))
            .caseReference("Reference" + number)
            .ccdCaseNumber("ccdCaseNumber" + number)
            .description("Test payments statuses for " + number)
            .serviceType("PROBATE")
            .currency("GBP")
            .siteId("AA0" + number)
            .userId(USER_ID)
            .paymentChannel(PaymentChannel.paymentChannelWith().name("online").build())
            .paymentMethod(PaymentMethod.paymentMethodWith().name("card").build())
            .paymentProvider(PaymentProvider.paymentProviderWith().name("gov pay").build())
            .paymentStatus(PaymentStatus.paymentStatusWith().name("failed").build())
            .externalReference("e2kkddts5215h9qqoeuth5c0v" + number)
            .reference("RC-1519-9028-2432-000" + number)
            .statusHistories(Arrays.asList(statusHistory))
            .build();

        populateCardPaymentToDbWith(payment, number);

        MvcResult result = restActions
            .withAuthorizedUser(USER_ID)
            .withUserId(USER_ID)
            .get("/cases/ccdCaseNumber1/payments")
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse payments = objectMapper.readValue(result.getResponse().getContentAsByteArray(), new TypeReference<PaymentsResponse>(){});

        assertThat(payments.getPayments().size()).isEqualTo(1);

        PaymentDto paymentDto = payments.getPayments().get(0);

        assertThat(paymentDto.getCcdCaseNumber()).isEqualTo("ccdCaseNumber1");

        Assert.assertThat(paymentDto.getStatusHistories(), hasItem(hasProperty("status", is("Failed"))));
        Assert.assertThat(paymentDto.getStatusHistories(), hasItem(hasProperty("errorCode",is("P0200"))));
        Assert.assertThat(paymentDto.getStatusHistories(), hasItem(hasProperty("errorMessage",is("Payment not found"))));
    }


    @Test
    @Transactional
    public void searchAllPaymentsWithCcdCaseNumberAndCitizenCredentialsFails() throws Exception {
        populateCardPaymentToDb("1");
        populateCreditAccountPaymentToDb("1");

        restActions
            .withAuthorizedUser(UserResolverBackdoor.CITIZEN_ID)
            .withUserId(UserResolverBackdoor.CITIZEN_ID)
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        assertThat(restActions
            .withAuthorizedUser(UserResolverBackdoor.CITIZEN_ID)
            .withUserId(UserResolverBackdoor.CITIZEN_ID)
            .get("/cases/ccdCaseNumber1/payments")
            .andExpect(status().isForbidden())
        .andReturn()).isNotNull();

    }

    @Test
    @Transactional
    public void searchAllPaymentsWithWrongCcdCaseNumberShouldReturn404() throws Exception {
        populateCardPaymentToDb("1");
        populateCreditAccountPaymentToDb("1");

        restActions
            .withAuthorizedUser(USER_ID)
            .withUserId(USER_ID)
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        assertThat(restActions
            .withAuthorizedUser(USER_ID)
            .withUserId(USER_ID)
            .get("/cases/ccdCaseNumber2/payments")
            .andExpect(status().isNotFound())
        .andReturn()).isNotNull();
    }

}
