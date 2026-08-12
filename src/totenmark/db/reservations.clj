(ns totenmark.db.reservations
  (:require [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [totenmark.config :as app-config]
            [totenmark.db.config :as db]
            [totenmark.utils.date :as date]))

(def ^:private query-options
  {:builder-fn rs/as-unqualified-kebab-maps})

(defn public-by-product
  [connectable product-ids]
  (if (seq product-ids)
    (->> (jdbc/execute!
          connectable
          (sql/format {:select [:product-id :created-at :expires-at]
                       :from [:reservations]
                       :where [:in :product-id product-ids]})
          query-options)
         (map (juxt :product-id #(dissoc % :product-id)))
         (into {}))
    {}))

(defn release-expired!
  "Remove reservas vencidas e devolve os anúncios ao catálogo. Pode operar no
  catálogo inteiro ou apenas em um produto dentro da transação atual."
  ([connectable]
   (release-expired! connectable nil))
  ([connectable product-id]
   (let [now (date/now)
         product-filter (when product-id " AND product_id = ?")
         delete-sql (str "DELETE FROM reservations WHERE expires_at <= ?"
                         product-filter)
         delete-params (cond-> [delete-sql now]
                         product-id (conj product-id))
         update-sql (str "UPDATE products SET status = 'available', updated_at = ?
                          WHERE status = 'reserved'"
                         product-filter
                         " AND NOT EXISTS (SELECT 1 FROM reservations
                                           WHERE reservations.product_id = products.product_id)")
         update-params (cond-> [update-sql now]
                         product-id (conj product-id))]
     (jdbc/execute-one! connectable delete-params)
     (jdbc/execute-one! connectable update-params))))

(defn reserve!
  [product-id user-id]
  (jdbc/with-transaction [tx db/ds]
    (release-expired! tx product-id)
    (let [product (jdbc/execute-one!
                   tx
                   ["SELECT product_id, user_id, status FROM products WHERE product_id = ?"
                    product-id]
                   query-options)]
      (cond
        (nil? product) {:result :not-found}
        (= user-id (:user-id product)) {:result :owner-cannot-reserve}
        (not= "available" (:status product)) {:result :unavailable}

        :else
        (let [now (date/now)
              update-result (jdbc/execute-one!
                             tx
                             ["UPDATE products SET status = 'reserved', updated_at = ?
                               WHERE product_id = ? AND status = 'available'"
                              now product-id])]
          ;; A condição no UPDATE é o que impede duas reservas simultâneas.
          (if (= 1 (:next.jdbc/update-count update-result))
            (let [expires-at (date/after-seconds
                              (app-config/reservation-ttl-seconds))]
              (jdbc/execute-one!
               tx
               ["INSERT INTO reservations(product_id, reserved_by, created_at, expires_at)
                 VALUES (?, ?, ?, ?)"
                product-id user-id now expires-at])
              {:result :reserved
               :reservation {:product-id product-id
                             :reserved-by user-id
                             :created-at now
                             :expires-at expires-at}})
            {:result :unavailable}))))))

(defn release!
  [product-id user-id]
  (jdbc/with-transaction [tx db/ds]
    (release-expired! tx product-id)
    (let [product (jdbc/execute-one!
                   tx
                   ["SELECT product_id, user_id FROM products WHERE product_id = ?"
                    product-id]
                   query-options)
          reservation (jdbc/execute-one!
                       tx
                       ["SELECT reserved_by FROM reservations WHERE product_id = ?"
                        product-id]
                       query-options)]
      (cond
        (nil? product) {:result :not-found}
        (nil? reservation) {:result :not-reserved}
        (not (or (= user-id (:user-id product))
                 (= user-id (:reserved-by reservation))))
        {:result :forbidden}

        :else
        (do
          (jdbc/execute-one! tx
                             ["DELETE FROM reservations WHERE product_id = ?"
                              product-id])
          (jdbc/execute-one! tx
                             ["UPDATE products SET status = 'available', updated_at = ?
                               WHERE product_id = ?"
                              (date/now) product-id])
          {:result :released})))))
