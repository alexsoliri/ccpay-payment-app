package uk.gov.hmcts.payment.api.componenttests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
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
import uk.gov.hmcts.payment.api.contract.CardPaymentRequest;
import uk.gov.hmcts.payment.api.contract.FeeDto;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;
import uk.gov.hmcts.payment.api.contract.util.Service;
import uk.gov.hmcts.payment.api.external.client.dto.CardDetails;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.PaymentChannel;
import uk.gov.hmcts.payment.api.model.PaymentFee;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.model.PaymentMethod;
import uk.gov.hmcts.payment.api.model.PaymentProvider;
import uk.gov.hmcts.payment.api.model.PaymentStatus;
import uk.gov.hmcts.payment.api.model.StatusHistory;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.ServiceResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.UserResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.CustomResultMatcher;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.RestActions;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;
import static uk.gov.hmcts.payment.api.model.PaymentFeeLink.paymentFeeLinkWith;

@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "componenttest"})
@SpringBootTest(webEnvironment = MOCK)
@Transactional
public class CardPaymentControllerTest extends PaymentsDataUtil {

    private final static String PAYMENT_REFERENCE_REGEX = "^[RC-]{3}(\\w{4}-){3}(\\w{4})";

    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule(9190);

    @Rule
    public WireMockClassRule instanceRule = wireMockRule;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ServiceResolverBackdoor serviceRequestAuthorizer;

    @Autowired
    private UserResolverBackdoor userRequestAuthorizer;

    @Autowired
    private PaymentDbBackdoor db;

    private static final String USER_ID = UserResolverBackdoor.CITIZEN_ID;

    private RestActions restActions;

    @Autowired
    private ObjectMapper objectMapper;

    protected CustomResultMatcher body() {
        return new CustomResultMatcher(objectMapper);
    }

    @Before
    public void setup() {
        MockMvc mvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        this.restActions = new RestActions(mvc, serviceRequestAuthorizer, userRequestAuthorizer, objectMapper);

        restActions
            .withAuthorizedService("divorce")
            .withAuthorizedUser(USER_ID)
            .withUserId(USER_ID)
            .withReturnUrl("https://www.gooooogle.com");

    }

