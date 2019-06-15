module ActiveRecord
  module ConnectionAdapters
    module MSSQL
      module ColumnDumper # :nodoc:
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

        def explicit_primary_key_default?(column)
          !column.identity?
        end

        def default_primary_key?(column)
          super && column.identity?
        end

      end
    end
  end
end
