package org.example.server;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseConfigTest {

    // helper to set the private static field
    private static void setStaticDataSource(Object value) throws Exception {
        Field f = DatabaseConfig.class.getDeclaredField("dataSource");
        f.setAccessible(true);
        f.set(null, value);
    }

    @Test
    void testGetDataSource_notNull() {
        assertNotNull(DatabaseConfig.getDataSource(), "getDataSource() should not return null");
    }

    @Test
    void testClose_invokesDataSourceClose() throws Exception {
        Field f = DatabaseConfig.class.getDeclaredField("dataSource");
        f.setAccessible(true);
        Object original = f.get(null);

        HikariDataSource mockDs = Mockito.mock(HikariDataSource.class);
        try {
            // inject mock
            f.set(null, mockDs);

            // call close and verify it delegates to the data source
            DatabaseConfig.close();
            Mockito.verify(mockDs).close();
        } finally {
            // restore original datasource to avoid test side-effects
            f.set(null, original);
        }
    }
}