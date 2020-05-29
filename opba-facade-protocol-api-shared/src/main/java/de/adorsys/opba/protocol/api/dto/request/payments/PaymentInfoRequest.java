package de.adorsys.opba.protocol.api.dto.request.payments;

import de.adorsys.opba.protocol.api.dto.request.FacadeServiceableGetter;
import de.adorsys.opba.protocol.api.dto.request.FacadeServiceableRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The request to get information about payment.
 */
// TODO Validation, Immutability
@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInfoRequest implements FacadeServiceableGetter {
    /**
     * The request representation that is being serviced by facade.
     */
    private FacadeServiceableRequest facadeServiceable;

}