import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ValidatorService } from 'angular-iban';
import { FintechSinglePaymentInitiationService } from '../../../api';
import { ActivatedRoute, Router } from '@angular/router';
import { ClassSinglePaymentInitiationRequest } from '../../../api/model-classes/ClassSinglePaymentInitiationRequest';
import { map } from 'rxjs/operators';
import { HeaderConfig } from '../../../models/consts';
import { RedirectStruct, RedirectType } from '../../redirect-page/redirect-struct';
import { StorageService } from '../../../services/storage.service';

@Component({
  selector: 'app-initiate',
  templateUrl: './initiate.component.html',
  styleUrls: ['./initiate.component.scss']
})
export class InitiateComponent implements OnInit {
  public static ROUTE = 'initiate';
  bankId = '';

  paymentForm: FormGroup;
  constructor(private formBuilder: FormBuilder,
              private fintechSinglePaymentInitiationService: FintechSinglePaymentInitiationService,
              private router: Router,
              private route: ActivatedRoute,
              private storageService: StorageService) {
    this.bankId = this.route.snapshot.paramMap.get('bankid');
    console.log('bankid:' + this.bankId);
  }

  ngOnInit() {
    this.paymentForm = this.formBuilder.group({
      name: ['peter', Validators.required],
      debitorIban: ['DE80760700240271232400', [ValidatorService.validateIban, Validators.required]],
      creditorIban: ['DE80760700240271232400', [ValidatorService.validateIban, Validators.required]],
      amount: ['12.34', [Validators.pattern('^[1-9]\\d*(\\.\\d{1,2})?$'), Validators.required]],
      purpose: ['test transfer']
    });
  }

  onConfirm() {
    // TODO
    const okurl = window.location.pathname;
    const notOkUrl = okurl;
    console.log('WARNING set ok url to {}', okurl);

    const paymentRequest = new ClassSinglePaymentInitiationRequest();
    paymentRequest.amount = this.paymentForm.getRawValue().amount;
    paymentRequest.name = this.paymentForm.getRawValue().name;
    paymentRequest.creditorIban = this.paymentForm.getRawValue().debitorIban;
    paymentRequest.debitorIban = this.paymentForm.getRawValue().creditorIban;
    paymentRequest.purpose = this.paymentForm.getRawValue().purpose;
    this.fintechSinglePaymentInitiationService.initiateSinglePayment('', '',
      okurl, notOkUrl, this.bankId, paymentRequest, 'response')
      .pipe(map(response => response))
      .subscribe(
        response => {
          console.log('response status of payment call is ', response.status)
          switch (response.status) {
            case 202:
              const location = response.headers.get(HeaderConfig.HEADER_FIELD_LOCATION);
              this.storageService.setRedirect(
                response.headers.get(HeaderConfig.HEADER_FIELD_REDIRECT_CODE),
                response.headers.get(HeaderConfig.HEADER_FIELD_AUTH_ID),
                response.headers.get(HeaderConfig.HEADER_FIELD_X_XSRF_TOKEN),
                parseInt(response.headers.get(HeaderConfig.HEADER_FIELD_REDIRECT_X_MAX_AGE), 0),
                RedirectType.PIS
              );
              console.log('location is ', location);
              window.location.href = location;
          }
        });
  }

  onDeny() {}

  get debitorIban() {
    return this.paymentForm.get('debitorIban');
  }

  get creditorIban() {
    return this.paymentForm.get('creditorIban');
  }
}