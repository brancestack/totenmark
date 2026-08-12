(ns totenmark.db.runtime-test
  (:require [clojure.test :refer [deftest is]]
            [next.jdbc :as jdbc]
            [totenmark.db.config :as db]
            [totenmark.db.migration :as migration]
            [totenmark.db.tokens :as tokens]))

(defn- with-temp-database
  [test-function]
  (let [path (str (java.nio.file.Files/createTempFile
                   "totenmark-runtime-"
                   ".sqlite"
                   (make-array java.nio.file.attribute.FileAttribute 0)))
        datasource (jdbc/get-datasource {:dbtype "sqlite"
                                         :dbname path
                                         :foreign_keys true
                                         :busy_timeout 5000})]
    (System/setProperty "totenmark.db.name" path)
    (try
      (with-redefs [db/ds datasource]
        (test-function datasource))
      (finally
        (System/clearProperty "totenmark.db.name")
        (java.nio.file.Files/deleteIfExists
         (java.nio.file.Path/of path (make-array String 0)))))))

(deftest readiness-depends-on-the-complete-schema
  (with-temp-database
    (fn [datasource]
      (is (false? (db/ready?)))
      (migration/migrate!)
      (is (true? (db/ready?)))
      (jdbc/execute-one! datasource ["DROP TABLE revoked_tokens"])
      (is (false? (db/ready?))))))

(deftest token-revocation-is-idempotent-and-cleans-expired-rows
  (with-temp-database
    (fn [datasource]
      (migration/migrate!)
      (let [now (.getEpochSecond (java.time.Instant/now))]
        (tokens/revoke! {:jti "active" :exp (+ now 600)})
        (tokens/revoke! {:jti "active" :exp (+ now 600)})
        (is (true? (tokens/revoked? "active")))
        (is (= 1 (-> (jdbc/execute-one!
                      datasource
                      ["SELECT COUNT(*) AS total FROM revoked_tokens
                        WHERE jti = 'active'"])
                     vals
                     first)))

        (tokens/revoke! {:jti "already-expired" :exp (dec now)})
        (is (false? (boolean (tokens/revoked? "already-expired"))))
        (tokens/revoke! {:jti "another" :exp (+ now 600)})
        (is (= 0 (-> (jdbc/execute-one!
                      datasource
                      ["SELECT COUNT(*) AS total FROM revoked_tokens
                        WHERE jti = 'already-expired'"])
                     vals
                     first)))
        (tokens/revoke! {})
        (is (nil? (tokens/revoked? nil)))))))
