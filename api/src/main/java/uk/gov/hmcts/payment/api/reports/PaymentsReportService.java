package uk.gov.hmcts.payment.api.reports;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.fees2.register.api.contract.FeeVersionDto;
import uk.gov.hmcts.payment.api.contract.FeeDto;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.api.dto.mapper.CardPaymentDtoMapper;
import uk.gov.hmcts.payment.api.email.CardPaymentReconciliationReportEmail;
import uk.gov.hmcts.payment.api.email.CreditAccountReconciliationReportEmail;
import uk.gov.hmcts.payment.api.email.Email;
import uk.gov.hmcts.payment.api.email.EmailService;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.service.CardPaymentService;
import uk.gov.hmcts.payment.api.util.PaymentMethodType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static org.slf4j.LoggerFactory.getLogger;
import static uk.gov.hmcts.payment.api.email.EmailAttachment.csv;

@org.springframework.stereotype.Service
@Transactional
public class PaymentsReportService {

    private static final Logger LOG = getLogger(PaymentsReportService.class);

    private static final String BYTE_ARRAY_OUTPUT_STREAM_NEWLINE = "\r\n";

    private static final String CARD_PAYMENTS_CSV_FILE_PREFIX = "hmcts_card_payments_";

    private static final String CREDIT_ACCOUNT_PAYMENTS_CSV_FILE_PREFIX = "hmcts_credit_account_payments_";

    private static final String PAYMENTS_CSV_FILE_EXTENSION = ".csv";

    private static final Charset utf8 = Charset.forName("UTF-8");

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");

    private static final String CARD_PAYMENTS_HEADER = "Service,Payment Group reference,Payment reference," +
        "CCD reference,Case reference,Payment created date,Payment status updated date,Payment status," +
        "Payment channel,Payment method,Payment amount,Site id,Fee code,Version,Calculated amount,Memo line,NAC," +
        "Fee volume";

    private static final String CREDIT_ACCOUNT_PAYMENTS_HEADER = "Service,Payment Group reference,Payment reference," +
        "CCD reference,Case reference,Organisation name,Customer internal reference,PBA Number,Payment created date," +
        "Payment status updated date,Payment status,Payment channel,Payment method,Payment amount,Site id,Fee code," +
        "Version,Calculated amount,Memo line,NAC,Fee volume";

    private CardPaymentService<PaymentFeeLink, String> cardPaymentService;

    private CardPaymentDtoMapper cardPaymentDtoMapper;

    private EmailService emailService;
    private FeesService feesService;

    private CardPaymentReconciliationReportEmail cardPaymentReconciliationReportEmail;
    private CreditAccountReconciliationReportEmail creditAccountReconciliationReportEmail;

    @Autowired
    public PaymentsReportService(@Qualifier("loggingCardPaymentService") CardPaymentService<PaymentFeeLink, String> cardPaymentService, CardPaymentDtoMapper cardPaymentDtoMapper,
                                 EmailService emailService, FeesService feesService,
                                 CardPaymentReconciliationReportEmail cardPaymentReconciliationReportEmail,
                                 CreditAccountReconciliationReportEmail creditAccountReconciliationReportEmail1) {
        this.cardPaymentService = cardPaymentService;
        this.cardPaymentDtoMapper = cardPaymentDtoMapper;
        this.emailService = emailService;
        this.feesService = feesService;
        this.cardPaymentReconciliationReportEmail = cardPaymentReconciliationReportEmail;
        this.creditAccountReconciliationReportEmail = creditAccountReconciliationReportEmail1;
    }

    public void generateCardPaymentsCsvAndSendEmail(Date startDate, Date endDate, String serviceName) {
        LOG.info("CardPaymentsCSVReport -  Start of csv report for Card Payments of service:{}", serviceName);

        feesService.dailyRefreshOfFeesData();
        List<PaymentDto> cardPaymentsCsvData = findPaymentsBy(startDate, endDate, PaymentMethodType.CARD.getType(), serviceName, null);

        String paymentsCsvFileName = CARD_PAYMENTS_CSV_FILE_PREFIX + LocalDateTime.now().format(formatter) + PAYMENTS_CSV_FILE_EXTENSION;
        generateCsvAndSendEmail(cardPaymentsCsvData, paymentsCsvFileName, CARD_PAYMENTS_HEADER, cardPaymentReconciliationReportEmail);
        LOG.info("CardPaymentsCSVReport -  End of csv report for Card Payments of service:{}", serviceName);
    }

