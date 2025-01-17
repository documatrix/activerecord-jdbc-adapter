/***** BEGIN LICENSE BLOCK *****
 * Copyright (c) 2012-2013 Karol Bucek <self@kares.org>
 * Copyright (c) 2006-2010 Nick Sieger <nick@nicksieger.com>
 * Copyright (c) 2006-2007 Ola Bini <ola.bini@gmail.com>
 * Copyright (c) 2008-2009 Thomas E Enebo <enebo@acm.org>
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 ***** END LICENSE BLOCK *****/
package arjdbc.mssql;

import arjdbc.jdbc.Callable;
import arjdbc.jdbc.RubyJdbcConnection;
import arjdbc.util.DateTimeUtils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.SQLException;
import java.sql.Types;
import java.sql.Timestamp;
import java.util.Locale;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyBoolean;
import org.jruby.RubyClass;
import org.jruby.RubyString;
import org.jruby.RubySymbol;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.ByteList;

/**
 *
 * @author nicksieger
 */
public class MSSQLRubyJdbcConnection extends RubyJdbcConnection {
    private static final long serialVersionUID = -745716565005219263L;

    public MSSQLRubyJdbcConnection(Ruby runtime, RubyClass metaClass) {
        super(runtime, metaClass);
    }

    public static RubyClass createMSSQLJdbcConnectionClass(Ruby runtime, RubyClass jdbcConnection) {
        final RubyClass clazz = getConnectionAdapters(runtime). // ActiveRecord::ConnectionAdapters
            defineClassUnder("MSSQLJdbcConnection", jdbcConnection, ALLOCATOR);
        clazz.defineAnnotatedMethods(MSSQLRubyJdbcConnection.class);
        getConnectionAdapters(runtime).setConstant("MssqlJdbcConnection", clazz); // backwards-compat
        return clazz;
    }

    public static RubyClass load(final Ruby runtime) {
        RubyClass jdbcConnection = getJdbcConnection(runtime);
        return createMSSQLJdbcConnectionClass(runtime, jdbcConnection);
    }

    protected static final ObjectAllocator ALLOCATOR = new ObjectAllocator() {
        public IRubyObject allocate(Ruby runtime, RubyClass klass) {
            return new MSSQLRubyJdbcConnection(runtime, klass);
        }
    };

    private static final byte[] EXEC = new byte[] { 'e', 'x', 'e', 'c' };

    @JRubyMethod(name = "exec?", required = 1, meta = true, frame = false)
    public static RubyBoolean exec_p(ThreadContext context, IRubyObject self, IRubyObject sql) {
        final ByteList sqlBytes = sql.asString().getByteList();
        return context.runtime.newBoolean( startsWithIgnoreCase(sqlBytes, EXEC) );
    }

    @Override
    protected RubyArray mapTables(final ThreadContext context, final Connection connection,
            final String catalog, final String schemaPattern, final String tablePattern,
            final ResultSet tablesSet) throws SQLException, IllegalStateException {

        final RubyArray tables = context.runtime.newArray();

        while ( tablesSet.next() ) {
            String schema = tablesSet.getString(TABLES_TABLE_SCHEM);
            if ( schema != null ) schema = schema.toLowerCase();
            // Under MS-SQL, don't return system tables/views unless explicitly asked for :
            if ( schemaPattern == null &&
                ( "sys".equals(schema) || "information_schema".equals(schema) ) ) {
                continue;
            }
            String name = tablesSet.getString(TABLES_TABLE_NAME);
            if ( name == null ) {
                // NOTE: seems there's a jTDS but when doing getColumns while
                // EXPLAIN is on (e.g. `SET SHOWPLAN_TEXT ON`) not returning
                // correct result set with table info (null NAME, invalid CAT)
                throw new IllegalStateException("got null name while matching table(s): [" +
                    catalog + "." + schemaPattern + "." + tablePattern + "] check " +
                    "if this happened during EXPLAIN (SET SHOWPLAN_TEXT ON) if so please try " +
                    "turning it off using the system property 'arjdbc.mssql.explain_support.disabled=true' " +
                    "or programatically by changing: `ArJdbc::MSSQL::ExplainSupport::DISABLED`");
            }
            tables.add( cachedString(context, caseConvertIdentifierForRails(connection, name)) );
        }
        return tables;
    }

