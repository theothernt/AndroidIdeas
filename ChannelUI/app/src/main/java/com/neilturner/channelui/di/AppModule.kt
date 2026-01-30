package com.neilturner.channelui.di

import com.neilturner.channelui.data.ChannelProvider
import com.neilturner.channelui.data.ChannelProviderImpl
import com.neilturner.channelui.ui.viewmodel.ChannelViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<ChannelProvider> { ChannelProviderImpl() }
    viewModel { ChannelViewModel(get()) }
}
