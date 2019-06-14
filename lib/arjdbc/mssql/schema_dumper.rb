module ActiveRecord
  module ConnectionAdapters
    module MSSQL
      module ColumnDumper # :nodoc:
        # ssss
        MSSQL_NO_LIMIT_TYPES = [
          'text',
          'ntext',
          'varchar(max)',
          'nvarchar(max)',
          'varbinary(max)'
        ].freeze

        private

        def schema_limit(column)
          return if MSSQL_NO_LIMIT_TYPES.include?(column.sql_type)
          super
        end

      end
    end
  end
end
