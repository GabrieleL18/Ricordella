package com.ricordella.app.domain.model

import com.ricordella.app.core.i18n.tr

/**
 * Cosa ha senso registrare nel registro di una cosa, secondo il suo tipo: il rifornimento solo
 * per i veicoli, la "riparazione" per l'elettronica, il "rinnovo" per i documenti, con i
 * suggerimenti giusti (nessun tagliando per la lavatrice).
 */
data class ExpenseProfile(
    /** Tipi di voce disponibili, nell'ordine in cui proporli. */
    val kinds: List<ExpenseKind>,
    /** Come si chiama qui un intervento ([ExpenseKind.SERVICE]). */
    val serviceLabel: String,
    val sectionTitle: String,
    val serviceTitles: List<String>,
    val otherTitles: List<String>,
    /** Mesi proposti per ricordare il prossimo intervento. */
    val nextMonths: Int,
    /** Km proposti per il prossimo intervento, solo per i veicoli. */
    val nextKm: Int? = null,
    val emptyText: String,
) {
    fun label(kind: ExpenseKind): String = when (kind) {
        ExpenseKind.SERVICE -> serviceLabel
        ExpenseKind.FUEL -> tr("Rifornimento")
        ExpenseKind.OTHER -> tr("Altra spesa")
    }

    fun suggestions(kind: ExpenseKind): List<String> = when (kind) {
        ExpenseKind.SERVICE -> serviceTitles
        ExpenseKind.FUEL -> emptyList()
        ExpenseKind.OTHER -> otherTitles
    }
}

object ExpenseProfiles {

    fun of(kind: ItemKind?, group: ItemGroup): ExpenseProfile = when (group) {
        ItemGroup.VEHICLES -> vehicle(kind)
        ItemGroup.HOME -> home(kind)
        ItemGroup.ELECTRONICS -> electronics(kind)
        ItemGroup.DOCUMENTS -> documents(kind)
        ItemGroup.GENERIC -> generic()
    }

    private fun vehicle(kind: ItemKind?) = ExpenseProfile(
        kinds = listOf(ExpenseKind.SERVICE, ExpenseKind.FUEL, ExpenseKind.OTHER),
        serviceLabel = tr("Intervento"),
        sectionTitle = tr("Manutenzione e spese"),
        serviceTitles = when (kind) {
            ItemKind.MOTORBIKE, ItemKind.SCOOTER ->
                listOf(tr("Tagliando"), tr("Pneumatici"), tr("Catena"), tr("Freni"), tr("Batteria"), tr("Revisione"))
            else -> listOf(tr("Tagliando"), tr("Cambio olio"), tr("Pneumatici"), tr("Freni"), tr("Batteria"), tr("Revisione"), tr("Carrozzeria"))
        },
        otherTitles = listOf(tr("Assicurazione"), tr("Bollo"), tr("Parcheggio"), tr("Pedaggi"), tr("Lavaggio"), tr("Accessori")),
        nextMonths = 12,
        nextKm = when (kind) {
            ItemKind.SCOOTER -> 5_000
            ItemKind.MOTORBIKE -> 10_000
            else -> 15_000
        },
        emptyText = tr("Segna tagliandi, rifornimenti, bollo e assicurazione: ti mostro quanto spendi, il consumo e il costo al km."),
    )

