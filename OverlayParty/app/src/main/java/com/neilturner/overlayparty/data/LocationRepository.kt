package com.neilturner.overlayparty.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class LocationRepository {
    private val scenicLocations = listOf(
        "Grand Canyon, USA - A immense steep-sided canyon carved by the Colorado River in Arizona, known for its visually overwhelming size and colorful landscape.",
        "Great Barrier Reef, Australia - The world's largest coral reef system composed of over 2,900 individual reefs.",
        "Machu Picchu, Peru - A 15th-century Inca citadel situated on a mountain ridge 2,430 metres above sea level, often referred to as the 'Lost City of the Incas'.",
        "Aurora Borealis, Iceland - A natural light display in the Earth's sky.",
        "Victoria Falls, Zambia/Zimbabwe - A waterfall on the Zambezi River in southern Africa, providing habitat for several unique species of plants and animals.",
        "Mount Everest, Nepal - The Earth's highest mountain above sea level.",
        "Petra, Jordan - A historical and archaeological city in southern Jordan, famous for its rock-cut architecture and water conduit system, established around 312 BC.",
        "Taj Mahal, India - An ivory-white marble mausoleum on the southern bank of the river Yamuna in the Indian city of Agra, commissioned in 1632.",
        "Santorini, Greece - An island in the southern Aegean Sea.",
        "Banff National Park, Canada - Canada's oldest national park, established in 1885, known for its rocky mountain peaks, turquoise glacial lakes, and abundant wildlife."
    )

    fun getLocationStream(): Flow<String> = flow {
        var index = 0
        while (true) {
            emit(scenicLocations[index])
            index = (index + 1) % scenicLocations.size
            delay(8_000)
        }
    }
}
