package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities =
    [
      ProductEntity::class,
      CartItemEntity::class,
      OrderEntity::class,
      StockMovementEntity::class,
      NotificationLogEntity::class,
    ],
  version = 1,
  exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun productDao(): ProductDao

  abstract fun cartDao(): CartDao

  abstract fun orderDao(): OrderDao

  abstract fun stockMovementDao(): StockMovementDao

  abstract fun notificationDao(): NotificationDao

  companion object {
    @Volatile private var instance: AppDatabase? = null

    fun getInstance(context: Context): AppDatabase {
      return instance
        ?: synchronized(this) {
          instance
            ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "odoomart_cache.db",
              )
              .fallbackToDestructiveMigration(dropAllTables = true)
              .build()
              .also { instance = it }
        }
    }
  }
}