    private fun home(kind: ItemKind?): ExpenseProfile {
        val (titles, months) = when (kind) {
            ItemKind.WASHING_MACHINE -> listOf(tr("Pulizia filtro"), tr("Pulizia guarnizione"), tr("Decalcificazione"), tr("Riparazione")) to 3
            ItemKind.DISHWASHER -> listOf(tr("Pulizia filtro"), tr("Sale e brillantante"), tr("Decalcificazione"), tr("Riparazione")) to 1
            ItemKind.FRIDGE -> listOf(tr("Pulizia"), tr("Sbrinamento"), tr("Guarnizioni"), tr("Riparazione")) to 3
            ItemKind.OVEN -> listOf(tr("Pulizia"), tr("Guarnizione"), tr("Riparazione")) to 6
            ItemKind.AIR_CONDITIONER -> listOf(tr("Pulizia filtri"), tr("Sanificazione"), tr("Ricarica gas"), tr("Riparazione")) to 6
            ItemKind.BOILER -> listOf(tr("Manutenzione caldaia"), tr("Controllo fumi"), tr("Riparazione")) to 12
            ItemKind.TV -> listOf(tr("Riparazione"), tr("Pulizia"), tr("Supporto a parete")) to 12
            else -> listOf(tr("Manutenzione"), tr("Pulizia"), tr("Riparazione"), tr("Controllo")) to 12
        }
        return ExpenseProfile(
            kinds = listOf(ExpenseKind.SERVICE, ExpenseKind.OTHER),
            serviceLabel = tr("Intervento"),
            sectionTitle = tr("Manutenzione e spese"),
            serviceTitles = titles,
            otherTitles = listOf(tr("Ricambi"), tr("Accessori"), tr("Detersivi"), tr("Installazione"), tr("Estensione garanzia")),
            nextMonths = months,
            emptyText = tr("Segna pulizie, interventi e ricambi: ti ricordo il prossimo e ti mostro quanto spendi."),
        )
    }

    private fun electronics(kind: ItemKind?) = ExpenseProfile(
        kinds = listOf(ExpenseKind.SERVICE, ExpenseKind.OTHER),
        serviceLabel = tr("Riparazione"),
        sectionTitle = tr("Riparazioni e spese"),
        serviceTitles = when (kind) {
            ItemKind.SMARTPHONE, ItemKind.TABLET -> listOf(tr("Schermo"), tr("Batteria"), tr("Riparazione"), tr("Pulizia"))
            ItemKind.COMPUTER -> listOf(tr("Riparazione"), tr("Upgrade"), tr("Batteria"), tr("Pulizia ventole"))
            ItemKind.CONSOLE -> listOf(tr("Riparazione"), tr("Pulizia ventole"), tr("Controller"))
            else -> listOf(tr("Riparazione"), tr("Batteria"), tr("Pulizia"))
        },
        otherTitles = listOf(tr("Accessori"), tr("Custodia"), tr("Assicurazione"), tr("Abbonamento"), tr("Software")),
        nextMonths = 12,
        emptyText = tr("Segna riparazioni, accessori e abbonamenti: ti mostro quanto ti costa ogni anno."),
    )

    private fun documents(kind: ItemKind?) = ExpenseProfile(
        kinds = listOf(ExpenseKind.SERVICE, ExpenseKind.OTHER),
        serviceLabel = tr("Rinnovo"),
        sectionTitle = tr("Rinnovi e spese"),
        serviceTitles = when (kind) {
            ItemKind.CONTRACT -> listOf(tr("Rinnovo"), tr("Variazione"), tr("Disdetta"))
            ItemKind.WARRANTY_DOCUMENT -> listOf(tr("Pratica di assistenza"), tr("Estensione"))
            else -> listOf(tr("Rinnovo"), tr("Duplicato"), tr("Foto tessera"))
        },
        otherTitles = listOf(tr("Marca da bollo"), tr("Diritti di segreteria"), tr("Spedizione"), tr("Raccomandata")),
        nextMonths = if (kind == ItemKind.PERSONAL_DOCUMENT) 120 else 12,
        emptyText = tr("Segna rinnovi, marche da bollo e diritti: ti ricordo la prossima scadenza."),
    )

    private fun generic() = ExpenseProfile(
        kinds = listOf(ExpenseKind.SERVICE, ExpenseKind.OTHER),
        serviceLabel = tr("Intervento"),
        sectionTitle = tr("Manutenzione e spese"),
        serviceTitles = listOf(tr("Manutenzione"), tr("Pulizia"), tr("Riparazione"), tr("Controllo")),
        otherTitles = listOf(tr("Ricambi"), tr("Accessori"), tr("Assicurazione")),
        nextMonths = 12,
        emptyText = tr("Segna interventi e spese: ti mostro quanto spendi ogni anno."),
    )
}
