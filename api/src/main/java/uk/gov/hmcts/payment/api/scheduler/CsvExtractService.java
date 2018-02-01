package uk.gov.hmcts.payment.api.scheduler;

import org.apache.commons.io.FileUtils;
import org.joda.time.MutableDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.payment.api.contract.CardPaymentDto;
import uk.gov.hmcts.payment.api.controllers.CardPaymentDtoMapper;
import uk.gov.hmcts.payment.api.model.CardPaymentService;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CsvExtractService {

    private static final SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");

    private static final String HEADER = "Service,Payment reference,CCD reference,Case reference,Payment date,Payment channel,Payment amount,"
        + "Site id,Fee code,Version,Fee code,Version,Fee code,Version,Fee code,Version,Fee code,Version";

    private CardPaymentService<PaymentFeeLink, String> cardPaymentService;

    private CardPaymentDtoMapper cardPaymentDtoMapper;

    private String extractFileLocation;

    @Autowired
    public CsvExtractService(@Qualifier("loggingCardPaymentService") CardPaymentService<PaymentFeeLink, String> cardPaymentService, CardPaymentDtoMapper cardPaymentDtoMapper,
                             @Value("${csv.extract.file.location}") String extractFileLocation) {
        this.cardPaymentService = cardPaymentService;
        this.cardPaymentDtoMapper = cardPaymentDtoMapper;
        this.extractFileLocation = extractFileLocation;
    }

    public void extractCsv(String startDate, String endDate) throws ParseException, IOException {
        Date fromDate = startDate == null ? sdf.parse(getYesterdaysDate()) : sdf.parse(startDate);
        Date toDate = endDate == null ? sdf.parse(getTodaysDate()) : sdf.parse(endDate);

        // Limiting search only to date without time.
        MutableDateTime mutableToDate = new MutableDateTime(toDate);
        mutableToDate.addDays(1);

        List<CardPaymentDto> cardPayments = cardPaymentService.search(fromDate, mutableToDate.toDate()).stream()
            .map(cardPaymentDtoMapper::toReconciliationResponseDto).collect(Collectors.toList());

        createCsv(cardPayments);
    }

    private void createCsv(List<CardPaymentDto> cardPayments) throws IOException {

        FileUtils.cleanDirectory(new File(extractFileLocation));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
        String fileNameSuffix = LocalDateTime.now().format(formatter);
        String extractFileName = extractFileLocation + File.separator + "hmcts_payments_" + fileNameSuffix + ".csv";

        Path path = Paths.get(extractFileName);

        try (BufferedWriter writer = Files.newBufferedWriter(path, Charset.forName("UTF-8"))) {
            writer.write(HEADER);
            writer.newLine();
            for (CardPaymentDto cardPayment : cardPayments) {
                writer.write(cardPayment.toCsv());
                writer.newLine();
            }

        } catch (IOException ex) {

        }


    }

    private String getYesterdaysDate() {
        Date now = new Date();
        MutableDateTime mtDtNow = new MutableDateTime(now);
        mtDtNow.addDays(-1);
        return sdf.format(mtDtNow.toDate());
    }

    private String getTodaysDate() {
        return sdf.format(new Date());
    }


}
