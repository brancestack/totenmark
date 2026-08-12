(ns totenmark.http.api-integration-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [next.jdbc :as jdbc]
            [totenmark.http.integration-support
             :refer [*test-ds* create-user-and-token! json-body login request
                     with-test-api]]
            [totenmark.utils.jwt :as jwt]))

(use-fixtures :each with-test-api)

(deftest public-catalog-and-protected-writing
  (testing "catalog is public but creation requires authentication"
    (is (= 200 (:status (request :get "/api/products"))))
    (is (= 401 (:status (request :post "/api/products"
                                 :body {:product-name "Bike"
                                        :price 500
                                        :category "sale"})))))
  (let [token (create-user-and-token! "seller")
        duplicate (request :post "/api/users"
                           :body {:username "another-seller"
                                  :email "SELLER@example.com"
                                  :password "password123"})
        creation (request :post "/api/products"
                          :token token
                          :body {:product-name "Bike"
                                 :description "City bike"
                                 :price 500
                                 :category "sale"
                                 :image-urls ["https://example.com/bike.jpg"]})
        product (-> creation json-body :product)]
    (is (= 409 (:status duplicate)))
    (is (= 201 (:status creation)))
    (is (= ["https://example.com/bike.jpg"] (:images product)))
    (let [listing (json-body (request :get "/api/products?limit=10&offset=0&q=Bike"))]
      (is (= 1 (get-in listing [:pagination :total])))
      (is (= "Bike" (get-in listing [:items 0 :product-name]))))))

(deftest reservation-is-atomic-and-authorized
  (let [seller-token (create-user-and-token! "seller")
        buyer-token (create-user-and-token! "buyer")
        other-token (create-user-and-token! "other")
        product-id (-> (request :post "/api/products"
                                :token seller-token
                                :body {:product-name "Desk"
                                       :price 200
                                       :category "sale"})
                       json-body :product :product-id)]
    (is (= 409 (:status (request :post (str "/api/products/" product-id "/reservations")
                                 :token seller-token))))
    (is (= 201 (:status (request :post (str "/api/products/" product-id "/reservations")
                                 :token buyer-token))))
    (is (= 409 (:status (request :post (str "/api/products/" product-id "/reservations")
                                 :token other-token))))
    (is (= 403 (:status (request :delete (str "/api/products/" product-id "/reservations")
                                 :token other-token))))
    (is (= 200 (:status (request :delete (str "/api/products/" product-id "/reservations")
                                 :token buyer-token))))
    (is (= "available"
           (:status (json-body (request :get (str "/api/products/" product-id))))))))

(deftest cors-and-health-checks
  (is (= 200 (:status (request :get "/health"))))
  (is (= 200 (:status (request :get "/ready"))))
  (let [response (request :options "/api/products"
                          :headers {"origin" "http://localhost:5173"})]
    (is (= 204 (:status response)))
    (is (= "http://localhost:5173"
           (get-in response [:headers "Access-Control-Allow-Origin"])))))

(deftest logout-revokes-token
  (let [token (create-user-and-token! "logout-user")]
    (is (= 200 (:status (request :post "/api/auth/logout" :token token))))
    (is (= 401 (:status (request :get "/api/users" :token token))))))

(deftest foreign-keys-and-owner-deletion
  (is (= 1 (-> (jdbc/execute-one! *test-ds* ["PRAGMA foreign_keys"])
               vals
               first)))
  (let [token (create-user-and-token! "temporary-seller")
        user-id (:user-id (jwt/verify token))]
    (is (= 201 (:status
                (request :post "/api/products"
                         :token token
                         :body {:product-name "Temporary item"
                                :price 10
                                :category "donation"
                                :image-urls ["https://example.com/item.jpg"]}))))
    (is (= 200 (:status
                (request :delete (str "/api/users/" user-id)
                         :token token))))
    (is (= 401 (:status (request :get "/api/users" :token token))))
    (is (= 0 (get-in (json-body (request :get "/api/products"))
                     [:pagination :total])))))

