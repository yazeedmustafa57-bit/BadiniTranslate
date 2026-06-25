# Keep data classes for Gson serialization
-keepclassmembers class com.badini.translate.data.db.DictionaryEntry {
    *;
}

# Keep Room entities
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.* <fields>;
}
