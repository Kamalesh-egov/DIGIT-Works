import 'dart:async';
import 'dart:io';

import 'package:digit_components/theme/digit_theme.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:url_strategy/url_strategy.dart';
import 'package:works_shg_app/router/app_navigator_observer.dart';
import 'package:works_shg_app/router/app_router.dart';

import 'Env/app_config.dart';
import 'blocs/app_bloc_observer.dart';
import 'blocs/app_config/app_config.dart';
import 'blocs/auth/auth.dart';
import 'blocs/localization/app_localization.dart';
import 'blocs/localization/localization.dart';

void main() {
  HttpOverrides.global = MyHttpOverrides();
  setPathUrlStrategy();
  setEnvironment(Environment.dev);
  Bloc.observer = AppBlocObserver();
  runZonedGuarded(() async {
    FlutterError.onError = (FlutterErrorDetails details) {
      FlutterError.dumpErrorToConsole(details);
      if (kDebugMode) {
        print(details.exception.toString());
      }
      // exit(1); /// to close the app smoothly
    };

    WidgetsFlutterBinding.ensureInitialized();

    runApp(MainApplication(appRouter: AppRouter()));
  }, (Object error, StackTrace stack) {
    if (kDebugMode) {
      print(error.toString());
    } // exit(1); /// to close the app smoothly
  });
  // runApp(const MyApp());
}

class MainApplication extends StatelessWidget {
  final AppRouter appRouter;

  const MainApplication({
    Key? key,
    required this.appRouter,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MultiBlocProvider(
      providers: [
        BlocProvider(create: (context) => AuthBloc(const AuthState())),
        BlocProvider(
          create: (context) => LocalizationBloc(
            const LocalizationState(),
          )..add(const LocalizationEvent.onLoadLocalization(
              module: 'rainmaker-common',
              tenantId: 'pb',
              locale: 'en_IN',
            )),
          lazy: false,
        ),
        BlocProvider(
          create: (_) => ApplicationConfigBloc(const ApplicationConfigState())
            ..add(const ApplicationConfigEvent.onfetchConfig()),
        ),
      ],
      child: BlocBuilder<AuthBloc, AuthState>(builder: (context, state) {
        return MaterialApp.router(
          supportedLocales: const [
            Locale('en', 'IN'),
            Locale('hi', 'IN'),
            Locale.fromSubtags(languageCode: 'pn'),
          ],
          locale: const Locale('en', 'IN'),
          localizationsDelegates: const [
            AppLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
            GlobalMaterialLocalizations.delegate,
          ],
          theme: DigitTheme.instance.mobileTheme,
          routeInformationParser: appRouter.defaultRouteParser(),
          routerDelegate: AutoRouterDelegate.declarative(
            appRouter,
            navigatorObservers: () => [AppRouterObserver()],
            routes: (handler) => [
              if (state.isAuthenticated)
                const AuthenticatedRouteWrapper()
              else
                const UnauthenticatedRouteWrapper(),
            ],
          ),
        );
      }),
    );
  }
}

class MyHttpOverrides extends HttpOverrides {
  @override
  HttpClient createHttpClient(SecurityContext? context) {
    return super.createHttpClient(context)
      ..badCertificateCallback =
          (X509Certificate cert, String host, int port) => true;
  }
}