(deftest user-profile-is-private-and-protected
  (let [alice-token (create-user-and-token! "alice-private")
        bob-token (create-user-and-token! "bob-private")
        alice-id (:user-id (jwt/verify alice-token))
        own-profile (json-body
                     (request :get (str "/api/users/" alice-id)
                              :token alice-token))
        public-profile (json-body
                        (request :get (str "/api/users/" alice-id)
                                 :token bob-token))]
    (is (= #{:id :username :email} (set (keys own-profile))))
    (is (= "alice-private@example.com" (:email own-profile)))
    (is (= #{:id :username} (set (keys public-profile))))
    (is (= 403 (:status
                (request :patch (str "/api/users/" alice-id)
                         :token bob-token
                         :body {:username "Bob was here"}))))
    (is (= 403 (:status
                (request :delete (str "/api/users/" alice-id)
                         :token bob-token))))
    (let [search (json-body
                  (request :get "/api/users?q=example.com"
                           :token bob-token))]
      (is (= 0 (get-in search [:pagination :total])))
      (is (empty? (:items search))))
    (is (= 0 (get-in (json-body
                       (request :get "/api/users?q=%25"
                                :token bob-token))
                      [:pagination :total])))))

(deftest email-and-password-updates-stay-consistent
  (let [alice-token (create-user-and-token! "alice-account")
        bob-token (create-user-and-token! "bob-account")
        alice-id (:user-id (jwt/verify alice-token))]
    (is (= 200 (:status
                (request :patch (str "/api/users/" alice-id)
                         :token alice-token
                         :body {:email "  ALICE.NEW@example.com  "}))))
    (is (= 401 (:status (login "alice-account@example.com" "password123"))))
    (let [new-email-token (-> (login "  ALICE.NEW@EXAMPLE.COM  " "password123")
                              json-body
                              :token)]
      (is (string? new-email-token))
      (is (= 409 (:status
                  (request :patch (str "/api/users/" alice-id)
                           :token alice-token
                           :body {:email "BOB-ACCOUNT@example.com"
                                  :password "must-not-be-applied"}))))
      (is (= "alice.new@example.com"
             (:email (json-body
                      (request :get (str "/api/users/" alice-id)
                               :token alice-token)))))
      (is (= 401 (:status
                  (login "alice.new@example.com" "must-not-be-applied"))))
      (is (= 200 (:status
                  (request :get "/api/users" :token new-email-token))))
      (is (= 200 (:status
                  (request :patch (str "/api/users/" alice-id)
                           :token alice-token
                           :body {:password "a-better-password"}))))
      (is (= 401 (:status
                  (request :get "/api/users" :token alice-token))))
      (is (= 401 (:status
                  (request :get "/api/users" :token new-email-token))))
      (is (= 401 (:status (login "alice.new@example.com" "password123"))))
      (let [fresh-token (-> (login "alice.new@example.com" "a-better-password")
                            json-body
                            :token)]
        (is (string? fresh-token))
        (is (= 200 (:status
                    (request :get (str "/api/users/" alice-id)
                             :token fresh-token))))))
    (is (= 200 (:status
                (request :get "/api/users"
                         :token bob-token))))))

(deftest concurrent-signups-keep-email-unique
  (let [start (promise)
        signup #(future
                  @start
                  (request :post "/api/users"
                           :body {:username %
                                  :email "RACE@example.com"
                                  :password "password123"}))
        first-request (signup "Race One")
        second-request (signup "Race Two")]
    (deliver start true)
    (is (= {201 1, 409 1}
           (frequencies (map :status [@first-request @second-request]))))
    (is (= 1 (-> (jdbc/execute-one!
                  *test-ds*
                  ["SELECT COUNT(*) AS total FROM users WHERE email = ?"
                   "race@example.com"])
                 vals
                 first)))))

(deftest deleting-a-buyer-releases-the-product
  (let [seller-token (create-user-and-token! "seller-delete-buyer")
        buyer-token (create-user-and-token! "buyer-to-delete")
        buyer-id (:user-id (jwt/verify buyer-token))
        product-id (-> (request :post "/api/products"
                                :token seller-token
                                :body {:product-name "Chair"
                                       :price 80
                                       :category "sale"})
                       json-body
                       :product
                       :product-id)]
    (is (= 201 (:status
                (request :post (str "/api/products/" product-id "/reservations")
                         :token buyer-token))))
    (is (= 200 (:status
                (request :delete (str "/api/users/" buyer-id)
                         :token buyer-token))))
    (is (= 401 (:status
                (request :get "/api/users" :token buyer-token))))
    (let [product (json-body
                   (request :get (str "/api/products/" product-id)))]
      (is (= "available" (:status product)))
      (is (not (contains? product :reservation))))))
