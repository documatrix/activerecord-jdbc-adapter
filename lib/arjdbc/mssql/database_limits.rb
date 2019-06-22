module ActiveRecord
 module ConnectionAdapters
   module MSSQL
     module DatabaseLimits

       private

       # max number of insert rows in mssql
       def insert_rows_length
         1_000
       end

     end
   end
 end
end
