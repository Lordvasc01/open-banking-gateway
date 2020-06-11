package de.adorsys.opba.protocol.hbci.service.protocol.ais;

import com.google.common.base.Strings;
import de.adorsys.multibanking.domain.Bank;
import de.adorsys.multibanking.domain.BankAccess;
import de.adorsys.multibanking.domain.BankAccount;
import de.adorsys.multibanking.domain.BankApiUser;
import de.adorsys.multibanking.domain.request.TransactionRequest;
import de.adorsys.multibanking.domain.response.AccountInformationResponse;
import de.adorsys.multibanking.domain.spi.OnlineBankingService;
import de.adorsys.multibanking.domain.transaction.AbstractTransaction;
import de.adorsys.multibanking.domain.transaction.LoadAccounts;
import de.adorsys.multibanking.hbci.model.HbciConsent;
import de.adorsys.opba.protocol.bpmnshared.service.context.ContextUtil;
import de.adorsys.opba.protocol.bpmnshared.service.exec.ValidatedExecution;
import de.adorsys.opba.protocol.hbci.context.AccountListHbciContext;
import de.adorsys.opba.protocol.hbci.context.HbciContext;
import de.adorsys.opba.protocol.hbci.service.protocol.ais.dto.AisListAccountsResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.iban4j.CountryCode;
import org.iban4j.Iban;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.stream.Collectors;

@Slf4j
@Service("hbciAccountListing")
@RequiredArgsConstructor
public class HbciAccountListing extends ValidatedExecution<AccountListHbciContext> {

    private final OnlineBankingService onlineBankingService;

    @Override
    protected void doRealExecution(DelegateExecution execution, AccountListHbciContext context) {

        HbciConsent consent = context.getHbciDialogConsent();
        TransactionRequest<LoadAccounts> request = create(new LoadAccounts(), new BankApiUser(), new BankAccess(), context.getBank(), consent);
        AccountInformationResponse response = onlineBankingService.loadBankAccounts(request);

        if (null == response.getAuthorisationCodeResponse()) {
            ContextUtil.getAndUpdateContext(
                    execution,
                    (AccountListHbciContext ctx) -> {
                        ctx.setResponse(
                                new AisListAccountsResult(
                                        response.getBankAccounts().stream()
                                                .map(it -> validateAndFixAccountIbans(execution, it))
                                                .collect(Collectors.toList()),
                                        Instant.now()
                                )
                        );
                        ctx.setTanChallengeRequired(false);
                    }
            );

            return;
        }

        onlineBankingService.getStrongCustomerAuthorisation().afterExecute(consent, response.getAuthorisationCodeResponse());
        ContextUtil.getAndUpdateContext(
                execution,
                (HbciContext ctx) -> {
                    ctx.setHbciDialogConsent((HbciConsent) response.getBankApiConsentData());
                    ctx.setTanChallengeRequired(true);
                }
        );
    }


    BankAccount validateAndFixAccountIbans(DelegateExecution execution, BankAccount account) {
        if (!Strings.isNullOrEmpty(account.getIban())) {
            return account;
        }

        log.warn("HBCI returned data without IBAN for execution ID {}, will compute IBAN", execution.getId());

        if (Strings.isNullOrEmpty(account.getBlz())) {
            throw new IllegalArgumentException("No BLZ to calculate IBAN");
        }

        if (Strings.isNullOrEmpty(account.getAccountNumber())) {
            throw new IllegalArgumentException("No account number to calculate IBAN");
        }
        // See https://www.iban.com/country/germany
        // IBAN is DEKK BBBB BBBB CCCC CCCC CC
        // Where:
        // K = MOD 97 (ISO 7064) Checksum
        // B = Bank Code aka BLZ ( Bankleitzahl in German )
        // C = Account number ( Kontonummer in German )
        Iban iban = new Iban.Builder()
                .countryCode(CountryCode.DE) // Don't expect to see HBCI outside of Germany
                .bankCode(Strings.padStart(account.getBlz(), 8, '0'))
                .accountNumber(Strings.padStart(account.getAccountNumber(), 10, '0'))
                .build();

        account.setIban(iban.toString());
        return account;
    }

    public static <T extends AbstractTransaction> TransactionRequest<T> create(T transaction,
                                                                               BankApiUser bankApiUser,
                                                                               BankAccess bankAccess,
                                                                               Bank bank,
                                                                               Object bankApiConsentData) {
        TransactionRequest<T> transactionRequest = new TransactionRequest<>(transaction);
        transactionRequest.setBankApiUser(bankApiUser);
        transactionRequest.setBankAccess(bankAccess);
        transactionRequest.setBankApiConsentData(bankApiConsentData);
        transactionRequest.setBank(bank);

        return transactionRequest;
    }
}