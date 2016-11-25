
package uk.gov.justice.payment.api.external.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Data
@JsonNaming(SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder(builderMethodName = "paymentWith")
@AllArgsConstructor
@NoArgsConstructor
public class Payment {

    private Integer amount;
    private State state;
    private String description;
    private String reference;
    private String email;
    private String paymentId;
    private String paymentProvider;
    private String cardBrand;
    private String returnUrl;
    private String createdDate;
    private RefundSummary refundSummary;
    @JsonProperty("_links")
    private Links links;

    @Data
    @JsonNaming(SnakeCaseStrategy.class)
    @JsonInclude(NON_NULL)
    @AllArgsConstructor
    public static class Links {
        private Link self;
        private Link nextUrl;
        private Link nextUrlPost;
        private Link events;
        private Link refunds;
        private Link cancel;
    }
}