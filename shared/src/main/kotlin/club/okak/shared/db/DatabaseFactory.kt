package club.okak.shared.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

object DatabaseFactory {
    /**
     * @param migrationLocations  e.g. ["classpath:db/migration"]
     * @param historyTable        unique per service, e.g. "flyway_auth_history"
     *                            — prevents all services from sharing one history
     *                            table and stepping on each other's V1 migration.
     */
    fun init(
        host: String,
        port: Int,
        db: String,
        user: String,
        password: String,
        migrationLocations: List<String>,
        historyTable: String = "flyway_schema_history"
    ) {
        val jdbcUrl = "jdbc:postgresql://$host:$port/$db"
        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = user
            this.password = password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        val dataSource = HikariDataSource(config)

        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations(*migrationLocations.toTypedArray())
            .table(historyTable)           // isolated history per service
            .outOfOrder(false)
            .validateOnMigrate(false)
            .baselineOnMigrate(true)       // handle non-empty schema with no history
            .baselineVersion("0")          // baseline at 0 so V1 still runs
            .load()

        flyway.repair()
        flyway.migrate()

        Database.connect(dataSource)
    }
}
