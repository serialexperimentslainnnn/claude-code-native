package dev.lain.claudejb.controller.db

import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project

interface DbAccess {

    class Connection(val name: String, val kind: String, val url: String)

    class Table(val schema: String, val name: String, val kind: String, val columns: Int)

    class Column(val name: String, val type: String, val nullable: Boolean, val primary: Boolean)

    class Rows(val columns: List<String>, val rows: List<List<String>>, val updated: Int, val truncated: Boolean)

    fun connections(): List<Connection>

    fun tables(connection: String): List<Table>

    fun columns(connection: String, table: String): List<Column>

    fun query(connection: String, sql: String, maxRows: Int, timeoutSeconds: Int): Rows

    companion object {
        fun of(project: Project): DbAccess? = project.serviceOrNull<DbAccess>()
    }
}
