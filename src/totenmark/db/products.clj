(ns totenmark.db.products
  (:require [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [totenmark.db.config :as db]
            [totenmark.db.reservations :as reservations]
            [totenmark.utils.date :as date]))

(def ^:private query-options
  {:builder-fn rs/as-unqualified-kebab-maps})

(defn- filters->where
  [{:keys [query-text category status user-id]}]
  (let [filters (cond-> []
                  query-text (conj [:or
                                    [:> [:instr [:lower :product-name]
                                          [:lower query-text]] 0]
                                    [:> [:instr [:lower [:coalesce :description ""]]
                                          [:lower query-text]] 0]])
                  category (conj [:= :category category])
                  status (conj [:= :status status])
                  user-id (conj [:= :user-id user-id]))]
    (when (seq filters)
      (into [:and] filters))))

(defn- find-row
  [connectable product-id]
  (jdbc/execute-one!
   connectable
   (sql/format {:select [:*]
                :from [:products]
                :where [:= :product-id product-id]})
   query-options))

(defn- images-by-product
  [connectable product-ids]
  (if (seq product-ids)
    (->> (jdbc/execute!
          connectable
          (sql/format {:select [:product-id :url]
                       :from [:product-images]
                       :where [:in :product-id product-ids]
                       :order-by [[:product-id :asc] [:position :asc]]})
          query-options)
         (group-by :product-id))
    {}))

(defn- with-details
  [connectable products]
  (let [product-ids (mapv :product-id products)
        images (images-by-product connectable product-ids)
        reservations (reservations/public-by-product connectable product-ids)]
    (mapv (fn [{:keys [product-id] :as product}]
            (cond-> (assoc product :images (mapv :url (get images product-id [])))
              (get reservations product-id)
              (assoc :reservation (get reservations product-id))))
          products)))

(defn- insert-images!
  [tx product-id image-urls]
  (doseq [[position url] (map-indexed vector image-urls)]
    (jdbc/execute-one!
     tx
     (sql/format {:insert-into :product-images
                  :values [{:product-id product-id
                            :url url
                            :position position}]})
     query-options)))

(defn create!
  [{:keys [product-name description price category status user-id image-urls]}]
  (jdbc/with-transaction [tx db/ds]
    (let [now (date/now)
          {:keys [product-id]}
          (jdbc/execute-one!
           tx
           (sql/format {:insert-into :products
                        :values [{:product-name product-name
                                  :description description
                                  :price price
                                  :category category
                                  :status status
                                  :user-id user-id
                                  :created-at now}]
                        :returning [:product-id]})
           query-options)]
      (insert-images! tx product-id image-urls)
      (first (with-details tx [(find-row tx product-id)])))))

(defn find-all
  [{:keys [limit offset] :as options}]
  (jdbc/with-transaction [tx db/ds]
    (reservations/release-expired! tx)
    (let [where (filters->where options)
          query (cond-> {:select [:*]
                         :from [:products]
                         :order-by [[:created-at :desc] [:product-id :desc]]
                         :limit limit
                         :offset offset}
                  where (assoc :where where))]
      (->> (jdbc/execute! tx (sql/format query) query-options)
           (with-details tx)))))

(defn count-all
  [options]
  (let [where (filters->where options)
        query (cond-> {:select [[[:count :*] :total]]
                       :from [:products]}
                where (assoc :where where))]
    (:total (jdbc/execute-one! db/ds (sql/format query) query-options))))

(defn find-by-id
  [product-id]
  (jdbc/with-transaction [tx db/ds]
    (reservations/release-expired! tx product-id)
    (when-let [product (find-row tx product-id)]
      (first (with-details tx [product])))))

(defn update!
  [{:keys [product-id image-urls] :as attrs}]
  (jdbc/with-transaction [tx db/ds]
    (let [product-fields (-> attrs
                             (dissoc :product-id :image-urls)
                             (assoc :updated-at (date/now)))]
      (jdbc/execute-one!
       tx
       (sql/format {:update :products
                    :set product-fields
                    :where [:= :product-id product-id]})
       query-options)
      (when (some? image-urls)
        (jdbc/execute-one! tx
                           ["DELETE FROM product_images WHERE product_id = ?"
                            product-id])
        (insert-images! tx product-id image-urls))
      (first (with-details tx [(find-row tx product-id)])))))

(defn delete!
  [product-id]
  (jdbc/execute-one!
   db/ds
   (sql/format {:delete-from :products
                :where [:= :product-id product-id]})
   query-options))
