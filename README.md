# ActiveRecord JDBC Alternative Adapter

This adapter is a fork of the ActiveRecord JDBC Adapter with basic support for
**SQL Server/Azure SQL**. This adapter may work with other databases
supported by the original adapter such as PostgreSQL but it is advised to
use the [original adapter](https://github.com/jruby/active)

This adapter only works with JRuby and it is advised to install the latest
stable versins of Rails

- For Rails `5.0.7.2` install the `50.3.1` version of this adapter
- For Rails `5.1.7` install the `51.3.0` version of this adapter

Support for Rails 5.2 is planned in the near future.


### How to use it:

Add the following to your `Gemfile`:

```ruby
platforms :jruby do
  # Use jdbc as the database for Active Record
  gem 'activerecord-jdbc-alt-adapter', '~> 50.3.1', require: 'arjdbc'
  gem 'jdbc-mssql', '~> 0.6.0'
end
```

Or look at the sample rails 5.0 app  [wombat](https://github.com/JesseChavez/wombat50)
and see how is set up.

### Breaking changes

- This adapter let SQL Server be SQL Server, it does not make SQL Server to be
  more like MySQL or PostgreSQL, The query will just fails if SQL Server does not
  support that SQL dialect.
- This adapter uses the `datetime2` sql data type as the Rails logical `datetime` data type.
- This adapter needs the mssql jdbc driver version 7.0.0  onwards to work properly,
  therefore you can use the gem `jdbc-mssql` version `0.6.0` onwards or the actual
  driver jar file  version `7.0.0`.


### Recommendation

If you have the old sql server `datetime` data type for `created_at` and
`updated_at`, you don't need to upgrade straightaway to `datetime2`, the old data type
(`datetime_basic`) will still work for simple updates, just make you add to the time zone
aware list. If you have complex `datetime` queries it is advised to upgrade to
`datetime2`

```ruby
# time zone aware configuration.
config.active_record.time_zone_aware_types = [:datetime, :datetime_basic]
```

In order to avoid deadlocks it is advised to use `SET READ_COMMITTED_SNAPSHOT ON`
Make sure to run `ALTER DATABASE your_db SET READ_COMMITTED_SNAPSHOT ON` against
your database.

If you prefer to use the `READ_UNCOMMITED` transaction isolation level as your
default isolation level, add the `transaction_isolation: 'read_uncommitted'` in
your database config.

If you have slow queries on your background jobs and locking queries you can change the default
`lock_timeout` config, add the `lock_timeout: 10000` in your database config.

database config example (`database.yml`):


```yml
# SQL Server (2012 or higher)

default: &default
  adapter: sqlserver
  encoding: utf8

development:
  <<: *default
  host: localhost
  database: sam_development
  username: SA
  password: password
  transaction_isolation: read_uncommitted
  lock_timeout: 10000

test:
  <<: *default
  host: localhost
  database: sam_test
  username: SA
  password: password

production:
  <<: *default
  host: localhost
  database: sam_production
  username:
  password:
```


### WARNING

Keep one eye in the Rails connection pool, we have not thoroughly tested that
part since we don't use the default Rails connection pool, other than that
this adapter should just work.



# ActiveRecord JDBC Adapter

[![Gem Version](https://badge.fury.io/rb/activerecord-jdbc-adapter.svg)][7]

ActiveRecord-JDBC-Adapter (AR-JDBC) is the main database adapter for Rails'
*ActiveRecord* component that can be used with [JRuby][0].
ActiveRecord-JDBC-Adapter provides full or nearly full support for:
**MySQL**, **PostgreSQL**, **SQLite3**.  In the near future there are plans to
add support **MSSQL**. Unless we get more contributions we will not be going
beyond these four adapters.  Note that the amount of work needed to get
another adapter is not huge but the amount of testing required to make sure
that adapter continues to work is not something we can do with the resources
we currently have.

For Oracle database users you are encouraged to use
https://github.com/rsim/oracle-enhanced.

Version **50.x** supports Rails version 5.0.x and it lives on branch 50-stable.
Version **51.x** supports Rails version 5.1.x and is currently on master until
its first release. The minimum version of JRuby for 50+ is JRuby **9.1.x** and
JRuby 9.1+ requires Java 7 or newer (we recommend Java 8 at minimum).

## Using ActiveRecord JDBC

### Inside Rails

To use AR-JDBC with JRuby on Rails:

1. Choose the adapter you wish to gem install. The following pre-packaged
adapters are available:

  - MySQL (`activerecord-jdbcmysql-adapter`)
  - PostgreSQL (`activerecord-jdbcpostgresql-adapter`)
  - SQLite3 (`activerecord-jdbcsqlite3-adapter`)

2. If you're generating a new Rails application, use the following command:

    jruby -S rails new sweetapp

3. Configure your *database.yml* in the normal Rails style:

```yml
development:
  adapter: mysql2 # or mysql
  database: blog_development
  username: blog
  password: 1234
```

For JNDI data sources, you may simply specify the JNDI location as follows, it's
recommended to use the same adapter: setting as one would configure when using
"bare" (JDBC) connections e.g. :

```yml
production:
  adapter: postgresql
  jndi: jdbc/PostgreDS
```

**NOTE:** any other settings such as *database:*, *username:*, *properties:* make
no difference since everything is already configured on the JNDI DataSource end.

JDBC driver specific properties might be set if you use an URL to specify the DB
or preferably using the *properties:* syntax:

```yml
production:
  adapter: mysql
  username: blog
  password: blog
  url: "jdbc:mysql://localhost:3306/blog?profileSQL=true"
  properties: # specific to com.mysql.jdbc.Driver
    socketTimeout:  60000
    connectTimeout: 60000
```

### Standalone with ActiveRecord

Once the setup is made (see below) you can establish a JDBC connection like this
(e.g. for `activerecord-jdbcderby-adapter`):

```ruby
ActiveRecord::Base.establish_connection(
  adapter: 'sqlite3',
  database: 'db/my-database'
)
```

#### Using Bundler

Proceed as with Rails; specify `ActiveRecord` in your Bundle along with the
chosen JDBC adapter(s), this time sample *Gemfile* for MySQL:

```ruby
gem 'activerecord', '~> 5.0.6'
gem 'activerecord-jdbcmysql-adapter', :platform => :jruby
```

When you `require 'bundler/setup'` everything will be set up for you as expected.

#### Without Bundler

Install the needed gems with JRuby, for example:

    gem install activerecord -v "~> 5.0.6"
    gem install activerecord-jdbc-adapter --ignore-dependencies

If you wish to use the adapter for a specific database, you can install it
directly and the (jdbc-) driver gem (dependency) will be installed as well:

    jruby -S gem install activerecord-jdbcmysql-adapter

Your program should include:

```ruby
require 'active_record'
require 'activerecord-jdbc-adapter' if defined? JRUBY_VERSION
```

## Source

The source for activerecord-jdbc-adapter is available using git:

    git clone git://github.com/jruby/activerecord-jdbc-adapter.git

Please note that the project manages multiple gems from a single repository,
if you're using *Bundler* >= 1.2 it should be able to locate all gemspecs from
the git repository. Sample *Gemfile* for running with (MySQL) master:

```ruby
gem 'activerecord-jdbc-adapter', :github => 'jruby/activerecord-jdbc-adapter'
gem 'activerecord-jdbcmysql-adapter', :github => 'jruby/activerecord-jdbc-adapter'
```

## Getting Involved

Please read our [CONTRIBUTING](CONTRIBUTING.md) & [RUNNING_TESTS](RUNNING_TESTS.md)
guides for starters. You can always help us by maintaining AR-JDBC's [wiki][5].

## Feedback

Please report bugs at our [issue tracker][3]. If you're not sure if
something's a bug, feel free to pre-report it on the [mailing lists][1] or
ask on the #JRuby IRC channel on http://freenode.net/ (try [web-chat][6]).

## Authors

This project was originally written by [Nick Sieger](http://github.com/nicksieger)
and [Ola Bini](http://github.com/olabini) with lots of help from the JRuby community.
Polished 3.x compatibility and 4.x support (for AR-JDBC >= 1.3.0) was managed by
[Karol Bucek](http://github.com/kares) among others.

## License

ActiveRecord-JDBC-Adapter is open-source released under the BSD/MIT license.
See [LICENSE.txt](LICENSE.txt) included with the distribution for details.

Open-source driver gems within AR-JDBC's sources are licensed under the same
license the database's drivers are licensed. See each driver gem's LICENSE.txt.

[0]: http://www.jruby.org/
[1]: http://jruby.org/community
[2]: http://github.com/jruby/activerecord-jdbc-adapter/blob/master/activerecord-jdbcmssql-adapter
[3]: https://github.com/jruby/activerecord-jdbc-adapter/issues
[4]: http://github.com/nicksieger/activerecord-cachedb-adapter
[5]: https://github.com/jruby/activerecord-jdbc-adapter/wiki
[6]: https://webchat.freenode.net/?channels=#jruby
[7]: http://badge.fury.io/rb/activerecord-jdbc-adapter
[8]: https://github.com/jruby/activerecord-jdbc-adapter/wiki/Migrating-from-1.2.x-to-1.3.0
