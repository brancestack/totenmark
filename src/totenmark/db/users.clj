(ns totenmark.db.users
  (:require [honey.sql :as sql]
            [next.jdbc :as next-jdbc]
            [next.jdbc.result-set :as rs]
            [totenmark.db.config :as config]))

(def opts
  {:builder-fn rs/as-unqualified-lower-maps})

(def public-columns
  [:id :username :email])

(defn create!
  [{:keys [username email password]}]
  (let [query (-> {:insert-into :users
                   :values [{:username username
                             :email    email
                             :password password}]}
                  sql/format)]
    (next-jdbc/execute-one!
     config/ds
     query
     opts)))

(defn find-all
  [{:keys [limit offset query-text]}]
  (let [statement (cond-> {:select public-columns
                           :from [:users]
                           :order-by [[:id :asc]]
                           :limit limit
                           :offset offset}
                    query-text
                    (assoc :where [:or
                                   [:like :username (str "%" query-text "%")]
                                   [:like :email (str "%" query-text "%")]]))
        query (-> statement
                  sql/format)]
    (next-jdbc/execute!
     config/ds
     query
     opts)))

(defn update!
  [attrs]
  (let [query (-> {:update :users
                   :set (dissoc attrs :id)
                   :where [:= :id (:id attrs)]}
                  sql/format)]
    (next-jdbc/execute-one!
     config/ds
     query
     opts)))

(defn delete!
  [id]
  (let [query (-> {:delete-from :users
                   :where [:= :id id]}
                  sql/format)]
    (next-jdbc/execute-one!
     config/ds
     query
     opts)))

(defn find-by-id
  [id]
  (let [query (-> {:select public-columns
                   :from [:users]
                   :where [:= :id id]}
                  sql/format)]
    (next-jdbc/execute-one!
     config/ds
     query
     opts)))

(defn find-by-email
  [email]
  (let [query (-> {:select [:*]
                   :from [:users]
                   :where [:= :email email]}
                  sql/format)]
    (next-jdbc/execute-one!
     config/ds
     query
     opts)))
