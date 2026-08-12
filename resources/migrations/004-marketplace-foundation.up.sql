-- Confere os dados antigos antes de reconstruir a tabela. O gatilho fica
-- temporariamente ativo para que um anuncio sem dono interrompa a migration.
CREATE TRIGGER IF NOT EXISTS products_owner_migration_guard
BEFORE UPDATE OF user_id ON products
WHEN NEW.user_id IS NULL
BEGIN
  SELECT RAISE(ABORT, 'ha anuncios sem dono; preencha products.user_id antes de migrar');
END;
--;;
UPDATE products SET user_id = user_id;
--;;
DROP TRIGGER products_owner_migration_guard;
--;;
DROP TABLE IF EXISTS products_v2;
--;;
CREATE TABLE products_v2 (
  product_id INTEGER PRIMARY KEY AUTOINCREMENT,
  product_name TEXT NOT NULL,
  description TEXT,
  price INTEGER NOT NULL,
  category TEXT NOT NULL CHECK (category IN ('donation', 'sale')),
  status TEXT NOT NULL CHECK (status IN ('available', 'reserved')),
  created_at TEXT NOT NULL,
  updated_at TEXT,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE
);
--;;
INSERT INTO products_v2 (
  product_id, product_name, description, price, category,
  status, created_at, updated_at, user_id
)
SELECT
  product_id, product_name, description, price, category,
  status, COALESCE(created_at, CURRENT_TIMESTAMP), updated_at, user_id
FROM products;
--;;
DROP TABLE products;
--;;
ALTER TABLE products_v2 RENAME TO products;
--;;
CREATE INDEX idx_products_user_id ON products(user_id);
--;;
CREATE INDEX idx_products_category_status ON products(category, status);
--;;
CREATE INDEX idx_products_created_at ON products(created_at);
--;;
CREATE TABLE product_images (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  product_id INTEGER NOT NULL REFERENCES products(product_id) ON DELETE CASCADE,
  url TEXT NOT NULL,
  position INTEGER NOT NULL DEFAULT 0,
  UNIQUE(product_id, position)
);
--;;
CREATE INDEX idx_product_images_product_id ON product_images(product_id);
--;;
CREATE TABLE reservations (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  product_id INTEGER NOT NULL UNIQUE REFERENCES products(product_id) ON DELETE CASCADE,
  reserved_by INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at TEXT NOT NULL,
  expires_at TEXT NOT NULL
);
--;;
CREATE INDEX idx_reservations_reserved_by ON reservations(reserved_by);
--;;
CREATE INDEX idx_reservations_expires_at ON reservations(expires_at);
