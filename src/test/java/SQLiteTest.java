import org.junit.Test;

import java.sql.*;

public class SQLiteTest {

    private static final String DBNAME = "jdbc:sqlite:test.db";

    @Test
    public void test() {
        try (Connection conn = DriverManager.getConnection(DBNAME)) {
            // テーブルを作る。
            // 実行するとgetConnection時に指定したtest.dbという名前のファイルが出来る。
            Statement stmt = conn.createStatement();
            stmt.execute("drop table if exists contract;");
            stmt.execute("create table if not exists contract (name text primary key, blocknumber integer);");

            stmt.execute("begin");
            stmt.execute("insert into contract values ('hoge', 1);");
            stmt.execute("commit");

            stmt.execute("begin");
            stmt.execute("insert into contract values ('hoge2', 2);");
            stmt.execute("rollback");

            stmt.execute("update contract set blocknumber = 11 where name == 'hoge';");

            ResultSet rs = stmt.executeQuery("select * from contract;");
            while (rs.next()) {
                String name = rs.getString(1);
                int blocknumber = rs.getInt(2);
                System.out.println(String.format("%s,%d", name, blocknumber));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Test(expected = SQLException.class)
    public void test2() {
        try (Connection conn = DriverManager.getConnection(DBNAME)) {
            Statement stmt = conn.createStatement();
            stmt.execute("drop table if exists contract;");
            stmt.execute("create table if not exists contract (name text primary key, blocknumber integer);");

            stmt.execute("begin");
            stmt.execute("insert into contract values ('hoge', 1);");

            stmt.execute("begin");
            stmt.execute("insert into contract values ('hoge', 2);");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    @Test(expected = SQLException.class)
    public void test3() {
        try (Connection conn = DriverManager.getConnection(DBNAME)) {
            Statement stmt = conn.createStatement();
            stmt.execute("drop table if exists contract;");
            stmt.execute("create table if not exists contract (name text primary key, blocknumber integer);");

            stmt.execute("rollback");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