    // Format properly SQL Server types (this is the variable sql_type)
    @Override
    protected String typeFromResultSet(final ResultSet resultSet) throws SQLException {
      final int precision = intFromResultSet(resultSet, COLUMN_SIZE);
      final int scale = intFromResultSet(resultSet, DECIMAL_DIGITS);
      final int length = intFromResultSet(resultSet, BUFFER_LENGTH);

      final int dataType = intFromResultSet(resultSet, DATA_TYPE);
      final String typeName = resultSet.getString(TYPE_NAME);

      final String uuid = "uniqueidentifier";
      final String money = "money";
      final String smallmoney = "smallmoney";
      final String datetime2 = "datetime2";

      switch (dataType) {
        // For integers the Rails limit option is the JDBC BUFFER_LENGTH.
        case Types.INTEGER:
            // limit is 4
            return typeName;
        case Types.TINYINT:
            // limit is 1
            return typeName;
        case Types.SMALLINT:
            // limit is 2
            return typeName;
        case Types.BIGINT:
            // limit is 8
            return typeName;
        case Types.BIT:
        case Types.REAL:
        case Types.DOUBLE:
            // SQL server FLOAT type is double in jdbc
            return typeName;
        case Types.DATE:
        case Types.TIMESTAMP:
            // For datetime2 sql type, Rails precision comes in the scale
            if (typeName.equals(datetime2)) {
              return formatTypeWithPrecision(typeName, scale);
            }

            return typeName;
        case Types.TIME:
            // For time the Rails precision comes in the scale
            return formatTypeWithPrecision(typeName, scale);
        case Types.NUMERIC:
        case Types.DECIMAL:
            // money and smallmoney are considered decimals with specific
            // precision, money(19,4) and smallmoney(10, 4)
            if ( typeName.equals(money) || typeName.equals(smallmoney) ) {
              return typeName;
            }

            return formatTypeWithPrecisionAndScale(typeName, precision, scale);
        case Types.CHAR:
            // The uuid is char type
            if ( typeName.equals(uuid) ) {
              return typeName;
            }

            return formatTypeWithPrecision(typeName, precision);
        case Types.NCHAR:
            return formatTypeWithPrecision(typeName, precision);
        case Types.VARCHAR:
        case Types.NVARCHAR:
            if ( precision == 2147483647 ) {
              return formatTypeWithPrecisionMax(typeName, "max");
            }

            return formatTypeWithPrecision(typeName, precision);
        case Types.BINARY:
        case Types.VARBINARY:
            if ( precision == 2147483647 ) {
              return formatTypeWithPrecisionMax(typeName, "max");
            }

            return formatTypeWithPrecision(typeName, precision);
        case Types.LONGVARBINARY:
            // This maps to IMAGE type
            return typeName;
        case Types.LONGVARCHAR:
            // This maps to TEXT type
            return typeName;
        case Types.LONGNVARCHAR:
            // This maps to XML and NTEXT types
            // FIXME: The XML type needs to be reviewed.
            return typeName;
        default:
            return formatTypeWithPrecisionAndScale(typeName, precision, scale);
      }
    }

    // Append the limit to types such as integers and strings
    protected static String formatTypeWithLimit(final String type, final int limit ) {

        if ( limit <= 0 ) return type;

        final StringBuilder typeStr = new StringBuilder();

        typeStr.append(type).append('(').append(limit).append(')');

        return typeStr.toString();
    }

    protected static String formatTypeWithPrecision(final String type, final int precision) {

        if ( precision < 0 ) return type;

        final StringBuilder typeStr = new StringBuilder().append(type);
        typeStr.append('(').append(precision).append(')');

        return typeStr.toString();
    }

    protected static String formatTypeWithPrecisionMax(final String type, final String precision) {

        final StringBuilder typeStr = new StringBuilder().append(type);
        typeStr.append('(').append(precision).append(')');

        return typeStr.toString();
    }

    // differs from  method in parent class because this appends scale 0
    protected static String formatTypeWithPrecisionAndScale(
        final String type, final int precision, final int scale) {

        if ( precision <= 0 ) return type;

        final StringBuilder typeStr = new StringBuilder().append(type);
        typeStr.append('(').append(precision);
        if ( scale >= 0 ) typeStr.append(',').append(scale);
        return typeStr.append(')').toString();
    }

    // Overrides method in parent, for some reason the method in parent
    // converts fractional seconds with padded zeros (e.g. 000xxxyyy)
    // maybe a bug in Jruby strftime('%6N') ?
    @Override
    protected IRubyObject timestampToRuby(ThreadContext context, Ruby runtime, ResultSet resultSet, int column) throws SQLException {

      final String value = resultSet.getString(column);

      if (value == null) return context.nil;

      return DateTimeUtils.parseDateTime(context, value, getDefaultTimeZone(context));
    }

    // For some reason DateTimeUtils.parseTime does not work, similar issue
    // seen in timestampToRuby, fractional seconds padded with zeros.
    @Override
    protected IRubyObject timeToRuby(ThreadContext context, Ruby runtime, ResultSet resultSet, int column) throws SQLException {
      final String value = resultSet.getString(column);

      if ( value == null ) return context.nil;

      final String datetime_value = "2000-01-01 " + value;

      return DateTimeUtils.parseDateTime(context, datetime_value, getDefaultTimeZone(context));
    }

