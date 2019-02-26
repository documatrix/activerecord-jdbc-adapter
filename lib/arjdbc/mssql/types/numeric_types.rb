# MSSQL numeric types definitions
module ActiveRecord
  module ConnectionAdapters
    module MSSQL
      module Type

        # Exact numerics
        class Integer < ActiveRecord::Type::Integer
        end

        class TinyInteger < ActiveRecord::Type::Integer
          def max_value
            256
          end

          def min_value
            0
          end
        end

        class SmallInteger < ActiveRecord::Type::Integer
        end

        class BigInteger < ActiveRecord::Type::Integer
          def type
            :bigint
          end
        end

        class Decimal < ActiveRecord::Type::Decimal
        end

        class Money < Decimal
          def initialize(options = {})
            super
            @precision = 19
            @scale = 4
          end
          def type
            :money
          end
        end

        class SmallMoney < Decimal
          def initialize(options = {})
            super
            @precision = 10
            @scale = 4
          end

          def type
            :smallmoney
          end
        end

        # Approximate numerics
        class Float < ActiveRecord::Type::Float
        end

        class Real < ActiveRecord::Type::Float
          def type
            :real
          end
        end

      end
    end
  end
end
