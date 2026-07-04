# Database Schema Management

The database is managed with Hybernate, on "update" mode.
This means that:
- **Hibernate automatically creates/updates tables** based on `@Entity` classes
- `update` mode never drops tables or deletes data
- Sql docs are not needed for the db to be built, as it is built automatically from annotations.