    // This overrides method in parent because of the issue in prepared
    // statements when setting datatime data types, the mssql-jdbc
    // uses datetime2 to cast datetime.
    //
    // More at:  https://github.com/Microsoft/mssql-jdbc/issues/443
    @Override
    protected void setTimestampParameter(final ThreadContext context,
        final Connection connection, final PreparedStatement statement,
        final int index, IRubyObject value,
        final IRubyObject attribute, final int type) throws SQLException {
      String tsString = DateTimeUtils.timestampTimeToString(context, value, getDefaultTimeZone(context), false);

      statement.setObject(index, tsString, Types.NVARCHAR);
    }

    // Handle more fractional second precision than (default) 59.123 only
    @Override
    protected void setTimeParameter(final ThreadContext context,
        final Connection connection, final PreparedStatement statement,
        final int index, IRubyObject value,
        final IRubyObject attribute, final int type) throws SQLException {
        String timeStr = DateTimeUtils.timeString(context, value, getDefaultTimeZone(context), true);
        statement.setObject(index, timeStr, Types.NVARCHAR);
    }

    // Overrides the method in parent, we only remove the savepoint
    // from the getSavepoints Map
    @JRubyMethod(name = "release_savepoint", required = 1)
    public IRubyObject release_savepoint(final ThreadContext context, final IRubyObject name) {
      if (name == context.nil) throw context.runtime.newArgumentError("nil savepoint name given");

      final Connection connection = getConnection(true);

      Object savepoint = getSavepoints(context).remove(name);

      if (savepoint == null) throw newSavepointNotSetError(context, name, "release");

      // NOTE: RubyHash.remove does not convert to Java as get does :
      if (!(savepoint instanceof Savepoint)) {
        savepoint = ((IRubyObject) savepoint).toJava(Savepoint.class);
      }

      // The 'releaseSavepoint' method is not currently supported
      // by the Microsoft SQL Server JDBC Driver
      // connection.releaseSavepoint((Savepoint) savepoint);
      return context.nil;
    }

    //----------------------------------------------------------------
    // read_uncommitted: "READ UNCOMMITTED",
    // read_committed:   "READ COMMITTED",
    // repeatable_read:  "REPEATABLE READ",
    // serializable:     "SERIALIZABLE"
    // snapshot:         "SNAPSHOT"
    //

    // Overrides method in parent only because it needs to call static method of child class
    protected void setTransactionIsolation(final ThreadContext context, final Connection connection,
        final IRubyObject isolation) throws SQLException {
      final int level = mapTransactionIsolationLevel(isolation);
      try {
        connection.setTransactionIsolation(level);
      }
      catch (SQLException e) {
        RubyClass txError = ActiveRecord(context).getClass("TransactionIsolationError");
        if ( txError != null ) throw wrapException(context, txError, e);
        throw e; // let it roll - will be wrapped into a JDBCError (non 4.0)
      }
    }

    // Overrides method in parent only because it needs to call static method of child class
    @JRubyMethod(name = "transaction_isolation", alias = "get_transaction_isolation")
    public IRubyObject get_transaction_isolation(final ThreadContext context) {
      return withConnection(context, new Callable<IRubyObject>() {
        public IRubyObject call(final Connection connection) throws SQLException {
          final int level = connection.getTransactionIsolation();
          final String isolationSymbol = formatTransactionIsolationLevel(level);
          if ( isolationSymbol == null ) return context.nil;
          return context.runtime.newSymbol(isolationSymbol);
        }
      });
    }

    // Overrides method in parent only because it needs to call static method of child class
    @JRubyMethod(name = "transaction_isolation=", alias = "set_transaction_isolation")
    public IRubyObject set_transaction_isolation(final ThreadContext context, final IRubyObject isolation) {
      return withConnection(context, new Callable<IRubyObject>() {
        public IRubyObject call(final Connection connection) throws SQLException {
          final int level;

          if ( isolation.isNil() ) {
            level = connection.getMetaData().getDefaultTransactionIsolation();
          } else {
            level = mapTransactionIsolationLevel(isolation);
          }

          connection.setTransactionIsolation(level);

          final String isolationSymbol = formatTransactionIsolationLevel(level);
          if ( isolationSymbol == null ) return context.nil;
          return context.runtime.newSymbol(isolationSymbol);
        }
      });
    }

    // hide parent method to support mssql specific transaction isolation level
    public static String formatTransactionIsolationLevel(final int level) {
      if ( level == Connection.TRANSACTION_READ_UNCOMMITTED ) return "read_uncommitted"; // 1
      if ( level == Connection.TRANSACTION_READ_COMMITTED ) return "read_committed"; // 2
      if ( level == Connection.TRANSACTION_REPEATABLE_READ ) return "repeatable_read"; // 4
      if ( level == Connection.TRANSACTION_SERIALIZABLE ) return "serializable"; // 8
      // this is alternative to SQLServerConnection.TRANSACTION_SNAPSHOT
      if ( level == Connection.TRANSACTION_READ_COMMITTED + 4094) return "snapshot";
      if ( level == 0 ) return null;
      throw new IllegalArgumentException("unexpected transaction isolation level: " + level);
    }


