module ActiveRecord
 module ConnectionAdapters
   module MSSQL
     module DatabaseLimits

       # Returns the maximum number of elements in an IN (x,y,z) clause.
       # NOTE: Could not find a limit for IN in mssql but 10000 seems to work
       # with the active record tests
       def in_clause_length
         10_000
       end

       private

       # the max bind params is 2100 but it seems
       # the jdbc uses 2 for something
       def bind_params_length
         2_098
       end

       # max number of insert rows in mssql
       def insert_rows_length
         1_000
       end

     end
   end
 end
end
