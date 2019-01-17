import org.ethereum.util.ByteUtil;
import org.junit.Test;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.Assert.assertTrue;

public class RocksDbTest {
    @Test
    public void test() throws RocksDBException {
        Options options = new Options();
        options.setCreateIfMissing(true);
        options.setIncreaseParallelism(2);
        final Path dbPath = Paths.get("rocksdbtest");
        RocksDB db = RocksDB.open(options, dbPath.toString());
        final byte[] key = ByteUtil.hexStringToBytes("1234");
        final byte[] value = ByteUtil.hexStringToBytes("5678");
        db.put(key, value);
        byte[] ret = db.get(key);
        assertTrue(Arrays.equals(value, ret));
        db.close();
        options.close();
    }
}
