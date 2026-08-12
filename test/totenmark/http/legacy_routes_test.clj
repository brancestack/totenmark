(ns totenmark.http.legacy-routes-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [totenmark.http.integration-support
             :refer [json-body login request with-test-api]]))

(use-fixtures :each with-test-api)

(deftest legacy-routes-still-cover-the-original-flow
  (is (= 201 (:status
              (request :post "/api/user/create"
                       :body {:username "Legacy user"
                              :email "legacy@example.com"
                              :password "password123"}))))
  (let [token (-> (login "legacy@example.com" "password123")
                  json-body
                  :token)
        creation (request :post "/api/product/create"
                          :token token
                          :body {:product-name "Legacy item"
                                 :price 40
                                 :category "sale"})
        id (-> creation json-body :product :product-id)]
    (is (= 201 (:status creation)))
    (is (= 1 (get-in (json-body
                       (request :get "/api/product/all"))
                      [:pagination :total])))
    (is (= 200 (:status
                (request :patch "/api/product/update"
                         :token token
                         :body {:product-id id :price 35}))))
    (is (= 35 (:price (json-body
                       (request :get (str "/api/product/" id))))))
    (is (= 200 (:status
                (request :delete "/api/product/delete"
                         :token token
                         :body {:product-id id}))))
    (is (= 404 (:status
                (request :get (str "/api/product/" id)))))))
