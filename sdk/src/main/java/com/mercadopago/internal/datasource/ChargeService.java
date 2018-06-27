package com.mercadopago.internal.datasource;

import android.support.annotation.NonNull;
import com.mercadopago.internal.repository.ChargeRepository;
import com.mercadopago.internal.repository.PaymentSettingRepository;
import com.mercadopago.internal.repository.UserSelectionRepository;
import com.mercadopago.model.PaymentMethod;
import com.mercadopago.model.commission.ChargeRule;
import com.mercadopago.model.commission.PaymentMethodRule;
import java.math.BigDecimal;

public class ChargeService implements ChargeRepository {

    @NonNull private final UserSelectionRepository userSelectionRepository;
    @NonNull private final PaymentSettingRepository configuration;

    public ChargeService(@NonNull final UserSelectionRepository userSelectionRepository,
        @NonNull final PaymentSettingRepository configuration) {
        this.userSelectionRepository = userSelectionRepository;
        this.configuration = configuration;
    }

    @NonNull
    private BigDecimal charges(@NonNull final Iterable<? extends ChargeRule> rules) {
        BigDecimal chargeAmount = BigDecimal.ZERO;
        for (final ChargeRule rule : rules) {
            if (rule.shouldBeTriggered(this)) {
                chargeAmount = chargeAmount.add(rule.charge());
            }
        }
        return chargeAmount;
    }

    @Override
    @NonNull
    public BigDecimal getChargeAmount() {
        return charges(configuration.chargeRules());
    }

    @Override
    public boolean shouldApply(@NonNull final PaymentMethodRule rule) {
        final PaymentMethod paymentMethod = userSelectionRepository.getPaymentMethod();
        final boolean notNull = paymentMethod != null;
        return notNull && (rule.getValue().equalsIgnoreCase(paymentMethod.getId()) ||
            rule.getValue().equalsIgnoreCase(paymentMethod.getPaymentTypeId()));
    }
}
