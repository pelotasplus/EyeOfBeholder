package pl.pelotasplus.eyeofbeholder.di

import org.koin.core.module.Module

/** What each platform has to supply that no shared code can: where saves live. */
expect fun platformModule(): Module
