package uk.gov.justice.payment.api.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.After;
import org.junit.ClassRule;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.payment.api.KeyConfig;
import uk.gov.justice.payment.api.PaymentController;
import uk.gov.justice.payment.api.services.PaymentService;

import java.util.Map;

import static org.mockito.Mockito.when;

/**
 * Created by zeeshan on 29/09/2016.
 */
public class AbstractPaymentTest {

    public static final int PORT = 9190;
    public static final String URL = "http://localhost:" + PORT + "/payments";

    @ClassRule
    public static WireMockRule wireMockRule = new WireMockRule(PORT);

    PaymentController paymentController = new PaymentController();
    ObjectMapper mapper = new ObjectMapper();
    RestTemplate restTemplate = new RestTemplate();
    ClassLoader classLoader = getClass().getClassLoader();

    @Mock
    PaymentService paymentService;

    @Mock
    KeyConfig keyConfig;
    @Mock
    Map<String, String> keys;
    @Mock
    HttpHeaders headers;

    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(keyConfig.getKey()).thenReturn(keys);
        when(keys.get(null)).thenReturn("someString");

        ReflectionTestUtils.setField(paymentController, "keyConfig", keyConfig);
        ReflectionTestUtils.setField(paymentController, "headers", headers);


    }

    @After
    public void cleanUp() {
        //wireMockRule.stop();
    }
}
