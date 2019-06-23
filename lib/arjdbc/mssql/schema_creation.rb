module ActiveRecord
  module ConnectionAdapters
    module MSSQL
      class SchemaCreation < AbstractAdapter::SchemaCreation
        private

        def visit_TableDefinition(o)
          if o.as
            table_name = quote_table_name(o.temporary ? "##{o.name}" : o.name)
            query = o.as.respond_to?(:to_sql) ? o.as.to_sql : o.as
            projections, source = query.match(%r{SELECT\s+(.*)?\s+FROM\s+(.*)?}).captures
            select_into = "SELECT #{projections} INTO #{table_name} FROM #{source}"
          else
            o.instance_variable_set :@as, nil
            super
          end
        end

        def add_column_options!(sql, options)
          sql << " DEFAULT #{quote_default_expression(options[:default], options[:column])}" if options_include_default?(options)

          sql << ' NOT NULL' if options[:null] == false

          sql << ' IDENTITY(1,1)' if options[:is_identity] == true

          sql << ' PRIMARY KEY' if options[:primary_key] == true

          sql
        end

        # There is no RESTRICT in MSSQL but it has NO ACTION which behave
        # same as RESTRICT, added this behave according rails api.
        def action_sql(action, dependency)
          case dependency
          when :restrict then "ON #{action} NO ACTION"
          else
            super
          end
        end

      end
    end
  end
end
