(ns totenmark.db.migration
  (:require [ragtime.jdbc :as ragtime-jdbc]
            [ragtime.repl :as repl]
            [totenmark.config :as app-config]))

(defn config
  []
  {:datastore   (ragtime-jdbc/sql-database   {:dbtype "sqlite"
                                              :dbname (app-config/database-name)
                                              :foreign_keys true})
   :migrations  (ragtime-jdbc/load-resources "migrations")})


(defn migrate!
  []
  (repl/migrate (config)))
