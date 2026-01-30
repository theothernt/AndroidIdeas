package com.neilturner.persistentlist.di

import androidx.room.Room
import com.neilturner.persistentlist.data.FileRepository
import com.neilturner.persistentlist.data.SmbRepository
import com.neilturner.persistentlist.data.SmbRepositoryImpl
import com.neilturner.persistentlist.data.db.AppDatabase
import com.neilturner.persistentlist.ui.main.MainViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "persistent-list-db"
        ).fallbackToDestructiveMigration()
         .build()
    }
    single { get<AppDatabase>().fileDao() }
    single<SmbRepository> { SmbRepositoryImpl() }
    single { FileRepository(get(), get()) }
    viewModel { MainViewModel(get()) }
}