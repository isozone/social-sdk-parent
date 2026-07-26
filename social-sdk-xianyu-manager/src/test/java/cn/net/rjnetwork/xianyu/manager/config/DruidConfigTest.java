package cn.net.rjnetwork.xianyu.manager.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DruidConfigTest {

    @Test
    void validateDialectMatchesUrl_allowsMatchingJdbcUrls() {
        assertDoesNotThrow(() -> validate("sqlite", "jdbc:sqlite:./data/xianyu-manager.db"));
        assertDoesNotThrow(() -> validate("mysql", "jdbc:mysql://localhost:3306/xianyu_manager"));
        assertDoesNotThrow(() -> validate("postgres", "jdbc:postgresql://localhost:5432/xianyu_manager"));
    }

    @Test
    void validateDialectMatchesUrl_rejectsSqlitePragmaOnMysqlMisconfiguration() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validate("sqlite", "jdbc:mysql://localhost:3306/xianyu_manager"));

        assertTrue(ex.getMessage().contains("Database dialect mismatch"));
        assertTrue(ex.getMessage().contains("bitefu.wall.db-type=sqlite"));
        assertTrue(ex.getMessage().contains("jdbc:mysql://localhost:3306/xianyu_manager"));
    }

    private static void validate(String dialect, String url) throws Exception {
        Method method = DruidConfig.class.getDeclaredMethod("validateDialectMatchesUrl", String.class, String.class);
        method.setAccessible(true);
        try {
            method.invoke(new DruidConfig(), dialect, url);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }
    }
}
