package pl.pelotasplus.eyeofbeholder

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform