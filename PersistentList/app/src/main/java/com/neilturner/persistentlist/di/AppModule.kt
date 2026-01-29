package com.neilturner.persistentlist.di

import com.neilturner.persistentlist.data.SmbRepository
import com.neilturner.persistentlist.data.SmbRepositoryImpl
import com.neilturner.persistentlist.ui.main.MainViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<SmbRepository> { SmbRepositoryImpl() }
    viewModel { MainViewModel(get()) }
}
