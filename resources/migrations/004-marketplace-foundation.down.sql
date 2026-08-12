DROP INDEX idx_reservations_expires_at;
--;;
DROP INDEX idx_reservations_reserved_by;
--;;
DROP TABLE reservations;
--;;
DROP INDEX idx_product_images_product_id;
--;;
DROP TABLE product_images;
--;;
DROP INDEX idx_products_created_at;
--;;
DROP INDEX idx_products_category_status;
--;;
DROP INDEX idx_products_user_id;
--;;
CREATE TABLE products_v1 (
  product_id INTEGER PRIMARY KEY AUTOINCREMENT,
  product_name TEXT NOT NULL,
  description TEXT,
  price INTEGER NOT NULL,
  category TEXT NOT NULL CHECK (category IN ('donation', 'sale')),
  status TEXT NOT NULL CHECK (status IN ('available', 'reserved')),
  created_at TEXT DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT,
  user_id INTEGER REFERENCES users(id)
);
--;;
INSERT INTO products_v1
SELECT product_id, product_name, description, price, category,
       status, created_at, updated_at, user_id
FROM products;
--;;
DROP TABLE products;
--;;
ALTER TABLE products_v1 RENAME TO products;
