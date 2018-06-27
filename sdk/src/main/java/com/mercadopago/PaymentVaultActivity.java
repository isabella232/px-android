package com.mercadopago;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mercadopago.adapters.PaymentMethodSearchItemAdapter;
import com.mercadopago.callbacks.OnSelectedCallback;
import com.mercadopago.controllers.CheckoutTimer;
import com.mercadopago.core.CheckoutStore;
import com.mercadopago.core.MercadoPagoCheckout;
import com.mercadopago.core.MercadoPagoComponents;
import com.mercadopago.customviews.GridSpacingItemDecoration;
import com.mercadopago.customviews.MPTextView;
import com.mercadopago.exceptions.MercadoPagoError;
import com.mercadopago.hooks.Hook;
import com.mercadopago.hooks.HookActivity;
import com.mercadopago.internal.datasource.PluginService;
import com.mercadopago.internal.di.AmountModule;
import com.mercadopago.internal.repository.PaymentSettingRepository;
import com.mercadopago.lite.exceptions.ApiException;
import com.mercadopago.model.Campaign;
import com.mercadopago.model.Card;
import com.mercadopago.model.CouponDiscount;
import com.mercadopago.model.CustomSearchItem;
import com.mercadopago.model.Discount;
import com.mercadopago.model.Issuer;
import com.mercadopago.model.Payer;
import com.mercadopago.model.PayerCost;
import com.mercadopago.model.PaymentMethod;
import com.mercadopago.model.PaymentMethodSearch;
import com.mercadopago.model.PaymentMethodSearchItem;
import com.mercadopago.model.Site;
import com.mercadopago.model.Token;
import com.mercadopago.observers.TimerObserver;
import com.mercadopago.plugins.PaymentMethodPlugin;
import com.mercadopago.plugins.PaymentMethodPluginActivity;
import com.mercadopago.plugins.model.PaymentMethodInfo;
import com.mercadopago.preferences.CheckoutPreference;
import com.mercadopago.preferences.FlowPreference;
import com.mercadopago.preferences.PaymentPreference;
import com.mercadopago.preferences.ServicePreference;
import com.mercadopago.presenters.PaymentVaultPresenter;
import com.mercadopago.providers.PaymentVaultProviderImpl;
import com.mercadopago.uicontrollers.FontCache;
import com.mercadopago.uicontrollers.paymentmethodsearch.PaymentMethodInfoController;
import com.mercadopago.uicontrollers.paymentmethodsearch.PaymentMethodSearchCustomOption;
import com.mercadopago.uicontrollers.paymentmethodsearch.PaymentMethodSearchOption;
import com.mercadopago.uicontrollers.paymentmethodsearch.PaymentMethodSearchViewController;
import com.mercadopago.util.ApiUtil;
import com.mercadopago.util.ErrorUtil;
import com.mercadopago.util.JsonUtil;
import com.mercadopago.util.ScaleUtil;
import com.mercadopago.views.AmountView;
import com.mercadopago.views.DiscountDetailDialog;
import com.mercadopago.views.PaymentVaultView;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PaymentVaultActivity extends MercadoPagoBaseActivity
    implements PaymentVaultView, TimerObserver {

    private static final String PUBLIC_KEY_BUNDLE = "publicKey";
    private static final String MERCHANT_BASE_URL_BUNDLE = "mMerchantBaseUrl";
    private static final String MERCHANT_GET_CUSTOMER_URI_BUNDLE = "mMerchantGetCustomerUri";
    private static final String MERCHANT_GET_CUSTOMER_ADDITIONAL_INFO = "mMerchantGetCustomerAdditionalInfo";
    private static final String SHOW_BANK_DEALS_BUNDLE = "mShowBankDeals";
    private static final String PRESENTER_BUNDLE = "presenter";

    public static final int COLUMN_SPACING_DP_VALUE = 20;
    public static final int COLUMNS = 2;

    public static final String EXTRA_PAYMENT_METHODS = "paymentMethodSearch";

    // Local vars
    protected boolean mActivityActive;
    protected Token mToken;
    protected Issuer mSelectedIssuer;
    protected PayerCost mSelectedPayerCost;
    protected Card mSelectedCard;
    protected Context mContext;

    protected Boolean mInstallmentsEnabled;

    // Controls
    protected RecyclerView mSearchItemsRecyclerView;
    protected AppBarLayout mAppBar;

    protected PaymentVaultPresenter presenter;
    protected CollapsingToolbarLayout mAppBarLayout;
    protected MPTextView mTimerTextView;
    protected Boolean mShowBankDeals;
    protected Boolean mEscEnabled;

    protected View mProgressLayout;

    protected String mPublicKey;
    protected String mPrivateKey;
    protected ServicePreference mServicePreference;

    protected String mMerchantBaseUrl;
    protected String mMerchantGetCustomerUri;
    protected Map<String, String> mMerchantGetCustomerAdditionalInfo;

    private AmountView amountView;
    private PaymentSettingRepository configuration;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final AmountModule amountModule = new AmountModule(this);
        configuration = amountModule.getConfigurationModule().getConfiguration();
        mPrivateKey = configuration.getCheckoutPreference().getPayer().getAccessToken();
        presenter = new PaymentVaultPresenter(amountModule.getAmountRepository(), configuration,
            amountModule.getConfigurationModule().getUserSelectionRepository(),
            new PluginService(this));

        getActivityParameters();
        configurePresenter();
        setMerchantInfo();
        setContentView();
        initializeControls();
        cleanPaymentMethodOptions();

        //Avoid automatic selection if activity restored on back pressed from next step
        initialize(savedInstanceState == null);
    }

    private void configurePresenter() {
        presenter.attachView(this);
        presenter.attachResourcesProvider(
            new PaymentVaultProviderImpl(getApplicationContext(), mPublicKey, mPrivateKey, mMerchantBaseUrl,
                mMerchantGetCustomerUri,
                mMerchantGetCustomerAdditionalInfo, mEscEnabled));
    }

    protected void setMerchantInfo() {
        if (mServicePreference != null) {
            mMerchantBaseUrl = mServicePreference.getDefaultBaseURL();
            mMerchantGetCustomerUri = mServicePreference.getGetCustomerURI();
            mMerchantGetCustomerAdditionalInfo = mServicePreference.getGetCustomerAdditionalInfo();
        }
    }

    protected void setContentView() {
        setContentView(R.layout.mpsdk_activity_payment_vault);
    }

    protected void getActivityParameters() {
        final Intent intent = getIntent();
        final Bundle extras = intent.getExtras();
        final JsonUtil instance = JsonUtil.getInstance();

        mShowBankDeals = intent.getBooleanExtra("showBankDeals", true);
        mEscEnabled = intent.getBooleanExtra("escEnabled", false);
        mInstallmentsEnabled = intent.getBooleanExtra("installmentsEnabled", true);
        mServicePreference =
            instance.fromJson(intent.getStringExtra("servicePreference"), ServicePreference.class);
        mPublicKey = intent.getStringExtra("merchantPublicKey");

        presenter.setInstallmentsReviewEnabled(
            intent.getBooleanExtra("installmentsReviewEnabled", true));
        presenter
            .setMaxSavedCards(intent.getIntExtra("maxSavedCards", FlowPreference.DEFAULT_MAX_SAVED_CARDS_TO_SHOW));
        presenter.setShowAllSavedCardsEnabled(intent.getBooleanExtra("showAllSavedCardsEnabled", false));

        if (intent.getStringExtra("selectedSearchItem") != null) {
            presenter.setSelectedSearchItem(instance
                .fromJson(intent.getStringExtra("selectedSearchItem"), PaymentMethodSearchItem.class));
        }

        if (extras != null && intent.hasExtra(EXTRA_PAYMENT_METHODS)) {
            final PaymentMethodSearch paymentMethodSearch =
                (PaymentMethodSearch) intent.getSerializableExtra(EXTRA_PAYMENT_METHODS);
            presenter.setPaymentMethodSearch(paymentMethodSearch);
            try {
                final Gson gson = new Gson();
                final Type listType = new TypeToken<List<Card>>() {
                }.getType();
                final List<Card> cards = (gson.fromJson(intent.getStringExtra("cards"), listType));

                paymentMethodSearch.setCards(cards, getString(R.string.mpsdk_last_digits_label));
            } catch (final Exception ex) {
                //Do nothing...
            }
        }
    }

    protected void initializeControls() {
        mTimerTextView = findViewById(R.id.mpsdkTimerTextView);

        amountView = findViewById(R.id.amount_view);
        mProgressLayout = findViewById(R.id.mpsdkProgressLayout);

        initializePaymentOptionsRecyclerView();
        mAppBar = findViewById(R.id.mpsdkAppBar);
        mAppBarLayout = findViewById(R.id.mpsdkCollapsingToolbar);
        initializeToolbar();
    }

    protected void initialize(final boolean selectAutomatically) {
        showTimer();
        presenter.initialize(selectAutomatically);
    }

    private void showTimer() {
        if (CheckoutTimer.getInstance().isTimerEnabled()) {
            CheckoutTimer.getInstance().addObserver(this);
            mTimerTextView.setVisibility(View.VISIBLE);
            mTimerTextView.setText(CheckoutTimer.getInstance().getCurrentTime());
        }
    }

    private void initializeToolbar() {
        final Toolbar toolbar = findViewById(R.id.mpsdkToolbar);
        setSupportActionBar(toolbar);
        final ActionBar supportActionBar = getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayShowTitleEnabled(false);
            supportActionBar.setDisplayHomeAsUpEnabled(true);
            supportActionBar.setDisplayShowHomeEnabled(true);
        }

        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        if (FontCache.hasTypeface(FontCache.CUSTOM_REGULAR_FONT)) {
            mAppBarLayout.setCollapsedTitleTypeface(FontCache.getTypeface(FontCache.CUSTOM_REGULAR_FONT));
            mAppBarLayout.setExpandedTitleTypeface(FontCache.getTypeface(FontCache.CUSTOM_REGULAR_FONT));
        }
    }

    protected void initializePaymentOptionsRecyclerView() {
        int columns = COLUMNS;
        mSearchItemsRecyclerView = findViewById(R.id.mpsdkGroupsList);
        mSearchItemsRecyclerView.setLayoutManager(new GridLayoutManager(this, columns));
        mSearchItemsRecyclerView.addItemDecoration(
            new GridSpacingItemDecoration(columns, ScaleUtil.getPxFromDp(COLUMN_SPACING_DP_VALUE, this), true));
        PaymentMethodSearchItemAdapter groupsAdapter = new PaymentMethodSearchItemAdapter();
        mSearchItemsRecyclerView.setAdapter(groupsAdapter);
    }

    protected void populateSearchList(List<PaymentMethodSearchItem> items,
        OnSelectedCallback<PaymentMethodSearchItem> onSelectedCallback) {
        PaymentMethodSearchItemAdapter adapter = (PaymentMethodSearchItemAdapter) mSearchItemsRecyclerView.getAdapter();
        List<PaymentMethodSearchViewController> customViewControllers =
            createSearchItemsViewControllers(items, onSelectedCallback);
        adapter.addItems(customViewControllers);
        adapter.notifyItemInserted();
    }

    @Deprecated
    private void populateCustomOptionsList(List<CustomSearchItem> customSearchItems,
        OnSelectedCallback<CustomSearchItem> onSelectedCallback) {
        PaymentMethodSearchItemAdapter adapter = (PaymentMethodSearchItemAdapter) mSearchItemsRecyclerView.getAdapter();
        List<PaymentMethodSearchViewController> customViewControllers =
            createCustomSearchItemsViewControllers(customSearchItems, onSelectedCallback);
        adapter.addItems(customViewControllers);
        adapter.notifyItemInserted();
    }

    private List<PaymentMethodSearchViewController> createSearchItemsViewControllers(
        List<PaymentMethodSearchItem> items, final OnSelectedCallback<PaymentMethodSearchItem> onSelectedCallback) {
        final List<PaymentMethodSearchViewController> customViewControllers = new ArrayList<>();
        for (final PaymentMethodSearchItem item : items) {
            PaymentMethodSearchViewController viewController = new PaymentMethodSearchOption(this, item);
            viewController.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onSelectedCallback.onSelected(item);
                }
            });
            customViewControllers.add(viewController);
        }
        return customViewControllers;
    }

    @Deprecated
    private List<PaymentMethodSearchViewController> createCustomSearchItemsViewControllers(
        final List<CustomSearchItem> customSearchItems, final OnSelectedCallback<CustomSearchItem> onSelectedCallback) {
        final List<PaymentMethodSearchViewController> customViewControllers = new ArrayList<>();
        for (final CustomSearchItem item : customSearchItems) {
            final PaymentMethodSearchCustomOption viewController = new PaymentMethodSearchCustomOption(this, item);
            viewController.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    onSelectedCallback.onSelected(item);
                }
            });

            customViewControllers.add(viewController);
        }
        return customViewControllers;
    }

    private List<PaymentMethodSearchViewController> createPluginItemsViewControllers(
        final List<PaymentMethodInfo> infoItems) {
        final CheckoutStore store = CheckoutStore.getInstance();
        final List<PaymentMethodSearchViewController> controllers = new ArrayList<>();
        for (final PaymentMethodInfo infoItem : infoItems) {
            final PaymentMethodPlugin plugin = store.getPaymentMethodPluginById(infoItem.id);
            if (plugin != null && plugin.isEnabled(store.getData())) {
                final PaymentMethodSearchViewController viewController =
                    new PaymentMethodInfoController(this, infoItem);
                viewController.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(final View v) {
                        final String id = String.valueOf(v.getTag());
                        presenter.selectPluginPaymentMethod(CheckoutStore.getInstance().getPaymentMethodPluginById(id));
                    }
                });
                controllers.add(viewController);
            }
        }
        return controllers;
    }

    @Override
    public void showPaymentMethodPluginActivity() {
        startActivityForResult(PaymentMethodPluginActivity.getIntent(this, mPublicKey),
            MercadoPagoComponents.Activities.PLUGIN_PAYMENT_METHOD_REQUEST_CODE);
        overrideTransitionIn();
    }

    @Override
    public void showSelectedItem(PaymentMethodSearchItem item) {
        final Intent intent = new Intent(this, PaymentVaultActivity.class);
        intent.putExtras(getIntent());
        intent.putExtra("selectedSearchItem", JsonUtil.getInstance().toJson(item));
        intent.putExtra(EXTRA_PAYMENT_METHODS, presenter.getPaymentMethodSearch());
        startActivityForResult(intent, MercadoPagoComponents.Activities.PAYMENT_VAULT_REQUEST_CODE);
        overridePendingTransition(R.anim.mpsdk_slide_right_to_left_in, R.anim.mpsdk_slide_right_to_left_out);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == MercadoPagoComponents.Activities.CARD_VAULT_REQUEST_CODE) {
            resolveCardRequest(resultCode, data);
        } else if (requestCode == MercadoPagoComponents.Activities.PAYMENT_METHODS_REQUEST_CODE) {
            presenter.onPaymentMethodReturned();
        } else if (requestCode == MercadoPagoComponents.Activities.PAYMENT_VAULT_REQUEST_CODE) {
            resolvePaymentVaultRequest(resultCode, data);
        } else if (requestCode == MercadoPagoComponents.Activities.PAYER_INFORMATION_REQUEST_CODE) {
            resolvePayerInformationRequest(resultCode, data);
        } else if (requestCode == MercadoPagoComponents.Activities.PLUGIN_PAYMENT_METHOD_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                presenter.onPluginAfterHookOne();
            } else {
                overrideTransitionOut();
            }
        } else if (requestCode == MercadoPagoComponents.Activities.HOOK_1) {
            resolveHook1Request(resultCode);
        } else if (requestCode == MercadoPagoComponents.Activities.HOOK_1_PLUGIN) {
            presenter.onPluginHookOneResult();
        } else if (requestCode == ErrorUtil.ERROR_REQUEST_CODE) {
            resolveErrorRequest(resultCode, data);
            overrideTransitionOut();
        }
    }

    private void resolveErrorRequest(int resultCode, Intent data) {
        presenter.onHookReset();

        if (resultCode == RESULT_OK) {
            recoverFromFailure();
        } else if (presenter.isItemSelected()) {
            hideProgress();
        } else {
            setResult(resultCode, data);
            finish();
        }
    }

    private void recoverFromFailure() {
        presenter.recoverFromFailure();
    }

    private void resolvePaymentVaultRequest(final int resultCode, final Intent data) {
        presenter.onHookReset();

        if (resultCode == RESULT_OK) {
            setResult(RESULT_OK, data);
            finish();
        } else if (resultCode == RESULT_CANCELED && data != null && data.hasExtra("mercadoPagoError")) {
            setResult(Activity.RESULT_CANCELED, data);
            finish();
        } else {
            //When it comes back from payment vault "children" view
            presenter.trackInitialScreen();

            if (shouldFinishOnBack(data)) {
                setResult(Activity.RESULT_CANCELED, data);
                finish();
            }
        }
    }

    protected void resolveCardRequest(final int resultCode, final Intent data) {
        presenter.onHookReset();

        if (resultCode == RESULT_OK) {
            showProgress();

            mToken = JsonUtil.getInstance().fromJson(data.getStringExtra("token"), Token.class);
            mSelectedIssuer = JsonUtil.getInstance().fromJson(data.getStringExtra("issuer"), Issuer.class);
            mSelectedPayerCost = JsonUtil.getInstance().fromJson(data.getStringExtra("payerCost"), PayerCost.class);
            mSelectedCard = JsonUtil.getInstance().fromJson(data.getStringExtra("card"), Card.class);
            final Discount discount = JsonUtil.getInstance().fromJson(data.getStringExtra("discount"), Discount.class);
            if (discount != null) {
                configuration.configure(discount);
                presenter.initializeAmountRow();
            }

            finishWithCardResult();
        } else {
            presenter.trackChildrenScreen();

            if (shouldFinishOnBack(data)) {
                setResult(Activity.RESULT_CANCELED, data);
                finish();
            } else {
                overridePendingTransition(R.anim.mpsdk_slide_left_to_right_in, R.anim.mpsdk_slide_left_to_right_out);
            }
            final Discount discount;

            if (data != null && data.getStringExtra("discount") != null) {
                discount = JsonUtil.getInstance().fromJson(data.getStringExtra("discount"), Discount.class);
                configuration.configure(discount);
                presenter.initializeAmountRow();
            }
        }
    }

    private void resolvePayerInformationRequest(final int resultCode, final Intent data) {
        presenter.onHookReset();
        if (resultCode == RESULT_OK) {
            final Payer payer = JsonUtil.getInstance().fromJson(data.getStringExtra("payer"), Payer.class);
            presenter.onPayerInformationReceived(payer);
        } else {
            overridePendingTransition(R.anim.mpsdk_slide_left_to_right_in, R.anim.mpsdk_slide_left_to_right_out);
        }
    }

    private boolean shouldFinishOnBack(Intent data) {
        return !CheckoutStore.getInstance().hasEnabledPaymentMethodPlugin() &&
            (presenter.getSelectedSearchItem() != null &&
                (!presenter.getSelectedSearchItem().hasChildren()
                    || (presenter.getSelectedSearchItem().getChildren().size() == 1))
                || (presenter.getSelectedSearchItem() == null &&
                presenter.isOnlyOneItemAvailable()) ||
                (data != null) && (data.getStringExtra("mercadoPagoError") != null));
    }

    @Override
    public void cleanPaymentMethodOptions() {
        final PaymentMethodSearchItemAdapter adapter =
            (PaymentMethodSearchItemAdapter) mSearchItemsRecyclerView.getAdapter();
        adapter.clear();
    }

    @Override
    public void finishPaymentMethodSelection(PaymentMethod paymentMethod) {
        finishWith(paymentMethod, configuration.getDiscount(), null);
    }

    @Override
    public void finishPaymentMethodSelection(PaymentMethod paymentMethod, Payer payer) {
        finishWith(paymentMethod, configuration.getDiscount(), payer);
    }

    private void finishWith(PaymentMethod paymentMethod, Discount discount, Payer payer) {
        Intent returnIntent = new Intent();
        returnIntent.putExtra("paymentMethod", JsonUtil.getInstance().toJson(paymentMethod));
        returnIntent.putExtra("discount", JsonUtil.getInstance().toJson(discount));
        returnIntent.putExtra("payer", JsonUtil.getInstance().toJson(payer));
        returnIntent.putExtra(EXTRA_PAYMENT_METHODS, presenter.getPaymentMethodSearch());

        finishWithResult(returnIntent);
    }

    protected void finishWithCardResult() {
        final Intent returnIntent = new Intent();
        returnIntent.putExtra("token", JsonUtil.getInstance().toJson(mToken));
        if (mSelectedIssuer != null) {
            returnIntent.putExtra("issuer", JsonUtil.getInstance().toJson(mSelectedIssuer));
        }
        returnIntent.putExtra("payerCost", JsonUtil.getInstance().toJson(mSelectedPayerCost));
        returnIntent.putExtra("discount", JsonUtil.getInstance().toJson(configuration.getDiscount()));
        returnIntent.putExtra("card", JsonUtil.getInstance().toJson(mSelectedCard));
        finishWithResult(returnIntent);
    }

    private void finishWithResult(final Intent returnIntent) {
        setResult(Activity.RESULT_OK, returnIntent);
        finish();
        overrideTransitionIn();
    }

    @Override
    public void showProgress() {
        mProgressLayout.setVisibility(View.VISIBLE);
        mAppBar.setVisibility(View.GONE);
    }

    @Override
    public void hideProgress() {
        mProgressLayout.setVisibility(View.GONE);
        mAppBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void setTitle(final String title) {
        if (mAppBarLayout != null) {
            mAppBarLayout.setTitle(title);
        }
    }

    @Override
    public void startSavedCardFlow(final Card card) {
        getCardVaultActivityBuilder()
            .setCard(card)
            .startActivity(this, MercadoPagoComponents.Activities.CARD_VAULT_REQUEST_CODE);

        overrideTransitionIn();
    }

    @Override
    public void startCardFlow(final Boolean automaticSelection) {
        getCardVaultActivityBuilder()
            .setAutomaticSelection(automaticSelection)
            .setAcceptedPaymentMethods(presenter.getPaymentMethodSearch().getPaymentMethods())
            .startActivity(this, MercadoPagoComponents.Activities.CARD_VAULT_REQUEST_CODE);

        overrideTransitionIn();
    }

    private MercadoPagoComponents.Activities.CardVaultActivityBuilder getCardVaultActivityBuilder() {
        return new MercadoPagoComponents.Activities.CardVaultActivityBuilder()
            .setMerchantPublicKey(mPublicKey)
            .setInstallmentsReviewEnabled(presenter.getInstallmentsReviewEnabled())
            .setInstallmentsEnabled(mInstallmentsEnabled)
            .setShowBankDeals(mShowBankDeals)
            .setESCEnabled(mEscEnabled);
    }

    @Override
    public void startPaymentMethodsSelection(final PaymentPreference paymentPreference) {
        new MercadoPagoComponents.Activities.PaymentMethodsActivityBuilder()
            .setActivity(this)
            .setMerchantPublicKey(mPublicKey)
            .setPaymentPreference(paymentPreference)
            .startActivity();
    }

    public void showApiException(ApiException apiException, String requestOrigin) {
        if (mActivityActive) {
            ApiUtil.showApiExceptionError(this, apiException, mPublicKey, requestOrigin);
        }
    }

    @Deprecated
    @Override
    public void showCustomOptions(List<CustomSearchItem> customSearchItems,
        OnSelectedCallback<CustomSearchItem> customSearchItemOnSelectedCallback) {
        populateCustomOptionsList(customSearchItems, customSearchItemOnSelectedCallback);
    }

    @Override
    public void showSearchItems(List<PaymentMethodSearchItem> searchItems,
        OnSelectedCallback<PaymentMethodSearchItem> paymentMethodSearchItemSelectionCallback) {
        populateSearchList(searchItems, paymentMethodSearchItemSelectionCallback);
    }

    @Override
    public void showPluginOptions(@NonNull final List<PaymentMethodPlugin> items, String position) {

        final List<PaymentMethodInfo> toInsert = new ArrayList<>();

        for (PaymentMethodPlugin plugin : items) {
            if (position.equalsIgnoreCase(plugin.displayOrder())) {
                toInsert.add(plugin.getPaymentMethodInfo(this));
            }
        }

        final PaymentMethodSearchItemAdapter adapter =
            (PaymentMethodSearchItemAdapter) mSearchItemsRecyclerView.getAdapter();
        final List<PaymentMethodSearchViewController> customViewControllers =
            createPluginItemsViewControllers(toInsert);
        adapter.addItems(customViewControllers);
        adapter.notifyItemInserted();
    }

    @Override
    public void showError(MercadoPagoError error, String requestOrigin) {
        if (error.isApiException()) {
            showApiException(error.getApiException(), requestOrigin);
        } else {
            ErrorUtil.startErrorActivity(this, error, mPublicKey);
        }
    }

    @Override
    public void onBackPressed() {
        Intent returnIntent = new Intent();
        returnIntent.putExtra("discount", JsonUtil.getInstance().toJson(configuration.getDiscount()));
        returnIntent.putExtra(EXTRA_PAYMENT_METHODS, presenter.getPaymentMethodSearch());
        setResult(RESULT_CANCELED, returnIntent);
        finish();
        overrideTransitionOut();
    }


    @Override
    protected void onResume() {
        mActivityActive = true;
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        mActivityActive = false;
        presenter.detachView();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        mActivityActive = false;
        super.onPause();
    }

    @Override
    protected void onStop() {
        mActivityActive = false;
        super.onStop();
    }

    @Override
    public void onTimeChanged(String timeToShow) {
        mTimerTextView.setText(timeToShow);
    }

    @Override
    public void onFinish() {
        setResult(MercadoPagoCheckout.TIMER_FINISHED_RESULT_CODE);
        finish();
    }

    @Override
    public void collectPayerInformation() {
        new MercadoPagoComponents.Activities.PayerInformationActivityBuilder()
            .setActivity(this)
            .setMerchantPublicKey(mPublicKey)
            .setPayerAccessToken(mPrivateKey)
            .startActivity();
        overrideTransitionIn();
    }

    //### HOOKS ######################

    public void resolveHook1Request(int resultCode) {
        if (resultCode == RESULT_OK) {
            presenter.onHookContinue();
        } else {
            overrideTransitionOut();
            presenter.onHookReset();
        }
    }

    @Override
    public void showHook(final Hook hook, final int code) {
        startActivityForResult(HookActivity.getIntent(this, hook), code);
        overrideTransitionIn();
    }

    @Override
    public void showDetailDialog(@NonNull final Discount discount, @NonNull final Campaign campaign) {
        DiscountDetailDialog.showDialog(discount, campaign, getSupportFragmentManager());
    }

    @Override
    public void showDetailDialog(@NonNull final CouponDiscount discount, @NonNull final Campaign campaign) {
        //TODO - Other dialog.
        DiscountDetailDialog.showDialog(null, null, getSupportFragmentManager());
    }

    @Override
    public void showDiscountInputDialog() {
        //TODO - Other dialog.
        DiscountDetailDialog.showDialog(null, null, getSupportFragmentManager());
    }

    @Override
    public void showAmount(@Nullable Discount discount, @Nullable Campaign campaign, final BigDecimal totalAmount,
        final Site site) {
        //TODO refactor -> should be not null // Quick and dirty implementation.
        if (discount == null) {
            amountView.show(totalAmount, site);
        } else {
            amountView.show(discount, campaign, totalAmount, site);
        }

        amountView.setOnClickListener(presenter);
    }

    @Override
    public void startDiscountFlow(final CheckoutPreference preference) {
        // TODO implement discount flow
    }
}
