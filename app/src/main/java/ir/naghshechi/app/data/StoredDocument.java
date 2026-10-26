package ir.naghshechi.app.data;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
@Entity(tableName = "documents")
public class StoredDocument {
    @PrimaryKey @NonNull public String id;
    @NonNull public String body;
    public StoredDocument(@NonNull String id, @NonNull String body) { this.id = id; this.body = body; }
}
