package com.ricordella.app.data.local.database

import com.ricordella.app.domain.model.Category
import com.ricordella.app.domain.model.ItemKind

/** Categorie predefinite di "Cose". Hanno ID stabili così backup e ripristino restano coerenti. */
object BuiltInCategories {

    private val names = linkedMapOf(
        ItemKind.CAR to "Auto",
        ItemKind.MOTORBIKE to "Moto",
        ItemKind.SCOOTER to "Scooter",
        ItemKind.OTHER_VEHICLE to "Altro veicolo",
        ItemKind.WASHING_MACHINE to "Lavatrice",
        ItemKind.DISHWASHER to "Lavastoviglie",
        ItemKind.FRIDGE to "Frigorifero",
        ItemKind.OVEN to "Forno",
        ItemKind.AIR_CONDITIONER to "Condizionatore",
        ItemKind.BOILER to "Caldaia",
        ItemKind.TV to "TV",
        ItemKind.OTHER_HOME to "Altro per la casa",
        ItemKind.SMARTPHONE to "Smartphone",
        ItemKind.TABLET to "Tablet",
        ItemKind.COMPUTER to "Computer",
        ItemKind.CONSOLE to "Console",
        ItemKind.CAMERA to "Fotocamera",
        ItemKind.OTHER_ELECTRONICS to "Altra elettronica",
        ItemKind.PERSONAL_DOCUMENT to "Documento personale",
        ItemKind.CONTRACT to "Contratto",
        ItemKind.WARRANTY_DOCUMENT to "Garanzia",
        ItemKind.OTHER_DOCUMENT to "Altro documento",
        ItemKind.EQUIPMENT to "Attrezzatura",
        ItemKind.OBJECT to "Oggetto",
        ItemKind.OTHER to "Altro",
    )

    val all: List<Category> = names.entries.mapIndexed { index, (kind, name) ->
        Category(
            id = idFor(kind),
            name = name,
            itemGroup = kind.group,
            kind = kind,
            isBuiltIn = true,
            sortOrder = index,
        )
    }

    fun idFor(kind: ItemKind): String = "builtin-" + kind.name.lowercase()
}
