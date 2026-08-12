(ns totenmark.db.config
  (:require [next.jdbc :as next-jdbc]
            [totenmark.config :as config]))

(def db {:dbtype "sqlite"
         :dbname (config/database-name)
         :foreign_keys true
         :busy_timeout 5000})

(def ds (next-jdbc/get-datasource db))

(defn ready?
  []
  (and (= 5 (-> (next-jdbc/execute-one!
                 ds
                 ["SELECT COUNT(*) AS total
                   FROM sqlite_master
                   WHERE type = 'table'
                   AND name IN ('users', 'products', 'product_images',
                                'reservations', 'revoked_tokens')"])
                vals
                first))
       (boolean
        (next-jdbc/execute-one!
         ds
         ["SELECT 1 AS found
           FROM pragma_table_info('users')
           WHERE name = 'session_version'"]))))
