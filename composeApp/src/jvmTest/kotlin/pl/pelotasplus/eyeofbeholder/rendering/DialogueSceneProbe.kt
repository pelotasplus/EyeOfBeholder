package pl.pelotasplus.eyeofbeholder.rendering

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Cps
import pl.pelotasplus.eyeofbeholder.data.model.DialogueScene
import pl.pelotasplus.eyeofbeholder.data.model.DialogueTextId
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.PlayField
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DcrRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DialogueTextRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.FontRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VcnRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VmpRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ViewConeRepositoryImpl
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/** Renders the priest over the play field so it can be looked at. */
class DialogueSceneProbe {

    @Test
    fun `draw the darkmoon priest over the view`() = runBlocking {
        val resources = ResourceRepositoryImpl()
        val pal = PalRepositoryImpl(resources)
        val cps = CpsRepositoryImpl(resources)
        val repository = ViewConeRepositoryImpl(
            infRepository = InfRepositoryImpl(
                resources, MazRepositoryImpl(resources), VmpRepositoryImpl(resources),
                VcnRepositoryImpl(resources), pal, cps, DecRepositoryImpl(resources),
            ),
            itemsRepository = ItemsRepositoryImpl(resources),
            cpsRepository = cps,
            dcrRepository = DcrRepositoryImpl(resources),
        )

        val inf = repository.loadLevel("LEVEL6.INF").getOrThrow()
        val sublevel = inf.subLevels[0]
        val viewPort = repository.renderPosition(
            items = inf.items,
            monsters = inf.monsterInstances,
            sublevel = sublevel,
            playerX = 10,
            playerY = 2,
            direction = Direction.NORTH,
        ).getOrThrow()

        val priest = cps.loadCps("SOUT2.CPS").getOrThrow()
        val font = FontRepositoryImpl(resources).loadFont("FONT6.FNT").getOrThrow()
        val text = DialogueTextRepositoryImpl(resources).text(DialogueTextId(28)).getOrThrow()

        // DisplayPicture(sout2, x = 20, y = 0): x counts eight-pixel columns
        val field = PlayField(
            background = cps.loadCps("PLAYFLD.CPS").getOrThrow(),
            decorations = cps.loadCps("DECORATE.CPS").getOrThrow(),
            palette = sublevel.palette,
            font = font,
        ).render(
            viewPort = viewPort,
            direction = Direction.NORTH,
            dialogue = DialogueScene.layout(
                frame = cps.loadCps("BORDER.CPS").getOrThrow(),
                portrait = DialogueScene.Picture(
                    cps = priest,
                    sourceLeft = 160,
                    sourceTop = 0,
                    width = Cps.PORTRAIT_WIDTH,
                    height = Cps.PORTRAIT_HEIGHT,
                    left = DialogueScene.PORTRAIT_LEFT,
                    top = DialogueScene.PORTRAIT_TOP,
                ),
                text = text.first,
                buttonLabels = listOf("leave", "attack"),
                font = font,
            ),
        )

        val image = BufferedImage(PlayField.WIDTH, PlayField.HEIGHT, BufferedImage.TYPE_INT_ARGB)
        field.getRows().forEachIndexed { y, row ->
            row.forEachIndexed { x, rgb ->
                val argb = if (rgb.transparent) 0
                else (0xFF shl 24) or (rgb.red shl 16) or (rgb.green shl 8) or rgb.blue
                image.setRGB(x, y, argb)
            }
        }

        val out = File("build/golden-failures/dialogue-priest.png")
        out.parentFile.mkdirs()
        ImageIO.write(image, "png", out)
        println("PRIEST written to ${out.absolutePath}")
    }
}
