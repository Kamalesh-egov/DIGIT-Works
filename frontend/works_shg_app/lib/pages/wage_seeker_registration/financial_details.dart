import 'dart:convert';

import 'package:digit_components/widgets/atoms/digit_text_form_field.dart';
import 'package:digit_components/widgets/digit_card.dart';
import 'package:digit_components/widgets/digit_elevated_button.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:http/http.dart' as http;
import 'package:reactive_forms/reactive_forms.dart';
import 'package:works_shg_app/services/urls.dart';
import 'package:works_shg_app/utils/Constants/i18_key_constants.dart' as i18;

import '../../blocs/localization/app_localization.dart';
import '../../blocs/wage_seeker_registration/wage_seeker_registration_bloc.dart';
import '../../models/mdms/wage_seeker_mdms.dart';
import '../../models/wage_seeker/financial_details_model.dart';
import '../../models/wage_seeker/individual_details_model.dart';
import '../../models/wage_seeker/location_details_model.dart';
import '../../models/wage_seeker/skill_details_model.dart';
import '../../utils/models.dart';
import '../../widgets/atoms/radio_button_list.dart';

class FinancialDetailsPage extends StatefulWidget {
  final void Function() onPressed;
  final WageSeekerMDMS? wageSeekerMDMS;
  const FinancialDetailsPage(
      {required this.onPressed, this.wageSeekerMDMS, super.key});

  @override
  FinancialDetailsState createState() {
    return FinancialDetailsState();
  }
}

class FinancialDetailsState extends State<FinancialDetailsPage> {
  String accountType = '';
  FinancialDetails? financialDetails = FinancialDetails();
  IndividualDetails? individualDetails = IndividualDetails();
  LocationDetails? locationDetails = LocationDetails();
  SkillDetails? skillDetails = SkillDetails();
  String hintText = '';
  String accountHolderKey = 'accountHolder';
  String accountNoKey = 'accountNo';
  String reAccountNoKey = 'reAccountNo';
  String ifscCodeKey = 'ifscCode';

  @override
  void initState() {
    super.initState();
    final registrationState = BlocProvider.of<WageSeekerBloc>(context).state;
    individualDetails = registrationState.individualDetails;
    skillDetails = registrationState.skillDetails;
    locationDetails = registrationState.locationDetails;
    if (registrationState.financialDetails != null) {
      financialDetails = registrationState.financialDetails;
      accountType =
          registrationState.financialDetails?.accountType.toString() ?? '';
    }
  }

  @override
  Widget build(BuildContext context) {
    var t = AppLocalizations.of(context);
    List<KeyValue> accountTypeList = widget
        .wageSeekerMDMS!.worksMDMS!.bankAccType!
        .map((e) => KeyValue(t.translate(e.code), e.code))
        .toList();
    return ReactiveFormBuilder(
      form: () => buildForm(financialDetails ?? FinancialDetails()),
      builder: (context, form, child) {
        if (financialDetails != null) {
          accountType = financialDetails!.accountType != null
              ? financialDetails!.accountType.toString()
              : accountType;
        }
        return Column(
          mainAxisAlignment: MainAxisAlignment.start,
          children: [
            DigitCard(
              margin: const EdgeInsets.all(16.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    t.translate(i18.common.financialDetails),
                    style: Theme.of(context).textTheme.displayMedium,
                  ),
                  Column(children: [
                    DigitTextFormField(
                      formControlName: accountHolderKey,
                      label: t.translate(i18.common.accountHolderName),
                      isRequired: true,
                      keyboardType: TextInputType.name,
                      validationMessages: {
                        'required': (_) => t.translate(
                              i18.wageSeeker.accountHolderNameRequired,
                            ),
                      },
                    ),
                    DigitTextFormField(
                      formControlName: accountNoKey,
                      label: t.translate(i18.common.accountNo),
                      isRequired: true,
                      keyboardType: TextInputType.number,
                      inputFormatter: [
                        FilteringTextInputFormatter.allow(RegExp("[0-9]"))
                      ],
                      validationMessages: {
                        'required': (_) => t.translate(
                              i18.wageSeeker.accountNumberRequired,
                            ),
                        'mustMatch': (_) => AppLocalizations.of(context)
                            .translate(i18.wageSeeker.reEnterAccountNumber)
                      },
                    ),
                    DigitTextFormField(
                      formControlName: reAccountNoKey,
                      label: t.translate(i18.common.reEnterAccountNo),
                      isRequired: true,
                      keyboardType: TextInputType.number,
                      inputFormatter: [
                        FilteringTextInputFormatter.allow(RegExp("[0-9]"))
                      ],
                      validationMessages: {
                        'mustMatch': (_) => AppLocalizations.of(context)
                            .translate(i18.wageSeeker.reEnterAccountNumber)
                      },
                    ),
                    StatefulBuilder(
                        builder: (BuildContext context, StateSetter setState) {
                      return DigitRadioButtonList(
                          context,
                          t.translate(i18.common.accountType),
                          accountType,
                          '',
                          '',
                          true,
                          accountTypeList, (value) {
                        setState(() {
                          accountType = value;
                        });
                      });
                    }),
                    DigitTextFormField(
                        formControlName: ifscCodeKey,
                        label: t.translate(i18.common.ifscCode),
                        onChanged: (val) async {
                          final url = Uri.parse(
                              '${Urls.commonServices.bankDetails}/${form.value[ifscCodeKey]}');
                          final response = await http.get(url);
                          if (response.statusCode == 200) {
                            final data = jsonDecode(response.body);
                            final String bankName = data['BANKCODE'];
                            final String branchName = data['BRANCH'];

                            setState(() {
                              hintText =
                                  '${t.translate(bankName)}, ${t.translate(branchName)}';
                            });
                          }
                        },
                        isRequired: true,
                        validationMessages: {
                          'required': (_) => t.translate(
                                i18.wageSeeker.ifscCodeRequired,
                              ),
                        },
                        hintText: hintText),
                  ]),
                  const SizedBox(height: 16),
                  DigitCard(
                      child: Center(
                    child: DigitElevatedButton(
                        onPressed: () {
                          form.markAllAsTouched(updateParent: false);
                          if (!form.valid) return;
                          final financeDetails = FinancialDetails(
                              accountHolderName:
                                  form.value[accountHolderKey].toString() ?? '',
                              accountNumber:
                                  form.value[accountNoKey].toString() ?? '',
                              reAccountNumber:
                                  form.value[reAccountNoKey].toString(),
                              ifscCode: form.value[ifscCodeKey].toString(),
                              accountType: accountType);
                          BlocProvider.of<WageSeekerBloc>(context).add(
                            WageSeekerCreateEvent(
                                individualDetails: individualDetails,
                                skillDetails: skillDetails,
                                locationDetails: locationDetails,
                                financialDetails: financeDetails),
                          );
                          widget.onPressed();
                        },
                        child: Center(
                          child: Text(t.translate(i18.common.submit)),
                        )),
                  ))
                ],
              ),
            ),
          ],
        );
      },
    );
  }

  FormGroup buildForm(FinancialDetails finance) => fb.group(<String, Object>{
        accountHolderKey: FormControl<String>(
            value: finance.accountHolderName,
            validators: [Validators.required]),
        accountNoKey: FormControl<String>(
            value: finance.accountNumber, validators: [Validators.required]),
        reAccountNoKey: FormControl<String>(value: finance.reAccountNumber),
        ifscCodeKey: FormControl<String>(
            value: finance.ifscCode, validators: [Validators.required]),
      }, [
        Validators.mustMatch(accountNoKey, reAccountNoKey)
      ]);
}
