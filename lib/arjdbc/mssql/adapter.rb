# frozen_string_literal: false
ArJdbc.load_java_part :MSSQL

require 'strscan'

require 'arel'
require 'arel/visitors/bind_visitor'
require 'arel/visitors/sqlserver'
require 'active_record/connection_adapters/abstract_adapter'

require 'arjdbc/abstract/core'
require 'arjdbc/abstract/connection_management'
require 'arjdbc/abstract/database_statements'
require 'arjdbc/abstract/statement_cache'
require 'arjdbc/abstract/transaction_support'

require 'arjdbc/mssql/schema_statements'
require 'arjdbc/mssql/database_statements'

# require 'arjdbc/util/quoted_cache'

module ArJdbc
  module MSSQL

    require 'arjdbc/mssql/utils'
    require 'arjdbc/mssql/limit_helpers'
    require 'arjdbc/mssql/lock_methods'
    require 'arjdbc/mssql/column'
    require 'arjdbc/mssql/explain_support'
    require 'arjdbc/mssql/types' if AR42

    include LimitHelpers
    include Utils
    include ExplainSupport
  end
end

module ActiveRecord::ConnectionAdapters
  class MSSQLColumn < Column
    # include ::ArJdbc::MSSQL::Column
  end

  class MSSQLAdapter < AbstractAdapter
    ADAPTER_NAME = 'MSSQL'.freeze

    include ArJdbc::Abstract::Core
    include ArJdbc::Abstract::ConnectionManagement
    include ArJdbc::Abstract::DatabaseStatements
    include ArJdbc::Abstract::StatementCache
    include ArJdbc::Abstract::TransactionSupport

    #include ::ArJdbc::MSSQL
    #include ::ArJdbc::Util::QuotedCache

    include MSSQL::SchemaStatements
    include MSSQL::DatabaseStatements

    def initialize(connection, logger = nil, connection_parameters = nil, config = {})
      # configure_connection happens in super
      super(connection, logger, config)
    end

    # @see ActiveRecord::ConnectionAdapters::JdbcAdapter#jdbc_connection_class
    def jdbc_connection_class(spec)
      ::ActiveRecord::ConnectionAdapters::MSSQLJdbcConnection
    end

    # @see ActiveRecord::ConnectionAdapters::JdbcAdapter#jdbc_column_class
    def jdbc_column_class
      ::ActiveRecord::ConnectionAdapters::MSSQLColumn
    end

    # FIXME: to be reviewed.
    def clear_cache!
      reload_type_map
      super
    end

    # Overrides the method in abstract adapter to set the limit and offset
    # in the right order. (SQLServer specific)
    # Called by bound_attributes
    def combine_bind_parameters(
      from_clause: [],
      join_clause: [],
      where_clause: [],
      having_clause: [],
      limit: nil,
      offset: nil
    )

      result = from_clause + join_clause + where_clause + having_clause
      result << offset if offset
      result << limit if limit
      result
    end

    def arel_visitor # :nodoc:
      ::Arel::Visitors::SQLServer.new(self)
    end
  end
end
