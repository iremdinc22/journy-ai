package com.journy.backend.feedback;

import com.journy.backend.feedback.model.TasteFeedbackAction;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** Disposable H2 compatibility check; this is not PostgreSQL execution evidence. */
class FeedbackMigrationValidationTest {
    @Test void v5PreservesLegacyRowsAndEnforcesUserScopedKeys() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:migration_" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "sa", "");
             var sql = connection.createStatement()) {
            sql.execute("""
                    create table taste_feedback (
                        id varchar(255) primary key, user_id varchar(255) not null,
                        action varchar(255) not null,
                        constraint taste_feedback_action_check check (action in
                        ('SAVED','REMOVED','VISITED','SKIPPED','NOT_INTERESTED',
                         'TOO_EXPENSIVE','TOO_FAR','ALREADY_VISITED','REPLACED')))
                    """);
            sql.execute("insert into taste_feedback values ('legacy1','A','REMOVED'), ('legacy2','A','REPLACED')");
            var resource = new ClassPathResource("db/migration/V5__feedback_event_identity.sql");
            ScriptUtils.executeSqlScript(connection, resource);
            ScriptUtils.executeSqlScript(connection, resource); // Safe manual reapplication.
            try (var rows = sql.executeQuery("select * from taste_feedback order by id")) {
                for (String action : new String[]{"REMOVED", "REPLACED"}) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("action")).isEqualTo(action);
                    for (String field : new String[]{"identity_verified", "source", "context_id", "event_key"})
                        assertThat(rows.getObject(field)).isNull();
                }
                assertThat(rows.next()).isFalse();
            }
            // All old and new Java actions are accepted by the migration's check.
            for (var action : TasteFeedbackAction.values())
                sql.execute("insert into taste_feedback(id,user_id,action) values ('enum_" + action + "','A','" + action + "')");
            sql.execute("insert into taste_feedback(id,user_id,action,event_key) values ('newA','A','SAVED','same')");
            sql.execute("insert into taste_feedback(id,user_id,action,event_key) values ('newB','B','SAVED','same')");
            assertThatThrownBy(() -> sql.execute(
                    "insert into taste_feedback(id,user_id,action,event_key) values ('duplicate','A','SAVED','same')"))
                    .isInstanceOf(SQLException.class).satisfies(e ->
                            assertThat(((SQLException) e).getSQLState()).isEqualTo("23505"));
            assertThatThrownBy(() -> sql.execute(
                    "insert into taste_feedback(id,user_id,action) values ('bad','A','UNKNOWN')"))
                    .isInstanceOf(SQLException.class);
        }
    }
}
