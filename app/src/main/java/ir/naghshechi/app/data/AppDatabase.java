package ir.naghshechi.app.data;
import androidx.room.Database;
import androidx.room.RoomDatabase;
@Database(entities = {StoredDocument.class}, version = 1, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase { public abstract DocumentDao documents(); }
