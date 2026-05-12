package club.okak.shared.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

object DatabaseFactory {
    fun init(
        host: String,
        port: Int,
        db: String,
        user: String,
        password: String,
        migrationLocations: List<String>
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
            .outOfOrder(false)
            .validateOnMigrate(false)  // skip checksum validation on already-applied migrations
            .load()
        flyway.repair()   // sync checksums if migration files changed
        flyway.migrate()

        Database.connect(dataSource)
    }
}
