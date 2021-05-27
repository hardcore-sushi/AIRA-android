use rusqlite::{Connection, Error, params};

pub struct KeyValueTable<'a> {
    db: Connection,
    table_name: &'a str,
}

impl<'a> KeyValueTable<'a> {
    pub fn new(db_path: &str, table_name: &'a str) -> Result<KeyValueTable<'a>, Error> {
        let db = Connection::open(db_path)?;
        db.execute(&format!("CREATE TABLE IF NOT EXISTS {} (key TEXT PRIMARY KEY, value BLOB)", table_name), [])?;
        Ok(KeyValueTable {db, table_name})
    }
    pub fn set(&self, key: &str, value: &[u8]) -> Result<usize, Error> {
        Ok(self.db.execute(&format!("INSERT INTO {} (key, value) VALUES (?1, ?2)", self.table_name), params![key, value])?)
    }
    pub fn get(&self, key: &str) -> Result<Vec<u8>, Error> {
        let mut stmt = self.db.prepare(&format!("SELECT value FROM {} WHERE key=\"{}\"", self.table_name, key))?;
        let mut rows = stmt.query([])?;
        match rows.next()? {
            Some(row) => Ok(row.get(0)?),
            None => Err(rusqlite::Error::QueryReturnedNoRows)
        }
    }
    pub fn del(&self, key: &str) -> Result<usize, Error> {
        self.db.execute(&format!("DELETE FROM {} WHERE key=\"{}\"", self.table_name, key), [])
    }
    pub fn update(&self, key: &str, value: &[u8]) -> Result<usize, Error> {
        self.db.execute(&format!("UPDATE {} SET value=? WHERE key=\"{}\"", self.table_name, key), params![value])
    }
    pub fn upsert(&self, key: &str, value: &[u8]) -> Result<usize, Error> {
        self.db.execute(&format!("INSERT INTO {} (key, value) VALUES(?1, ?2) ON CONFLICT(key) DO UPDATE SET value=?3", self.table_name), params![key, value, value])
    }
}