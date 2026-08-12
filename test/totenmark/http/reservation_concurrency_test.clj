(ns totenmark.http.reservation-concurrency-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [next.jdbc :as jdbc]
            [totenmark.http.integration-support
             :refer [*test-ds* create-user-and-token! json-body request
                     with-test-api]]))

(use-fixtures :each with-test-api)

(defn- product-id
  [seller-token name]
  (-> (request :post "/api/products"
               :token seller-token
               :body {:product-name name
                      :price 100
                      :category "sale"})
      json-body
      :product
      :product-id))

(defn- reservation-count
  [id]
  (-> (jdbc/execute-one!
       *test-ds*
       ["SELECT COUNT(*) AS total FROM reservations WHERE product_id = ?" id])
      vals
      first))

(deftest exactly-one-buyer-wins-a-concurrent-reservation
  (let [seller-token (create-user-and-token! "race-seller")
        buyer-tokens (mapv #(create-user-and-token! (str "race-buyer-" %))
                           (range 5))
        id (product-id seller-token "Console")
        start (promise)
        attempts (mapv (fn [token]
                         (future
                           @start
                           (request :post
                                    (str "/api/products/" id "/reservations")
                                    :token token)))
                       buyer-tokens)]
    (deliver start true)
    (is (= {201 1, 409 4}
           (frequencies (map (comp :status deref) attempts))))
    (is (= 1 (reservation-count id)))
    (is (= "reserved"
           (:status (json-body
                     (request :get (str "/api/products/" id))))))))

(deftest an-expired-reservation-returns-to-the-catalog
  (let [seller-token (create-user-and-token! "expiry-seller")
        first-buyer (create-user-and-token! "expiry-first")
        second-buyer (create-user-and-token! "expiry-second")
        id (product-id seller-token "Typewriter")
        path (str "/api/products/" id "/reservations")]
    (is (= 201 (:status (request :post path :token first-buyer))))
    (jdbc/execute-one!
     *test-ds*
     ["UPDATE reservations SET expires_at = '1970-01-01T00:00:00Z'
       WHERE product_id = ?" id])
    (let [product (json-body (request :get (str "/api/products/" id)))]
      (is (= "available" (:status product)))
      (is (not (contains? product :reservation))))
    (is (= 0 (reservation-count id)))
    (is (= 201 (:status (request :post path :token second-buyer))))))

(deftest release-and-new-reservation-never-leave-an-impossible-state
  (let [seller-token (create-user-and-token! "handoff-seller")
        first-buyer (create-user-and-token! "handoff-first")
        second-buyer (create-user-and-token! "handoff-second")]
    (dotimes [round 8]
      (let [id (product-id seller-token (str "Handoff " round))
            path (str "/api/products/" id "/reservations")
            _ (is (= 201 (:status (request :post path :token first-buyer))))
            start (promise)
            release (future @start (request :delete path :token first-buyer))
            reserve (future @start (request :post path :token second-buyer))]
        (deliver start true)
        (is (= 200 (:status @release)))
        (is (contains? #{201 409} (:status @reserve)))
        (let [product (json-body (request :get (str "/api/products/" id)))
              reservations (reservation-count id)]
          (is (or (and (= "available" (:status product))
                       (zero? reservations))
                  (and (= "reserved" (:status product))
                       (= 1 reservations)))))))))
