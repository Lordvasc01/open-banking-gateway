package de.adorsys.opba.fintech.impl.service;

import de.adorsys.opba.api.security.external.domain.OperationType;
import de.adorsys.opba.fintech.impl.config.FintechUiConfig;
import de.adorsys.opba.fintech.impl.controller.RestRequestContext;
import de.adorsys.opba.fintech.impl.database.entities.ConsentEntity;
import de.adorsys.opba.fintech.impl.database.entities.RedirectUrlsEntity;
import de.adorsys.opba.fintech.impl.database.entities.SessionEntity;
import de.adorsys.opba.fintech.impl.database.repositories.ConsentRepository;
import de.adorsys.opba.fintech.impl.properties.TppProperties;
import de.adorsys.opba.fintech.impl.tppclients.ConsentType;
import de.adorsys.opba.fintech.impl.tppclients.TppAisClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

import static de.adorsys.opba.fintech.impl.tppclients.Consts.COMPUTE_FINTECH_ID;
import static de.adorsys.opba.fintech.impl.tppclients.Consts.COMPUTE_X_REQUEST_SIGNATURE;
import static de.adorsys.opba.fintech.impl.tppclients.Consts.COMPUTE_X_TIMESTAMP_UTC;


@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {
    private final FintechUiConfig uiConfig;
    private final TppAisClient tppAisClient;
    private final RestRequestContext restRequestContext;
    private final TppProperties tppProperties;
    private final RedirectHandlerService redirectHandlerService;
    private final ConsentRepository consentRepository;
    private final HandleAcceptedService handleAcceptedService;

    public ResponseEntity listAccounts(SessionEntity sessionEntity,
                                       String fintechOkUrl, String fintechNOKUrl,
                                       String bankID) {

        final String fintechRedirectCode = UUID.randomUUID().toString();
        ResponseEntity accounts = readOpbaResponse(bankID, sessionEntity, fintechRedirectCode);

        switch (accounts.getStatusCode()) {
            case OK:
                return new ResponseEntity<>(accounts.getBody(), HttpStatus.OK);
            case ACCEPTED:
                log.debug("create redirect entity for redirect code {}", fintechRedirectCode);
                redirectHandlerService.registerRedirectStateForSession(fintechRedirectCode, fintechOkUrl, fintechNOKUrl);
                return handleAcceptedService.handleAccepted(consentRepository, ConsentType.AIS, bankID, fintechRedirectCode, sessionEntity, accounts.getHeaders());
            case UNAUTHORIZED:
                return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
            default:
                throw new RuntimeException("DID NOT EXPECT RETURNCODE:" + accounts.getStatusCode());
        }
    }

    private ResponseEntity readOpbaResponse(String bankID, SessionEntity sessionEntity, String redirectCode) {
        UUID xRequestId = UUID.fromString(restRequestContext.getRequestId());
        Optional<ConsentEntity> optionalConsent = consentRepository.findByUserEntityAndBankIdAndConsentTypeAndConsentConfirmed(sessionEntity.getUserEntity(),
                bankID, ConsentType.AIS, Boolean.TRUE);
        if (optionalConsent.isPresent()) {
            log.info("LoA found valid ais consent for user {} bank {}", sessionEntity.getUserEntity().getLoginUserName(), bankID);
            return tppAisClient.getAccounts(
                    tppProperties.getServiceSessionPassword(),
                    sessionEntity.getUserEntity().getFintechUserId(),
                    RedirectUrlsEntity.buildOkUrl(uiConfig, redirectCode),
                    RedirectUrlsEntity.buildNokUrl(uiConfig, redirectCode),
                    xRequestId,
                    COMPUTE_X_TIMESTAMP_UTC,
                    OperationType.AIS.toString(),
                    COMPUTE_X_REQUEST_SIGNATURE,
                    COMPUTE_FINTECH_ID,
                    bankID,
                    null,
                    optionalConsent.get().getTppServiceSessionId());
        }
        log.info("LoA no valid ais consent for user {} bank {} available", sessionEntity.getUserEntity().getLoginUserName(), bankID);
        // FIXME: HACKETTY-HACK - force consent retrieval for transactions on ALL accounts
        // Should be superseded and fixed with
        // https://github.com/adorsys/open-banking-gateway/issues/303
        return tppAisClient.getTransactions(
                UUID.randomUUID().toString(), // As consent is missing this will be ignored
                tppProperties.getServiceSessionPassword(),
                sessionEntity.getUserEntity().getFintechUserId(),
                RedirectUrlsEntity.buildOkUrl(uiConfig, redirectCode),
                RedirectUrlsEntity.buildNokUrl(uiConfig, redirectCode),
                xRequestId,
                COMPUTE_X_TIMESTAMP_UTC,
                OperationType.AIS.toString(),
                COMPUTE_X_REQUEST_SIGNATURE,
                COMPUTE_FINTECH_ID,
                bankID, null, null, null, null, null, null, null);
    }
}