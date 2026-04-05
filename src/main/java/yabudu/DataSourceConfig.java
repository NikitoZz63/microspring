package yabudu;

import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;

public class DataSourceConfig {

    public static DataSource create() {

        JdbcDataSource ds = new JdbcDataSource();

        ds.setURL("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
        ds.setUser("user");
        ds.setPassword("");

        return ds;

    }


}