    @Test
    @Transactional
    public void createCardPaymentWithValidInputData_shouldReturnStatusCreatedTest() throws Exception {

        stubFor(post(urlPathMatching("/v1/payments"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody(contentsOf("gov-pay-responses/create-payment-response.json"))));

        stubFor(get(urlPathMatching("/v1/payments/ak8gtvb438drmp59cs7ijppr3i"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(contentsOf("gov-pay-responses/get-payment-response.json"))));


        MvcResult result = restActions
            .withReturnUrl("https://www.google.com")
            .withHeader("service-callback-url", "http://payments.com")
            .post("/card-payments", cardPaymentRequest())
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);

        MvcResult result2 = restActions
            .get("/card-payments/" + paymentDto.getReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentDto paymentsResponse = objectMapper.readValue(result2.getResponse().getContentAsString(), PaymentDto.class);

        assertEquals("http://payments.com", db.findByReference(paymentsResponse.getPaymentGroupReference()).getPayments().get(0).getServiceCallbackUrl());

        assertNotNull(paymentDto);
        assertEquals("Initiated", paymentDto.getStatus());
        assertTrue(paymentDto.getReference().matches(PAYMENT_REFERENCE_REGEX));
    }

    @Test
    public void createCardPaymentWithInvalidInputDataShouldReturnStatusBadRequestTest() throws Exception {
        restActions
            .withReturnUrl("https://www.google.com")
            .post("/card-payments", cardPaymentInvalidRequestJson())
            .andExpect(status().isBadRequest());
    }

    @Test
    public void createCardPayment_withMissingCcdCaseNumberAndCaseReference_shouldReturn422Test() throws Exception {
        CardPaymentRequest cardPaymentRequest = CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(new BigDecimal("200.11"))
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .service(Service.CMC)
            .siteId("siteID")
            .fees(Arrays.asList(FeeDto.feeDtoWith()
                .calculatedAmount(new BigDecimal("200.11"))
                .code("X0001")
                .version("1")
                .build())).build();


        MvcResult result = restActions
            .post("/card-payments", cardPaymentRequest)
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

        assertEquals(result.getResponse().getContentAsString(), "eitherOneRequired: Either ccdCaseNumber or caseReference is required.");
    }

    @Test
    public void retrieveCardPaymentAndMapTheGovPayStatusTest() throws Exception {
        stubFor(get(urlPathMatching("/v1/payments/ia2mv22nl5o880rct0vqfa7k76"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(contentsOf("gov-pay-responses/get-payment-response.json"))));

        Payment payment = Payment.paymentWith()
            .amount(new BigDecimal("11.99"))
            .caseReference("Reference1")
            .ccdCaseNumber("ccdCaseNumber1")
            .description("Description1")
            .serviceType("PROBATE")
            .currency("GBP")
            .siteId("AA01")
            .userId(USER_ID)
            .paymentChannel(PaymentChannel.paymentChannelWith().name("online").build())
            .paymentMethod(PaymentMethod.paymentMethodWith().name("card").build())
            .paymentProvider(PaymentProvider.paymentProviderWith().name("gov pay").build())
            .paymentStatus(PaymentStatus.paymentStatusWith().name("created").build())
            .externalReference("ia2mv22nl5o880rct0vqfa7k76")
            .reference("RC-1519-9028-1909-3475")
            .statusHistories(Arrays.asList(StatusHistory.statusHistoryWith()
                .status("Initiated")
                .externalStatus("created")
                .build()))
            .build();
        PaymentFee fee = PaymentFee.feeWith().calculatedAmount(new BigDecimal("11.99")).version("1").code("X0001").build();

        PaymentFeeLink paymentFeeLink = db.create(paymentFeeLinkWith().paymentReference("2018-15186162001").payments(Arrays.asList(payment)).fees(Arrays.asList(fee)));
        payment.setPaymentLink(paymentFeeLink);

        Payment savedPayment = paymentFeeLink.getPayments().get(0);

        MvcResult result = restActions
            .get("/card-payments/" + savedPayment.getReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);
        assertNotNull(paymentDto);
        assertEquals(paymentDto.getReference(), payment.getReference());
        assertEquals(paymentDto.getExternalReference(), payment.getExternalReference());
        assertEquals("Success", paymentDto.getStatus());
    }

    @Test
    public void retrieveCardPaymentStatuses_byPaymentReferenceTest() throws Exception {
        stubFor(get(urlPathMatching("/v1/payments/e2kkddts5215h9qqoeuth5c0v3"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(contentsOf("gov-pay-responses/get-payment-status-response.json"))));

        StatusHistory statusHistory = StatusHistory.statusHistoryWith().status("Initiated").externalStatus("created").build();
        Payment payment = Payment.paymentWith()
            .amount(new BigDecimal("499.99"))
            .caseReference("Reference1")
            .ccdCaseNumber("ccdCaseNumber1")
            .description("Test payments statuses")
            .serviceType("PROBATE")
            .currency("GBP")
            .siteId("AA01")
            .userId(USER_ID)
            .paymentChannel(PaymentChannel.paymentChannelWith().name("online").build())
            .paymentMethod(PaymentMethod.paymentMethodWith().name("card").build())
            .paymentProvider(PaymentProvider.paymentProviderWith().name("gov pay").build())
            .paymentStatus(PaymentStatus.paymentStatusWith().name("created").build())
            .externalReference("e2kkddts5215h9qqoeuth5c0v3")
            .reference("RC-1519-9028-2432-9115")
            .statusHistories(Arrays.asList(statusHistory))
            .build();
        PaymentFee fee = PaymentFee.feeWith().calculatedAmount(new BigDecimal("499.99")).version("1").code("X0123").build();

        PaymentFeeLink paymentFeeLink = db.create(paymentFeeLinkWith().paymentReference("2018-15186162002").payments(Arrays.asList(payment)).fees(Arrays.asList(fee)));
        payment.setPaymentLink(paymentFeeLink);

        Payment savedPayment = paymentFeeLink.getPayments().get(0);

        MvcResult result = restActions
            .get("/card-payments/" + savedPayment.getReference() + "/statuses")
            .andExpect(status().isOk())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);
        assertNotNull(paymentDto);
        assertEquals(paymentDto.getReference(), savedPayment.getReference());
        assertEquals(paymentDto.getAmount(), new BigDecimal("499.99"));
        assertEquals(paymentDto.getStatusHistories().size(), 1);
        paymentDto.getStatusHistories().stream().forEach(h -> {
            assertEquals(h.getStatus(), "Success");
            assertEquals(h.getExternalStatus(), "success");
        });
    }

    @Test
    public void retrieveCardDetails_byPaymentReferenceTest() throws Exception {
        stubFor(get(urlPathMatching("/v1/payments/ah0288ctvgqgcmbatdp1viu61j"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(contentsOf("gov-pay-responses/get-card-details-response.json"))));

        StatusHistory statusHistory = StatusHistory.statusHistoryWith().status("Success").externalStatus("success").build();
        Payment payment = Payment.paymentWith()
            .amount(new BigDecimal("121.11"))
            .caseReference("Reference")
            .ccdCaseNumber("ccdCaseNumber")
            .description("Test payments statuses")
            .serviceType("PROBATE")
            .currency("GBP")
            .siteId("AA011")
            .userId(USER_ID)
            .paymentChannel(PaymentChannel.paymentChannelWith().name("online").build())
            .paymentMethod(PaymentMethod.paymentMethodWith().name("card").build())
            .paymentProvider(PaymentProvider.paymentProviderWith().name("gov pay").build())
            .paymentStatus(PaymentStatus.paymentStatusWith().name("success").build())
            .externalReference("ah0288ctvgqgcmbatdp1viu61j")
            .reference("RC-1529-9159-9129-3183")
            .statusHistories(Arrays.asList(statusHistory))
            .build();
        PaymentFee fee = PaymentFee.feeWith().calculatedAmount(new BigDecimal("121.11")).version("1").code("FEE0123").build();

        PaymentFeeLink paymentFeeLink = db.create(paymentFeeLinkWith().paymentReference("2018-15186161221").payments(Arrays.asList(payment)).fees(Arrays.asList(fee)));
        payment.setPaymentLink(paymentFeeLink);

        Payment savedPayment = paymentFeeLink.getPayments().get(0);

        MvcResult result = restActions
            .get("/card-payments/RC-1529-9159-9129-3183/details")
            .andExpect(status().isOk())
            .andReturn();

        CardDetails cardDetails = objectMapper.readValue(result.getResponse().getContentAsByteArray(), CardDetails.class);
        assertEquals(cardDetails.getExpiryDate(), "11/18");
        assertEquals(cardDetails.getCardBrand(), "Visa");
        assertEquals(cardDetails.getCardholderName(), "TEST CARD");
        assertEquals(cardDetails.getLastDigitsCardNumber(), "1111");

    }

    @Test
    public void retrieveCardDetails_shouldReturn404_ifDetailsNotFoundTest() throws Exception {
        stubFor(get(urlPathMatching("/v1/payments/ia2mv22nl5o880rct0vqfa7k76"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(contentsOf("gov-pay-responses/get-payment-error-response.json"))));

        StatusHistory statusHistory = StatusHistory.statusHistoryWith().status("Failed").externalStatus("error").build();
        Payment payment = Payment.paymentWith()
            .amount(new BigDecimal("22.89"))
            .caseReference("Reference")
            .ccdCaseNumber("ccdCaseNumber")
            .description("Test payments statuses")
            .serviceType("PROBATE")
            .currency("GBP")
            .siteId("AA001")
            .userId(USER_ID)
            .paymentChannel(PaymentChannel.paymentChannelWith().name("online").build())
            .paymentMethod(PaymentMethod.paymentMethodWith().name("card").build())
            .paymentProvider(PaymentProvider.paymentProviderWith().name("gov pay").build())
            .paymentStatus(PaymentStatus.paymentStatusWith().name("error").build())
            .externalReference("ia2mv22nl5o880rct0vqfa7k76")
            .reference("RC-1518-9429-1432-7825")
            .statusHistories(Arrays.asList(statusHistory))
            .build();
        PaymentFee fee = PaymentFee.feeWith().calculatedAmount(new BigDecimal("22.89")).version("1").code("FEE0112").build();

        PaymentFeeLink paymentFeeLink = db.create(paymentFeeLinkWith().paymentReference("2018-15186161221").payments(Arrays.asList(payment)).fees(Arrays.asList(fee)));
        payment.setPaymentLink(paymentFeeLink);

        Payment savedPayment = paymentFeeLink.getPayments().get(0);

        restActions
            .get("/card-payments/RC-1518-9429-1432-7825/details")
            .andExpect(status().isNotFound());
    }

    @Test
    public void createPaymentWithChannelTelephonyAndProviderPciPal() throws Exception {
        BigDecimal amount = new BigDecimal("100");
        CardPaymentRequest cardPaymentRequest = CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(amount)
            .description("description")
            .caseReference("telRefNumber")
            .ccdCaseNumber("1234")
            .service(Service.PROBATE)
            .currency(CurrencyCode.GBP)
            .provider("pci pal")
            .channel("telephony")
            .siteId("siteId")
            .fees(Collections.singletonList(FeeDto.feeDtoWith()
                .code("feeCode")
                .version("1")
                .calculatedAmount(new BigDecimal("100.1"))
                .build()))
            .build();

        MvcResult result = restActions
            .withReturnUrl("https://www.google.com")
            .withHeader("service-callback-url", "http://payments.com")
            .post("/card-payments", cardPaymentRequest)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);

        MvcResult result2 = restActions
            .get("/card-payments/" + paymentDto.getReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentDto paymentsResponse = objectMapper.readValue(result2.getResponse().getContentAsString(), PaymentDto.class);

        assertEquals("http://payments.com", db.findByReference(paymentsResponse.getPaymentGroupReference()).getPayments().get(0).getServiceCallbackUrl());

        assertNotNull(paymentDto);
        assertEquals("Initiated", paymentDto.getStatus());
        assertTrue(paymentDto.getReference().matches(PAYMENT_REFERENCE_REGEX));
        assertEquals("Amount saved in remissionDbBackdoor is equal to the on inside the request", amount, paymentsResponse.getAmount());
    }

    @Test
    public void retrieveCardPayment_withNonExistingReferenceTest() throws Exception {
        restActions
            .get("/card-payments/" + "RC-1518-9576-1498-8035")
            .andExpect(status().isNotFound());
    }

    @Test
    public void retrieveCardPayment_andMapGovPayErrorStatusTest() throws Exception {
        stubFor(get(urlPathMatching("/v1/payments/ia2mv22nl5o880rct0vqfa7k76"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(contentsOf("gov-pay-responses/get-payment-error-response.json"))));

        StatusHistory statusHistory = StatusHistory.statusHistoryWith().status("Initiated").externalStatus("created").build();
        Payment payment = Payment.paymentWith()
            .amount(new BigDecimal("22.89"))
            .caseReference("Reference")
            .ccdCaseNumber("ccdCaseNumber")
            .description("Description")
            .serviceType("PROBATE")
            .currency("GBP")
            .siteId("AA00")
            .userId(USER_ID)
            .paymentChannel(PaymentChannel.paymentChannelWith().name("online").build())
            .paymentMethod(PaymentMethod.paymentMethodWith().name("card").build())
            .paymentProvider(PaymentProvider.paymentProviderWith().name("gov pay").build())
            .paymentStatus(PaymentStatus.paymentStatusWith().name("created").build())
            .externalReference("ia2mv22nl5o880rct0vqfa7k76")
            .reference("RC-1518-9429-1432-7825")
            .statusHistories(Arrays.asList(statusHistory))
            .build();
        PaymentFee fee = PaymentFee.feeWith().calculatedAmount(new BigDecimal("22.89")).version("1").code("X0011").build();

        PaymentFeeLink paymentFeeLink = db.create(paymentFeeLinkWith().paymentReference("2018-15186162003").payments(Arrays.asList(payment)).fees(Arrays.asList(fee)));
        payment.setPaymentLink(paymentFeeLink);

        Payment savedPayment = paymentFeeLink.getPayments().get(0);

        MvcResult result = restActions
            .get("/card-payments/" + savedPayment.getReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);
        assertNotNull(paymentDto);
        assertEquals(paymentDto.getReference(), payment.getReference());
        assertEquals(paymentDto.getStatus(), "Failed");

        PaymentFeeLink savedPaymentGroup = db.findByReference("2018-15186162003");
        Payment dbPayment = savedPaymentGroup.getPayments().get(0);
        assertEquals(dbPayment.getReference(), "RC-1518-9429-1432-7825");
        dbPayment.getStatusHistories().stream().forEach(h -> {
            assertEquals(h.getErrorCode(), "P0200");
            assertEquals(h.getMessage(), "Payment not found");
        });
    }

    @Test
    public void createCardPaymentForCMC_withCaseReferenceOnly_shouldReturnStatusCreatedTest() throws Exception {

        stubFor(post(urlPathMatching("/v1/payments"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody(contentsOf("gov-pay-responses/create-payment-response.json"))));


        MvcResult result = restActions
            .withReturnUrl("https://www.google.com")
            .post("/card-payments", cardPaymentRequestWithCaseReference())
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);
        assertNotNull(paymentDto);
        assertEquals("Initiated", paymentDto.getStatus());
        assertTrue(paymentDto.getReference().matches(PAYMENT_REFERENCE_REGEX));
    }

    private CardPaymentRequest cardPaymentRequest() throws Exception {
        return objectMapper.readValue(requestJson().getBytes(), CardPaymentRequest.class);
    }

    private CardPaymentRequest cardPaymentRequestWithCaseReference() throws Exception {
        return objectMapper.readValue(jsonWithCaseReference().getBytes(), CardPaymentRequest.class);
    }


    public String jsonWithCaseReference() {
        return "{\n" +
            "  \"amount\": 101.89,\n" +
            "  \"description\": \"New passport application\",\n" +
            "  \"case_reference\": \"caseReference\",\n" +
            "  \"service\": \"CMC\",\n" +
            "  \"currency\": \"GBP\",\n" +
            "  \"return_url\": \"https://www.gooooogle.com\",\n" +
            "  \"site_id\": \"siteId\",\n" +
            "  \"fees\": [\n" +
            "    {\n" +
            "      \"calculated_amount\": 101.89,\n" +
            "      \"code\": \"X0101\",\n" +
            "      \"version\": \"1\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";
    }

    private String cardPaymentInvalidRequestJson() {
        return "{\n" +
            "  \"amount\": 101.89,\n" +
            "  \"description\": \"New passport application\",\n" +
            "  \"ccd_case_number\": \"CCD101\",\n" +
            "  \"case_reference\": \"12345\",\n" +
            "  \"service\": \"PROBATE\",\n" +
            "  \"currency\": \"GBP\",\n" +
            "  \"return_url\": \"https://www.gooooogle.com\",\n" +
            "  \"site_id\": \"AA101\",\n" +
            "  \"fees\": [\n" +
            "    {\n" +
            "      \"calculated_amount\": 101.89,\n" +
            "      \"code\": \"X0101\",\n" +
            "      \"version\": \"1\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";
    }
}
