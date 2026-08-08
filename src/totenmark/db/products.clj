(ns totenmark.db.products
  (:require [honey.sql :as sql]
            [next.jdbc :as next-jdbc]
            [next.jdbc.result-set :as rs]
            [totenmark.db.config :as config]
            [totenmark.utils.date :as date]))

(def opts
  {:builder-fn rs/as-unqualified-lower-maps})

(defn create!
  [{:keys [product-name description price category status user-id]}]
  (let [query (-> {:insert-into :products
                   :values [{:product-name product-name
                             :description  description
                             :price        price
                             :category     category
                             :status       status
                             :user-id      user-id}]}
                  sql/format)]
    (next-jdbc/execute-one!
     config/ds
     query
     opts)))

(defn find-all
  [{:keys [limit offset query-text category status]}]
  (let [conditions (cond-> []
                     query-text (conj [:like :product-name (str "%" query-text "%")])
                     category (conj [:= :category category])
                     status (conj [:= :status status]))
        statement (cond-> {:select [:*]
                           :from [:products]
                           :order-by [[:product-id :asc]]
                           :limit limit
                           :offset offset}
                    (seq conditions) (assoc :where (into [:and] conditions)))
        query (-> statement
                  sql/format)]
    (next-jdbc/execute!
     config/ds
     query
     opts)))

(defn update!
  [attrs]
  (let [query (-> {:update :products
                   :set (-> attrs
                            (assoc :updated-at (date/now))
                            (dissoc :product-id))
                   :where [:= :product-id (:product-id attrs)]}
                  sql/format)]
    (next-jdbc/execute-one!
     config/ds
     query
     opts)))

(defn delete!
  [product-id]
  (let [query (-> {:delete-from :products
                   :where [:= :product-id product-id]}
                  sql/format)]
    (next-jdbc/execute-one!
     config/ds
     query
     opts)))

(defn find-by-id
  [product-id]
  (let [query (-> {:select [:*]
                   :from [:products]
                   :where [:= :product-id product-id]}
                  sql/format)]
    (next-jdbc/execute-one!
     config/ds
     query
     opts)))
