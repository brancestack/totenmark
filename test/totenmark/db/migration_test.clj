(ns totenmark.db.migration-test
  (:require [clojure.test :refer [deftest is]]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [ragtime.repl :as ragtime]
            [totenmark.db.migration :as migration]))

(defn- with-temp-database
  [test-function]
  (let [path (str (java.nio.file.Files/createTempFile
                   "totenmark-migration-"
                   ".sqlite"
                   (make-array java.nio.file.attribute.FileAttribute 0)))
        datasource (jdbc/get-datasource {:dbtype "sqlite"
                                         :dbname path
                                         :foreign_keys true})]
    (System/setProperty "totenmark.db.name" path)
    (try
      (test-function datasource)
      (finally
        (System/clearProperty "totenmark.db.name")
        (java.nio.file.Files/deleteIfExists
         (java.nio.file.Path/of path (make-array String 0)))))))

(deftest refuses-to-discard-products-without-an-owner
  (with-temp-database
    (fn [datasource]
      (let [full-config (migration/config)
            old-schema (assoc full-config
                              :migrations
                              (take 3 (:migrations full-config)))]
        (ragtime/migrate old-schema)
        (jdbc/execute-one!
         datasource
         ["INSERT INTO products(product_name, price, category, status)
           VALUES ('Sem dono', 10, 'donation', 'available')"])

        (is (thrown? Exception (ragtime/migrate full-config)))
        (is (= 1 (-> (jdbc/execute-one! datasource
                                        ["SELECT COUNT(*) AS total FROM products"])
                     vals
                     first)))
        (is (nil? (jdbc/execute-one!
                   datasource
                   ["SELECT name FROM sqlite_master
                     WHERE type = 'table' AND name = 'products_v2'"])))

        (jdbc/execute-one!
         datasource
         ["INSERT INTO users(username, email, password) VALUES (?, ?, ?)"
          "Legacy owner" "owner@example.com" "old-hash"])
        (jdbc/execute-one!
         datasource
         ["UPDATE products SET user_id = 1 WHERE product_name = 'Sem dono'"])
        (ragtime/migrate full-config)
        (is (= 1 (-> (jdbc/execute-one!
                      datasource
                      ["SELECT user_id FROM products WHERE product_name = 'Sem dono'"])
                     vals
                     first)))
        (is (nil? (jdbc/execute-one!
                   datasource
                   ["SELECT name FROM sqlite_master
                     WHERE type = 'trigger'
                     AND name = 'products_owner_migration_guard'"])))))))

(deftest refuses-ambiguous-legacy-emails-without-changing-users
  (with-temp-database
    (fn [datasource]
      (let [full-config (migration/config)
            before-user-hardening (assoc full-config
                                         :migrations
                                         (take 5 (:migrations full-config)))]
        (ragtime/migrate before-user-hardening)
        (jdbc/execute-one!
         datasource
         ["INSERT INTO users(username, email, password) VALUES (?, ?, ?)"
          "Alice" "Alice@example.com" "old-hash"])
        (jdbc/execute-one!
         datasource
         ["INSERT INTO users(username, email, password) VALUES (?, ?, ?)"
          "Alice clone" " alice@EXAMPLE.com " "old-hash"])

        (is (thrown? Exception (ragtime/migrate full-config)))
        (is (= 2 (-> (jdbc/execute-one! datasource
                                       ["SELECT COUNT(*) AS total FROM users"])
                     vals
                     first)))
        (is (nil? (jdbc/execute-one!
                   datasource
                   ["SELECT name FROM pragma_table_info('users')
                     WHERE name = 'session_version'"])))
        (is (nil? (jdbc/execute-one!
                   datasource
                   ["SELECT name FROM sqlite_master
                     WHERE type = 'index'
                     AND name = 'idx_users_email_normalized'"])))

        (jdbc/execute-one!
         datasource
         ["UPDATE users SET email = 'alice-clone@example.com'
           WHERE username = 'Alice clone'"])
        (ragtime/migrate full-config)
        (is (= #{"alice@example.com" "alice-clone@example.com"}
               (->> (jdbc/execute! datasource ["SELECT email FROM users"])
                    (map (comp first vals))
                    set)))
        (is (= #{0}
               (->> (jdbc/execute! datasource
                                    ["SELECT session_version FROM users"])
                    (map (comp first vals))
                    set)))))))

(deftest normalizes-a-legacy-email-and-keeps-it-unique
  (with-temp-database
    (fn [datasource]
      (let [full-config (migration/config)
            before-user-hardening (assoc full-config
                                         :migrations
                                         (take 5 (:migrations full-config)))]
        (ragtime/migrate before-user-hardening)
        (jdbc/execute-one!
         datasource
         ["INSERT INTO users(username, email, password) VALUES (?, ?, ?)"
          "Alice" " Alice@Example.COM " "old-hash"])

        (ragtime/migrate full-config)

        (is (= "alice@example.com"
               (-> (jdbc/execute-one! datasource
                                      ["SELECT email FROM users"])
                   vals
                   first)))
        (is (= 0 (-> (jdbc/execute-one! datasource
                                        ["SELECT session_version FROM users"])
                     vals
                     first)))
        (is (thrown?
             Exception
             (jdbc/execute-one!
              datasource
              ["INSERT INTO users(username, email, password) VALUES (?, ?, ?)"
               "Another Alice" "ALICE@example.com" "old-hash"])))))))

(deftest latest-migration-rolls-back-and-applies-again
  (with-temp-database
    (fn [datasource]
      (let [full-config (migration/config)]
        (ragtime/migrate full-config)
        (jdbc/execute-one!
         datasource
         ["INSERT INTO users(username, email, password) VALUES (?, ?, ?)"
          "Alice" "alice@example.com" "old-hash"])

        (ragtime/rollback full-config 1)

        (is (= "alice@example.com"
               (-> (jdbc/execute-one! datasource ["SELECT email FROM users"])
                   vals
                   first)))
        (is (nil? (jdbc/execute-one!
                   datasource
                   ["SELECT name FROM pragma_table_info('users')
                     WHERE name = 'session_version'"])))
        (is (nil? (jdbc/execute-one!
                   datasource
                   ["SELECT name FROM sqlite_master
                     WHERE type = 'index'
                     AND name = 'idx_users_email_normalized'"])))

        (ragtime/migrate full-config)

        (is (= 0 (-> (jdbc/execute-one!
                      datasource
                      ["SELECT session_version FROM users"])
                     vals
                     first)))))))

(deftest final-schema-has-no-broken-foreign-keys
  (with-temp-database
    (fn [datasource]
      (ragtime/migrate (migration/config))
      (is (empty? (jdbc/execute! datasource ["PRAGMA foreign_key_check"])))
      (is (= #{"users" "products" "product_images"
               "reservations" "revoked_tokens" "ragtime_migrations"}
             (->> (jdbc/execute!
                   datasource
                   ["SELECT name FROM sqlite_master
                     WHERE type = 'table' AND name NOT LIKE 'sqlite_%'"])
                  (map (comp first vals))
                  set)))
      (is (= 1
             (->> (jdbc/execute!
                   datasource
                   ["PRAGMA table_info('products')"]
                   {:builder-fn rs/as-unqualified-kebab-maps})
                  (filter #(= "user_id" (:name %)))
                  first
                  :notnull)))
      (is (= "CASCADE"
             (->> (jdbc/execute!
                   datasource
                   ["PRAGMA foreign_key_list('products')"]
                   {:builder-fn rs/as-unqualified-kebab-maps})
                  (filter #(= "user_id" (:from %)))
                  first
                  :on-delete))))))
