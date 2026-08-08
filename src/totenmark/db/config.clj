(ns totenmark.db.config
  (:require [next.jdbc :as next-jdbc]
            [totenmark.config :as config]))

(def db {:dbtype "sqlite"
         :dbname (config/database-name)})

(def ds (next-jdbc/get-datasource db))
