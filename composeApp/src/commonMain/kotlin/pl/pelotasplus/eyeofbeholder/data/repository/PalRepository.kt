package pl.pelotasplus.eyeofbeholder.data.repository

import pl.pelotasplus.eyeofbeholder.data.model.Pal

interface PalRepository {
    fun loadPal(name: String): Pal
}

class PalRepositoryImpl(
) : PalRepository {
    override fun loadPal(name: String): Pal {
        return Pal(
            name = name,
            colors = emptyList()
        )
    }
}
