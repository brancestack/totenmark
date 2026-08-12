(ns totenmark.db.tokens
  (:require [next.jdbc :as jdbc]
            [totenmark.db.config :as config]))

(defn- epoch-seconds
  []
  (.getEpochSecond (java.time.Instant/now)))

(defn revoke!
  [{:keys [jti exp]}]
  (when (and jti exp)
    (jdbc/with-transaction [tx config/ds]
      (jdbc/execute-one! tx ["DELETE FROM revoked_tokens WHERE expires_at <= ?"
                             (epoch-seconds)])
      (jdbc/execute-one! tx
                         ["INSERT OR IGNORE INTO revoked_tokens(jti, expires_at) VALUES (?, ?)"
                          jti exp]))))

(defn revoked?
  [jti]
  (when jti
    (boolean (jdbc/execute-one!
              config/ds
              ["SELECT 1 AS revoked FROM revoked_tokens WHERE jti = ? AND expires_at > ?"
               jti (epoch-seconds)]))))
