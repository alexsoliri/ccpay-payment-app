
package uk.gov.justice.payment.api.controllers.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder(builderMethodName = "paymentDtoWith")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class PaymentDto {

    private Integer amount;
    private StateDto state;
    private String description;
    private String applicationReference;
    private String paymentReference;
    private String paymentId;
    private String createdDate;

    @JsonProperty("_links")
    private LinksDto links;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(NON_NULL)
    public static class StateDto {
        private String status;
        private Boolean finished;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonNaming(SnakeCaseStrategy.class)
    @JsonInclude(NON_NULL)
    public static class LinksDto {
        private LinkDto nextUrl;
        private LinkDto cancel;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(value = NON_NULL)
    public static class LinkDto {
        private String href;
        private String method;
    }
}