    public void generateCreditAccountPaymentsCsvAndSendEmail(Date startDate, Date endDate, String serviceName) {
        LOG.info("CreditAccountPaymentsCSVReport -  Start of csv report for PBA Payments of service:{}", serviceName);

        feesService.dailyRefreshOfFeesData();
        List<PaymentDto> creditAccountPaymentsCsvData = findPaymentsBy(startDate, endDate, PaymentMethodType.PBA.getType(), serviceName, null);

        String paymentsCsvFileName = CREDIT_ACCOUNT_PAYMENTS_CSV_FILE_PREFIX + LocalDateTime.now().format(formatter) + PAYMENTS_CSV_FILE_EXTENSION;
        generateCsvAndSendEmail(creditAccountPaymentsCsvData, paymentsCsvFileName, CREDIT_ACCOUNT_PAYMENTS_HEADER, creditAccountReconciliationReportEmail);
        LOG.info("CreditAccountPaymentsCSVReport -  End of csv report job for PBA Payments of service:{}", serviceName);
    }

    private List<PaymentDto> findPaymentsBy(Date startDate, Date endDate, String paymentMethod,
                                            String serviceName, String ccdCaseNumber) {
        List<PaymentDto> paymentDtos = cardPaymentService
            .search(startDate, endDate, paymentMethod, serviceName, ccdCaseNumber)
            .stream()
            .map(cardPaymentDtoMapper::toReconciliationResponseDto)
            .collect(Collectors.toList());
        return enrichWithFeeData(paymentDtos);
    }

    private void generateCsvAndSendEmail(List<PaymentDto> payments, String paymentsCsvFileName, String header, Email mail) {

        byte[] paymentsByteArray = createPaymentsCsvByteArray(payments, paymentsCsvFileName, header);
        sendEmail(mail, paymentsByteArray, paymentsCsvFileName);
    }

    private void sendEmail(Email email, byte[] paymentsCsvByteArray, String csvFileName) {
        email.setAttachments(newArrayList(csv(paymentsCsvByteArray, csvFileName)));
        emailService.sendEmail(email);
        LOG.info("PaymentsReportService - Payments report email sent to " + Arrays.toString(email.getTo()));
    }

    private byte[] createPaymentsCsvByteArray(List<PaymentDto> payments, String paymentsCsvFileName, String header) {
        byte[] paymentsCsvByteArray = null;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            bos.write(header.getBytes(utf8));
            bos.write(BYTE_ARRAY_OUTPUT_STREAM_NEWLINE.getBytes(utf8));
            for (PaymentDto payment : payments) {
                if (paymentsCsvFileName.startsWith(CARD_PAYMENTS_CSV_FILE_PREFIX)) {
                    bos.write(payment.toCardPaymentCsv().getBytes(utf8));
                } else if (paymentsCsvFileName.startsWith(CREDIT_ACCOUNT_PAYMENTS_CSV_FILE_PREFIX)) {
                    bos.write(payment.toCreditAccountPaymentCsv().getBytes(utf8));
                }
                bos.write(BYTE_ARRAY_OUTPUT_STREAM_NEWLINE.getBytes(utf8));
            }

            LOG.info("PaymentsReportService - Total " + payments.size() + " payments records written in payments csv file " + paymentsCsvFileName);

            paymentsCsvByteArray = bos.toByteArray();

        } catch (IOException ex) {

            LOG.error("PaymentsReportService - Error while creating card payments csv file " + paymentsCsvFileName + ". Error message is " + ex.getMessage());

        }
        return paymentsCsvByteArray;

    }

    public List<PaymentDto> enrichWithFeeData(List<PaymentDto> payments) {
        for (PaymentDto payment : payments) {
            for (FeeDto fee : payment.getFees()) {
                Optional<FeeVersionDto> optionalFeeVersionDto = feesService.getFeeVersion(fee.getCode(), fee.getVersion());
                if (optionalFeeVersionDto.isPresent()) {
                    fee.setMemoLine(optionalFeeVersionDto.get().getMemoLine());
                    fee.setNaturalAccountCode(optionalFeeVersionDto.get().getNaturalAccountCode());
                }
            }
        }
        return payments;
    }
}

