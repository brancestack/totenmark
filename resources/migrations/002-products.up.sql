CREATE TABLE products (product_id INTEGER PRIMARY KEY AUTOINCREMENT,
                       product_name TEXT NOT NULL,
                       description TEXT,
                       price INTEGER NOT NULL,
                       category TEXT NOT NULL CHECK (category IN ('donation', 'sale')),
                       status TEXT NOT NULL CHECK (status IN ('available', 'reserved')),
                       created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                       updated_at TEXT);