    // hide parent method to support mssql specific transaction isolation level
    public static int mapTransactionIsolationLevel(final IRubyObject isolation) {
      final Object isolationString;

      if ( isolation instanceof RubySymbol ) {
        isolationString = ((RubySymbol) isolation).asJavaString(); // RubySymbol (interned)
      } else {
        isolationString = isolation.asString().toString().toLowerCase(Locale.ENGLISH).intern();
      }

      if ( isolationString == "read_uncommitted" ) return Connection.TRANSACTION_READ_UNCOMMITTED; // 1
      if ( isolationString == "read_committed" ) return Connection.TRANSACTION_READ_COMMITTED; // 2
      if ( isolationString == "repeatable_read" ) return Connection.TRANSACTION_REPEATABLE_READ; // 4
      if ( isolationString == "serializable" ) return Connection.TRANSACTION_SERIALIZABLE; // 8
      // this is alternative to SQLServerConnection.TRANSACTION_SNAPSHOT
      if ( isolationString == "snapshot" ) return Connection.TRANSACTION_READ_COMMITTED + 4094;

      throw new IllegalArgumentException(
          "unexpected isolation level: " + isolation + " (" + isolationString + ")"
          );
    }

    // overrides method in parent for database specific types
    protected String mapTypeToString(final IRubyObject type) {
      final String typeStr = type.asJavaString();

      if (typeStr == "datetime_basic") return "datetime";

      if (typeStr == "smalldatetime") return "datetime";

      return typeStr;
    }

    // Get MSSQL major version, 8, 9, 10, 11, 12, etc.
    @JRubyMethod
    public IRubyObject database_major_version(final ThreadContext context) throws SQLException {
      return withConnection(context, new Callable<IRubyObject>() {
        public IRubyObject call(final Connection connection) throws SQLException {
          final DatabaseMetaData metaData = connection.getMetaData();

          return context.runtime.newFixnum( metaData.getDatabaseMajorVersion() );
        }
      });
    }

    /**
     * Microsoft SQL 2000+ support schemas
     */
    @Override
    protected boolean databaseSupportsSchemas() {
        return true;
    }

    /**
     * Treat LONGVARCHAR as CLOB on MSSQL for purposes of converting a JDBC value to Ruby.
     */
    @Override
    protected IRubyObject jdbcToRuby(
        final ThreadContext context, final Ruby runtime,
        final int column, int type, final ResultSet resultSet)
        throws SQLException {
        if ( type == Types.LONGVARCHAR || type == Types.LONGNVARCHAR ) type = Types.CLOB;
        return super.jdbcToRuby(context, runtime, column, type, resultSet);
    }

    @Override
    protected ColumnData[] extractColumns(final ThreadContext context,
        final Connection connection, final ResultSet resultSet,
        final boolean downCase) throws SQLException {
        return filterRowNumFromColumns( super.extractColumns(context, connection, resultSet, downCase) );
    }

    /**
     * Filter out the <tt>_row_num</tt> column from results.
     */
    private static ColumnData[] filterRowNumFromColumns(final ColumnData[] columns) {
        for ( int i = 0; i < columns.length; i++ ) {
            if ( "_row_num".equals( columns[i].getName() ) ) {
                final ColumnData[] filtered = new ColumnData[columns.length - 1];

                if ( i > 0 ) {
                    System.arraycopy(columns, 0, filtered, 0, i);
                }

                if ( i + 1 < columns.length ) {
                    System.arraycopy(columns, i + 1, filtered, i, columns.length - (i + 1));
                }

                return filtered;
            }
        }
        return columns;
    }

    // internal helper not meant as a "public" API - used in one place thus every
    @JRubyMethod(name = "jtds_driver?")
    public RubyBoolean jtds_driver_p(final ThreadContext context) throws SQLException {
        // "jTDS Type 4 JDBC Driver for MS SQL Server and Sybase"
        // SQLJDBC: "Microsoft JDBC Driver 4.0 for SQL Server"
        return withConnection(context, new Callable<RubyBoolean>() {
            // NOTE: only used in one place for now (on release_savepoint) ...
            // might get optimized to only happen once since driver won't change
            public RubyBoolean call(final Connection connection) throws SQLException {
                final String driver = connection.getMetaData().getDriverName();
                return context.getRuntime().newBoolean( driver.indexOf("jTDS") >= 0 );
            }
        });
    }

}
