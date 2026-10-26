package ir.naghshechi.app.data;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
@Dao
public interface DocumentDao {
    @Query("SELECT body FROM documents WHERE id = :id LIMIT 1") String read(String id);
    @Insert(onConflict = OnConflictStrategy.REPLACE) void write(StoredDocument document);
}
