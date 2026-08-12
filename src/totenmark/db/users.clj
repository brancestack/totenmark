(ns totenmark.db.users
  (:require [clojure.string :as str]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [totenmark.db.config :as db]
            [totenmark.utils.date :as date]))

(def ^:private query-options
  {:builder-fn rs/as-unqualified-kebab-maps})

(def ^:private public-columns
  [:id :username])

(def ^:private account-columns
  [:id :username :email])

(defn- email-conflict?
  [exception]
  (loop [^Throwable cause exception]
    (cond
      (nil? cause) false
      (str/includes? (or (.getMessage cause) "")
                     "SQLITE_CONSTRAINT_UNIQUE") true
      :else (recur (.getCause cause)))))

(defn create!
  [{:keys [username email password]}]
  (try
    (jdbc/execute-one!
     db/ds
     (sql/format {:insert-into :users
                  :values [{:username username
                            :email email
                            :password password}]})
     query-options)
    {:result :created}
    (catch Exception exception
      (if (email-conflict? exception)
        {:result :email-taken}
        (throw exception)))))

(defn find-all
  [{:keys [limit offset query-text]}]
  (let [query (cond-> {:select public-columns
                       :from [:users]
                       :order-by [[:username :asc]]
                       :limit limit
                       :offset offset}
                query-text
                (assoc :where [:> [:instr [:lower :username]
                                      [:lower query-text]] 0]))]
    (jdbc/execute! db/ds (sql/format query) query-options)))

(defn count-all
  [{:keys [query-text]}]
  (let [query (cond-> {:select [[[:count :*] :total]]
                       :from [:users]}
                query-text
                (assoc :where [:> [:instr [:lower :username]
                                      [:lower query-text]] 0]))]
    (:total (jdbc/execute-one! db/ds (sql/format query) query-options))))

(defn update!
  [{:keys [id] :as attrs}]
  (let [fields (cond-> (dissoc attrs :id)
                 (contains? attrs :password)
                 (assoc :session-version [:+ :session-version 1]))]
    (try
      (let [result (jdbc/execute-one!
                    db/ds
                    (sql/format {:update :users
                                 :set fields
                                 :where [:= :id id]})
                    query-options)]
        {:result (if (= 1 (:next.jdbc/update-count result))
                   :updated
                   :not-found)})
      (catch Exception exception
        (if (email-conflict? exception)
          {:result :email-taken}
          (throw exception))))))

(defn delete!
  [id]
  (jdbc/with-transaction [tx db/ds]
    ;; Produtos do vendedor e reservas do comprador saem por ON DELETE CASCADE.
    (jdbc/execute-one! tx
                       (sql/format {:delete-from :users
                                    :where [:= :id id]})
                       query-options)
    (jdbc/execute-one!
     tx
     ["UPDATE products SET status = 'available', updated_at = ?
       WHERE status = 'reserved'
       AND NOT EXISTS (SELECT 1 FROM reservations
                       WHERE reservations.product_id = products.product_id)"
      (date/now)]
     query-options)))

(defn find-by-id
  [id]
  (jdbc/execute-one!
   db/ds
   (sql/format {:select public-columns
                :from [:users]
                :where [:= :id id]})
   query-options))

(defn find-account-by-id
  [id]
  (jdbc/execute-one!
   db/ds
   (sql/format {:select account-columns
                :from [:users]
                :where [:= :id id]})
   query-options))

(defn auth-state
  [id]
  (jdbc/execute-one!
   db/ds
   ["SELECT id, session_version FROM users WHERE id = ?" id]
   query-options))

(defn find-by-email
  [email]
  (jdbc/execute-one!
   db/ds
   (sql/format {:select [:*]
                :from [:users]
                :where [:= :email email]})
   query-options))
