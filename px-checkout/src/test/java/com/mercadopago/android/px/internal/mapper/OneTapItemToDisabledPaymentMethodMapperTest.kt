package com.mercadopago.android.px.internal.mapper

import com.mercadopago.android.px.internal.datasource.CustomOptionIdSolver
import com.mercadopago.android.px.internal.mappers.OneTapItemToDisabledPaymentMethodMapper
import com.mercadopago.android.px.internal.repository.PayerPaymentMethodKey
import com.mercadopago.android.px.model.CardMetadata
import com.mercadopago.android.px.model.StatusMetadata
import com.mercadopago.android.px.model.internal.Application
import com.mercadopago.android.px.model.internal.DisabledPaymentMethod
import com.mercadopago.android.px.model.internal.OneTapItem
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.internal.matchers.apachecommons.ReflectionEquals
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.mock

@RunWith(MockitoJUnitRunner::class)
class OneTapItemToDisabledPaymentMethodMapperTest {

    private lateinit var oneTapItems: List<OneTapItem>

    @Test
    fun whenMapOneTapItemToDisabledPaymentMethodWithoutOfflineMethod() {
        val cardOneTapItem = makeCardOneTapItem()
        val cardApplication = cardOneTapItem.getApplications()[0]

        oneTapItems = listOf(cardOneTapItem)

        val expectedKey = PayerPaymentMethodKey(
            CustomOptionIdSolver.getByApplication(cardOneTapItem, cardApplication),
            cardApplication.paymentMethod.type)
        val expectedDisabledMethod = DisabledPaymentMethod(cardApplication.paymentMethod.id)

        val actual = OneTapItemToDisabledPaymentMethodMapper().map(oneTapItems)

        Assert.assertTrue(actual.size == 1)
        Assert.assertTrue(actual.containsKey(expectedKey))
        Assert.assertTrue(ReflectionEquals(expectedDisabledMethod).matches(actual[expectedKey]))
    }

    @Test
    fun whenMapOneTapItemToDisabledPaymentMethodWithOfflineMethod() {
        val cardOneTapItem = makeCardOneTapItem()
        val cardApplication = cardOneTapItem.getApplications()[0]

        oneTapItems = listOf(makeCardOneTapItem(), makeOfflineOneTapItem())

        val expectedKey = PayerPaymentMethodKey(
            CustomOptionIdSolver.getByApplication(cardOneTapItem, cardApplication),
            cardApplication.paymentMethod.type)
        val expectedDisabledMethod = DisabledPaymentMethod(cardApplication.paymentMethod.id)

        val actual = OneTapItemToDisabledPaymentMethodMapper().map(oneTapItems)

        Assert.assertTrue(actual.size == 1)
        Assert.assertTrue(actual.containsKey(expectedKey))
        Assert.assertTrue(ReflectionEquals(expectedDisabledMethod).matches(actual[expectedKey]))
    }

    @Test
    fun whenMapOneTapItemToDisabledPaymentMethodWithOnlyOfflineMethod() {
        oneTapItems = listOf(makeOfflineOneTapItem())

        val actual = OneTapItemToDisabledPaymentMethodMapper().map(oneTapItems)

        Assert.assertTrue(actual.isEmpty())
    }

    private fun makeCardOneTapItem(): OneTapItem {
        val card: CardMetadata = mock {
            on { id }.thenReturn("123456")
        }
        val cardApplicationStatus: StatusMetadata = mock {
            on { isEnabled }.thenReturn(false)
        }
        val cardApplicationPaymentMethod: Application.PaymentMethod = mock {
            on { id }.thenReturn("visa")
            on { type }.thenReturn("credit_card")
        }
        val cardApplication: Application = mock {
            on { paymentMethod }.thenReturn(cardApplicationPaymentMethod)
            on { status }.thenReturn(cardApplicationStatus)
        }

        return mock {
            on { isCard }.thenReturn(true)
            on { this.card }.thenReturn(card)
            on { getApplications() }.thenReturn(listOf(cardApplication))
        }
    }

    private fun makeOfflineOneTapItem(): OneTapItem {
        val offlineApplication: Application = mock()

        return mock {
            on { isOfflineMethods }.thenReturn(true)
            on { getApplications() }.thenReturn(listOf(offlineApplication))
        }
    }
}