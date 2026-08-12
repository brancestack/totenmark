(ns totenmark.http.integration-support
  (:require [clojure.data.json :as json]
            [clojure.test :refer [is]]
            [io.pedestal.connector.test :as connector.test]
            [next.jdbc :as jdbc]
            [totenmark.db.config :as db]
            [totenmark.db.migration :as migration]
            [totenmark.http.server :as server]))

(def ^:dynamic *connector*)
(def ^:dynamic *test-ds*)

(defn json-body
  [response]
  (json/read-str (:body response) :key-fn keyword))

(defn request
  [method path & {:keys [body raw-body token headers]}]
  (connector.test/response-for
   *connector*
   method
   path
   :headers (merge {"content-type" "application/json"}
                   (when token {"authorization" (str "Bearer " token)})
                   headers)
   :body (if (some? raw-body)
           raw-body
           (when (some? body) (json/write-str body)))))

(defn login
  [email password]
  (request :post "/api/auth/login"
           :body {:email email :password password}))

(defn create-user-and-token!
  [suffix]
  (let [email (str suffix "@example.com")]
    (is (= 201 (:status (request :post "/api/users"
                                 :body {:username suffix
                                        :email email
                                        :password "password123"}))))
    (let [response (login email "password123")]
      (is (= 200 (:status response)))
      (:token (json-body response)))))

(defn with-test-api
  [test-function]
  (let [path (str (java.nio.file.Files/createTempFile
                   "totenmark-integration-"
                   ".sqlite"
                   (make-array java.nio.file.attribute.FileAttribute 0)))
        test-ds (jdbc/get-datasource {:dbtype "sqlite"
                                      :dbname path
                                      :foreign_keys true
                                      :busy_timeout 5000})]
    (System/setProperty "totenmark.db.name" path)
    (System/setProperty "totenmark.jwt.secret"
                        "integration-test-secret-with-sufficient-length")
    (try
      (migration/migrate!)
      (with-redefs [db/ds test-ds]
        (binding [*connector* (server/create-connector)
                  *test-ds* test-ds]
          (test-function)))
      (finally
        (System/clearProperty "totenmark.db.name")
        (System/clearProperty "totenmark.jwt.secret")
        (java.nio.file.Files/deleteIfExists
         (java.nio.file.Path/of path (make-array String 0)))))))
