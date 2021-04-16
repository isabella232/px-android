package com.mercadopago.android.px.internal.mappers

import com.mercadopago.android.px.internal.datasource.CustomOptionIdSolver
import com.mercadopago.android.px.model.internal.DisabledPaymentMethod
import com.mercadopago.android.px.model.internal.OneTapItem
import com.mercadopago.android.px.internal.repository.PayerPaymentMethodKey as Key

internal class OneTapItemToDisabledPaymentMethodMapper {
    fun map(value: List<OneTapItem>): HashMap<Key, DisabledPaymentMethod> {
        return hashMapOf<Key, DisabledPaymentMethod>().also { map ->
            value.forEach { oneTapItem ->
                oneTapItem.getApplications().forEach { application ->
                    application.takeIf { !oneTapItem.isOfflineMethods && !it.status.isEnabled }?.let {
                        map[Key(CustomOptionIdSolver.getByApplication(oneTapItem, it), it.paymentMethod.type)] =
                            DisabledPaymentMethod(it.paymentMethod.id)
                    }
                }
            }
        }
    }
}