(ns totenmark.http.api-edge-cases-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [next.jdbc :as jdbc]
            [totenmark.http.integration-support
             :refer [*test-ds* create-user-and-token! json-body login request
                     with-test-api]]))

(use-fixtures :each with-test-api)

(deftest rejects-malformed-or-unsupported-input
  (testing "malformed JSON never reaches a handler"
    (let [response (request :post "/api/users"
                            :raw-body "{not-json"
                            :headers {"origin" "http://localhost:5173"})]
      (is (= 400 (:status response)))
      (is (= "http://localhost:5173"
             (get-in response [:headers "Access-Control-Allow-Origin"])))
      (is (string? (get-in response [:headers "X-Request-ID"])))))
  (testing "unknown account fields are rejected"
    (is (= 422 (:status
                (request :post "/api/users"
                         :body {:username "Alice"
                                :email "alice@example.com"
                                :password "password123"
                                :admin true})))))
  (testing "BCrypt's 72-byte boundary is enforced before hashing"
    (is (= 422 (:status
                (request :post "/api/users"
                         :body {:username "Long Password"
                                :email "long-password@example.com"
                                :password (apply str (repeat 73 "a"))}))))
    (is (= 422 (:status
                (request :post "/api/users"
                         :body {:username "Unicode Password"
                                :email "unicode-password@example.com"
                                :password (apply str (repeat 19 "🔐"))})))))
  (testing "login requires strings"
    (is (= 422 (:status
                (request :post "/api/auth/login"
                         :body {:email ["alice@example.com"]
                                :password 12345678})))))
  (testing "product fields respect the storage contract"
    (let [token (create-user-and-token! "edge-seller")]
      (doseq [body [{:product-name "Map description"
                     :description {:unexpected true}
                     :price 10
                     :category "sale"}
                    {:product-name "Oversized price"
                     :price 9223372036854775808N
                     :category "sale"}
                    {:product-name "Forged owner"
                     :price 10
                     :category "sale"
                     :user-id 999}
                    {:product-name "Reserved at creation"
                     :price 10
                     :category "sale"
                     :status "reserved"}]]
        (is (= 422 (:status
                    (request :post "/api/products"
                             :token token
                             :body body))))))))

(deftest validates-identifiers-and-pagination-boundaries
  (let [token (create-user-and-token! "boundary-user")]
    (doseq [path ["/api/users/0" "/api/users/-1"
                  "/api/products/0" "/api/products/-1"]]
      (is (= 422 (:status (request :get path :token token)))))
    (doseq [path ["/api/users?limit=0"
                  "/api/users?limit=101"
                  "/api/users?offset=-1"
                  "/api/products?limit=0"
                  "/api/products?offset=-1"
                  "/api/products?user-id=0"
                  "/api/products?category=trade"
                  "/api/products?status=sold"]]
      (is (= 422 (:status (request :get path :token token)))))))

(deftest cors-and-request-identifiers-cover-error-responses
  (testing "an allowed origin is present even on authentication errors"
    (let [response (request :get "/api/users"
                            :headers {"origin" "http://localhost:5173"
                                      "x-request-id" "client-request-42"})]
      (is (= 401 (:status response)))
      (is (= "http://localhost:5173"
             (get-in response [:headers "Access-Control-Allow-Origin"])))
      (is (= "client-request-42"
             (get-in response [:headers "X-Request-ID"])))))
  (testing "unknown origins are rejected"
    (is (= 403 (:status
                (request :get "/api/products"
                         :headers {"origin" "https://unknown.example"})))))
  (testing "the server generates an identifier when the client does not"
    (let [request-id (get-in (request :get "/health")
                             [:headers "X-Request-ID"])]
      (is (string? request-id))
      (is (uuid? (parse-uuid request-id))))))

(deftest logout-revokes-only-the-current-session
  (let [token-a (create-user-and-token! "two-sessions")
        token-b (-> (login "two-sessions@example.com" "password123")
                    json-body
                    :token)]
    (is (not= token-a token-b))
    (is (= 200 (:status (request :post "/api/auth/logout" :token token-a))))
    (is (= 401 (:status (request :get "/api/users" :token token-a))))
    (is (= 200 (:status (request :get "/api/users" :token token-b))))))

(deftest route-level-rate-limits-are-wired
  (let [signup-statuses
        (mapv (fn [_]
                (:status (request :post "/api/users"
                                  :body {:username ""
                                         :email "invalid"
                                         :password "short"})))
              (range 11))
        login-statuses
        (mapv (fn [_]
                (:status (login "missing@example.com" "password123")))
              (range 21))]
    (is (= (concat (repeat 10 422) [429]) signup-statuses))
    (is (= (concat (repeat 20 401) [429]) login-statuses))))

(deftest damaged-legacy-password-data-does-not-break-login
  (jdbc/execute-one!
   *test-ds*
   ["INSERT INTO users(username, email, password) VALUES (?, ?, ?)"
    "Legacy" "legacy-broken@example.com" "not-a-bcrypt-hash"])
  (is (= 401 (:status
              (login "legacy-broken@example.com" "password123")))))

(deftest readiness-fails-when-an-essential-table-disappears
  (is (= 200 (:status (request :get "/ready"))))
  (jdbc/execute-one! *test-ds* ["DROP TABLE revoked_tokens"])
  (is (= 503 (:status (request :get "/ready"))))
  (is (= 404 (:status (request :get "/route-that-does-not-exist")))))
