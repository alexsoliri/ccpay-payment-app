package uk.gov.hmcts.payment.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.payment.api.util.AccountStatus;

import java.math.BigDecimal;
import java.util.Date;

@JsonNaming(PropertyNamingStrategy.UpperCamelCaseStrategy.class)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class AccountDto {

    private String pbaNumber;

    private String accountName;

    private BigDecimal creditLimit;

    private BigDecimal availableBalance;

    private AccountStatus status;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "GMT")
    private Date effectiveDate;

}
