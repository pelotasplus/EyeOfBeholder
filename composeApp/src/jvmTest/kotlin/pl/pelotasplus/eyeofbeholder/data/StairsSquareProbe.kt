package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
import pl.pelotasplus.eyeofbeholder.data.model.getWall
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VcnRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VmpRepositoryImpl
import kotlin.test.Test

class StairsSquareProbe {

    @Test
    fun `what the squares around the level 5 stairs are made of`() = runBlocking {
        val resources = ResourceRepositoryImpl()
        val inf = InfRepositoryImpl(
            resources, MazRepositoryImpl(resources), VmpRepositoryImpl(resources),
            VcnRepositoryImpl(resources), PalRepositoryImpl(resources),
            CpsRepositoryImpl(resources), DecRepositoryImpl(resources),
        ).loadInf("LEVEL5.INF", ItemsRepositoryImpl(resources).loadItems().getOrThrow()).getOrThrow()

        val maz = inf.subLevels[0].maz
        (4..8).forEach { y ->
            val square = maz[10, y]
            println(
                "10x$y " + WallSide.entries.joinToString { "${it.name.first()}=${square.getWall(it)}" }
            )
        }
    }
}
