package com.ricordella.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppointmentTest {

    @Test
    fun fillsTheDefaultTemplate() {
        assertEquals(
            "Ciao Mario, ti ricordo il tuo appuntamento \"Taglio\" di lunedì 12 ottobre alle 10:30. A presto!",
            AppointmentMessage.fill(AppointmentMessage.DEFAULT_TEMPLATE, "Mario", "Taglio", "lunedì 12 ottobre", "10:30"),
        )
    }

    @Test
    fun allDayDropsTheTime() {
        assertEquals(
            "Ciao Mario, ti ricordo il tuo appuntamento \"Taglio\" di lunedì 12 ottobre. A presto!",
            AppointmentMessage.fill(AppointmentMessage.DEFAULT_TEMPLATE, "Mario", "Taglio", "lunedì 12 ottobre", null),
        )
    }

    @Test
    fun dataIsAlwaysThere() {
        assertEquals("Ciao Anna!\nTaglio: 12/10 10:30", AppointmentMessage.fill("Ciao {nome}!", "Anna", "Taglio", "12/10", "10:30"))
        assertEquals("Ciao!\nTaglio: 12/10", AppointmentMessage.fill("Ciao {nome}!", "", "Taglio", "12/10", null))
    }

    @Test
    fun targetsNeedPhoneOrEmail() {
        val info = AppointmentInfo(
            contacts = listOf(AppointmentContact("A", phone = "333"), AppointmentContact("B", email = "b@x.it")),
            channels = setOf(MessageChannel.WHATSAPP, MessageChannel.EMAIL),
        )
        assertEquals(listOf("A" to MessageChannel.WHATSAPP, "B" to MessageChannel.EMAIL), info.targets.map { it.first.name to it.second })
        // Solo email ma canale Email spento: l'email arriva lo stesso.
        val onlyPhones = info.copy(channels = setOf(MessageChannel.WHATSAPP, MessageChannel.SMS))
        assertEquals(listOf("A" to MessageChannel.WHATSAPP, "A" to MessageChannel.SMS, "B" to MessageChannel.EMAIL), onlyPhones.targets.map { it.first.name to it.second })
    }
}
