package io.horizontalsystems.bankwallet.core

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import com.google.firebase.FirebaseApp
import io.horizontalsystems.bankwallet.BuildConfig
import io.horizontalsystems.bankwallet.core.factories.*
import io.horizontalsystems.bankwallet.core.managers.*
import io.horizontalsystems.bankwallet.core.providers.AppConfigProvider
import io.horizontalsystems.bankwallet.core.providers.Erc20ContractInfoProvider
import io.horizontalsystems.bankwallet.core.providers.FeeCoinProvider
import io.horizontalsystems.bankwallet.core.providers.FeeRateProvider
import io.horizontalsystems.bankwallet.core.storage.*
import io.horizontalsystems.bankwallet.modules.fulltransactioninfo.FullTransactionInfoFactory
import io.horizontalsystems.bankwallet.modules.keystore.KeyStoreActivity
import io.horizontalsystems.bankwallet.modules.launcher.LauncherActivity
import io.horizontalsystems.bankwallet.modules.lockscreen.LockScreenActivity
import io.horizontalsystems.bankwallet.modules.lockscreen.LockScreenModule
import io.horizontalsystems.bankwallet.modules.ratechart.RateChartActivity
import io.horizontalsystems.bankwallet.modules.tor.TorConnectionActivity
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.CoreApp
import io.horizontalsystems.core.ICoreApp
import io.horizontalsystems.core.security.EncryptionManager
import io.horizontalsystems.core.security.KeyStoreManager
import io.horizontalsystems.pin.PinComponent
import io.reactivex.plugins.RxJavaPlugins
import java.util.logging.Level
import java.util.logging.Logger

class App : CoreApp() {

    companion object : ICoreApp by CoreApp {

        lateinit var feeRateProvider: FeeRateProvider
        lateinit var localStorage: ILocalStorage
        lateinit var torKitManager: ITorManager
        lateinit var chartTypeStorage: IChartTypeStorage

        lateinit var wordsManager: WordsManager
        lateinit var networkManager: INetworkManager
        lateinit var backgroundStateChangeListener: BackgroundStateChangeListener
        lateinit var appConfigProvider: IAppConfigProvider
        lateinit var adapterManager: IAdapterManager
        lateinit var walletManager: IWalletManager
        lateinit var walletStorage: IWalletStorage
        lateinit var accountManager: IAccountManager
        lateinit var backupManager: IBackupManager
        lateinit var accountCreator: IAccountCreator
        lateinit var predefinedAccountTypeManager: IPredefinedAccountTypeManager

        lateinit var xRateManager: IRateManager
        lateinit var connectivityManager: ConnectivityManager
        lateinit var appDatabase: AppDatabase
        lateinit var accountsStorage: IAccountsStorage
        lateinit var priceAlertManager: IPriceAlertManager
        lateinit var enabledWalletsStorage: IEnabledWalletStorage
        lateinit var transactionInfoFactory: FullTransactionInfoFactory
        lateinit var transactionDataProviderManager: TransactionDataProviderManager
        lateinit var ethereumKitManager: IEthereumKitManager
        lateinit var eosKitManager: IEosKitManager
        lateinit var binanceKitManager: BinanceKitManager
        lateinit var numberFormatter: IAppNumberFormatter
        lateinit var addressParserFactory: AddressParserFactory
        lateinit var feeCoinProvider: FeeCoinProvider
        lateinit var notificationManager: INotificationManager
        lateinit var appStatusManager: IAppStatusManager
        lateinit var appVersionManager: AppVersionManager
        lateinit var blockchainSettingsManager: IBlockchainSettingsManager
        lateinit var accountCleaner: IAccountCleaner
        lateinit var rateCoinMapper: RateCoinMapper
        lateinit var rateAppManager: IRateAppManager
        lateinit var derivationSettingsManager: IDerivationSettingsManager
        lateinit var coinRecordStorage: ICoinRecordStorage
        lateinit var coinManager: ICoinManager
        lateinit var erc20ContractInfoProvider: IErc20ContractInfoProvider
        lateinit var notificationSubscriptionManager: INotificationSubscriptionManager
        lateinit var termsManager: ITermsManager
    }

    override fun onCreate() {
        super.onCreate()

        if (!BuildConfig.DEBUG) {
            //Disable logging for lower levels in Release build
            Logger.getLogger("").level = Level.SEVERE
        }

        RxJavaPlugins.setErrorHandler { e: Throwable? ->
            Log.w("RxJava ErrorHandler", e)
        }

        instance = this
        preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        val appConfig = AppConfigProvider()
        appConfigProvider = appConfig
        appConfigTestMode = appConfig
        languageConfigProvider = appConfig

        feeRateProvider = FeeRateProvider(appConfigProvider)
        backgroundManager = BackgroundManager(this)

        ethereumKitManager = EthereumKitManager(appConfig.infuraProjectId, appConfig.infuraProjectSecret, appConfig.etherscanApiKey, appConfig.testMode, backgroundManager)
        eosKitManager = EosKitManager(appConfigTestMode.testMode)
        binanceKitManager = BinanceKitManager(appConfigTestMode.testMode)

        appDatabase = AppDatabase.getInstance(this)
        accountsStorage = AccountsStorage(appDatabase)

        AppLog.logsDao = appDatabase.logsDao()

        coinRecordStorage = CoinRecordStorage(appDatabase)
        coinManager = CoinManager(appConfigProvider, coinRecordStorage)

        enabledWalletsStorage = EnabledWalletsStorage(appDatabase)
        walletStorage = WalletStorage(coinManager, enabledWalletsStorage)

        LocalStorageManager(preferences).apply {
            localStorage = this
            chartTypeStorage = this
            pinStorage = this
            themeStorage = this
        }

        torKitManager = TorManager(instance, localStorage)

        val communicationSettingsManager = CommunicationSettingsManager(appConfigProvider, appDatabase)
        derivationSettingsManager = DerivationSettingsManager(appConfigProvider, appDatabase)
        val syncModeSettingsManager = SyncModeSettingsManager(appConfigProvider, appDatabase)
        blockchainSettingsManager = BlockchainSettingsManager(derivationSettingsManager, syncModeSettingsManager, communicationSettingsManager)

        wordsManager = WordsManager()
        networkManager = NetworkManager()
        accountCleaner = AccountCleaner(appConfigTestMode.testMode)
        accountManager = AccountManager(accountsStorage, accountCleaner)
        backupManager = BackupManager(accountManager)
        walletManager = WalletManager(accountManager, walletStorage)
        accountCreator = AccountCreator(AccountFactory(), wordsManager)
        predefinedAccountTypeManager = PredefinedAccountTypeManager(accountManager, accountCreator)

        KeyStoreManager("MASTER_KEY", KeyStoreCleaner(localStorage, accountManager, walletManager)).apply {
            keyStoreManager = this
            keyProvider = this
        }

        encryptionManager = EncryptionManager(keyProvider)

        systemInfoManager = SystemInfoManager()

        languageManager = LanguageManager()
        currencyManager = CurrencyManager(localStorage, appConfigProvider)
        numberFormatter = NumberFormatter(languageManager)

        connectivityManager = ConnectivityManager(backgroundManager)

        val adapterFactory = AdapterFactory(instance, appConfigTestMode.testMode, ethereumKitManager, eosKitManager, binanceKitManager, blockchainSettingsManager, backgroundManager)
        adapterManager = AdapterManager(walletManager, adapterFactory, ethereumKitManager, eosKitManager, binanceKitManager)

        rateCoinMapper = RateCoinMapper()
        xRateManager = RateManager(this, walletManager, currencyManager, rateCoinMapper)

        transactionDataProviderManager = TransactionDataProviderManager(appConfigTestMode.testMode, appConfigProvider.etherscanApiKey, localStorage)
        transactionInfoFactory = FullTransactionInfoFactory(networkManager, transactionDataProviderManager)

        addressParserFactory = AddressParserFactory()
        feeCoinProvider = FeeCoinProvider(appConfigProvider)

        notificationManager = NotificationManager(NotificationManagerCompat.from(this)).apply {
            backgroundManager.registerListener(this)
        }
        notificationSubscriptionManager = NotificationSubscriptionManager(appDatabase, notificationManager)
        priceAlertManager = PriceAlertManager(appDatabase, notificationSubscriptionManager, coinManager)

        appStatusManager = AppStatusManager(systemInfoManager, localStorage, predefinedAccountTypeManager, walletManager, adapterManager, coinManager, ethereumKitManager, eosKitManager, binanceKitManager)
        appVersionManager = AppVersionManager(systemInfoManager, localStorage).apply {
            backgroundManager.registerListener(this)
        }
        pinComponent = PinComponent(
                pinStorage = pinStorage,
                encryptionManager = encryptionManager,
                excludedActivityNames = listOf(
                        KeyStoreActivity::class.java.name,
                        LockScreenActivity::class.java.name,
                        LauncherActivity::class.java.name,
                        TorConnectionActivity::class.java.name,
                        RateChartActivity::class.java.name
                )
        )

        backgroundStateChangeListener = BackgroundStateChangeListener(systemInfoManager, keyStoreManager, pinComponent).apply {
            backgroundManager.registerListener(this)
        }

        rateAppManager = RateAppManager(walletManager, adapterManager, localStorage)
        erc20ContractInfoProvider = Erc20ContractInfoProvider(appConfigTestMode, appConfigProvider, networkManager)

        termsManager = TermsManager(localStorage)

        val nightMode = if (CoreApp.themeStorage.isLightModeOn)
            AppCompatDelegate.MODE_NIGHT_NO else
            AppCompatDelegate.MODE_NIGHT_YES

        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }

        registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks(torKitManager))

        startManagers()

        FirebaseApp.initializeApp(this)
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(localeAwareContext(base))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        localeAwareContext(this)
    }

    private fun startManagers() {
        Thread(Runnable {
            rateAppManager.onAppLaunch()
            accountManager.loadAccounts()
            walletManager.loadWallets()
            adapterManager.preloadAdapters()
            accountManager.clearAccounts()
            notificationSubscriptionManager.processJobs()
        }).start()

        rateAppManager.onAppBecomeActive()
    }
}
